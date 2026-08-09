package ani.sanin.parsers

import ani.sanin.Mapper
import ani.sanin.media.Media
import ani.sanin.util.Logger
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.net.URLEncoder

class AniVaultProvider : NativeAnimeParser() {

    override val name = "AniVault"
    override val saveName = "AniVault"
    override fun isDubAvailableSeparately(sourceLang: Int?): Boolean = true

    override val defaultBaseUrl = "https://anivault-scraper.vercel.app"

    private val currentSource: String
        get() = providerSource(saveName) ?: "anikoto"

    override val knownServers: List<String>
        get() = when (currentSource) {
            "animeheaven" -> listOf("AnimeHeaven")
            "senshi" -> listOf("Senshi", "StreamNin", "FileMoon")
            "anikoto" -> listOf("HD-1", "Vidstream-2", "Kiwi Stream (sub)")
            else -> emptyList()
        }

    override fun setupPreferenceScreen(screen: eu.kanade.tachiyomi.PreferenceScreen) {
        super.setupPreferenceScreen(screen)
        addProviderSourcePreference(
            screen,
            entries = arrayOf("Anikoto", "AnimeHeaven", "Miruro", "Senshi"),
            values = arrayOf("anikoto", "animeheaven", "miruro", "senshi"),
            default = "anikoto"
        )
    }

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
                val jsonStr = get("$baseUrl/api/episodes?anilistId=$anilistId&source=$currentSource")
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
                val servers = mutableListOf<VideoServer>()
                val seenStreams = mutableSetOf<String>()

                suspend fun fetchType(type: String) {
                    val watch = fetchWatch(anilistId, episodeNum, type, null) ?: return
                    val defaultName = (watch["server"] as? JsonPrimitive)?.contentOrNull
                    for (name in serverNames(watch)) {
                        val perServer = if (name == defaultName) watch
                            else fetchWatch(anilistId, episodeNum, type, name) ?: continue
                        val m3u8 = (perServer["m3u8"] as? JsonPrimitive)?.contentOrNull
                        val hlsProxyUrl = (perServer["hlsProxyUrl"] as? JsonPrimitive)?.contentOrNull
                        val embedUrl = (perServer["embedUrl"] as? JsonPrimitive)?.contentOrNull
                        val streamUrl = m3u8 ?: hlsProxyUrl ?: embedUrl
                        if (streamUrl.isNullOrBlank()) continue
                        // The backend serves the same stream under several labels; dedupe them.
                        if (!seenStreams.add(streamUrl)) continue
                        servers.add(buildServer(name, type, streamUrl, perServer))
                    }
                }

                // Dub mode: list dub servers only, and fall back to sub when the episode has no dub.
                // Sub mode: sub servers first, then dub.
                val requested = if (selectDub) listOf("dub") else listOf("sub", "dub")
                requested.forEach { fetchType(it) }
                if (selectDub && servers.isEmpty()) fetchType("sub")
                servers
            } catch (e: Exception) {
                Logger.log("AniVault loadVideoServers error: ${e.message}")
                emptyList()
            }
        }
    }

    private suspend fun fetchWatch(anilistId: Int, episodeNum: Int, type: String, server: String?): JsonObject? {
        val url = "$baseUrl/api/watch/$currentSource/$anilistId/$episodeNum/$type" +
            (server?.let { "?server=${URLEncoder.encode(it, "utf-8")}" } ?: "")
        return try {
            Mapper.json.parseToJsonElement(get(url)) as? JsonObject
        } catch (e: Exception) {
            null
        }
    }

    private fun serverNames(obj: JsonObject): List<String> {
        val list = (obj["availableServers"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?.filter { it.isNotBlank() }
        if (!list.isNullOrEmpty()) return list
        return listOfNotNull((obj["server"] as? JsonPrimitive)?.contentOrNull)
    }

    private suspend fun buildServer(name: String, type: String, streamUrl: String, obj: JsonObject): VideoServer {
        val extraData = mutableMapOf(
            "referer" to "https://megaplay.buzz/",
            "audio" to type.uppercase()
        )
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
        // Pass AniVault skip timestamps (when present) through to the player skip system
        obj["intro"]?.let { if (it is JsonObject || it is JsonPrimitive) extraData["intro"] = it.toString() }
        obj["outro"]?.let { if (it is JsonObject || it is JsonPrimitive) extraData["outro"] = it.toString() }
        if (streamUrl.contains(".m3u8", ignoreCase = true)) {
            resolveMasterQuality(streamUrl)?.let { extraData["quality"] = it }
        }
        return VideoServer(name, streamUrl, extraData)
    }

    private val qualityCache = mutableMapOf<String, String?>()

    private suspend fun resolveMasterQuality(masterUrl: String): String? {
        if (qualityCache.containsKey(masterUrl)) return qualityCache[masterUrl]
        val label = try {
            val body = get(masterUrl, mapOf("Referer" to "https://megaplay.buzz/"))
            val heights = body.lineSequence()
                .map { it.trim() }
                .filter { it.startsWith("#EXT-X-STREAM-INF:", ignoreCase = true) }
                .mapNotNull {
                    Regex("RESOLUTION=\\d+x(\\d+)", RegexOption.IGNORE_CASE)
                        .find(it)?.groupValues?.get(1)?.toIntOrNull()
                }
                .toList()
            when {
                heights.size >= 2 -> {
                    val min = heights.min()
                    val max = heights.max()
                    "Multi \u00b7 ${min}-${max}p"
                }
                heights.size == 1 -> "${heights.first()}p"
                else -> null
            }
        } catch (e: Exception) {
            null
        }
        qualityCache[masterUrl] = label
        return label
    }
}
