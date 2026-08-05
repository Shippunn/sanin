package ani.sanin.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ani.sanin.R
import ani.sanin.databinding.ActivitySettingsExtrasBinding
import ani.sanin.initActivity
import ani.sanin.navBarHeight
import ani.sanin.restartApp
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.statusBarHeight
import ani.sanin.themes.ThemeManager
import ani.sanin.util.FocusEffectUtil
import ani.sanin.util.customAlertDialog
import com.google.android.material.materialswitch.MaterialSwitch

class SettingsExtrasActivity : AppCompatActivity() {
    lateinit var binding: ActivitySettingsExtrasBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager(this).applyTheme()
        initActivity(this)

        binding = ActivitySettingsExtrasBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.apply {
            settingsExtrasLayout.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = statusBarHeight
                bottomMargin = navBarHeight
            }
            extrasSettingsBack.isFocusable = true
            extrasSettingsBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
            FocusEffectUtil.applyFocusListener(
                extrasSettingsBack,
                extrasExtensions,
                extrasNotifications,
                extrasAddons,
                extrasLogCapture,
                extrasCache,
                extrasImmersive,
                extrasHomeLayout,
                extrasSmallView,
                extrasEmoji,
            )

            extrasExtensions.isFocusable = true
            extrasExtensions.setOnClickListener {
                startActivity(Intent(this@SettingsExtrasActivity, SettingsExtensionsActivity::class.java))
            }

            extrasNotifications.isFocusable = true
            extrasNotifications.setOnClickListener {
                startActivity(Intent(this@SettingsExtrasActivity, SettingsNotificationActivity::class.java))
            }

            extrasAddons.isFocusable = true
            extrasAddons.setOnClickListener {
                startActivity(Intent(this@SettingsExtrasActivity, SettingsAddonActivity::class.java))
            }

            extrasLogCapture.isFocusable = true
            extrasLogCapture.setOnClickListener {
                startActivity(Intent(this@SettingsExtrasActivity, SettingsLogActivity::class.java))
            }

            extrasCache.isFocusable = true
            extrasCache.setOnClickListener {
                startActivity(Intent(this@SettingsExtrasActivity, SettingsCacheActivity::class.java))
            }

            extrasImmersive.isChecked = PrefManager.getVal(PrefName.ImmersiveMode)
            extrasImmersive.setOnCheckedChangeListener { _, isChecked ->
                PrefManager.setVal(PrefName.ImmersiveMode, isChecked)
                restartApp()
            }

            extrasHomeLayout.setOnClickListener {
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

                val recyclerView = RecyclerView(this@SettingsExtrasActivity).apply {
                    layoutManager = LinearLayoutManager(this@SettingsExtrasActivity)
                    setPadding(0, 32, 0, 0)
                    clipToPadding = false
                }
                val adapter = HomeLayoutAdapter(displayList, views, currentVisibility) { fromPos, toPos ->
                    val item = displayList.removeAt(fromPos)
                    displayList.add(toPos, item)
                    recyclerView.adapter?.notifyItemMoved(fromPos, toPos)
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
                    setTitle(getString(R.string.home_layout_show))
                    setCustomView(recyclerView)
                    setPosButton(R.string.ok) {
                        PrefManager.setVal(PrefName.HomeLayout, currentVisibility)
                        PrefManager.setVal(PrefName.HomeLayoutOrder, displayList.drop(1))
                        restartApp()
                    }
                    setNegButton(R.string.cancel, null)
                    show()
                }
            }

            extrasSmallView.isChecked = PrefManager.getVal(PrefName.SmallView)
            extrasSmallView.setOnCheckedChangeListener { _, isChecked ->
                PrefManager.setVal(PrefName.SmallView, isChecked)
                restartApp()
            }

            extrasEmoji.isChecked = PrefManager.getVal(PrefName.Emoji)
            extrasEmoji.setOnCheckedChangeListener { _, isChecked ->
                PrefManager.setVal(PrefName.Emoji, isChecked)
            }
        }
    }

    override fun onResume() {
        ThemeManager(this).applyTheme()
        super.onResume()
    }

    inner class HomeLayoutAdapter(
        private val displayList: MutableList<Int>,
        private val views: Array<String>,
        private val currentVisibility: MutableList<Boolean>,
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
            val originalIndex = displayList[position]
            holder.title.text = views[originalIndex]

            holder.switch.setOnCheckedChangeListener(null)
            holder.switch.isChecked = currentVisibility[originalIndex]
            holder.switch.setOnCheckedChangeListener { _, isChecked ->
                currentVisibility[originalIndex] = isChecked
            }

            if (position == 0) {
                holder.dragHandle.visibility = View.INVISIBLE
            } else {
                holder.dragHandle.visibility = View.VISIBLE
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
                            if (pos < displayList.size - 1) {
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

        override fun getItemCount(): Int = displayList.size
    }
}
