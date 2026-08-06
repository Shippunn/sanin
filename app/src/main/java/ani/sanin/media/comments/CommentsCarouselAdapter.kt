package ani.sanin.media.comments

import android.text.TextUtils
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ani.sanin.R
import ani.sanin.connections.comments.Comment
import ani.sanin.databinding.ItemCommentCarouselBinding
import ani.sanin.loadImage
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class CommentsCarouselAdapter(
    private val fragment: CommentsFragment,
    private val markwon: io.noties.markwon.Markwon
) : ListAdapter<Comment, CommentsCarouselAdapter.ViewHolder>(DiffCallback()) {

    private var focusedPosition = 0
    private val replyParentsShown = mutableSetOf<Int>()

    fun setFocusedPosition(pos: Int) {
        val old = focusedPosition
        focusedPosition = pos
        if (old != pos) {
            notifyItemChanged(old)
            notifyItemChanged(pos)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCommentCarouselBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val comment = getItem(position)
        val b = holder.binding
        val isFocused = position == focusedPosition

        b.carouselUserName.text = comment.username
        b.carouselTimestamp.text = formatTimestamp(comment.timestamp)
        markwon.setMarkdown(b.carouselCommentText, comment.content)
        if (comment.content.contains("![")) {
            b.carouselCommentText.maxLines = Int.MAX_VALUE
            b.carouselCommentText.ellipsize = null
        } else {
            b.carouselCommentText.maxLines = 3
            b.carouselCommentText.ellipsize = TextUtils.TruncateAt.END
        }
        b.carouselVoteCount.text = (comment.upvotes - comment.downvotes).toString()

        val primaryColor = resolvePrimaryColor(b.root.context)
        val isUpvoted = comment.userVoteType == 1
        val isDownvoted = comment.userVoteType == -1

        if (comment.profilePictureUrl != null) {
            b.carouselAvatar.loadImage(comment.profilePictureUrl)
        } else {
            b.carouselAvatar.setImageResource(R.drawable.ic_round_add_circle_24)
        }

        b.carouselUpVote.setColorFilter(if (isUpvoted) primaryColor else 0xFF888888.toInt())
        b.carouselDownVote.setColorFilter(if (isDownvoted) primaryColor else 0xFF888888.toInt())
        b.carouselVoteCount.setTextColor(
            if (isUpvoted || isDownvoted) primaryColor else 0xFFFFFFFF.toInt()
        )

        val card = b.root as MaterialCardView
        card.setCardBackgroundColor(if (isFocused) 0xFF1E1E1E.toInt() else 0xFF111111.toInt())
        card.strokeColor = if (isFocused) primaryColor else 0
        card.strokeWidth = if (isFocused) 2 else 0
        card.isFocusable = isFocused

        b.carouselReply.setOnClickListener {
            fragment.startReply(comment)
        }

        b.carouselUpVote.setOnClickListener {
            val newVote = if (comment.userVoteType == 1) 0 else 1
            fragment.voteComment(comment, newVote, holder.adapterPosition)
        }

        b.carouselDownVote.setOnClickListener {
            val newVote = if (comment.userVoteType == -1) 0 else -1
            fragment.voteComment(comment, newVote, holder.adapterPosition)
        }

        b.carouselMore.setOnClickListener {
            fragment.showCommentMenu(comment, holder.adapterPosition)
        }

        b.root.setOnClickListener {
            fragment.openCommentDetail(comment)
        }

        when {
            comment.isAnikoto -> {
                b.carouselSourceBadge.visibility = View.VISIBLE
                b.carouselSourceBadge.text = "anikoto"
                b.carouselSourceBadge.setTextColor(0xFF00E5FF.toInt())
            }
            else -> {
                b.carouselSourceBadge.visibility = View.VISIBLE
                b.carouselSourceBadge.text = "dantotsu"
                b.carouselSourceBadge.setTextColor(0xFFBB86FC.toInt())
            }
        }

        if (comment.anikotoEpisode != null) {
            b.carouselEpisodeTag.visibility = View.VISIBLE
            b.carouselEpisodeTag.text = "ep ${comment.anikotoEpisode}"
            b.carouselEpisodeTag.alpha = if (isFocused) 1f else 0.7f
        } else {
            b.carouselEpisodeTag.visibility = View.GONE
        }

        if ((comment.replyCount ?: 0) > 0 && comment.commentId !in replyParentsShown) {
            b.carouselShowReplies.visibility = View.VISIBLE
            b.carouselShowReplies.text = "Show replies (${comment.replyCount})"
            b.carouselShowReplies.setOnClickListener {
                replyParentsShown.add(comment.commentId)
                b.carouselShowReplies.visibility = View.GONE
                fragment.showReplies(comment, holder.adapterPosition)
            }
        } else {
            b.carouselShowReplies.visibility = View.GONE
        }

        val showActions = isFocused
        b.carouselActionRow.visibility = if (showActions) View.VISIBLE else View.INVISIBLE
        b.carouselAvatar.layoutParams.width = if (isFocused) 48 else 32
        b.carouselAvatar.layoutParams.height = if (isFocused) 48 else 32
        b.carouselUserName.textSize = if (isFocused) 16f else 13f
        b.carouselCommentText.textSize = if (isFocused) 15f else 13f
        b.carouselUserName.alpha = if (isFocused) 1f else 0.7f
        b.carouselCommentText.alpha = if (isFocused) 1f else 0.6f
        b.carouselTimestamp.visibility = if (isFocused) View.VISIBLE else View.INVISIBLE
        b.carouselSourceBadge.alpha = if (isFocused) 1f else 0.7f
    }

    private fun formatTimestamp(timestamp: String): String {
        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            dateFormat.timeZone = TimeZone.getTimeZone("UTC")
            val date = dateFormat.parse(timestamp) ?: return timestamp
            val now = System.currentTimeMillis()
            val diff = now - date.time
            val seconds = diff / 1000
            val minutes = seconds / 60
            val hours = minutes / 60
            val days = hours / 24
            return when {
                days > 0 -> "${days}d"
                hours > 0 -> "${hours}h"
                minutes > 0 -> "${minutes}m"
                else -> "now"
            }
        } catch (_: Exception) {
            return timestamp
        }
    }

    private fun resolvePrimaryColor(context: android.content.Context): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(android.R.attr.colorPrimary, typedValue, true)
        return typedValue.data
    }

    class DiffCallback : DiffUtil.ItemCallback<Comment>() {
        override fun areItemsTheSame(a: Comment, b: Comment): Boolean {
            return a.commentId == b.commentId
        }
        override fun areContentsTheSame(a: Comment, b: Comment): Boolean {
            return a == b
        }
    }

    class ViewHolder(val binding: ItemCommentCarouselBinding) : RecyclerView.ViewHolder(binding.root)
}
