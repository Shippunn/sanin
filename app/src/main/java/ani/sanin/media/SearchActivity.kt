package ani.sanin.media

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.view.updatePaddingRelative
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ani.sanin.connections.anilist.AniMangaSearchResults
import ani.sanin.connections.anilist.Anilist
import ani.sanin.connections.anilist.AnilistSearch
import ani.sanin.connections.anilist.AnilistSearch.SearchType
import ani.sanin.connections.anilist.CharacterSearchResults
import ani.sanin.connections.anilist.StaffSearchResults
import ani.sanin.connections.anilist.StudioSearchResults
import ani.sanin.connections.anilist.UserSearchResults
import ani.sanin.databinding.ActivitySearchBinding
import ani.sanin.initActivity
import ani.sanin.navBarHeight
import ani.sanin.profile.UsersAdapter
import ani.sanin.profile.User
import ani.sanin.px
import ani.sanin.R
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.statusBarHeight
import ani.sanin.themes.ThemeManager
import ani.sanin.util.Logger
import ani.sanin.util.TvKeyboardUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Timer
import java.util.TimerTask

class SearchActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySearchBinding
    private val scope = lifecycleScope
    val model: AnilistSearch by viewModels()

    var style: Int = 0
    lateinit var searchType: SearchType
    private var screenWidth: Float = 0f

    private lateinit var mediaAdaptor: MediaAdaptor
    private lateinit var characterAdaptor: CharacterAdapter
    private lateinit var studioAdaptor: StudioAdapter
    private lateinit var staffAdaptor: AuthorAdapter
    private lateinit var usersAdapter: UsersAdapter

    private lateinit var progressAdapter: ProgressAdapter
    private lateinit var concatAdapter: ConcatAdapter
    private lateinit var headerAdaptor: HeaderInterface

    lateinit var aniMangaResult: AniMangaSearchResults
    lateinit var characterResult: CharacterSearchResults
    lateinit var studioResult: StudioSearchResults
    lateinit var staffResult: StaffSearchResults
    lateinit var userResult: UserSearchResults

    lateinit var updateChips: (() -> Unit)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeManager(this).applyTheme()
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val header = binding.searchHeader
                val searchBarText = header.searchBarText
                val keyboardVisible = TvKeyboardUtil.isCompactKeyboardVisible(searchBarText)
                Logger.log(Log.INFO, "SearchActivity BACK: keyboardVisible=$keyboardVisible focused=${currentFocus?.let { it.javaClass.simpleName + "#" + it.id }}", "TvKeyboard")
                if (keyboardVisible) {
                    searchBarText.clearFocus()
                    header.searchFilter.post { header.searchFilter.requestFocus() }
                    TvKeyboardUtil.hideCompactKeyboard(searchBarText)
                    return
                }
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        })
        initActivity(this)
        screenWidth = resources.displayMetrics.run { widthPixels / density }

        binding.root.updatePaddingRelative(top = statusBarHeight)
        binding.searchRecyclerView.updatePaddingRelative(
            bottom = navBarHeight + 80f.px
        )

        val notSet = model.notSet
        searchType = SearchType.fromString(intent.getStringExtra("type") ?: "ANIME")
        when (searchType) {
            SearchType.ANIME, SearchType.MANGA -> {
                style = PrefManager.getVal(PrefName.SearchStyle)
                var listOnly: Boolean? = intent.getBooleanExtra("listOnly", false)
                if (!listOnly!!) listOnly = null

                if (model.notSet) {
                    model.notSet = false
                    model.aniMangaSearchResults = AniMangaSearchResults(
                        intent.getStringExtra("type") ?: "ANIME",
                        isAdult = if (Anilist.adult) intent.getBooleanExtra(
                            "hentai",
                            false
                        ) else false,
                        onList = listOnly,
                        search = intent.getStringExtra("query"),
                        genres = intent.getStringExtra("genre")?.let { mutableListOf(it) },
                        tags = intent.getStringExtra("tag")?.let { mutableListOf(it) },
                        sort = intent.getStringExtra("sortBy"),
                        status = intent.getStringExtra("status"),
                        source = intent.getStringExtra("source"),
                        countryOfOrigin = intent.getStringExtra("country"),
                        season = intent.getStringExtra("season"),
                        seasonYear = if (intent.getStringExtra("type") == "ANIME") intent.getStringExtra(
                            "seasonYear"
                        )
                            ?.toIntOrNull() else null,
                        startYear = if (intent.getStringExtra("type") == "MANGA") intent.getStringExtra(
                            "seasonYear"
                        )
                            ?.toIntOrNull() else null,
                        results = mutableListOf(),
                        hasNextPage = false
                    )
                }

                aniMangaResult = model.aniMangaSearchResults
                mediaAdaptor =
                    MediaAdaptor(
                        style,
                        model.aniMangaSearchResults.results,
                        this,
                        matchParent = true
                    )
            }

            SearchType.CHARACTER -> {
                if (model.notSet) {
                    model.notSet = false
                    model.characterSearchResults = CharacterSearchResults(
                        search = intent.getStringExtra("query"),
                        results = mutableListOf(),
                        hasNextPage = false
                    )
                }
                characterResult = model.characterSearchResults
                characterAdaptor = CharacterAdapter(model.characterSearchResults.results as MutableList<Character>)
            }

            SearchType.STUDIO -> {
                if (model.notSet) {
                    model.notSet = false
                    model.studioSearchResults = StudioSearchResults(
                        search = intent.getStringExtra("query"),
                        results = mutableListOf(),
                        hasNextPage = false
                    )
                }
                studioResult = model.studioSearchResults
                studioAdaptor = StudioAdapter(model.studioSearchResults.results as MutableList<Studio>)
            }

            SearchType.STAFF -> {
                if (model.notSet) {
                    model.notSet = false
                    model.staffSearchResults = StaffSearchResults(
                        search = intent.getStringExtra("query"),
                        results = mutableListOf(),
                        hasNextPage = false
                    )
                }
                staffResult = model.staffSearchResults
                staffAdaptor = AuthorAdapter(model.staffSearchResults.results as MutableList<Author>)
            }

            SearchType.USER -> {
                if (model.notSet) {
                    model.notSet = false
                    model.userSearchResults = UserSearchResults(
                        search = intent.getStringExtra("query"),
                        results = mutableListOf(),
                        hasNextPage = false
                    )
                }
                userResult = model.userSearchResults
                usersAdapter = UsersAdapter(model.userSearchResults.results as MutableList<User>, grid = true)
            }
        }

        progressAdapter = ProgressAdapter(searched = model.searched)
        headerAdaptor = if (searchType == SearchType.ANIME || searchType == SearchType.MANGA) {
            SearchAdapter(this, searchType, binding.searchHeader)
        } else {
            SupportingSearchAdapter(this, searchType, binding.searchHeader)
        }
        headerAdaptor.bind()

        val gridSize = (screenWidth / 120f).toInt().coerceIn(3, 6)
        val gridLayoutManager = GridLayoutManager(this, gridSize)
        gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return when (position) {
                    concatAdapter.itemCount - 1 -> gridSize
                    else -> when (style) {
                        0 -> 1
                        else -> gridSize
                    }
                }
            }
        }

        concatAdapter = when (searchType) {
            SearchType.ANIME, SearchType.MANGA -> {
                ConcatAdapter(mediaAdaptor, progressAdapter)
            }

            SearchType.CHARACTER -> {
                ConcatAdapter(characterAdaptor, progressAdapter)
            }

            SearchType.STUDIO -> {
                ConcatAdapter(studioAdaptor, progressAdapter)
            }

            SearchType.STAFF -> {
                ConcatAdapter(staffAdaptor, progressAdapter)
            }

            SearchType.USER -> {
                ConcatAdapter(usersAdapter, progressAdapter)
            }
        }

        binding.searchRecyclerView.layoutManager = gridLayoutManager
        binding.searchRecyclerView.adapter = concatAdapter

        binding.searchRecyclerView.addOnScrollListener(object :
            RecyclerView.OnScrollListener() {
            override fun onScrolled(v: RecyclerView, dx: Int, dy: Int) {
                if (!v.canScrollVertically(1)) {
                    if (model.hasNextPage(searchType) && model.resultsIsNotEmpty(searchType) && !loading) {
                        scope.launch(Dispatchers.IO) {
                            model.loadNextPage(searchType)
                        }
                    }
                }
                super.onScrolled(v, dx, dy)
            }
        })

        when (searchType) {
            SearchType.ANIME, SearchType.MANGA -> {
                model.getSearch<AniMangaSearchResults>(searchType).observe(this) {
                    if (it != null) {
                        model.aniMangaSearchResults.apply {
                            onList = it.onList
                            isAdult = it.isAdult
                            perPage = it.perPage
                            search = it.search
                            sort = it.sort
                            genres = it.genres
                            excludedGenres = it.excludedGenres
                            excludedTags = it.excludedTags
                            tags = it.tags
                            season = it.season
                            startYear = it.startYear
                            seasonYear = it.seasonYear
                            status = it.status
                            source = it.source
                            format = it.format
                            countryOfOrigin = it.countryOfOrigin
                            page = it.page
                            hasNextPage = it.hasNextPage
                        }

                        val prev = model.aniMangaSearchResults.results.size
                        val newResults = it.results.distinctBy { it.id }.filter { newItem -> model.aniMangaSearchResults.results.none { oldItem -> oldItem.id == newItem.id } }
                        model.aniMangaSearchResults.results.addAll(newResults)
                        mediaAdaptor.notifyItemRangeInserted(prev, newResults.size)

                        progressAdapter.bar?.isVisible = it.hasNextPage
                    }
                }
            }

            SearchType.CHARACTER -> {
                model.getSearch<CharacterSearchResults>(searchType).observe(this) {
                    if (it != null) {
                        model.characterSearchResults.apply {
                            search = it.search
                            page = it.page
                            hasNextPage = it.hasNextPage
                        }

                        val prev = model.characterSearchResults.results.size
                        @Suppress("UNCHECKED_CAST")
                        val newResults = (it.results as List<Character>).distinctBy { it.id }.filter { newItem -> (model.characterSearchResults.results as List<Character>).none { oldItem -> oldItem.id == newItem.id } }
                        model.characterSearchResults.results.addAll(newResults)
                        characterAdaptor.notifyItemRangeInserted(prev, newResults.size)

                        progressAdapter.bar?.isVisible = it.hasNextPage
                    }
                }
            }

            SearchType.STUDIO -> {
                model.getSearch<StudioSearchResults>(searchType).observe(this) {
                    if (it != null) {
                        model.studioSearchResults.apply {
                            search = it.search
                            page = it.page
                            hasNextPage = it.hasNextPage
                        }

                        val prev = model.studioSearchResults.results.size
                        @Suppress("UNCHECKED_CAST")
                        val newResults = (it.results as List<Studio>).distinctBy { it.id }.filter { newItem -> (model.studioSearchResults.results as List<Studio>).none { oldItem -> oldItem.id == newItem.id } }
                        model.studioSearchResults.results.addAll(newResults)
                        studioAdaptor.notifyItemRangeInserted(prev, newResults.size)

                        progressAdapter.bar?.isVisible = it.hasNextPage
                    }
                }
            }

            SearchType.STAFF -> {
                model.getSearch<StaffSearchResults>(searchType).observe(this) {
                    if (it != null) {
                        model.staffSearchResults.apply {
                            search = it.search
                            page = it.page
                            hasNextPage = it.hasNextPage
                        }

                        val prev = model.staffSearchResults.results.size
                        @Suppress("UNCHECKED_CAST")
                        val newResults = (it.results as List<Author>).distinctBy { it.id }.filter { newItem -> (model.staffSearchResults.results as List<Author>).none { oldItem -> oldItem.id == newItem.id } }
                        model.staffSearchResults.results.addAll(newResults)
                        staffAdaptor.notifyItemRangeInserted(prev, newResults.size)

                        progressAdapter.bar?.isVisible = it.hasNextPage
                    }
                }
            }

            SearchType.USER -> {
                model.getSearch<UserSearchResults>(searchType).observe(this) {
                    if (it != null) {
                        model.userSearchResults.apply {
                            search = it.search
                            page = it.page
                            hasNextPage = it.hasNextPage
                        }

                        val prev = model.userSearchResults.results.size
                        @Suppress("UNCHECKED_CAST")
                        val newResults = (it.results as List<User>).distinctBy { it.id }.filter { newItem -> (model.userSearchResults.results as List<User>).none { oldItem -> oldItem.id == newItem.id } }
                        model.userSearchResults.results.addAll(newResults)
                        usersAdapter.notifyItemRangeInserted(prev, newResults.size)

                        progressAdapter.bar?.isVisible = it.hasNextPage
                    }
                }
            }
        }

        binding.searchRecyclerView.post { runInitialSearchActions(notSet) }
    }

    fun emptyMediaAdapter() {
        searchTimer.cancel()
        searchTimer.purge()
        // Same as search(): forget the focused card before removing it, so the next
        // result set doesn't restore/scroll back to its old position.
        binding.searchRecyclerView.clearFocus()
        when (searchType) {
            SearchType.ANIME, SearchType.MANGA -> {
                mediaAdaptor.notifyItemRangeRemoved(0, model.aniMangaSearchResults.results.size)
                model.aniMangaSearchResults.results.clear()
            }

            SearchType.CHARACTER -> {
                characterAdaptor.notifyItemRangeRemoved(
                    0,
                    model.characterSearchResults.results.size
                )
                model.characterSearchResults.results.clear()
            }

            SearchType.STUDIO -> {
                studioAdaptor.notifyItemRangeRemoved(0, model.studioSearchResults.results.size)
                model.studioSearchResults.results.clear()
            }

            SearchType.STAFF -> {
                staffAdaptor.notifyItemRangeRemoved(0, model.staffSearchResults.results.size)
                model.staffSearchResults.results.clear()
            }

            SearchType.USER -> {
                usersAdapter.notifyItemRangeRemoved(0, model.userSearchResults.results.size)
                model.userSearchResults.results.clear()
            }
        }
        progressAdapter.bar?.visibility = View.GONE
    }

    private var searchTimer = Timer()
    private var loading = false
    fun search() {
        headerAdaptor.setHistoryVisibility(false)
        val size = model.size(searchType)
        model.clearResults(searchType)
        // Drop any focused card before the old results are removed, otherwise RecyclerView
        // restores that position (and scrolls to it) after the new results are laid out.
        binding.searchRecyclerView.clearFocus()
        binding.searchRecyclerView.post {
            when (searchType) {
                SearchType.ANIME, SearchType.MANGA -> {
                    mediaAdaptor.notifyItemRangeRemoved(0, size)
                }

                SearchType.CHARACTER -> {
                    characterAdaptor.notifyItemRangeRemoved(0, size)
                }

                SearchType.STUDIO -> {
                    studioAdaptor.notifyItemRangeRemoved(0, size)
                }

                SearchType.STAFF -> {
                    staffAdaptor.notifyItemRangeRemoved(0, size)
                }

                SearchType.USER -> {
                    usersAdapter.notifyItemRangeRemoved(0, size)
                }
            }
            binding.searchRecyclerView.scrollToPosition(0)
        }

        progressAdapter.bar?.visibility = View.VISIBLE

        searchTimer.cancel()
        searchTimer.purge()
        val timerTask: TimerTask = object : TimerTask() {
            override fun run() {
                scope.launch(Dispatchers.IO) {
                    loading = true
                    model.loadSearch(searchType)
                    loading = false
                }
            }
        }
        searchTimer = Timer()
        searchTimer.schedule(timerTask, 500)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun recycler() {
        if (searchType == SearchType.ANIME || searchType == SearchType.MANGA) {
            mediaAdaptor.type = style
            mediaAdaptor.refreshCache()
            mediaAdaptor.notifyDataSetChanged()
        }
    }

    private fun runInitialSearchActions(notSet: Boolean) {
        if (isFinishing || isDestroyed) return

        if (!notSet) {
            if (!model.searched) {
                model.searched = true
                headerAdaptor.search?.run()
            }
        } else {
            headerAdaptor.requestFocus?.run()
        }

        if (intent.getBooleanExtra("search", false)) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED)
            search()
        }
    }

    var state: Parcelable? = null
    override fun onPause() {
        if (this::headerAdaptor.isInitialized) {
            headerAdaptor.addHistory()
        }
        super.onPause()
        state = binding.searchRecyclerView.layoutManager?.onSaveInstanceState()
    }

    override fun onResume() {
        super.onResume()
        binding.searchRecyclerView.layoutManager?.onRestoreInstanceState(state)
    }
}
