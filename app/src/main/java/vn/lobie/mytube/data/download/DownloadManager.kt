package vn.lobie.mytube.data.download

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import vn.lobie.mytube.data.local.db.MyTubeDatabase
import vn.lobie.mytube.data.local.db.entity.DownloadEntity
import vn.lobie.mytube.domain.model.Video
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class DownloadManager private constructor(private val context: Context) {

    private val db = MyTubeDatabase.getInstance(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    companion object {
        @Volatile
        private var INSTANCE: DownloadManager? = null

        fun getInstance(context: Context): DownloadManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DownloadManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private fun getDownloadsDir(): File {
        val dir = context.getExternalFilesDir("downloads") ?: File(context.filesDir, "downloads")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getDownloadedFile(videoId: String): File? {
        val file = File(getDownloadsDir(), "${videoId}.mp4")
        return if (file.exists() && file.length() > 0) file else null
    }

    fun startDownload(video: Video, streamUrl: String, quality: String = "720p") {
        if (activeJobs.containsKey(video.id)) {
            Log.d("DownloadManager", "Download already in progress for ${video.id}")
            return
        }

        val job = scope.launch {
            val targetFile = File(getDownloadsDir(), "${video.id}.mp4")
            val downloadEntity = DownloadEntity(
                videoId = video.id,
                title = video.title,
                channelName = video.channel.name,
                thumbnailUrl = video.thumbnailUrl,
                filePath = targetFile.absolutePath,
                quality = quality,
                status = 1, // 1 = downloading
                progressPercent = 0,
                startedAt = System.currentTimeMillis()
            )
            db.downloadDao().insert(downloadEntity)

            var inputStream: InputStream? = null
            var outputStream: FileOutputStream? = null

            try {
                val request = Request.Builder()
                    .url(streamUrl)
                    .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    throw java.io.IOException("HTTP error code: ${response.code}")
                }

                val body = response.body ?: throw java.io.IOException("Empty response body")
                val totalBytes = body.contentLength()
                inputStream = body.byteStream()
                outputStream = FileOutputStream(targetFile)

                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalRead = 0L
                var lastProgress = 0

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    if (totalBytes > 0) {
                        val progress = ((totalRead * 100) / totalBytes).toInt()
                        if (progress != lastProgress) {
                            lastProgress = progress
                            db.downloadDao().updateProgress(video.id, progress, 1)
                        }
                    }
                }

                outputStream.flush()
                db.downloadDao().updateCompleted(video.id, System.currentTimeMillis(), 2) // 2 = completed
                Log.d("DownloadManager", "Download completed: ${video.title} (${targetFile.length()} bytes)")
            } catch (e: Exception) {
                Log.e("DownloadManager", "Download failed for ${video.id}", e)
                db.downloadDao().updateProgress(video.id, 0, 3) // 3 = failed
                if (targetFile.exists()) {
                    targetFile.delete()
                }
            } finally {
                try { inputStream?.close() } catch (_: Exception) {}
                try { outputStream?.close() } catch (_: Exception) {}
                activeJobs.remove(video.id)
            }
        }

        activeJobs[video.id] = job
    }

    fun cancelDownload(videoId: String) {
        val job = activeJobs.remove(videoId)
        job?.cancel()
        scope.launch {
            val file = File(getDownloadsDir(), "${videoId}.mp4")
            if (file.exists()) {
                file.delete()
            }
            db.downloadDao().delete(videoId)
        }
    }

    fun deleteDownload(videoId: String) {
        cancelDownload(videoId)
    }
}
