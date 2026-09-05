package com.watchit.fragments

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.watchit.R
import com.watchit.activities.MovieDetailsActivity
import com.watchit.activities.SeriesDetailsActivity
import com.watchit.activities.VideoPlayerActivity
import com.watchit.adapters.*
import com.watchit.databinding.FragmentHomeBinding
import com.watchit.databinding.FragmentMoviesBinding
import com.watchit.databinding.FragmentSeriesBinding
import com.watchit.databinding.FragmentLiveTvBinding
import com.watchit.databinding.FragmentFavoriteBinding
import com.watchit.models.*
import com.watchit.viewmodels.HomeViewModel
import com.watchit.viewmodels.MoviesViewModel
import com.watchit.viewmodels.SeriesViewModel
import com.watchit.viewmodels.LiveTVViewModel
import com.watchit.PreferenceManager
import java.util.Timer
import java.util.TimerTask

// ═══════════════════════════════════════════════════════════════════════════════
//  HOME FRAGMENT
// ═══════════════════════════════════════════════════════════════════════════════
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()

    private lateinit var bannerAdapter: BannerAdapter
    private lateinit var movieAdapter: MovieAdapter
    private lateinit var seriesAdapter: SeriesAdapter
    private lateinit var channelAdapter: ChannelGridAdapter
    private lateinit var starAdapter: StarAdapter
    private lateinit var continueWatchingAdapter: ContinueWatchingAdapter

    private var bannerTimer: Timer? = null
    private var currentBannerPos = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAdapters()
        observeViewModel()
        setupSwipeRefresh()
    }

    private fun setupAdapters() {
        bannerAdapter = BannerAdapter { }
        binding.viewPagerBanner.adapter = bannerAdapter
        binding.dotsIndicator.setViewPager2(binding.viewPagerBanner)

        movieAdapter = MovieAdapter { movie ->
            startActivity(Intent(requireContext(), MovieDetailsActivity::class.java).apply {
                putExtra(MovieDetailsActivity.EXTRA_MOVIE, movie)
            })
        }
        binding.rvPopularMovies.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvPopularMovies.adapter = movieAdapter

        starAdapter = StarAdapter()
        binding.rvStars.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvStars.adapter = starAdapter

        channelAdapter = ChannelGridAdapter { channel ->
            startActivity(Intent(requireContext(), VideoPlayerActivity::class.java).apply {
                putExtra(VideoPlayerActivity.EXTRA_STREAM_URL, channel.streamUrl)
                putExtra(VideoPlayerActivity.EXTRA_CONTENT_ID, channel.id)
                putExtra(VideoPlayerActivity.EXTRA_TITLE, channel.name)
                putExtra(VideoPlayerActivity.EXTRA_POSTER, channel.logo)
                putExtra(VideoPlayerActivity.EXTRA_TYPE, "live")
            })
        }
        binding.rvChannels.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.rvChannels.adapter = channelAdapter

        continueWatchingAdapter = ContinueWatchingAdapter { item ->
            startActivity(Intent(requireContext(), VideoPlayerActivity::class.java).apply {
                putExtra(VideoPlayerActivity.EXTRA_STREAM_URL, item.streamUrl)
                putExtra(VideoPlayerActivity.EXTRA_CONTENT_ID, item.id)
                putExtra(VideoPlayerActivity.EXTRA_TITLE, item.title)
                putExtra(VideoPlayerActivity.EXTRA_POSTER, item.poster)
                putExtra(VideoPlayerActivity.EXTRA_TYPE, item.type)
            })
        }
        binding.rvContinueWatching.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvContinueWatching.adapter = continueWatchingAdapter

        seriesAdapter = SeriesAdapter { series ->
            startActivity(Intent(requireContext(), SeriesDetailsActivity::class.java).apply {
                putExtra(SeriesDetailsActivity.EXTRA_SERIES, series)
            })
        }
        binding.rvTrendingSeries.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvTrendingSeries.adapter = seriesAdapter
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.shimmerHome.visibility = if (loading) View.VISIBLE else View.GONE
            binding.scrollContent.visibility = if (loading) View.GONE else View.VISIBLE
            if (loading) binding.shimmerHome.startShimmer() else binding.shimmerHome.stopShimmer()
        }
        viewModel.banners.observe(viewLifecycleOwner) { banners ->
            bannerAdapter.submitList(banners)
            startBannerAutoSlide(banners.size)
        }
        viewModel.popularMovies.observe(viewLifecycleOwner)  { movieAdapter.submitList(it) }
        viewModel.trendingSeries.observe(viewLifecycleOwner) { seriesAdapter.submitList(it) }
        viewModel.featuredChannels.observe(viewLifecycleOwner) { channelAdapter.submitList(it) }
        viewModel.popularStars.observe(viewLifecycleOwner)  { starAdapter.submitList(it) }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeResources(R.color.primary_red)
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadData(forceRefresh = true)
        }
        // Loading শেষ হলে spinner বন্ধ করো
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            if (!loading) binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun startBannerAutoSlide(size: Int) {
        bannerTimer?.cancel()
        if (size <= 1) return
        bannerTimer = Timer()
        bannerTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                activity?.runOnUiThread {
                    currentBannerPos = (currentBannerPos + 1) % size
                    binding.viewPagerBanner.setCurrentItem(currentBannerPos, true)
                }
            }
        }, 4000L, 4000L)
    }

    override fun onResume() {
        super.onResume()
        val list = PreferenceManager.getContinueWatchingList(requireContext())
        continueWatchingAdapter.submitList(list)
        binding.layoutContinueWatching.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        bannerTimer?.cancel()
        super.onDestroyView()
        _binding = null
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  MOVIES FRAGMENT  — Genre + Year chip filter সহ
// ═══════════════════════════════════════════════════════════════════════════════
class MoviesFragment : Fragment() {

    private var _binding: FragmentMoviesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MoviesViewModel by viewModels()
    private lateinit var adapter: MovieAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMoviesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ── RecyclerView setup ─────────────────────────────────────────────
        adapter = MovieAdapter { movie ->
            startActivity(Intent(requireContext(), MovieDetailsActivity::class.java).apply {
                putExtra(MovieDetailsActivity.EXTRA_MOVIE, movie)
            })
        }
        binding.rvMovies.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.rvMovies.adapter = adapter

        // ── Loading state ──────────────────────────────────────────────────
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            if (loading) {
                binding.shimmer.visibility = View.VISIBLE
                binding.shimmer.startShimmer()
                binding.rvMovies.visibility    = View.GONE
                binding.filterBar.visibility   = View.GONE
                binding.layoutEmpty.visibility = View.GONE
            } else {
                binding.shimmer.stopShimmer()
                binding.shimmer.visibility = View.GONE
            }
        }

        // ── Genre chips (ডেটা load হলে একবার তৈরি হয়) ────────────────────
        viewModel.genres.observe(viewLifecycleOwner) { genres ->
            if (genres.isNotEmpty()) {
                buildGenreChips(genres)
                binding.filterBar.visibility = View.VISIBLE
            }
        }

        // ── Year chips ─────────────────────────────────────────────────────
        viewModel.years.observe(viewLifecycleOwner) { years ->
            if (years.isNotEmpty()) {
                buildYearChips(years)
            }
        }

        // ── Filtered movie list ────────────────────────────────────────────
        viewModel.movies.observe(viewLifecycleOwner) { movies ->
            adapter.submitList(movies)
            binding.swipeRefresh.isRefreshing = false
            if (movies.isEmpty()) {
                binding.rvMovies.visibility    = View.GONE
                binding.layoutEmpty.visibility = View.VISIBLE
            } else {
                binding.rvMovies.visibility    = View.VISIBLE
                binding.layoutEmpty.visibility = View.GONE
            }
        }

        // ── SwipeRefresh ───────────────────────────────────────────────────
        binding.swipeRefresh.setColorSchemeResources(R.color.primary_red)
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadMovies(forceRefresh = true)
        }
    }

    // ── Category ChipGroup (Hollywood / Bollywood / Hindi Dubbed / ...)
    private fun buildGenreChips(categories: List<String>) {
        binding.chipGroupGenre.removeAllViews()
        binding.chipGroupGenre.addView(
            makeChip("সব ক্যাটাগরি", checked = true) {
                uncheckOthers(binding.chipGroupGenre, it)
                viewModel.filterByGenre("সব")
            }
        )
        categories.forEach { cat ->
            binding.chipGroupGenre.addView(
                makeChip(cat) {
                    uncheckOthers(binding.chipGroupGenre, it)
                    viewModel.filterByGenre(cat)
                }
            )
        }
    }

    // ── Year ChipGroup তৈরি ──────────────────────────────────────────────────
    private fun buildYearChips(years: List<String>) {
        binding.chipGroupYear.removeAllViews()
        binding.chipGroupYear.addView(
            makeChip("সব বছর", checked = true) {
                uncheckOthers(binding.chipGroupYear, it)
                viewModel.filterByYear("সব")
            }
        )
        years.forEach { year ->
            binding.chipGroupYear.addView(
                makeChip(year) {
                    uncheckOthers(binding.chipGroupYear, it)
                    viewModel.filterByYear(year)
                }
            )
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  SERIES FRAGMENT  — Category chip filter সহ
// ═══════════════════════════════════════════════════════════════════════════════
class SeriesFragment : Fragment() {

    private var _binding: FragmentSeriesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SeriesViewModel by viewModels()
    private lateinit var adapter: SeriesAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSeriesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ── RecyclerView setup ─────────────────────────────────────────────
        adapter = SeriesAdapter { series ->
            startActivity(Intent(requireContext(), SeriesDetailsActivity::class.java).apply {
                putExtra(SeriesDetailsActivity.EXTRA_SERIES, series)
            })
        }
        binding.rvSeries.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.rvSeries.adapter = adapter

        // ── Loading state ──────────────────────────────────────────────────
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            if (loading) {
                binding.shimmer.visibility = View.VISIBLE
                binding.shimmer.startShimmer()
                binding.rvSeries.visibility    = View.GONE
                binding.filterBar.visibility   = View.GONE
                binding.layoutEmpty.visibility = View.GONE
            } else {
                binding.shimmer.stopShimmer()
                binding.shimmer.visibility = View.GONE
            }
        }

        // ── Genre chips ────────────────────────────────────────────────────
        viewModel.genres.observe(viewLifecycleOwner) { genres ->
            if (genres.isNotEmpty()) {
                buildGenreChips(genres)
                binding.filterBar.visibility = View.VISIBLE
            }
        }

        // ── Year chips ─────────────────────────────────────────────────────
        viewModel.years.observe(viewLifecycleOwner) { years ->
            if (years.isNotEmpty()) {
                buildYearChips(years)
            }
        }

        // ── Filtered series list ───────────────────────────────────────────
        viewModel.series.observe(viewLifecycleOwner) { series ->
            adapter.submitList(series)
            binding.swipeRefresh.isRefreshing = false
            if (series.isEmpty()) {
                binding.rvSeries.visibility    = View.GONE
                binding.layoutEmpty.visibility = View.VISIBLE
            } else {
                binding.rvSeries.visibility    = View.VISIBLE
                binding.layoutEmpty.visibility = View.GONE
            }
        }

        // ── SwipeRefresh ───────────────────────────────────────────────────
        binding.swipeRefresh.setColorSchemeResources(R.color.primary_red)
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadSeries(forceRefresh = true)
        }
    }

    // ── Category ChipGroup (English Series / Hindi Dubbed Series / Hindi Tv Series)
    private fun buildGenreChips(categories: List<String>) {
        binding.chipGroupGenre.removeAllViews()
        binding.chipGroupGenre.addView(
            makeChip("সব ক্যাটাগরি", checked = true) {
                uncheckOthers(binding.chipGroupGenre, it)
                viewModel.filterByGenre("সব")
            }
        )
        categories.forEach { cat ->
            binding.chipGroupGenre.addView(
                makeChip(cat) {
                    uncheckOthers(binding.chipGroupGenre, it)
                    viewModel.filterByGenre(cat)
                }
            )
        }
    }

    private fun buildYearChips(years: List<String>) {
        binding.chipGroupYear.removeAllViews()
        binding.chipGroupYear.addView(
            makeChip("সব বছর", checked = true) {
                uncheckOthers(binding.chipGroupYear, it)
                viewModel.filterByYear("সব")
            }
        )
        years.forEach { year ->
            binding.chipGroupYear.addView(
                makeChip(year) {
                    uncheckOthers(binding.chipGroupYear, it)
                    viewModel.filterByYear(year)
                }
            )
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  LIVE TV FRAGMENT
// ═══════════════════════════════════════════════════════════════════════════════
class LiveTVFragment : Fragment() {

    private var _binding: FragmentLiveTvBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LiveTVViewModel by viewModels()
    private lateinit var channelAdapter: ChannelAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLiveTvBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupChannelList()
        observeViewModel()
    }

    private fun setupChannelList() {
        channelAdapter = ChannelAdapter { channel ->
            startActivity(Intent(requireContext(), VideoPlayerActivity::class.java).apply {
                putExtra(VideoPlayerActivity.EXTRA_STREAM_URL, channel.streamUrl)
                putExtra(VideoPlayerActivity.EXTRA_CONTENT_ID, channel.id)
                putExtra(VideoPlayerActivity.EXTRA_TITLE, channel.name)
                putExtra(VideoPlayerActivity.EXTRA_POSTER, channel.logo)
                putExtra(VideoPlayerActivity.EXTRA_TYPE, "live")
            })
        }
        binding.rvChannels.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.rvChannels.adapter = channelAdapter
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.shimmer.visibility    = if (loading) View.VISIBLE else View.GONE
            binding.rvChannels.visibility = if (loading) View.GONE else View.VISIBLE
        }
        viewModel.filteredChannels.observe(viewLifecycleOwner) {
            channelAdapter.submitList(it)
            binding.swipeRefresh.isRefreshing = false
        }
        viewModel.categories.observe(viewLifecycleOwner) { buildCategoryChips(it) }

        // ── SwipeRefresh ───────────────────────────────────────────────────
        binding.swipeRefresh.setColorSchemeResources(R.color.primary_red)
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadChannels(forceRefresh = true)
        }
    }

    private fun buildCategoryChips(categories: List<String>) {
        binding.chipGroupCategories.removeAllViews()
        binding.chipGroupCategories.addView(
            makeChip("সব", checked = true) {
                uncheckOthers(binding.chipGroupCategories, it)
                viewModel.filterByCategory("All")
            }
        )
        categories.forEach { cat ->
            binding.chipGroupCategories.addView(
                makeChip(cat) {
                    uncheckOthers(binding.chipGroupCategories, it)
                    viewModel.filterByCategory(cat)
                }
            )
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  FAVORITES FRAGMENT
// ═══════════════════════════════════════════════════════════════════════════════
class FavoriteFragment : Fragment() {

    private var _binding: FragmentFavoriteBinding? = null
    private val binding get() = _binding!!
    private lateinit var movieAdapter: MovieAdapter
    private lateinit var seriesAdapter: SeriesAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFavoriteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        movieAdapter = MovieAdapter { movie ->
            startActivity(Intent(requireContext(), MovieDetailsActivity::class.java).apply {
                putExtra(MovieDetailsActivity.EXTRA_MOVIE, movie)
            })
        }
        binding.rvFavMovies.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.rvFavMovies.adapter = movieAdapter

        seriesAdapter = SeriesAdapter { series ->
            startActivity(Intent(requireContext(), SeriesDetailsActivity::class.java).apply {
                putExtra(SeriesDetailsActivity.EXTRA_SERIES, series)
            })
        }
        binding.rvFavSeries.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.rvFavSeries.adapter = seriesAdapter
    }

    override fun onResume() {
        super.onResume()
        val favMovies = PreferenceManager.getFavoriteMovies(requireContext())
        val favSeries = PreferenceManager.getFavoriteSeries(requireContext())
        movieAdapter.submitList(favMovies)
        seriesAdapter.submitList(favSeries)
        binding.tvNoFavorites.visibility =
            if (favMovies.isEmpty() && favSeries.isEmpty()) View.VISIBLE else View.GONE
        binding.tvMoviesLabel.visibility = if (favMovies.isEmpty()) View.GONE else View.VISIBLE
        binding.tvSeriesLabel.visibility = if (favSeries.isEmpty()) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  SHARED CHIP HELPERS
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Filter chip তৈরি করে।
 * - checked = true হলে primary_red রঙে শুরু হবে
 * - onClick এ chip নিজেই পাঠানো হয় যাতে uncheckOthers করা যায়
 */
fun Fragment.makeChip(
    label: String,
    checked: Boolean = false,
    onClick: (Chip) -> Unit
): Chip = Chip(requireContext()).apply {
    text = label
    isCheckable = true
    isChecked = checked
    textSize = 12f

    // ── background: selected=red, normal=surface_card ──
    chipBackgroundColor = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
        intArrayOf(
            requireContext().getColor(R.color.primary_red),
            requireContext().getColor(R.color.surface_card)
        )
    )

    // ── text: selected=white, normal=text_secondary ──
    setTextColor(
        ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(
                requireContext().getColor(R.color.white),
                requireContext().getColor(R.color.text_secondary)
            )
        )
    )

    // ── stroke: selected=red, normal=surface_card (invisible border) ──
    chipStrokeColor = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
        intArrayOf(
            requireContext().getColor(R.color.primary_red),
            requireContext().getColor(R.color.surface_card)
        )
    )
    chipStrokeWidth = 1.5f

    setOnClickListener { onClick(this) }
}

/**
 * ChipGroup-এ একটি chip checked হলে বাকি সব uncheck করে।
 */
fun uncheckOthers(group: ChipGroup, selected: Chip) {
    for (i in 0 until group.childCount) {
        (group.getChildAt(i) as? Chip)?.isChecked = (group.getChildAt(i) == selected)
    }
}
