package ani.sanin.media

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.sanin.R
import ani.sanin.connections.anilist.Anilist
import ani.sanin.connections.mal.MAL
import ani.sanin.currContext
import ani.sanin.media.anime.Episode
import ani.sanin.media.anime.SelectorDialogFragment
import ani.sanin.others.AniSkip
import ani.sanin.others.Anify
import ani.sanin.others.Jikan
import ani.sanin.others.Kitsu
import ani.sanin.parsers.AnimeSources
import ani.sanin.parsers.ShowResponse
import ani.sanin.parsers.VideoExtractor
import ani.sanin.parsers.WatchSources
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.snackString
import ani.sanin.tryWithSuspend
import ani.sanin.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

class MediaDetailsViewModel : ViewModel() {
    val scrolledToTop = MutableLiveData(true)

    fun saveSelected(id: Int, data: Selected) {
        PrefManager.setCustomVal("Selected-$id", data)
    }


    fun loadSelected(media: Media, isDownload: Boolean = false): Selected {
        if ((media.format == "LOCAL" || media.format == "LOCAL_NOVEL") && media.selected != null) {
            return media.selected!!
        }
        val data =
            PrefManager.getNullableCustomVal("Selected-${media.id}", null, Selected::class.java)
                ?: Selected().let {
                    it.sourceIndex = 0
                    it.preferDub = PrefManager.getVal(PrefName.PreferDub)
                    saveSelected(media.id, it)
                    it
                }
        if (isDownload) {
            data.sourceIndex = when {
                media.anime != null -> {
                    AnimeSources.list.size - 1
                }

                media.format == "MANGA" || media.format == "ONE_SHOT" -> {
                    0
                }

                else -> {
                0
                }
            }
        }
        return data
    }

    var continueMedia: Boolean? = null
    var loading = false

    private val media: MutableLiveData<Media> = MutableLiveData<Media>(null)
    fun getMedia(): LiveData<Media> = media
    fun loadMedia(m: Media) {
        if (!loading) {
            loading = true
            val rescueMode: Boolean = PrefManager.getVal(PrefName.RescueMode)
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                if (m.id == 0 && m.format?.startsWith("LOCAL") == true) {
                    m.folderName = m.folderName ?: m.name
                    media.postValue(m)

                    val mapKeyStr = m.folderName ?: m.name
                    var mappedId = PrefManager.getCustomVal<Int>("local_mapping_$mapKeyStr", 0)
                    if (mappedId == 0) {
                        try {
                            val searchType = "ANIME"
                            val searchFormat = null as String?
                            var results = Anilist.query.searchAniManga(searchType, search = m.name, format = searchFormat)
                            if (results == null || results.results.isEmpty()) {
                                if (m.folderName != null && m.folderName != m.name) {
                                    results = Anilist.query.searchAniManga(searchType, search = m.folderName!!, format = searchFormat)
                                }
                            }
                            if (results != null && results.results.isNotEmpty()) {
                                mappedId = results.results[0].id
                                PrefManager.setCustomVal("local_mapping_$mapKeyStr", mappedId)
                            }
                        } catch (e: Exception) {
                            ani.sanin.util.Logger.log(e)
                        }
                    }

                    if (mappedId != 0) {
                        val newMedia = m.copy(id = mappedId)
                        val fetchedMedia = Anilist.query.mediaDetails(newMedia)
                        fetchedMedia?.format = m.format 
                        
                        // Cache
                        fetchedMedia?.cover?.let { ani.sanin.settings.saving.PrefManager.setCustomVal("local_cover_$mapKeyStr", it) }
                        fetchedMedia?.banner?.let { ani.sanin.settings.saving.PrefManager.setCustomVal("local_banner_$mapKeyStr", it) }

                        fetchedMedia?.folderName = m.folderName ?: m.name
                        fetchedMedia?.selected = m.selected
                        media.postValue(fetchedMedia)
                    }
                } else if (m.id == 0) {
                    m.folderName = m.folderName ?: m.name
                    media.postValue(m)
                } else if (rescueMode && m.idMAL != null) {
                    tryWithSuspend {
                        val malId = m.idMAL!!
                        val malNode = MAL.query.getAnimeDetails(malId)
                        if (malNode != null) {
                            val detailed = Media(malNode, true)
                            detailed.userProgress = m.userProgress ?: detailed.userProgress
                            detailed.userStatus = m.userStatus ?: detailed.userStatus
                            detailed.userScore = if (m.userScore != 0) m.userScore else detailed.userScore
                            detailed.isListPrivate = m.isListPrivate
                            detailed.userListId = m.userListId
                            detailed.userRepeat = m.userRepeat
                            detailed.userUpdatedAt = m.userUpdatedAt ?: detailed.userUpdatedAt
                            detailed.userCompletedAt = m.userCompletedAt
                            detailed.userStartedAt = m.userStartedAt
                            detailed.cameFromContinue = m.cameFromContinue
                            detailed.selected = m.selected
                            detailed.isFav = m.isFav
                            detailed.shareLink = "https://myanimelist.net/anime/$malId"
                            detailed.anime?.episodes = m.anime?.episodes
                            enrichRescueModeDetails(detailed)
                            media.postValue(detailed)
                            launchBackgroundEnrichment(detailed)
                        } else {
                            val jikanData = MAL.jikan.getAnimeById(malId)
                            if (jikanData != null) {
                                val detailed = Media(jikanData, true)
                                detailed.userProgress = m.userProgress ?: detailed.userProgress
                                detailed.userStatus = m.userStatus ?: detailed.userStatus
                                detailed.userScore = if (m.userScore != 0) m.userScore else detailed.userScore
                                detailed.isListPrivate = m.isListPrivate
                                detailed.userListId = m.userListId
                                detailed.userRepeat = m.userRepeat
                                detailed.userUpdatedAt = m.userUpdatedAt
                                detailed.userCompletedAt = m.userCompletedAt
                                detailed.userStartedAt = m.userStartedAt
                                detailed.cameFromContinue = m.cameFromContinue
                                detailed.selected = m.selected
                                detailed.isFav = m.isFav
                                detailed.shareLink = "https://myanimelist.net/anime/$malId"
                                detailed.anime?.episodes = m.anime?.episodes
                                enrichRescueModeDetails(detailed)
                                media.postValue(detailed)
                                launchBackgroundEnrichment(detailed)
                            } else {
                                media.postValue(m)
                            }
                        }
                    }
                } else if (rescueMode) {
                    media.postValue(m)
                } else {
                    media.postValue(Anilist.query.mediaDetails(m))
                }
                loading = false
            }
        }

        if (!PrefManager.getVal<Boolean>(PrefName.RescueMode)) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    if (m.idIMDB == null) {
                        m.idIMDB = ani.sanin.others.IdMappers.getImdbId(m.id)
                    }
                } catch (e: Exception) {
                    ani.sanin.util.Logger.log(e)
                }
            }
        }
    }

    private suspend fun enrichRescueModeDetails(media: Media) {
        val malId = media.idMAL ?: return
        supervisorScope {
            val fullDeferred = async {
                MAL.jikan.getAnimeById(malId)
            }
            val charactersDeferred = async {
                MAL.jikan.getAnimeCharacters(malId)
            }
            val staffDeferred = async {
                MAL.jikan.getAnimeStaff(malId)
            }
            val reviewsDeferred = async {
                MAL.jikan.getAnimeReviews(malId)
            }
            val recommendationsDeferred = async {
                MAL.jikan.getRecommendations(true, malId)
            }

            val fullData = fullDeferred.await()
            if (fullData != null) {
                val fullMapped = Media(fullData, true)
                if (media.description.isNullOrBlank() && !fullMapped.description.isNullOrBlank()) {
                    media.description = fullMapped.description
                }
                if (fullMapped.synonyms.isNotEmpty()) media.synonyms = fullMapped.synonyms
                if (fullMapped.genres.isNotEmpty()) media.genres = fullMapped.genres
                if (!fullMapped.externalLinks.isNullOrEmpty()) media.externalLinks = fullMapped.externalLinks
                if ((media.meanScore == null || media.meanScore == 0) && fullMapped.meanScore != null) {
                    media.meanScore = fullMapped.meanScore
                }
                if (media.source.isNullOrBlank() && !fullMapped.source.isNullOrBlank()) {
                    media.source = fullMapped.source
                }
                if (!fullMapped.relations.isNullOrEmpty()) {
                    if (media.relations.isNullOrEmpty() || (fullMapped.relations?.size ?: 0) > (media.relations?.size ?: 0)) {
                        media.relations = fullMapped.relations
                    }
                    if (media.prequel == null) media.prequel = fullMapped.prequel
                    if (media.sequel == null) media.sequel = fullMapped.sequel
                }
                if (!fullMapped.staff.isNullOrEmpty()) {
                    media.staff = ArrayList(
                        ((media.staff ?: arrayListOf()) + fullMapped.staff!!).distinctBy { it.id }
                    )
                }
                if (!fullMapped.recommendations.isNullOrEmpty() &&
                    (fullMapped.recommendations?.size ?: 0) > (media.recommendations?.size ?: 0)) {
                    media.recommendations = fullMapped.recommendations
                }
                if (!fullMapped.trailer.isNullOrBlank()) media.trailer = fullMapped.trailer
                fullMapped.anime?.let { anime ->
                    if (anime.op.isNotEmpty()) media.anime?.op = anime.op
                    if (anime.ed.isNotEmpty()) media.anime?.ed = anime.ed
                    anime.mainStudio?.let { media.anime?.mainStudio = it }
                    if (!anime.producers.isNullOrEmpty()) media.anime?.producers = anime.producers
                    anime.season?.let { media.anime?.season = it }
                    anime.seasonYear?.let { media.anime?.seasonYear = it }
                    if (media.anime?.nextAiringEpisodeTime == null && anime.nextAiringEpisodeTime != null) {
                        media.anime?.nextAiringEpisodeTime = anime.nextAiringEpisodeTime
                    }
                    val estimated = anime.nextAiringEpisode ?: 0
                    val watched = media.userProgress ?: 0
                    val nextAiring = if (watched > 0) {
                        if (watched >= (estimated + 1)) watched else estimated
                    } else {
                        estimated
                    }
                    media.anime?.nextAiringEpisode = nextAiring
                }
            }

            val jRecommendations = try { recommendationsDeferred.await() } catch (_: Exception) { null }
            val mappedRecommendations = jRecommendations
                ?.mapNotNull { it.entry }
                ?.map {
                    Media(
                        id = it.malId,
                        idMAL = it.malId,
                        name = it.title,
                        nameRomaji = it.title ?: "",
                        userPreferredName = it.title ?: "",
                        cover = it.images?.jpg?.largeImageUrl ?: it.images?.jpg?.imageUrl,
                        banner = it.images?.jpg?.largeImageUrl,
                        isAdult = false,
                        status = null,
                        meanScore = null,
                        popularity = null,
                        format = null,
                    )
                }
                ?.distinctBy { it.id }
                ?.let { ArrayList(it) }
            
            if (!mappedRecommendations.isNullOrEmpty()) {
                if (media.recommendations.isNullOrEmpty() || mappedRecommendations.size > (media.recommendations?.size ?: 0)) {
                    media.recommendations = mappedRecommendations
                }
            }

            val mappedCharacters = charactersDeferred.await()
                .mapNotNull { jChar ->
                    val character = jChar.character ?: return@mapNotNull null
                    Character(
                        id = character.malId,
                        name = character.name,
                        image = character.images?.jpg?.largeImageUrl ?: character.images?.jpg?.imageUrl,
                        banner = media.banner ?: media.cover,
                        role = jChar.role ?: "",
                        isFav = false,
                        voiceActor = jChar.voiceActors
                            ?.mapNotNull { va ->
                                va.person?.let { person ->
                                    Author(
                                        id = person.malId,
                                        name = person.name,
                                        image = person.images?.jpg?.largeImageUrl ?: person.images?.jpg?.imageUrl,
                                        role = va.language
                                    )
                                }
                            }
                            ?.let { ArrayList(it) }
                    )
                }
            if (mappedCharacters.isNotEmpty()) {
                media.characters = ArrayList(mappedCharacters.distinctBy { it.id })
            }

            val mappedStaff = staffDeferred.await()
                .mapNotNull { staff ->
                    val person = staff.person ?: return@mapNotNull null
                    Author(
                        id = person.malId,
                        name = person.name,
                        image = person.images?.jpg?.largeImageUrl ?: person.images?.jpg?.imageUrl,
                        role = staff.positions?.joinToString(", ")
                    )
                }

            val allStaff = mappedStaff.distinctBy { it.id }
            if (allStaff.isNotEmpty()) {
                media.staff = ArrayList(
                    ((media.staff ?: arrayListOf()) + allStaff).distinctBy { it.id }
                )
            }

            mapJikanReviews(media, reviewsDeferred.await(), true, malId)
        }
    }

    private fun launchBackgroundEnrichment(media: Media) {
        val malId = media.idMAL ?: return
        val isAnime = media.anime != null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                fetchRelationCovers(media, isAnime)

                enrichRecommendationDetails(media, isAnime)

                this@MediaDetailsViewModel.media.postValue(media)
            } catch (_: Exception) {}
        }
    }

    private suspend fun enrichRecommendationDetails(media: Media, isAnime: Boolean) {
        val recs = media.recommendations?.take(15) ?: return
        val recsToEnrich = recs.filter { rec ->
            rec.meanScore == null || rec.meanScore == 0 ||
            (rec.anime != null && rec.anime?.totalEpisodes == null)
        }
        if (recsToEnrich.isEmpty()) return
        kotlinx.coroutines.supervisorScope {
            val deferreds = recsToEnrich.map { rec ->
                async {
                    try {
                        val recMalId = rec.idMAL ?: return@async
                        
                        val coverUrl: String?
                        val score: Int?
                        val statusStr: String?
                        val episodesCount: Int?

                        val node = MAL.query.getAnimeDetails(recMalId)
                        if (node != null) {
                            coverUrl = node.mainPicture?.large ?: node.mainPicture?.medium
                            score = ((node.mean ?: 0f) * 10f).toInt()
                            statusStr = node.status?.replace("_", " ")?.uppercase(java.util.Locale.US)
                            episodesCount = node.numEpisodes
                        } else {
                            val jikanNode = MAL.jikan.getAnimeById(recMalId)
                            if (jikanNode != null) {
                                coverUrl = jikanNode.images?.jpg?.largeImageUrl ?: jikanNode.images?.jpg?.imageUrl
                                score = ((jikanNode.score ?: 0f) * 10f).toInt()
                                statusStr = jikanNode.status?.replace("_", " ")?.uppercase(java.util.Locale.US)
                                episodesCount = jikanNode.episodes
                            } else {
                                coverUrl = null
                                score = null
                                statusStr = null
                                episodesCount = null
                            }
                        }

                        if (coverUrl != null || score != null || statusStr != null) {
                            if (coverUrl != null) {
                                rec.cover = rec.cover ?: coverUrl
                            }
                            if (score != null) {
                                rec.meanScore = score
                            }
                            if (statusStr != null) {
                                rec.status = statusStr
                            }
                            if (episodesCount != null) {
                                rec.anime?.totalEpisodes = episodesCount
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
            deferreds.forEach { it.await() }
        }
        media.recommendations = ArrayList(recs)
    }


    private fun mapJikanReviews(
        media: Media,
        jikanReviews: List<ani.sanin.connections.mal.JikanReview>,
        isAnime: Boolean,
        malId: Int
    ) {
        if (jikanReviews.isEmpty()) return
        val mapped = jikanReviews.mapNotNull { review ->
            val reviewText = review.review ?: return@mapNotNull null
            val summary = if (reviewText.length > 200) reviewText.take(200) + "…" else reviewText
            val userName = review.user?.username ?: "Anonymous"
            val userAvatar = review.user?.images?.jpg?.imageUrl
            val createdAt = try {
                java.time.Instant.parse(review.date ?: "").epochSecond.toInt()
            } catch (_: Exception) { 0 }
            ani.sanin.connections.anilist.api.Query.Review(
                id = review.malId,
                mediaId = malId,
                mediaType = if (isAnime) "ANIME" else "MANGA",
                summary = summary,
                body = reviewText,
                rating = review.reactions?.overall ?: 0,
                ratingAmount = review.reactions?.overall ?: 0,
                userRating = "NO_VOTE",
                score = (review.score ?: 0) * 10,
                private = false,
                siteUrl = review.url ?: "https://myanimelist.net",
                createdAt = createdAt,
                updatedAt = null,
                user = ani.sanin.connections.anilist.api.User(
                    id = review.malId,
                    name = userName,
                    avatar = ani.sanin.connections.anilist.api.UserAvatar(
                        large = userAvatar,
                        medium = userAvatar
                    ),
                    bannerImage = null,
                    isFollowing = null,
                    isFollower = null,
                    options = null,
                    mediaListOptions = null,
                    favourites = null,
                    statistics = null,
                    unreadNotificationCount = null,
                )
            )
        }
        if (mapped.isNotEmpty()) {
            media.review = ArrayList(mapped.take(5))
        }
    }

    private suspend fun fetchRelationCovers(media: Media, isAnime: Boolean) {
        val relationsToFetch = mutableListOf<Media>()
        media.prequel?.let { if (it.cover == null) relationsToFetch.add(it) }
        media.sequel?.let { if (it.cover == null) relationsToFetch.add(it) }
        media.relations?.forEach { rel ->
            if (rel.cover == null && !relationsToFetch.contains(rel)) relationsToFetch.add(rel)
        }
        if (relationsToFetch.isEmpty()) return

        kotlinx.coroutines.supervisorScope {
            val deferreds = relationsToFetch.take(8).map { rel ->
                async {
                    val relMalId = rel.idMAL ?: return@async
                    val relIsAnime = rel.anime != null || rel.relation?.contains("ANIME", true) == true
                    try {
                        val coverUrl: String?
                        val score: Int?
                        val statusStr: String?
                        val episodesCount: Int?
                        var resolvedFmt: String? = null

                        val node = MAL.query.getAnimeDetails(relMalId)
                        if (node != null) {
                            coverUrl = node.mainPicture?.large ?: node.mainPicture?.medium
                            score = ((node.mean ?: 0f) * 10f).toInt()
                            statusStr = node.status?.replace("_", " ")?.uppercase(java.util.Locale.US)
                            episodesCount = node.numEpisodes
                            resolvedFmt = node.mediaType?.uppercase(java.util.Locale.US)
                        } else {
                            val jikanNode = MAL.jikan.getAnimeById(relMalId)
                            if (jikanNode != null) {
                                coverUrl = jikanNode.images?.jpg?.largeImageUrl ?: jikanNode.images?.jpg?.imageUrl
                                score = ((jikanNode.score ?: 0f) * 10f).toInt()
                                statusStr = jikanNode.status?.replace("_", " ")?.uppercase(java.util.Locale.US)
                                episodesCount = jikanNode.episodes
                                val typeStr = jikanNode.type?.uppercase(java.util.Locale.US)
                                resolvedFmt = typeStr ?: "TV"
                            } else {
                                coverUrl = null
                                score = null
                                statusStr = null
                                episodesCount = null
                            }
                        }

                        if (coverUrl != null || score != null || statusStr != null) {
                            if (coverUrl != null) {
                                rel.cover = coverUrl
                                rel.banner = coverUrl
                            }
                            if (score != null && rel.meanScore == null) {
                                rel.meanScore = score
                            }
                            if (statusStr != null && rel.status == null) {
                                rel.status = statusStr
                            }
                            if (episodesCount != null) {
                                rel.anime?.totalEpisodes = episodesCount
                            }
                            if (resolvedFmt != null) {
                                rel.format = resolvedFmt
                                val rawRelation = rel.relation?.substringBefore("\n") ?: ""
                                if (rawRelation.isNotEmpty()) {
                                    rel.relation = "$rawRelation\n$resolvedFmt"
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
            deferreds.forEach { it.await() }
        }
    }

    fun setMedia(m: Media) {
        media.postValue(m)

        if (!PrefManager.getVal<Boolean>(PrefName.RescueMode)) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    if (m.idIMDB == null) {
                        m.idIMDB = ani.sanin.others.IdMappers.getImdbId(m.id)
                    }
                } catch (e: Exception) {
                    ani.sanin.util.Logger.log(e)
                }
            }
        }
    }

    val responses = MutableLiveData<List<ShowResponse>?>(null)


    //Anime
    private val kitsuEpisodes: MutableLiveData<Map<String, Episode>> =
        MutableLiveData<Map<String, Episode>>(null)

    fun getKitsuEpisodes(): LiveData<Map<String, Episode>> = kitsuEpisodes
    suspend fun loadKitsuEpisodes(s: Media) {
        tryWithSuspend {
            if (kitsuEpisodes.value == null) kitsuEpisodes.postValue(Kitsu.getKitsuEpisodesDetails(s))
        }
    }

    private val anifyEpisodes: MutableLiveData<Map<String, Episode>> =
        MutableLiveData<Map<String, Episode>>(null)

    fun getAnifyEpisodes(): LiveData<Map<String, Episode>> = anifyEpisodes
    suspend fun loadAnifyEpisodes(s: Int) {
        tryWithSuspend {
            if (anifyEpisodes.value == null) anifyEpisodes.postValue(Anify.fetchAndParseMetadata(s))
        }
    }

    private val fillerEpisodes: MutableLiveData<Map<String, Episode>> =
        MutableLiveData<Map<String, Episode>>(null)

    fun getFillerEpisodes(): LiveData<Map<String, Episode>> = fillerEpisodes
    suspend fun loadFillerEpisodes(s: Media) {
        tryWithSuspend {
            if (fillerEpisodes.value == null) fillerEpisodes.postValue(
                Jikan.getEpisodes(
                    s.idMAL ?: return@tryWithSuspend
                )
            )
        }
    }

    suspend fun fetchKitsuEpisodes(media: Media): Map<String, Episode>? {
        return try {
            val data = Kitsu.getKitsuEpisodesDetails(media)
            if (data != null && kitsuEpisodes.value == null) kitsuEpisodes.postValue(data)
            data
        } catch (e: Exception) {
            Logger.log(e)
            null
        }
    }

    suspend fun fetchAnifyEpisodes(id: Int): Map<String, Episode>? {
        return try {
            val data = Anify.fetchAndParseMetadata(id)
            if (data != null && anifyEpisodes.value == null) anifyEpisodes.postValue(data)
            data
        } catch (e: Exception) {
            Logger.log(e)
            null
        }
    }

    suspend fun fetchFillerEpisodes(media: Media): Map<String, Episode>? {
        return try {
            val id = media.idMAL ?: return null
            val data = Jikan.getEpisodes(id)
            if (data != null && fillerEpisodes.value == null) fillerEpisodes.postValue(data)
            data
        } catch (e: Exception) {
            Logger.log(e)
            null
        }
    }

    var watchSources: WatchSources? = null

    private val episodes = MutableLiveData<MutableMap<Int, MutableMap<String, Episode>>>(null)
    private val epsLoaded = mutableMapOf<Int, MutableMap<String, Episode>>()
    fun getEpisodes(): LiveData<MutableMap<Int, MutableMap<String, Episode>>> = episodes
    suspend fun loadEpisodes(media: Media, i: Int, invalidate: Boolean = false) {
        epsLoaded.keys.removeAll { it != i }
        if (!epsLoaded.containsKey(i) || invalidate) {
            Logger.log("Watch: ViewModel loadEpisodes source idx=$i invalidate=$invalidate media='${media.name}'")
            epsLoaded[i] = watchSources?.loadEpisodesFromMedia(i, media) ?: return
        }
        episodes.postValue(epsLoaded)
    }

    suspend fun forceLoadEpisode(media: Media, i: Int) {
        Logger.log("Watch: ViewModel forceLoadEpisode source idx=$i media='${media.name}'")
        epsLoaded[i] = watchSources?.loadEpisodesFromMedia(i, media) ?: return
        episodes.postValue(epsLoaded)
    }

    suspend fun overrideEpisodes(i: Int, source: ShowResponse, id: Int) {
        watchSources?.saveResponse(i, id, source)
        epsLoaded[i] =
            watchSources?.loadEpisodes(i, source.link, source.extra, source.sAnime) ?: return
        episodes.postValue(epsLoaded)
    }

    private var episode = MutableLiveData<Episode?>(null)
    fun getEpisode(): LiveData<Episode?> = episode

    suspend fun loadEpisodeVideos(ep: Episode, i: Int, post: Boolean = true) {
        val link = ep.link ?: return
        if (!ep.allStreams || ep.extractors.isNullOrEmpty()) {
            val existingExtractors = ep.extractors?.filterNotNull().orEmpty().toMutableList()
            if (existingExtractors.size != ep.extractors?.size) {
                Logger.log(
                    Log.WARN,
                    "Watch: null extractor(s) in stale list for episode '${ep.number}' — dropped"
                )
            }
            val list = mutableListOf<VideoExtractor>()
            ep.extractors = list
            watchSources?.get(i)?.apply {
                if (!post && !allowsPreloading) return@apply
                val sourceName = this.name
                ep.sEpisode?.let {
                    loadByVideoServers(link, ep.extra, it) { extractor ->
                        try {
                            if (extractor.videos.isNotEmpty()) {
                                list.add(extractor)
                                ep.extractorCallback?.invoke(extractor)
                            }
                        } catch (e: Exception) {
                            Logger.log(
                                Log.ERROR,
                                "Watch: bad extractor from '$sourceName' for episode '${ep.number}': ${e.message}"
                            )
                        }
                    }
                }
                ep.extractorCallback = null
                if (list.isNotEmpty())
                    ep.allStreams = true
                else if (existingExtractors.isNotEmpty())
                    ep.extractors = existingExtractors
            }
        }
        val extractorList = ep.extractors?.filterNotNull().orEmpty()
        if (extractorList.size != ep.extractors?.size) {
            Logger.log(
                Log.WARN,
                "Watch: null extractor(s) inside ep.extractors for episode '${ep.number}' — filtered before use"
            )
        }
        Logger.log(
            "Watch: episode '${ep.number}' extractors=${extractorList.size} " +
                "videos=${extractorList.sumOf { it.videos.size }} source idx=$i post=$post"
        )
        if (extractorList.isEmpty()) {
            Logger.log(Log.ERROR, "Watch: NO video servers found for episode '${ep.number}' source idx=$i")
        }

        if (post) {
            episode.postValue(ep)
            MainScope().launch(Dispatchers.Main) {
                episode.value = null
            }
        }
    }

    val timeStamps = MutableLiveData<List<AniSkip.Stamp>?>()
    private val timeStampsMap: MutableMap<Int, List<AniSkip.Stamp>?> = mutableMapOf()
    suspend fun loadTimeStamps(
        malId: Int?,
        episodeNum: Int?,
        duration: Long,
        useProxyForTimeStamps: Boolean,
        extensionTimestamps: List<eu.kanade.tachiyomi.animesource.model.TimeStamp> = emptyList()
    ) {
        episodeNum ?: return
        if (timeStampsMap.containsKey(episodeNum))
            return timeStamps.postValue(timeStampsMap[episodeNum])
        // Extension timestamps take priority; fall back to AniSkip when the extension has none
        val result: List<AniSkip.Stamp>? = if (extensionTimestamps.isNotEmpty()) {
            extensionTimestamps.map { it.toAniSkipStamp() }
        } else if (malId != null) {
            AniSkip.getResult(malId, episodeNum, duration, useProxyForTimeStamps)
        } else {
            null
        }
        timeStampsMap[episodeNum] = result
        timeStamps.postValue(result)
    }

    private fun eu.kanade.tachiyomi.animesource.model.TimeStamp.toAniSkipStamp(): AniSkip.Stamp {
        val skipType = when (type) {
            eu.kanade.tachiyomi.animesource.model.ChapterType.Opening -> "op"
            eu.kanade.tachiyomi.animesource.model.ChapterType.Ending -> "ed"
            eu.kanade.tachiyomi.animesource.model.ChapterType.Recap -> "recap"
            eu.kanade.tachiyomi.animesource.model.ChapterType.MixedOp -> "mixed-op"
            eu.kanade.tachiyomi.animesource.model.ChapterType.Other ->
                name.lowercase().replace(" ", "-").ifEmpty { "other" }
        }
        return AniSkip.Stamp(
            interval = AniSkip.AniSkipInterval(start, end),
            skipType = skipType,
            skipId = name,
            // episodeLength represents total episode duration; use 0.0 as a sentinel since
            // extension timestamps don't carry the full episode length
            episodeLength = 0.0
        )
    }

    suspend fun loadEpisodeSingleVideo(
        ep: Episode,
        selected: Selected,
        post: Boolean = true,
        selectedServerName: String? = null
    ): Boolean {

        val server = selectedServerName ?: selected.server ?: return false
        val link = ep.link ?: return false

        if (ep.extractors?.find{ it.server.name == server } == null) {
            Logger.log("Watch: loading single video server '$server' for episode '${ep.number}' source idx=${selected.sourceIndex}")
            if(ep.extractors == null){
                ep.extractors = mutableListOf(watchSources?.get(selected.sourceIndex)?.let {
                    selected.sourceIndex = selected.sourceIndex
                    if (!post && !it.allowsPreloading) null
                    else ep.sEpisode?.let { it1 ->
                        it.loadSingleVideoServer(
                            server, link, ep.extra,
                            it1, post
                        )
                    }
                } ?: return false)
            }
            else{
                ep.extractors!!.add(watchSources?.get(selected.sourceIndex)?.let {
                    selected.sourceIndex = selected.sourceIndex
                    if (!post && !it.allowsPreloading) null
                    else ep.sEpisode?.let { it1 ->
                        it.loadSingleVideoServer(
                            server, link, ep.extra,
                            it1, post
                        )
                    }
                } ?: return false)
            }
            ep.allStreams = false
        }
        if (post) {
            episode.postValue(ep)
            MainScope().launch(Dispatchers.Main) {
                episode.value = null
            }
        }
        return true
    }

    fun setEpisode(ep: Episode?, who: String) {
        Logger.log("set episode ${ep?.number} - $who")
        episode.postValue(ep)
        MainScope().launch(Dispatchers.Main) {
            episode.value = null
        }
    }

    val epChanged = MutableLiveData(true)
    fun onEpisodeClick(
        media: Media,
        i: String = "",
        manager: FragmentManager,
        launch: Boolean = true,
        prevEp: String? = null,
        isDownload: Boolean = false,
        episodes: ArrayList<String> = arrayListOf() // used for handling an array of episodes to download or to view a single episode
    ) {
        Handler(Looper.getMainLooper()).post {
            if (manager.findFragmentByTag("dialog") == null && !manager.isDestroyed) {
                if(episodes.isEmpty()){
                    episodes.add(i)
                }
                for (ep in episodes){
                    if (media.anime?.episodes?.get(ep) == null) {
                        snackString(currContext()?.getString(R.string.episode_not_found, ep))
                        Logger.log(Log.ERROR, "Watch: episode '$ep' not found in list of ${media.anime?.episodes?.size} for '${media.name}'")
                        return@post
                    }
                }
                Logger.log("Watch: opening selector for episode(s) ${episodes.joinToString(",")} source='${watchSources?.get(media.selected?.sourceIndex ?: 0)?.name}' (idx ${media.selected?.sourceIndex})")
                media.selected = this.loadSelected(media)
                val selector =
                    SelectorDialogFragment.newInstance(
                        media.selected!!.server,
                        launch,
                        prevEp,
                        isDownload,
                        episodes
                    )
                selector.show(manager, "dialog")
            }
        }
    }

    private val fetchedOnlineSubtitles = mutableMapOf<String, List<Any>>()

    fun saveFetchedSubtitles(id: String, subs: List<Any>) {
        fetchedOnlineSubtitles[id] = subs
    }

    fun getFetchedSubtitles(id: String): List<Any>? {
        return fetchedOnlineSubtitles[id]
    }

    fun clearFetchedSubtitles(id: String) {
        fetchedOnlineSubtitles.remove(id)
    }

    private val localSubtitlesMap = mutableMapOf<String, MutableList<Any>>()

    fun saveLocalSubtitle(id: String, sub: Any) {
        val list = localSubtitlesMap.getOrPut(id) { mutableListOf() }
        val isDuplicate = list.any { existing ->
            existing is ani.sanin.parsers.Subtitle &&
            sub is ani.sanin.parsers.Subtitle &&
            existing.file.url == sub.file.url
        }
        if (!isDuplicate) list.add(sub)
    }

    fun getLocalSubtitles(id: String): List<Any> {
        return localSubtitlesMap[id] ?: emptyList()
    }

    fun clearLocalSubtitles(id: String) {
        localSubtitlesMap.remove(id)
    }
}
