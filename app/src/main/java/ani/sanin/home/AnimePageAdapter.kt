package ani.sanin.home

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.view.animation.LayoutAnimationController
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import ani.sanin.home.BannerCarouselAdapter
import ani.sanin.R
import ani.sanin.connections.anilist.Anilist
import ani.sanin.connections.anizip.AniZip
import ani.sanin.connections.mal.MAL
import ani.sanin.databinding.ItemAnimePageBinding
import ani.sanin.databinding.LayoutTrendingBinding
import ani.sanin.getAppString
import ani.sanin.getThemeColor
import ani.sanin.loadImage
import ani.sanin.media.Media
import ani.sanin.media.MediaAdaptor
import ani.sanin.media.MediaListViewActivity
import ani.sanin.openLinkInCustomTab
import ani.sanin.profile.ProfileActivity
import ani.sanin.px
import ani.sanin.setSafeOnClickListener
import ani.sanin.setSlideIn
import ani.sanin.setSlideUp
import ani.sanin.settings.SettingsDialogFragment
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.statusBarHeight
import ani.sanin.util.FocusEffectUtil
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AnimePageAdapter : RecyclerView.Adapter<AnimePageAdapter.AnimePageViewHolder>() {
    val ready = MutableLiveData(false)
    lateinit var binding: ItemAnimePageBinding
    private lateinit var trendingBinding: LayoutTrendingBinding
    var bannerAdapter: BannerCarouselAdapter? = null
    private var bannerSnap: PagerSnapHelper? = null
    private var trendingAutoScrollHandler: android.os.Handler? = null
    private var trendingAutoScrollRunnable: Runnable? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AnimePageViewHolder {
        val binding =
            ItemAnimePageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AnimePageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AnimePageViewHolder, position: Int) {
        binding = holder.binding
        trendingBinding = LayoutTrendingBinding.bind(binding.root)
        trendingBinding.trendingViewPager.overScrollMode = RecyclerView.OVER_SCROLL_NEVER

        trendingBinding.titleContainer.updatePadding(top = statusBarHeight)

        if (PrefManager.getVal(PrefName.SmallView)) trendingBinding.trendingContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = (-108f).px
        }

        listOf(
            binding.animePreviousSeason,
            binding.animeThisSeason,
            binding.animeNextSeason
        ).forEachIndexed { i, it ->
            it.setSafeOnClickListener { onSeasonClick.invoke(i) }
            it.setOnLongClickListener { onSeasonLongClick.invoke(i) }
            FocusEffectUtil.applyFocusListener(it)
        }

        val rescueMode = PrefManager.getVal<Boolean>(PrefName.RescueMode)
        binding.animeIncludeList.isVisible = if (rescueMode) MAL.token != null else Anilist.token != null

        binding.animeIncludeList.isChecked = PrefManager.getVal(PrefName.PopularAnimeList)

        binding.animeIncludeList.setOnCheckedChangeListener { _, isChecked ->
            onIncludeListClick.invoke(isChecked)

            PrefManager.setVal(PrefName.PopularAnimeList, isChecked)
        }
        if (ready.value == false)
            ready.postValue(true)
    }

    lateinit var onSeasonClick: ((Int) -> Unit)
    lateinit var onSeasonLongClick: ((Int) -> Boolean)
    lateinit var onIncludeListClick: ((Boolean) -> Unit)

    override fun getItemCount(): Int = 1

    fun updateHeight() {
        trendingBinding.trendingViewPager.updateLayoutParams { height += statusBarHeight }
    }

    fun updateTrending(media: List<Media>) {
        trendingBinding.trendingProgressBar.visibility = View.GONE
        val rv = trendingBinding.trendingViewPager
        rv.layoutManager = LinearLayoutManager(rv.context, LinearLayoutManager.HORIZONTAL, false)
        bannerSnap?.let { it.attachToRecyclerView(null) }
        bannerSnap = PagerSnapHelper()
        bannerSnap?.attachToRecyclerView(rv)
        rv.overScrollMode = RecyclerView.OVER_SCROLL_NEVER
        rv.isFocusable = true
        rv.descendantFocusability = android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS
        rv.nextFocusDownId = R.id.animeSeasons
        val scope = CoroutineScope(Dispatchers.Main)
        bannerAdapter = BannerCarouselAdapter(
            media, scope, { item ->
                val context = binding.root.context
                ContextCompat.startActivity(
                    context,
                    Intent(context, ani.sanin.media.MediaDetailsActivity::class.java)
                        .putExtra("media", item)
                        .putExtra("anime", true),
                    null
                )
            },
            nextFocusDownId = R.id.animeSeasons
        )
        rv.adapter = bannerAdapter
        val start = Int.MAX_VALUE / 2 - (Int.MAX_VALUE / 2 % media.size)
        rv.scrollToPosition(start)
        setupTrendingDots(rv, media.size)
        rv.layoutAnimation = LayoutAnimationController(setSlideIn(), 0.25f)
        trendingBinding.titleContainer.startAnimation(setSlideUp())
        binding.animeSeasonsCont.layoutAnimation =
            LayoutAnimationController(setSlideIn(), 0.25f)
        trendingAutoScrollHandler?.removeCallbacksAndMessages(null)
        trendingAutoScrollHandler = android.os.Handler(android.os.Looper.getMainLooper())
        trendingAutoScrollRunnable = object : Runnable {
            private var currentIndex = start
            override fun run() {
                if (media.isEmpty()) return
                currentIndex++
                rv.smoothScrollToPosition(currentIndex)
                trendingAutoScrollHandler?.postDelayed(this, 5000L)
            }
        }
        trendingAutoScrollHandler?.postDelayed(trendingAutoScrollRunnable!!, 5000L)

        scope.launch(Dispatchers.IO) {
            val allImages = AniZip.getImagesBatch(media.map { it.id })
            val backdrops = allImages.mapValues { it.value.backdropUrl }
            val logos = allImages.mapValues { it.value.logoUrl }
            withContext(Dispatchers.Main) {
                bannerAdapter?.updateUrls(backdrops, logos)
            }
        }
    }

    private fun setupTrendingDots(rv: RecyclerView, itemCount: Int) {
        val dots = trendingBinding.trendingDots
        dots.removeAllViews()
        val density = rv.context.resources.displayMetrics.density
        val dotsList = mutableListOf<View>()
        for (i in 0 until itemCount) {
            val dot = View(rv.context)
            val w = if (i == 0) (32 * density).toInt() else (12 * density).toInt()
            val lp = LinearLayout.LayoutParams(w, (4 * density).toInt())
            lp.marginEnd = (6 * density).toInt()
            dot.layoutParams = lp
            dot.background = if (i == 0)
                ContextCompat.getDrawable(rv.context, R.drawable.banner_dot_active)
            else
                ContextCompat.getDrawable(rv.context, R.drawable.banner_dot_inactive)
            dot.setOnClickListener {
                val lm = rv.layoutManager as LinearLayoutManager
                val current = lm.findFirstVisibleItemPosition()
                val currentReal = current % itemCount
                if (i == currentReal) return@setOnClickListener
                if (i > currentReal) rv.smoothScrollToPosition(current + (i - currentReal))
                else rv.smoothScrollToPosition(current - (currentReal - i))
            }
            dots.addView(dot)
            dotsList.add(dot)
        }
        dots.visibility = View.VISIBLE

        rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val lm = rv.layoutManager as LinearLayoutManager
                    val pos = lm.findFirstVisibleItemPosition() % itemCount
                    for (i in 0 until dotsList.size) {
                        val dot = dotsList[i]
                        val lp = dot.layoutParams
                        lp.width = if (i == pos) (32 * density).toInt() else (12 * density).toInt()
                        dot.layoutParams = lp
                        dot.background = if (i == pos)
                            ContextCompat.getDrawable(rv.context, R.drawable.banner_dot_active)
                        else
                            ContextCompat.getDrawable(rv.context, R.drawable.banner_dot_inactive)
                    }
                }
            }
        })
    }

    fun updateRecent(adaptor: MediaAdaptor, media: MutableList<Media>) {
        binding.apply {
            init(
                adaptor,
                animeUpdatedRecyclerView,
                animeUpdatedProgressBar,
                animeRecently,
                animeRecentlyMore,
                getAppString(R.string.updated),
                media
            )
            animePopular.visibility = View.VISIBLE
            animePopular.startAnimation(setSlideUp())
            if (adaptor.itemCount == 0) {
                animeRecentlyContainer.visibility = View.GONE
            }
        }

    }

    fun updateMovies(adaptor: MediaAdaptor, media: MutableList<Media>) {
        binding.apply {
            init(
                adaptor,
                animeMoviesRecyclerView,
                animeMoviesProgressBar,
                animeMovies,
                animeMoviesMore,
                getAppString(R.string.trending_movies),
                media
            )
        }
    }

    fun updateTopRated(adaptor: MediaAdaptor, media: MutableList<Media>) {
        binding.apply {
            init(
                adaptor,
                animeTopRatedRecyclerView,
                animeTopRatedProgressBar,
                animeTopRated,
                animeTopRatedMore,
                getAppString(R.string.top_rated),
                media
            )
        }
    }

    fun updateMostFav(adaptor: MediaAdaptor, media: MutableList<Media>) {
        binding.apply {
            init(
                adaptor,
                animeMostFavRecyclerView,
                animeMostFavProgressBar,
                animeMostFav,
                animeMostFavMore,
                getAppString(R.string.most_favourite),
                media
            )
        }
    }

    fun init(
        adaptor: MediaAdaptor,
        recyclerView: RecyclerView,
        progress: View,
        title: View,
        more: View,
        string: String,
        media: MutableList<Media>
    ) {
        progress.visibility = View.GONE
        recyclerView.adapter = adaptor
        recyclerView.layoutManager =
            LinearLayoutManager(
                recyclerView.context,
                LinearLayoutManager.HORIZONTAL,
                false
            )

        more.setOnClickListener {
            MediaListViewActivity.passedMedia = media.toCollection(ArrayList())
            ContextCompat.startActivity(
                it.context, Intent(it.context, MediaListViewActivity::class.java)
                    .putExtra("title", string),
                null
            )
        }
        recyclerView.visibility = View.VISIBLE
        title.visibility = View.VISIBLE
        more.visibility = View.VISIBLE
        title.startAnimation(setSlideUp())
        more.startAnimation(setSlideUp())
        recyclerView.layoutAnimation =
            LayoutAnimationController(setSlideIn(), 0.25f)
    }

    inner class AnimePageViewHolder(val binding: ItemAnimePageBinding) :
        RecyclerView.ViewHolder(binding.root)
}
