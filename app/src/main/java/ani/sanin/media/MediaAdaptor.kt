package ani.sanin.media

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import ani.sanin.R
import ani.sanin.blurImage
import ani.sanin.connections.LogoApi
import ani.sanin.connections.anizip.AniZip
import ani.sanin.currActivity
import ani.sanin.databinding.ItemMediaCompactBinding
import ani.sanin.databinding.ItemMediaCompactLandBinding
import ani.sanin.databinding.ItemMediaLargeBinding
import ani.sanin.databinding.ItemMediaPageBinding
import ani.sanin.databinding.ItemMediaPageSmallBinding
import ani.sanin.loadImage
import ani.sanin.setSafeOnClickListener
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.util.FocusEffectUtil
import com.flaviofaria.kenburnsview.RandomTransitionGenerator
import java.io.Serializable
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class MediaAdaptor(
    var type: Int,
    private val mediaList: MutableList<Media>?,
    private val activity: FragmentActivity,
    private val matchParent: Boolean = false,
    private val viewPager: ViewPager2? = null,
    private val fav: Boolean = false,
    private val isOtherUser: Boolean = false,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var rawCardStyle = 0
    private var isLandscape = false
    private var cachedCardRoundness = PrefManager.getVal<Int>(PrefName.StandardCardRoundness).toFloat()
    private var cachedCardSize = PrefManager.getVal<Float>(PrefName.CardSize)
    private var cachedBannerAnimations = PrefManager.getVal<Boolean>(PrefName.AnimationsEnabled) && PrefManager.getVal<Boolean>(PrefName.BannerAnimations)
    private var cachedAnimationSpeed = PrefManager.getVal<Float>(PrefName.AnimationSpeed)
    private var cachedCardTitlePosition = PrefManager.getVal<Int>(PrefName.CardTitlePosition)
    private var cachedCardGradientIntensity = PrefManager.getVal<Float>(PrefName.CardGradientIntensity)

    fun refreshCache() {
        cachedCardRoundness = PrefManager.getVal<Int>(PrefName.StandardCardRoundness).toFloat()
        cachedCardSize = PrefManager.getVal(PrefName.CardSize)
        cachedBannerAnimations = PrefManager.getVal<Boolean>(PrefName.AnimationsEnabled) && PrefManager.getVal<Boolean>(PrefName.BannerAnimations)
        cachedAnimationSpeed = PrefManager.getVal(PrefName.AnimationSpeed)
        cachedCardTitlePosition = PrefManager.getVal(PrefName.CardTitlePosition)
        cachedCardGradientIntensity = PrefManager.getVal(PrefName.CardGradientIntensity)
        cachedAnimationSpeed = PrefManager.getVal(PrefName.AnimationSpeed)
    }

    init {
        if (type == 0) {
            rawCardStyle = PrefManager.getVal<Int>(PrefName.CardStyle)
            type = when (rawCardStyle) {
                0, 4, 6 -> 0
                1 -> 1
                2, 5 -> 2
                3 -> 3
                else -> 0
            }
        }
        isLandscape = if (type == 0) PrefManager.getVal<Int>(PrefName.CardOrientation) == 0 else false
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (type == 0 && isLandscape) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_media_compact_land, parent, false)
            return MediaLandscapeViewHolder(
                ani.sanin.databinding.ItemMediaCompactLandBinding.bind(view)
            )
        }
        return when (type) {
            0 -> MediaViewHolder(
                ItemMediaCompactBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

            1 -> MediaLargeViewHolder(
                ItemMediaLargeBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

            2 -> MediaPageViewHolder(
                ItemMediaPageBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

            3 -> MediaPageSmallViewHolder(
                ItemMediaPageSmallBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

            else -> throw IllegalArgumentException()
        }

    }

    private var logoJobs = mutableMapOf<Int, Job>()
    private var backdropJobs = mutableMapOf<Int, Job>()

    private fun bindLogo(
        clearlogo: ImageView,
        overlayTitle: TextView,
        media: Media,
        position: Int
    ) {
        logoJobs[position]?.cancel()
        logoJobs[position] = activity.lifecycleScope.launch(Dispatchers.Main) {
            val logoUrl = LogoApi.getLogoUrl(media.id)
            if (!logoUrl.isNullOrBlank()) {
                clearlogo.visibility = View.VISIBLE
                clearlogo.loadImage(logoUrl)
                overlayTitle.visibility = View.GONE
            } else {
                clearlogo.visibility = View.GONE
                overlayTitle.visibility = View.VISIBLE
                overlayTitle.text = media.userPreferredName
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val cardRoundness = cachedCardRoundness

        if (holder is MediaLandscapeViewHolder) {
            bindLandscape(holder, position, cardRoundness)
            return
        }
        when (type) {
             0 -> {
                val b = (holder as MediaViewHolder).binding
                val media = mediaList?.getOrNull(position)
                if (media != null) {
                    b.itemCompactImage.loadImage(media.cover)
                    val cardSize = cachedCardSize
                    val finalW = (102 * cardSize).toInt()
                    val finalH = (154 * cardSize).toInt()
                    b.itemCompactImage.updateLayoutParams {
                        width = finalW
                        height = finalH
                    }
                    val styleRadius = when (rawCardStyle) {
                        4 -> 24f
                        6 -> 4f
                        else -> cardRoundness
                    }
                    b.itemCompactCard.radius = styleRadius
                    b.itemCompactOngoing.isVisible =
                        media.status == currActivity()!!.getString(R.string.status_releasing)
                    b.itemCompactScore.text =
                        ((if (media.userScore == 0) (media.meanScore
                            ?: 0) else media.userScore) / 10.0).toString()
                    b.itemCompactScoreBG.background = ContextCompat.getDrawable(
                        b.root.context,
                        (if (media.userScore != 0) R.drawable.item_user_score else R.drawable.item_score)
                    )
                    b.itemCompactTitle.text = media.userPreferredName
                    if (media.anime != null) {
                        b.itemCompactUserProgress.text = (media.userProgress ?: "~").toString()
                        b.itemCompactTotal.text =
                            " | ${if (media.anime.nextAiringEpisode != null) (media.anime.nextAiringEpisode.toString() + " | " + (media.anime.totalEpisodes ?: "~").toString()) else (media.anime.totalEpisodes ?: "~").toString()}"
                        b.itemCompactProgressContainer.visibility =
                            if (media.isFav) View.GONE else View.VISIBLE
                    } else {
                        b.itemCompactProgressContainer.visibility = View.GONE
                    }
                }
            }

             1 -> {
                val b = (holder as MediaLargeViewHolder).binding
                val media = mediaList?.get(position)
                if (media != null) {
                    b.itemCompactImage.loadImage(media.cover)
                    blurImage(b.itemCompactBanner, media.banner ?: media.cover)
                    b.itemCompactOngoing.isVisible =
                        media.status == currActivity()!!.getString(R.string.status_releasing)
                    b.itemCompactTitle.text = media.userPreferredName
                    b.itemCompactScore.text =
                        ((if (media.userScore == 0) (media.meanScore
                            ?: 0) else media.userScore) / 10.0).toString()
                    b.itemCompactScoreBG.background = ContextCompat.getDrawable(
                        b.root.context,
                        (if (media.userScore != 0) R.drawable.item_user_score else R.drawable.item_score)
                    )
                    if (media.anime != null) {
                        val itemTotal = " " + if ((media.anime.totalEpisodes
                                ?: 0) != 1
                        ) currActivity()!!.getString(R.string.episode_plural) else currActivity()!!.getString(
                            R.string.episode_singular
                        )
                        b.itemTotal.text = itemTotal
                        b.itemCompactTotal.text =
                            if (media.anime.nextAiringEpisode != null) (media.anime.nextAiringEpisode.toString() + " / " + (media.anime.totalEpisodes
                                ?: "??").toString()) else (media.anime.totalEpisodes
                                ?: "??").toString()
                    }
                    if (position == mediaList.size - 2 && viewPager != null) viewPager.post {
                        val start = mediaList.size
                        mediaList.addAll(mediaList)
                        val end = mediaList.size - start
                        notifyItemRangeInserted(start, end)
                    }
                }
            }

            2 -> {
                val b = (holder as MediaPageViewHolder).binding
                val media = mediaList?.get(position)
                if (media != null) {

                    val bannerAnimations = cachedBannerAnimations
                    b.itemCompactImage.loadImage(media.cover)
                    if (bannerAnimations)
                        b.itemCompactBanner.setTransitionGenerator(
                            RandomTransitionGenerator(
                                (10000 + 15000 * cachedAnimationSpeed).toLong(),
                                AccelerateDecelerateInterpolator()
                            )
                        )
                    blurImage(
                        if (bannerAnimations) b.itemCompactBanner else b.itemCompactBannerNoKen,
                        media.banner ?: media.cover
                    )
                    b.itemCompactOngoing.isVisible =
                        media.status == currActivity()!!.getString(R.string.status_releasing)
                    b.itemCompactTitle.text = media.userPreferredName
                    b.itemCompactScore.text =
                        ((if (media.userScore == 0) (media.meanScore
                            ?: 0) else media.userScore) / 10.0).toString()
                    b.itemCompactScoreBG.background = ContextCompat.getDrawable(
                        b.root.context,
                        (if (media.userScore != 0) R.drawable.item_user_score else R.drawable.item_score)
                    )
                    if (media.anime != null) {
                        b.itemTotal.text = " " + if ((media.anime.totalEpisodes
                                ?: 0) != 1
                        ) currActivity()!!.getString(R.string.episode_plural)
                        else currActivity()!!.getString(R.string.episode_singular)
                        b.itemCompactTotal.text =
                            if (media.anime.nextAiringEpisode != null) (media.anime.nextAiringEpisode.toString() + " / " + (media.anime.totalEpisodes
                                ?: "??").toString()) else (media.anime.totalEpisodes
                                ?: "??").toString()
                    }
                    @SuppressLint("NotifyDataSetChanged")
                    if (position == mediaList!!.size - 2 && viewPager != null) viewPager.post {
                        val size = mediaList.size
                        mediaList.addAll(mediaList)
                        notifyItemRangeInserted(size - 1, mediaList.size)
                    }
                }
            }

            3 -> {
                val b = (holder as MediaPageSmallViewHolder).binding
                val media = mediaList?.get(position)
                if (media != null) {
                    val bannerAnimations = cachedBannerAnimations
                    b.itemCompactImage.loadImage(media.cover)
                    if (bannerAnimations)
                        b.itemCompactBanner.setTransitionGenerator(
                            RandomTransitionGenerator(
                                (10000 + 15000 * cachedAnimationSpeed).toLong(),
                                AccelerateDecelerateInterpolator()
                            )
                        )
                    blurImage(
                        if (bannerAnimations) b.itemCompactBanner else b.itemCompactBannerNoKen,
                        media.banner ?: media.cover
                    )
                    b.itemCompactOngoing.isVisible =
                        media.status == currActivity()!!.getString(R.string.status_releasing)
                    b.itemCompactTitle.text = media.userPreferredName
                    b.itemCompactScore.text =
                        ((if (media.userScore == 0) (media.meanScore
                            ?: 0) else media.userScore) / 10.0).toString()
                    b.itemCompactScoreBG.background = ContextCompat.getDrawable(
                        b.root.context,
                        (if (media.userScore != 0) R.drawable.item_user_score else R.drawable.item_score)
                    )
                    media.genres.apply {
                        if (isNotEmpty()) {
                            var genres = ""
                            forEach { genres += "$it • " }
                            genres = genres.removeSuffix(" • ")
                            b.itemCompactGenres.text = genres
                        }
                    }
                    b.itemCompactStatus.text = media.status ?: ""
                    if (media.anime != null) {
                        b.itemTotal.text = " " + if ((media.anime.totalEpisodes
                                ?: 0) != 1
                        ) currActivity()!!.getString(R.string.episode_plural)
                        else currActivity()!!.getString(R.string.episode_singular)
                        b.itemCompactTotal.text =
                            if (media.anime.nextAiringEpisode != null) (media.anime.nextAiringEpisode.toString() + " / " + (media.anime.totalEpisodes
                                ?: "??").toString()) else (media.anime.totalEpisodes
                                ?: "??").toString()
                    }
                    @SuppressLint("NotifyDataSetChanged")
                    if (position == mediaList!!.size - 2 && viewPager != null) viewPager.post {
                        val size = mediaList.size
                        mediaList.addAll(mediaList)
                        notifyItemRangeInserted(size - 1, mediaList.size)
                    }
                }
            }
        }
    }

    override fun getItemCount() = mediaList!!.size

    override fun getItemViewType(position: Int): Int {
        return type
    }

    fun randomOptionClick() {
        val media = if (!mediaList.isNullOrEmpty()) {
            mediaList.random()
        } else {
            null
        }
        media?.let {
            val index = mediaList?.indexOf(it) ?: -1
            clicked(index, null)
        }
    }

    private fun applyFocusWithFade(itemView: View, borderTarget: View) {
        FocusEffectUtil.applyFocusListener(itemView, borderTarget)
        val focusListener = itemView.onFocusChangeListener
        itemView.alpha = 0.85f
        itemView.setOnFocusChangeListener { v, hasFocus ->
            focusListener?.onFocusChange(v, hasFocus)
            if (PrefManager.getVal<Boolean>(PrefName.AnimationsEnabled) && PrefManager.getVal<Boolean>(PrefName.FocusAnimations)) {
                if (hasFocus) {
                    v.animate().alpha(1f).setDuration(200).start()
                } else {
                    v.animate().alpha(0.85f).setDuration(200).start()
                }
            } else {
                v.alpha = if (hasFocus) 1f else 0.85f
            }
        }
    }

    private fun setupDpadLongPress(view: View, callback: () -> Boolean) {
        val runnable = Runnable { callback() }
        view.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> {
                        view.postDelayed(runnable, ViewConfiguration.getLongPressTimeout().toLong())
                    }
                    KeyEvent.ACTION_UP -> {
                        view.removeCallbacks(runnable)
                    }
                }
            }
            false
        }
    }

    inner class MediaViewHolder(val binding: ItemMediaCompactBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            if (matchParent) itemView.updateLayoutParams { width = -1 }
            itemView.isFocusable = true
            itemView.isFocusableInTouchMode = false
            itemView.setSafeOnClickListener {
                clicked(
                    bindingAdapterPosition,
                    binding.itemCompactImage,
                    resizeBitmap(getBitmapFromImageView(binding.itemCompactImage), 100)
                )
            }
            itemView.setOnLongClickListener { longClicked(bindingAdapterPosition) }
            setupDpadLongPress(itemView) { longClicked(bindingAdapterPosition) }
            applyFocusWithFade(itemView, binding.itemCompactCard)
        }
    }

    inner class MediaLargeViewHolder(val binding: ItemMediaLargeBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            itemView.isFocusable = true
            itemView.isFocusableInTouchMode = false
            itemView.setSafeOnClickListener {
                clicked(
                    bindingAdapterPosition,
                    binding.itemCompactImage,
                    resizeBitmap(getBitmapFromImageView(binding.itemCompactImage), 100)
                )
            }
            itemView.setOnLongClickListener { longClicked(bindingAdapterPosition) }
            setupDpadLongPress(itemView) { longClicked(bindingAdapterPosition) }
            applyFocusWithFade(itemView, binding.itemCompactCard)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    inner class MediaPageViewHolder(val binding: ItemMediaPageBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            binding.itemCompactImage.setSafeOnClickListener {
                clicked(
                    bindingAdapterPosition,
                    binding.itemCompactImage,
                    resizeBitmap(getBitmapFromImageView(binding.itemCompactImage), 100)
                )
            }
            itemView.isFocusable = true
            itemView.isFocusableInTouchMode = false
            applyFocusWithFade(itemView, binding.itemCompactCard)
            itemView.setOnTouchListener { _, _ -> true }
            itemView.setOnLongClickListener { longClicked(bindingAdapterPosition) }
            setupDpadLongPress(itemView) { longClicked(bindingAdapterPosition) }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    inner class MediaPageSmallViewHolder(val binding: ItemMediaPageSmallBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            binding.itemCompactImage.setSafeOnClickListener {
                clicked(
                    bindingAdapterPosition,
                    binding.itemCompactImage,
                    resizeBitmap(getBitmapFromImageView(binding.itemCompactImage), 100)
                )
            }
            binding.itemCompactTitleContainer.setSafeOnClickListener {
                clicked(
                    bindingAdapterPosition,
                    binding.itemCompactImage,
                    resizeBitmap(getBitmapFromImageView(binding.itemCompactImage), 100)
                )
            }
            itemView.isFocusable = true
            itemView.isFocusableInTouchMode = false
            applyFocusWithFade(itemView, binding.itemCompactCard)
            itemView.setOnTouchListener { _, _ -> true }
            itemView.setOnLongClickListener { longClicked(bindingAdapterPosition) }
            setupDpadLongPress(itemView) { longClicked(bindingAdapterPosition) }
        }
    }

    inner class MediaLandscapeViewHolder(val binding: ItemMediaCompactLandBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            if (matchParent) itemView.updateLayoutParams { width = -1 }
            itemView.isFocusable = true
            itemView.isFocusableInTouchMode = false
            itemView.setSafeOnClickListener {
                clicked(
                    bindingAdapterPosition,
                    binding.itemCompactImage,
                    resizeBitmap(getBitmapFromImageView(binding.itemCompactImage), 100)
                )
            }
            itemView.setOnLongClickListener { longClicked(bindingAdapterPosition) }
            setupDpadLongPress(itemView) { longClicked(bindingAdapterPosition) }
            applyFocusWithFade(itemView, binding.itemCompactCard)
        }
    }

    private fun bindLandscape(
        holder: MediaLandscapeViewHolder,
        position: Int,
        cardRoundness: Float
    ) {
        val b = holder.binding
        val media = mediaList?.getOrNull(position)
        if (media != null) {
            b.itemCompactCard.radius = cardRoundness
            b.itemCompactOngoing.isVisible =
                media.status == currActivity()!!.getString(R.string.status_releasing)
            b.itemCompactScore.text =
                ((if (media.userScore == 0) (media.meanScore ?: 0) else media.userScore) / 10.0).toString()
            b.itemCompactScoreBG.background = ContextCompat.getDrawable(
                b.root.context,
                (if (media.userScore != 0) R.drawable.item_user_score else R.drawable.item_score)
            )

            b.itemCompactImage.scaleType = ImageView.ScaleType.CENTER_CROP
            b.itemCompactImage.loadImage(media.cover)
            backdropJobs[position]?.cancel()
            backdropJobs[position] = activity.lifecycleScope.launch(Dispatchers.IO) {
                val backdropUrl = AniZip.getBackdropUrl(media.id)
                if (backdropUrl != null) {
                    withContext(Dispatchers.Main) {
                        b.itemCompactImage.loadImage(backdropUrl)
                    }
                }
            }
            val titlePos = cachedCardTitlePosition
            when (titlePos) {
                0 -> {
                    b.itemCompactOverlay.visibility = View.VISIBLE
                    val intensity = cachedCardGradientIntensity
                    if (intensity <= 0f) {
                        b.itemCompactOverlay.background = null
                    } else {
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
                        b.itemCompactOverlay.background = gradient
                    }
                    b.itemCompactTitleBelow.visibility = View.GONE
                    logoJobs[position]?.cancel()
                    logoJobs[position] = activity.lifecycleScope.launch(Dispatchers.Main) {
                        val logoUrl = LogoApi.getLogoUrl(media.id)
                        if (!logoUrl.isNullOrBlank()) {
                            b.itemCompactClearlogo.visibility = View.VISIBLE
                            b.itemCompactClearlogo.loadImage(logoUrl)
                            b.itemCompactOverlayTitle.visibility = View.GONE
                        } else {
                            b.itemCompactClearlogo.visibility = View.GONE
                            b.itemCompactOverlayTitle.visibility = View.VISIBLE
                            b.itemCompactOverlayTitle.text = media.userPreferredName
                        }
                    }
                }
                1 -> {
                    b.itemCompactOverlay.visibility = View.GONE
                    b.itemCompactClearlogo.visibility = View.GONE
                    b.itemCompactOverlayTitle.visibility = View.GONE
                    b.itemCompactTitleBelow.visibility = View.VISIBLE
                    b.itemCompactTitleBelow.text = media.userPreferredName
                }
                else -> {
                    b.itemCompactOverlay.visibility = View.GONE
                    b.itemCompactClearlogo.visibility = View.GONE
                    b.itemCompactOverlayTitle.visibility = View.GONE
                    b.itemCompactTitleBelow.visibility = View.GONE
                }
            }
            b.itemCompactScoreBG.visibility = View.VISIBLE
            if (media.anime != null) {
                b.itemCompactUserProgress.text = (media.userProgress ?: "~").toString()
                b.itemCompactTotal.text =
                    " | ${if (media.anime.nextAiringEpisode != null) (media.anime.nextAiringEpisode.toString() + " | " + (media.anime.totalEpisodes ?: "~").toString()) else (media.anime.totalEpisodes ?: "~").toString()}"
                b.itemCompactProgressContainer.visibility =
                    if (media.isFav) View.GONE else View.VISIBLE
            } else {
                b.itemCompactProgressContainer.visibility = View.GONE
            }
        }
    }



    fun clicked(position: Int, itemCompactImage: ImageView?, bitmap: Bitmap? = null) {
        if ((mediaList?.size ?: 0) > position && position != -1) {
            val media = mediaList?.get(position)
            if (bitmap != null) MediaSingleton.bitmap = bitmap
            ContextCompat.startActivity(
                activity,
                Intent(activity, MediaDetailsActivity::class.java).putExtra(
                    "media",
                    media as Serializable
                ),
                if (itemCompactImage != null) {
                    ActivityOptionsCompat.makeSceneTransitionAnimation(
                        activity,
                        itemCompactImage,
                        ViewCompat.getTransitionName(itemCompactImage)!!
                    ).toBundle()
                } else {
                    null
                }
            )
        }
    }


    fun longClicked(position: Int): Boolean {
        if (isOtherUser) return false
        if ((mediaList?.size ?: 0) > position && position != -1) {
            val media = mediaList?.get(position) ?: return false
            if (activity.supportFragmentManager.findFragmentByTag("list") == null) {
                MediaListDialogSmallFragment.newInstance(media)
                    .show(activity.supportFragmentManager, "list")
                return true
            }
        }
        return false
    }

    fun getBitmapFromImageView(imageView: ImageView): Bitmap? {
        val drawable = imageView.drawable ?: return null

        // If the drawable is a BitmapDrawable, then just get the bitmap
        if (drawable is BitmapDrawable) {
            return drawable.bitmap
        }

        // Create a bitmap with the same dimensions as the drawable
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )

        // Draw the drawable onto the bitmap
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)

        return bitmap
    }

    fun resizeBitmap(source: Bitmap?, maxDimension: Int): Bitmap? {
        if (source == null) return null
        val width = source.width
        val height = source.height
        val newWidth: Int
        val newHeight: Int

        if (width > height) {
            newWidth = maxDimension
            newHeight = (height * (maxDimension.toFloat() / width)).toInt()
        } else {
            newHeight = maxDimension
            newWidth = (width * (maxDimension.toFloat() / height)).toInt()
        }

        return Bitmap.createScaledBitmap(source, newWidth, newHeight, true)
    }

}
