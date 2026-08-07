package ani.sanin.media.anime

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
//import androidx.compose.ui.test.performClick
//import androidx.compose.ui.geometry.isEmpty
//import androidx.compose.ui.semantics.text
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
//import androidx.glance.visibility
//import androidx.glance.visibility
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withCreated
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ani.sanin.R
import ani.sanin.connections.crashlytics.CrashlyticsInterface
import ani.sanin.copyToClipboard
import ani.sanin.currActivity
import ani.sanin.currContext
import ani.sanin.databinding.BottomSheetSelectorBinding
import ani.sanin.databinding.ItemQualityOptionBinding
import ani.sanin.databinding.ItemStreamBinding
import ani.sanin.databinding.ItemUrlBinding
import ani.sanin.getThemeColor
import ani.sanin.hideSystemBars
import ani.sanin.media.Media
import ani.sanin.media.MediaDetailsViewModel
import ani.sanin.media.MediaType
import ani.sanin.navBarHeight
import ani.sanin.parsers.Subtitle
import ani.sanin.parsers.Video
import ani.sanin.parsers.NativeAnimeParser
import ani.sanin.parsers.VideoExtractor
import ani.sanin.parsers.VideoType
import ani.sanin.setSafeOnClickListener
import ani.sanin.settings.SettingsAddonActivity
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.snackString
import ani.sanin.toast
import ani.sanin.tryWith
import ani.sanin.util.Logger
import ani.sanin.util.customAlertDialog
import ani.sanin.util.FocusEffectUtil
import ani.sanin.util.GlassComponent
import ani.sanin.util.GlassEffectManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import tachiyomi.core.util.lang.launchIO
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.DecimalFormat


class SelectorDialogFragment : DialogFragment() {
    private var _binding: BottomSheetSelectorBinding? = null
    private val binding get() = _binding!!
    val model: MediaDetailsViewModel by activityViewModels()
    private var scope: CoroutineScope = lifecycleScope
    private var media: Media? = null
    private var episode: Episode? = null
    private var prevEpisode: String? = null
    private var makeDefault = false
    private var selected: String? = null
    private var launch: Boolean? = null
    private var isDownloadMenu: Boolean? = null
    private var episodes: ArrayList<String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            selected = it.getString("server")
            launch = it.getBoolean("launch", true)
            prevEpisode = it.getString("prev")
            isDownloadMenu = it.getBoolean("isDownload")
            episodes = it.getStringArrayList("episodes")
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { w ->
            w.setBackgroundDrawableResource(android.R.color.transparent)
            val widthPx = (resources.displayMetrics.widthPixels * 0.92f).toInt()
            w.setLayout(widthPx, WindowManager.LayoutParams.WRAP_CONTENT)
            w.setGravity(Gravity.CENTER)
            w.setDimAmount(0.5f)
            w.statusBarColor = Color.TRANSPARENT
            w.navigationBarColor =
                requireContext().getThemeColor(com.google.android.material.R.attr.colorSurface)
        }
        GlassEffectManager.applyGlassToSheet(binding.selectorContainer, GlassComponent.ServerSheet, 16f)
    }

    @Suppress("DEPRECATION")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetSelectorBinding.inflate(inflater, container, false)
        FocusEffectUtil.applyFocusListener(binding.root)
        return binding.root
    }

    interface EpisodeDownloadListener {
        fun onFinishingUserSelection(selectedServerName: String,
                                     selectedSubtitles: MutableList<String>,
                                     selectedAudioTracks: MutableList<String>)
    }
    class EpisodeDownloadHandler(private val _onFinishingUserSelection: (String, MutableList<String>, MutableList<String>) -> Unit)
        : EpisodeDownloadListener{
        override fun onFinishingUserSelection(selectedServerName: String,
                                              selectedSubtitles: MutableList<String>,
                                              selectedAudioTracks: MutableList<String>) {
            _onFinishingUserSelection(selectedServerName, selectedSubtitles, selectedAudioTracks)
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        var loaded = false
        model.getMedia().observe(viewLifecycleOwner) { m ->
            media = m
            if (media != null && !loaded) {
                loaded = true
                val providerName =
                    model.watchSources?.get(media!!.selected?.sourceIndex ?: 0)?.name
                binding.selectorProviderName.isVisible = providerName != null
                binding.selectorProviderName.text = providerName ?: ""
                binding.selectorAutoProviderName.isVisible = providerName != null
                binding.selectorAutoProviderName.text = providerName ?: ""

                fun fail(resId: Int){
                    snackString(getString(resId))
                    tryWith {
                        dismissAllowingStateLoss()
                    }
                }
                fun initializeVideoServerSelector(ep: Episode, onEpisodeDownloadHandler: EpisodeDownloadHandler? = null) {
                    binding.selectorRecyclerView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                        bottomMargin = navBarHeight
                    }
                    binding.selectorRecyclerView.adapter = null
                    binding.selectorProgressBar.visibility = View.VISIBLE
                    makeDefault = PrefManager.getVal(PrefName.MakeDefault)
                    binding.selectorMakeDefault.isChecked = makeDefault
                    binding.selectorMakeDefault.setOnClickListener {
                        makeDefault = binding.selectorMakeDefault.isChecked
                        PrefManager.setVal(PrefName.MakeDefault, makeDefault)
                    }
                    binding.selectorRecyclerView.layoutManager =
                        LinearLayoutManager(
                            requireActivity(),
                            LinearLayoutManager.VERTICAL,
                            false
                        )
                    val adapter = ExtractorAdapter(onEpisodeDownloadHandler)
                    binding.selectorRecyclerView.adapter = adapter
                    // NyanTV approach: always force fresh fetch from source
                    ep.allStreams = false
                    ep.extractors = null
                    // Show servers progressively as each one finishes loading
                    ep.extractorCallback = { extractor ->
                        scope.launch(Dispatchers.Main) {
                            if (_binding == null || !isAdded) return@launch
                            adapter.add(extractor)
                        }
                    }
                    if (!ep.allStreams) {
                        scope.launch(Dispatchers.IO) {
                            // Phase 1: fetch server names and show them immediately
                            val servers = model.loadEpisodeVideoServers(ep, media!!.selected!!.sourceIndex)
                            if (servers.isNullOrEmpty()) {
                                withContext(Dispatchers.Main) {
                                    if (_binding == null || !isAdded) return@withContext
                                    binding.selectorProgressBar.visibility = View.GONE
                                    fail(R.string.stream_selection_empty)
                                }
                                return@launch
                            }
                            withContext(Dispatchers.Main) {
                                if (_binding == null || !isAdded) return@withContext
                                servers.forEach {
                                    adapter.addServer(
                                        it.name,
                                        it.extraData?.get("quality"),
                                        it.extraData?.get("audio")
                                    )
                                }
                                binding.selectorProgressBar.visibility = View.GONE
                                binding.selectorMakeDefault.post { binding.selectorMakeDefault.requestFocus() }
                            }
                            // Phase 2: fill each server's videos progressively
                            model.loadEpisodeVideos(ep, media!!.selected!!.sourceIndex, servers = servers)
                            withContext(Dispatchers.Main) {
                                if (_binding == null || !isAdded) return@withContext
                                // Remove placeholders for servers that failed or timed out
                                adapter.removePendingPlaceholders()
                                if (adapter.itemCount == 0) {
                                    fail(R.string.stream_selection_empty)
                                }
                                if (model.watchSources!!.isDownloadedSource(media?.selected!!.sourceIndex)) {
                                    adapter.performClick(0)
                                }
                                binding.selectorMakeDefault.post { binding.selectorMakeDefault.requestFocus() }
                            }
                        }
                    } else {
                        media!!.anime?.episodes?.set(media!!.anime?.selectedEpisode!!, ep)
                        adapter.addAll(ep.extractors)
                        if (ep.extractors?.size == 0) {
                            fail(R.string.stream_selection_empty)
                        }
                        if (model.watchSources!!.isDownloadedSource(media?.selected!!.sourceIndex)) {
                            adapter.performClick(0)
                        }
                        binding.selectorProgressBar.visibility = View.GONE
                        binding.selectorMakeDefault.post { binding.selectorMakeDefault.requestFocus() }
                    }
                }
                suspend fun loadEpisodeSingleServer(episodeName: String, selectedServerName: String): Boolean{
                    media?.anime?.selectedEpisode = episodeName
                    val ep = media?.anime?.episodes?.get(media?.anime?.selectedEpisode)!!
                    episode = ep

                    var success = false
                    scope.launch(Dispatchers.IO) {
                        success = model.loadEpisodeSingleVideo(
                            ep,
                            media!!.selected!!,
                            selectedServerName = selectedServerName
                        )
                    }.join()
                    Log.d("AnimeDownloader", "Loading Episode Server State: $success")
                    return success
                }
                fun startEpisodeDownload(episodeName: String, selectedServerName: String,
                                         selectedSubtitles: MutableList<String>,
                                         selectedAudioTracks: MutableList<String>){
                }

                Log.d("AnimeDownloader", "Selected Server for watching: $selected")
                if(episodes.isNullOrEmpty()){
                    fail(R.string.empty_episodes_list)
                }
                if (isDownloadMenu == false) {
                    media?.anime?.selectedEpisode = episodes?.get(0)
                    val ep = media?.anime?.episodes?.get(media?.anime?.selectedEpisode)
                    episode = ep
                    if (ep != null) {
                        if (selected != null && media?.format != "LOCAL") {
                            binding.selectorListContainer.visibility = View.GONE
                            binding.selectorAutoListContainer.visibility = View.VISIBLE
                            binding.selectorAutoText.text = selected
                            binding.selectorCancel.setOnClickListener {
                                media!!.selected!!.server = null
                                model.saveSelected(media!!.id, media!!.selected!!)
                                tryWith {
                                    dismissAllowingStateLoss()
                                }
                            }

                            fun failToList() {
                                snackString(getString(R.string.auto_select_server_error))
                                media!!.selected!!.server = null
                                model.saveSelected(media!!.id, media!!.selected!!)
                                binding.selectorAutoListContainer.visibility = View.GONE
                                binding.selectorListContainer.visibility = View.VISIBLE
                                initializeVideoServerSelector(ep)
                            }

                            fun load() {
                                val size =
                                    if (model.watchSources!!.isDownloadedSource(media!!.selected!!.sourceIndex)) {
                                        ep.extractors?.firstOrNull()?.videos?.size
                                    } else {
                                        ep.extractors?.find { it?.server?.name == selected }?.videos?.size
                                    }

                                if (size != null && size >= media!!.selected!!.video) {
                                    media!!.anime!!.episodes?.get(media!!.anime!!.selectedEpisode!!)?.selectedExtractor =
                                        selected
                                    media!!.anime!!.episodes?.get(media!!.anime!!.selectedEpisode!!)?.selectedVideo =
                                        media!!.selected!!.video
                                    startExoplayer(media!!)
                                } else failToList()
                            }

                            if (ep.extractors?.filter { it?.server?.name == selected }.isNullOrEmpty()) {
                                scope.launch{
                                    val success = withContext(Dispatchers.IO){
                                        loadEpisodeSingleServer(ep.number, selected!!)
                                    }
                                    withContext(Dispatchers.Main) {
                                        if (_binding == null || !isAdded) return@withContext
                                        if (!success) {
                                            failToList()
                                        } else {
                                            load()
                                        }
                                    }
                                }
                            } else load()
                        }
                        else
                            initializeVideoServerSelector(ep)
                    }
                }
                else {
                    binding.selectorMakeDefault.visibility = View.GONE
                    media?.anime?.selectedEpisode = episodes?.get(0)
                    val ep = media?.anime?.episodes?.get(media?.anime?.selectedEpisode)
                    episode = ep

                    if (ep != null) {
                        val downloadHandler =
                            EpisodeDownloadHandler(_onFinishingUserSelection = { selectedServerName,
                                                                                 selectedSubtitles,
                                                                                 selectedAudioTracks ->
                                binding.selectorListContainer.visibility = View.GONE
                                binding.selectorAutoListContainer.visibility = View.VISIBLE
                                binding.selectorTitle.text = "Starting Download"
                                binding.selectorAutoText.text =
                                    "Starting download using server:\n$selectedServerName"
                                binding.selectorCancel.visibility = View.GONE

                                scope.launch(Dispatchers.IO) {
                                    val serverSelectionScope = CoroutineScope(Dispatchers.IO)
                                    val serverSelectionTasks = mutableListOf<Deferred<Unit>>()
                                    for (episodeName in episodes!!.drop(1)) {
                                        serverSelectionTasks.add(serverSelectionScope.async {
                                            if(!loadEpisodeSingleServer(episodeName, selectedServerName)){
                                                Log.d("AnimeDownloader", "Error loading server $selectedServerName for episode $episodeName")
                                                fail(R.string.auto_select_server_error)
                                            }
                                        })
                                    }
                                    serverSelectionTasks.awaitAll()

                                    for(episodeName in episodes!!){
                                        startEpisodeDownload(episodeName, selectedServerName, selectedSubtitles, selectedAudioTracks)
                                    }
                                    tryWith{
                                        dismissAllowingStateLoss()
                                    }
                                }
                            })
                        initializeVideoServerSelector(ep, downloadHandler)
                    }
                }
            }
        }
        super.onViewCreated(view, savedInstanceState)
    }

    private val externalPlayerResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        Logger.log(result.data.toString())
    }

    private fun exportMagnetIntent(episode: Episode, video: Video): Intent {
        val amnis = "com.amnis"
        return Intent(Intent.ACTION_VIEW).apply {
            component = ComponentName(amnis, "$amnis.gui.player.PlayerActivity")
            data = Uri.parse(video.file.url)
            putExtra("title", "${media?.name} - ${episode.title}")
            putExtra("position", 0)
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra("secure_uri", true)
            val headersArray = arrayOf<String>()
            video.file.headers.forEach {
                headersArray.plus(arrayOf(it.key, it.value))
            }
            putExtra("headers", headersArray)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    @SuppressLint("UnsafeOptInUsageError")
    fun startExoplayer(media: Media) {
        if (!isAdded || _binding == null) return
        prevEpisode = null

        episode?.let { ep ->
            val video = ep.extractors?.find {
                it?.server?.name == ep.selectedExtractor
            }?.videos?.getOrNull(ep.selectedVideo)
            video?.file?.url?.let { url ->
            }
        }

        dismissAllowingStateLoss()
        if (launch!!) {
            stopAddingToList()
            val intent = Intent(activity, ExoplayerView::class.java)
            ExoplayerView.media = media
            ExoplayerView.initialized = true
            startActivity(intent)
        } else {
            model.setEpisode(
                media.anime!!.episodes!![media.anime.selectedEpisode!!]!!,
                "startExo no launch"
            )
        }
    }

    private fun stopAddingToList() {
        episode?.extractorCallback = null
        episode?.also {
            it.extractors = it.extractors?.toMutableList()
        }
    }

    private inner class ServerPlaceholder(val name: String, val quality: String?, val subDub: String?)

    private inner class ExtractorAdapter(private val onEpisodeDownloadHandler: EpisodeDownloadHandler? = null) :
        RecyclerView.Adapter<ExtractorAdapter.StreamViewHolder>() {
        val links = mutableListOf<Any>()

        override fun getItemViewType(position: Int): Int =
            if (links[position] is ServerPlaceholder) 0 else 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StreamViewHolder =
            StreamViewHolder(
                ItemStreamBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

        override fun onBindViewHolder(holder: StreamViewHolder, position: Int) {
            when (val item = links.getOrNull(position)) {
                is ServerPlaceholder -> {
                    holder.binding.streamName.text = item.name
                    holder.binding.streamName.visibility = View.VISIBLE
                    val meta = listOfNotNull(
                        item.quality,
                        item.subDub?.uppercase()
                    ).joinToString(" \u00b7 ")
                    holder.binding.streamMeta.text = meta
                    holder.binding.streamMeta.visibility =
                        if (meta.isEmpty()) View.GONE else View.VISIBLE
                    holder.binding.streamLoading.visibility = View.VISIBLE
                    holder.binding.streamRecyclerView.visibility = View.GONE
                }
                is VideoExtractor -> {
                    holder.binding.streamName.text = item.server.name
                    holder.binding.streamName.visibility = View.VISIBLE
                    val meta = listOfNotNull(
                        item.server.extraData?.get("quality"),
                        item.server.extraData?.get("audio")?.uppercase()
                    ).joinToString(" \u00b7 ")
                    holder.binding.streamMeta.text = meta
                    holder.binding.streamMeta.visibility =
                        if (meta.isEmpty()) View.GONE else View.VISIBLE
                    holder.binding.streamLoading.visibility = View.GONE
                    holder.binding.streamRecyclerView.visibility = View.VISIBLE
                    holder.binding.streamHeader.setOnClickListener {
                        performClick(holder.bindingAdapterPosition)
                    }
                    holder.binding.streamRecyclerView.layoutManager = LinearLayoutManager(requireContext())
                    holder.binding.streamRecyclerView.adapter = VideoAdapter(item, onEpisodeDownloadHandler)
                }
                null -> {}
            }
        }

        override fun getItemCount(): Int = links.size

        fun addServer(name: String, quality: String? = null, subDub: String? = null) {
            if (name in disabledServerNames()) return
            links.add(ServerPlaceholder(name, quality, subDub))
            notifyItemInserted(links.size - 1)
        }

        fun add(videoExtractor: VideoExtractor) {
            if (videoExtractor.videos.isNotEmpty() && videoExtractor.server.name !in disabledServerNames()) {
                val idx = links.indexOfFirst { it is ServerPlaceholder && it.name == videoExtractor.server.name }
                if (idx >= 0) {
                    links[idx] = videoExtractor
                    notifyItemChanged(idx)
                } else {
                    links.add(videoExtractor)
                    notifyItemInserted(links.size - 1)
                }
            }
        }

        fun removePendingPlaceholders() {
            val placeholders = links.filterIsInstance<ServerPlaceholder>()
            if (placeholders.isNotEmpty()) {
                links.removeAll(placeholders)
                notifyDataSetChanged()
            }
        }

        fun addAll(extractors: List<VideoExtractor>?) {
            val disabled = disabledServerNames()
            val valid = extractors.orEmpty().toList().filterNotNull()
                .filter { it.server.name !in disabled }
            if (valid.isEmpty()) return
            links.addAll(valid)
            notifyItemRangeInserted(0, links.size)
        }

        fun performClick(position: Int) {
            try {
                val extractor = links.getOrNull(position) as? VideoExtractor ?: return
                val options = buildQualityOptions(extractor)
                if (options.isEmpty()) {
                    playVideo(extractor, 0, remember = false)
                    return
                }
                if (options.size <= 1) {
                    playVideo(extractor, options.first().videoIndex, remember = false)
                    return
                }
                val rememberEnabled = PrefManager.getVal<Boolean>(PrefName.RememberQualityChoice)
                val preferred = getPreferredQuality(extractor.server.name)
                if (rememberEnabled && preferred != null) {
                    val saved = options.firstOrNull { it.label == preferred } ?: return
                    playVideo(extractor, saved.videoIndex, remember = true)
                    return
                }
                showQualityDialog(extractor, options, rememberEnabled)
            } catch (e: Exception) {
                Injekt.get<CrashlyticsInterface>().logException(e)
            }
        }

        private inner class StreamViewHolder(val binding: ItemStreamBinding) :
            RecyclerView.ViewHolder(binding.root) {
            init {
                itemView.isFocusable = false
                itemView.setOnClickListener {
                    performClick(bindingAdapterPosition)
                }
            }
        }
    }

    private inner class VideoAdapter(private val extractor: VideoExtractor,private val onEpisodeDownloadHandler: EpisodeDownloadHandler?) :
        RecyclerView.Adapter<VideoAdapter.UrlViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UrlViewHolder {
            return UrlViewHolder(
                ItemUrlBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
        }

        override fun onBindViewHolder(holder: UrlViewHolder, position: Int) {
            val binding = holder.binding
            val video = extractor.videos[position]
            if (isDownloadMenu == true) {
                binding.urlDownload.visibility = View.VISIBLE
            } else {
                binding.urlDownload.visibility = View.GONE
            }
            val subtitles = extractor.subtitles
            if (subtitles.isNotEmpty()) {
                binding.urlSub.visibility = View.VISIBLE
            } else {
                binding.urlSub.visibility = View.GONE
            }
            binding.urlSub.setOnClickListener {
                if (subtitles.isNotEmpty()) {
                    val subtitleNames = subtitles.map { it.language }
                    var subtitleToDownload: Subtitle? = null
                    requireActivity().customAlertDialog().apply {
                        setTitle(R.string.download_subtitle)
                        singleChoiceItems(subtitleNames.toTypedArray(),  dismissOnSelect = false) { which ->
                            subtitleToDownload = subtitles[which]
                        }
                        setPosButton(R.string.download) {
                            snackString("Download unavailable")
                        }
                        setNegButton(R.string.cancel) {}
                    }.show()
                } else {
                    snackString(R.string.no_subtitles_available)
                }
            }
            binding.urlDownload.setSafeOnClickListener {
                snackString("Download unavailable")
            }
            if (video.format == VideoType.CONTAINER) {
                binding.urlSize.isVisible = video.size != null
                // if video size is null or 0, show "Unknown Size" else show the size in MB
                val sizeText = getString(
                    R.string.mb_size, "${if (video.extraNote != null) " : " else ""}${
                        if (video.size == 0.0) getString(R.string.size_unknown) else DecimalFormat("#.##").format(
                            video.size ?: 0
                        )
                    }"
                )
                binding.urlSize.text = sizeText
            }
            binding.urlNote.visibility = View.VISIBLE
            binding.urlNote.text = video.format.name
            val serverName = extractor.server.name
            val qualityRegex = Regex("\\d{3,4}\\s*p", RegexOption.IGNORE_CASE)
            binding.urlQuality.text = when {
                video.quality != null && !serverName.contains(qualityRegex) ->
                    "${video.quality}p"
                video.quality != null -> serverName
                extractor.videos.size > 1 && video.format == VideoType.M3U8 ->
                    getString(R.string.multi_quality)
                else -> serverName
            }
        }

        override fun getItemCount(): Int = extractor.videos.size

        private inner class UrlViewHolder(val binding: ItemUrlBinding) :
            RecyclerView.ViewHolder(binding.root) {
            init {
                itemView.isFocusable = true
                FocusEffectUtil.applyFocusListener(itemView)
                val borderListener = itemView.onFocusChangeListener
                itemView.setOnFocusChangeListener { v, hasFocus ->
                    borderListener?.onFocusChange(v, hasFocus)
                    if (hasFocus) {
                        var p: android.view.ViewParent? = v.parent
                        while (p != null) {
                            if (p is RecyclerView && p.id == R.id.selectorRecyclerView) {
                                val outerChild = v.parent?.parent?.parent as? View
                                if (outerChild != null) {
                                    val outerPos = p.getChildAdapterPosition(outerChild)
                                    if (outerPos != RecyclerView.NO_POSITION) {
                                        (p.layoutManager as? LinearLayoutManager)
                                            ?.scrollToPositionWithOffset(outerPos, 0)
                                    }
                                }
                                break
                            }
                            p = (p as? View)?.parent
                        }
                    }
                }
                itemView.setSafeOnClickListener {
                    if (isDownloadMenu == true) {
                        binding.urlDownload.performClick()
                        return@setSafeOnClickListener
                    }
                    tryWith(true) {
                        media!!.anime!!.episodes!![media!!.anime!!.selectedEpisode!!]?.selectedExtractor =
                            extractor.server.name
                        media!!.anime!!.episodes!![media!!.anime!!.selectedEpisode!!]?.selectedVideo =
                            bindingAdapterPosition
                        if (PrefManager.getVal<Boolean>(PrefName.RememberQualityChoice)) {
                            savePreferredQuality(
                                extractor.server.name,
                                qualityLabelFor(extractor.videos[bindingAdapterPosition])
                            )
                        }
                        if (makeDefault) {
                            media!!.selected!!.server = extractor.server.name
                            media!!.selected!!.video = bindingAdapterPosition
                            model.saveSelected(media!!.id, media!!.selected!!)
                        }
                        Log.d("AnimeDownloader", "Should start the player")
                        startExoplayer(media!!)
                    }
                }
                itemView.setOnLongClickListener {
                    val video = extractor.videos[bindingAdapterPosition]
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.parse(video.file.url), "video/*")
                    }
                    copyToClipboard(video.file.url, true)
                    dismissAllowingStateLoss()
                    startActivity(Intent.createChooser(intent, "Open Video in :"))
                    true
                }
            }
        }
    }

    private data class QualityOption(val label: String, val videoIndex: Int)

    private fun buildQualityOptions(extractor: VideoExtractor): List<QualityOption> {
        val options = mutableListOf<QualityOption>()
        val masterIndex =
            extractor.videos.indexOfFirst { it.quality == null && it.format == VideoType.M3U8 }
        if (masterIndex >= 0) {
            options += QualityOption(getString(R.string.quality_auto), masterIndex)
        }
        extractor.videos.mapIndexedNotNull { index, v -> v.quality?.let { index to it } }
            .distinctBy { it.second }
            .sortedByDescending { it.second }
            .forEach { (index, quality) -> options += QualityOption("${quality}p", index) }
        return options
    }

    private fun qualityLabelFor(video: Video): String =
        if (video.quality == null) getString(R.string.quality_auto) else "${video.quality}p"

    private fun getPreferredQuality(serverName: String): String? {
        return PrefManager.getVal<List<String>>(PrefName.PreferredQuality)
            .mapNotNull { entry ->
                entry.split('=', limit = 2)
                    .takeIf { it.size == 2 && it[0] == serverName }?.get(1)
            }
            .firstOrNull()
    }

    private fun savePreferredQuality(serverName: String, label: String) {
        val current = PrefManager.getVal<List<String>>(PrefName.PreferredQuality)
            .filterNot { it.startsWith("$serverName=") }
        PrefManager.setVal(PrefName.PreferredQuality, current + "$serverName=$label")
    }

    private fun playVideo(extractor: VideoExtractor, videoIndex: Int, remember: Boolean) {
        try {
            media!!.anime!!.episodes!![media!!.anime!!.selectedEpisode!!]?.selectedExtractor =
                extractor.server.name
            media!!.anime!!.episodes!![media!!.anime!!.selectedEpisode!!]?.selectedVideo =
                videoIndex
            if (remember) {
                PrefManager.setVal(PrefName.RememberQualityChoice, true)
                savePreferredQuality(extractor.server.name, qualityLabelFor(extractor.videos[videoIndex]))
            }
            startExoplayer(media!!)
        } catch (e: Exception) {
            Injekt.get<CrashlyticsInterface>().logException(e)
        }
    }

    private fun showQualityDialog(
        extractor: VideoExtractor,
        options: List<QualityOption>,
        rememberEnabled: Boolean
    ) {
        if (!isAdded || _binding == null) return
        val dialog = Dialog(requireActivity(), R.style.MyPopup)
        dialog.setContentView(R.layout.dialog_quality_select)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.apply {
            setLayout(
                (resources.displayMetrics.widthPixels * 0.92f).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            setDimAmount(0.5f)
            statusBarColor = Color.TRANSPARENT
            navigationBarColor = requireContext().getThemeColor(
                com.google.android.material.R.attr.colorSurface
            )
        }
        GlassEffectManager.applyGlassToSheet(
            dialog.findViewById(R.id.qualityDialogContainer),
            GlassComponent.ServerSheet,
            16f
        )
        dialog.findViewById<TextView>(R.id.qualityDialogTitle).text =
            getString(R.string.select_quality)
        dialog.findViewById<TextView>(R.id.qualityDialogServer).text = extractor.server.name
        val rememberCheck = dialog.findViewById<CheckBox>(R.id.qualityDialogRemember)
        rememberCheck.isChecked = rememberEnabled
        val recycler = dialog.findViewById<RecyclerView>(R.id.qualityDialogRecycler)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        val selectedIndex = options.indexOfFirst { it.label == getPreferredQuality(extractor.server.name) }
        recycler.adapter = QualityDialogAdapter(options, selectedIndex) { option ->
            playVideo(extractor, option.videoIndex, rememberCheck.isChecked)
            dialog.dismiss()
        }
        recycler.post { recycler.requestFocus() }
        dialog.findViewById<View>(R.id.qualityDialogCancel).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    private inner class QualityDialogAdapter(
        private val options: List<QualityOption>,
        private var selectedIndex: Int,
        private val onSelect: (QualityOption) -> Unit
    ) : RecyclerView.Adapter<QualityDialogAdapter.QualityViewHolder>() {

        inner class QualityViewHolder(val binding: ItemQualityOptionBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QualityViewHolder =
            QualityViewHolder(
                ItemQualityOptionBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

        override fun onBindViewHolder(holder: QualityViewHolder, position: Int) {
            val option = options[position]
            holder.binding.qualityOptionLabel.text = option.label
            holder.binding.qualityOptionCheck.isVisible = position == selectedIndex
            holder.itemView.isFocusable = true
            FocusEffectUtil.applyFocusListener(holder.itemView)
            holder.itemView.setOnClickListener {
                selectedIndex = position
                notifyDataSetChanged()
                onSelect(option)
            }
        }

        override fun getItemCount(): Int = options.size
    }

    private fun disabledServerNames(): Set<String> {
        val provider = model.watchSources?.get(media?.selected?.sourceIndex ?: 0)
        val saveName = (provider as? NativeAnimeParser)?.saveName ?: return emptySet()
        return PrefManager.getVal<List<String>>(PrefName.ProviderDisabledServers)
            .mapNotNull { entry ->
                entry.split('=', limit = 2)
                    .takeIf { it.size == 2 && it[0] == saveName }?.get(1)
            }
            .toSet()
    }

    companion object {
        fun newInstance(
            server: String? = null,
            la: Boolean = true,
            prev: String? = null,
            isDownload: Boolean,
            episodes: ArrayList<String>
        ): SelectorDialogFragment =
            SelectorDialogFragment().apply {
                arguments = Bundle().apply {
                    putString("server", server)
                    putBoolean("launch", la)
                    putString("prev", prev)
                    putBoolean("isDownload", isDownload)
                    putStringArrayList("episodes", episodes)
                }
            }
    }

    override fun onSaveInstanceState(outState: Bundle) {}

    override fun onDismiss(dialog: DialogInterface) {
        if (launch == false) {
            activity?.hideSystemBars()
            model.epChanged.postValue(true)
            if (prevEpisode != null) {
                media?.anime?.selectedEpisode = prevEpisode
                model.setEpisode(media?.anime?.episodes?.get(prevEpisode) ?: return, "prevEp")
            }
        }
        super.onDismiss(dialog)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
