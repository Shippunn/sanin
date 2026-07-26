package ani.sanin.media

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import ani.sanin.GesturesListener
import ani.sanin.R
import ani.sanin.Refresh
import ani.sanin.connections.anilist.Anilist
import ani.sanin.connections.anizip.AniZip
import ani.sanin.connections.mal.MAL
import ani.sanin.databinding.ActivityMediaBinding
import ani.sanin.getThemeColor
import ani.sanin.initActivity
import ani.sanin.loadImage
import ani.sanin.openLinkInBrowser
import ani.sanin.media.anime.AnimeWatchFragment
import ani.sanin.media.comments.CommentsFragment
import ani.sanin.others.getSerialized
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.snackString
import ani.sanin.themes.ThemeManager
import ani.sanin.util.FocusEffectUtil
import ani.sanin.util.GlassComponent
import ani.sanin.util.GlassEffectManager
import ani.sanin.util.LauncherWrapper
import ani.sanin.util.NavPillCustomizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds


class MediaDetailsActivity : AppCompatActivity() {
    lateinit var launcher: LauncherWrapper
    lateinit var binding: ActivityMediaBinding
    private val scope = lifecycleScope
    private val model: MediaDetailsViewModel by viewModels()
    var selected = 0
    var anime = true
    private var adult = false
    private var hasComments = false
    private lateinit var watchFragment: AnimeWatchFragment
    private lateinit var commentsFragment: CommentsFragment
    private var commentsAdded = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        var media: Media = intent.getSerialized("media") ?: mediaSingleton ?: emptyMedia()
        val id = intent.getIntExtra("mediaId", -1)
        if (id != -1) {
            val rescueMode: Boolean = PrefManager.getVal(PrefName.RescueMode)
            runBlocking {
                withContext(Dispatchers.IO) {
                    if (rescueMode) {
                        val animeNode = MAL.query.getAnimeDetails(id)
                        media = if (animeNode != null) Media(animeNode, true)
                        else emptyMedia()
                    } else {
                        media = Anilist.query.getMedia(id, false) ?: emptyMedia()
                    }
                }
            }
        }
        if (media.name == "No media found") {
            snackString(media.name)
            onBackPressedDispatcher.onBackPressed()
            return
        }
        val contract = ActivityResultContracts.OpenDocumentTree()
        launcher = LauncherWrapper(this, contract)

        mediaSingleton = null
        ThemeManager(this).applyTheme()
        initActivity(this)
        MediaSingleton.bitmap = null

        binding = ActivityMediaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val isDownload = intent.getBooleanExtra("download", false)
        media.selected = model.loadSelected(media, isDownload)
        val rescueMode: Boolean = PrefManager.getVal(PrefName.RescueMode)
        hasComments = PrefManager.getVal<Int>(PrefName.CommentsEnabled) == 1 && !rescueMode

        // Load full-screen banner background (portrait mediaBg or landscape mediaBanner)
        val bannerBrightness = PrefManager.getVal<Float>(PrefName.BannerBrightness)
        if (bannerBrightness > 0f) {
            val fallbackUrl = media.banner ?: media.cover
            binding.mediaBg?.loadImage(fallbackUrl)
            binding.mediaBg?.alpha = bannerBrightness
            binding.mediaBgGradient?.alpha = bannerBrightness
            binding.mediaBanner?.loadImage(fallbackUrl)
            binding.mediaBanner?.alpha = bannerBrightness
            binding.mediaBannerNoKen?.loadImage(fallbackUrl)
            binding.mediaBannerNoKen?.alpha = bannerBrightness
            lifecycleScope.launch {
                val tmdbUrl = AniZip.getBackdropUrl(media.id)
                if (tmdbUrl != null) {
                    binding.mediaBg?.loadImage(tmdbUrl)
                    binding.mediaBanner?.loadImage(tmdbUrl)
                    binding.mediaBannerNoKen?.loadImage(tmdbUrl)
                }
            }
        } else {
            binding.mediaBg?.visibility = View.GONE
            binding.mediaBgGradient?.visibility = View.GONE
            binding.mediaBanner?.visibility = View.GONE
            binding.mediaBannerNoKen?.visibility = View.GONE
        }

        // Close button
        binding.mediaClose.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        FocusEffectUtil.applyFocusListener(binding.mediaClose)

        // Incognito mode
        if (PrefManager.getVal(PrefName.Incognito)) {
            binding.incognito.visibility = View.VISIBLE
        }

        // Load MediaInfoFragment into the left panel
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.mediaInfoFragmentContainer, MediaInfoFragment())
                .commit()
        }

        // Native nav pills (info/watch/comments — info now focuses the left panel)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val cornerPx = NavPillCustomizer.getCornerRadiusDp() * resources.displayMetrics.density
            binding.mediaNavPills?.outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, cornerPx)
                }
            }
            binding.mediaNavPills?.elevation = 10f
            binding.mediaNavPills?.clipToOutline = true
        }
        val primaryColor = getThemeColor(com.google.android.material.R.attr.colorPrimary)
        val onBgColor = getThemeColor(com.google.android.material.R.attr.colorOnBackground)
        val navInfo = binding.navPillInfo
        val navWatch = binding.navPillWatch
        val navComments = binding.navPillComments
        val allNav = listOfNotNull(navInfo, navWatch, navComments)
        allNav.forEach { FocusEffectUtil.applyFocusListener(it) }

        binding.navPillBg?.live = PrefManager.getVal<Boolean>(PrefName.AnimationsEnabled) && PrefManager.getVal<Boolean>(PrefName.LiveSideRail)
        binding.navPillBg?.setGlassEnabled(
            GlassEffectManager.isComponentEnabled(GlassComponent.NavPills)
        )
        binding.navPillBg?.doOnLayout { updateMediaNavIconTints(selected) }
        binding.mediaNavPills?.let { frame ->
            if (frame.childCount > 1 && frame.getChildAt(1) is LinearLayout) {
                NavPillCustomizer.applyToPillList(frame.getChildAt(1) as LinearLayout)
            }
        }

        fun showWatchTab(container: FrameLayout, animate: Boolean) {
            val ft = supportFragmentManager.beginTransaction()
            val alreadyAdded = ::watchFragment.isInitialized && watchFragment.isAdded
            if (alreadyAdded) {
                ft.show(watchFragment)
            } else {
                watchFragment = AnimeWatchFragment()
                ft.add(R.id.mediaTabContent, watchFragment, "watch")
            }
            if (::commentsFragment.isInitialized && commentsFragment.isAdded) {
                if (animate && alreadyAdded && PrefManager.getVal<Boolean>(PrefName.AnimationsEnabled) && PrefManager.getVal<Boolean>(PrefName.TransitionAnimations)) {
                    val watchView = watchFragment.requireView()
                    val commentsView = commentsFragment.requireView()
                    watchView.alpha = 0f
                    watchView.scaleX = 0.92f
                    watchView.scaleY = 0.92f
                    ft.hide(commentsFragment).commit()
                    watchView.animate()
                        .alpha(1f).scaleX(1f).scaleY(1f)
                        .setDuration(300)
                        .setInterpolator(android.view.animation.OvershootInterpolator())
                        .start()
                } else {
                    ft.hide(commentsFragment).commit()
                }
            } else {
                ft.commit()
            }
        }

        fun showCommentsTab(container: FrameLayout, animate: Boolean) {
            if (!commentsAdded) {
                commentsAdded = true
                val ft = supportFragmentManager.beginTransaction()
                commentsFragment = CommentsFragment().apply {
                    arguments = Bundle().apply {
                        putInt("mediaId", media.id)
                        putString("mediaName", media.mainName())
                        putString("mediaFormat", media.format)
                        val commentId = intent.getIntExtra("commentId", -1)
                        if (commentId != -1) putInt("commentId", commentId)
                    }
                }
                if (::watchFragment.isInitialized && watchFragment.isAdded) {
                    ft.hide(watchFragment)
                }
                ft.add(R.id.mediaTabContent, commentsFragment, "comments")
                ft.commit()
            } else {
                val ft = supportFragmentManager.beginTransaction()
                val watchAlreadyAdded = ::watchFragment.isInitialized && watchFragment.isAdded
                ft.show(commentsFragment)
                if (watchAlreadyAdded) {
                    if (animate && PrefManager.getVal<Boolean>(PrefName.AnimationsEnabled) && PrefManager.getVal<Boolean>(PrefName.TransitionAnimations)) {
                        val watchView = watchFragment.requireView()
                        val commentsView = commentsFragment.requireView()
                        commentsView.alpha = 0f
                        commentsView.scaleX = 0.92f
                        commentsView.scaleY = 0.92f
                        ft.hide(watchFragment).commit()
                        commentsView.animate()
                            .alpha(1f).scaleX(1f).scaleY(1f)
                            .setDuration(300)
                            .setInterpolator(android.view.animation.OvershootInterpolator())
                            .start()
                    } else {
                        ft.hide(watchFragment).commit()
                    }
                } else {
                    ft.commit()
                }
            }
        }

        fun selectTab(idx: Int, animate: Boolean = true) {
            selected = idx
            updateMediaNavIconTints(selected)
            when (idx) {
                0 -> {
                    binding.mediaInfoFragmentContainer!!.visibility = View.VISIBLE
                    binding.mediaRightPanel!!.visibility = View.GONE
                }
                1 -> {
                    binding.mediaInfoFragmentContainer!!.visibility = View.GONE
                    binding.mediaRightPanel!!.visibility = View.VISIBLE
                    binding.mediaTabContent?.let {
                        showWatchTab(it, animate)
                        it.requestFocus()
                    }
                }
                2 -> {
                    binding.mediaInfoFragmentContainer!!.visibility = View.GONE
                    binding.mediaRightPanel!!.visibility = View.VISIBLE
                    binding.mediaTabContent?.let {
                        showCommentsTab(it, animate)
                        it.requestFocus()
                    }
                }
            }
            val sel = model.loadSelected(media, isDownload)
            sel.window = idx
            model.saveSelected(media.id, sel)
        }

        navInfo?.setOnClickListener { selectTab(0); hideNavPills() }
        navWatch?.setOnClickListener { selectTab(1); hideNavPills() }
        navComments?.visibility = if (hasComments) View.VISIBLE else View.GONE
        if (hasComments) {
            navComments?.setOnClickListener { selectTab(2); hideNavPills() }
        }



        // Restore last selected tab (0=Info, 1=Watch, 2=Comments)
        val savedWindow = media.selected!!.window
        var defaultTab = if (savedWindow == 2 && (!hasComments || rescueMode)) 1 else savedWindow
        if (model.continueMedia == null && media.cameFromContinue) {
            model.continueMedia = PrefManager.getVal(PrefName.ContinueMedia)
            defaultTab = 1
        }
        if (intent.getStringExtra("FRAGMENT_TO_LOAD") != null && hasComments) defaultTab = 2
        selectTab(defaultTab, animate = false)

        // Gesture for double-tap on banner bg
        val gestureDetector = GestureDetector(this, object : GesturesListener() {
            override fun onDoubleClick(event: MotionEvent) {
                snackString(getString(R.string.enable_banner_animations))
            }
            override fun onLongClick(event: MotionEvent) {
                val bannerTitle = getString(R.string.banner, media.userPreferredName)
                ani.sanin.others.ImageViewDialog.newInstance(
                    this@MediaDetailsActivity,
                    bannerTitle,
                    media.banner ?: media.cover
                )
            }
        })
        binding.mediaBg?.setOnTouchListener { _, motionEvent ->
            gestureDetector.onTouchEvent(motionEvent); true
        }
        binding.mediaBanner?.setOnTouchListener { _, motionEvent ->
            gestureDetector.onTouchEvent(motionEvent); true
        }
        binding.mediaBannerNoKen?.setOnTouchListener { _, motionEvent ->
            gestureDetector.onTouchEvent(motionEvent); true
        }

        adult = media.isAdult

        model.getMedia().observe(this) { updatedMedia ->
            if (updatedMedia != null) {
                media = updatedMedia
                if (media.format?.startsWith("LOCAL") == true) {
                    openLinkInBrowser(media.shareLink)
                }
            }
        }

        val live = Refresh.activity.getOrPut(this.hashCode()) { MutableLiveData(true) }
        live.observe(this) {
            if (it) {
                scope.launch(Dispatchers.IO) {
                    model.loadMedia(media)
                    live.postValue(false)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!::binding.isInitialized) return

        val extContainer = findViewById<android.widget.FrameLayout>(R.id.fragmentExtensionsContainer)
        if (extContainer != null) {
            val hasExtFragment = supportFragmentManager.findFragmentById(R.id.fragmentExtensionsContainer) != null
            if (hasExtFragment) {
                supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
                extContainer.visibility = View.GONE
            }
        }
        binding.navPillBg?.live = PrefManager.getVal<Boolean>(PrefName.AnimationsEnabled) && PrefManager.getVal<Boolean>(PrefName.LiveSideRail)
        if (PrefManager.getVal<Boolean>(PrefName.SideRailPersist)) {
            showNavPills()
        }
        binding.mediaTabContent?.post { binding.mediaTabContent?.requestFocus() }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                    if (binding.mediaNavPills?.visibility == View.VISIBLE) {
                        hideNavPills()
                        if (binding.mediaNavPills?.visibility == View.VISIBLE) return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    val focusedId = currentFocus?.id
                    if (focusedId == R.id.navPillInfo || focusedId == R.id.navPillWatch || focusedId == R.id.navPillComments) {
                        if (PrefManager.getVal<Boolean>(PrefName.SideRailPersist)) return false
                        hideNavPills()
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    val focusedId = currentFocus?.id
                    if (focusedId == R.id.navPillInfo || focusedId == R.id.navPillWatch || focusedId == R.id.navPillComments) {
                        return true
                    }
                    if (binding.mediaNavPills?.visibility != View.VISIBLE &&
                        currentFocus?.focusSearch(View.FOCUS_LEFT) == null) {
                        showNavPills()
                        focusNavPillForSelectedTab()
                        return true
                    }
                }
                KeyEvent.KEYCODE_MENU -> {
                    if (binding.mediaNavPills?.visibility != View.VISIBLE) {
                        showNavPills()
                        focusNavPillForSelectedTab()
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun showNavPills() {
        binding.mediaNavPills?.visibility = View.VISIBLE
        binding.navPillBg?.doOnLayout { updateMediaNavIconTints(selected) }
    }

    private fun hideNavPills() {
        if (PrefManager.getVal<Boolean>(PrefName.SideRailPersist)) return
        binding.mediaNavPills?.visibility = View.GONE
        val focusTarget = binding.mediaTabContent
            ?: if (selected == 0) binding.mediaInfoFragmentContainer else binding.mediaRightPanel
        focusTarget?.requestFocus()
    }

    private fun updateMediaNavIconTints(selectedIdx: Int) {
        val customColor = NavPillCustomizer.getIconColor()
        val pills = listOfNotNull(binding.navPillInfo, binding.navPillWatch, binding.navPillComments)
        pills.forEachIndexed { i, pill ->
            pill.imageTintList = ColorStateList.valueOf(customColor)
            pill.alpha = 1f
        }
    }

    private fun focusNavPillForSelectedTab() {
        val targetId = when (selected) {
            0 -> R.id.navPillInfo
            1 -> R.id.navPillWatch
            2 -> R.id.navPillComments
            else -> R.id.navPillInfo
        }
        val target = binding.root.findViewById<View>(targetId)
        if (target?.visibility == View.VISIBLE) {
            target.requestFocus()
        } else {
            binding.navPillInfo?.requestFocus()
        }
    }

    companion object {
        var mediaSingleton: Media? = null
    }

    class PopImageButton(
        private val scope: CoroutineScope,
        private val image: ImageView,
        private val d1: Int,
        private val d2: Int,
        private val c1: Int,
        private val c2: Int,
        var clicked: Boolean,
        needsInitialClick: Boolean = false,
        callback: suspend (Boolean) -> (Unit)
    ) {
        private var disabled = false
        private val context = image.context
        private var pressable = true

        init {
            enabled(true)
            if (needsInitialClick) {
                scope.launch {
                    clicked()
                }
            }
            image.setOnClickListener {
                if (pressable && !disabled) {
                    pressable = false
                    clicked = !clicked
                    scope.launch {
                        launch(Dispatchers.IO) {
                            callback.invoke(clicked)
                        }
                        clicked()
                        pressable = true
                    }
                }
            }
        }

        suspend fun clicked() {
            if (PrefManager.getVal<Boolean>(PrefName.AnimationsEnabled) && PrefManager.getVal<Boolean>(PrefName.LikeButtonAnimations)) {
                ObjectAnimator.ofFloat(image, "scaleX", 1f, 0f).setDuration(69).start()
                ObjectAnimator.ofFloat(image, "scaleY", 1f, 0f).setDuration(100).start()
                delay(100.milliseconds)
            }

            if (clicked) {
                if (PrefManager.getVal<Boolean>(PrefName.AnimationsEnabled) && PrefManager.getVal<Boolean>(PrefName.LikeButtonAnimations)) {
                    ObjectAnimator.ofArgb(
                        image,
                        "ColorFilter",
                        ContextCompat.getColor(context, c1),
                        ContextCompat.getColor(context, c2)
                    ).setDuration(120).start()
                } else {
                    image.colorFilter = android.graphics.PorterDuffColorFilter(
                        ContextCompat.getColor(context, c2),
                        android.graphics.PorterDuff.Mode.SRC_IN
                    )
                }
                image.setImageDrawable(AppCompatResources.getDrawable(context, d1))
            } else image.setImageDrawable(AppCompatResources.getDrawable(context, d2))
            if (PrefManager.getVal<Boolean>(PrefName.AnimationsEnabled) && PrefManager.getVal<Boolean>(PrefName.LikeButtonAnimations)) {
                ObjectAnimator.ofFloat(image, "scaleX", 0f, 1.5f).setDuration(120).start()
                ObjectAnimator.ofFloat(image, "scaleY", 0f, 1.5f).setDuration(100).start()
                delay(120.milliseconds)
                ObjectAnimator.ofFloat(image, "scaleX", 1.5f, 1f).setDuration(100).start()
                ObjectAnimator.ofFloat(image, "scaleY", 1.5f, 1f).setDuration(100).start()
                delay(200.milliseconds)
                if (clicked) {
                    ObjectAnimator.ofArgb(
                        image,
                        "ColorFilter",
                        ContextCompat.getColor(context, c2),
                        ContextCompat.getColor(context, c1)
                    ).setDuration(200).start()
                }
            } else {
                if (clicked) {
                    image.colorFilter = android.graphics.PorterDuffColorFilter(
                        ContextCompat.getColor(context, c1),
                        android.graphics.PorterDuff.Mode.SRC_IN
                    )
                }
            }
        }

        fun enabled(enabled: Boolean) {
            disabled = !enabled
            image.alpha = if (disabled) 0.33f else 1f
        }
    }
}
