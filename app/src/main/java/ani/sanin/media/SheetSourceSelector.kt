package ani.sanin.media

import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ani.sanin.databinding.BottomSheetSelectorBinding
import ani.sanin.getThemeColor
import ani.sanin.util.GlassComponent
import ani.sanin.util.GlassEffectManager

class SheetSourceSelector : DialogFragment() {
    private var _binding: BottomSheetSelectorBinding? = null
    private val binding get() = _binding!!
    private var sources: List<String> = emptyList()
    private var onSelect: ((Int) -> Unit)? = null
    private var onDismiss: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sources = arguments?.getStringArrayList("sources")?.toList() ?: emptyList()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { w ->
            w.setBackgroundDrawableResource(android.R.color.transparent)
            val widthPx = (resources.displayMetrics.widthPixels * 0.65f).toInt()
            w.setLayout(widthPx, WindowManager.LayoutParams.WRAP_CONTENT)
            w.setGravity(Gravity.CENTER)
            w.setDimAmount(0.5f)
            w.statusBarColor = Color.TRANSPARENT
            w.navigationBarColor =
                requireContext().getThemeColor(com.google.android.material.R.attr.colorSurface)
        }
        GlassEffectManager.applyGlassToSheet(binding.selectorContainer, GlassComponent.SourceSelector, 16f)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetSelectorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.selectorMakeDefault.visibility = View.GONE
        binding.selectorRecyclerView.layoutManager = LinearLayoutManager(requireActivity())
        val focusColor = requireContext().getThemeColor(com.google.android.material.R.attr.colorControlHighlight)
        binding.selectorRecyclerView.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val tv = TextView(parent.context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    setPadding(64, 24, 64, 24)
                    textAlignment = View.TEXT_ALIGNMENT_CENTER
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
                    isFocusable = true
                    isClickable = true
                    setOnFocusChangeListener { v, hasFocus ->
                        v.setBackgroundColor(if (hasFocus) focusColor else Color.TRANSPARENT)
                    }
                }
                return object : RecyclerView.ViewHolder(tv) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val tv = holder.itemView as TextView
                val text = sources[position]
                tv.text = text
                if (text.startsWith("───")) {
                    tv.isFocusable = false
                    tv.isClickable = false
                    tv.alpha = 0.5f
                    tv.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                } else {
                    tv.isFocusable = true
                    tv.isClickable = true
                    tv.alpha = 1f
                    tv.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
                    tv.setOnClickListener {
                        onSelect?.invoke(position)
                        dismissAllowingStateLoss()
                    }
                }
            }

            override fun getItemCount() = sources.size
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismiss?.invoke()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(
            sources: ArrayList<String>,
            onSelect: (Int) -> Unit,
            onDismiss: (() -> Unit)? = null
        ): SheetSourceSelector {
            val f = SheetSourceSelector()
            f.onSelect = onSelect
            f.onDismiss = onDismiss
            f.arguments = Bundle().apply {
                putStringArrayList("sources", sources)
            }
            return f
        }
    }
}
