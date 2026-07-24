package ani.sanin.parsers

import ani.sanin.Mapper
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import kotlinx.serialization.json.*

class ReAnimeProvider : NativeAnimeParser() {

    override val name = "ReAnime"
    override val saveName = "reanime"
    override val baseUrl = "https://reanime.to"
    override fun isDubAvailableSeparately(sourceLang: Int?): Boolean = true

    private val api = "$baseUrl/api"

    override suspend fun search(query: String): List<ShowResponse> {
        val json = get("$api/v1/search?q=${encode(query)}&limit=20&offset=0", "$baseUrl/")
        val root = Mapper.json.parseToJsonElement(json).jsonObject
        val results = (root["results"] as? JsonArray).orEmpty()
        return results.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val animeId = item.string("anime_id") ?: return@mapNotNull null
            val title = item["title"] as? JsonObject
            val name = title?.string("english") ?: title?.string("romaji") ?: title?.string("native") ?: animeId
            ShowResponse(name = name, link = animeId, coverUrl = defaultImage, extra = mutableMapOf("anime_id" to animeId))
        }
    }

    override suspend fun autoSearch(mediaObj: ani.sanin.media.Media): ShowResponse? {
        val titles = listOfNotNull(mediaObj.name, mediaObj.nameRomaji)
            .filter(String::isNotBlank).distinct()
        val candidates = titles.flatMap { title ->
            runCatching { search(title) }.getOrDefault(emptyList())
        }.distinctBy { it.extra?.get("anime_id") }
        return candidates.maxByOrNull { candidate ->
            titles.maxOfOrNull { title -> bigramScore(title, candidate.name.orEmpty()) } ?: 0.0
        }?.takeIf {
            titles.maxOfOrNull { title -> bigramScore(title, it.name.orEmpty()) } ?: 0.0 >= 0.2
        }
    }

    private fun bigramScore(a: String, b: String): Double {
        val sa = a.lowercase().replace(Regex("[^a-z0-9]"), "")
        val sb = b.lowercase().replace(Regex("[^a-z0-9]"), "")
        if (sa == sb) return 1.0
        if (sa in sb || sb in sa) return 0.86
        if (sa.length < 2 || sb.length < 2) return 0.0
        val counts = HashMap<String, Int>()
        for (i in 0 until sa.length - 1) { val p = sa.substring(i, i + 2); counts[p] = (counts[p] ?: 0) + 1 }
        var hits = 0
        for (i in 0 until sb.length - 1) {
            val p = sb.substring(i, i + 2)
            val c = counts[p] ?: 0
            if (c > 0) { hits++; counts[p] = c - 1 }
        }
        return (2.0 * hits) / ((sa.length - 1) + (sb.length - 1))
    }

    override suspend fun loadEpisodes(animeLink: String, extra: Map<String, String>?, sAnime: SAnime): List<Episode> {
        val animeId = extra?.get("anime_id") ?: return emptyList()
        val json = get("$api/v1/info?id=${encode(animeId)}", "$baseUrl/")
        val root = Mapper.json.parseToJsonElement(json).jsonObject
        val sub = root.number("subbed")?.toInt() ?: 0
        val dub = root.number("dubbed")?.toInt() ?: 0
        val episodes = if (selectDub) (1..dub).toSet() else (1..sub).toSet()
        return episodes.map { Episode(number = it.toString(), link = animeId, extra = extra) }
    }

    override suspend fun loadVideoServers(episodeLink: String, extra: Map<String, String>?, sEpisode: SEpisode): List<VideoServer> {
        val animeId = extra?.get("anime_id") ?: return emptyList()
        val episodeNum = sEpisode.episode_number.toInt()
        val audio = if (selectDub) "dub" else "sub"

        val flix = runCatching {
            val json = get("$api/flix/$animeId/$episodeNum", "$baseUrl/")
            Mapper.json.parseToJsonElement(json).jsonObject
        }.getOrNull()

        val links = (flix?.get("servers") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val accepted = if (audio == "dub") setOf("dub", "s-dub") else setOf("sub", "s-sub")

        val servers = mutableListOf<VideoServer>()
        links.mapNotNull { link ->
            val kind = link.string("dataType")?.lowercase() ?: return@mapNotNull null
            if (kind !in accepted) return@mapNotNull null
            var url = link.string("dataLink") ?: return@mapNotNull null
            if (audio == "dub" && !url.contains(Regex("""[?&]a="""))) {
                url += if ('?' in url) "&a=1" else "?a=1"
            }
            link.string("serverName")?.let { url to it }
        }.distinctBy { it.first }.take(4).forEach { (url, name) ->
            servers += VideoServer("ReAnime $name", url, mapOf("referer" to "$baseUrl/", "type" to "embed"))
        }
        return servers
    }

    private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.number(name: String): Double? = (this[name] as? JsonPrimitive)?.doubleOrNull

    override var selectDub: Boolean = false
}
