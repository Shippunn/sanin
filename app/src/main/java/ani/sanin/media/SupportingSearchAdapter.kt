package ani.sanin.media

import android.annotation.SuppressLint
import android.view.View
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
import ani.sanin.R
import ani.sanin.connections.anilist.AnilistSearch.SearchType
import ani.sanin.connections.anilist.AnilistSearch.SearchType.Companion.toAnilistString
import ani.sanin.connections.anilist.SearchResults
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.util.FocusEffectUtil

class SupportingSearchAdapter(private val activity: SearchActivity, private val type: SearchType) :
    HeaderInterface() {

    private var searchText by mutableStateOf("")
    private val searchFocusRequester = FocusRequester()

    private fun triggerSearch() {
        val searchVal = searchText.takeIf { it.isNotEmpty() }
        val result: SearchResults<*> = when (type) {
            SearchType.CHARACTER -> activity.characterResult
            SearchType.STUDIO -> activity.studioResult
            SearchType.STAFF -> activity.staffResult
            SearchType.USER -> activity.userResult
            else -> throw IllegalArgumentException("Invalid search type")
        }
        result.search = searchVal
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

        searchText = when (type) {
            SearchType.CHARACTER -> activity.characterResult.search ?: ""
            SearchType.STUDIO -> activity.studioResult.search ?: ""
            SearchType.STAFF -> activity.staffResult.search ?: ""
            SearchType.USER -> activity.userResult.search ?: ""
            else -> throw IllegalArgumentException("Invalid search type")
        }
        searchTextValue = searchText

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

        binding.searchBarCompose.setContent {
            val focusManager = LocalFocusManager.current
            OutlinedTextField(
                value = searchText,
                onValueChange = { onTextChanged(it) },
                singleLine = true,
                placeholder = {
                    Text(
                        activity.searchType.toAnilistString(),
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
    }
}
