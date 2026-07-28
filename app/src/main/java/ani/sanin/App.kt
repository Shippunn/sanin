package ani.sanin

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import ani.sanin.aniyomi.anime.custom.AppModule
import ani.sanin.aniyomi.anime.custom.PreferenceModule
import ani.sanin.connections.comments.CommentsAPI
import ani.sanin.connections.trakt.TraktAuth
import ani.sanin.connections.crashlytics.CrashlyticsInterface
import ani.sanin.notifications.TaskScheduler
import ani.sanin.others.DisabledReports
import ani.sanin.parsers.AnimeSources
import ani.sanin.settings.SettingsActivity
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.util.FinalExceptionHandler
import ani.sanin.util.LogcatBuffer
import ani.sanin.util.Logger
import com.google.android.material.color.DynamicColors
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import logcat.AndroidLogcatLogger
import logcat.LogPriority
import logcat.LogcatLogger
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get


@SuppressLint("StaticFieldLeak")
class App : Application() {
    private lateinit var animeExtensionManager: AnimeExtensionManager

    init {
        instance = this
    }

    val mFTActivityLifecycleCallbacks = FTActivityLifecycleCallbacks()

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        PrefManager.init(this)

        val crashlytics =
            ani.sanin.connections.crashlytics.CrashlyticsFactory.createCrashlytics()
        Injekt.addSingletonFactory<CrashlyticsInterface> { crashlytics }
        crashlytics.initialize(this)
        if (PrefManager.getVal(PrefName.LoggingEnabled)) {
            Logger.init(this)
            LogcatBuffer.start()
            Logger.log(Log.WARN, "App: Logging started")
        }
        Thread.setDefaultUncaughtExceptionHandler(FinalExceptionHandler())

        Injekt.importModule(AppModule(this))
        Injekt.importModule(PreferenceModule(this))


        val useMaterialYou: Boolean = PrefManager.getVal(PrefName.UseMaterialYou)
        if (useMaterialYou) {
            DynamicColors.applyToActivitiesIfAvailable(this)
        }
        registerActivityLifecycleCallbacks(mFTActivityLifecycleCallbacks)

        crashlytics.setCrashlyticsCollectionEnabled(!DisabledReports)
        (PrefManager.getVal(PrefName.SharedUserID) as Boolean).let {
            if (!it) return@let
            val aUsername = PrefManager.getVal(PrefName.AnilistUserName, null as String?)
            if (aUsername != null) {
                crashlytics.setCustomKey("aUsername", aUsername)
            }
        }
        crashlytics.setCustomKey("device Info", SettingsActivity.getDeviceInfo())

        initializeNetwork()

        setupNotificationChannels()
        if (!LogcatLogger.isInstalled) {
            LogcatLogger.install(AndroidLogcatLogger(LogPriority.VERBOSE))
        }

        if (PrefManager.getVal<Int>(PrefName.CommentsEnabled) == 0) {
            if (BuildConfig.FLAVOR.contains("fdroid")) {
                PrefManager.setVal(PrefName.CommentsEnabled, 2)
            } else {
                PrefManager.setVal(PrefName.CommentsEnabled, 1)
            }
        }

        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            val anikotoRepo = "https://raw.githubusercontent.com/Confused-Creature-180/aniyomi-extensions/repo/index.min.json"
            val repos = PrefManager.getVal<Set<String>>(PrefName.AnimeExtensionRepos)
            if (anikotoRepo !in repos) {
                PrefManager.setVal(PrefName.AnimeExtensionRepos, repos + anikotoRepo)
            }
            animeExtensionManager = Injekt.get()
            launch {
                animeExtensionManager.findAvailableExtensions()
            }
            Logger.log("Anime Extensions: ${animeExtensionManager.installedExtensionsFlow.first()}")
            AnimeSources.init(animeExtensionManager.installedExtensionsFlow)
        }
        GlobalScope.launch {
            if (PrefManager.getVal<Int>(PrefName.CommentsEnabled) == 1) {
                CommentsAPI.fetchAuthToken(this@App)
            }
            TraktAuth.loadSavedToken()

            val useAlarmManager = PrefManager.getVal<Boolean>(PrefName.UseAlarmManager)
            val scheduler = TaskScheduler.create(this@App, useAlarmManager)
            try {
                scheduler.scheduleAllTasks(this@App)
            } catch (e: IllegalStateException) {
                Logger.log("Failed to schedule tasks")
                Logger.log(e)
            }
        }
    }

    private fun setupNotificationChannels() {
        try {
            Notifications.createChannels(this)
        } catch (e: Exception) {
            Logger.log("Failed to modify notification channels")
            Logger.log(e)
        }
    }

    inner class FTActivityLifecycleCallbacks : ActivityLifecycleCallbacks {
        var currentActivity: Activity? = null
        var lastActivity: String? = null
        override fun onActivityCreated(p0: Activity, p1: Bundle?) {
            lastActivity = p0.javaClass.simpleName
        }

        override fun onActivityStarted(p0: Activity) {
            currentActivity = p0
        }

        override fun onActivityResumed(p0: Activity) {
            currentActivity = p0
        }

        override fun onActivityPaused(p0: Activity) {}
        override fun onActivityStopped(p0: Activity) {}
        override fun onActivitySaveInstanceState(p0: Activity, p1: Bundle) {}
        override fun onActivityDestroyed(p0: Activity) {}
    }

    companion object {
        var instance: App? = null

        /** Reference to the application context.
         *
         * USE WITH EXTREME CAUTION!**/
        var context: Context? = null
        fun currentContext(): Context? {
            return instance?.mFTActivityLifecycleCallbacks?.currentActivity ?: context
        }

        fun currentActivity(): Activity? {
            return instance?.mFTActivityLifecycleCallbacks?.currentActivity
        }
    }
}