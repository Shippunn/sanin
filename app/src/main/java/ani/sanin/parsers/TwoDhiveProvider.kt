package ani.sanin.parsers

import ani.sanin.Mapper
import kotlinx.serialization.json.*

class TwoDhiveProvider : NativeAnimeParser() {

    override val name = "2Dhive"
    override val saveName = "2dhive"
    override val baseUrl = "https://2dhive.com"
    override fun isDubAvailableSeparately(sourceLang: Int?): Boolean = true

    override suspend fun search(query: String): List<ShowResponse> = emptyList()

    override suspend fun autoSearch(mediaObj: ani.sanin.media.Media): ShowResponse? {
        if (mediaObj.idMAL == null) return null
        return ShowResponse(
            name = mediaObj.mainName(),
            link = mediaObj.idMAL.toString(),
            coverUrl = defaultImage,
            extra = mutableMapOf("malId" to mediaObj.idMAL.toString())
        )
    }

    override suspend fun loadEpisodes(animeLink: String, extra: Map<String, String>?, sAnime: SAnime?): List<Episode> {
        val malId = extra?.get("malId")?.toIntOrNull() ?: return emptyList()
        val fallback = 100
        val referer = "$baseUrl/episode?anime=$malId&ep_num=1"
        val firstProps = runCatching { extractAstroProps(get(referer, mapOf("Referer" to referer))) }.getOrNull()
        val total = firstProps?.number("totalEpisodes")?.toInt()?.takeIf { it > 0 } ?: fallback

        suspend fun available(audio: String, episode: Int): Boolean {
            val currentProps = if (episode == 1) firstProps
                else runCatching { extractAstroProps(get("$baseUrl/episode?anime=$malId&ep_num=$episode", mapOf("Referer" to referer))) }.getOrNull()
            val has2dHive = (currentProps?.get("servers") as? JsonArray).orEmpty().any { element ->
                val server = element as? JsonObject
                val isDub = (server?.get("dub") as? JsonPrimitive)?.booleanOrNull ?: false
                isDub == (audio == "dub")
            }
            if (has2dHive) return true
            val page = runCatching {
                get("https://megaplay.buzz/stream/mal/$malId/$episode/$audio", mapOf("Referer" to "$baseUrl/"))
            }.getOrDefault("")
            return megaPlayPageAvailable(page)
        }

        suspend fun highestAvailable(limit: Int, available: suspend (Int) -> Boolean): Int {
            if (limit <= 0 || !available(1)) return 0
            if (limit == 1 || available(limit)) return limit
            var low = 1
            var high = limit - 1
            while (low < high) {
                val middle = (low + high + 1) / 2
                if (available(middle)) low = middle else high = middle - 1
            }
            return low
        }

        val subEps = highestAvailable(total) { available("sub", it) }
        val dubEps = highestAvailable(total) { available("dub", it) }
        val episodes = if (selectDub) (1..dubEps).toSet() else (1..subEps).toSet()
        return episodes.map { Episode(number = it.toString(), link = malId.toString(), extra = extra) }
    }

    override suspend fun loadVideoServers(episodeLink: String, extra: Map<String, String>?, sEpisode: SEpisode?): List<VideoServer> {
        val malId = extra?.get("malId")?.toIntOrNull() ?: return emptyList()
        val episodeNum = sEpisode?.number?.toInt() ?: return emptyList()
        val audio = if (selectDub) "dub" else "sub"
        val referer = "$baseUrl/episode?anime=$malId&ep_num=$episodeNum"

        val servers = mutableListOf<VideoServer>()
        val subtitles = mutableListOf<String>()
        val props = runCatching { extractAstroProps(get(referer, mapOf("Referer" to referer))) }.getOrNull()
        val matchingServers = (props?.get("servers") as? JsonArray).orEmpty().mapNotNull { element ->
            val server = element as? JsonObject ?: return@mapNotNull null
            val isDub = server["dub"]?.let { (it as? JsonPrimitive)?.booleanOrNull } ?: false
            if (!server.string("server_name").equals("HAdfree", true) || isDub != (audio == "dub")) return@mapNotNull null
            server.string("slug")
        }
        matchingServers.take(3).forEach { slug ->
            val direct = runCatching {
                val json = get("$baseUrl/api/hadfree?slug=${encode(slug)}", mapOf("Referer" to referer))
                Mapper.json.parseToJsonElement(json).jsonObject.string("streamUrl")
            }.getOrNull()
            if (!direct.isNullOrBlank()) {
                val type = if (direct.contains(".m3u8", true)) "hls" else "mp4"
                servers += VideoServer("2Dhive HAdfree", direct, mapOf("referer" to referer, "type" to type))
            }
        }

        if (audio == "sub") {
            val hiAnime = runCatching {
                val json = get("$baseUrl/api/hianime?mal_id=$malId&ep_num=$episodeNum", mapOf("Referer" to referer))
                Mapper.json.parseToJsonElement(json).jsonObject
            }.getOrNull()
            hiAnime?.string("m3u8")?.let { hls ->
                servers += VideoServer("2Dhive hiAnime", hls, mapOf("referer" to referer, "type" to "hls"))
            }
            hiAnime?.string("subtitle")?.let { subUrl ->
                subtitles += """{"url":"$subUrl","language":"en","type":"vtt"}"""
            }
        }

        val megaPlayEmbed = "https://megaplay.buzz/stream/mal/$malId/$episodeNum/$audio"
        val megaPlayPage = runCatching { get(megaPlayEmbed, mapOf("Referer" to referer)) }.getOrDefault("")
        val fileId = megaPlayPage.data("id")
        if (!fileId.isNullOrBlank()) {
            val origin = origin(megaPlayEmbed)
            val source = runCatching {
                val json = get("$origin/stream/getSources?id=${encode(fileId)}", mapOf("Referer" to "$origin/", "X-Requested-With" to "XMLHttpRequest"))
                Mapper.json.parseToJsonElement(json).jsonObject
            }.getOrNull()
            source?.string("sources")?.let { sourcesStr ->
                try {
                    val sourcesJson = Mapper.json.parseToJsonElement(sourcesStr).jsonArray
                    sourcesJson.forEach { elem ->
                        val obj = elem.jsonObject
                        obj.string("file")?.let { file ->
                            servers += VideoServer("2Dhive MegaPlay", file, mapOf("referer" to referer, "type" to "hls"))
                        }
                    }
                } catch (_: Exception) { }
            }
            (source?.get("tracks") as? JsonArray).orEmpty().forEach { track ->
                val obj = track as? JsonObject ?: return@forEach
                val kind = obj.string("kind")?.lowercase()
                if (kind != null && kind != "captions" && kind != "subtitles") return@forEach
                val file = obj.string("file") ?: return@forEach
                val label = obj.string("label") ?: "Subtitle"
                subtitles += """{"url":"$file","language":"${language(label)}","type":"vtt"}"""
            }
        }
        if (servers.isEmpty() && megaPlayPage.data("id") == null) return emptyList()

        return servers
    }

    private fun extractAstroProps(html: String): JsonObject? {
        val marker = html.indexOf("prefetchedHls")
        if (marker < 0) return null
        val valueStart = html.lastIndexOf("props=\"", marker).takeIf { it >= 0 }?.plus(7) ?: return null
        val valueEnd = html.indexOf('"', valueStart).takeIf { it > valueStart } ?: return null
        val raw = decodeEntities(html.substring(valueStart, valueEnd))
        val root = Mapper.json.parseToJsonElement(raw) as? JsonObject ?: return null
        return JsonObject(root.mapValues { (_, value) -> astroDecode(value) })
    }

    private fun astroDecode(value: JsonElement): JsonElement {
        if (value !is JsonArray || value.size < 2) {
            return if (value is JsonObject) JsonObject(value.mapValues { astroDecode(it.value) }) else value
        }
        val type = (value[0] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: return value
        return when (type) {
            0 -> if (value[1] is JsonObject) JsonObject((value[1] as JsonObject).mapValues { astroDecode(it.value) }) else value[1]
            1 -> if (value[1] is JsonArray) JsonArray((value[1] as JsonArray).map(::astroDecode)) else value[1]
            else -> value[1]
        }
    }

    private fun megaPlayPageAvailable(page: String): Boolean = page.isNotBlank() &&
        !Regex("""<title>\s*Error\b|\berror-container\b""", RegexOption.IGNORE_CASE).containsMatchIn(page) &&
        (page.data("id") != null || Regex("""<iframe\b[^>]*src=["'][^"']+["']""", RegexOption.IGNORE_CASE).containsMatchIn(page))

    private fun String.data(name: String): String? = Regex("""data-${Regex.escape(name)}=["']([^"']*)["']""", RegexOption.IGNORE_CASE)
        .find(this)?.groupValues?.get(1)?.let(::decodeEntities)

    private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.number(name: String): Double? = (this[name] as? JsonPrimitive)?.doubleOrNull

    override var selectDub: Boolean = false
}
