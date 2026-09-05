package com.watchit.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.watchit.PreferenceManager
import com.watchit.R
import com.watchit.adapters.EpisodeAdapter
import com.watchit.databinding.ActivitySeriesDetailsBinding
import com.watchit.models.Episode
import com.watchit.models.Series

class SeriesDetailsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySeriesDetailsBinding
    private lateinit var series: Series
    private lateinit var episodeAdapter: EpisodeAdapter
    private var selectedSeason = 0

    companion object {
        const val EXTRA_SERIES = "series"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySeriesDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        series = intent.getParcelableExtra(EXTRA_SERIES) ?: run { finish(); return }
        bindData()
        setupEpisodeList()
        setupSeasonSpinner()
    }

    private fun bindData() {
        Glide.with(this).load(series.banner.ifEmpty { series.poster }).centerCrop().into(binding.ivBanner)
        Glide.with(this).load(series.poster).into(binding.ivPoster)
        binding.tvTitle.text       = series.title
        binding.tvGenre.text       = series.genre
        binding.tvYear.text        = series.year
        binding.tvRating.text      = "⭐ ${series.rating}"
        binding.tvDescription.text = series.description

        updateFavIcon()
        binding.btnFavorite.setOnClickListener {
            PreferenceManager.toggleFavoriteSeries(this, series)
            updateFavIcon()
        }
        binding.btnTrailer.setOnClickListener {
            if (series.trailerUrl.isNotEmpty())
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(series.trailerUrl)))
        }
        binding.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun updateFavIcon() {
        binding.btnFavorite.setImageResource(
            if (PreferenceManager.isFavoriteSeries(this, series.id))
                R.drawable.ic_favorite_filled else R.drawable.ic_favorite_outline
        )
    }

    private fun setupEpisodeList() {
        episodeAdapter = EpisodeAdapter { episode -> playEpisode(episode) }
        binding.rvEpisodes.layoutManager = LinearLayoutManager(this)
        binding.rvEpisodes.adapter = episodeAdapter
    }

    private fun setupSeasonSpinner() {
        if (series.seasons.isEmpty()) return
        val seasonNames = series.seasons.map { "Season ${it.seasonNumber}" }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, seasonNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerSeason.adapter = adapter
        binding.spinnerSeason.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                selectedSeason = pos
                episodeAdapter.submitList(series.seasons[pos].episodes)
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        episodeAdapter.submitList(series.seasons[0].episodes)
    }

    private fun playEpisode(episode: Episode) {
        startActivity(Intent(this, VideoPlayerActivity::class.java).apply {
            putExtra(VideoPlayerActivity.EXTRA_STREAM_URL, episode.streamUrl)
            putExtra(VideoPlayerActivity.EXTRA_CONTENT_ID, "${series.id}_${episode.id}")
            putExtra(VideoPlayerActivity.EXTRA_TITLE, series.title)
            putExtra(VideoPlayerActivity.EXTRA_POSTER, series.poster)
            putExtra(VideoPlayerActivity.EXTRA_TYPE, "series")
            putExtra(VideoPlayerActivity.EXTRA_EPISODE_TITLE, episode.title)
        })
    }
}
