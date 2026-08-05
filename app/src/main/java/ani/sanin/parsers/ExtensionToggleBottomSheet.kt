package ani.sanin.parsers

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ani.sanin.R
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.materialswitch.MaterialSwitch
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ExtensionToggleBottomSheet : BottomSheetDialogFragment() {

    private val animeExtensionManager: AnimeExtensionManager = Injekt.get()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.bottom_sheet_extension_toggle, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val builtInHeader = view.findViewById<TextView>(R.id.builtInHeader)
        val builtInRecyclerView = view.findViewById<RecyclerView>(R.id.builtInRecyclerView)
        val extensionsHeader = view.findViewById<TextView>(R.id.extensionsHeader)
        val extensionsRecyclerView = view.findViewById<RecyclerView>(R.id.extensionsRecyclerView)

        // Built-in providers
        val builtInProviders = AnimeSources.allNativeParsers
        val enabledProviders = PrefManager.getVal<Set<String>>(PrefName.EnabledProviders).toMutableSet()
        if (enabledProviders.isEmpty()) {
            enabledProviders.addAll(builtInProviders.map { it.saveName })
            PrefManager.setVal(PrefName.EnabledProviders, enabledProviders)
        }

        if (builtInProviders.isNotEmpty()) {
            builtInHeader.visibility = View.VISIBLE
            builtInRecyclerView.layoutManager = LinearLayoutManager(requireContext())
            builtInRecyclerView.adapter = ExtensionAdapter(
                items = builtInProviders.map { ExtItem(it.saveName, it.name, null) },
                enabledNames = enabledProviders,
                onToggle = { name, enabled ->
                    val current = PrefManager.getVal<Set<String>>(PrefName.EnabledProviders).toMutableSet()
                    if (enabled) current.add(name) else current.remove(name)
                    PrefManager.setVal(PrefName.EnabledProviders, current)
                }
            )
        }

        // Installed extensions
        val extensions = animeExtensionManager.installedExtensionsFlow.value
        val enabledExtensions = PrefManager.getVal<Set<String>>(PrefName.EnabledExtensions).toMutableSet()

        if (extensions.isNotEmpty()) {
            extensionsHeader.visibility = View.VISIBLE
            extensionsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
            extensionsRecyclerView.adapter = ExtensionAdapter(
                items = extensions.map { ExtItem(it.pkgName, it.name, it.icon) },
                enabledNames = enabledExtensions,
                onToggle = { name, enabled ->
                    val current = PrefManager.getVal<Set<String>>(PrefName.EnabledExtensions).toMutableSet()
                    if (enabled) current.add(name) else current.remove(name)
                    PrefManager.setVal(PrefName.EnabledExtensions, current)
                }
            )
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return BottomSheetDialog(requireContext(), R.style.ThemeOverlay.Material3.BottomSheetDialog)
    }

    private data class ExtItem(val id: String, val name: String, val icon: android.graphics.Bitmap?)

    private class ExtensionAdapter(
        private val items: List<ExtItem>,
        private val enabledNames: MutableSet<String>,
        private val onToggle: (String, Boolean) -> Unit
    ) : RecyclerView.Adapter<ExtensionAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.itemExtIcon)
            val name: TextView = view.findViewById(R.id.itemExtName)
            val switch: MaterialSwitch = view.findViewById(R.id.itemExtSwitch)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_extension_toggle, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.name.text = item.name
            holder.switch.isChecked = item.id in enabledNames
            if (item.icon != null) {
                holder.icon.setImageBitmap(item.icon)
            }
            holder.switch.setOnCheckedChangeListener { _, isChecked ->
                onToggle(item.id, isChecked)
            }
        }

        override fun getItemCount(): Int = items.size
    }
}