package ani.sanin.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ani.sanin.databinding.ItemProviderBinding
import ani.sanin.util.FocusEffectUtil

class ProviderAdapter(
    private val items: List<ProviderItem>,
    private val onToggle: (ProviderItem, Boolean) -> Unit
) : RecyclerView.Adapter<ProviderAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemProviderBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemProviderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val b = holder.binding
        b.providerName.text = item.name
        b.providerSwitch.isChecked = item.isEnabled
        b.providerSwitch.isFocusable = true
        b.providerSwitch.isFocusableInTouchMode = false
        FocusEffectUtil.applyFocusListener(b.providerSwitch, b.providerSwitch, true)
        b.providerSwitch.setOnCheckedChangeListener { _, isChecked ->
            onToggle(item, isChecked)
        }
        b.root.setOnClickListener { b.providerSwitch.toggle() }
        b.root.isFocusable = true
        b.root.isFocusableInTouchMode = false
        FocusEffectUtil.applyFocusListener(b.root)
    }

    override fun getItemCount(): Int = items.size
}
