package ani.sanin.parsers

import ani.sanin.FileUrl
import ani.sanin.Mapper
import ani.sanin.okHttpClient
import eu.kanade.tachiyomi.animesource.model.ChapterType
import eu.kanade.tachiyomi.animesource.model.TimeStamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.Request
import java.net.URI

class NativeVideoExtractor(override val server: VideoServer) : VideoExtractor() {

    override suspend fun extract(): VideoContainer {
        val url = server.embed.url
        var headers = server.embed.headers
        val extraData = server.extraData
        if (extraData != null && headers.isEmpty()) {
            val ref = extraData["referer"]
            if (!ref.isNullOrBlank()) headers = mapOf("Referer" to ref)
        }

        val videos = mutableListOf(
            Video(
                quality = null,
                format = VideoType.M3U8,
                file = FileUrl(url, headers),
                size = null
            )
        )

        if (url.contains(".m3u8", ignoreCase = true)) {
            val parsed = parseHlsMaster(url, headers)
            videos.addAll(parsed)
        } else if (url.contains(".mpd", ignoreCase = true)) {
            // DASH — single entry is fine
        } else {
            // Container format — single entry is fine
        }

        val subtitles = parseSubtitles(server.extraData?.get("subtitles"))
        val timestamps = parseTimestamps(server.extraData)

        return VideoContainer(videos, subtitles, timestamps = timestamps)
    }

    private fun parseTimestamps(extraData: Map<String, String>?): List<TimeStamp> {
        val result = mutableListOf<TimeStamp>()
        extraData?.get("intro")?.let { json ->
            parseTimestamp(json, "intro", ChapterType.Opening)?.let { result.add(it) }
        }
        extraData?.get("outro")?.let { json ->
            parseTimestamp(json, "outro", ChapterType.Ending)?.let { result.add(it) }
        }
        return result
    }

    private fun parseTimestamp(jsonStr: String, name: String, type: ChapterType): TimeStamp? {
        return try {
            val obj = Mapper.json.parseToJsonElement(jsonStr) as? JsonObject ?: return null
            val start = (obj["start"] as? JsonPrimitive)
                ?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() } ?: return null
            val end = (obj["end"] as? JsonPrimitive)
                ?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() } ?: return null
            TimeStamp(start = start, end = end, name = name, type = type)
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun parseHlsMaster(masterUrl: String, headers: Map<String, String>): List<Video> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(masterUrl)
                    .header("User-Agent", NativeAnimeParser.USER_AGENT)
                    .apply { headers.forEach { (k, v) -> header(k, v) } }
                    .get().build()
                val body = okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext emptyList()
                    response.body?.string().orEmpty()
                }

                val baseUri = URI(masterUrl)
                val lines = body.lines()
                val videos = mutableListOf<Video>()
                var i = 0
                while (i < lines.size) {
                    val line = lines[i].trim()
                    if (line.startsWith("#EXT-X-STREAM-INF:", ignoreCase = true)) {
                        val quality = Regex("RESOLUTION=\\d+x(\\d+)", RegexOption.IGNORE_CASE)
                            .find(line)?.groupValues?.get(1)?.toIntOrNull()
                        val bw = Regex("BANDWIDTH=(\\d+)", RegexOption.IGNORE_CASE)
                            .find(line)?.groupValues?.get(1)?.toLongOrNull()
                        var j = i + 1
                        while (j < lines.size) {
                            val next = lines[j].trim()
                            if (next.isNotEmpty() && !next.startsWith("#")) {
                                val variantUrl = if (next.startsWith("http")) next
                                    else baseUri.resolve(next).toString()
                                videos.add(
                                    Video(
                                        quality = quality,
                                        format = VideoType.M3U8,
                                        file = FileUrl(variantUrl, headers),
                                        size = if (bw != null) bw.toDouble() else null
                                    )
                                )
                                break
                            }
                            j++
                        }
                        i = j
                    } else {
                        i++
                    }
                }

                videos.sortedByDescending { it.quality ?: 0 }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    private fun parseSubtitles(jsonStr: String?): List<Subtitle> {
        if (jsonStr.isNullOrBlank()) return emptyList()
        return try {
            val array = Mapper.json.parseToJsonElement(jsonStr) as? JsonArray ?: return emptyList()
            array.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val subUrl = (obj["url"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                val lang = (obj["language"] as? JsonPrimitive)?.contentOrNull ?: "Unknown"
                val typeStr = (obj["type"] as? JsonPrimitive)?.contentOrNull
                val type = when (typeStr?.lowercase()) {
                    "ass", "ssa" -> SubtitleType.ASS
                    "srt" -> SubtitleType.SRT
                    else -> SubtitleType.VTT
                }
                Subtitle(language = lang, file = FileUrl(subUrl), type = type)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
