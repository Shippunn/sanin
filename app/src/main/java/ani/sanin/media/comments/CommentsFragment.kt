package ani.sanin.media.comments

import android.annotation.SuppressLint
import android.content.Context.INPUT_METHOD_SERVICE
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import ani.sanin.R
import ani.sanin.buildMarkwon
import ani.sanin.connections.anilist.Anilist
import ani.sanin.connections.comments.Comment
import ani.sanin.connections.mal.MAL
import ani.sanin.connections.LogoApi
import ani.sanin.connections.comments.AnikotoAPI
import ani.sanin.connections.comments.CommentsAPI
import ani.sanin.media.MediaListDialogFragment
import ani.sanin.databinding.FragmentCommentsBinding
import ani.sanin.loadImage
import ani.sanin.media.MediaDetailsActivity
import ani.sanin.media.MediaDetailsViewModel
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.snackString
import ani.sanin.util.Logger
import ani.sanin.util.TvKeyboardUtil
import ani.sanin.util.customAlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@SuppressLint("ClickableViewAccessibility")
class CommentsFragment : Fragment() {
    lateinit var binding: FragmentCommentsBinding
    lateinit var activity: MediaDetailsActivity
    private var interactionState = InteractionState.NONE
    private var commentWithInteraction: Comment? = null
    private var tag: Int? = null
    private var filterTag: Int? = null
    private var mediaId: Int = -1
    var mediaName: String = ""
    var pagesLoaded = 1
    var totalPages = 1
    private var userProgress: Int? = null
    private var commentsLoaded = false
    private var isAutoFilterOn = false
    private var isSpoilerMode = false

    private var displayedComments = mutableListOf<Comment>()
    private val anikotoCommentPool = mutableListOf<Comment>()
    private val saninComments = mutableListOf<Comment>()
    private var anikotoLoadDone = false
    private var saninRevealed = false
    private var revealInProgress = false
    private lateinit var carouselAdapter: CommentsCarouselAdapter
    private lateinit var markwon: io.noties.markwon.Markwon

    enum class InteractionState { NONE, EDIT, REPLY }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentCommentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity = requireActivity() as MediaDetailsActivity

        val mediaId = arguments?.getInt("mediaId") ?: -1
        mediaName = arguments?.getString("mediaName") ?: "unknown"
        if (mediaId == -1) {
            snackString("Invalid Media ID")
            return
        }
        this.mediaId = mediaId
        markwon = buildMarkwon(activity, fragment = this)
        setupCarousel()

        binding.commentUserAvatar.loadImage(Anilist.avatar)
        val markwonEditor = io.noties.markwon.editor.MarkwonEditor.create(markwon)
        binding.commentInput.addTextChangedListener(
            io.noties.markwon.editor.MarkwonEditorTextWatcher.withProcess(markwonEditor)
        )

        val isOfflineOrLocal = !ani.sanin.isOnline(activity)

        val model: MediaDetailsViewModel by activityViewModels()
        model.getMedia().observe(viewLifecycleOwner) { newMedia ->
            if (newMedia != null && newMedia.id != 0) {
                binding.commentsPoster.loadImage(newMedia.cover)
                binding.commentsLogo.setImageDrawable(null)
                binding.commentsTitle.visibility = View.GONE

                lifecycleScope.launch {
                    val logoUrl = LogoApi.getLogoUrl(newMedia.id)
                    if (logoUrl != null) {
                        binding.commentsLogo.visibility = View.VISIBLE
                        binding.commentsTitle.visibility = View.GONE
                        binding.commentsLogo.loadImage(logoUrl)
                    } else {
                        binding.commentsLogo.visibility = View.GONE
                        binding.commentsTitle.visibility = View.VISIBLE
                        binding.commentsTitle.text = newMedia.userPreferredName ?: newMedia.name
                    }
                }

                userProgress = newMedia.userProgress
                updateCurrentProgressButton()

                updateListEditorText(newMedia.userStatus)
                setupListEditor()

                if (!commentsLoaded || newMedia.id != this.mediaId) {
                    this.mediaId = newMedia.id
                    commentsLoaded = true

                    if (isOfflineOrLocal) {
                        binding.commentsOfflineText.visibility = View.VISIBLE
                        binding.commentsList.visibility = View.GONE
                        binding.commentCurrentProgress.visibility = View.GONE
                        binding.commentMessageContainer.visibility = View.GONE
                        binding.commentsProgressBar.visibility = View.GONE
                    } else {
                        binding.commentsProgressBar.visibility = View.VISIBLE
                        binding.commentsList.visibility = View.GONE
                        Logger.log("Comments: starting load media=$mediaId (offline=false)")
                        lifecycleScope.launch {
                            val commentId = arguments?.getInt("commentId")
                            if (commentId != null && commentId > 0) {
                                loadSingleComment(commentId)
                            } else {
                                loadAndDisplayComments()
                            }
                        }
                    }
                    if (CommentsAPI.authToken == null) {
                        binding.commentMessageContainer.visibility = View.GONE
                    }
                }
            }
        }

        setupInputListeners()
        updateCurrentProgressButton()
    }

    private fun updateListEditorText(userStatus: String?) {
        val statuses: Array<String> = resources.getStringArray(R.array.status)
        val statusStrings = resources.getStringArray(R.array.status_anime)
        val userStatusText =
            if (userStatus != null) statusStrings[statuses.indexOf(userStatus).coerceAtLeast(0)] else null
        if (userStatusText != null) {
            binding.commentsListEditor.text = userStatusText
        } else {
            binding.commentsListEditor.setText(R.string.add_list)
        }
    }

    private fun setupListEditor() {
        binding.commentsListEditor.setOnClickListener {
            val rescueMode: Boolean = PrefManager.getVal(PrefName.RescueMode)
            val fm = requireActivity().supportFragmentManager
            if (fm.findFragmentByTag("dialog") == null) {
                if (rescueMode) {
                    if (MAL.token != null) {
                        MediaListDialogFragment().show(fm, "dialog")
                    } else snackString("Please login to MAL")
                } else if (Anilist.userid != null) {
                    MediaListDialogFragment().show(fm, "dialog")
                } else snackString("Please login to AniList")
            }
        }
        binding.commentsListEditor.setOnLongClickListener {
            PrefManager.setCustomVal("${mediaId}_progressDialog", true)
            snackString(getString(R.string.auto_update_reset))
            true
        }
    }

    private fun setupCarousel() {
        carouselAdapter = CommentsCarouselAdapter(this, markwon)
        val lm = CommentsCarouselLayoutManager(activity)
        binding.commentsList.layoutManager = lm
        binding.commentsList.adapter = carouselAdapter
        binding.commentsList.itemAnimator = null
        binding.commentsList.clipChildren = false
        binding.commentsList.clipToPadding = false

        binding.commentsList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    carouselAdapter.setFocusedPosition(lm.focusedPosition)
                }
            }
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                carouselAdapter.setFocusedPosition(lm.focusedPosition)
                val lastVisible = lm.findLastVisibleItemPosition()
                if (lastVisible >= carouselAdapter.itemCount - 3) {
                    revealNextBatch()
                }
            }
        })

        binding.commentsList.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: View) {
                if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                    view.nextFocusRightId = R.id.commentsPoster
                }
            }
            override fun onChildViewDetachedFromWindow(view: View) {}
        })

        binding.commentsList.setOnKeyListener { v, keyCode, event ->
            if (event.action != android.view.KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (keyCode) {
                android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (lm.focusedPosition < carouselAdapter.itemCount - 1) {
                        lm.scrollToNext()
                        carouselAdapter.setFocusedPosition(lm.focusedPosition)
                    }
                    true
                }
                android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                    if (lm.focusedPosition > 0) {
                        lm.scrollToPrevious()
                        carouselAdapter.setFocusedPosition(lm.focusedPosition)
                    }
                    true
                }
                android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                        (activity as MediaDetailsActivity).showNavPills()
                        (activity as MediaDetailsActivity).focusNavPillForSelectedTab()
                    }
                    true
                }
                android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER -> {
                    val pos = lm.focusedPosition
                    if (pos in 0 until carouselAdapter.itemCount) {
                        carouselAdapter.currentList.getOrNull(pos)?.let { openCommentDetail(it) }
                    }
                    true
                }
                else -> false
            }
        }

        binding.commentsList.post { binding.commentsList.requestFocus() }
    }

    private fun setupInputListeners() {
        binding.commentSend.setOnClickListener {
            if (CommentsAPI.isBanned) {
                snackString("You are banned from commenting :(")
                return@setOnClickListener
            }
            if (PrefManager.getVal(PrefName.FirstComment)) {
                showCommentRulesDialog()
            } else {
                showTagDialogThenProcess()
            }
        }

        binding.commentReplyToCancel.setOnClickListener {
            resetOldState()
        }

        binding.commentSpoiler.setOnClickListener {
            isSpoilerMode = !isSpoilerMode
            binding.commentSpoiler.setImageResource(
                if (isSpoilerMode) R.drawable.format_spoiler_24
                else R.drawable.ic_round_remove_red_eye_24
            )
        }

        binding.commentGif.setOnClickListener {
            val gifPicker = GifPickerBottomDialog.newInstance()
            gifPicker.setOnGifSelectedListener { gifUrl ->
                val currentText = binding.commentInput.text.toString()
                val gifMarkdown = "![gif]($gifUrl)"
                val newText = if (currentText.isEmpty()) gifMarkdown else "$currentText\n$gifMarkdown"
                binding.commentInput.setText(newText)
                binding.commentInput.setSelection(newText.length)
            }
            gifPicker.show(childFragmentManager, "gifPicker")
        }

        binding.commentInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                TvKeyboardUtil.showKeyboardDelayed(binding.commentInput)
            }
        }
    }

    private fun updateCurrentProgressButton() {
        val progress = userProgress ?: 0
        if (progress <= 0) {
            binding.commentCurrentProgress.visibility = View.GONE
            return
        }
        val label = "Ep"
        val activeFilter = filterTag ?: progress
        binding.commentCurrentProgress.text = "$label $activeFilter"
        binding.commentCurrentProgress.visibility = View.VISIBLE
    }

    suspend fun loadAndDisplayComments() {
        binding.commentsProgressBar.visibility = View.VISIBLE
        binding.commentsList.visibility = View.GONE
        binding.commentsOfflineText.visibility = View.GONE
        displayedComments.clear()
        anikotoCommentPool.clear()
        saninComments.clear()
        anikotoLoadDone = false
        saninRevealed = false
        pagesLoaded = 1

        val hasAnikoto = PrefManager.getVal<Int>(PrefName.AnikotoCommentsEnabled) == 1
        anikotoLoadDone = !hasAnikoto
        Logger.log("Comments: loading comments for media $mediaId (anikoto=$hasAnikoto)")

        coroutineScope {
            val jobs = mutableListOf<Job>()
            if (hasAnikoto) {
                jobs += launch { loadAnikotoComments() }
            }
            jobs += launch { loadProvider("Sanin") { loadSaninComments() } }
            jobs.forEach { it.join() }
        }

        revealNextBatch()
        renderComments()
    }

    /**
     * Runs one comment provider with a hard timeout and renders whatever is
     * available immediately, so a slow or failing source never blocks the others.
     */
    private suspend fun loadProvider(tag: String, block: suspend () -> Unit) {
        try {
            withTimeoutOrNull(PROVIDER_TIMEOUT_MS) { block() }
        } catch (e: Exception) {
            Logger.log(Log.ERROR, "Comments: $tag comments failed: ${e.message}")
        }
        renderComments()
    }

    private fun renderComments() {
        Logger.log("Comments: rendered ${displayedComments.size} comments")
        carouselAdapter.submitList(displayedComments.toList())
        binding.commentsProgressBar.visibility = View.GONE
        binding.commentsList.visibility = View.VISIBLE
        if (displayedComments.isNotEmpty()) binding.commentsList.requestFocus()
    }

    /** Reveals up to 15 comments per call so TV rendering stays smooth. */
    private fun revealNextBatch() {
        if (revealInProgress) return
        revealInProgress = true
        try {
            var added = false
            var count = 0
            while (count < REVEAL_BATCH_SIZE && anikotoCommentPool.isNotEmpty()) {
                displayedComments.add(anikotoCommentPool.removeAt(0))
                count++
                added = true
            }
            if (count < REVEAL_BATCH_SIZE && anikotoCommentPool.isEmpty() && anikotoLoadDone &&
                !saninRevealed && saninComments.isNotEmpty()
            ) {
                val batch = saninComments.take(REVEAL_BATCH_SIZE - count)
                saninComments.removeAll(batch)
                displayedComments.addAll(batch)
                added = true
                if (saninComments.isEmpty()) saninRevealed = true
            }
            if (added) {
                carouselAdapter.submitList(displayedComments.toList())
                binding.commentsProgressBar.visibility = View.GONE
                binding.commentsList.visibility = View.VISIBLE
                if (displayedComments.isNotEmpty()) binding.commentsList.requestFocus()
            }
        } finally {
            revealInProgress = false
        }
    }

    private suspend fun loadSaninComments() {
        val effectiveFilter = getEffectiveFilter()
        Logger.log("Comments: fetching Sanin comments media=$mediaId tag=$effectiveFilter")
        try {
            val comments = withContext(Dispatchers.IO) {
                CommentsAPI.getCommentsForId(mediaId, page = 1, tag = effectiveFilter, sort = null)
            }
            saninComments.addAll(comments?.comments ?: emptyList())
            saninComments.sortByDescending { timestampToMillis(it.timestamp) }
            totalPages = comments?.totalPages ?: 1
            Logger.log("Comments: Sanin fetched ${saninComments.size} comments (totalPages=${comments?.totalPages})")
        } catch (e: Exception) {
            Logger.log(Log.ERROR, "Comments: Sanin fetch FAILED: ${e.message}")
        }
    }

    private suspend fun loadAnikotoComments() {
        if (mediaName.isBlank()) return
        Logger.log("Comments: fetching Anikoto comments title=$mediaName progress=$userProgress")
        try {
            withTimeoutOrNull(ANIKOTO_TOTAL_TIMEOUT_MS) {
                AnikotoAPI.getCommentsForMedia(mediaId, mediaName, userProgress) { batch ->
                    anikotoCommentPool.addAll(batch)
                    revealNextBatch()
                }
            }
        } catch (e: Exception) {
            Logger.log(Log.ERROR, "Comments: Anikoto comments failed: ${e.message}")
        } finally {
            anikotoLoadDone = true
            revealNextBatch()
        }
    }

    private suspend fun loadSingleComment(commentId: Int) {
        binding.commentsProgressBar.visibility = View.VISIBLE
        binding.commentsList.visibility = View.GONE
        displayedComments.clear()
        Logger.log("Comments: loading single comment $commentId for media $mediaId")

        val comment = withContext(Dispatchers.IO) {
            CommentsAPI.getSingleComment(commentId)
        }
        if (comment != null) displayedComments.add(comment)
        else Logger.log(Log.ERROR, "Comments: single comment $commentId NOT FOUND")

        carouselAdapter.submitList(displayedComments.toList())
        binding.commentsProgressBar.visibility = View.GONE
        binding.commentsList.visibility = View.VISIBLE
    }

    private fun timestampToMillis(timestamp: String): Long {
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            fmt.timeZone = TimeZone.getTimeZone("UTC")
            fmt.parse(timestamp)?.time ?: 0L
        } catch (_: Exception) { 0L }
    }

    fun voteComment(comment: Comment, voteType: Int, position: Int) {
        if (comment.isAnikoto) {
            snackString("Voting on Anikoto comments coming soon")
            return
        }
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                CommentsAPI.vote(comment.commentId, voteType)
            }
            if (result != null) {
                comment.userVoteType = voteType
                if (voteType == 1) comment.upvotes++
                else if (voteType == -1) comment.downvotes++
                else {
                    if (comment.userVoteType == 1) comment.upvotes--
                    else comment.downvotes--
                }
                carouselAdapter.notifyItemChanged(position)
            } else {
                snackString("Vote failed")
            }
        }
    }

    fun startReply(comment: Comment) {
        if (comment.isAnikoto) {
            snackString("Replying on Anikoto comments coming soon")
            return
        }
        commentWithInteraction = comment
        binding.commentReplyToContainer.visibility = View.VISIBLE
        binding.commentReplyTo.text = "Replying to ${comment.username}"
        binding.commentInput.requestFocus()
        TvKeyboardUtil.showKeyboardDelayed(binding.commentInput)
        interactionState = InteractionState.REPLY
    }

    fun showCommentMenu(comment: Comment, position: Int) {
        activity.customAlertDialog().apply {
            setTitle(comment.username)
            singleChoiceItems(arrayOf("View Full", "Copy Text", "Report")) { which ->
                when (which) {
                    0 -> openCommentDetail(comment)
                    1 -> {
                        val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("comment", comment.content))
                        snackString("Copied")
                    }
                    2 -> {
                        if (comment.isAnikoto) {
                            snackString("Reporting Anikoto comments coming soon")
                        } else {
                            lifecycleScope.launch {
                                withContext(Dispatchers.IO) { CommentsAPI.reportComment(comment.commentId, comment.username, mediaName, comment.userId) }
                                snackString("Reported")
                            }
                        }
                    }
                }
            }
            show()
        }
    }

    fun openCommentDetail(comment: Comment) {
        if (childFragmentManager.findFragmentByTag("commentZoom") != null) return
        val dialog = CommentZoomDialog()
        val bundle = Bundle().apply {
            putInt("commentId", comment.commentId)
            putString("content", comment.content)
            putString("username", comment.username)
            putString("avatarUrl", comment.profilePictureUrl)
            putString("timestamp", comment.timestamp)
            putInt("upvotes", comment.upvotes)
            putInt("downvotes", comment.downvotes)
            putInt("userVoteType", comment.userVoteType ?: 0)
        }
        dialog.arguments = bundle
        dialog.listener = object : CommentZoomDialog.ZoomActionListener {
            override fun onReply(commentId: Int, username: String) {
                startReply(comment)
            }
            override fun onVote(commentId: Int, voteType: Int, currentVoteType: Int) {
                val idx = displayedComments.indexOfFirst { it.commentId == commentId }
                if (idx >= 0) voteComment(comment, voteType, idx)
            }
        }
        dialog.show(childFragmentManager, "commentZoom")
    }

    fun showReplies(comment: Comment, position: Int) {
        if (comment.replyCount <= 0) return
        lifecycleScope.launch {
            val replies = withContext(Dispatchers.IO) {
                AnikotoAPI.getReplies(comment.commentId, comment.anikotoEpisode ?: 0, mediaId)
            }
            if (replies.isEmpty()) {
                snackString("No replies yet")
                return@launch
            }
            val idx = displayedComments.indexOfFirst { it.commentId == comment.commentId }
            if (idx >= 0) {
                displayedComments.addAll(idx + 1, replies)
                carouselAdapter.submitList(displayedComments.toList())
            }
        }
    }

    private fun getEffectiveFilter(): Int? = when {
        filterTag != null -> filterTag
        isAutoFilterOn && userProgress != null && userProgress!! > 0 -> userProgress
        else -> null
    }

    private fun resetOldState(): InteractionState {
        val oldState = interactionState
        interactionState = InteractionState.NONE
        commentWithInteraction = null
        binding.commentReplyToContainer.visibility = View.GONE
        binding.commentInput.setText("")
        val imm = activity.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.commentInput.windowToken, 0)
        return when (oldState) {
            InteractionState.EDIT -> InteractionState.EDIT
            InteractionState.REPLY -> InteractionState.REPLY
            else -> InteractionState.NONE
        }
    }

    private fun showTagDialogThenProcess() {
        if (interactionState == InteractionState.EDIT) {
            processComment()
            return
        }
        val commentText = binding.commentInput.text.toString()
        if (commentText.isEmpty()) {
            snackString("Comment cannot be empty")
            return
        }
        processComment()
    }

    private fun processComment() {
        val commentText = binding.commentInput.text.toString()
        if (commentText.isEmpty()) {
            snackString("Comment cannot be empty")
            return
        }
        val finalText = if (isSpoilerMode) "||$commentText||" else commentText

        lifecycleScope.launch {
            when (interactionState) {
                InteractionState.EDIT -> handleEditComment(finalText)
                else -> handleNewComment(finalText)
            }
            resetOldState()
        }
    }

    private suspend fun handleNewComment(text: String) {
        val parentId = if (interactionState == InteractionState.REPLY) {
            commentWithInteraction?.commentId
        } else null

        val result = withContext(Dispatchers.IO) {
            CommentsAPI.comment(mediaId, parentId, text, tag)
        }
        if (result != null) {
            Logger.log("Comments: posted comment id=${result.commentId} media=$mediaId parent=$parentId")
            val newComment = Comment(
                commentId = result.commentId ?: 0,
                userId = (Anilist.userid ?: 0).toString(),
                mediaId = mediaId,
                parentCommentId = parentId,
                content = text,
                timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(System.currentTimeMillis()),
                deleted = false,
                tag = tag,
                upvotes = 0,
                downvotes = 0,
                userVoteType = 0,
                username = Anilist.username ?: "Unknown",
                profilePictureUrl = Anilist.avatar,
                totalVotes = 0
            )
            displayedComments.add(0, newComment)
            carouselAdapter.submitList(displayedComments.toList())
            carouselAdapter.notifyItemInserted(0)
            snackString("Comment posted")
        } else {
            Logger.log(Log.ERROR, "Comments: FAILED to post comment media=$mediaId parent=$parentId")
            snackString("Failed to post comment")
        }
    }

    private suspend fun handleEditComment(text: String) {
        val target = commentWithInteraction ?: return
        val result = withContext(Dispatchers.IO) {
            CommentsAPI.editComment(target.commentId, text)
        }
        if (result != null) {
            Logger.log("Comments: edited comment id=${target.commentId}")
            target.content = text
            val idx = displayedComments.indexOfFirst { it.commentId == target.commentId }
            if (idx >= 0) carouselAdapter.notifyItemChanged(idx)
            snackString("Comment edited")
        } else {
            Logger.log(Log.ERROR, "Comments: FAILED to edit comment id=${target.commentId}")
            snackString("Failed to edit comment")
        }
    }

    private fun showCommentRulesDialog() {
        activity.customAlertDialog().apply {
            setTitle("Welcome to Comments")
            setMessage("By commenting, you agree to follow the community rules. Be respectful, no spam, no NSFW.")
            setPosButton("Got it") {
                PrefManager.setVal(PrefName.FirstComment, false)
                showTagDialogThenProcess()
            }
            setNegButton("Cancel") { }
            show()
        }
    }

    companion object {
        private const val PROVIDER_TIMEOUT_MS = 12_000L
        private const val REVEAL_BATCH_SIZE = 15
        private const val ANIKOTO_TOTAL_TIMEOUT_MS = 300_000L

        fun resolveColorAttr(attr: Int, context: android.content.Context): Int {
            val typedArray = context.obtainStyledAttributes(intArrayOf(attr))
            val color = typedArray.getColor(0, 0xFFBB86FC.toInt())
            typedArray.recycle()
            return color
        }
    }
}
