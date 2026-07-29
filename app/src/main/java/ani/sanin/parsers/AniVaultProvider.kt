package ani.sanin.parsers

import ani.sanin.Mapper
import ani.sanin.media.Media
import ani.sanin.util.Logger
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

class AniVaultProvider : NativeAnimeParser() {

    override val name = "AniVault"
    override val saveName = "AniVault"
    override fun isDubAvailableSeparately(sourceLang: Int?): Boolean = true

    override val baseUrl = "https://anivault-scraper.vercel.app"

    override suspend fun autoSearch(mediaObj: Media): ShowResponse? {
        val saved = loadSavedShowResponse(mediaObj.id)
        if (saved != null) return saved
        val response = ShowResponse(
            name = mediaObj.mainName(),
            link = saveName,
            coverUrl = mediaObj.cover ?: defaultImage,
            extra = mutableMapOf(
                "anilist_id" to mediaObj.id.toString()
            )
        )
        saveShowResponse(mediaObj.id, response)
        return response
    }

    override suspend fun loadEpisodes(animeLink: String, extra: Map<String, String>?, sAnime: SAnime): List<Episode> {
        val anilistId = extra?.get("anilist_id")?.toIntOrNull()
        if (anilistId == null || anilistId <= 0) return emptyList()
        return withContext(Dispatchers.IO) {
            try {
                val jsonStr = get("$baseUrl/api/episodes?anilistId=$anilistId&source=anikoto")
                val obj = Mapper.json.parseToJsonElement(jsonStr) as? JsonObject ?: return@withContext emptyList()
                val array = obj["episodes"] as? JsonArray ?: return@withContext emptyList()
                array.mapNotNull { element ->
                    val ep = element as? JsonObject ?: return@mapNotNull null
                    val number = (ep["num"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
                    val title = (ep["title"] as? JsonPrimitive)?.contentOrNull
                    Episode(
                        number = number.toString(),
                        link = number.toString(),
                        title = title?.takeIf { it.isNotBlank() },
                        extra = extra
                    )
                }
            } catch (e: Exception) {
                Logger.log("AniVault loadEpisodes error: ${e.message}")
                emptyList()
            }
        }
    }

    override suspend fun loadVideoServers(episodeLink: String, extra: Map<String, String>?, sEpisode: SEpisode): List<VideoServer> {
        val anilistId = extra?.get("anilist_id")?.toIntOrNull()
        val episodeNum = episodeLink.toIntOrNull()
        if (anilistId == null || anilistId <= 0 || episodeNum == null) return emptyList()
        return withContext(Dispatchers.IO) {
            try {
                val type = if (selectDub) "dub" else "sub"
                val jsonStr = get("$baseUrl/api/watch/anikoto/$anilistId/$episodeNum/$type")
                val obj = Mapper.json.parseToJsonElement(jsonStr) as? JsonObject ?: return@withContext emptyList()

                val servers = mutableListOf<VideoServer>()
                val m3u8 = (obj["m3u8"] as? JsonPrimitive)?.contentOrNull
                val embedUrl = (obj["embedUrl"] as? JsonPrimitive)?.contentOrNull
                val hlsProxyUrl = (obj["hlsProxyUrl"] as? JsonPrimitive)?.contentOrNull

                val extraData = mutableMapOf("referer" to "https://megaplay.buzz/")

                val subs = obj["subtitles"] as? JsonArray
                if (subs != null && subs.isNotEmpty()) {
                    val subJson = subs.mapNotNull { sub ->
                        val subObj = sub as? JsonObject ?: return@mapNotNull null
                        val url = (subObj["url"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                        val lang = (subObj["lang"] as? JsonPrimitive)?.contentOrNull ?: "Unknown"
                        val code = language(lang)
                        "{\"url\":\"${url.replace("\"", "\\\"")}\",\"language\":\"$code\",\"type\":\"vtt\"}"
                    }
                    if (subJson.isNotEmpty()) {
                        extraData["subtitles"] = "[${subJson.joinToString(",")}]"
                    }
                }

                val streamUrl = m3u8 ?: hlsProxyUrl ?: embedUrl
                if (!streamUrl.isNullOrBlank()) {
                    servers.add(VideoServer("AniVault", streamUrl, extraData))
                }

                if (!embedUrl.isNullOrBlank() && m3u8 != null) {
                    servers.add(VideoServer("AniVault Embed", embedUrl))
                }

                servers
            } catch (e: Exception) {
                Logger.log("AniVault loadVideoServers error: ${e.message}")
                emptyList()
            }
        }
    }
}
