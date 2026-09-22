package vn.lobie.mytube.data.remote.musicbrainz

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import vn.lobie.mytube.core.common.AppLogger
import vn.lobie.mytube.domain.model.ArtistInfo
import vn.lobie.mytube.domain.model.MusicMetadata
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class MusicBrainzClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val metadataCache = ConcurrentHashMap<String, MusicMetadata>()
    private val artistCache = ConcurrentHashMap<String, ArtistInfo>()

    companion object {
        private const val USER_AGENT = "MyTube/2.0 ( https://github.com/ngoloc2k4/MyTube; contact@lobie.vn )"
        private const val MB_BASE = "https://musicbrainz.org/ws/2"
        private const val CAA_BASE = "https://coverartarchive.org/release"
        private const val WIKI_BASE = "https://en.wikipedia.org/api/rest_v1/page/summary"
    }

    /**
     * Chuẩn hóa tên video YouTube để bóc tách Tên nghệ sĩ và Tên bài hát.
     * Ví dụ: "Sơn Tùng M-TP - Nơi Này Có Anh [Official Music Video]" -> ("Sơn Tùng M-TP", "Nơi Này Có Anh")
     */
    fun cleanTrackAndArtist(rawTitle: String, channelName: String): Pair<String, String> {
        var clean = rawTitle
            .replace(Regex("(?i)\\[(official\\s+)?(music\\s+)?video\\]"), "")
            .replace(Regex("(?i)\\((official\\s+)?(music\\s+)?video\\)"), "")
            .replace(Regex("(?i)\\[mv\\]|\\(mv\\)"), "")
            .replace(Regex("(?i)\\[lyrics?\\]|\\(lyrics?\\)"), "")
            .replace(Regex("(?i)\\[audio\\]|\\(audio\\)"), "")
            .replace(Regex("(?i)\\[visualizer\\]|\\(visualizer\\)"), "")
            .replace(Regex("(?i)\\[(4k|hd|fhd|uhd)\\]|\\((4k|hd|fhd|uhd)\\)"), "")
            .replace(Regex("(?i)\\b(4k|hd|fhd|uhd)\\b"), "")
            .replace(Regex("(?i)\\|\\s*official\\s*.*$"), "")
            .replace(Regex("(?i)\\|\\s*audio.*$"), "")
            .trim()

        // Phân tách nếu có dấu '-' hoặc ':'
        return if (clean.contains(" - ")) {
            val parts = clean.split(" - ", limit = 2)
            val artist = parts[0].trim()
            val track = parts[1].trim()
            Pair(artist, track)
        } else if (clean.contains(" – ")) { // en-dash
            val parts = clean.split(" – ", limit = 2)
            Pair(parts[0].trim(), parts[1].trim())
        } else {
            // Không có dấu phân cách: dùng channelName làm nghệ sĩ và clean làm tên bài
            val artist = channelName
                .replace(Regex("(?i)\\s*-\\s*topic$"), "")
                .replace(Regex("(?i)\\s*official$"), "")
                .trim()
            Pair(artist, clean)
        }
    }

    /**
     * Tra cứu thông tin bài hát và ảnh bìa chất lượng cao từ MusicBrainz & Cover Art Archive.
     */
    suspend fun getEnrichedMetadata(rawTitle: String, channelName: String): Result<MusicMetadata> = withContext(Dispatchers.IO) {
        val (artist, track) = cleanTrackAndArtist(rawTitle, channelName)
        val cacheKey = "$artist||$track".lowercase()
        metadataCache[cacheKey]?.let { return@withContext Result.success(it) }

        try {
            val query = StringBuilder()
            val cleanTrack = track.replace("\"", "")
            val cleanArtist = artist.replace("\"", "")
            if (cleanArtist.isNotBlank() && cleanTrack.isNotBlank()) {
                query.append("recording:\"$cleanTrack\" AND artist:\"$cleanArtist\"")
            } else {
                query.append(cleanTrack.ifBlank { cleanArtist })
            }

            val encodedQuery = URLEncoder.encode(query.toString(), "UTF-8")
            val url = "$MB_BASE/recording/?query=$encodedQuery&fmt=json&limit=1"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("MusicBrainz returned ${response.code}"))
            }

            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty body"))
            val root = json.parseToJsonElement(body).jsonObject
            val recordings = root["recordings"]?.jsonArray ?: JsonArray(emptyList())

            if (recordings.isEmpty()) {
                val fallback = MusicMetadata(
                    trackTitle = track,
                    artistName = artist
                )
                metadataCache[cacheKey] = fallback
                return@withContext Result.success(fallback)
            }

            val firstRec = recordings[0].jsonObject
            val mbid = firstRec["id"]?.jsonPrimitive?.content ?: ""
            val foundTitle = firstRec["title"]?.jsonPrimitive?.content ?: track

            var artistName = artist
            var artistMbid = ""
            val artistCredit = firstRec["artist-credit"]?.jsonArray
            if (!artistCredit.isNullOrEmpty()) {
                val firstArtist = artistCredit[0].jsonObject
                artistName = firstArtist["name"]?.jsonPrimitive?.content ?: artist
                artistMbid = firstArtist["artist"]?.jsonObject?.get("id")?.jsonPrimitive?.content ?: ""
            }

            var albumTitle = ""
            var releaseMbid = ""
            var releaseYear = ""
            val releases = firstRec["releases"]?.jsonArray
            if (!releases.isNullOrEmpty()) {
                val firstRel = releases[0].jsonObject
                albumTitle = firstRel["title"]?.jsonPrimitive?.content ?: ""
                releaseMbid = firstRel["id"]?.jsonPrimitive?.content ?: ""
                val relDate = firstRel["date"]?.jsonPrimitive?.content ?: ""
                releaseYear = relDate.take(4)
            }

            val genres = mutableListOf<String>()
            val tags = firstRec["tags"]?.jsonArray
            tags?.forEach { t ->
                val name = t.jsonObject["name"]?.jsonPrimitive?.content
                if (!name.isNullOrBlank()) genres.add(name.replaceFirstChar { it.uppercase() })
            }

            val coverArtUrl = if (releaseMbid.isNotBlank()) {
                "$CAA_BASE/$releaseMbid/front-500"
            } else ""

            val metadata = MusicMetadata(
                mbid = mbid,
                trackTitle = foundTitle,
                artistName = artistName,
                artistMbid = artistMbid,
                albumTitle = albumTitle,
                releaseMbid = releaseMbid,
                releaseYear = releaseYear,
                coverArtUrl = coverArtUrl,
                genres = genres
            )

            metadataCache[cacheKey] = metadata
            AppLogger.d("MusicBrainz", "Enriched metadata for '$track' by '$artistName' (Release MBID: $releaseMbid)")
            Result.success(metadata)
        } catch (e: Exception) {
            AppLogger.w("MusicBrainz", "Failed to enrich metadata: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Tra cứu thông tin tiểu sử nghệ sĩ từ MusicBrainz + tóm tắt Wikipedia.
     */
    suspend fun getArtistInfo(artistName: String, artistMbid: String? = null): Result<ArtistInfo> = withContext(Dispatchers.IO) {
        val cacheKey = (artistMbid ?: artistName).lowercase()
        artistCache[cacheKey]?.let { return@withContext Result.success(it) }

        try {
            var mbid = artistMbid ?: ""
            var name = artistName
            var type = ""
            var country = ""
            var lifeSpan = ""
            val tags = mutableListOf<String>()

            if (mbid.isNotBlank()) {
                val url = "$MB_BASE/artist/$mbid?fmt=json"
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    val obj = json.parseToJsonElement(body).jsonObject
                    name = obj["name"]?.jsonPrimitive?.content ?: name
                    type = obj["type"]?.jsonPrimitive?.content ?: ""
                    country = obj["country"]?.jsonPrimitive?.content ?: ""
                    val lifeObj = obj["life-span"]?.jsonObject
                    val begin = lifeObj?.get("begin")?.jsonPrimitive?.content
                    val end = lifeObj?.get("end")?.jsonPrimitive?.content
                    val ended = lifeObj?.get("ended")?.jsonPrimitive?.booleanOrNull ?: false
                    lifeSpan = if (begin != null) {
                        if (ended && end != null) "$begin – $end" else "từ $begin"
                    } else ""
                }
            }

            // Lấy tóm tắt tiểu sử từ Wikipedia
            var bio = ""
            try {
                val wikiUrl = "$WIKI_BASE/${URLEncoder.encode(name, "UTF-8")}"
                val wikiReq = Request.Builder()
                    .url(wikiUrl)
                    .header("User-Agent", USER_AGENT)
                    .build()
                val wikiResp = client.newCall(wikiReq).execute()
                if (wikiResp.isSuccessful) {
                    val wikiBody = wikiResp.body?.string() ?: ""
                    val wikiObj = json.parseToJsonElement(wikiBody).jsonObject
                    bio = wikiObj["extract"]?.jsonPrimitive?.content ?: ""
                }
            } catch (_: Exception) {}

            val artistInfo = ArtistInfo(
                mbid = mbid,
                name = name,
                type = type,
                country = country,
                lifeSpan = lifeSpan,
                biography = bio,
                tags = tags
            )
            artistCache[cacheKey] = artistInfo
            Result.success(artistInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
