package com.watchit.activities

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.watchit.adapters.ChannelAdapter
import com.watchit.adapters.MovieAdapter
import com.watchit.adapters.SeriesAdapter
import com.watchit.databinding.ActivitySearchBinding
import com.watchit.viewmodels.SearchViewModel

class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding
    private val viewModel: SearchViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        // ViewPager + Tabs
        val pagerAdapter = SearchPagerAdapter(this, viewModel)
        binding.viewPager.adapter = pagerAdapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, pos ->
            tab.text = when (pos) { 0 -> "Movies"; 1 -> "Series"; else -> "Live TV" }
        }.attach()

        // Search input
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { viewModel.search(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })

        // Result count
        viewModel.results.observe(this) { (movies, series, channels) ->
            val total = movies.size + series.size + channels.size
            binding.tvResultCount.text = if (total > 0) "$total results found" else ""
        }

        // Keyboard auto-show
        binding.etSearch.requestFocus()
    }
}

// ── ViewPager Adapter ──────────────────────────────────────────────────────────
class SearchPagerAdapter(
    fa: FragmentActivity,
    private val viewModel: SearchViewModel
) : FragmentStateAdapter(fa) {
    override fun getItemCount() = 3
    override fun createFragment(position: Int): Fragment = SearchTabFragment.newInstance(position)
}

// ── Each Tab Fragment ──────────────────────────────────────────────────────────
class SearchTabFragment : Fragment() {

    companion object {
        private const val ARG_TAB = "tab"
        fun newInstance(tab: Int) = SearchTabFragment().apply {
            arguments = Bundle().apply { putInt(ARG_TAB, tab) }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val rv = RecyclerView(requireContext()).apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            setPadding(8, 8, 8, 16)
            clipToPadding = false
        }
        return rv
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val rv = view as RecyclerView
        val tab = arguments?.getInt(ARG_TAB) ?: 0
        val viewModel = (requireActivity() as SearchActivity).let {
            androidx.lifecycle.ViewModelProvider(it)[SearchViewModel::class.java]
        }

        when (tab) {
            0 -> {
                val adapter = MovieAdapter { movie ->
                    startActivity(Intent(requireContext(), MovieDetailsActivity::class.java).apply {
                        putExtra(MovieDetailsActivity.EXTRA_MOVIE, movie)
                    })
                }
                rv.adapter = adapter
                viewModel.results.observe(viewLifecycleOwner) { adapter.submitList(it.first) }
            }
            1 -> {
                val adapter = SeriesAdapter { series ->
                    startActivity(Intent(requireContext(), SeriesDetailsActivity::class.java).apply {
                        putExtra(SeriesDetailsActivity.EXTRA_SERIES, series)
                    })
                }
                rv.adapter = adapter
                viewModel.results.observe(viewLifecycleOwner) { adapter.submitList(it.second) }
            }
            else -> {
                val adapter = ChannelAdapter { channel ->
                    startActivity(Intent(requireContext(), VideoPlayerActivity::class.java).apply {
                        putExtra(VideoPlayerActivity.EXTRA_STREAM_URL, channel.streamUrl)
                        putExtra(VideoPlayerActivity.EXTRA_CONTENT_ID, channel.id)
                        putExtra(VideoPlayerActivity.EXTRA_TITLE, channel.name)
                        putExtra(VideoPlayerActivity.EXTRA_POSTER, channel.logo)
                        putExtra(VideoPlayerActivity.EXTRA_TYPE, "live")
                    })
                }
                rv.adapter = adapter
                viewModel.results.observe(viewLifecycleOwner) { adapter.submitList(it.third) }
            }
        }
    }
}
