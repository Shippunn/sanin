package ani.sanin.media

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import androidx.core.math.MathUtils.clamp
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import ani.sanin.BottomSheetDialogFragment
import ani.sanin.R
import ani.sanin.databinding.BottomSheetSourceSearchBinding
import ani.sanin.media.anime.AnimeSourceAdapter
import ani.sanin.navBarHeight
import ani.sanin.parsers.AnimeParser
import ani.sanin.parsers.AnimeSources
import ani.sanin.parsers.HAnimeSources
import ani.sanin.px
import ani.sanin.tryWithSuspend
import ani.sanin.util.FocusEffectUtil
import ani.sanin.util.GlassComponent
import ani.sanin.util.GlassEffectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SourceSearchDialogFragment : BottomSheetDialogFragment() {

    private var _binding: BottomSheetSourceSearchBinding? = null
    private val binding get() = _binding!!
    val model: MediaDetailsViewModel by activityViewModels()
    private var searched = false
    private var searchText by mutableStateOf("")
    private val searchFocusRequester = FocusRequester()
    var i: Int? = null
    var id: Int? = null
    var media: Media? = null
    var onSourceSelected: ((ani.sanin.parsers.ShowResponse) -> Unit)? = null

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
        GlassEffectManager.applyGlassToSheet(binding.mediaListContainer, GlassComponent.SourceSelector, 16f)

        val scope = requireActivity().lifecycleScope
        model.getMedia().observe(viewLifecycleOwner) {
            media = it
            if (media != null) {
                binding.mediaListProgressBar.visibility = View.GONE
                binding.mediaListLayout.visibility = View.VISIBLE

                binding.searchRecyclerView.visibility = View.GONE
                binding.searchProgress.visibility = View.VISIBLE

                val source: Any? = if (media!!.anime != null) {
                    if (i == null) i = media!!.selected?.sourceIndex ?: 0
                    (if (media!!.isAdult) HAnimeSources else AnimeSources)[i!!]
                } else null

                fun search(keepFocus: Boolean = false) {
                    scope.launch {
                        val src = source as? AnimeParser
                        model.responses.postValue(
                            withContext(Dispatchers.IO) {
                                tryWithSuspend {
                                    src?.search(searchText)
                                }
                            }
                        )
                    }
                }
                val srcName = (source as? AnimeParser)?.name ?: "Search"
                binding.searchSourceTitle.text = srcName
                searchText = media!!.mainName()

                binding.searchBarCompose.setContent {
                    val focusManager = LocalFocusManager.current
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        singleLine = true,
                        placeholder = { Text(srcName, fontSize = 14.sp) },
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

                if (!searched) search(keepFocus = true)
                searched = true
                model.responses.observe(viewLifecycleOwner) { j ->
                    if (j != null) {
                        binding.searchRecyclerView.visibility = View.VISIBLE
                        binding.searchProgress.visibility = View.GONE
                        binding.searchRecyclerView.adapter =
                            AnimeSourceAdapter(j, model, i!!, media!!.id, this, scope)
                        binding.searchRecyclerView.layoutManager = GridLayoutManager(
                            requireActivity(),
                            clamp(
                                requireActivity().resources.displayMetrics.widthPixels / 124f.px,
                                1,
                                4
                            )
                        )
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun dismiss() {
        model.responses.value = null
        super.dismiss()
    }
}
