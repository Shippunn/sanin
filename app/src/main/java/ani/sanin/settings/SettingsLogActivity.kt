package ani.sanin.settings

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.LinearLayoutManager
import ani.sanin.R
import ani.sanin.databinding.ActivitySettingsLogBinding
import ani.sanin.initActivity
import ani.sanin.navBarHeight
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.statusBarHeight
import ani.sanin.themes.ThemeManager
import ani.sanin.toast
import ani.sanin.util.FocusEffectUtil
import ani.sanin.util.LogcatBuffer
import ani.sanin.util.Logger

class SettingsLogActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsLogBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager(this).applyTheme()
        initActivity(this)
        val context = this
        binding = ActivitySettingsLogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.apply {
            settingsLogLayout.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = statusBarHeight
                bottomMargin = navBarHeight
            }
            logSettingsBack.isFocusable = true
            logSettingsBack.setOnClickListener {
                onBackPressedDispatcher.onBackPressed()
            }
            FocusEffectUtil.applyFocusListener(logSettingsBack)

            val loggingEnabled = PrefManager.getVal<Boolean>(PrefName.LoggingEnabled)

            settingsRecyclerView.adapter = SettingsAdapter(
                arrayListOf(
                    Settings(
                        type = 2,
                        name = "Logging",
                        desc = "Master switch for all log capture features",
                        icon = R.drawable.ic_round_edit_note_24,
                        isChecked = loggingEnabled,
                        switch = { isChecked, _ ->
                            PrefManager.setVal(PrefName.LoggingEnabled, isChecked)
                            if (isChecked) {
                                Logger.init(context)
                                LogcatBuffer.start()
                                Logger.log(Log.WARN, "Logging enabled manually")
                                toast("Logging enabled")
                            } else {
                                LogcatBuffer.stop()
                                Logger.clearLog()
                                toast("Logging disabled")
                            }
                            recreate()
                        },
                    ),
                    Settings(
                        type = 1,
                        name = "View Live Logcat",
                        desc = "Open a screen showing live logcat output",
                        icon = R.drawable.ic_round_view_list_24,
                        onClick = {
                            startActivity(Intent(this@SettingsLogActivity, LiveLogcatActivity::class.java))
                        },
                    ),
                    Settings(
                        type = 1,
                        name = "Capture Last 2 Minutes",
                        desc = "Read logcat entries from the past 2 minutes",
                        icon = R.drawable.ic_round_history_24,
                        onClick = {
                            if (!PrefManager.getVal<Boolean>(PrefName.LoggingEnabled)) {
                                toast("Enable Logging first")
                            } else {
                                val logs = Logger.readLogcatLastMinutes(2)
                                val truncated = if (logs.length > 500_000) {
                                    "... (truncated, ${logs.length} chars total)\n\n" + logs.takeLast(500_000)
                                } else logs
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Logcat - Last 2 Minutes")
                                    putExtra(Intent.EXTRA_TEXT, truncated)
                                }
                                startActivity(Intent.createChooser(shareIntent, "Share logs"))
                            }
                        },
                    ),
                    Settings(
                        type = 1,
                        name = "Clear Log Cache",
                        desc = "Delete all stored log files",
                        icon = R.drawable.ic_round_delete_24,
                        onClick = {
                            Logger.clearLog()
                            toast("Log cache cleared")
                        },
                    ),
                    Settings(
                        type = 1,
                        name = "Share Log File",
                        desc = "Share the saved log file with others",
                        icon = R.drawable.ic_round_share_24,
                        onClick = {
                            Logger.shareLog(context)
                        },
                    ),
                ),
            )
            settingsRecyclerView.apply {
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
                setHasFixedSize(true)
            }
        }
    }
}
