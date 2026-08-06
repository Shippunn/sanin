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
import ani.sanin.R
import ani.sanin.buildMarkwon
import ani.sanin.connections.comments.AnikotoAPI
import ani.sanin.connections.comments.Comment
import ani.sanin.databinding.DialogCommentZoomBinding
import ani.sanin.loadImage
import ani.sanin.util.FocusEffectUtil
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class CommentZoomDialog : DialogFragment() {
    private var _binding: DialogCommentZoomBinding? = null
    private val binding get() = _binding!!
    var listener: ZoomActionListener? = null
    private var commentId: Int = 0
    private var userVoteType: Int = 0
    private var upvotes: Int = 0
    private var downvotes: Int = 0
    private var replyCount: Int = 0
    private var isAnikoto: Boolean = false
    private var mediaId: Int = 0
    private var anikotoEpisode: Int = 0
    private lateinit var markwon: io.noties.markwon.Markwon

    interface ZoomActionListener {
        fun onReply(commentId: Int, username: String)
        fun onVote(commentId: Int, voteType: Int, currentVoteType: Int)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, android.R.style.Theme_DeviceDefault_NoActionBar)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogCommentZoomBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = arguments ?: return

        markwon = buildMarkwon(requireActivity())

        commentId = args.getInt("commentId")
        userVoteType = args.getInt("userVoteType", 0)
        upvotes = args.getInt("upvotes", 0)
        downvotes = args.getInt("downvotes", 0)
        replyCount = args.getInt("replyCount", 0)
        isAnikoto = args.getBoolean("isAnikoto", false)
        mediaId = args.getInt("mediaId", 0)
        anikotoEpisode = args.getInt("anikotoEpisode", 0)
        val username = args.getString("username") ?: ""
        val timestamp = args.getString("timestamp") ?: ""

        binding.zoomUserName.text = username
        binding.zoomUserTime.text = formatTimestamp(timestamp)
        markwon.setMarkdown(binding.zoomCommentText, args.getString("content") ?: "")
        binding.zoomVotes.text = "${upvotes - downvotes} votes"
        updateVoteCount()
        updateVoteIcons()

        val tag = args.getString("tag")
        if (tag != null) {
            binding.zoomTag.visibility = View.VISIBLE
            binding.zoomTag.text = tag
        } else {
            binding.zoomTag.visibility = View.GONE
        }
        args.getString("avatarUrl")?.let { binding.zoomUserAvatar.loadImage(it) }

        binding.zoomClose.setOnClickListener { dismiss() }
        binding.root.setOnClickListener { dismiss() }

        binding.zoomReply.setOnClickListener {
            listener?.onReply(commentId, username)
            dismiss()
        }

        binding.zoomUpVote.setOnClickListener {
            val newVoteType = if (userVoteType == 1) 0 else 1
            listener?.onVote(commentId, newVoteType, userVoteType)
            if (newVoteType != userVoteType) {
                if (userVoteType == -1) downvotes -= 1
                upvotes += if (newVoteType == 1) 1 else -1
                userVoteType = newVoteType
                updateVoteCount()
                updateVoteIcons()
            }
        }

        binding.zoomDownVote.setOnClickListener {
            val newVoteType = if (userVoteType == -1) 0 else -1
            listener?.onVote(commentId, newVoteType, userVoteType)
            if (newVoteType != userVoteType) {
                if (userVoteType == 1) upvotes -= 1
                downvotes += if (newVoteType == -1) 1 else -1
                userVoteType = newVoteType
                updateVoteCount()
                updateVoteIcons()
            }
        }

        if (isAnikoto && replyCount > 0) {
            loadReplies()
        }

        FocusEffectUtil.applyFocusListener(
            binding.zoomReply,
            binding.zoomUpVote,
            binding.zoomVoteCount,
            binding.zoomDownVote,
            binding.zoomClose,
            binding.zoomUserAvatar,
        )
    }

    private fun loadReplies() {
        binding.zoomRepliesSection.visibility = View.VISIBLE
        binding.zoomRepliesTitle.text = "Replies ($replyCount)"
        lifecycleScope.launch {
            val replies = withContext(Dispatchers.IO) {
                AnikotoAPI.getReplies(commentId, anikotoEpisode, mediaId)
            }
            if (!isAdded || _binding == null) return@launch
            if (replies.isEmpty()) {
                binding.zoomRepliesTitle.text = "Replies ($replyCount) · failed to load"
                return@launch
            }
            binding.zoomRepliesTitle.text = "Replies (${replies.size})"
            replies.forEach { addReplyRow(it) }
        }
    }

    private fun addReplyRow(reply: Comment) {
        val row = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_zoom_reply, binding.zoomRepliesList, false)
        row.findViewById<android.widget.TextView>(R.id.replyUserName).text = reply.username
        row.findViewById<android.widget.TextView>(R.id.replyUserTime).text = formatTimestamp(reply.timestamp)
        markwon.setMarkdown(
            row.findViewById(R.id.replyContent),
            reply.content
        )
        val avatar = row.findViewById<com.google.android.material.imageview.ShapeableImageView>(R.id.replyAvatar)
        if (reply.profilePictureUrl != null) {
            avatar.loadImage(reply.profilePictureUrl)
        } else {
            avatar.setImageResource(R.drawable.ic_round_add_circle_24)
        }
        binding.zoomRepliesList.addView(row)
    }

    private fun updateVoteCount() {
        binding.zoomVoteCount.text = (upvotes - downvotes).toString()
        binding.zoomVotes.text = "${upvotes - downvotes} votes"
    }

    private fun updateVoteIcons() {
        when (userVoteType) {
            1 -> {
                binding.zoomUpVote.setImageResource(R.drawable.ic_round_upvote_active_24)
                binding.zoomUpVote.alpha = 1f
                binding.zoomDownVote.setImageResource(R.drawable.ic_round_upvote_inactive_24)
                binding.zoomDownVote.alpha = 0.6f
            }
            -1 -> {
                binding.zoomUpVote.setImageResource(R.drawable.ic_round_upvote_inactive_24)
                binding.zoomUpVote.alpha = 0.6f
                binding.zoomDownVote.setImageResource(R.drawable.ic_round_upvote_active_24)
                binding.zoomDownVote.alpha = 1f
            }
            else -> {
                binding.zoomUpVote.setImageResource(R.drawable.ic_round_upvote_inactive_24)
                binding.zoomUpVote.alpha = 0.6f
                binding.zoomDownVote.setImageResource(R.drawable.ic_round_upvote_inactive_24)
                binding.zoomDownVote.alpha = 0.6f
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val window = dialog?.window ?: return
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
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

    private fun formatTimestamp(timestamp: String): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val parsed = sdf.parse(timestamp)
            val diff = System.currentTimeMillis() - (parsed?.time ?: 0)
            val days = diff / (24 * 60 * 60 * 1000)
            val hours = diff / (60 * 60 * 1000) % 24
            val minutes = diff / (60 * 1000) % 60
            when {
                days > 0 -> "${days}d"
                hours > 0 -> "${hours}h"
                minutes > 0 -> "${minutes}m"
                else -> "now"
            }
        } catch (_: Exception) {
            "now"
        }
    }

    companion object {
        fun newInstance(
            commentId: Int,
            username: String,
            timestamp: String,
            content: String,
            votes: String,
            tag: String?,
            avatarUrl: String?,
            userVoteType: Int = 0,
            upvotes: Int = 0,
            downvotes: Int = 0,
        ): CommentZoomDialog {
            val args = Bundle().apply {
                putInt("commentId", commentId)
                putString("username", username)
                putString("timestamp", timestamp)
                putString("content", content)
                putString("votes", votes)
                putString("tag", tag)
                putString("avatarUrl", avatarUrl)
                putInt("userVoteType", userVoteType)
                putInt("upvotes", upvotes)
                putInt("downvotes", downvotes)
            }
            val dialog = CommentZoomDialog()
            dialog.arguments = args
            return dialog
        }
    }
}
