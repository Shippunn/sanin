package ani.sanin

import android.animation.ObjectAnimator
import android.view.animation.DecelerateInterpolator
import android.annotation.SuppressLint
import kotlin.math.cos
import kotlin.math.exp
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.core.view.doOnAttach
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import androidx.drawerlayout.widget.DrawerLayout
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.RecyclerView
import ani.sanin.connections.LogoApi
import ani.sanin.media.anime.ExoplayerView
import ani.sanin.connections.anilist.Anilist
import ani.sanin.connections.anilist.AnilistHomeViewModel
import ani.sanin.connections.mal.MAL
import ani.sanin.util.FocusEffectUtil
import ani.sanin.util.NavPillCustomizer
import android.widget.LinearLayout
import ani.sanin.util.GlassComponent
import ani.sanin.util.GlassEffectManager
import ani.sanin.databinding.ActivityMainBinding
import ani.sanin.databinding.DialogUserAgentBinding
import ani.sanin.databinding.SplashScreenBinding
import ani.sanin.home.AnimeFragment
import ani.sanin.home.DiscoveryFragment
import ani.sanin.home.HomeFragment
import ani.sanin.home.LibraryFragment
import ani.sanin.home.NoInternet
import ani.sanin.media.MediaDetailsActivity
import ani.sanin.notifications.TaskScheduler
import ani.sanin.others.calc.CalcActivity
import ani.sanin.profile.ProfileActivity
import ani.sanin.profile.activity.FeedActivity
import ani.sanin.profile.notification.NotificationActivity
import ani.sanin.settings.AddRepositoryBottomSheet
import ani.sanin.settings.ExtensionsActivity
import ani.sanin.settings.FirstTimeProviderDialog
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefManager.asLiveBool
import ani.sanin.settings.saving.PrefName
import ani.sanin.settings.saving.SharedPreferenceBooleanLiveData
import ani.sanin.settings.saving.internal.PreferenceKeystore
import ani.sanin.settings.saving.internal.PreferencePackager
import ani.sanin.themes.ThemeManager
import ani.sanin.util.TvKeyboardUtil
import ani.sanin.ui.components.NavigationPillsViewModel
import ani.sanin.util.AudioHelper
import ani.sanin.util.Logger
import ani.sanin.util.customAlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import eu.kanade.domain.source.service.SourcePreferences
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.Serializable


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var incognitoLiveData: SharedPreferenceBooleanLiveData
    private val scope = lifecycleScope
    private var load = false
    lateinit var navPillsViewModel: NavigationPillsViewModel
    private var currentFragmentTag: String? = null

    private val tabFragments = mapOf(
        0 to "home",
        1 to "anime",
        2 to "discovery",
        3 to "library"
    )

    private fun getFragmentForTab(index: Int): Fragment = when (index) {
        0 -> HomeFragment()
        1 -> AnimeFragment()
        2 -> DiscoveryFragment()
        3 -> LibraryFragment()
        else -> HomeFragment()
    }

    private fun switchTab(index: Int) {
        if (supportFragmentManager.isStateSaved) return
        val tag = tabFragments[index] ?: "home"
        if (tag == currentFragmentTag) return
        currentFragmentTag = tag
        val fragment = getFragmentForTab(index)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment, tag)
            .commit()
    }


    @kotlin.OptIn(DelicateCoroutinesApi::class)
    @SuppressLint("InternalInsetResource", "DiscouragedApi")
    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager(this).applyTheme()
        LogoApi.init(this)

        super.onCreate(savedInstanceState)

        window.setBackgroundDrawableResource(R.drawable.splash_logo)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!CalcActivity.hasPermission) {
            val pin: String = PrefManager.getVal(PrefName.AppPassword)
            if (pin.isNotEmpty()) {
                ContextCompat.startActivity(
                    this@MainActivity,
                    Intent(this@MainActivity, CalcActivity::class.java)
                        .putExtra("code", pin)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
                    null
                )
                finish()
                return
            }
        }
        TaskScheduler.scheduleSingleWork(this)

        if (Intent.ACTION_VIEW == intent.action) {
            handleViewIntent(intent)
        }

        val offset = try {
            val statusBarHeightId = resources.getIdentifier("status_bar_height", "dimen", "android")
            resources.getDimensionPixelSize(statusBarHeightId)
        } catch (e: Exception) {
            statusBarHeight
        }
        val layoutParams = binding.incognito.layoutParams as ViewGroup.MarginLayoutParams
        layoutParams.topMargin = 11 * offset / 12
        binding.incognito.layoutParams = layoutParams

        val rescueLayoutParams = binding.rescueModeIcon.layoutParams as ViewGroup.MarginLayoutParams
        rescueLayoutParams.topMargin = 11 * offset / 12
        binding.rescueModeIcon.layoutParams = rescueLayoutParams

      
        fun syncRescueIconMargin(incognitoOn: Boolean) {
            val p = binding.rescueModeIcon.layoutParams as ViewGroup.MarginLayoutParams
            p.marginStart = if (incognitoOn) {
                (54f * resources.displayMetrics.density).toInt()
            } else {
                (16f * resources.displayMetrics.density).toInt()
            }
            binding.rescueModeIcon.layoutParams = p
        }
        syncRescueIconMargin(PrefManager.getVal(PrefName.Incognito))

        incognitoLiveData = PrefManager.getLiveVal(
            PrefName.Incognito,
            false
        ).asLiveBool()
        incognitoLiveData.observe(this) {
            if (it) {
                if (PrefManager.getVal<Boolean>(PrefName.AnimationsEnabled) && PrefManager.getVal<Boolean>(PrefName.IncognitoBannerAnimations)) {
                    val slideDownAnim = ObjectAnimator.ofFloat(
                        binding.incognito,
                        View.TRANSLATION_Y,
                        -(200f + statusBarHeight),
                        0f
                    )
                    slideDownAnim.duration = 200
                    slideDownAnim.start()
                } else {
                    binding.incognito.translationY = 0f
                }
                binding.incognito.visibility = View.VISIBLE
                if (PrefManager.getVal<Boolean>(PrefName.RescueMode)) syncRescueIconMargin(true)
            } else {
                if (PrefManager.getVal<Boolean>(PrefName.AnimationsEnabled) && PrefManager.getVal<Boolean>(PrefName.IncognitoBannerAnimations)) {
                    val slideUpAnim = ObjectAnimator.ofFloat(
                        binding.incognito,
                        View.TRANSLATION_Y,
                        0f,
                        -(200f + statusBarHeight)
                    )
                    slideUpAnim.duration = 200
                    slideUpAnim.start()
                    Handler(Looper.getMainLooper()).postDelayed({
                        binding.incognito.visibility = View.GONE
                        if (PrefManager.getVal<Boolean>(PrefName.RescueMode)) syncRescueIconMargin(false)
                    }, 200)
                } else {
                    binding.incognito.visibility = View.GONE
                    binding.incognito.translationY = -(200f + statusBarHeight)
                    if (PrefManager.getVal<Boolean>(PrefName.RescueMode)) syncRescueIconMargin(false)
                }
            }
        }

        val rescueModeLiveData = PrefManager.getLiveVal(PrefName.RescueMode, false).asLiveBool()
        rescueModeLiveData.observe(this) {
            if (it) {
                syncRescueIconMargin(PrefManager.getVal(PrefName.Incognito))
                if (PrefManager.getVal<Boolean>(PrefName.AnimationsEnabled) && PrefManager.getVal<Boolean>(PrefName.MiscUiAnimations)) {
                    val slideDownAnim = ObjectAnimator.ofFloat(
                        binding.rescueModeIcon,
                        View.TRANSLATION_Y,
                        -(200f + statusBarHeight),
                        0f
                    )
                    slideDownAnim.duration = 200
                    slideDownAnim.start()
                } else {
                    binding.rescueModeIcon.translationY = 0f
                }
                binding.rescueModeIcon.visibility = View.VISIBLE
            } else {
                if (PrefManager.getVal<Boolean>(PrefName.AnimationsEnabled) && PrefManager.getVal<Boolean>(PrefName.MiscUiAnimations)) {
                    val slideUpAnim = ObjectAnimator.ofFloat(
                        binding.rescueModeIcon,
                        View.TRANSLATION_Y,
                        0f,
                        -(200f + statusBarHeight)
                    )
                    slideUpAnim.duration = 200
                    slideUpAnim.start()
                    //wait for animation to finish
                    Handler(Looper.getMainLooper()).postDelayed(
                        { binding.rescueModeIcon.visibility = View.GONE },
                        200
                    )
                } else {
                    binding.rescueModeIcon.visibility = View.GONE
                    binding.rescueModeIcon.translationY = -(200f + statusBarHeight)
                }
            }
        }
        binding.rescueModeIcon.setOnClickListener {
            PrefManager.setVal(PrefName.RescueMode, false)
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            startActivity(intent)
            overridePendingTransition(0, 0)
            finish()
            overridePendingTransition(0, 0)
        }
        incognitoNotification(this)

        var doubleBackToExitPressedOnce = false
        onBackPressedDispatcher.addCallback(this) {
            if (binding.mainDrawer.isDrawerOpen(Gravity.END)) {
                binding.mainDrawer.closeDrawer(Gravity.END)
            } else if (doubleBackToExitPressedOnce) {
                finish()
            } else {
                doubleBackToExitPressedOnce = true
                snackString(this@MainActivity.getString(R.string.back_to_exit)).apply {
                    this?.addCallback(object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                        override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                            super.onDismissed(transientBottomBar, event)
                            doubleBackToExitPressedOnce = false
                        }
                    })
                }
            }
        }

        binding.root.isMotionEventSplittingEnabled = false

        val splash = SplashScreenBinding.inflate(layoutInflater)
        binding.root.addView(splash.root)
        val splashStart = System.currentTimeMillis()
        val initComplete = CompletableDeferred<Unit>()
        lifecycleScope.launch {
            initComplete.await()
            val elapsed = System.currentTimeMillis() - splashStart
            if (elapsed < 2700L) delay(2700L - elapsed)
                window.setBackgroundDrawableResource(R.color.bg_black)
                if (PrefManager.getVal<Boolean>(PrefName.AnimationsEnabled) && PrefManager.getVal<Boolean>(PrefName.SplashAnimations)) {
                    ObjectAnimator.ofFloat(splash.root, View.ALPHA, 1f, 0f).apply {
                        duration = 400L
                        doOnEnd { binding.root.removeView(splash.root) }
                        start()
                    }
                } else {
                    binding.root.removeView(splash.root)
                }
        }

        binding.root.doOnAttach {
            initActivity(this)
            val preferences: SourcePreferences = Injekt.get()
                    if (preferences.animeExtensionUpdatesCount()
                            .get() > 0) {
                snackString(R.string.extension_updates_available)
                    ?.setDuration(Snackbar.LENGTH_SHORT)
                    ?.setAction(R.string.review) {
                        startActivity(Intent(this, ExtensionsActivity::class.java))
                    }
            }
            window.navigationBarColor = ContextCompat.getColor(this, android.R.color.transparent)
            binding.mainProgressBar.visibility = View.GONE

            // Setup home side rail (replaces compose navPills)
            navPillsViewModel = ViewModelProvider(this)[NavigationPillsViewModel::class.java]
            setupHomeNavRail()

            // Setup avatar and right rail drawer
            binding.mainAvatarContainer.visibility = View.VISIBLE
            Anilist.getSavedToken()
            loadAvatar()
            binding.mainUserAvatarContainer.setOnClickListener {
                if (!binding.mainDrawer.isDrawerOpen(Gravity.END)) {
                    populateRightRail()
                    binding.mainDrawer.openDrawer(Gravity.END)
                } else {
                    binding.mainDrawer.closeDrawer(Gravity.END)
                }
            }
            binding.mainCalendarContainer.setOnClickListener {
                ContextCompat.startActivity(
                    this,
                    Intent(this, ani.sanin.media.CalendarActivity::class.java),
                    null
                )
            }
            // Focus: each icon gets its own border
            FocusEffectUtil.applyFocusListener(binding.mainCalendarContainer)
            FocusEffectUtil.applyFocusListener(binding.mainUserAvatarContainer)
            // Focus chain: calendar ↔ avatar
            binding.mainCalendarContainer.nextFocusLeftId = R.id.mainUserAvatarContainer
            binding.mainCalendarContainer.nextFocusRightId = R.id.mainUserAvatarContainer
            binding.mainUserAvatarContainer.nextFocusLeftId = R.id.mainCalendarContainer
            binding.mainUserAvatarContainer.nextFocusRightId = R.id.mainCalendarContainer

            // Observe tab changes
            lifecycleScope.launch {
                navPillsViewModel.currentTab.collect { tabIndex ->
                    switchTab(tabIndex)
                }
            }

            // Load initial tab
            var startTab = PrefManager.getVal<Int>(PrefName.DefaultStartUpTab)
            if (startTab > 1) {
                startTab = 0
                PrefManager.setVal(PrefName.DefaultStartUpTab, 0)
            }
            navPillsViewModel.setTab(startTab)
            switchTab(startTab)

            // Setup avatar and right rail drawer
            loadAvatar()
            setupRightRail()
            binding.homeNavRail.post { updateSideRail() }

            initComplete.complete(Unit)

            if (PrefManager.getVal<Boolean>(PrefName.SmartTrim)) {
                lifecycleScope.launch(Dispatchers.IO) {
                    trimCachePeriodically()
                }
            }
        }

        if (!PrefManager.getVal<Boolean>(PrefName.FirstTimeProviderShown)) {
            val enabled = PrefManager.getVal<Set<String>>(PrefName.EnabledProviders)
            if (enabled.isEmpty()) {
                FirstTimeProviderDialog().show(supportFragmentManager, "FirstTimeProvider")
            }
            PrefManager.setVal(PrefName.FirstTimeProviderShown, true)
        }

        var launched = false
        intent.extras?.let { extras ->
            val fragmentToLoad = extras.getString("FRAGMENT_TO_LOAD")
            val mediaId = extras.getInt("mediaId", -1)
            val commentId = extras.getInt("commentId", -1)
            val activityId = extras.getInt("activityId", -1)

            if (fragmentToLoad != null && mediaId != -1 && commentId != -1) {
                val detailIntent = Intent(this, MediaDetailsActivity::class.java).apply {
                    putExtra("FRAGMENT_TO_LOAD", fragmentToLoad)
                    putExtra("mediaId", mediaId)
                    putExtra("commentId", commentId)
                }
                launched = true
                startActivity(detailIntent)
            } else if (fragmentToLoad == "FEED" && activityId != -1) {
                if (!PrefManager.getVal<Boolean>(PrefName.RescueMode)) {
                    val feedIntent = Intent(this, FeedActivity::class.java).apply {
                        putExtra("FRAGMENT_TO_LOAD", "NOTIFICATIONS")
                        putExtra("activityId", activityId)
                    }
                    launched = true
                    startActivity(feedIntent)
                }
            } else if (fragmentToLoad == "NOTIFICATIONS" && activityId != -1) {
                Logger.log("MainActivity, onCreate: $activityId")
                if (!PrefManager.getVal<Boolean>(PrefName.RescueMode)) {
                    val notificationIntent = Intent(this, NotificationActivity::class.java).apply {
                        putExtra("activityId", activityId)
                    }
                    launched = true
                    startActivity(notificationIntent)
                }
            }
        }
        val offlineMode: Boolean = PrefManager.getVal(PrefName.OfflineMode)
        if (!isOnline(this)) {
            snackString(this@MainActivity.getString(R.string.no_internet_connection))
            startActivity(Intent(this, NoInternet::class.java))
        } else {
            if (offlineMode) {
                snackString(this@MainActivity.getString(R.string.no_internet_connection))
                startActivity(Intent(this, NoInternet::class.java))
            } else {
                val model: AnilistHomeViewModel by viewModels()

                //Load Data
                if (!load && !launched) {
                    scope.launch(Dispatchers.IO) {
                        model.loadMain(this@MainActivity)
                        val id = intent.extras?.getInt("mediaId", 0)
                        val isMAL = intent.extras?.getBoolean("mal") ?: false
                        val cont = intent.extras?.getBoolean("continue") ?: false
                        val mediaType = intent.extras?.getString("mediaType")
                        if (id != null && id != 0) {
                            val media = withContext(Dispatchers.IO) {
                                Anilist.query.getMedia(id, isMAL, mediaType)
                            }
                            if (media != null) {
                                media.cameFromContinue = cont
                                startActivity(
                                    Intent(this@MainActivity, MediaDetailsActivity::class.java)
                                        .putExtra("media", media as Serializable)
                                )
                            } else {
                                snackString(this@MainActivity.getString(R.string.anilist_not_found))
                            }
                        }
                        val username = intent.extras?.getString("username")
                        if (username != null) {
                            val nameInt = username.toIntOrNull()
                            if (nameInt != null) {
                                startActivity(
                                    Intent(this@MainActivity, ProfileActivity::class.java)
                                        .putExtra("userId", nameInt)
                                )
                            } else {
                                startActivity(
                                    Intent(this@MainActivity, ProfileActivity::class.java)
                                        .putExtra("username", username)
                                )
                            }
                        }
                    }
                    load = true
                }

            }
        }
        if (PrefManager.getVal(PrefName.OC)) {
            AudioHelper.run(this, R.raw.audio)
            PrefManager.setVal(PrefName.OC, false)
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                    val id = currentFocus?.id
                    if (id == R.id.homeNavHome || id == R.id.homeNavAnime || id == R.id.homeNavDiscovery || id == R.id.homeNavLibrary) {
                        if (PrefManager.getVal<Boolean>(PrefName.SideRailPersist)) {
                            setHomeNavPillsFocusable(false)
                            val tag = currentFragmentTag
                            if (tag != null) {
                                supportFragmentManager.findFragmentByTag(tag)?.view?.let { it.requestFocus() }
                            }
                            return true
                        }
                        hideHomeNavRail()
                        if (binding.homeNavRail.visibility == View.VISIBLE) return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    val id = currentFocus?.id
                    if (id == R.id.homeNavHome || id == R.id.homeNavAnime || id == R.id.homeNavDiscovery || id == R.id.homeNavLibrary) {
                        if (PrefManager.getVal<Boolean>(PrefName.SideRailPersist)) {
                            setHomeNavPillsFocusable(false)
                            return false
                        }
                        hideHomeNavRail()
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    val id = currentFocus?.id
                    if (id == R.id.homeNavHome || id == R.id.homeNavAnime || id == R.id.homeNavDiscovery || id == R.id.homeNavLibrary) {
                        return true
                    }
                    if (binding.homeNavRail.visibility == View.VISIBLE && PrefManager.getVal<Boolean>(PrefName.SideRailPersist)) {
                        val focus = currentFocus
                        var atLeftEdge = false
                        if (focus != null) {
                            val railWidth = (60f * resources.displayMetrics.density).toInt()
                            atLeftEdge = focus.left <= railWidth || focus.focusSearch(View.FOCUS_LEFT) == null
                        }
                        if (atLeftEdge) {
                            setHomeNavPillsFocusable(true)
                            val tab = navPillsViewModel.currentTab.value
                            val targetId = when (tab) { 0 -> R.id.homeNavHome; 1 -> R.id.homeNavAnime; 2 -> R.id.homeNavDiscovery; 3 -> R.id.homeNavLibrary; else -> R.id.homeNavHome }
                            binding.root.findViewById<View>(targetId)?.requestFocus()
                            return true
                        }
                    }
                    if (binding.homeNavRail.visibility != View.VISIBLE) {
                        val focus = currentFocus
                        if (focus != null) {
                            var p = focus.parent
                            var inHorizontalRv = false
                            while (p != null) {
                                if (p is RecyclerView) {
                                    val lm = p.layoutManager
                                    if (lm != null && lm.canScrollHorizontally()) {
                                        val holder = p.findContainingViewHolder(focus)
                                        if (holder != null && holder.bindingAdapterPosition > 0) {
                                            inHorizontalRv = true
                                        } else if (p.canScrollHorizontally(-1)) {
                                            inHorizontalRv = true
                                        }
                                    }
                                    break
                                }
                                p = (p as? View)?.parent
                            }
                            if (!inHorizontalRv) {
                                val railWidth = (60f * resources.displayMetrics.density).toInt()
                                if (focus.left <= railWidth || focus.focusSearch(View.FOCUS_LEFT) == null) {
                                    showHomeNavRail()
                                    return true
                                }
                            }
                        }
                    }
                }
                KeyEvent.KEYCODE_MENU -> {
                    if (binding.homeNavRail.visibility == View.VISIBLE && PrefManager.getVal<Boolean>(PrefName.SideRailPersist)) {
                        setHomeNavPillsFocusable(true)
                        val tab = navPillsViewModel.currentTab.value
                        val targetId = when (tab) { 0 -> R.id.homeNavHome; 1 -> R.id.homeNavAnime; 2 -> R.id.homeNavDiscovery; 3 -> R.id.homeNavLibrary; else -> R.id.homeNavHome }
                        binding.root.findViewById<View>(targetId)?.requestFocus()
                        return true
                    }
                    if (binding.homeNavRail.visibility != View.VISIBLE) {
                        showHomeNavRail()
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onRestart() {
        super.onRestart()
        window.navigationBarColor = ContextCompat.getColor(this, android.R.color.transparent)
    }

    override fun onResume() {
        super.onResume()
        loadAvatar()
        binding.homeNavRailBg.live = PrefManager.getVal<Boolean>(PrefName.AnimationsEnabled) && PrefManager.getVal<Boolean>(PrefName.LiveSideRail)
        binding.homeNavRailBg.setGlassEnabled(
            GlassEffectManager.isComponentEnabled(GlassComponent.NavPills)
        )
        updateSideRailGlass()
        binding.homeNavRail.post { updateSideRail() }
        updateNavPillFocusChains()
    }

    private fun updateSideRail() {
        val autoOrientation = PrefManager.getVal<Boolean>(PrefName.SideRailAutoOrientation)
        val show: Boolean
        if (autoOrientation) {
            val isPortrait = resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
            PrefManager.setVal(PrefName.SideRailPersist, isPortrait)
            show = isPortrait
        } else {
            show = PrefManager.getVal<Boolean>(PrefName.SideRailPersist)
        }
        if (show && ::navPillsViewModel.isInitialized) {
            binding.homeNavRail.visibility = View.VISIBLE
            binding.homeNavRail.translationX = 0f
            binding.homeNavRail.scaleY = 1f
            binding.homeNavRail.alpha = 1f
            updateHomeNavIconTints()
            setHomeNavPillsFocusable(false)
        } else {
            binding.homeNavRail.visibility = View.GONE
        }
    }

    private fun updateSideRailGlass() {
        if (!GlassEffectManager.isComponentEnabled(GlassComponent.SideRail)) return
        findViewById<View>(R.id.rightRailContainer)?.let { container ->
            GlassEffectManager.applyGlass(container, GlassComponent.SideRail, 0f)
        }
    }

    private fun handleViewIntent(intent: Intent) {
        val uri: Uri? = intent.data
        try {
            if (uri == null) {
                throw Exception("Uri is null")
            }
            if (uri.scheme == "aniyomi" && uri.host == "add-repo") {
                val url = uri.getQueryParameter("url") ?: throw Exception("No url for repo import")
                val (prefName, name) = when (uri.scheme) {
                    "aniyomi" -> PrefName.AnimeExtensionRepos to "Anime"
                    else -> throw Exception("Invalid scheme")
                }
                val savedRepos: Set<String> = PrefManager.getVal(prefName)
                val newRepos = savedRepos.toMutableSet()
                AddRepositoryBottomSheet.addRepoWarning(this) {
                    newRepos.add(url)
                    PrefManager.setVal(prefName, newRepos)
                    toast("$name Extension Repo added")
                }
                return
            }
            if (intent.type == null) return
            val jsonString =
                contentResolver.openInputStream(uri)?.readBytes()
                    ?: throw Exception("Error reading file")
            val name =
                DocumentFile.fromSingleUri(this, uri)?.name ?: "settings"
            //.sani is encrypted, .ani is not
            if (name.endsWith(".sani")) {
                passwordAlertDialog { password ->
                    if (password != null) {
                        val salt = jsonString.copyOfRange(0, 16)
                        val encrypted = jsonString.copyOfRange(16, jsonString.size)
                        val decryptedJson = try {
                            PreferenceKeystore.decryptWithPassword(
                                password,
                                encrypted,
                                salt
                            )
                        } catch (e: Exception) {
                            toast("Incorrect password")
                            return@passwordAlertDialog
                        }
                        if (PreferencePackager.unpack(decryptedJson)) {
                            val newIntent = Intent(this, this.javaClass)
                            this.finish()
                            startActivity(newIntent)
                        }
                    } else {
                        toast("Password cannot be empty")
                    }
                }
            } else if (name.endsWith(".ani")) {
                val decryptedJson = jsonString.toString(Charsets.UTF_8)
                if (PreferencePackager.unpack(decryptedJson)) {
                    val newIntent = Intent(this, this.javaClass)
                    this.finish()
                    startActivity(newIntent)
                }
            } else {
                toast("Invalid file type")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            toast("Error importing settings")
        }
    }

    private fun passwordAlertDialog(callback: (CharArray?) -> Unit) {
        val password = CharArray(16).apply { fill('0') }

        // Inflate the dialog layout
        val dialogView = DialogUserAgentBinding.inflate(layoutInflater).apply {
            TvKeyboardUtil.setupTvInput(userAgentTextBox)
            userAgentTextBox.hint = "Password"
            subtitle.visibility = View.VISIBLE
            subtitle.text = getString(R.string.enter_password_to_decrypt_file)
        }
        customAlertDialog().apply {
            setTitle("Enter Password")
            setCustomView(dialogView.root)
            setPosButton(R.string.yes) {
                val editText = dialogView.userAgentTextBox
                if (editText.text?.isNotBlank() == true) {
                    editText.text?.toString()?.trim()?.toCharArray(password)
                    callback(password)
                } else {
                    toast("Password cannot be empty")
                }
            }
            setNegButton(R.string.cancel) {
                password.fill('0')
                callback(null)
            }
            setOnShowListener {
                dialogView.userAgentTextBox.requestFocus()
                TvKeyboardUtil.showKeyboardDelayed(dialogView.userAgentTextBox)
            }
            show()
        }
    }

    private fun loadAvatar() {
        binding.mainUserAvatar.loadImage(Anilist.avatar)
        val showRedDot = PrefManager.getVal<Boolean>(PrefName.ShowNotificationRedDot)
        if (showRedDot == true) {
            binding.mainNotificationCount.isVisible = Anilist.unreadNotificationCount > 0
            binding.mainNotificationCount.text = Anilist.unreadNotificationCount.toString()
        } else {
            binding.mainNotificationCount.isVisible = false
        }
    }

    private fun setupHomeNavRail() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val cornerPx = NavPillCustomizer.getCornerRadiusDp() * resources.displayMetrics.density
            binding.homeNavRail.outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, cornerPx)
                }
            }
            binding.homeNavRail.elevation = 10f
            binding.homeNavRail.clipToOutline = true
        }

        binding.homeNavRailBg.live = PrefManager.getVal<Boolean>(PrefName.AnimationsEnabled) && PrefManager.getVal<Boolean>(PrefName.LiveSideRail)
        binding.homeNavRailBg.setGlassEnabled(
            GlassEffectManager.isComponentEnabled(GlassComponent.NavPills)
        )

        binding.homeNavRailBg.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateHomeNavIconTints()
        }

        val pills = listOf(binding.homeNavHome, binding.homeNavAnime, binding.homeNavDiscovery, binding.homeNavLibrary)
        pills.forEachIndexed { index, pill ->
            pill.setOnClickListener {
                navPillsViewModel.setTab(index)
                hideHomeNavRail()
            }
            FocusEffectUtil.applyFocusListener(pill)
        }

        updateNavPillFocusChains()
        NavPillCustomizer.applyToPillList(binding.homeNavRail.getChildAt(1) as LinearLayout)
    }

    private fun updateNavPillFocusChains() {
        binding.mainCalendarContainer.nextFocusLeftId = View.NO_ID
        binding.mainCalendarContainer.nextFocusDownId = R.id.homeBannerCarousel
        binding.mainUserAvatarContainer.nextFocusDownId = R.id.homeBannerCarousel
        binding.root.findViewById<View>(R.id.homeBannerCarousel)?.nextFocusUpId = R.id.mainUserAvatarContainer
        binding.root.findViewById<View>(R.id.homeNavigatingBannerContainer)?.nextFocusUpId = R.id.mainUserAvatarContainer
    }

    private fun updateHomeNavIconTints() {
        val bg = binding.homeNavRailBg
        if (bg.height <= 0) return
        val pills = listOf(binding.homeNavHome, binding.homeNavAnime, binding.homeNavDiscovery, binding.homeNavLibrary)
        val customColor = NavPillCustomizer.getIconColor()
        pills.forEachIndexed { i, pill ->
            pill.imageTintList = android.content.res.ColorStateList.valueOf(customColor)
            pill.alpha = 1f
        }
    }

    private fun setHomeNavPillsFocusable(focusable: Boolean) {
        listOf(binding.homeNavHome, binding.homeNavAnime, binding.homeNavDiscovery, binding.homeNavLibrary).forEach {
            it.isFocusable = focusable
            it.isFocusableInTouchMode = false
        }
    }

    private fun showHomeNavRail() {
        if (PrefManager.getVal<Boolean>(PrefName.AnimationsEnabled) && PrefManager.getVal<Boolean>(PrefName.NavRailAnimations)) {
            binding.homeNavRail.apply {
                visibility = View.VISIBLE
                pivotY = 0f
                translationX = -60f * resources.displayMetrics.density
                scaleY = 0.3f
                alpha = 0f
            }
            binding.homeNavRail.post {
                ObjectAnimator.ofFloat(binding.homeNavRail, View.SCALE_Y, 1f).apply {
                    interpolator = SpringInterpolator()
                    duration = 700
                }.start()
                binding.homeNavRail.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setInterpolator(DecelerateInterpolator())
                    .setDuration(500)
                    .start()
                updateHomeNavIconTints()
            }
        } else {
            binding.homeNavRail.visibility = View.VISIBLE
            binding.homeNavRail.translationX = 0f
            binding.homeNavRail.scaleY = 1f
            binding.homeNavRail.alpha = 1f
            updateHomeNavIconTints()
        }
        setHomeNavPillsFocusable(true)
        val tab = navPillsViewModel.currentTab.value
        val id = when (tab) {
            0 -> R.id.homeNavHome
            1 -> R.id.homeNavAnime
            2 -> R.id.homeNavDiscovery
            3 -> R.id.homeNavLibrary
            else -> R.id.homeNavHome
        }
        binding.root.findViewById<View>(id)?.requestFocus()
    }

    private fun hideHomeNavRail() {
        if (PrefManager.getVal<Boolean>(PrefName.SideRailPersist)) return
        binding.homeNavRail.visibility = View.GONE
        val lp = binding.fragmentContainer.layoutParams as ViewGroup.MarginLayoutParams
        if (lp.rightMargin != 0) {
            lp.rightMargin = 0
            binding.fragmentContainer.layoutParams = lp
        }
        val tag = currentFragmentTag
        if (tag != null) {
            supportFragmentManager.findFragmentByTag(tag)?.view?.let {
                it.requestFocus()
            }
        }
    }

    private fun setupRightRail() {
        val drawerItems = mapOf(
            R.id.rightRailNotifications to {
                startActivity(Intent(this, NotificationActivity::class.java))
            },
            R.id.rightRailExtensions to {
                startActivity(Intent(this, ExtensionsActivity::class.java))
            },
            R.id.rightRailAnimeList to {
                ContextCompat.startActivity(this, Intent(this, ani.sanin.media.user.ListActivity::class.java)
                    .putExtra("anime", true)
                    .putExtra("userId", Anilist.userid ?: 0)
                    .putExtra("username", Anilist.username), null)
            },
            R.id.rightRailSettings to {
                startActivity(Intent(this, ani.sanin.settings.SettingsActivity::class.java))
            },
            R.id.rightRailProviders to {
                startActivity(Intent(this, ani.sanin.settings.ProvidersActivity::class.java))
            },
            R.id.rightRailSync to {
                lifecycleScope.launch(Dispatchers.IO) {
                    ani.sanin.connections.syncPendingProgressUpdates()
                    ani.sanin.connections.syncPendingDeletions()
                }
                snackString("Sync triggered")
            },
            R.id.rightRailClearCache to {
                lifecycleScope.launch(Dispatchers.Main) {
                    try {
                        Glide.get(this@MainActivity).clearMemory()
                        LogoApi.clearCache()
                        withContext(Dispatchers.IO) {
                            cacheDir.deleteRecursively()
                            externalCacheDir?.deleteRecursively()
                            Glide.get(this@MainActivity).clearDiskCache()
                            ExoplayerView.clearAllCaches()
                        }
                        snackString("Cache cleared")
                    } catch (e: Exception) {
                        snackString("Failed to clear cache: ${e.message}")
                    }
                }
            },
            R.id.rightRailLogout to {
                customAlertDialog().apply {
                    setTitle("Log Out")
                    setMessage("Are you sure you want to log out?")
                    setPosButton("Yes") {
                        Anilist.removeSavedToken()
                        MAL.removeSavedToken()
                        startActivity(Intent(this@MainActivity, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                        finish()
                    }
                    setNegButton("No", null)
                    show()
                }
            }
        )

        for ((id, action) in drawerItems) {
            val view = findViewById<View>(id)
            view.setOnClickListener {
                binding.mainDrawer.closeDrawer(Gravity.END)
                action()
            }
            view.setOnKeyListener { v, keyCode, event ->
                if (event.action == KeyEvent.ACTION_UP &&
                    (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)
                ) {
                    binding.mainDrawer.closeDrawer(Gravity.END)
                    action()
                    true
                } else false
            }
            FocusEffectUtil.applyFocusListener(view)
        }

        findViewById<View>(R.id.rightRailAvatarCard).setOnClickListener {
            binding.mainDrawer.closeDrawer(Gravity.END)
            ContextCompat.startActivity(this, Intent(this, ProfileActivity::class.java)
                .putExtra("userId", Anilist.userid), null)
        }
        findViewById<View>(R.id.rightRailAvatarCard).setOnKeyListener { v, keyCode, event ->
            if (event.action == KeyEvent.ACTION_UP &&
                (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)
            ) {
                binding.mainDrawer.closeDrawer(Gravity.END)
                ContextCompat.startActivity(this, Intent(this, ProfileActivity::class.java)
                    .putExtra("userId", Anilist.userid), null)
                true
            } else false
        }
        FocusEffectUtil.applyFocusListener(findViewById(R.id.rightRailAvatarCard))

        binding.mainDrawer.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {}
            override fun onDrawerStateChanged(newState: Int) {}
            override fun onDrawerOpened(drawerView: View) {
                findViewById<View>(R.id.rightRailNotifications).requestFocus()
                if (GlassEffectManager.isComponentEnabled(GlassComponent.SideRail)) {
                    findViewById<View>(R.id.rightRailContainer)?.let { container ->
                        container.post {
                            GlassEffectManager.applyGlass(container, GlassComponent.SideRail, 0f)
                        }
                    }
                }
            }
            override fun onDrawerClosed(drawerView: View) {
                findViewById<View>(R.id.rightRailContainer)?.let {
                    GlassEffectManager.removeGlass(it)
                }
            }
        })
    }

    private fun populateRightRail() {
        findViewById<ImageView>(R.id.rightRailAvatar).loadImage(Anilist.avatar)
        findViewById<TextView>(R.id.rightRailUserName).text = Anilist.username ?: MAL.username ?: "User"
        findViewById<TextView>(R.id.rightRailUserEmail).text = "AniList ID: ${Anilist.userid ?: "—"}"
        findViewById<TextView>(R.id.rightRailEpisodesWatched).text = (Anilist.episodesWatched ?: 0).toString()
    }

    private suspend fun trimCachePeriodically() {
        while (true) {
            val interval = PrefManager.getVal<Int>(PrefName.TrimIntervalMin).coerceIn(5, 30)
            delay(interval * 60 * 1000L)
            if (!PrefManager.getVal<Boolean>(PrefName.SmartTrim)) continue
            trimCache()
        }
    }

    private suspend fun trimCache() {
        try {
            val cap = PrefManager.getVal<Int>(PrefName.CacheCapMb).coerceIn(70, 200)
            val intensity = PrefManager.getVal<Int>(PrefName.TrimIntensity).coerceIn(40, 90)
            val capBytes = cap * 1024L * 1024L

            val dirs = listOfNotNull(cacheDir, externalCacheDir)
            val allFiles = dirs.flatMap { dir ->
                if (!dir.exists()) return@flatMap emptyList()
                dir.walkTopDown().filter { it.isFile }.toList()
            }
            val totalSize = allFiles.sumOf { it.length() }
            if (totalSize <= capBytes) return

            val targetSize = capBytes * (100 - intensity) / 100
            val recentCutoff = System.currentTimeMillis() - 30 * 60 * 1000L
            val deletable = allFiles
                .filter { it.lastModified() < recentCutoff }
                .sortedBy { it.lastModified() }

            var removed = 0L
            for (file in deletable) {
                if (totalSize - removed <= targetSize) break
                removed += file.length()
                file.delete()
            }

            dirs.forEach { dir ->
                if (dir.exists()) {
                    dir.walkTopDown().filter { it.isDirectory }
                        .sortedByDescending { it.path.length }
                        .forEach { it.delete() }
                }
            }

            if (removed > 0) {
                val mb = removed / (1024L * 1024L)
                withContext(Dispatchers.Main) {
                    snackString("$mb MB cache cleared")
                }
            }
        } catch (_: Exception) { }
    }

}

private class SpringInterpolator(
    private val damping: Float = 6f,
    private val stiffness: Float = 10f
) : android.animation.TimeInterpolator {
    override fun getInterpolation(t: Float): Float {
        if (t <= 0f) return 0f
        if (t >= 1f) return 1f
        val decay = exp(-t * damping)
        val oscillation = cos(t * stiffness)
        return 1f - decay * oscillation
    }
}