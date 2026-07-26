package ani.sanin.media.anime

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.NumberPicker
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getString
import androidx.core.content.ContextCompat.startActivity
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import ani.sanin.FileUrl
import ani.sanin.FocusableDropdownAdapter
import ani.sanin.R
import ani.sanin.currActivity
import ani.sanin.currContext
import ani.sanin.databinding.DialogLayoutBinding
import ani.sanin.databinding.ItemChipBinding
import ani.sanin.databinding.ItemMediaSourceBinding
import ani.sanin.displayTimer
import ani.sanin.isOnline
import ani.sanin.loadImage
import ani.sanin.media.Media
import ani.sanin.media.MediaDetailsActivity
import ani.sanin.media.MediaNameAdapter

import ani.sanin.media.SourceSearchDialogFragment
import ani.sanin.openSettings
import ani.sanin.others.LanguageMapper
import ani.sanin.others.webview.CookieCatcher
import ani.sanin.parsers.AnimeSources
import ani.sanin.parsers.DynamicAnimeParser
// OfflineAnimeParser removed
import ani.sanin.parsers.WatchSources
import ani.sanin.px
import ani.sanin.settings.FAQActivity
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.snackString
import ani.sanin.toast
import ani.sanin.util.FocusEffectUtil
import ani.sanin.util.customAlertDialog
import com.google.android.material.chip.Chip
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.notification.Notifications.CHANNEL_SUBSCRIPTION_CHECK
import eu.kanade.tachiyomi.util.system.WebViewUtil
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch


class AnimeWatchAdapter(
    private val media: Media,
    private val fragment: AnimeWatchFragment,
    private val watchSources: WatchSources
) : RecyclerView.Adapter<AnimeWatchAdapter.ViewHolder>() {
    private var autoSelect = true
    var subscribe: MediaDetailsActivity.PopImageButton? = null
    private var _binding: ItemMediaSourceBinding? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val bind =
            ItemMediaSourceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(bind)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val binding = holder.binding
        _binding = binding

        binding.faqbutton.setOnClickListener {
            startActivity(
                fragment.requireContext(),
                Intent(fragment.requireContext(), FAQActivity::class.java),
                null
            )
        }
        binding.animeSourceDubbed.isChecked = media.selected!!.preferDub
        binding.animeSourceDubbedText.text =
            if (media.selected!!.preferDub) currActivity()!!.getString(R.string.dubbed) else currActivity()!!.getString(
                R.string.subbed
            )

        // PreferDub
        var changing = false
        binding.animeSourceDubbed.setOnCheckedChangeListener { _, isChecked ->
            binding.animeSourceDubbedText.text =
                if (isChecked) currActivity()!!.getString(R.string.dubbed) else currActivity()!!.getString(
                    R.string.subbed
                )
            if (!changing) fragment.onDubClicked(isChecked)
        }

        // Wrong Title
        binding.mediaSourceSearch.setOnClickListener {
            SourceSearchDialogFragment().show(
                fragment.requireActivity().supportFragmentManager,
                null
            )
        }
        val offline = !isOnline(binding.root.context) || PrefManager.getVal(PrefName.OfflineMode)

        binding.mediaSourcePillScroll.isGone = offline
        binding.mediaSourceSettings.isGone = offline
        binding.mediaSourceSearch.isGone = offline
        binding.mediaSourceTitle.isGone = offline

        // Source Selection — Pills
        val displayNames = watchSources.displayNames.filter { it != "Local" }
        var source =
            media.selected!!.sourceIndex.let { if (it >= watchSources.names.size) 0 else it }
        setLanguageList(media.selected!!.langIndex, source)
        if (watchSources.names.isNotEmpty() && source in 0 until watchSources.names.size) {
            watchSources[source].apply {
                this.selectDub = media.selected!!.preferDub
                binding.mediaSourceTitle.text = showUserText
                showUserTextListener = {
                    MainScope().launch {
                        binding.mediaSourceTitle.text = it
                        binding.mediaSourceSpinner.isVisible = it.startsWith("Searching")
                    }
                }
                binding.mediaSourceSpinner.isVisible = showUserText.startsWith("Searching")
                binding.animeSourceDubbedCont.isVisible = true
            }
        }

        binding.mediaSourceTitle.isSelected = true
        val chipGroup = binding.mediaSourceChipGroupPill
        chipGroup.removeAllViews()
        val screenWidth = fragment.screenWidth.px
        displayNames.filter { !it.startsWith("───") }.forEachIndexed { _, name ->
            val chip = LayoutInflater.from(chipGroup.context).inflate(R.layout.item_chip, chipGroup, false) as Chip
            chip.text = name
            chip.isCheckable = true
            chip.isClickable = true
            chip.isFocusable = true
            val actualIndex = watchSources.names.indexOf(name)
            if (actualIndex >= 0) {
                chip.tag = actualIndex
                if (actualIndex == source) chip.isChecked = true
                chip.setOnClickListener {
                        autoSelect = false
                        val idx = chip.tag as Int
                        if (idx == source) return@setOnClickListener
                        fragment.onSourceChange(idx).apply {
                            binding.mediaSourceTitle.text = showUserText
                            showUserTextListener = {
                                MainScope().launch {
                                    binding.mediaSourceTitle.text = it
                                    binding.mediaSourceSpinner.isVisible = it.startsWith("Searching")
                                }
                            }
                            binding.mediaSourceSpinner.isVisible = showUserText.startsWith("Searching")
                            changing = true
                            binding.animeSourceDubbed.isChecked = selectDub
                            changing = false
                            binding.animeSourceDubbedCont.isVisible = true
                            source = idx
                            setLanguageList(0, idx)
                        }
                        subscribeButton(false)
                        fragment.loadEpisodes(idx, true)
                        binding.mediaSourcePillScroll.smoothScrollTo(
                            (chip.left - screenWidth / 2) + (chip.width / 2),
                            0
                        )
                    }
                }
            chipGroup.addView(chip)
        }

        binding.mediaSourceLanguage.setOnItemClickListener { _, _, i, _ ->
            // Check if 'extension' and 'selected' properties exist and are accessible
            (watchSources[source] as? DynamicAnimeParser)?.let { ext ->
                ext.sourceLanguage = i
                fragment.onLangChange(i)
                fragment.onSourceChange(media.selected!!.sourceIndex).apply {
                    binding.mediaSourceTitle.text = showUserText
                    showUserTextListener = {
                        MainScope().launch {
                            binding.mediaSourceTitle.text = it
                            binding.mediaSourceSpinner.isVisible = it.startsWith("Searching")
                        }
                    }
                    binding.mediaSourceSpinner.isVisible = showUserText.startsWith("Searching")
                    changing = true
                    binding.animeSourceDubbed.isChecked = selectDub
                    changing = false
                    binding.animeSourceDubbedCont.isVisible = isDubAvailableSeparately()
                    setLanguageList(i, source)
                }
                subscribeButton(false)
                fragment.loadEpisodes(media.selected!!.sourceIndex, true)
            } ?: run { }
        }

        // Settings
        binding.mediaSourceSettings.setOnClickListener {
            val parser = watchSources[source]
            if (parser is DynamicAnimeParser) {
                fragment.openSettings(parser.extension)
            } else {
                toast("Source not configurable")
            }
        }

        FocusEffectUtil.applyFocusListener(binding.mediaSourcePillScroll, binding.mediaSourcePillScroll)
        FocusEffectUtil.applyFocusListener(binding.mediaSourceSearch, binding.mediaSourceSearch)
        binding.mediaSourceSearch.nextFocusRightId = R.id.mediaSourceSearch
        FocusEffectUtil.applyFocusListener(binding.mediaSourceSettings, binding.mediaSourceSettings, true)
        FocusEffectUtil.applyFocusListener(binding.animeSourceDubbed, binding.animeSourceDubbed, true)
        binding.animeSourceDubbed.nextFocusUpId = R.id.mediaSourceSettings
        binding.animeSourceDubbed.nextFocusLeftId = R.id.mediaSourcePillScroll
        binding.animeSourceDubbed.nextFocusRightId = R.id.mediaSourceSearch
        binding.mediaSourceSettings.nextFocusDownId = R.id.animeSourceDubbed
        FocusEffectUtil.applyFocusListener(binding.mediaSourceSubscribe, binding.mediaSourceSubscribe, true)
        FocusEffectUtil.applyFocusListener(binding.mediaNestedButton, binding.mediaNestedButton, true)
        binding.mediaNestedButton.nextFocusRightId = R.id.mediaNestedButton
        FocusEffectUtil.applyFocusListener(binding.sourceContinue)
        binding.mediaNestedButton.nextFocusDownId = R.id.ScrollTop
        binding.mediaSourceSubscribe.nextFocusDownId = R.id.ScrollTop
        binding.animeSourceDubbed.nextFocusDownId = R.id.ScrollTop
        binding.faqbutton.nextFocusDownId = R.id.ScrollTop

        // Icons

        // Subscribe
        subscribe = MediaDetailsActivity.PopImageButton(
            fragment.lifecycleScope,
            binding.mediaSourceSubscribe,
            R.drawable.ic_round_notifications_active_24,
            R.drawable.ic_round_notifications_none_24,
            R.color.bg_opp,
            R.color.violet_400,
            fragment.subscribed,
            true
        ) { enabled ->
            fragment.onNotificationPressed(enabled, watchSources.names.getOrElse(source) { "" })
        }

        subscribeButton(false)

        binding.mediaSourceSubscribe.setOnLongClickListener {
            openSettings(fragment.requireContext(), CHANNEL_SUBSCRIPTION_CHECK)
        }

        // Nested Button
        binding.mediaNestedButton.setOnClickListener {
            val dialogBinding = DialogLayoutBinding.inflate(fragment.layoutInflater)
            dialogBinding.apply {
                var refresh = false
                var run = false
                var reversed = media.selected!!.recyclerReversed
                var style =
                    media.selected!!.recyclerStyle ?: PrefManager.getVal(PrefName.AnimeDefaultView)

                mediaSourceTop.rotation = if (reversed) -90f else 90f
                sortText.text = if (reversed) "Down to Up" else "Up to Down"
                mediaSourceTop.setOnClickListener {
                    reversed = !reversed
                    mediaSourceTop.rotation = if (reversed) -90f else 90f
                    sortText.text = if (reversed) "Down to Up" else "Up to Down"
                    run = true
                }

                var metadataApi = PrefManager.getVal<Int>(PrefName.EpisodeMetadataSource) // 0 or 1
                metadataApiText.text = if (metadataApi == 0) "Kitsu" else "AniZip"
                metadataApiTop.setOnClickListener {
                    metadataApi = if (metadataApi == 0) 1 else 0
                    metadataApiText.text = if (metadataApi == 0) "Kitsu" else "AniZip"
                    PrefManager.setVal(PrefName.EpisodeMetadataSource, metadataApi)
                    
                    if (metadataApi == 0) {
                        fragment.loadKitsuEpisodesAsync()
                    }
                    refresh = true
                }
                
                // Grids
                var selected = when (style) {
                    0 -> mediaSourceList
                    1 -> mediaSourceGrid
                    2 -> mediaSourceCompact
                    else -> mediaSourceList
                }
                when (style) {
                    0 -> layoutText.setText(R.string.list)
                    1 -> layoutText.setText(R.string.grid)
                    2 -> layoutText.setText(R.string.compact)
                    else -> mediaSourceList
                }
                selected.alpha = 1f
                fun selected(it: ImageButton) {
                    selected.alpha = 0.33f
                    selected = it
                    selected.alpha = 1f
                }
                mediaSourceList.setOnClickListener {
                    selected(it as ImageButton)
                    style = 0
                    layoutText.setText(R.string.list)
                    run = true
                }
                mediaSourceGrid.setOnClickListener {
                    selected(it as ImageButton)
                    style = 1
                    layoutText.setText(R.string.grid)
                    run = true
                }
                mediaSourceCompact.setOnClickListener {
                    selected(it as ImageButton)
                    style = 2
                    layoutText.setText(R.string.compact)
                    run = true
                }
                mediaWebviewContainer.setOnClickListener {
                    if (!WebViewUtil.supportsWebView(fragment.requireContext())) {
                        toast(R.string.webview_not_installed)
                    }
                    // Start CookieCatcher activity
                    if (watchSources.names.isNotEmpty() && source in 0 until watchSources.names.size) {
                        val sourceAHH = watchSources[source] as? DynamicAnimeParser
                        val sourceHttp =
                            sourceAHH?.extension?.sources?.firstOrNull() as? AnimeHttpSource
                        val url = sourceHttp?.baseUrl
                        url?.let {
                            refresh = true
                            val headersMap = try {
                                sourceHttp.headers.toMultimap()
                                    .mapValues { it.value.getOrNull(0) ?: "" }
                            } catch (e: Exception) {
                                emptyMap()
                            }
                            val intent =
                                Intent(fragment.requireContext(), CookieCatcher::class.java)
                                    .putExtra("url", url)
                                    .putExtra("headers", headersMap as HashMap<String, String>)
                            startActivity(fragment.requireContext(), intent, null)
                        }
                    }
                }

                //implement Multi download
                downloadNo.setText("0")
                if (media.format == "LOCAL") {
                    animeDownloadContainer.visibility = View.GONE
                    mediaWebviewContainer.visibility = View.GONE
                }
                mediaDownloadTop.setOnClickListener {
                    // Alert dialog asking for the number of Episodes to download
                    fragment.requireContext().customAlertDialog().apply {
                        setTitle("Multi Episode Downloader")
                        setMessage("Enter the number of episodes to download")
                        val input = NumberPicker(currContext())
                        input.minValue = 1
                        input.maxValue = 20
                        input.value = 1
                        setCustomView(input)
                        setPosButton(R.string.ok) {
                            downloadNo.setText("${input.value}")
                        }
                        setNegButton(R.string.cancel)
                        show()
                    }
                }

                resetProgress.setOnClickListener {
                    fragment.requireContext().customAlertDialog().apply {
                        setTitle(" Delete Progress for all episodes of ${media.nameRomaji}")
                        setMessage("This will delete all the locally stored progress for all episodes")
                        setPosButton(R.string.ok) {
                            val prefix = "${media.id}_"
                            val regex = Regex("^${prefix}\\d+$")

                            PrefManager.getAllCustomValsForMedia(prefix)
                                .keys
                                .filter { it.matches(regex) }
                                .onEach { key -> PrefManager.removeCustomVal(key) }
                            snackString("Deleted the progress of all Episodes for ${media.nameRomaji}")
                        }
                        setNegButton(R.string.no)
                        show()
                    }
                }

                resetProgressDef.text = getString(currContext()!!, R.string.clear_stored_episode)

                // Hidden
                mangaScanlatorContainer.visibility = View.GONE
                //animeDownloadContainer.visibility = View.GONE
                fragment.requireContext().customAlertDialog().apply {
                    setTitle("Options")
                    setCustomView(dialogBinding.root)
                    setPosButton("OK") {
                        if (run) fragment.onIconPressed(style, reversed)
                        if (downloadNo.text.toString() != "0") {

                        }
                        if (refresh) fragment.loadEpisodes(source, true)
                    }
                    setNegButton("Cancel") {
                        if (refresh) fragment.loadEpisodes(source, true)
                    }
                    show()
                }
            }
        }
        // Episode Handling
        handleEpisodes()

        //clear progress
        binding.sourceTitle.setOnLongClickListener {
            fragment.requireContext().customAlertDialog().apply {
                setTitle(" Delete Progress for all episodes of ${media.nameRomaji}")
                setMessage("This will delete all the locally stored progress for all episodes")
                setPosButton(R.string.ok) {
                    val prefix = "${media.id}_"
                    val regex = Regex("^${prefix}\\d+$")

                    PrefManager.getAllCustomValsForMedia(prefix)
                        .keys
                        .filter { it.matches(regex) }
                        .onEach { key -> PrefManager.removeCustomVal(key) }
                    snackString("Deleted the progress of all Episodes for ${media.nameRomaji}")
                }
                setNegButton(R.string.no)
                show()
            }
            true
        }
    }

    fun subscribeButton(enabled: Boolean) {
        subscribe?.enabled(enabled)
    }

    // Chips
    fun updateChips(limit: Int, names: Array<String>, arr: Array<Int>, selected: Int = 0) {
        val binding = _binding
        if (binding != null) {
            val screenWidth = fragment.screenWidth.px
            var select: Chip? = null
            for (position in arr.indices) {
                val last = if (position + 1 == arr.size) names.size else (limit * (position + 1))
                val chip =
                    ItemChipBinding.inflate(
                        LayoutInflater.from(fragment.context),
                        binding.mediaSourceChipGroup,
                        false
                    ).root
                chip.isCheckable = true
                chip.isFocusable = true
                fun selected() {
                    chip.isChecked = true
                    binding.mediaWatchChipScroll.smoothScrollTo(
                        (chip.left - screenWidth / 2) + (chip.width / 2),
                        0
                    )
                }

                val chipText = "${names[limit * (position)]} - ${names[last - 1]}"
                chip.text = chipText
                chip.setTextColor(
                    ContextCompat.getColorStateList(
                        fragment.requireContext(),
                        R.color.chip_text_color
                    )
                )

                chip.setOnClickListener {
                    selected()
                    fragment.onChipClicked(position, limit * (position), last - 1)
                }
                binding.mediaSourceChipGroup.addView(chip)
                if (selected == position) {
                    selected()
                    select = chip
                }
            }
            if (select != null)
                binding.mediaWatchChipScroll.apply {
                    post {
                        scrollTo(
                            (select.left - screenWidth / 2) + (select.width / 2),
                            0
                        )
                    }
                }
        }
    }

    fun clearChips() {
        _binding?.mediaSourceChipGroup?.removeAllViews()
    }

    fun handleEpisodes() {
        val binding = _binding
        if (binding != null) {
            if (media.anime?.episodes != null) {
                val episodes = media.anime.episodes!!.keys.toTypedArray()

                val anilistEp = (media.userProgress ?: 0).plus(1)
                val appEp = PrefManager.getCustomVal<String?>(
                    "${media.id}_current_ep", ""
                )?.toIntOrNull() ?: 1

                var continueEp = (if (anilistEp > appEp) anilistEp else appEp).toString()
                if (episodes.contains(continueEp)) {
                    binding.sourceContinue.visibility = View.VISIBLE
                    handleProgress(
                        binding.itemMediaProgressCont,
                        binding.itemMediaProgress,
                        binding.itemMediaProgressEmpty,
                        media.id,
                        continueEp
                    )
                    if ((binding.itemMediaProgress.layoutParams as LinearLayout.LayoutParams).weight > PrefManager.getVal<Float>(
                            PrefName.WatchPercentage
                        )
                    ) {
                        val e = episodes.indexOf(continueEp)
                        if (e != -1 && e + 1 < episodes.size) {
                            continueEp = episodes[e + 1]
                            handleProgress(
                                binding.itemMediaProgressCont,
                                binding.itemMediaProgress,
                                binding.itemMediaProgressEmpty,
                                media.id,
                                continueEp
                            )
                        }
                    }
                    val ep = media.anime.episodes!![continueEp]!!

                    val cleanedTitle = ep.title?.let { MediaNameAdapter.removeEpisodeNumber(it) }

                    binding.itemMediaImage.loadImage(
                        ep.thumb ?: FileUrl[media.banner ?: media.cover], 0
                    )
                    if (ep.filler) binding.itemEpisodeFillerView.visibility = View.VISIBLE

                    binding.mediaSourceContinueText.text =
                        currActivity()!!.getString(
                            R.string.continue_episode, ep.number, if (ep.filler)
                                currActivity()!!.getString(R.string.filler_tag)
                            else
                                "", cleanedTitle
                        )
                    binding.sourceContinue.setOnClickListener {
                        fragment.onEpisodeClick(continueEp)
                    }
                    if (fragment.continueEp) {
                        if (
                            (binding.itemMediaProgress.layoutParams as LinearLayout.LayoutParams)
                                .weight < PrefManager.getVal<Float>(PrefName.WatchPercentage)
                        ) {
                            binding.sourceContinue.performClick()
                            fragment.continueEp = false
                        }
                    }
                } else {
                    binding.sourceContinue.visibility = View.GONE
                }

                binding.sourceProgressBar.visibility = View.GONE

                val sourceFound = media.anime.episodes!!.isNotEmpty()
                val isDownloadedSource =
                    false

                if (isDownloadedSource) {
                    binding.sourceNotFound.text = if (sourceFound) {
                        currActivity()!!.getString(R.string.source_not_found)
                    } else {
                        currActivity()!!.getString(R.string.download_not_found)
                    }
                } else {
                    binding.sourceNotFound.text =
                        currActivity()!!.getString(R.string.source_not_found)
                }

                binding.sourceNotFound.isGone = sourceFound
                binding.faqbutton.isGone = sourceFound

                if (!sourceFound && PrefManager.getVal(PrefName.SearchSources) && autoSelect) {
                    val nextIndex = media.selected!!.sourceIndex + 1
                    if (nextIndex < watchSources.names.size) {
                        fragment.onSourceChange(nextIndex).apply {
                            binding.mediaSourceTitle.text = showUserText
                            showUserTextListener = {
                                MainScope().launch {
                                    binding.mediaSourceTitle.text = it
                                    binding.mediaSourceSpinner.isVisible = it.startsWith("Searching")
                                }
                            }
                            binding.mediaSourceSpinner.isVisible = showUserText.startsWith("Searching")
                            binding.animeSourceDubbed.isChecked = selectDub
                            binding.animeSourceDubbedCont.isVisible = isDubAvailableSeparately()
                            setLanguageList(0, nextIndex)
                        }
                        subscribeButton(false)
                        fragment.loadEpisodes(nextIndex, false)
                        _binding?.mediaSourceChipGroupPill?.let { cg ->
                            for (i in 0 until cg.childCount) {
                                val c = cg.getChildAt(i) as? Chip ?: continue
                                c.isChecked = c.tag == nextIndex
                            }
                        }
                    }
                }
            } else {
                binding.sourceContinue.visibility = View.GONE
                binding.sourceNotFound.visibility = View.GONE
                binding.faqbutton.visibility = View.GONE
                clearChips()
                binding.sourceProgressBar.visibility = View.VISIBLE
            }
        }
    }

    private fun setLanguageList(lang: Int, source: Int) {
        val binding = _binding
        if (watchSources is AnimeSources) {
            val parser = watchSources[source] as? DynamicAnimeParser
            if (parser != null) {
                (watchSources[source] as? DynamicAnimeParser)?.let { ext ->
                    ext.sourceLanguage = lang
                }
                try {
                    binding?.mediaSourceLanguage?.setText(parser.extension.sources[lang].lang)
                } catch (e: IndexOutOfBoundsException) {
                    binding?.mediaSourceLanguage?.setText(
                        parser.extension.sources.firstOrNull()?.lang ?: "Unknown"
                    )
                }
                val adapter = FocusableDropdownAdapter(
                    fragment.requireContext(),
                    R.layout.item_dropdown,
                    parser.extension.sources.map { LanguageMapper.getLanguageName(it.lang) }
                )
                val items = adapter.count

                binding?.mediaSourceLanguageContainer?.visibility =
                    if (items > 1) View.VISIBLE else View.GONE
                binding?.mediaSourceLanguage?.setAdapter(adapter)

            }
        }
    }

    override fun getItemCount(): Int = 1

    inner class ViewHolder(val binding: ItemMediaSourceBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            displayTimer(media, binding.animeSourceContainer)
        }
    }
}
