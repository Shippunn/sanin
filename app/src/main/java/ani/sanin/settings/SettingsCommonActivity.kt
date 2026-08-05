package ani.sanin.settings

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch
import ani.sanin.R
import ani.sanin.connections.anilist.Anilist
import ani.sanin.databinding.ActivitySettingsCommonBinding
import ani.sanin.databinding.DialogUserAgentBinding
import ani.sanin.initActivity
import ani.sanin.navBarHeight
import ani.sanin.savePrefsToDownloads
import ani.sanin.restartApp
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.util.TvKeyboardUtil
import ani.sanin.settings.saving.internal.Location
import ani.sanin.settings.saving.internal.PreferenceKeystore
import ani.sanin.settings.saving.internal.PreferencePackager
import ani.sanin.statusBarHeight
import ani.sanin.themes.ThemeManager
import ani.sanin.toast
import ani.sanin.util.FocusEffectUtil
import ani.sanin.util.LauncherWrapper
import ani.sanin.util.StoragePermissions
import ani.sanin.util.customAlertDialog
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SettingsCommonActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsCommonBinding
    private lateinit var launcher: LauncherWrapper

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager(this).applyTheme()
        initActivity(this)
        val context = this
        binding = ActivitySettingsCommonBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val openDocumentLauncher =
            registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) {
                    try {
                        val jsonString =
                            contentResolver.openInputStream(uri)?.readBytes()
                                ?: throw Exception("Error reading file")
                        val name = DocumentFile.fromSingleUri(this, uri)?.name ?: "settings"
                        // .sani is encrypted, .ani is not
                        if (name.endsWith(".sani")) {
                            passwordAlertDialog(false) { password ->
                                if (password != null) {
                                    val salt = jsonString.copyOfRange(0, 16)
                                    val encrypted = jsonString.copyOfRange(16, jsonString.size)
                                    val decryptedJson =
                                        try {
                                            PreferenceKeystore.decryptWithPassword(
                                                password,
                                                encrypted,
                                                salt,
                                            )
                                        } catch (e: Exception) {
                                            toast(getString(R.string.incorrect_password))
                                            return@passwordAlertDialog
                                        }
                                    if (PreferencePackager.unpack(decryptedJson)) restartApp()
                                } else {
                                    toast(getString(R.string.password_cannot_be_empty))
                                }
                            }
                        } else if (name.endsWith(".ani")) {
                            val decryptedJson = jsonString.toString(Charsets.UTF_8)
                            if (PreferencePackager.unpack(decryptedJson)) restartApp()
                        } else {
                            toast(getString(R.string.unknown_file_type))
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        toast(getString(R.string.error_importing_settings))
                    }
                }
            }
        val contract = ActivityResultContracts.OpenDocumentTree()
        launcher = LauncherWrapper(this, contract)

        binding.apply {
            settingsCommonLayout.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = statusBarHeight
                bottomMargin = navBarHeight
            }
            commonSettingsBack.isFocusable = true
            commonSettingsBack.setOnClickListener {
                onBackPressedDispatcher.onBackPressed()
            }
            FocusEffectUtil.applyFocusListener(commonSettingsBack)
            val exDns =
                listOf(
                    "None",
                    "Cloudflare",
                    "Google",
                    "AdGuard",
                    "Quad9",
                    "AliDNS",
                    "DNSPod",
                    "360",
                    "Quad101",
                    "Mullvad",
                    "Controld",
                    "Njalla",
                    "Shecan",
                    "Libre",
                )
            settingsExtensionDns.setText(exDns[PrefManager.getVal(PrefName.DohProvider)])
            settingsExtensionDns.isFocusable = true
            settingsExtensionDns.setOnClickListener {
                val current = PrefManager.getVal<Int>(PrefName.DohProvider)
                customAlertDialog().apply {
                    setTitle("DNS Provider")
                    singleChoiceItems(exDns.toTypedArray(), current) { index ->
                        PrefManager.setVal(PrefName.DohProvider, index)
                        settingsExtensionDns.setText(exDns[index])
                        restartApp()
                    }
                    show()
                }
            }

            val startUpTabs = arrayOf("Home", "Anime")

            settingsRecyclerView.adapter =
                SettingsAdapter(
                    arrayListOf(
                        Settings(
                            type = 1,
                            name = "Home Sections",
                            desc = "Reorder, show or hide home sections",
                            icon = R.drawable.ic_round_home_24,
                            onClick = {
                                val currentVisibility = PrefManager.getVal<List<Boolean>>(PrefName.HomeLayout).toMutableList()
                                var currentOrder = PrefManager.getVal<List<Int>>(PrefName.HomeLayoutOrder).toMutableList()
                                val views = resources.getStringArray(R.array.home_layouts)

                                if (currentVisibility.size < views.size) {
                                    repeat(views.size - currentVisibility.size) { currentVisibility.add(true) }
                                } else if (currentVisibility.size > views.size) {
                                    currentVisibility.subList(views.size, currentVisibility.size).clear()
                                }

                                val allIndices = views.indices.toList()
                                if (currentOrder.isEmpty()) {
                                    currentOrder = allIndices.toMutableList()
                                } else {
                                    val sanitizedOrder = currentOrder.filter { it in allIndices }.distinct().toMutableList()
                                    val missing = allIndices.filterNot { it in sanitizedOrder }
                                    sanitizedOrder.addAll(missing)
                                    currentOrder = sanitizedOrder
                                }

                                val displayList = currentOrder.toMutableList()

                                val recyclerView = RecyclerView(this@SettingsCommonActivity).apply {
                                    layoutManager = LinearLayoutManager(this@SettingsCommonActivity)
                                    setPadding(0, 32, 0, 0)
                                    clipToPadding = false
                                }
                                val adapter = HomeLayoutAdapter(displayList, views, currentVisibility) { fromPos, toPos ->
                                    val item = displayList.removeAt(fromPos)
                                    displayList.add(toPos, item)
                                    adapter.notifyItemMoved(fromPos, toPos)
                                }
                                recyclerView.adapter = adapter

                                val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
                                    ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
                                ) {
                                    override fun onMove(
                                        recyclerView: RecyclerView,
                                        viewHolder: RecyclerView.ViewHolder,
                                        target: RecyclerView.ViewHolder
                                    ): Boolean {
                                        val fromPos = viewHolder.bindingAdapterPosition
                                        val toPos = target.bindingAdapterPosition

                                        val item = displayList.removeAt(fromPos)
                                        displayList.add(toPos, item)
                                        adapter.notifyItemMoved(fromPos, toPos)
                                        return true
                                    }

                                    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

                                    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                                        super.clearView(recyclerView, viewHolder)
                                        viewHolder.itemView.elevation = 0f
                                    }

                                    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                                        super.onSelectedChanged(viewHolder, actionState)
                                        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                                            viewHolder?.itemView?.elevation = 8f
                                        }
                                    }
                                })
                                itemTouchHelper.attachToRecyclerView(recyclerView)

                                customAlertDialog().apply {
                                    setTitle("Home Sections")
                                    setCustomView(recyclerView)
                                    setPosButton(R.string.ok) {
                                        PrefManager.setVal(PrefName.HomeLayout, currentVisibility)
                                        PrefManager.setVal(PrefName.HomeLayoutOrder, displayList.drop(1))
                                        restartApp()
                                    }
                                    setNegButton(R.string.cancel, null)
                                    show()
                                }
                            },
                        ),
                        Settings(
                            type = 1,
                            name = "Server Load Timeout",
                            desc = "Max wait per video server (10-25 seconds)",
                            icon = R.drawable.ic_round_history_24,
                            onClick = {
                                val min = 10
                                val max = 25
                                val current = PrefManager.getVal<Int>(PrefName.ServerLoadTimeoutSeconds).coerceIn(min, max)
                                val label = TextView(context).apply {
                                    text = "$current seconds"
                                    textSize = 16f
                                }
                                val seekBar = SeekBar(context).apply {
                                    setMax(max - min)
                                    progress = current - min
                                    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                                        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                                            label.text = "${progress + min} seconds"
                                        }
                                        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                                        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                                    })
                                }
                                val container = LinearLayout(context).apply {
                                    orientation = LinearLayout.VERTICAL
                                    setPadding(64, 32, 64, 32)
                                    addView(label)
                                    addView(seekBar)
                                }
                                customAlertDialog().apply {
                                    setTitle("Server Load Timeout (seconds)")
                                    setMessage("Maximum time to wait for each video server before skipping it.")
                                    setCustomView(container)
                                    setPosButton(R.string.ok) {
                                        val value = seekBar.progress + min
                                        PrefManager.setVal(PrefName.ServerLoadTimeoutSeconds, value)
                                    }
                                    setNegButton(R.string.cancel)
                                    show()
                                }
                            },
                        ),
                        Settings(
                            type = 1,
                            name = "Startup Tab",
                            desc = "Default tab on app launch",
                            icon = R.drawable.ic_round_home_24,
                            onClick = {
                                val labels = startUpTabs
                                customAlertDialog().apply {
                                    setTitle("Startup Tab")
                                    singleChoiceItems(labels, PrefManager.getVal<Int>(PrefName.DefaultStartUpTab)) { index ->
                                        PrefManager.setVal(PrefName.DefaultStartUpTab, index)
                                        initActivity(context)
                                    }
                                    show()
                                }
                            },
                        ),
                        Settings(
                            type = 1,
                            name = getString(R.string.backup_restore),
                            desc = getString(R.string.backup_restore_desc),
                            icon = R.drawable.backup_restore,
                            onClick = {
                                StoragePermissions.downloadsPermission(context)
                                val filteredLocations = Location.entries.filter { it.exportable }
                                val selectedArray = BooleanArray(filteredLocations.size) { false }
                                context.customAlertDialog().apply {
                                    setTitle(R.string.backup_restore)
                                    multiChoiceItems(
                                        filteredLocations.map { it.name }.toTypedArray(),
                                        selectedArray,
                                    ) { updatedSelection ->
                                        for (i in updatedSelection.indices) {
                                            selectedArray[i] = updatedSelection[i]
                                        }
                                    }
                                    setPosButton(R.string.button_restore) {
                                        openDocumentLauncher.launch(arrayOf("*/*"))
                                    }
                                    setNegButton(R.string.button_backup) {
                                        if (!selectedArray.contains(true)) {
                                            toast(R.string.no_location_selected)
                                            return@setNegButton
                                        }
                                        val selected =
                                            filteredLocations.filterIndexed { index, _ -> selectedArray[index] }
                                        if (selected.contains(Location.Protected)) {
                                            passwordAlertDialog(true) { password ->
                                                if (password != null) {
                                                    savePrefsToDownloads(
                                                        "SaninSettings",
                                                        PrefManager.exportAllPrefs(selected),
                                                        context,
                                                        password,
                                                    )
                                                } else {
                                                    toast(R.string.password_cannot_be_empty)
                                                }
                                            }
                                        } else {
                                            savePrefsToDownloads(
                                                "SaninSettings",
                                                PrefManager.exportAllPrefs(selected),
                                                context,
                                                null,
                                            )
                                        }
                                    }
                                    setNeutralButton(R.string.cancel) {}
                                    show()
                                }
                            },
                        ),
                        Settings(
                            type = 2,
                            name = getString(R.string.always_continue_content),
                            desc = getString(R.string.always_continue_content_desc),
                            icon = R.drawable.ic_round_delete_24,
                            isChecked = PrefManager.getVal(PrefName.ContinueMedia),
                            switch = { isChecked, _ ->
                                PrefManager.setVal(PrefName.ContinueMedia, isChecked)
                            },
                        ),
                        Settings(
                            type = 2,
                            name = getString(R.string.hide_private),
                            desc = getString(R.string.hide_private_desc),
                            icon = R.drawable.ic_round_remove_red_eye_24,
                            isChecked = PrefManager.getVal(PrefName.HidePrivate),
                            switch = { isChecked, _ ->
                                PrefManager.setVal(PrefName.HidePrivate, isChecked)
                                restartApp()
                            },
                        ),
                        Settings(
                            type = 1,
                            name = "Keyboard Mode",
                            desc = "System / Custom keyboard",
                            icon = R.drawable.ic_round_keyboard_24,
                            onClick = {
                                val current = PrefManager.getVal<Int>(PrefName.KeyboardMode)
                                val labels = arrayOf("System Keyboard", "Custom")
                                val idx = if (current == 2) 1 else 0
                                customAlertDialog().apply {
                                    setTitle("Keyboard Mode")
                                    singleChoiceItems(labels, idx) { index ->
                                        PrefManager.setVal(PrefName.KeyboardMode, if (index == 1) 2 else 0)
                                        restartApp()
                                    }
                                    show()
                                }
                            },
                        ),
                        Settings(
                            type = 2,
                            name = getString(R.string.search_source_list),
                            desc = getString(R.string.search_source_list_desc),
                            icon = R.drawable.ic_round_search_sources_24,
                            isChecked = PrefManager.getVal(PrefName.SearchSources),
                            switch = { isChecked, _ ->
                                PrefManager.setVal(PrefName.SearchSources, isChecked)
                            },
                        ),
                        Settings(
                            type = 2,
                            name = getString(R.string.recentlyListOnly),
                            desc = getString(R.string.recentlyListOnly_desc),
                            icon = R.drawable.ic_round_new_releases_24,
                            isChecked = PrefManager.getVal(PrefName.RecentlyListOnly),
                            switch = { isChecked, _ ->
                                PrefManager.setVal(PrefName.RecentlyListOnly, isChecked)
                            },
                        ),
                        Settings(
                            type = 2,
                            name = getString(R.string.adult_only_content),
                            desc = getString(R.string.adult_only_content_desc),
                            icon = R.drawable.ic_round_nsfw_24,
                            isChecked = PrefManager.getVal(PrefName.AdultOnly),
                            switch = { isChecked, _ ->
                                PrefManager.setVal(PrefName.AdultOnly, isChecked)
                                restartApp()
                            },
                            isVisible = Anilist.adult,
                        ),
                        Settings(
                            type = 2,
                            name = "Auto-Sync AniList",
                            desc = "Automatically sync watch progress with AniList",
                            icon = R.drawable.ic_round_sync_24,
                            isChecked = PrefManager.getVal(PrefName.AutoSyncAniList),
                            switch = { isChecked, _ ->
                                PrefManager.setVal(PrefName.AutoSyncAniList, isChecked)
                            }
                        ),
                        Settings(
                            type = 2,
                            name = "Update Progress Automatically",
                            desc = "Auto-update episode progress on watch",
                            icon = R.drawable.ic_round_sync_24,
                            isChecked = PrefManager.getVal(PrefName.UpdateProgressAutomatically),
                            switch = { isChecked, _ ->
                                PrefManager.setVal(PrefName.UpdateProgressAutomatically, isChecked)
                            }
                        ),
                        Settings(
                            type = 2,
                            name = "Auto-Update Extensions",
                            desc = "Automatically check for extension updates",
                            icon = R.drawable.ic_round_playlist_add_24,
                            isChecked = PrefManager.getVal(PrefName.AutoUpdateExtensions),
                            switch = { isChecked, _ ->
                                PrefManager.setVal(PrefName.AutoUpdateExtensions, isChecked)
                            }
                        ),
                        Settings(
                            type = 2,
                            name = "AniList Notifications",
                            desc = "Fetch AniList notification count",
                            icon = R.drawable.ic_anilist,
                            isChecked = PrefManager.getVal(PrefName.AnilistNotifications),
                            switch = { isChecked, _ ->
                                PrefManager.setVal(PrefName.AnilistNotifications, isChecked)
                            }
                        ),
                        Settings(
                            type = 2,
                            name = "Episode Notifications",
                            desc = "Notify when new episodes are released",
                            icon = R.drawable.ic_round_notifications_none_24,
                            isChecked = PrefManager.getVal(PrefName.EpisodeNotifications),
                            switch = { isChecked, _ ->
                                PrefManager.setVal(PrefName.EpisodeNotifications, isChecked)
                            }
                        ),
                    ),
                )
            settingsRecyclerView.apply {
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
                setHasFixedSize(true)
            }
        }
    }

    private fun passwordAlertDialog(
        isExporting: Boolean,
        callback: (CharArray?) -> Unit,
    ) {
        val password = CharArray(16).apply { fill('0') }

        // Inflate the dialog layout
        val dialogView = DialogUserAgentBinding.inflate(layoutInflater)
        TvKeyboardUtil.setupTvInput(dialogView.userAgentTextBox)
        val box = dialogView.userAgentTextBox
        box.hint = getString(R.string.password)
        box.setSingleLine()

        val dialog =
            AlertDialog
                .Builder(this, R.style.MyPopup)
                .setTitle(getString(R.string.enter_password))
                .setView(dialogView.root)
                .setPositiveButton(R.string.ok, null)
                .setNegativeButton(R.string.cancel) { dialog, _ ->
                    password.fill('0')
                    dialog.dismiss()
                    callback(null)
                }.create()

        fun handleOkAction() {
            val editText = dialogView.userAgentTextBox
            if (editText.text?.isNotBlank() == true) {
                editText.text
                    ?.toString()
                    ?.trim()
                    ?.toCharArray(password)
                dialog.dismiss()
                callback(password)
            } else {
                toast(getString(R.string.password_cannot_be_empty))
            }
        }
        box.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                handleOkAction()
                true
            } else {
                false
            }
        }
        dialogView.subtitle.visibility = View.VISIBLE
        if (!isExporting) {
            dialogView.subtitle.text =
                getString(R.string.enter_password_to_decrypt_file)
        }

        dialog.window?.apply {
            setDimAmount(0.8f)
            attributes.windowAnimations = android.R.style.Animation_Dialog
            TvKeyboardUtil.retainWindowFocus(this)
        }
        dialog.setOnShowListener {
            dialogView.userAgentTextBox.requestFocus()
            TvKeyboardUtil.showKeyboardDelayed(dialogView.userAgentTextBox)
        }
        dialog.show()

        // Override the positive button here
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            handleOkAction()
        }
    }

    inner class HomeLayoutAdapter(
        private val items: MutableList<Int>,
        private val views: Array<String>,
        private val visibility: MutableList<Boolean>,
        private val onMove: (Int, Int) -> Unit
    ) : RecyclerView.Adapter<HomeLayoutAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val dragHandle: ImageView = view.findViewById(R.id.itemHomeLayoutDragHandle)
            val title: TextView = view.findViewById(R.id.itemHomeLayoutTitle)
            val switch: MaterialSwitch = view.findViewById(R.id.itemHomeLayoutSwitch)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_home_layout, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val idx = items[position]
            holder.title.text = views[idx]
            holder.switch.isChecked = visibility[idx]
            holder.switch.setOnCheckedChangeListener { _, isChecked ->
                visibility[idx] = isChecked
            }
            holder.itemView.setOnKeyListener { _, keyCode, event ->
                if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                    when (keyCode) {
                        android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                            val pos = holder.bindingAdapterPosition
                            if (pos > 0) {
                                onMove(pos, pos - 1)
                                true
                            } else false
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                            val pos = holder.bindingAdapterPosition
                            if (pos < items.size - 1) {
                                onMove(pos, pos + 1)
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            }
            holder.itemView.isFocusable = true
            holder.itemView.isFocusableInTouchMode = true
        }

        override fun getItemCount(): Int = items.size
    }
}
