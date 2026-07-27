package ani.sanin.media.comments

import android.annotation.SuppressLint
import android.content.Context.INPUT_METHOD_SERVICE
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import ani.sanin.connections.LogoApi
import ani.sanin.connections.anilist.Anilist
import ani.sanin.connections.comments.Comment
import ani.sanin.connections.comments.CommentsAPI
import ani.sanin.connections.trakt.TraktAPI
import ani.sanin.connections.trakt.TraktAuth
import ani.sanin.connections.trakt.TraktComment
import ani.sanin.connections.trakt.TraktSearchResult
import ani.sanin.databinding.FragmentCommentsBinding
import ani.sanin.loadImage
import ani.sanin.media.MediaDetailsActivity
import ani.sanin.media.MediaDetailsViewModel
import ani.sanin.others.IdMappers
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.snackString
import ani.sanin.util.TvKeyboardUtil
import ani.sanin.util.customAlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private var backgroundColor: Int = 0
    var pagesLoaded = 1
    var totalPages = 1
    private var userProgress: Int? = null
    private var totalEpisodesOrChapters: Int? = null
    private var isAnime: Boolean = true
    private var commentsLoaded = false
    private var isAutoFilterOn = false
    private var isSpoilerMode = false

    private var currentSource = CommentSource.DANOTSU
    private var traktResult: TraktSearchResult? = null
    private var displayedComments = mutableListOf<Comment>()
    private lateinit var carouselAdapter: CommentsCarouselAdapter

    enum class CommentSource { DANOTSU, TRAKT }
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
        backgroundColor = (binding.root.background as? ColorDrawable)?.color ?: 0

        setupCarousel()

        binding.commentUserAvatar.loadImage(Anilist.avatar)
        val markwon = buildMarkwon(activity, fragment = this)
        val markwonEditor = io.noties.markwon.editor.MarkwonEditor.create(markwon)
        binding.commentInput.addTextChangedListener(
            io.noties.markwon.editor.MarkwonEditorTextWatcher.withProcess(markwonEditor)
        )

        val isOfflineOrLocal = !ani.sanin.isOnline(activity)

        val model: MediaDetailsViewModel by activityViewModels()
        model.getMedia().observe(viewLifecycleOwner) { newMedia ->
            if (newMedia != null && newMedia.id != 0) {
                binding.commentsTitle.text = newMedia.userPreferredName ?: newMedia.name

                if (newMedia.cover != null) {
                    binding.commentsPoster.loadImage(newMedia.cover)
                }

                val fm = requireActivity().supportFragmentManager
                isAnime = newMedia.anime != null
                userProgress = newMedia.userProgress
                totalEpisodesOrChapters = newMedia.anime?.totalEpisodes
                updateCurrentProgressButton()

                if (!commentsLoaded || newMedia.id != this.mediaId) {
                    this.mediaId = newMedia.id
                    commentsLoaded = true
                    traktResult = null
                    currentSource = CommentSource.DANOTSU

                    lifecycleScope.launch {
                        traktResult = lookupTraktIds()
                        updateSourceBarVisibility()
                    }

                    if (isOfflineOrLocal) {
                        binding.commentsOfflineText.visibility = View.VISIBLE
                        binding.commentsList.visibility = View.GONE
                        binding.commentSourceBar.visibility = View.GONE
                        binding.commentCurrentProgress.visibility = View.GONE
                        binding.commentMessageContainer.visibility = View.GONE
                        binding.commentsProgressBar.visibility = View.GONE
                    } else if (CommentsAPI.authToken != null) {
                        lifecycleScope.launch {
                            val commentId = arguments?.getInt("commentId")
                            if (commentId != null && commentId > 0) {
                                loadSingleComment(commentId)
                            } else {
                                loadAndDisplayComments()
                            }
                        }
                    } else {
                        binding.commentMessageContainer.visibility = View.GONE
                    }
                }
            }
        }

        binding.commentsPoster.nextFocusRightId = binding.commentInput.id

        setupSourceButtons()
        setupInputListeners()
        updateSourceBarVisibility()
        updateCurrentProgressButton()
    }

    private fun setupCarousel() {
        carouselAdapter = CommentsCarouselAdapter(this)
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
            }
        })

        binding.commentsList.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: View) {
                if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                    view.nextFocusRightId = R.id.commentSourceBar
                } else {
                    view.nextFocusDownId = R.id.commentSourceBar
                }
                view.setOnKeyListener { v, keyCode, event ->
                    if (event.action != android.view.KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                    val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    when (keyCode) {
                        android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (lm.focusedPosition < carouselAdapter.itemCount - 1) {
                                lm.scrollToNext(); true
                            } else if (!isLandscape) {
                                false // at bottom edge in portrait → fall through to source bar
                            } else {
                                true
                            }
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                            if (lm.focusedPosition > 0) {
                                lm.scrollToPrevious(); true
                            } else if (!isLandscape) {
                                false // at top edge in portrait → fall through
                            } else {
                                true
                            }
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (isLandscape) {
                                (activity as MediaDetailsActivity).showNavPills()
                                (activity as MediaDetailsActivity).focusNavPillForSelectedTab()
                            }
                            true
                        }
                        else -> false
                    }
                }
            }
            override fun onChildViewDetachedFromWindow(view: View) {
                view.setOnKeyListener(null)
            }
        })
    }

    private fun setupSourceButtons() {
        binding.commentSourceSanin.setOnClickListener {
            if (currentSource != CommentSource.DANOTSU) {
                currentSource = CommentSource.DANOTSU
                highlightSource()
                lifecycleScope.launch { loadAndDisplayComments() }
            }
        }
        binding.commentSourceTrakt.setOnClickListener {
            if (currentSource != CommentSource.TRAKT) {
                currentSource = CommentSource.TRAKT
                highlightSource()
                lifecycleScope.launch { loadAndDisplayComments() }
            }
        }
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

    private fun highlightSource() {
        val primary = resolveColorAttr(com.google.android.material.R.attr.colorPrimary)
        val onPrimary = resolveColorAttr(com.google.android.material.R.attr.colorOnPrimary)
        val onBg = 0xFF888888.toInt()

        when (currentSource) {
            CommentSource.DANOTSU -> {
                binding.commentSourceSanin.setTextColor(onPrimary)
                binding.commentSourceSanin.setBackgroundColor(primary)
                binding.commentSourceTrakt.setTextColor(onBg)
                binding.commentSourceTrakt.background = null
            }
            CommentSource.TRAKT -> {
                binding.commentSourceTrakt.setTextColor(onPrimary)
                binding.commentSourceTrakt.setBackgroundColor(primary)
                binding.commentSourceSanin.setTextColor(onBg)
                binding.commentSourceSanin.background = null
            }
        }
        updateUiForSource()
    }

    private fun updateUiForSource() {
        val isSanin = currentSource == CommentSource.DANOTSU
        binding.commentMessageContainer.visibility =
            if (isSanin && CommentsAPI.authToken != null) View.VISIBLE
            else if (!isSanin && TraktAuth.isLoggedIn()) View.VISIBLE
            else View.GONE
        binding.commentCurrentProgress.visibility = if (isSanin && (userProgress ?: 0) > 0) View.VISIBLE else View.GONE
    }

    private suspend fun lookupTraktIds(): TraktSearchResult? {
        val imdbId = IdMappers.getImdbId(mediaId) ?: return null
        return TraktAPI.searchByImdb(imdbId)
    }

    private fun updateSourceBarVisibility() {
        val hasTrakt = traktResult != null && PrefManager.getVal<Int>(PrefName.TraktCommentsEnabled) == 1
        binding.commentSourceBar.visibility = if (hasTrakt) View.VISIBLE else View.GONE
        if (hasTrakt) highlightSource()
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
        pagesLoaded = 1

        when (currentSource) {
            CommentSource.DANOTSU -> loadSaninComments()
            CommentSource.TRAKT -> loadTraktComments()
        }

        carouselAdapter.submitList(displayedComments.toList())
        binding.commentsProgressBar.visibility = View.GONE
        binding.commentsList.visibility = View.VISIBLE
    }

    private suspend fun loadSaninComments() {
        val effectiveFilter = getEffectiveFilter()
        val comments = withContext(Dispatchers.IO) {
            CommentsAPI.getCommentsForId(mediaId, page = 1, tag = effectiveFilter, sort = null)
        }
        displayedComments.addAll(sortComments(comments?.comments))
        totalPages = comments?.totalPages ?: 1
    }

    private suspend fun loadTraktComments() {
        val type = traktResult?.mediaType ?: run {
            withContext(Dispatchers.Main) { snackString("Trakt: media not found") }
            totalPages = 1
            return
        }
        val id = traktResult?.traktId ?: run {
            withContext(Dispatchers.Main) { snackString("Trakt: media not found") }
            totalPages = 1
            return
        }
        val sort = when (PrefManager.getVal(PrefName.CommentSortOrder, "newest")) {
            "newest" -> "newest"
            "oldest" -> "oldest"
            else -> "likes"
        }
        val traktComments = withContext(Dispatchers.IO) {
            TraktAPI.getComments(type, id, page = 1, sort = sort)
        }
        displayedComments.addAll(sortComments(traktComments.map { traktToComment(it) }))
        totalPages = if (traktComments.size < 25) 1 else 2
    }

    private fun traktToComment(tc: TraktComment): Comment {
        val avatarUrl = tc.user.images?.avatar?.full
        return Comment(
            commentId = tc.id,
            userId = tc.user.username,
            mediaId = mediaId,
            parentCommentId = if (tc.parentId > 0) tc.parentId else null,
            content = tc.comment,
            timestamp = tc.createdAt,
            deleted = false,
            tag = null,
            upvotes = tc.likes,
            downvotes = 0,
            userVoteType = if (tc.userLiked) 1 else 0,
            username = tc.user.name ?: tc.user.username,
            profilePictureUrl = avatarUrl,
            totalVotes = tc.likes,
            isTrakt = true
        )
    }

    private suspend fun loadSingleComment(commentId: Int) {
        binding.commentsProgressBar.visibility = View.VISIBLE
        binding.commentsList.visibility = View.GONE
        displayedComments.clear()

        val comment = withContext(Dispatchers.IO) {
            CommentsAPI.getSingleComment(commentId)
        }
        if (comment != null) displayedComments.add(comment)

        carouselAdapter.submitList(displayedComments.toList())
        binding.commentsProgressBar.visibility = View.GONE
        binding.commentsList.visibility = View.VISIBLE
    }

    private fun sortComments(comments: List<Comment>?): List<Comment> {
        if (comments == null) return emptyList()
        return when (PrefManager.getVal(PrefName.CommentSortOrder, "newest")) {
            "newest" -> comments.sortedByDescending { timestampToMillis(it.timestamp) }
            "oldest" -> comments.sortedBy { timestampToMillis(it.timestamp) }
            "highest_rated" -> comments.sortedByDescending { it.upvotes - it.downvotes }
            "lowest_rated" -> comments.sortedBy { it.upvotes - it.downvotes }
            else -> comments
        }
    }

    private fun timestampToMillis(timestamp: String): Long {
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            fmt.timeZone = TimeZone.getTimeZone("UTC")
            fmt.parse(timestamp)?.time ?: 0L
        } catch (_: Exception) { 0L }
    }

    fun voteComment(comment: Comment, voteType: Int, position: Int) {
        if (currentSource == CommentSource.TRAKT) {
            snackString("Voting on Trakt comments coming soon")
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
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO) { CommentsAPI.reportComment(comment.commentId, comment.username, mediaName, comment.userId) }
                            snackString("Reported")
                        }
                    }
                }
            }
            show()
        }
    }

    fun openCommentDetail(comment: Comment) {
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
            putBoolean("isTrakt", comment.isTrakt)
        }
        dialog.arguments = bundle
        dialog.listener = object : CommentZoomDialog.ZoomActionListener {
            override fun onReply(commentId: Int, username: String) {
                startReply(comment)
            }
            override fun onVote(commentId: Int, voteType: Int, currentVoteType: Int, isTrakt: Boolean) {
                val idx = displayedComments.indexOfFirst { it.commentId == commentId }
                if (idx >= 0) voteComment(comment, voteType, idx)
            }
        }
        dialog.show(childFragmentManager, "commentZoom")
    }

    private fun getEffectiveFilter(): Int? = when {
        filterTag != null -> filterTag
        isAutoFilterOn && userProgress != null && userProgress!! > 0 -> userProgress
        else -> null
    }

    private fun resolveColorAttr(attr: Int): Int {
        val typedArray = activity.obtainStyledAttributes(intArrayOf(attr))
        val color = typedArray.getColor(0, 0)
        typedArray.recycle()
        return color
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
                InteractionState.REPLY -> handleNewComment(finalText)
                else -> handleNewComment(finalText)
            }
            resetOldState()
        }
    }

    private suspend fun handleNewComment(text: String) {
        val parentId = if (interactionState == InteractionState.REPLY) {
            commentWithInteraction?.commentId
        } else null

        if (currentSource == CommentSource.TRAKT) {
            handleTraktNewComment(text, parentId)
            return
        }

        val result = withContext(Dispatchers.IO) {
            CommentsAPI.comment(mediaId, parentId, text, tag)
        }
        if (result != null) {
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
            snackString("Failed to post comment")
        }
    }

    private suspend fun handleEditComment(text: String) {
        val target = commentWithInteraction ?: return
        val result = withContext(Dispatchers.IO) {
            CommentsAPI.editComment(target.commentId, text)
        }
        if (result != null) {
            target.content = text
            val idx = displayedComments.indexOfFirst { it.commentId == target.commentId }
            if (idx >= 0) carouselAdapter.notifyItemChanged(idx)
            snackString("Comment edited")
        } else {
            snackString("Failed to edit comment")
        }
    }

    private suspend fun handleTraktNewComment(text: String, parentId: Int?) {
        val type = traktResult?.mediaType ?: return
        val id = traktResult?.traktId ?: return
        val isSpoiler = isSpoilerMode
        withContext(Dispatchers.IO) {
            if (parentId != null) {
                TraktAPI.replyToComment(parentId, text)
            } else {
                TraktAPI.postComment(type, id, text, isSpoiler)
            }
        }
        snackString("Trakt comment posted")
        loadAndDisplayComments()
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
        fun resolveColorAttr(attr: Int, context: android.content.Context): Int {
            val typedArray = context.obtainStyledAttributes(intArrayOf(attr))
            val color = typedArray.getColor(0, 0xFFBB86FC.toInt())
            typedArray.recycle()
            return color
        }
    }
}
