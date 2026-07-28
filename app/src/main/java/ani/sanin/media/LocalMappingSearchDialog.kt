package ani.sanin.media

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.math.MathUtils.clamp
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ani.sanin.BottomSheetDialogFragment
import ani.sanin.R
import ani.sanin.connections.anilist.Anilist
import ani.sanin.databinding.BottomSheetSourceSearchBinding
import ani.sanin.databinding.ItemMediaCompactBinding
import ani.sanin.loadImage
import ani.sanin.navBarHeight
import ani.sanin.px
import ani.sanin.settings.saving.PrefManager
import ani.sanin.snackString
import ani.sanin.tryWithSuspend
import ani.sanin.util.FocusEffectUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class LocalMappingSearchDialog : BottomSheetDialogFragment() {

    private var _binding: BottomSheetSourceSearchBinding? = null
    private val binding get() = _binding!!
    private var searched = false
    private var searchText by mutableStateOf("")
    private val searchFocusRequester = FocusRequester()

    var folderName: String? = null
    var searchType: String = "ANIME"
    var searchFormat: String? = null
    var onMappingSelected: ((Int) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetSourceSearchBinding.inflate(inflater, container, false)
        FocusEffectUtil.applyFocusListener(binding.root)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.mediaListContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> { bottomMargin += navBarHeight }

        val scope = viewLifecycleOwner.lifecycleScope

        binding.mediaListProgressBar.visibility = View.GONE
        binding.mediaListLayout.visibility = View.VISIBLE
        binding.searchRecyclerView.visibility = View.GONE
        binding.searchProgress.visibility = View.VISIBLE

        binding.searchSourceTitle.text = "Map to AniList"
        searchText = folderName ?: ""

        fun search() {
            binding.searchRecyclerView.visibility = View.GONE
            binding.searchProgress.visibility = View.VISIBLE
            scope.launch {
                val results = withContext(Dispatchers.IO) {
                    tryWithSuspend {
                        Anilist.query.searchAniManga(
                            type = searchType,
                            search = searchText,
                            format = searchFormat
                        )
                    }
                }
                if (results != null && results.results.isNotEmpty()) {
                    binding.searchRecyclerView.visibility = View.VISIBLE
                    binding.searchProgress.visibility = View.GONE
                    binding.searchRecyclerView.adapter =
                        LocalMappingResultAdapter(results.results) { selectedMedia ->
                            val mapKey = folderName ?: return@LocalMappingResultAdapter
                            PrefManager.setCustomVal("local_mapping_$mapKey", selectedMedia.id)
                            snackString("Mapped to: ${selectedMedia.userPreferredName}")
                            onMappingSelected?.invoke(selectedMedia.id)
                            dismiss()
                        }
                    binding.searchRecyclerView.layoutManager = GridLayoutManager(
                        requireActivity(),
                        clamp(
                            requireActivity().resources.displayMetrics.widthPixels / 124f.px,
                            1,
                            4
                        )
                    )
                } else {
                    binding.searchProgress.visibility = View.GONE
                    snackString("No results found")
                }
            }
        }

        binding.searchBarCompose.setContent {
            val focusManager = LocalFocusManager.current
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                singleLine = true,
                placeholder = { Text("Search", fontSize = 14.sp) },
                trailingIcon = {
                    IconButton(onClick = { search() }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_round_search_24),
                            contentDescription = "Search",
                            tint = ComposeColor.White
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { search() }
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

        search()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(
            folderName: String,
            isAnime: Boolean,
            isNovel: Boolean = false,
            onMappingSelected: (Int) -> Unit
        ): LocalMappingSearchDialog {
            return LocalMappingSearchDialog().apply {
                this.folderName = folderName
                this.searchType = "ANIME"
                this.searchFormat = null
                this.onMappingSelected = onMappingSelected
            }
        }
    }
}


private class LocalMappingResultAdapter(
    private val results: List<Media>,
    private val onItemClick: (Media) -> Unit
) : RecyclerView.Adapter<LocalMappingResultAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemMediaCompactBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemMediaCompactBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun getItemCount(): Int = results.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val media = results[position]
        holder.binding.itemCompactImage.loadImage(media.cover)
        holder.binding.itemCompactTitle.text = media.userPreferredName
        holder.binding.itemCompactScore.text = media.meanScore?.let { "$it%" } ?: ""
        holder.binding.root.setOnClickListener {
            onItemClick(media)
        }
    }
}
