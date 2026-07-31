package ani.sanin.parsers

import android.util.Log
import ani.sanin.Lazier
import ani.sanin.media.Media
import ani.sanin.media.anime.Episode
import ani.sanin.parsers.MangaChapter
import ani.sanin.tryWithSuspend
import ani.sanin.util.Logger
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.source.model.SManga

abstract class WatchSources : BaseSources() {

    override operator fun get(i: Int): AnimeParser {
        return (list.getOrNull(i) ?: list.firstOrNull())?.get?.value as? AnimeParser
            ?: EmptyAnimeParser()
    }

    fun isDownloadedSource(i: Int): Boolean {
        return false
    }

    suspend fun loadEpisodesFromMedia(i: Int, media: Media): MutableMap<String, Episode> {
        val parser = get(i)
        Logger.log("Watch: loadEpisodesFromMedia source='${parser.name}' (idx $i) media='${media.name}'")
        val res = tryWithSuspend(true) {
            parser.autoSearch(media)
        }
        if (res == null) {
            Logger.log(Log.ERROR, "Watch: FAILED autoSearch for source='${parser.name}' (idx $i) media='${media.name}'")
            return mutableMapOf()
        }
        val map = loadEpisodes(i, res.link, res.extra, res.sAnime)
        Logger.log("Watch: loaded ${map.size} episodes from source='${parser.name}' (idx $i) media='${media.name}'")
        return map
    }

    suspend fun loadEpisodes(
        i: Int,
        showLink: String,
        extra: Map<String, String>?,
        sAnime: SAnime?
    ): MutableMap<String, Episode> {
        val map = mutableMapOf<String, Episode>()
        val parser = get(i)
        tryWithSuspend(true) {
            val anime = sAnime ?: SAnime.create().apply {
                url = showLink
                title = ""
            }
            parser.loadEpisodes(showLink, extra, anime).forEach {
                val sEpisode = it.sEpisode ?: SEpisode.create().apply {
                    url = it.link
                    name = it.title ?: ""
                    episode_number = it.number.toFloatOrNull() ?: 0f
                }
                map[it.number] = Episode(
                    it.number,
                    it.link,
                    it.title,
                    it.description,
                    it.thumbnail,
                    it.isFiller,
                    extra = it.extra,
                    sEpisode = sEpisode
                )
            }
        } ?: Logger.log(Log.ERROR, "Watch: FAILED loadEpisodes source='${parser.name}' (idx $i) link='$showLink'")
        if (map.isEmpty()) {
            Logger.log(Log.WARN, "Watch: loadEpisodes returned 0 episodes source='${parser.name}' (idx $i) link='$showLink'")
        }
        return map
    }

}

abstract class MangaReadSources : BaseSources() {

    override operator fun get(i: Int): BaseParser? {
        return (list.getOrNull(i) ?: list.firstOrNull())?.get?.value as? MangaParser
            ?: EmptyMangaParser()
    }

    suspend fun loadChaptersFromMedia(i: Int, media: Media): MutableMap<String, MangaChapter> {
        return mutableMapOf()
    }

    suspend fun loadChapters(i: Int, show: ShowResponse): MutableMap<String, MangaChapter> {
        return mutableMapOf()
    }
}

abstract class NovelReadSources : BaseSources() {
    override operator fun get(i: Int): NovelParser? {
        return if (list.isNotEmpty()) {
            (list.getOrNull(i) ?: list[0]).get.value as NovelParser
        } else {
            return EmptyNovelParser()
        }
    }

}



abstract class BaseSources {
    abstract val list: List<Lazier<BaseParser>>

    val names: List<String> get() = list.map { it.name }

    open val displayNames: List<String> get() = names

    fun flushText() {
        list.forEach {
            if (it.get.isInitialized())
                it.get.value?.showUserText = ""
        }
    }

    open operator fun get(i: Int): BaseParser? {
        return list[i].get.value
    }

    fun saveResponse(i: Int, mediaId: Int, response: ShowResponse) {
        get(i)?.saveShowResponse(mediaId, response, true)
    }

    companion object {
        const val SEPARATOR = "───"
    }
}



