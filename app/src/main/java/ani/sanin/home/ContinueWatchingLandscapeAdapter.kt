package ani.sanin.home

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import ani.sanin.R
import ani.sanin.connections.LogoApi
import ani.sanin.connections.anizip.AniZip
import ani.sanin.loadImage
import ani.sanin.media.Media
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.util.FocusEffectUtil
import com.google.android.material.card.MaterialCardView
import com.google.android.material.imageview.ShapeableImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ContinueWatchingLandscapeAdapter(
    private val items: MutableList<Media>,
    private val onItemClick: (Media) -> Unit
) : RecyclerView.Adapter<ContinueWatchingLandscapeAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_continue_watching_landscape, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val media = items[position]
        val ctx = holder.itemView.context

        holder.card.radius =
            PrefManager.getVal<Int>(PrefName.ContinueWatchingCardRoundness).toFloat()

        CoroutineScope(Dispatchers.IO).launch {
            val useScreenshot = PrefManager.getVal<Boolean>(PrefName.ContinueWatchingShowScreenshot)
            val imageUrl = if (useScreenshot) {
                val ep = media.userProgress?.toString()
                ep?.let { media.anime?.episodes?.get(it)?.thumb?.url }
            } else null
            if (imageUrl.isNullOrBlank()) {
                val anizipUrl = AniZip.getBackdropUrl(media.id) ?: media.cover
                withContext(Dispatchers.Main) {
                    if (!anizipUrl.isNullOrBlank()) {
                        holder.image.loadImage(anizipUrl)
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    holder.image.loadImage(imageUrl)
                }
            }
        }

        holder.clearlogo.visibility = View.GONE
        holder.overlayTitle.visibility = View.GONE
        CoroutineScope(Dispatchers.Main).launch {
            val logoUrl = LogoApi.getLogoUrl(media.id)
            if (!logoUrl.isNullOrBlank()) {
                holder.clearlogo.visibility = View.VISIBLE
                holder.clearlogo.loadImage(logoUrl)
                holder.overlayTitle.visibility = View.GONE
            } else {
                holder.clearlogo.visibility = View.GONE
                holder.overlayTitle.visibility = View.VISIBLE
                holder.overlayTitle.text = media.userPreferredName ?: media.name
            }
        }

        val episodes = media.anime?.totalEpisodes
        val progress = media.userProgress ?: 0
        val isReleasing = media.status == ctx.getString(R.string.status_releasing)

        if (progress > 0) {
            holder.episodeNo.visibility = View.VISIBLE
            holder.episodeNo.text = "E$progress"
        } else {
            holder.episodeNo.visibility = View.GONE
        }

        val subtitle = buildString {
            val nextAiring = media.anime?.nextAiringEpisodeTime
            if (episodes != null) {
                append("S1")
            }
            if (progress > 0) {
                append(" · $progress")
            }
            if (nextAiring != null && isReleasing) {
                val timeUntilAiring = nextAiring - System.currentTimeMillis() / 1000
                if (timeUntilAiring > 0 && timeUntilAiring < 86400) {
                    val minutes = (timeUntilAiring / 60).toInt()
                    append(" · ${minutes}min left")
                }
            }
        }
        holder.subtitle.text = subtitle

        if (progress > 0) {
            val position = PrefManager.getCustomVal("${media.id}_$progress", 0L)
            val duration = PrefManager.getCustomVal("${media.id}_${progress}_max", 0L)
            if (position > 0 && duration > 0) {
                holder.timeWatched.visibility = View.VISIBLE
                val posStr = String.format("%02d:%02d", position / 60000, (position % 60000) / 1000)
                val durStr = String.format("%02d:%02d", duration / 60000, (duration % 60000) / 1000)
                holder.timeWatched.text = "$posStr/$durStr"
            } else {
                holder.timeWatched.visibility = View.GONE
            }
        } else {
            holder.timeWatched.visibility = View.GONE
        }

        if (episodes != null && episodes > 0) {
            holder.progress.max = episodes
            holder.progress.progress = progress
        } else {
            holder.progress.max = 100
            holder.progress.progress = 0
        }

        holder.ongoing.isVisible = isReleasing

        if (media.anime != null) {
            holder.cwUserProgress.text = (media.userProgress ?: "~").toString()
            holder.cwTotal.text =
                " | ${if (media.anime.nextAiringEpisode != null) (media.anime.nextAiringEpisode.toString() + " | " + (media.anime.totalEpisodes ?: "~").toString()) else (media.anime.totalEpisodes ?: "~").toString()}"
            holder.cwProgressRow.visibility = View.VISIBLE
        } else {
            holder.cwProgressRow.visibility = View.GONE
        }

        setGradient(holder.gradientOverlay)
        holder.itemView.setOnClickListener { onItemClick(media) }
        holder.itemView.isFocusable = true
        holder.itemView.isFocusableInTouchMode = false
        holder.itemView.alpha = 0.85f
        FocusEffectUtil.applyFocusListener(holder.itemView, holder.card, fade = true)
    }

    override fun getItemCount() = items.size

    private fun setGradient(view: View) {
        val intensity = PrefManager.getVal<Float>(PrefName.CardGradientIntensity)
        if (intensity <= 0f) {
            view.background = null
            return
        }
        val endAlpha = 255
        val startColor = Color.argb(0, 0, 0, 0)
        val endColor = Color.argb(
            (endAlpha * intensity).toInt().coerceIn(0, 255),
            0, 0, 0
        )
        val gradient = GradientDrawable(
            GradientDrawable.Orientation.BOTTOM_TOP,
            intArrayOf(endColor, startColor)
        )
        view.background = gradient
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.cwCard)
        val image: ShapeableImageView = view.findViewById(R.id.cwImage)
        val gradientOverlay: View = view.findViewById(R.id.cwGradientOverlay)
        val clearlogo: ImageView = view.findViewById(R.id.cwClearlogo)
        val overlayTitle: TextView = view.findViewById(R.id.cwOverlayTitle)
        val title: TextView = view.findViewById(R.id.cwTitle)
        val subtitle: TextView = view.findViewById(R.id.cwSubtitle)
        val episodeNo: TextView = view.findViewById(R.id.cwEpisodeNo)
        val timeWatched: TextView = view.findViewById(R.id.cwTimeWatched)
        val cwProgressRow: LinearLayout = view.findViewById(R.id.cwProgressRow)
        val cwUserProgress: TextView = view.findViewById(R.id.cwUserProgress)
        val cwTotal: TextView = view.findViewById(R.id.cwTotal)
        val progress: ProgressBar = view.findViewById(R.id.cwProgress)
        val ongoing: View = view.findViewById(R.id.cwOngoing)
    }
}