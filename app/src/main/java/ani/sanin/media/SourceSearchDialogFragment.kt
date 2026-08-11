package ani.sanin.media

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
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
import ani.sanin.util.TvKeyboardUtil
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
                    if (!keepFocus) {
                        binding.searchBarText.clearFocus()
                        binding.mediaListContainer.requestFocus()
                    }
                    scope.launch {
                        val src = source as? AnimeParser
                        model.responses.postValue(
                            withContext(Dispatchers.IO) {
                                tryWithSuspend {
                                    src?.search(binding.searchBarText.text.toString())
                                }
                            }
                        )
                    }
                }
                val srcName = (source as? AnimeParser)?.name ?: "Search"
                binding.searchSourceTitle.text = srcName
                binding.searchBarText.setText(media!!.mainName())
                TvKeyboardUtil.setupTvInput(binding.searchBarText)
                dialog?.window?.let { TvKeyboardUtil.retainWindowFocus(it) }
                binding.searchBarText.nextFocusDownId = R.id.searchRecyclerView
                if (TvKeyboardUtil.isTv(requireContext())) {
                    binding.searchBarText.post { binding.searchBarText.requestFocus() }
                }
                dialog?.setOnKeyListener { _, keyCode, event ->
                    if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                        val keyboardOpen = when (TvKeyboardUtil.keyboardMode()) {
                            1 -> TvKeyboardUtil.isKeyboardVisible(binding.searchBarText)
                            2 -> TvKeyboardUtil.isCompactKeyboardVisible(binding.searchBarText)
                            else -> false
                        }
                        if (keyboardOpen) {
                            TvKeyboardUtil.hideKeyboard(binding.searchBarText)
                            return@setOnKeyListener true
                        }
                    }
                    false
                }
                binding.searchBar.setEndIconOnClickListener { search() }
                binding.searchBarText.setOnEditorActionListener { _, actionId, _ ->
                    if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) { search(); true } else false
                }
                if (!searched) search(keepFocus = true)
                searched = true
                model.responses.observe(viewLifecycleOwner) { j ->
                    if (j != null) {
                        binding.searchRecyclerView.visibility = View.VISIBLE
                        binding.searchProgress.visibility = View.GONE
                        // Drop any focus/scroll position from the previous result set before
                        // swapping the adapter, so RecyclerView doesn't restore an old (middle) card.
                        binding.searchRecyclerView.clearFocus()
                        binding.searchRecyclerView.scrollToPosition(0)
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
                        // After the new results are laid out, land on the first card unless the
                        // user is still typing in the search bar (e.g. the initial auto-search).
                        binding.searchRecyclerView.post {
                            binding.searchRecyclerView.scrollToPosition(0)
                            if (!binding.searchBarText.hasFocus()) {
                                binding.searchRecyclerView.findViewHolderForAdapterPosition(0)
                                    ?.itemView?.requestFocus()
                            }
                        }
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
