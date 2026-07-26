package ani.sanin.settings

import android.animation.Animator
import android.animation.ObjectAnimator
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.recyclerview.widget.RecyclerView
import ani.sanin.R
import ani.sanin.databinding.ItemProviderBinding
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName

class ProviderAdapter(
    private val items: MutableList<ProviderItem>,
    private val onStateChanged: () -> Unit
) : RecyclerView.Adapter<ProviderAdapter.ViewHolder>() {

    private val downloading = mutableSetOf<String>()

    class ViewHolder(val binding: ItemProviderBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemProviderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val b = holder.binding
        b.providerName.text = item.name

        if (item.saveName in downloading) {
            showDownloading(b)
        } else if (item.isEnabled) {
            showEnabled(b, position)
        } else {
            showIdle(b, position)
        }
    }

    private fun showIdle(b: ItemProviderBinding, pos: Int) {
        b.providerActionIcon.setImageResource(R.drawable.ic_extension)
        b.providerActionIcon.clearColorFilter()
        b.providerActionIcon.setColorFilter(Color.parseColor("#FFBB86FC"))
        b.providerActionIcon.visibility = View.VISIBLE
        b.providerProgress.visibility = View.GONE
        b.providerProgress.progress = 0
        b.providerActionIcon.setOnClickListener { startDownload(b, pos) }
    }

    private fun showDownloading(b: ItemProviderBinding) {
        b.providerActionIcon.visibility = View.GONE
        b.providerProgress.visibility = View.VISIBLE
    }

    private fun showEnabled(b: ItemProviderBinding, pos: Int) {
        b.providerActionIcon.setImageResource(R.drawable.ic_round_delete_24)
        b.providerActionIcon.clearColorFilter()
        b.providerActionIcon.setColorFilter(Color.parseColor("#FFCF6679"))
        b.providerActionIcon.visibility = View.VISIBLE
        b.providerProgress.visibility = View.GONE
        b.providerProgress.progress = 0
        b.providerActionIcon.setOnClickListener { v ->
            v.isEnabled = false
            val it = items[pos]
            it.isEnabled = false
            val current = PrefManager.getVal<Set<String>>(PrefName.EnabledProviders)
            PrefManager.setVal(PrefName.EnabledProviders, current - it.saveName)
            downloading.remove(it.saveName)
            notifyItemChanged(pos)
            onStateChanged()
            v.postDelayed({ v.isEnabled = true }, 300)
        }
    }

    private fun startDownload(b: ItemProviderBinding, pos: Int) {
        val item = items[pos]
        downloading.add(item.saveName)
        notifyItemChanged(pos)

        b.providerProgress.progress = 0
        b.providerProgress.visibility = View.VISIBLE

        val animator = ObjectAnimator.ofInt(b.providerProgress, "progress", 0, 100)
        animator.duration = 2500
        animator.interpolator = AccelerateDecelerateInterpolator()
        animator.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(a: Animator) {}
            override fun onAnimationEnd(a: Animator) {
                downloading.remove(item.saveName)
                item.isEnabled = true
                val current = PrefManager.getVal<Set<String>>(PrefName.EnabledProviders)
                PrefManager.setVal(PrefName.EnabledProviders, current + item.saveName)
                notifyItemChanged(pos)
                onStateChanged()
            }
            override fun onAnimationCancel(a: Animator) {
                downloading.remove(item.saveName)
                notifyItemChanged(pos)
            }
            override fun onAnimationRepeat(a: Animator) {}
        })
        animator.start()
    }

    override fun getItemCount(): Int = items.size
}
