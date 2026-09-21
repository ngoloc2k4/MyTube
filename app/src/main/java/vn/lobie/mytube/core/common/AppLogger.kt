package vn.lobie.mytube.core.common

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogEntry(
    val id: Long = System.nanoTime(),
    val time: Long = System.currentTimeMillis(),
    val level: String, // "D", "I", "W", "E"
    val tag: String, // "Player", "Source", "Invidious", "NewPipe", "InnerTube", "Fallback", "Network"
    val videoId: String? = null,
    val message: String,
    val raw: String? = null
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(time))
}

object AppLogger {
    private const val MAX_RAM_LOGS = 350
    private const val MAX_FILE_SIZE_BYTES = 2 * 1024 * 1024L // 2MB rolling

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val logScope = CoroutineScope(Dispatchers.IO)
    private var logFile: File? = null
    var isVerboseLoggingEnabled: Boolean = true

    fun init(context: Context) {
        val dir = File(context.cacheDir, "logs")
        if (!dir.exists()) dir.mkdirs()
        logFile = File(dir, "mytube_debug.log")
        i("System", "AppLogger initialized. Device=${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})")
    }

    fun d(tag: String, msg: String, videoId: String? = null, raw: String? = null) {
        if (isVerboseLoggingEnabled) log("D", tag, msg, videoId, raw)
    }

    fun i(tag: String, msg: String, videoId: String? = null, raw: String? = null) = log("I", tag, msg, videoId, raw)
    fun w(tag: String, msg: String, videoId: String? = null, raw: String? = null) = log("W", tag, msg, videoId, raw)
    fun e(tag: String, msg: String, videoId: String? = null, raw: String? = null) = log("E", tag, msg, videoId, raw)

    fun log(level: String, tag: String, message: String, videoId: String? = null, raw: String? = null) {
        val vidPrefix = if (!videoId.isNullOrEmpty()) "[$videoId] " else ""
        when (level) {
            "E" -> Log.e("MyTube.$tag", "$vidPrefix$message", raw?.let { Exception(it) })
            "W" -> Log.w("MyTube.$tag", "$vidPrefix$message", raw?.let { Exception(it) })
            "I" -> Log.i("MyTube.$tag", "$vidPrefix$message")
            else -> Log.d("MyTube.$tag", "$vidPrefix$message")
        }

        val entry = LogEntry(
            level = level,
            tag = tag,
            videoId = videoId,
            message = message,
            raw = raw
        )

        val current = _logs.value.toMutableList()
        current.add(0, entry)
        if (current.size > MAX_RAM_LOGS) {
            current.removeAt(current.lastIndex)
        }
        _logs.value = current

        logScope.launch {
            writeToFile(entry)
        }
    }

    @Synchronized
    private fun writeToFile(entry: LogEntry) {
        val file = logFile ?: return
        try {
            if (file.exists() && file.length() > MAX_FILE_SIZE_BYTES) {
                val content = file.readText()
                val halfIndex = content.length / 2
                val nextNewline = content.indexOf('\n', halfIndex)
                val trimmed = if (nextNewline != -1) content.substring(nextNewline + 1) else content.substring(halfIndex)
                file.writeText(trimmed)
            }

            val line = buildString {
                append(entry.formattedTime)
                append(" [").append(entry.level).append("] ")
                append(entry.tag).append(": ")
                if (entry.videoId != null) append("[").append(entry.videoId).append("] ")
                append(entry.message)
                if (!entry.raw.isNullOrEmpty()) {
                    append("\n--- DETAILS ---\n").append(entry.raw).append("\n---------------")
                }
                append("\n")
            }
            FileOutputStream(file, true).use { fos ->
                fos.write(line.toByteArray(Charsets.UTF_8))
            }
        } catch (_: Exception) {}
    }

    fun clear() {
        _logs.value = emptyList()
        logScope.launch {
            try {
                logFile?.delete()
            } catch (_: Exception) {}
        }
    }

    fun getExportText(sourcePriority: List<String> = emptyList()): String {
        return buildString {
            append("========================================\n")
            append("           MyTube Debug Log             \n")
            append("========================================\n")
            append("Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
            append("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
            append("App Version: 2.0.0\n")
            if (sourcePriority.isNotEmpty()) {
                append("Source Priority: ${sourcePriority.joinToString(" > ")}\n")
            }
            append("Generated At: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n")
            append("========================================\n\n")

            val currentLogs = _logs.value.reversed()
            currentLogs.forEach { entry ->
                append(entry.formattedTime)
                append(" [").append(entry.level).append("] ")
                append(entry.tag).append(": ")
                if (entry.videoId != null) append("[").append(entry.videoId).append("] ")
                append(entry.message)
                if (!entry.raw.isNullOrEmpty()) {
                    append("\n--- DETAILS ---\n").append(entry.raw).append("\n---------------")
                }
                append("\n")
            }
        }
    }

    fun maskUrl(url: String): String {
        return try {
            val uri = android.net.Uri.parse(url)
            val host = uri.host ?: "unknown"
            val expire = uri.getQueryParameter("expire")
            val mime = uri.getQueryParameter("mime")
            val itag = uri.getQueryParameter("itag")
            buildString {
                append("https://").append(host)
                val params = mutableListOf<String>()
                if (itag != null) params.add("itag=$itag")
                if (expire != null) params.add("expire=$expire")
                if (mime != null) params.add("mime=$mime")
                params.add("sig=***")
                append("?").append(params.joinToString("&"))
            }
        } catch (e: Exception) {
            "masked_url"
        }
    }
}
