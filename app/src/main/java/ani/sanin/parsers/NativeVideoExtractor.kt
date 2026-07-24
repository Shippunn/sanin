package ani.sanin.parsers

import ani.sanin.FileUrl
import ani.sanin.Mapper
import kotlinx.serialization.json.*

class NativeVideoExtractor(override val server: VideoServer) : VideoExtractor() {

    override suspend fun extract(): VideoContainer {
        val url = server.embed.url
        var headers = server.embed.headers
        val extraData = server.extraData
        if (extraData != null && headers.isEmpty()) {
            val ref = extraData["referer"]
            if (!ref.isNullOrBlank()) headers = mapOf("Referer" to ref)
        }

        val format = when {
            url.contains(".m3u8", ignoreCase = true) -> VideoType.M3U8
            url.contains(".mpd", ignoreCase = true) -> VideoType.DASH
            else -> VideoType.CONTAINER
        }

        val videos = listOf(
            Video(
                quality = null,
                format = format,
                file = FileUrl(url, headers),
                size = null
            )
        )

        val subtitles = parseSubtitles(server.extraData?.get("subtitles"))

        return VideoContainer(videos, subtitles)
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
                Subtitle(language = lang, file = subUrl, type = type)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
