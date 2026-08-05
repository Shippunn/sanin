package ani.sanin.parsers

import ani.sanin.Lazier
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

object AnimeSources : WatchSources() {
    override var list: List<Lazier<BaseParser>> = emptyList()
    var pinnedAnimeSources: List<String> = emptyList()
    var isInitialized = false

    val allNativeParsers by lazy {
        listOf(
            SenshiProvider(),
            AniBdProvider(),
            AniZoneProvider(),
            AnimeGGProvider(),
            AniVaultProvider(),
        )
    }

    val nativeParsers: List<NativeAnimeParser> get() {
        val enabled = PrefManager.getVal<Set<String>>(PrefName.EnabledProviders)
        return allNativeParsers.filter { it.saveName in enabled }
    }

    val nativeNames: List<String> get() = nativeParsers.map { it.name }

    override val displayNames: List<String> get() {
        val all = list
        val enabledExtensions = PrefManager.getVal<Set<String>>(PrefName.EnabledExtensions)
        val extNames = all.filter { it.name !in nativeNames && it.name != "Local" }
            .filter { enabledExtensions.isEmpty() || it.name in enabledExtensions }
            .map { it.name }
        return buildList {
            if (nativeNames.isNotEmpty()) add("─── Built-in ───")
            addAll(nativeNames)
            if (extNames.isNotEmpty()) add("─── Extensions ───")
            addAll(extNames)
        }
    }

    suspend fun init(fromExtensions: StateFlow<List<AnimeExtension.Installed>>) {
        pinnedAnimeSources =
            PrefManager.getNullableVal<List<String>>(PrefName.AnimeSourcesOrder, null)
                ?: emptyList()

        val initialExtensions = fromExtensions.first()
        rebuildList(initialExtensions)
        isInitialized = true

        fromExtensions.collect { extensions ->
            rebuildList(extensions)
        }
    }

    private fun rebuildList(extensions: List<AnimeExtension.Installed>) {
        val extParsers = createParsersFromExtensions(extensions)
        list = buildList {
            nativeParsers.forEach { add(Lazier({ it }, it.saveName)) }
            addAll(extParsers)
            add(Lazier({ LocalAnimeParser() }, "Local"))
        }
    }

    fun performReorderAnimeSources() {
        val extParsers = list.filter { it.name !in nativeNames && it.name != "Local" }
        val sortedExt = sortPinnedAnimeSources(extParsers, pinnedAnimeSources)
        list = buildList {
            nativeParsers.forEach { add(Lazier({ it }, it.saveName)) }
            addAll(sortedExt)
            add(Lazier({ LocalAnimeParser() }, "Local"))
        }
    }

    private fun createParsersFromExtensions(extensions: List<AnimeExtension.Installed>): List<Lazier<BaseParser>> {
        return extensions.map { extension ->
            Lazier({ DynamicAnimeParser(extension) }, extension.name)
        }
    }

    private fun sortPinnedAnimeSources(
        sources: List<Lazier<BaseParser>>,
        pinnedAnimeSources: List<String>
    ): List<Lazier<BaseParser>> {
        val pinnedSourcesMap = sources.filter { pinnedAnimeSources.contains(it.name) }
            .associateBy { it.name }
        val orderedPinnedSources = pinnedAnimeSources.mapNotNull { name ->
            pinnedSourcesMap[name]
        }
        val unpinnedSources = sources.filterNot { pinnedAnimeSources.contains(it.name) }
        return orderedPinnedSources + unpinnedSources
    }
}

object HAnimeSources : WatchSources() {
    override val list: List<Lazier<BaseParser>> get() = AnimeSources.list
}
