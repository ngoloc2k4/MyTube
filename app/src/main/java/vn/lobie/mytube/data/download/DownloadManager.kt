package vn.lobie.mytube.data.download

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import vn.lobie.mytube.data.local.db.MyTubeDatabase
import vn.lobie.mytube.data.local.db.entity.DownloadEntity
import vn.lobie.mytube.domain.model.Video
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

class DownloadManager private constructor(private val context: Context) {

    private val db = MyTubeDatabase.getInstance(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()

    private val _downloadProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Int>> = _downloadProgress.asStateFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
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

        fun formatFileSize(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB")
            val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
            return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
        }
    }

    fun getDownloadsDir(): File {
        val dir = context.getExternalFilesDir("downloads") ?: File(context.filesDir, "downloads")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getDownloadedFile(videoId: String): File? {
        val dir = getDownloadsDir()
        val extensions = listOf("mp4", "m4a", "mp3", "webm", "mkv")
        for (ext in extensions) {
            val file = File(dir, "$videoId.$ext")
            if (file.exists() && file.length() > 0) {
                return file
            }
        }
        return null
    }

    fun isDownloading(videoId: String): Boolean = activeJobs.containsKey(videoId)

    fun startDownload(
        video: Video,
        streamUrl: String,
        audioUrl: String? = null,
        quality: String = "720p",
        format: String = "mp4",
        isAudioOnly: Boolean = false
    ) {
        if (activeJobs.containsKey(video.id)) {
            Log.d("DownloadManager", "Download already in progress for ${video.id}")
            return
        }

        val job = scope.launch {
            val extension = if (isAudioOnly) {
                if (format.contains("mp3", ignoreCase = true)) "mp3" else "m4a"
            } else "mp4"

            val targetFile = File(getDownloadsDir(), "${video.id}.$extension")
            val tempVideoFile = File(getDownloadsDir(), "${video.id}_temp_video.mp4")
            val tempAudioFile = File(getDownloadsDir(), "${video.id}_temp_audio.m4a")

            val downloadEntity = DownloadEntity(
                videoId = video.id,
                title = video.title,
                channelName = video.channel.name,
                thumbnailUrl = video.thumbnailUrl,
                filePath = targetFile.absolutePath,
                format = extension,
                quality = quality,
                status = 1, // 1 = downloading
                progressPercent = 0,
                startedAt = System.currentTimeMillis()
            )
            db.downloadDao().insert(downloadEntity)
            updateProgressState(video.id, 0)

            try {
                if (isAudioOnly) {
                    // 1. Audio-only download (M4A/MP3)
                    Log.d("DownloadManager", "Starting audio-only download for ${video.id}: $streamUrl")
                    val success = downloadStreamToFile(streamUrl, targetFile) { percent ->
                        updateProgress(video.id, percent)
                    }
                    if (!success) throw IllegalStateException("Failed to download audio stream")

                    db.downloadDao().updateCompletedDetails(
                        videoId = video.id,
                        completedAt = System.currentTimeMillis(),
                        status = 2,
                        fileSizeBytes = targetFile.length(),
                        filePath = targetFile.absolutePath
                    )
                } else if (!audioUrl.isNullOrBlank() && audioUrl != streamUrl) {
                    // 2. DASH separate streams: Download video + audio tracks, then multiplex via MediaMuxer
                    Log.d("DownloadManager", "Starting DASH download for ${video.id} (muxing video + audio)")

                    // Download video track (0% -> 70%)
                    val videoSuccess = downloadStreamToFile(streamUrl, tempVideoFile) { p ->
                        val combined = (p * 0.70).toInt()
                        updateProgress(video.id, combined)
                    }
                    if (!videoSuccess) throw IllegalStateException("Failed to download video track")

                    // Download audio track (70% -> 90%)
                    val audioSuccess = downloadStreamToFile(audioUrl, tempAudioFile) { p ->
                        val combined = 70 + (p * 0.20).toInt()
                        updateProgress(video.id, combined)
                    }
                    if (!audioSuccess) throw IllegalStateException("Failed to download audio track")

                    // Mux tracks into final MP4 (90% -> 100%)
                    updateProgress(video.id, 92)
                    val muxSuccess = muxVideoAndAudio(tempVideoFile, tempAudioFile, targetFile)
                    if (muxSuccess && targetFile.exists() && targetFile.length() > 0) {
                        tempVideoFile.delete()
                        tempAudioFile.delete()
                        db.downloadDao().updateCompletedDetails(
                            videoId = video.id,
                            completedAt = System.currentTimeMillis(),
                            status = 2,
                            fileSizeBytes = targetFile.length(),
                            filePath = targetFile.absolutePath
                        )
                    } else {
                        Log.w("DownloadManager", "Muxing failed, fallback to video-only track")
                        if (tempVideoFile.exists() && tempVideoFile.length() > 0) {
                            if (targetFile.exists()) targetFile.delete()
                            tempVideoFile.renameTo(targetFile)
                            tempAudioFile.delete()
                            db.downloadDao().updateCompletedDetails(
                                videoId = video.id,
                                completedAt = System.currentTimeMillis(),
                                status = 2,
                                fileSizeBytes = targetFile.length(),
                                filePath = targetFile.absolutePath
                            )
                        } else {
                            throw IllegalStateException("Muxing failed and no fallback video available")
                        }
                    }
                } else {
                    // 3. Progressive single video stream
                    Log.d("DownloadManager", "Starting progressive video download for ${video.id}: $streamUrl")
                    val success = downloadStreamToFile(streamUrl, targetFile) { percent ->
                        updateProgress(video.id, percent)
                    }
                    if (!success) throw IllegalStateException("Failed to download progressive stream")

                    db.downloadDao().updateCompletedDetails(
                        videoId = video.id,
                        completedAt = System.currentTimeMillis(),
                        status = 2,
                        fileSizeBytes = targetFile.length(),
                        filePath = targetFile.absolutePath
                    )
                }

                updateProgressState(video.id, 100)
                Log.d("DownloadManager", "Download successfully completed: ${video.title} (${targetFile.length()} bytes)")
            } catch (e: Exception) {
                if (e is CancellationException) {
                    Log.d("DownloadManager", "Download cancelled for ${video.id}")
                } else {
                    Log.e("DownloadManager", "Download failed for ${video.id}", e)
                    db.downloadDao().updateProgress(video.id, 0, 3) // 3 = failed
                }
                if (targetFile.exists()) targetFile.delete()
                if (tempVideoFile.exists()) tempVideoFile.delete()
                if (tempAudioFile.exists()) tempAudioFile.delete()
            } finally {
                removeProgressState(video.id)
                activeJobs.remove(video.id)
            }
        }

        activeJobs[video.id] = job
    }

    private suspend fun downloadStreamToFile(
        url: String,
        destFile: File,
        onProgress: suspend (Int) -> Unit
    ): Boolean {
        var input: InputStream? = null
        var output: FileOutputStream? = null

        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e("DownloadManager", "HTTP ${response.code} downloading $url")
                response.close()
                return false
            }

            val body = response.body ?: run {
                response.close()
                return false
            }

            val totalBytes = body.contentLength()
            input = body.byteStream()
            output = FileOutputStream(destFile)

            val buffer = ByteArray(32768) // 32KB buffer for fast transfer
            var bytesRead: Int
            var totalRead = 0L
            var lastPercent = -1

            while (input.read(buffer).also { bytesRead = it } != -1) {
                if (!coroutineContext.isActive) {
                    response.close()
                    return false
                }
                output.write(buffer, 0, bytesRead)
                totalRead += bytesRead
                if (totalBytes > 0) {
                    val currentPercent = ((totalRead * 100) / totalBytes).toInt()
                    if (currentPercent != lastPercent) {
                        lastPercent = currentPercent
                        onProgress(currentPercent)
                    }
                }
            }

            output.flush()
            response.close()
            true
        } catch (e: Exception) {
            Log.e("DownloadManager", "Error downloading stream to ${destFile.name}", e)
            false
        } finally {
            try { input?.close() } catch (_: Exception) {}
            try { output?.close() } catch (_: Exception) {}
        }
    }

    private fun muxVideoAndAudio(videoFile: File, audioFile: File, outputFile: File): Boolean {
        var videoExtractor: MediaExtractor? = null
        var audioExtractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null

        return try {
            videoExtractor = MediaExtractor().apply { setDataSource(videoFile.absolutePath) }
            audioExtractor = MediaExtractor().apply { setDataSource(audioFile.absolutePath) }

            var videoTrackIndex = -1
            for (i in 0 until videoExtractor.trackCount) {
                val format = videoExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoTrackIndex = i
                    break
                }
            }

            var audioTrackIndex = -1
            for (i in 0 until audioExtractor.trackCount) {
                val format = audioExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    break
                }
            }

            if (videoTrackIndex < 0) {
                Log.e("DownloadManager", "No video track found in ${videoFile.name}")
                return false
            }

            if (outputFile.exists()) {
                outputFile.delete()
            }

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            videoExtractor.selectTrack(videoTrackIndex)
            val videoFormat = videoExtractor.getTrackFormat(videoTrackIndex)
            val muxerVideoTrack = muxer.addTrack(videoFormat)

            var muxerAudioTrack = -1
            if (audioTrackIndex >= 0) {
                audioExtractor.selectTrack(audioTrackIndex)
                val audioFormat = audioExtractor.getTrackFormat(audioTrackIndex)
                muxerAudioTrack = muxer.addTrack(audioFormat)
            }

            muxer.start()

            val maxVideoSize = try {
                videoFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            } catch (_: Exception) { 1024 * 1024 }
            val buffer = ByteBuffer.allocate(maxOf(maxVideoSize, 1024 * 1024))
            val bufferInfo = MediaCodec.BufferInfo()

            // Write video track samples
            var lastVideoPts = 0L
            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = videoExtractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) break
                val pts = videoExtractor.sampleTime
                bufferInfo.presentationTimeUs = if (pts >= lastVideoPts) pts else lastVideoPts + 1000L
                lastVideoPts = bufferInfo.presentationTimeUs
                bufferInfo.flags = videoExtractor.sampleFlags
                muxer.writeSampleData(muxerVideoTrack, buffer, bufferInfo)
                videoExtractor.advance()
            }

            // Write audio track samples
            if (muxerAudioTrack >= 0) {
                var lastAudioPts = 0L
                while (true) {
                    bufferInfo.offset = 0
                    bufferInfo.size = audioExtractor.readSampleData(buffer, 0)
                    if (bufferInfo.size < 0) break
                    val pts = audioExtractor.sampleTime
                    bufferInfo.presentationTimeUs = if (pts >= lastAudioPts) pts else lastAudioPts + 1000L
                    lastAudioPts = bufferInfo.presentationTimeUs
                    bufferInfo.flags = audioExtractor.sampleFlags
                    muxer.writeSampleData(muxerAudioTrack, buffer, bufferInfo)
                    audioExtractor.advance()
                }
            }

            muxer.stop()
            true
        } catch (e: Exception) {
            Log.e("DownloadManager", "MediaMuxer exception", e)
            false
        } finally {
            try { videoExtractor?.release() } catch (_: Exception) {}
            try { audioExtractor?.release() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
        }
    }

    private suspend fun updateProgress(videoId: String, percent: Int) {
        updateProgressState(videoId, percent)
        db.downloadDao().updateProgress(videoId, percent, 1)
    }

    private fun updateProgressState(videoId: String, percent: Int) {
        val current = _downloadProgress.value.toMutableMap()
        current[videoId] = percent
        _downloadProgress.value = current
    }

    private fun removeProgressState(videoId: String) {
        val current = _downloadProgress.value.toMutableMap()
        current.remove(videoId)
        _downloadProgress.value = current
    }

    fun cancelDownload(videoId: String) {
        val job = activeJobs.remove(videoId)
        job?.cancel()
        removeProgressState(videoId)
        scope.launch {
            val dir = getDownloadsDir()
            listOf(
                "${videoId}.mp4",
                "${videoId}.m4a",
                "${videoId}.mp3",
                "${videoId}.webm",
                "${videoId}_temp_video.mp4",
                "${videoId}_temp_audio.m4a"
            ).forEach { name ->
                val f = File(dir, name)
                if (f.exists()) f.delete()
            }
            db.downloadDao().delete(videoId)
        }
    }

    fun deleteDownload(videoId: String) {
        cancelDownload(videoId)
    }
}
