package ani.sanin.media

import android.annotation.SuppressLint
import android.content.Intent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardActions
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.HORIZONTAL
import ani.sanin.R
import ani.sanin.connections.anilist.Anilist
import ani.sanin.connections.anilist.AnilistSearch.SearchType
import ani.sanin.connections.anilist.ChipItem
import ani.sanin.connections.anilist.toChipList
import ani.sanin.connections.anilist.removeChip
import ani.sanin.databinding.ItemChipBinding
import ani.sanin.openLinkInBrowser
import ani.sanin.others.imagesearch.ImageSearchActivity
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.util.FocusEffectUtil
import com.google.android.material.checkbox.MaterialCheckBox.STATE_CHECKED
import com.google.android.material.checkbox.MaterialCheckBox.STATE_INDETERMINATE
import com.google.android.material.checkbox.MaterialCheckBox.STATE_UNCHECKED

class SearchAdapter(private val activity: SearchActivity, private val type: SearchType) :
    HeaderInterface() {

    private var searchText by mutableStateOf("")
    private val searchFocusRequester = FocusRequester()

    private fun updateFilterTextViewDrawable() {
        val filterDrawable = when (activity.aniMangaResult.sort) {
            Anilist.sortBy[0] -> R.drawable.ic_round_area_chart_24
            Anilist.sortBy[1] -> R.drawable.ic_round_filter_peak_24
            Anilist.sortBy[2] -> R.drawable.ic_round_star_graph_24
            Anilist.sortBy[3] -> R.drawable.ic_round_new_releases_24
            Anilist.sortBy[4] -> R.drawable.ic_round_filter_list_24
            Anilist.sortBy[5] -> R.drawable.ic_round_filter_list_24_reverse
            Anilist.sortBy[6] -> R.drawable.ic_round_assist_walker_24
            else -> R.drawable.ic_round_filter_alt_24
        }
        binding.searchFilter.setIconResource(filterDrawable)
    }

    private fun triggerSearch() {
        activity.aniMangaResult.apply {
            search = searchText.ifBlank { null }
        }
        if (searchText.equals("hentai", true)) {
            openLinkInBrowser("https://www.youtube.com/watch?v=GgJrEOo0QoA")
        }
        activity.search()
    }

    private fun onTextChanged(s: String) {
        searchTextValue = s
        searchText = s
        if (s.isBlank()) {
            activity.emptyMediaAdapter()
            binding.searchHistoryList.postDelayed({ setHistoryVisibility(true) }, 200)
        } else {
            setHistoryVisibility(false)
            triggerSearch()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: SearchHeaderViewHolder, position: Int) {
        binding = holder.binding

        searchHistoryAdapter = SearchHistoryAdapter(type) {
            searchText = it
            searchTextValue = it
        }
        binding.searchHistoryList.layoutManager = LinearLayoutManager(binding.root.context)
        binding.searchHistoryList.adapter = searchHistoryAdapter

        if (activity.searchType != SearchType.MANGA && activity.searchType != SearchType.ANIME) {
            throw IllegalArgumentException("Invalid search type (wrong adapter)")
        }

        when (activity.style) {
            0 -> {
                binding.searchResultGrid.alpha = 1f
                binding.searchResultList.alpha = 0.33f
            }

            1 -> {
                binding.searchResultList.alpha = 1f
                binding.searchResultGrid.alpha = 0.33f
            }
        }

        searchText = activity.aniMangaResult.search ?: ""
        searchTextValue = searchText

        var adult = activity.aniMangaResult.isAdult
        var listOnly = activity.aniMangaResult.onList

        binding.searchAdultCheck.isChecked = adult
        binding.searchList.isChecked = listOnly == true

        binding.searchChipRecycler.adapter = SearchChipAdapter(activity, this).also {
            activity.updateChips = { it.update() }
        }

        binding.searchChipRecycler.layoutManager =
            LinearLayoutManager(binding.root.context, HORIZONTAL, false)

        FocusEffectUtil.applyFocusListener(
            binding.searchList,
            binding.searchAdultCheck,
            binding.searchByImage,
            binding.clearHistory,
            binding.searchFilter,
            binding.searchResultGrid,
            binding.searchResultList
        )
        binding.searchFilter.setOnClickListener {
            SearchFilterBottomDialog.newInstance().show(activity.supportFragmentManager, "dialog")
        }
        binding.searchFilter.setOnLongClickListener {
            val popupMenu = PopupMenu(activity, binding.searchFilter)
            popupMenu.menuInflater.inflate(R.menu.sortby_filter_menu, popupMenu.menu)
            popupMenu.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.sort_by_score -> {
                        activity.aniMangaResult.sort = Anilist.sortBy[0]
                        activity.updateChips.invoke()
                        activity.search()
                        updateFilterTextViewDrawable()
                    }

                    R.id.sort_by_popular -> {
                        activity.aniMangaResult.sort = Anilist.sortBy[1]
                        activity.updateChips.invoke()
                        activity.search()
                        updateFilterTextViewDrawable()
                    }

                    R.id.sort_by_trending -> {
                        activity.aniMangaResult.sort = Anilist.sortBy[2]
                        activity.updateChips.invoke()
                        activity.search()
                        updateFilterTextViewDrawable()
                    }

                    R.id.sort_by_recent -> {
                        activity.aniMangaResult.sort = Anilist.sortBy[3]
                        activity.updateChips.invoke()
                        activity.search()
                        updateFilterTextViewDrawable()
                    }

                    R.id.sort_by_a_z -> {
                        activity.aniMangaResult.sort = Anilist.sortBy[4]
                        activity.updateChips.invoke()
                        activity.search()
                        updateFilterTextViewDrawable()
                    }

                    R.id.sort_by_z_a -> {
                        activity.aniMangaResult.sort = Anilist.sortBy[5]
                        activity.updateChips.invoke()
                        activity.search()
                        updateFilterTextViewDrawable()
                    }

                    R.id.sort_by_pure_pain -> {
                        activity.aniMangaResult.sort = Anilist.sortBy[6]
                        activity.updateChips.invoke()
                        activity.search()
                        updateFilterTextViewDrawable()
                    }
                }
                true
            }
            popupMenu.show()
            true
        }
        if (activity.aniMangaResult.type != "ANIME") {
            binding.searchByImage.visibility = View.GONE
        }
        binding.searchByImage.setOnClickListener {
            activity.startActivity(Intent(activity, ImageSearchActivity::class.java))
        }
        binding.clearHistory.setOnClickListener {
            if (PrefManager.getVal<Boolean>(PrefName.AnimationsEnabled) && PrefManager.getVal<Boolean>(PrefName.SearchHeaderAnimations)) {
                it.startAnimation(fadeOutAnimation())
            } else {
                it.alpha = 0f
            }
            it.visibility = View.GONE
            searchHistoryAdapter.clearHistory()
        }
        updateClearHistoryVisibility()

        binding.searchBarCompose.setContent {
            val focusManager = LocalFocusManager.current
            OutlinedTextField(
                value = searchText,
                onValueChange = { onTextChanged(it) },
                singleLine = true,
                placeholder = {
                    Text(
                        activity.aniMangaResult.type,
                        fontSize = 14.sp
                    )
                },
                leadingIcon = if (PrefManager.getVal(PrefName.Incognito)) {
                    {
                        Icon(
                            painter = painterResource(R.drawable.ic_incognito_24),
                            contentDescription = null,
                            tint = ComposeColor.White
                        )
                    }
                } else null,
                trailingIcon = {
                    IconButton(onClick = { triggerSearch() }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_round_search_24),
                            contentDescription = "Search",
                            tint = ComposeColor.White
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        triggerSearch()
                        focusManager.clearFocus()
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(searchFocusRequester)
                    .onPreviewKeyEvent { ev ->
                        if (ev.type == KeyEventType.KeyDown) {
                            when (ev.key) {
                                Key.DirectionDown -> {
                                    focusManager.moveFocus(FocusDirection.Down)
                                    true
                                }
                                else -> false
                            }
                        } else false
                    },
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = ComposeColor.White,
                    unfocusedTextColor = ComposeColor.White,
                    cursorColor = ComposeColor.White,
                    focusedBorderColor = ComposeColor.White.copy(alpha = 0.5f),
                    unfocusedBorderColor = ComposeColor.White.copy(alpha = 0.3f)
                )
            )
        }

        search = Runnable { triggerSearch() }
        requestFocus = Runnable { searchFocusRequester.requestFocus() }

        binding.searchResultGrid.setOnClickListener {
            it.alpha = 1f
            binding.searchResultList.alpha = 0.33f
            activity.style = 0
            PrefManager.setVal(PrefName.SearchStyle, 0)
            activity.recycler()
        }
        binding.searchResultList.setOnClickListener {
            it.alpha = 1f
            binding.searchResultGrid.alpha = 0.33f
            activity.style = 1
            PrefManager.setVal(PrefName.SearchStyle, 1)
            activity.recycler()
        }

        if (Anilist.adult) {
            binding.searchAdultCheck.visibility = View.VISIBLE
            binding.searchAdultCheck.isChecked = adult
            binding.searchAdultCheck.setOnCheckedChangeListener { _, b ->
                adult = b
                triggerSearch()
            }
        } else binding.searchAdultCheck.visibility = View.GONE
        binding.searchList.apply {
            if (Anilist.userid != null) {
                visibility = View.VISIBLE
                checkedState = when (listOnly) {
                    null -> STATE_UNCHECKED
                    true -> STATE_CHECKED
                    false -> STATE_INDETERMINATE
                }

                addOnCheckedStateChangedListener { _, state ->
                    listOnly = when (state) {
                        STATE_CHECKED -> true
                        STATE_INDETERMINATE -> false
                        STATE_UNCHECKED -> null
                        else -> null
                    }
                }

                setOnTouchListener { _, event ->
                    (event.actionMasked == MotionEvent.ACTION_DOWN).also {
                        if (it) checkedState = (checkedState + 1) % 3
                        triggerSearch()
                    }
                }
            } else visibility = View.GONE
        }
    }

    class SearchChipAdapter(
        val activity: SearchActivity,
        private val searchAdapter: SearchAdapter
    ) :
        RecyclerView.Adapter<SearchChipAdapter.SearchChipViewHolder>() {
        private var chips: MutableList<ChipItem> = activity.aniMangaResult.toChipList()

        inner class SearchChipViewHolder(val binding: ItemChipBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchChipViewHolder {
            val binding =
                ItemChipBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return SearchChipViewHolder(binding)
        }


        override fun onBindViewHolder(holder: SearchChipViewHolder, position: Int) {
            val chip = chips[position]
            holder.binding.root.apply {
                text = chip.text.replace("_", " ")
                setOnClickListener {
                    activity.aniMangaResult.removeChip(chip)
                    update()
                    activity.search()
                    searchAdapter.updateFilterTextViewDrawable()
                }
            }
        }

        @SuppressLint("NotifyDataSetChanged")
        fun update() {
            chips = activity.aniMangaResult.toChipList()
            notifyDataSetChanged()
            searchAdapter.updateFilterTextViewDrawable()
        }

        override fun getItemCount(): Int = chips.size
    }
}
