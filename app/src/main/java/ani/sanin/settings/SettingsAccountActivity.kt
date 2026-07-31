package ani.sanin.settings

import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import ani.sanin.R
import ani.sanin.connections.anilist.Anilist

import ani.sanin.connections.mal.MAL
import ani.sanin.connections.trakt.TraktAuth
import ani.sanin.databinding.ActivitySettingsAccountsBinding
import ani.sanin.initActivity
import ani.sanin.loadImage
import ani.sanin.navBarHeight
import ani.sanin.openLinkInBrowser
import ani.sanin.others.CustomBottomDialog
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.snackString
import ani.sanin.startMainActivity
import ani.sanin.statusBarHeight
import ani.sanin.themes.ThemeManager
import ani.sanin.util.FocusEffectUtil
import ani.sanin.util.customAlertDialog
import io.noties.markwon.Markwon
import io.noties.markwon.SoftBreakAddsNewLinePlugin
import kotlinx.coroutines.launch

class SettingsAccountActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsAccountsBinding
    private val restartMainActivity = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = startMainActivity(this@SettingsAccountActivity)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager(this).applyTheme()
        initActivity(this)
        val context = this

        binding = ActivitySettingsAccountsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.apply {
            settingsAccountsLayout.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = statusBarHeight
                bottomMargin = navBarHeight
            }
            accountSettingsBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

            settingsAccountHelp.isFocusable = true
            settingsAccountHelp.setOnClickListener {
                CustomBottomDialog.newInstance().apply {
                    setTitleText(context.getString(R.string.account_help))
                    addView(
                        TextView(it.context).apply {
                            val markWon = Markwon.builder(it.context)
                                .usePlugin(SoftBreakAddsNewLinePlugin.create()).build()
                            markWon.setMarkdown(this, context.getString(R.string.full_account_help))
                        }
                    )
                }.show(supportFragmentManager, "dialog")
            }

            fun reload() {
                settingsAnilistLogin.isFocusable = true
                settingsAnilistAvatar.isFocusable = true
                settingsAnilistTokenExpiry.isFocusable = true
                settingsMALLogin.isFocusable = true
                settingsMALAvatar.isFocusable = true
                FocusEffectUtil.applyFocusListener(
                    settingsAnilistLogin, settingsAnilistAvatar, settingsAnilistTokenExpiry,
                    settingsMALLogin, settingsMALAvatar
                )
                if (Anilist.token != null) {
                    settingsAnilistLogin.setText(R.string.logout)
                    settingsAnilistLogin.setOnClickListener {
                        Anilist.removeSavedToken()
                        restartMainActivity.isEnabled = true
                        reload()
                    }
                    settingsAnilistUsername.visibility = View.VISIBLE
                    settingsAnilistUsername.text = Anilist.username
                    settingsAnilistAvatar.loadImage(Anilist.avatar)
                    settingsAnilistAvatar.setOnClickListener {
                        it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        val anilistLink = getString(
                            R.string.anilist_link,
                            PrefManager.getVal<String>(PrefName.AnilistUserName)
                        )
                        openLinkInBrowser(anilistLink)
                    }

                    if (Anilist.bg != null) {
                        settingsAnilistBanner.visibility = View.VISIBLE
                        settingsAnilistScrim.visibility = View.VISIBLE
                        settingsAnilistBanner.loadImage(Anilist.bg)
                    } else {
                        settingsAnilistBanner.visibility = View.GONE
                        settingsAnilistScrim.visibility = View.GONE
                    }
                    
                    val daysLeft = Anilist.getTokenExpiryDays()
                    if (daysLeft != null) {
                        settingsAnilistTokenExpiry.visibility = View.VISIBLE
                        settingsAnilistTokenExpiry.text = when {
                            daysLeft <= 0 -> "Reconnect Now"
                            else -> "Reconnect in $daysLeft days"
                        }
                        settingsAnilistTokenExpiry.setOnClickListener {
                            Anilist.loginIntent(context)
                        }
                    } else {
                        settingsAnilistTokenExpiry.visibility = View.GONE
                    }

                    settingsMALLoginRequired.visibility = View.GONE
                    settingsMALLogin.visibility = View.VISIBLE
                    settingsMALUsername.visibility = View.VISIBLE

                    if (MAL.token != null) {
                        settingsMALLogin.setText(R.string.logout)
                        settingsMALLogin.setOnClickListener {
                            MAL.removeSavedToken()
                            restartMainActivity.isEnabled = true
                            reload()
                        }
                        settingsMALUsername.visibility = View.VISIBLE
                        settingsMALUsername.text = MAL.username
                        settingsMALAvatar.loadImage(MAL.avatar)
                        settingsMALAvatar.setOnClickListener {
                            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            openLinkInBrowser(getString(R.string.myanilist_link, MAL.username))
                        }
                    } else {
                        settingsMALAvatar.setImageResource(R.drawable.ic_round_person_24)
                        settingsMALUsername.visibility = View.GONE
                        settingsMALLogin.setText(R.string.login)
                        settingsMALLogin.setOnClickListener {
                            MAL.loginIntent(context)
                        }
                    }
                } else {
                    settingsAnilistAvatar.setImageResource(R.drawable.ic_round_person_24)
                    settingsAnilistUsername.visibility = View.GONE
                    settingsAnilistTokenExpiry.visibility = View.GONE
                    settingsAnilistBanner.visibility = View.GONE
                    settingsAnilistScrim.visibility = View.GONE
                    settingsRecyclerView.visibility = View.GONE
                    settingsAnilistLogin.setText(R.string.login)
                    settingsAnilistLogin.setOnClickListener {
                        context.customAlertDialog().apply {
                            setTitle(getString(R.string.login_to_anilist))
                            singleChoiceItems(
                                arrayOf(
                                    getString(R.string.browser_login)
                                )
                            ) { choice ->
                                when (choice) {
                                    0 -> Anilist.loginIntent(context)
                                }
                            }
                            setNegButton(R.string.cancel)
                            show()
                        }
                    }
                    settingsMALLoginRequired.visibility = View.VISIBLE
                    settingsMALLogin.visibility = View.GONE
                    settingsMALUsername.visibility = View.GONE
                }


            }
            reload()
        }
        binding.settingsDiscordLogin.isFocusable = true
        FocusEffectUtil.applyFocusListener(binding.settingsDiscordLogin)
        binding.settingsDiscordLogin.setOnClickListener {
            openLinkInBrowser(getString(R.string.discord))
        }
        binding.settingsRecyclerView.adapter = SettingsAdapter(
            arrayListOf(

                Settings(
                    type = 1,
                    name = getString(R.string.anilist_settings),
                    desc = getString(R.string.alsettings_desc),
                    icon = R.drawable.ic_anilist,
                    onClick = {
                        lifecycleScope.launch {
                            Anilist.query.getUserData()
                            startActivity(Intent(context, AnilistSettingsActivity::class.java))
                        }
                    },
                    isActivity = true
                ),
                Settings(
                    type = 2,
                    name = getString(R.string.comments_button),
                    desc = getString(R.string.comments_button_desc),
                    icon = R.drawable.ic_round_comment_24,
                    isChecked = PrefManager.getVal<Int>(PrefName.CommentsEnabled) == 1,
                    switch = { isChecked, _ ->
                        PrefManager.setVal(PrefName.CommentsEnabled, if (isChecked) 1 else 2)
                        reload()
                    },
                    isVisible = Anilist.token != null
                ),
                Settings(
                    type = 2,
                    name = "Trakt Reviews",
                    desc = "Show community reviews from Trakt in the comments tab",
                    icon = R.drawable.ic_round_comment_24,
                    isChecked = PrefManager.getVal<Int>(PrefName.TraktCommentsEnabled) == 1,
                    switch = { isChecked, _ ->
                        PrefManager.setVal(PrefName.TraktCommentsEnabled, if (isChecked) 1 else 0)
                    },
                    isVisible = true
                ),
                Settings(
                    type = 0,
                    name = if (TraktAuth.isLoggedIn()) "Trakt: ${TraktAuth.username}" else "Login to Trakt",
                    desc = if (TraktAuth.isLoggedIn()) "Tap to logout" else "Like, reply & post on Trakt",
                    icon = R.drawable.vpn_key_24,
                    onClick = {
                        if (TraktAuth.isLoggedIn()) {
                            TraktAuth.logout()
                            snackString("Logged out of Trakt")
                            reload()
                        } else {
                            TraktAuth.loginIntent(context)
                        }
                    },
                    isVisible = true
                ),

            )
        )
        binding.settingsRecyclerView.layoutManager =
            LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)

    }

    fun reload() {
        snackString(getString(R.string.restart_app_extra))
    }
}