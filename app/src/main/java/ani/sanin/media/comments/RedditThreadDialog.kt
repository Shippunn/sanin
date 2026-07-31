package ani.sanin.media.comments

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ani.sanin.R
import ani.sanin.buildMarkwon
import ani.sanin.connections.reddit.RedditAPI
import ani.sanin.databinding.DialogItemRedditCommentBinding
import ani.sanin.databinding.DialogRedditThreadBinding
import ani.sanin.util.FocusEffectUtil

class RedditThreadDialog : DialogFragment() {
    private var _binding: DialogRedditThreadBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, android.R.style.Theme_DeviceDefault_NoActionBar)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogRedditThreadBinding.inflate(inflater, container, false)
        return binding.root
    }

    @Suppress("DEPRECATION")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = arguments ?: return
        val markwon = buildMarkwon(requireActivity())

        binding.redditDialogTitle.text = args.getString("title") ?: ""
        val comments = args.getSerializable("comments") as? List<RedditAPI.RedditComment> ?: emptyList()
        binding.redditDialogMeta.text = "r/anime \u00b7 ${comments.size} top comments"

        binding.redditDialogList.layoutManager = LinearLayoutManager(requireContext())
        binding.redditDialogList.adapter = CommentAdapter(comments, markwon)

        binding.redditDialogClose.setOnClickListener { dismiss() }
        FocusEffectUtil.applyFocusListener(binding.redditDialogClose)
    }

    override fun onStart() {
        super.onStart()
        val window = dialog?.window ?: return
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val dm = resources.displayMetrics
        window.setLayout((dm.widthPixels * 0.85).toInt(), (dm.heightPixels * 0.8).toInt())
        window.setGravity(Gravity.CENTER)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes.blurBehindRadius = 25
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setDimAmount(0.5f)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class CommentAdapter(
        private val comments: List<RedditAPI.RedditComment>,
        private val markwon: io.noties.markwon.Markwon
    ) : RecyclerView.Adapter<CommentAdapter.VH>() {

        class VH(val binding: DialogItemRedditCommentBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            return VH(DialogItemRedditCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        override fun getItemCount(): Int = comments.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val comment = comments[position]
            holder.binding.redditCommentAuthor.text = "u/${comment.author}"
            holder.binding.redditCommentScore.text = "${comment.score} pts"
            markwon.setMarkdown(holder.binding.redditCommentBody, comment.body)
            FocusEffectUtil.applyFocusListener(holder.binding.root)
        }
    }

    companion object {
        fun newInstance(title: String, comments: List<RedditAPI.RedditComment>): RedditThreadDialog {
            return RedditThreadDialog().apply {
                arguments = Bundle().apply {
                    putString("title", title)
                    putSerializable("comments", ArrayList(comments))
                }
            }
        }
    }
}
