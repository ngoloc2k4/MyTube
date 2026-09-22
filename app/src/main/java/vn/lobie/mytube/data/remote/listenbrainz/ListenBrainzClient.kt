package vn.lobie.mytube.data.remote.listenbrainz

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import vn.lobie.mytube.core.common.AppLogger
import java.util.concurrent.TimeUnit

class ListenBrainzClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val BASE_URL = "https://api.listenbrainz.org/1"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    /**
     * Xác thực token cá nhân ListenBrainz. Trả về tên người dùng nếu hợp lệ.
     */
    suspend fun validateToken(token: String): Result<String> = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext Result.failure(IllegalArgumentException("Token is empty"))
        try {
            val request = Request.Builder()
                .url("$BASE_URL/validate-token")
                .header("Authorization", "Token $token")
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            val obj = json.parseToJsonElement(body).jsonObject
            val isValid = obj["valid"]?.jsonPrimitive?.booleanOrNull ?: false
            if (isValid) {
                val userName = obj["user_name"]?.jsonPrimitive?.content ?: "User"
                Result.success(userName)
            } else {
                val msg = obj["message"]?.jsonPrimitive?.content ?: "Token không hợp lệ"
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Gửi bản ghi nghe nhạc (Scrobble) lên ListenBrainz.
     */
    suspend fun submitListen(
        token: String,
        artistName: String,
        trackName: String,
        releaseName: String = "",
        listenedAt: Long = System.currentTimeMillis() / 1000
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext Result.failure(IllegalArgumentException("Token is empty"))
        if (artistName.isBlank() || trackName.isBlank()) return@withContext Result.failure(IllegalArgumentException("Artist or track is empty"))

        try {
            val trackMeta = buildJsonObject {
                put("artist_name", artistName)
                put("track_name", trackName)
                if (releaseName.isNotBlank()) {
                    put("release_name", releaseName)
                }
            }

            val payloadItem = buildJsonObject {
                put("listened_at", listenedAt)
                put("track_metadata", trackMeta)
            }

            val root = buildJsonObject {
                put("listen_type", "single_listen")
                put("payload", buildJsonArray { add(payloadItem) })
            }

            val requestBody = root.toString().toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url("$BASE_URL/submit-listens")
                .header("Authorization", "Token $token")
                .header("Content-Type", "application/json")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                AppLogger.i("ListenBrainz", "Scrobbled: '$trackName' by '$artistName'")
                Result.success(Unit)
            } else {
                val err = "ListenBrainz submit failed: HTTP ${response.code}"
                AppLogger.w("ListenBrainz", err)
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            AppLogger.w("ListenBrainz", "Scrobble error: ${e.message}")
            Result.failure(e)
        }
    }
}
