package ani.sanin.parsers

import android.content.Context
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.SwitchPreferenceCompat
import ani.sanin.currContext
import ani.sanin.okHttpClient
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import eu.kanade.tachiyomi.PreferenceScreen
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

    /**
     * Base screen shared by all native providers; providers may override to add
     * provider-specific preferences (call super first).
     */
    open fun setupPreferenceScreen(screen: PreferenceScreen) {
        val context: Context = currContext ?: return

        screen.addPreference(SwitchPreferenceCompat(context).apply {
            title = "Prefer Dub"
            summary = "Prefer dubbed audio when available"
            isChecked = PrefManager.getVal(PrefName.PreferDub)
            setOnPreferenceChangeListener { _, newValue ->
                PrefManager.setVal(PrefName.PreferDub, newValue as Boolean)
                true
            }
        })

        screen.addPreference(EditTextPreference(context).apply {
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
        val context: Context = currContext ?: return
        screen.addPreference(ListPreference(context).apply {
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

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
}
