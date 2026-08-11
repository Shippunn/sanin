package ani.sanin.parsers

import android.content.Context
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import ani.sanin.currContext
import ani.sanin.media.Media
import ani.sanin.okHttpClient
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.util.Logger
import eu.kanade.tachiyomi.PreferenceScreen
import me.xdrop.fuzzywuzzy.FuzzySearch
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.net.URI

abstract class NativeAnimeParser : AnimeParser() {

    override val hostUrl: String
        get() = "https://${saveName.lowercase()}.localhost"

    open val defaultBaseUrl: String = ""

    val baseUrl: String
        get() = providerBaseUrl(saveName) ?: defaultBaseUrl

    /** Dub/sub preference is remembered per provider (falls back to the global default). */
    override var selectDub: Boolean
        get() = PrefManager.getProviderDub(saveName)
        set(value) {
            PrefManager.setProviderDub(saveName, value)
        }

    /** Server names the user can disable from this provider's settings screen. */
    open val knownServers: List<String> = emptyList()

    /**
     * Base screen shared by all native providers; providers may override to add
     * provider-specific preferences (call super first).
     */
    open fun setupPreferenceScreen(screen: PreferenceScreen) {
        val context: Context = currContext() ?: return

        screen.addPreference(SwitchPreferenceCompat(context).apply {
            key = "prefer_dub_$saveName"
            title = "Prefer Dub"
            summary = "Prefer dubbed audio when available"
            isChecked = selectDub
            setOnPreferenceChangeListener { _, newValue ->
                selectDub = newValue as Boolean
                true
            }
        })

        screen.addPreference(EditTextPreference(context).apply {
            key = "base_url_$saveName"
            title = "Base URL / Mirror"
            summary = providerBaseUrl(saveName) ?: defaultBaseUrl
            dialogTitle = "Base URL / Mirror"
            isPersistent = false
            setText(providerBaseUrl(saveName) ?: defaultBaseUrl)
            setOnPreferenceChangeListener { _, newValue ->
                val url = (newValue as? String)?.trim().orEmpty()
                saveProviderBaseUrl(saveName, url)
                summary = url.ifBlank { defaultBaseUrl }
                true
            }
        })

        screen.addPreference(SwitchPreferenceCompat(context).apply {
            key = "remember_quality_$saveName"
            title = "Remember my quality choice"
            summary = "Auto-play the last chosen quality for multi-quality servers"
            isChecked = PrefManager.getVal(PrefName.RememberQualityChoice)
            setOnPreferenceChangeListener { _, newValue ->
                PrefManager.setVal(PrefName.RememberQualityChoice, newValue as Boolean)
                true
            }
        })

        screen.addPreference(Preference(context).apply {
            key = "reset_quality_$saveName"
            title = "Reset saved quality choices"
            summary = "Clear the per-server quality preferences"
            setOnPreferenceClickListener {
                PrefManager.setVal(PrefName.PreferredQuality, emptyList<String>())
                summary = "Cleared"
                true
            }
        })

        if (knownServers.isNotEmpty()) {
            screen.addPreference(MultiSelectListPreference(context).apply {
                key = "disabled_servers_$saveName"
                title = "Disable servers"
                summary = "Disabled servers won't show in the server sheet"
                dialogTitle = "Disable servers"
                this.entries = knownServers.toTypedArray()
                this.entryValues = knownServers.toTypedArray()
                isPersistent = false
                values = disabledServers(saveName).toMutableSet()
                setOnPreferenceChangeListener { _, newValue ->
                    @Suppress("UNCHECKED_CAST")
                    saveDisabledServers(saveName, newValue as Set<String>)
                    true
                }
            })
        }
    }

    private fun providerBaseUrl(saveName: String): String? =
        PrefManager.getVal<List<String>>(PrefName.ProviderBaseUrls)
            .mapNotNull { entry ->
                entry.split('=', limit = 2)
                    .takeIf { it.size == 2 && it[0] == saveName }?.get(1)
            }
            .firstOrNull()
            ?.takeIf { it.isNotBlank() }

    private fun saveProviderBaseUrl(saveName: String, url: String) {
        val current = PrefManager.getVal<List<String>>(PrefName.ProviderBaseUrls)
            .filterNot { it.startsWith("$saveName=") }
        PrefManager.setVal(
            PrefName.ProviderBaseUrls,
            if (url.isBlank()) current else current + "$saveName=$url"
        )
    }

    private fun disabledServers(saveName: String): Set<String> =
        PrefManager.getVal<List<String>>(PrefName.ProviderDisabledServers)
            .mapNotNull { entry ->
                entry.split('=', limit = 2)
                    .takeIf { it.size == 2 && it[0] == saveName }?.get(1)
            }
            .toSet()

    private fun saveDisabledServers(saveName: String, servers: Set<String>) {
        val current = PrefManager.getVal<List<String>>(PrefName.ProviderDisabledServers)
            .filterNot { it.startsWith("$saveName=") }
        PrefManager.setVal(PrefName.ProviderDisabledServers, current + servers.map { "$saveName=$it" })
    }

    protected fun providerSource(saveName: String): String? =
        PrefManager.getVal<List<String>>(PrefName.ProviderSources)
            .mapNotNull { entry ->
                entry.split('=', limit = 2)
                    .takeIf { it.size == 2 && it[0] == saveName }?.get(1)
            }
            .firstOrNull()
            ?.takeIf { it.isNotBlank() }

    protected fun saveProviderSource(saveName: String, source: String) {
        val current = PrefManager.getVal<List<String>>(PrefName.ProviderSources)
            .filterNot { it.startsWith("$saveName=") }
        PrefManager.setVal(PrefName.ProviderSources, current + "$saveName=$source")
    }

    protected fun addProviderSourcePreference(
        screen: PreferenceScreen,
        entries: Array<String>,
        values: Array<String>,
        default: String
    ) {
        val context: Context = currContext() ?: return
        screen.addPreference(ListPreference(context).apply {
            key = "scraper_source_$saveName"
            title = "Scraper source"
            summary = providerSource(saveName) ?: default
            this.entries = entries
            this.entryValues = values
            value = providerSource(saveName) ?: default
            setOnPreferenceChangeListener { _, newValue ->
                saveProviderSource(saveName, newValue.toString())
                summary = newValue.toString()
                true
            }
        })
    }

    override suspend fun search(query: String): List<ShowResponse> = emptyList()

    override suspend fun getVideoExtractor(server: VideoServer): VideoExtractor {
        return NativeVideoExtractor(server)
    }

    protected fun get(url: String, referer: String? = null, accept: String = "application/json, */*"): String {
        val request = Request.Builder().url(url)
            .header("User-Agent", USER_AGENT)
            .apply { referer?.let { header("Referer", it) } }
            .header("Accept", accept)
            .get().build()
        okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
            return body
        }
    }

    protected fun get(url: String, headers: Map<String, String>): String {
        val request = Request.Builder().url(url)
            .header("User-Agent", USER_AGENT)
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .get().build()
        okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
            return body
        }
    }

    protected fun decodeEntities(value: String): String {
        var decoded = value
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
        decoded = Regex("&#(\\d+);").replace(decoded) { m ->
            m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
        }
        decoded = Regex("&#x([0-9a-fA-F]+);", RegexOption.IGNORE_CASE).replace(decoded) { m ->
            m.groupValues[1].toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
        }
        return decoded.replace("\\/", "/")
    }

    protected fun attr(tag: String, name: String): String {
        val escaped = Regex.escape(name)
        val quoted = Regex("\\b$escaped\\s*=\\s*([\"'])(.*?)\\1", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(tag)
            ?.groupValues
            ?.get(2)
        val raw = quoted ?: Regex("\\b$escaped\\s*=\\s*([^\"'\\s>]+)", RegexOption.IGNORE_CASE)
            .find(tag)
            ?.groupValues
            ?.get(1)
        return raw?.let(::decodeEntities).orEmpty()
    }

    protected fun hlsUrls(html: String): List<String> = Regex(
        "(?:https?:)?(?:\\\\/|/)[^\"'\\s<>]+?\\.m3u8[^\"'\\s<>]*",
        RegexOption.IGNORE_CASE,
    ).findAll(html)
        .map { decodeEntities(it.value).replace("\\/", "/") }
        .map { if (it.startsWith("//")) "https:$it" else it }
        .distinct()
        .toList()

    protected fun absoluteUrl(base: String, value: String): String {
        if (value.startsWith("http://") || value.startsWith("https://")) return value
        return runCatching { URI(base).resolve(value).toString() }.getOrDefault(value)
    }

    protected fun stripTags(html: String): String = decodeEntities(
        html.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim(),
    )

    protected fun origin(url: String): String = runCatching {
        URI(url).let { "${it.scheme}://${it.authority}" }
    }.getOrDefault(url.substringBefore('/', ""))

    protected fun language(label: String?): String {
        val text = label?.lowercase() ?: return "und"
        return when {
            text.contains("english") || text == "en" -> "en"
            text.contains("japanese") || text == "ja" -> "ja"
            text.contains("indonesian") || text == "id" -> "id"
            text.contains("thai") || text == "th" -> "th"
            text.contains("arabic") || text == "ar" -> "ar"
            text.contains("french") || text == "fr" -> "fr"
            text.contains("german") || text == "de" -> "de"
            text.contains("spanish") || text == "es" -> "es"
            text.contains("portuguese") || text == "pt" -> "pt"
            text.contains("italian") || text == "it" -> "it"
            text.contains("russian") || text == "ru" -> "ru"
            else -> "und"
        }
    }

    /**
     * Auto-matches a media entry to this source.
     *
     * Tries the main name first, then a simplified query (season tokens stripped),
     * then romaji variants. Prefers the entry whose season matches, which fixes
     * sites like Latanime that split seasons into separate entries.
     */
    override suspend fun autoSearch(mediaObj: Media): ShowResponse? {
        val saved = loadSavedShowResponse(mediaObj.id)
        if (saved != null) {
            saveShowResponse(mediaObj.id, saved, true)
            return saved
        }
        setUserText("Searching : ${mediaObj.mainName()}")
        Logger.log("Searching : ${mediaObj.mainName()}")

        val mediaSeason = seasonFromText("${mediaObj.mainName()} ${mediaObj.nameRomaji}")
        val queries = buildList {
            add(mediaObj.mainName())
            val simplified = searchableQuery(mediaObj.mainName())
            if (simplified.isNotBlank() && simplified != mediaObj.mainName().trim()) {
                add(simplified)
            }
            if (mediaObj.nameRomaji.isNotBlank()) {
                add(mediaObj.nameRomaji)
                val simplifiedRomaji = searchableQuery(mediaObj.nameRomaji)
                if (simplifiedRomaji.isNotBlank() && simplifiedRomaji != mediaObj.nameRomaji.trim()) {
                    add(simplifiedRomaji)
                }
            }
        }.distinct()

        var best: ShowResponse? = null
        var bestScore = 0.0
        queries.forEach { query ->
            searchWithFallback(query).forEach { result ->
                var score = FuzzySearch.ratio(result.name.lowercase(), query.lowercase()).toDouble()
                val resultSeason = seasonFromText(result.name)
                if (mediaSeason != null && resultSeason == mediaSeason) score += 40.0
                if (score > bestScore) {
                    bestScore = score
                    best = result
                }
            }
        }
        Logger.log(
            "$name autoSearch for '${mediaObj.mainName()}': " +
                "picked '${best?.name ?: "none"}' (score $bestScore)"
        )
        if (best != null) saveShowResponse(mediaObj.id, best)
        return best
    }

    /**
     * Site searches often choke on season suffixes ("Season 4" / "3rd Season").
     * Try the raw query first, then a simplified one stripped of season tokens and punctuation.
     */
    protected suspend fun searchWithFallback(query: String): List<ShowResponse> {
        var results = search(query)
        if (results.isEmpty()) {
            val simplified = searchableQuery(query)
            if (simplified.isNotBlank() && simplified != query.trim()) {
                Logger.log("$name search: 0 results for '$query', retrying with '$simplified'")
                results = search(simplified)
            }
        }
        return results
    }

    protected fun searchableQuery(query: String): String {
        var q = query
        q = Regex("""(?i)(?:season|temporada|part)\s*\d+""").replace(q, " ")
        q = Regex("""(?i)\d+(?:st|nd|rd|th)?\s+season""").replace(q, " ")
        q = q.replace(Regex("""[^\p{L}\p{N} ]+"""), " ")
        q = Regex("""\s+""").replace(q, " ").trim()
        return q
    }

    protected fun seasonFromText(text: String): Int? {
        val t = text.lowercase()
        Regex("""(?:season|temporada|part)\s*(\d+)""").find(t)?.let {
            return it.groupValues[1].toIntOrNull()
        }
        Regex("""(\d+)(?:st|nd|rd|th)?\s+season""").find(t)?.let {
            return it.groupValues[1].toIntOrNull()
        }
        Regex("""\bs(\d+)\b""").find(t)?.let {
            return it.groupValues[1].toIntOrNull()
        }
        Regex("""\b([ivx]+)\b""").find(t)?.let {
            return romanToInt[it.groupValues[1]]
        }
        return null
    }

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
}

private val romanToInt = mapOf(
    "i" to 1, "ii" to 2, "iii" to 3, "iv" to 4, "v" to 5,
    "vi" to 6, "vii" to 7, "viii" to 8, "ix" to 9, "x" to 10
)
