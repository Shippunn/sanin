package ani.sanin.media

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.LinearLayoutManager
import ani.sanin.App.Companion.context
import ani.sanin.R
import ani.sanin.connections.anilist.AnilistSearch.SearchType
import ani.sanin.connections.anilist.AnilistSearch.SearchType.Companion.toAnilistString
import ani.sanin.connections.anilist.SearchResults
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.util.FocusEffectUtil
import ani.sanin.util.TvKeyboardUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SupportingSearchAdapter(private val activity: SearchActivity, private val type: SearchType) :
    HeaderInterface() {

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: SearchHeaderViewHolder, position: Int) {
        binding = holder.binding

        searchHistoryAdapter = SearchHistoryAdapter(type) {
            binding.searchBarText.setText(it)
            binding.searchBarText.setSelection(it.length)
        }
        binding.searchHistoryList.layoutManager = LinearLayoutManager(binding.root.context)
        binding.searchHistoryList.adapter = searchHistoryAdapter

        if (activity.searchType == SearchType.MANGA || activity.searchType == SearchType.ANIME) {
            throw IllegalArgumentException("Invalid search type (wrong adapter)")
        }

        binding.searchByImage.visibility = View.GONE
        binding.searchResultGrid.visibility = View.GONE
        binding.searchResultList.visibility = View.GONE
        binding.searchFilter.visibility = View.GONE
        binding.searchAdultCheck.visibility = View.GONE
        binding.searchList.visibility = View.GONE
        binding.searchChipRecycler.visibility = View.GONE

        binding.searchBar.hint = activity.searchType.toAnilistString()
        if (PrefManager.getVal(PrefName.Incognito)) {
            val startIconDrawableRes = R.drawable.ic_incognito_24
            val startIconDrawable: Drawable? =
                context?.let { AppCompatResources.getDrawable(it, startIconDrawableRes) }
            binding.searchBar.startIconDrawable = startIconDrawable
        }

        binding.searchBarText.removeTextChangedListener(textWatcher)
        when (type) {
            SearchType.CHARACTER -> {
                binding.searchBarText.setText(activity.characterResult.search)
            }

            SearchType.STUDIO -> {
                binding.searchBarText.setText(activity.studioResult.search)
            }

            SearchType.STAFF -> {
                binding.searchBarText.setText(activity.staffResult.search)
            }

            SearchType.USER -> {
                binding.searchBarText.setText(activity.userResult.search)
            }

            else -> throw IllegalArgumentException("Invalid search type")
        }

        FocusEffectUtil.applyFocusListener(binding.clearHistory)
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
        fun searchTitle() {
            val searchText = binding.searchBarText.text.toString().takeIf { it.isNotEmpty() }

            val result: SearchResults<*> = when (type) {
                SearchType.CHARACTER -> activity.characterResult
                SearchType.STUDIO -> activity.studioResult
                SearchType.STAFF -> activity.staffResult
                SearchType.USER -> activity.userResult
                else -> throw IllegalArgumentException("Invalid search type")
            }

            result.search = searchText
            activity.search()
        }

        textWatcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable) {}

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (s.toString().isBlank()) {
                    activity.emptyMediaAdapter()
                    CoroutineScope(Dispatchers.IO).launch {
                        delay(200)
                        activity.runOnUiThread {
                            setHistoryVisibility(true)
                        }
                    }
                } else {
                    setHistoryVisibility(false)
                    searchTitle()
                }
            }
        }
        binding.searchBarText.addTextChangedListener(textWatcher)

        binding.searchBarText.setOnEditorActionListener { _, actionId, _ ->
            return@setOnEditorActionListener when (actionId) {
                EditorInfo.IME_ACTION_SEARCH -> {
                    searchTitle()
                    binding.searchBarText.clearFocus()
                    true
                }

                else -> false
            }
        }
        binding.searchBar.setEndIconOnClickListener { searchTitle() }

        when (TvKeyboardUtil.keyboardMode()) {
            0 -> TvKeyboardUtil.setupSystemKeyboard(binding.searchBarText)
            1 -> TvKeyboardUtil.setupEditTextWithToggle(binding.searchBarText, binding.searchKeyboardToggle)
            2 -> TvKeyboardUtil.setupEditTextForAlwaysVisible(binding.searchBarText)
        }
        search = Runnable { searchTitle() }
        requestFocus = Runnable { binding.searchBarText.requestFocus() }
    }
}
