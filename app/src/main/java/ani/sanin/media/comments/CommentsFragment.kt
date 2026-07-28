package ani.sanin.media.comments

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.*
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
import ani.sanin.connections.comments.CommentsAPI
import ani.sanin.connections.trakt.TraktAPI
import ani.sanin.media.MediaListDialogFragment
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
import ani.sanin.util.customAlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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
    private var commentText by mutableStateOf("")

    private var traktResult: TraktSearchResult? = null
    private var displayedComments = mutableListOf<Comment>()
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

        binding.commentInput.setContent {
            val focusManager = LocalFocusManager.current
            OutlinedTextField(
                value = commentText,
                onValueChange = { commentText = it },
                singleLine = false,
                maxLines = 3,
                placeholder = { androidx.compose.material3.Text("Add a comment...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .onPreviewKeyEvent { ev ->
                        if (ev.type == KeyEventType.KeyDown) {
                            when (ev.key) {
                                Key.DirectionDown -> { focusManager.moveFocus(FocusDirection.Down); true }
                                else -> false
                            }
                        } else false
                    },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = ComposeColor.White,
                    unfocusedTextColor = ComposeColor.White,
                    cursorColor = ComposeColor.White
                )
            )
        }

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
                    traktResult = null

                    if (isOfflineOrLocal) {
                        binding.commentsOfflineText.visibility = View.VISIBLE
                        binding.commentsList.visibility = View.GONE
                        binding.commentCurrentProgress.visibility = View.GONE
                        binding.commentMessageContainer.visibility = View.GONE
                        binding.commentsProgressBar.visibility = View.GONE
                    } else if (CommentsAPI.authToken != null || TraktAuth.isLoggedIn()) {
                        lifecycleScope.launch {
                            traktResult = lookupTraktIds()
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
            }
        })

        binding.commentsList.setOnKeyListener { v, keyCode, event ->
            if (event.action != android.view.KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (keyCode) {
                android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (lm.focusedPosition < carouselAdapter.itemCount - 1) {
                        lm.scrollToNext()
                    }
                    true
                }
                android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                    if (lm.focusedPosition > 0) {
                        lm.scrollToPrevious()
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
                else -> false
            }
        }

        binding.commentsList.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: View) {
                if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                    view.nextFocusRightId = R.id.commentsPoster
                }
            }
            override fun onChildViewDetachedFromWindow(view: View) {}
        })
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
                val currentText = commentText
                val gifMarkdown = "![gif]($gifUrl)"
                commentText = if (currentText.isEmpty()) gifMarkdown else "$currentText\n$gifMarkdown"
            }
            gifPicker.show(childFragmentManager, "gifPicker")
        }

        binding.commentInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
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

    private suspend fun lookupTraktIds(): TraktSearchResult? {
        val imdbId = IdMappers.getImdbId(mediaId) ?: return null
        return TraktAPI.searchByImdb(imdbId)
    }

    suspend fun loadAndDisplayComments() {
        binding.commentsProgressBar.visibility = View.VISIBLE
        binding.commentsList.visibility = View.GONE
        binding.commentsOfflineText.visibility = View.GONE
        displayedComments.clear()
        pagesLoaded = 1

        val hasTrakt = traktResult != null && PrefManager.getVal<Int>(PrefName.TraktCommentsEnabled) == 1
        coroutineScope {
            val traktDef = if (hasTrakt && traktResult != null) async { loadTraktComments() } else null
            val saninDef = async { loadSaninComments() }
            traktDef?.await()
            saninDef.await()
        }

        val merged = displayedComments.sortedByDescending { timestampToMillis(it.timestamp) }
        displayedComments.clear()
        displayedComments.addAll(merged)

        carouselAdapter.submitList(displayedComments.toList())
        binding.commentsProgressBar.visibility = View.GONE
        binding.commentsList.visibility = View.VISIBLE
        if (displayedComments.isNotEmpty()) binding.commentsList.requestFocus()
    }

    private suspend fun loadSaninComments() {
        val effectiveFilter = getEffectiveFilter()
        var comments: CommentResponse? = null
        repeat(3) { attempt ->
            comments = withTimeoutOrNull(10000) {
                withContext(Dispatchers.IO) {
                    CommentsAPI.getCommentsForId(mediaId, page = 1, tag = effectiveFilter, sort = null)
                }
            }
            if (comments != null) return@repeat
            if (attempt < 2) kotlinx.coroutines.delay(1000L shl attempt)
        }
        displayedComments.addAll(comments?.comments ?: emptyList())
        totalPages = comments?.totalPages ?: 1
    }

    private suspend fun loadTraktComments() {
        val type = traktResult?.mediaType ?: return
        val id = traktResult?.traktId ?: return
        val sort = when (PrefManager.getVal(PrefName.CommentSortOrder, "newest")) {
            "newest" -> "newest"
            "oldest" -> "oldest"
            else -> "likes"
        }
        val traktComments = withContext(Dispatchers.IO) {
            TraktAPI.getComments(type, id, page = 1, sort = sort)
        }
        displayedComments.addAll(traktComments.map { traktToComment(it) })
    }

    override fun onResume() {
        super.onResume()
        if (displayedComments.isNotEmpty()) binding.commentsList.requestFocus()
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

    private fun timestampToMillis(timestamp: String): Long {
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            fmt.timeZone = TimeZone.getTimeZone("UTC")
            fmt.parse(timestamp)?.time ?: 0L
        } catch (_: Exception) { 0L }
    }

    fun voteComment(comment: Comment, voteType: Int, position: Int) {
        if (comment.isTrakt) {
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

    private fun resetOldState(): InteractionState {
        val oldState = interactionState
        interactionState = InteractionState.NONE
        commentWithInteraction = null
        binding.commentReplyToContainer.visibility = View.GONE
        commentText = ""
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
        val commentTextVal = commentText
        if (commentTextVal.isEmpty()) {
            snackString("Comment cannot be empty")
            return
        }
        processComment()
    }

    private fun processComment() {
        val commentTextVal = commentText
        if (commentTextVal.isEmpty()) {
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

        if (traktResult != null && PrefManager.getVal<Int>(PrefName.TraktCommentsEnabled) == 1) {
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
