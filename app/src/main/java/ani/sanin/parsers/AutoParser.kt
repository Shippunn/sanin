package ani.sanin.parsers

import ani.sanin.media.Media
import ani.sanin.util.Logger

class AutoParser(private val nativeProviders: List<NativeAnimeParser>) : AnimeParser() {

    override val name = "Auto"
    override val saveName = "Auto"

    private var chosenProvider: String? = null

    override suspend fun search(query: String): List<ShowResponse> = emptyList()

    override suspend fun autoSearch(mediaObj: Media): ShowResponse? {
        for (provider in nativeProviders) {
            try {
                val result = provider.autoSearch(mediaObj)
                if (result != null) {
                    chosenProvider = provider.saveName
                    Logger.log("Auto: selected ${provider.saveName} for ${mediaObj.mainName()}")
                    return result.copy(link = saveName)
                }
            } catch (e: Exception) {
                Logger.log("Auto: ${provider.saveName} autoSearch failed - ${e.message}")
            }
        }
        return null
    }

    override suspend fun loadEpisodes(animeLink: String, extra: Map<String, String>?, sAnime: SAnime?): List<Episode> {
        val candidates = if (chosenProvider != null) {
            listOfNotNull(nativeProviders.find { it.saveName == chosenProvider }) + nativeProviders
        } else nativeProviders

        for (provider in candidates.distinct()) {
            try {
                val episodes = provider.loadEpisodes(provider.saveName, extra, sAnime)
                if (episodes.isNotEmpty()) {
                    chosenProvider = provider.saveName
                    Logger.log("Auto: ${provider.saveName} returned ${episodes.size} episodes")
                    return episodes
                }
            } catch (e: Exception) {
                Logger.log("Auto: ${provider.saveName} loadEpisodes failed - ${e.message}")
            }
        }
        return emptyList()
    }

    override suspend fun loadVideoServers(episodeLink: String, extra: Map<String, String>?, sEpisode: SEpisode?): List<VideoServer> {
        val candidates = if (chosenProvider != null) {
            listOfNotNull(nativeProviders.find { it.saveName == chosenProvider }) + nativeProviders
        } else nativeProviders

        for (provider in candidates.distinct()) {
            try {
                val servers = provider.loadVideoServers(episodeLink, extra, sEpisode)
                if (servers.isNotEmpty()) {
                    Logger.log("Auto: ${provider.saveName} returned ${servers.size} servers")
                    return servers
                }
            } catch (e: Exception) {
                Logger.log("Auto: ${provider.saveName} loadVideoServers failed - ${e.message}")
            }
        }
        return emptyList()
    }

    override suspend fun getVideoExtractor(server: VideoServer): VideoExtractor {
        return NativeVideoExtractor(server)
    }

    override var selectDub: Boolean = false
}
