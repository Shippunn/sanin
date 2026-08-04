package ani.sanin.media.anime

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.math.MathUtils
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.Button
import ani.sanin.R
import ani.sanin.connections.anilist.Anilist
import ani.sanin.connections.mal.MAL
import ani.sanin.media.MediaListDialogFragment
import ani.sanin.px
import ani.sanin.connections.LogoApi
import ani.sanin.databinding.FragmentMediaSourceBinding
import ani.sanin.loadImage
import ani.sanin.dp
import ani.sanin.isOnline
import ani.sanin.media.Media
import ani.sanin.media.MediaDetailsActivity
import ani.sanin.media.MediaDetailsViewModel
import ani.sanin.media.MediaNameAdapter
import ani.sanin.media.MediaType
import ani.sanin.FileUrl
import ani.sanin.navBarHeight
import ani.sanin.notifications.subscription.SubscriptionHelper
import ani.sanin.notifications.subscription.SubscriptionHelper.Companion.saveSubscription
import ani.sanin.others.LanguageMapper
import ani.sanin.parsers.AnimeParser
import ani.sanin.parsers.AnimeSources
import ani.sanin.parsers.HAnimeSources
import ani.sanin.setBaseline
import ani.sanin.setNavigationTheme
import ani.sanin.toPx
import ani.sanin.settings.extensionprefs.AnimeSourcePreferencesFragment
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.snackString
import ani.sanin.toast
import ani.sanin.util.FocusEffectUtil
import ani.sanin.util.GlassComponent
import ani.sanin.util.GlassEffectManager
import ani.sanin.util.Logger
import ani.sanin.util.StoragePermissions.Companion.accessAlertDialog
import ani.sanin.util.StoragePermissions.Companion.hasDirAccess
import ani.sanin.util.customAlertDialog
import com.anggrayudi.storage.file.extension
import com.google.android.material.appbar.AppBarLayout
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.core.util.lang.launchIO
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

class AnimeWatchFragment : Fragment() {
    private var _binding: FragmentMediaSourceBinding? = null
    private val binding get() = _binding!!
    private val model: MediaDetailsViewModel by activityViewModels()

    private lateinit var media: Media

    private var start = 0
    private var end: Int? = null
    private var style: Int? = null
    private var reverse = false

    private lateinit var headerAdapter: AnimeWatchAdapter
    private lateinit var episodeAdapter: EpisodeAdapter

    // download removed

    var screenWidth = 0f
    private var progress = View.VISIBLE

    var continueEp: Boolean = false
    var loaded = false
    private var loadEpisodesJob: kotlinx.coroutines.Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentMediaSourceBinding.inflate(inflater, container, false)
        return _binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.post {
            if (isAdded) {
                GlassEffectManager.applyGlass(view, GlassComponent.EpisodeDrawer, 0f)
            }
        }
        // download receiver removed


        binding.mediaSourceRecycler.updatePadding(bottom = binding.mediaSourceRecycler.paddingBottom + navBarHeight)
        screenWidth = resources.displayMetrics.widthPixels.dp

        var maxGridSize = (screenWidth / 100f).roundToInt()
        maxGridSize = max(4, maxGridSize - (maxGridSize % 2))

        val gridLayoutManager = GridLayoutManager(requireContext(), maxGridSize)

        gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                val style = episodeAdapter.getItemViewType(position)

                return when (position) {
                    0 -> maxGridSize
                    else -> when (style) {
                        0 -> maxGridSize
                        1 -> 2
                        2 -> 1
                        else -> maxGridSize
                    }
                }
            }
        }

        binding.mediaSourceRecycler.layoutManager = gridLayoutManager

        binding.ScrollTop.setOnClickListener {
            binding.mediaSourceRecycler.scrollToPosition(10)
            binding.mediaSourceRecycler.smoothScrollToPosition(0)
        }
        FocusEffectUtil.applyFocusListener(binding.ScrollTop, binding.ScrollTop)
        binding.mediaSourceRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                val scrollOffset = recyclerView.computeVerticalScrollOffset().toFloat()
                val logo = binding.mediaWatchLogo
                val title = binding.mediaWatchTitle
                val addToList = binding.mediaWatchAddToList
                val maxTranslate = 200f.px.toFloat()
                val translation = -minOf(scrollOffset, maxTranslate)
                val alpha = 1f - (translation / -maxTranslate)
                logo.translationY = translation
                logo.alpha = alpha
                title.translationY = translation
                title.alpha = alpha
                addToList.translationY = translation
                addToList.alpha = alpha

                val position = gridLayoutManager.findFirstVisibleItemPosition()
                if (position > 2) {
                    binding.ScrollTop.translationY = -(navBarHeight + 12.toPx).toFloat()
                    binding.ScrollTop.visibility = View.VISIBLE
                } else {
                    binding.ScrollTop.visibility = View.GONE
                }
            }
        })
        model.scrolledToTop.observe(viewLifecycleOwner) {
            if (it) binding.mediaSourceRecycler.scrollToPosition(0)
        }

        continueEp = model.continueMedia ?: false
        model.getMedia().observe(viewLifecycleOwner) {
            if (it != null) {
                if (this::media.isInitialized) {
                    if (it.anime != null && it.anime?.episodes == null) {
                        it.anime?.episodes = media.anime?.episodes
                    }
                }
                media = it
                media.selected = model.loadSelected(media)

                lifecycleScope.launch(Dispatchers.Main) {
                    val logoUrl = LogoApi.getLogoUrl(media.id)
                    if (!logoUrl.isNullOrBlank()) {
                        binding.mediaWatchLogo.visibility = View.VISIBLE
                        binding.mediaWatchLogo.loadImage(logoUrl)
                    } else {
                        binding.mediaWatchTitle.visibility = View.VISIBLE
                        binding.mediaWatchTitle.text = media.userPreferredName ?: media.name
                    }
                }

                // Add to List button
                val rescueMode: Boolean = PrefManager.getVal(PrefName.RescueMode)
                fun updateAddToList() {
                    val statuses: Array<String> = resources.getStringArray(R.array.status)
                    val statusStrings = resources.getStringArray(R.array.status_anime)
                    val userStatus =
                        if (media.userStatus != null) statusStrings[statuses.indexOf(media.userStatus).coerceAtLeast(0)] else statusStrings[0]
                    if (media.userStatus != null) {
                        binding.mediaWatchAddToList.visibility = View.VISIBLE
                        binding.mediaWatchAddToList.text = userStatus
                    } else {
                        binding.mediaWatchAddToList.setText(R.string.add_list)
                    }
                }
                updateAddToList()
                val fm = requireActivity().supportFragmentManager
                binding.mediaWatchAddToList.setOnClickListener {
                    if (rescueMode) {
                        if (MAL.token != null) {
                            if (fm.findFragmentByTag("dialog") == null)
                                MediaListDialogFragment().show(fm, "dialog")
                        } else snackString("Please login to MAL")
                    } else if (Anilist.userid != null) {
                        if (fm.findFragmentByTag("dialog") == null)
                            MediaListDialogFragment().show(fm, "dialog")
                    } else snackString(getString(R.string.please_login_anilist))
                }
                FocusEffectUtil.applyFocusListener(binding.mediaWatchAddToList)

                if (!PrefManager.getVal<Boolean>(PrefName.SmartSourcePersistence)) {
                    if (media.selected != null) {
                        media.selected!!.sourceIndex = 0
                        media.selected!!.server = null
                    }
                }
                if (media.format == "LOCAL") {
                    val localSourceIndex = AnimeSources.list.indexOfFirst { parser -> parser.name == "Local" }
                        .takeIf { parserIndex -> parserIndex >= 0 } ?: 0
                    media.selected!!.sourceIndex = localSourceIndex
                }

                subscribed =
                    SubscriptionHelper.getSubscriptions().containsKey(media.id)

                style = media.selected!!.recyclerStyle
                reverse = media.selected!!.recyclerReversed

                progress = View.GONE
                binding.mediaInfoProgressBar.visibility = progress

                if (!loaded) {
                    model.watchSources = if (media.isAdult) HAnimeSources else AnimeSources
                    Logger.log(
                        "Watch: initialized sources=${model.watchSources!!.names.size} " +
                            "selected='${model.watchSources!!.list.getOrNull(media.selected!!.sourceIndex)?.name}' " +
                            "(idx ${media.selected!!.sourceIndex})"
                    )

                    val offlineMode =
                        model.watchSources!!.isDownloadedSource(media.selected!!.sourceIndex)

                    headerAdapter = AnimeWatchAdapter(it, this, model.watchSources!!)
                    episodeAdapter =
                        EpisodeAdapter(
                            style ?: PrefManager.getVal(PrefName.AnimeDefaultView),
                            media,
                            this,
                            offlineMode = offlineMode
                        )

                    binding.mediaSourceRecycler.adapter =
                        ConcatAdapter(headerAdapter, episodeAdapter)

                    cancelLoadEpisodesJob("media-observer-init")
                    loadEpisodesJob = lifecycleScope.launch(Dispatchers.IO) {
                        val offline =
                            !isOnline(binding.root.context) || PrefManager.getVal(PrefName.OfflineMode)
                        val isLocal = model.watchSources!!.list.getOrNull(media.selected!!.sourceIndex)?.name == "Local"
                        if (offline && !isLocal) {
                            Logger.log(Log.WARN, "Watch: offline detected, switching to source idx ${model.watchSources!!.list.lastIndex}")
                            media.selected!!.sourceIndex = model.watchSources!!.list.lastIndex
                        }
                        // Load episodes immediately — don't block on metadata APIs
                        model.loadEpisodes(media, media.selected!!.sourceIndex)
                        if (!offline && !isLocal) {
                            launch { model.fetchKitsuEpisodes(media) }
                            launch { model.fetchAnifyEpisodes(media.id) }
                            launch { model.fetchFillerEpisodes(media) }
                        }
                    }
                    logLoadEpisodesJobCompletion("media-observer-init")
                    loaded = true
                } else {
                    reload()
                }
            }
        }
        model.getEpisodes().observe(viewLifecycleOwner) { loadedEpisodes ->
            if (loadedEpisodes != null) {
                val episodes = loadedEpisodes[media.selected!!.sourceIndex]
                if (episodes != null) {
                    Logger.log("Watch: episode list received ${episodes.size} eps from source idx ${media.selected!!.sourceIndex}")
                    viewLifecycleOwner.lifecycleScope.launch {
                        withContext(Dispatchers.Default) {
                            enrichEpisodes(episodes)
                        }
                        media.anime?.episodes = episodes

                        // CHIP GROUP
                        val total = episodes.size
                        val divisions = total.toDouble() / 10
                        start = 0
                        end = null
                        val limit = when {
                            (divisions < 25) -> 25
                            (divisions < 50) -> 50
                            else -> 100
                        }
                        headerAdapter.clearChips()
                        if (total > limit) {
                            val arr = media.anime!!.episodes!!.keys.toTypedArray()
                            val stored = ceil((total).toDouble() / limit).toInt()
                            val position = MathUtils.clamp(media.selected!!.chip, 0, stored - 1)
                            val last = if (position + 1 == stored) total else (limit * (position + 1))
                            start = limit * (position)
                            end = last - 1
                            Logger.log("Watch: generating ${stored} chips (total=$total limit=$limit) selected chip $position")
                            headerAdapter.updateChips(
                                limit,
                                arr,
                                (1..stored).toList().toTypedArray(),
                                position
                            )
                        }

                        headerAdapter.subscribeButton(true)
                        reload()

                        pendingEpisodeClick?.let { ep ->
                            pendingEpisodeClick = null
                            onEpisodeClick(ep)
                        }
                    }
                }
            }
        }

        model.getKitsuEpisodes().observe(viewLifecycleOwner) { i ->
            if (i != null) {
                media.anime?.kitsuEpisodes = i
                refreshEpisodes()
            }
        }

        model.getFillerEpisodes().observe(viewLifecycleOwner) { i ->
            if (i != null) {
                media.anime?.fillerEpisodes = i
                refreshEpisodes()
            }
        }
        model.getAnifyEpisodes().observe(viewLifecycleOwner) { i ->
            if (i != null) {
                media.anime?.anifyEpisodes = i
                refreshEpisodes()
            }
        }
    }

    private fun enrichEpisodes(episodes: MutableMap<String, Episode>) {
        val metadataPriority = PrefManager.getVal<Int>(PrefName.EpisodeMetadataSource)
        episodes.forEach { (i, episode) ->
            if (media.anime?.fillerEpisodes != null) {
                if (media.anime!!.fillerEpisodes!!.containsKey(i)) {
                    val fillerEp = media.anime!!.fillerEpisodes!![i]
                    episode.filler = fillerEp?.filler ?: false
                    episode.date = fillerEp?.date ?: episode.date
                }
            }

            val applyKitsu = {
                if (media.anime?.kitsuEpisodes != null) {
                    if (media.anime!!.kitsuEpisodes!!.containsKey(i)) {
                        val kitsuEp = media.anime!!.kitsuEpisodes!![i]
                        episode.desc = kitsuEp?.desc ?: episode.desc
                        episode.thumb = kitsuEp?.thumb ?: episode.thumb
                    }
                }
            }

            val applyAniZip = {
                if (media.anime?.anifyEpisodes != null) {
                    if (media.anime!!.anifyEpisodes!!.containsKey(i)) {
                        val anifyEp = media.anime!!.anifyEpisodes!![i]
                        episode.desc = anifyEp?.desc ?: episode.desc
                        episode.thumb = anifyEp?.thumb ?: episode.thumb
                        episode.rating = anifyEp?.extra?.get("rating") ?: episode.rating
                        val airDate = anifyEp?.extra?.get("airDate")
                        if (!airDate.isNullOrBlank()) {
                            episode.date = airDate.substringBefore("T")
                        }
                    }
                }
            }

            if (metadataPriority == 0) {
                applyAniZip()
                applyKitsu()
            } else {
                applyKitsu()
                applyAniZip()
            }

            val anilistThumb = media.streamingEpisodes?.firstOrNull { se ->
                se.title?.matches(Regex("""Episode\s*$i[\s:.,]?""", RegexOption.IGNORE_CASE)) == true
            }?.thumbnail
            if (anilistThumb != null) {
                episode.thumb = FileUrl(anilistThumb)
            }

            val anifyTitle = cleanTitle(media.anime?.anifyEpisodes?.get(i)?.title)
            val kitsuTitle = cleanTitle(media.anime?.kitsuEpisodes?.get(i)?.title)
            val jikanTitle = cleanTitle(media.anime?.fillerEpisodes?.get(i)?.title)
            episode.title = anifyTitle ?: kitsuTitle ?: jikanTitle ?: buildFallbackEpisodeTitle(i, episode)
        }
    }

    private fun refreshEpisodes() {
        val eps = media.anime?.episodes
        if (eps != null) {
            Logger.log(
                "Watch: metadata refresh kitsu=${media.anime?.kitsuEpisodes?.size} " +
                    "filler=${media.anime?.fillerEpisodes?.size} anify=${media.anime?.anifyEpisodes?.size}"
            )
            viewLifecycleOwner.lifecycleScope.launch {
                withContext(Dispatchers.Default) {
                    enrichEpisodes(eps)
                }
                episodeAdapter.notifyItemRangeChanged(
                    0, episodeAdapter.arr.size, "metadata"
                )
            }
        }
    }

    private fun cleanTitle(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }
    private fun buildFallbackEpisodeTitle(index: String, currentEpisode: Episode): String {
        val parsedNumber = MediaNameAdapter.findEpisodeNumber(currentEpisode.number)
            ?: MediaNameAdapter.findEpisodeNumber(index)
            ?: currentEpisode.number
        return "Episode $parsedNumber"
    }

    //implement Multi download

    fun multiDelete(episodeNumber: String? = null, n: Int) {
    }

    fun onSourceChange(i: Int): AnimeParser {
        cancelLoadEpisodesJob("onSourceChange")
        val oldIdx = media.selected?.sourceIndex ?: -1
        // Clear stale extractor/server state from all episodes before switching
        media.anime?.episodes?.values?.forEach { ep ->
            ep?.selectedExtractor = null
            ep?.selectedVideo = 0
            ep?.selectedSubtitle = -1
            ep?.extractors = null
            ep?.allStreams = false
        }
        media.anime?.episodes = null
        pendingEpisodeClick = null
        if (::episodeAdapter.isInitialized) {
            episodeAdapter.submitList(emptyList(), style ?: PrefManager.getVal(PrefName.AnimeDefaultView))
            Logger.log("Watch: stale episode list cleared on source change")
        }
        val selected = model.loadSelected(media)
        model.watchSources?.get(selected.sourceIndex)?.showUserTextListener = null
        selected.sourceIndex = i
        selected.server = null
        model.saveSelected(media.id, selected)
        media.selected = selected
        val parser = model.watchSources?.get(i)!!
        Logger.log(
            "Watch: source change '${model.watchSources?.get(oldIdx)?.name ?: "?"}'($oldIdx) -> '${parser.name}'($i)"
        )
        return parser
    }

    fun onLangChange(i: Int) {
        val selected = model.loadSelected(media)
        selected.langIndex = i
        model.saveSelected(media.id, selected)
        media.selected = selected
        Logger.log("Watch: language changed to index $i")
    }

    fun onDubClicked(checked: Boolean) {
        val selected = model.loadSelected(media)
        model.watchSources?.get(selected.sourceIndex)?.selectDub = checked
        selected.preferDub = checked
        model.saveSelected(media.id, selected)
        media.selected = selected
        Logger.log("Watch: dub ${if (checked) "enabled" else "disabled"} for source '${model.watchSources?.get(selected.sourceIndex)?.name}' (idx ${selected.sourceIndex})")
        lifecycleScope.launch(Dispatchers.IO) {
            model.forceLoadEpisode(media, selected.sourceIndex)
        }
    }

    fun loadEpisodes(i: Int, invalidate: Boolean) {
        cancelLoadEpisodesJob("loadEpisodes($i,$invalidate)")
        Logger.log("Watch: loadEpisodes requested source idx=$i invalidate=$invalidate")
        loadEpisodesJob = lifecycleScope.launch(Dispatchers.IO) { model.loadEpisodes(media, i, invalidate) }
        logLoadEpisodesJobCompletion("loadEpisodes($i,$invalidate)")
    }

    private fun cancelLoadEpisodesJob(caller: String) {
        val job = loadEpisodesJob
        if (job != null && job.isActive) {
            Logger.log(Log.WARN, "Watch: CANCEL loadEpisodesJob (active) from $caller")
            job.cancel()
        } else {
            Logger.log("Watch: cancel loadEpisodesJob (inactive/null) from $caller")
        }
        loadEpisodesJob = null
    }

    private fun logLoadEpisodesJobCompletion(tag: String) {
        loadEpisodesJob?.invokeOnCompletion { cause ->
            if (cause != null) {
                Logger.log(
                    Log.ERROR,
                    "Watch: job[$tag] ENDED: ${cause.message}\n${cause.stackTraceToString()}"
                )
            } else {
                Logger.log("Watch: job[$tag] ENDED normally")
            }
        }
    }

    fun loadKitsuEpisodesAsync() {
        lifecycleScope.launch(Dispatchers.IO) { model.loadKitsuEpisodes(media) }
    }

    fun onIconPressed(viewType: Int, rev: Boolean) {
        style = viewType
        reverse = rev
        media.selected!!.recyclerStyle = style
        media.selected!!.recyclerReversed = reverse
        model.saveSelected(media.id, media.selected!!)
        reload()
    }

    fun onChipClicked(i: Int, s: Int, e: Int) {
        media.selected!!.chip = i
        start = s
        end = e
        model.saveSelected(media.id, media.selected!!)
        Logger.log("Watch: chip clicked index $i (episodes range $s-$e)")
        reload()
    }

    var subscribed = false
    fun onNotificationPressed(subscribed: Boolean, source: String) {
        this.subscribed = subscribed
        saveSubscription(media, subscribed)
        snackString(
            if (subscribed) getString(R.string.subscribed_notification, source)
            else getString(R.string.unsubscribed_notification)
        )
    }

    fun openSettings(pkg: AnimeExtension.Installed) {
        Logger.log("Watch: opening extension settings pkg='${pkg.name}' sources=${pkg.sources.size} configurable=${pkg.sources.filterIsInstance<ConfigurableAnimeSource>().size}")
        val changeUIVisibility: (Boolean) -> Unit = { show ->
            val activity = activity
            if (activity is MediaDetailsActivity && isAdded) {
                activity.findViewById<AppBarLayout>(R.id.mediaAppBar)?.isGone = true
                activity.findViewById<View>(R.id.mediaTabContent)?.isVisible = show
                activity.findViewById<CardView>(R.id.mediaCover)?.isGone = true
                activity.findViewById<CardView>(R.id.mediaClose).isVisible = show
                activity.findViewById<View>(R.id.mediaNavPills)?.isVisible = show
                activity.findViewById<FrameLayout>(R.id.fragmentExtensionsContainer).isGone = show
            }
        }
        var itemSelected = false
        val allSettings = pkg.sources.filterIsInstance<ConfigurableAnimeSource>()
        if (allSettings.isNotEmpty()) {
            var selectedSetting = allSettings[0]
            if (allSettings.size > 1) {
                val names =
                    allSettings.map { LanguageMapper.getLanguageName(it.lang) }.toTypedArray()
                requireContext()
                    .customAlertDialog()
                    .apply {
                        setTitle("Select a Source")
                        singleChoiceItems(names) { which ->
                            selectedSetting = allSettings[which]
                            itemSelected = true
                            requireActivity().runOnUiThread {
                                val fragment =
                                    AnimeSourcePreferencesFragment().getInstance(selectedSetting) {
                                        changeUIVisibility(true)
                                        loadEpisodes(media.selected!!.sourceIndex, true)
                                    }
                                parentFragmentManager.beginTransaction()
                                    .setCustomAnimations(R.anim.slide_up, R.anim.slide_down)
                                    .replace(R.id.fragmentExtensionsContainer, fragment)
                                    .addToBackStack(null)
                                    .commit()
                                changeUIVisibility(false)
                            }
                        }
                        onDismiss {
                            if (!itemSelected) {
                                changeUIVisibility(true)
                            }
                        }
                        show()
                    }
            } else {
                requireActivity().runOnUiThread {
                    val fragment =
                        AnimeSourcePreferencesFragment().getInstance(selectedSetting) {
                            changeUIVisibility(true)
                            loadEpisodes(media.selected!!.sourceIndex, true)
                        }
                    parentFragmentManager.beginTransaction().apply {
                        setCustomAnimations(R.anim.slide_up, R.anim.slide_down)
                        replace(R.id.fragmentExtensionsContainer, fragment)
                        addToBackStack(null)
                        commit()
                    }
                    changeUIVisibility(false)
                }
            }
        } else {
            Toast.makeText(requireContext(), "Source is not configurable", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private var pendingEpisodeClick: String? = null

    fun onEpisodeClick(i: String) {
        model.continueMedia = false
        model.saveSelected(media.id, media.selected!!)
        val sourceName = model.watchSources?.get(media.selected!!.sourceIndex)?.name ?: "?"
        if (media.anime?.episodes == null) {
            pendingEpisodeClick = i
            Logger.log("Watch: episode '$i' queued — episodes still loading for source='$sourceName' (idx ${media.selected!!.sourceIndex})")
            return
        }
        Logger.log("Watch: episode clicked '$i' source='$sourceName' (idx ${media.selected!!.sourceIndex})")
        model.onEpisodeClick(media, i, requireActivity().supportFragmentManager)
    }



    @OptIn(UnstableApi::class)

    @kotlin.OptIn(DelicateCoroutinesApi::class)


    @SuppressLint("NotifyDataSetChanged")
    private fun reload() {
        if (!::headerAdapter.isInitialized || !::episodeAdapter.isInitialized) {
            Logger.log(Log.WARN, "Watch: reload skipped, adapters not initialized (header=${::headerAdapter.isInitialized} episode=${::episodeAdapter.isInitialized})")
            return
        }
        Logger.log("Watch: reload start")
        try {
            val selected = model.loadSelected(media)
            Logger.log("Watch: reload selected")

            // Find latest episode for subscription
            selected.latest =
                media.anime?.episodes?.values?.maxOfOrNull { it.number.toFloatOrNull() ?: 0f } ?: 0f
            selected.latest =
                media.userProgress?.toFloat()?.takeIf { selected.latest < it } ?: selected.latest

            model.saveSelected(media.id, selected)
            Logger.log("Watch: reload saved")

            headerAdapter.handleEpisodes()
            episodeAdapter.refreshCache()
            Logger.log("Watch: reload header+cache")

            val watchSources = model.watchSources
            val mediaSelected = media.selected
            if (watchSources == null || mediaSelected == null) {
                Logger.log(Log.WARN, "Watch: reload skipped, watchSources=${watchSources != null} selected=${mediaSelected != null}")
                return
            }
            val isDownloaded = watchSources.isDownloadedSource(mediaSelected.sourceIndex)
            episodeAdapter.offlineMode = isDownloaded
            Logger.log("Watch: reload sources (downloaded=$isDownloaded)")

            val episodes = media.anime?.episodes
            var arr: ArrayList<Episode> = arrayListOf()
            if (episodes != null && episodes.isNotEmpty()) {
                val values = episodes.values.filterNotNull()
                val endIdx = if (end != null && end!! < values.size) end!! else values.size - 1
                if (start <= endIdx) {
                    arr.addAll(values.slice(start..endIdx))
                }
                if (reverse)
                    arr = (arr.reversed() as? ArrayList<Episode>) ?: arr
            }
            Logger.log(
                "Watch: reload list=${arr.size} (total=${episodes?.size} nonNull=${episodes?.values?.count { it != null }}) " +
                    "slice=$start..${end ?: episodes?.size?.minus(1) ?: -1} reverse=$reverse downloaded=$isDownloaded"
            )
            episodeAdapter.submitList(arr, style ?: PrefManager.getVal(PrefName.AnimeDefaultView))
            Logger.log("Watch: reload done")
        } catch (e: Exception) {
            Logger.log(Log.ERROR, "Watch: reload FAILED: ${e.message}")
            Logger.log(e)
        }
    }

    override fun onDestroy() {
        Logger.log("Watch: onDestroy")
        model.watchSources?.flushText()
        super.onDestroy()
        try {
        } catch (_: IllegalArgumentException) {
        }
    }

    var state: Parcelable? = null
    override fun onResume() {
        super.onResume()
        Logger.log("Watch: onResume")
        binding.mediaInfoProgressBar.visibility = progress
        binding.mediaSourceRecycler.layoutManager?.onRestoreInstanceState(state)

        requireActivity().setNavigationTheme()
    }

    override fun onPause() {
        super.onPause()
        Logger.log("Watch: onPause")
        state = binding.mediaSourceRecycler.layoutManager?.onSaveInstanceState()
    }

    override fun onStop() {
        super.onStop()
        Logger.log("Watch: onStop")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Logger.log("Watch: onDestroyView")
    }

    companion object {
        const val ACTION_DOWNLOAD_STARTED = "ani.sanin.ACTION_DOWNLOAD_STARTED"
        const val ACTION_DOWNLOAD_FINISHED = "ani.sanin.ACTION_DOWNLOAD_FINISHED"
        const val ACTION_DOWNLOAD_FAILED = "ani.sanin.ACTION_DOWNLOAD_FAILED"
        const val ACTION_DOWNLOAD_PROGRESS = "ani.sanin.ACTION_DOWNLOAD_PROGRESS"
        const val EXTRA_EPISODE_NUMBER = "extra_episode_number"
        const val EXTRA_DOWNLOADED_BYTES = "extra_downloaded_bytes"
        const val EXTRA_ESTIMATED_TOTAL_BYTES = "extra_estimated_total_bytes"
    }

}
