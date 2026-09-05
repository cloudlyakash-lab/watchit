package com.watchit.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.watchit.databinding.*
import com.watchit.models.*

// ═══════════════════════════════════════════════════════════════════════════════
//  UTILITY: "Animation Movie #Moana 2" → "Moana 2"
//           "Hindi Dubbed Movie #Raja Shivaji (2026)" → "Raja Shivaji (2026)"
//           # না থাকলে original title ই রাখে
// ═══════════════════════════════════════════════════════════════════════════════
fun String.cleanDisplayTitle(): String {
    val hashIndex = indexOf('#')
    return if (hashIndex != -1) substring(hashIndex + 1).trim() else trim()
}

// ═══════════════════════════════════════════════════════════════════════════════
//  BANNER ADAPTER (ViewPager2)
// ═══════════════════════════════════════════════════════════════════════════════
class BannerAdapter(
    private val onClick: (Banner) -> Unit
) : ListAdapter<Banner, BannerAdapter.VH>(BannerDiff()) {

    inner class VH(val b: ItemBannerBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemBannerBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, pos: Int) {
        val banner = getItem(pos)
        Glide.with(h.b.ivBanner)
            .load(banner.image)
            .transition(DrawableTransitionOptions.withCrossFade())
            .centerCrop()
            .into(h.b.ivBanner)
        h.b.tvBannerTitle.text = banner.title.cleanDisplayTitle()
        h.b.root.setOnClickListener { onClick(banner) }
    }

    class BannerDiff : DiffUtil.ItemCallback<Banner>() {
        override fun areItemsTheSame(o: Banner, n: Banner) = o.id == n.id
        override fun areContentsTheSame(o: Banner, n: Banner) = o == n
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  MOVIE ADAPTER (Grid / Horizontal)
// ═══════════════════════════════════════════════════════════════════════════════
class MovieAdapter(
    private val onClick: (Movie) -> Unit
) : ListAdapter<Movie, MovieAdapter.VH>(MovieDiff()) {

    inner class VH(val b: ItemMovieBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemMovieBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, pos: Int) {
        val movie = getItem(pos)
        Glide.with(h.b.ivPoster)
            .load(movie.poster)
            .transition(DrawableTransitionOptions.withCrossFade())
            .placeholder(com.watchit.R.drawable.placeholder_poster)
            .centerCrop()
            .into(h.b.ivPoster)
        // ★ prefix বাদ দিয়ে clean title দেখাও
        h.b.tvTitle.text   = movie.title.cleanDisplayTitle()
        h.b.tvYear.text    = movie.year
        h.b.tvQuality.text = movie.quality
        h.b.root.setOnClickListener { onClick(movie) }

        // Quality badge color
        h.b.tvQuality.setBackgroundResource(
            when (movie.quality) {
                "4K"  -> com.watchit.R.drawable.badge_4k
                "HD"  -> com.watchit.R.drawable.badge_hd
                "CAM" -> com.watchit.R.drawable.badge_cam
                else  -> com.watchit.R.drawable.badge_hd
            }
        )
    }

    class MovieDiff : DiffUtil.ItemCallback<Movie>() {
        override fun areItemsTheSame(o: Movie, n: Movie) = o.id == n.id
        override fun areContentsTheSame(o: Movie, n: Movie) = o == n
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  SERIES ADAPTER (Grid)
// ═══════════════════════════════════════════════════════════════════════════════
class SeriesAdapter(
    private val onClick: (Series) -> Unit
) : ListAdapter<Series, SeriesAdapter.VH>(SeriesDiff()) {

    inner class VH(val b: ItemSeriesBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemSeriesBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, pos: Int) {
        val series = getItem(pos)
        Glide.with(h.b.ivPoster)
            .load(series.poster)
            .transition(DrawableTransitionOptions.withCrossFade())
            .placeholder(com.watchit.R.drawable.placeholder_poster)
            .centerCrop()
            .into(h.b.ivPoster)
        // ★ prefix বাদ দিয়ে clean title দেখাও
        h.b.tvTitle.text   = series.title.cleanDisplayTitle()
        h.b.tvYear.text    = series.year
        h.b.tvQuality.text = series.quality
        h.b.root.setOnClickListener { onClick(series) }
    }

    class SeriesDiff : DiffUtil.ItemCallback<Series>() {
        override fun areItemsTheSame(o: Series, n: Series) = o.id == n.id
        override fun areContentsTheSame(o: Series, n: Series) = o == n
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  CHANNEL ADAPTER (Grid - Live TV Page)
// ═══════════════════════════════════════════════════════════════════════════════
class ChannelAdapter(
    private val onClick: (Channel) -> Unit
) : ListAdapter<Channel, ChannelAdapter.VH>(ChannelDiff()) {

    inner class VH(val b: ItemChannelBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemChannelBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, pos: Int) {
        val ch = getItem(pos)
        Glide.with(h.b.ivLogo)
            .load(ch.logo)
            .transition(DrawableTransitionOptions.withCrossFade())
            .centerInside()
            .into(h.b.ivLogo)
        h.b.tvName.text = ch.name
        h.b.root.setOnClickListener { onClick(ch) }
    }

    class ChannelDiff : DiffUtil.ItemCallback<Channel>() {
        override fun areItemsTheSame(o: Channel, n: Channel) = o.id == n.id
        override fun areContentsTheSame(o: Channel, n: Channel) = o == n
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  CHANNEL GRID ADAPTER (Home Section - smaller cards)
// ═══════════════════════════════════════════════════════════════════════════════
class ChannelGridAdapter(
    private val onClick: (Channel) -> Unit
) : ListAdapter<Channel, ChannelGridAdapter.VH>(ChannelGridDiff()) {

    inner class VH(val b: ItemChannelGridBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemChannelGridBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, pos: Int) {
        val ch = getItem(pos)
        Glide.with(h.b.ivLogo).load(ch.logo).centerInside().into(h.b.ivLogo)
        h.b.tvName.text = ch.name
        h.b.root.setOnClickListener { onClick(ch) }
    }

    class ChannelGridDiff : DiffUtil.ItemCallback<Channel>() {
        override fun areItemsTheSame(o: Channel, n: Channel) = o.id == n.id
        override fun areContentsTheSame(o: Channel, n: Channel) = o == n
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  STAR ADAPTER (Horizontal Circular)
// ═══════════════════════════════════════════════════════════════════════════════
class StarAdapter : ListAdapter<Star, StarAdapter.VH>(StarDiff()) {

    inner class VH(val b: ItemStarBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemStarBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, pos: Int) {
        val star = getItem(pos)
        Glide.with(h.b.ivPhoto)
            .load(star.photo)
            .transition(DrawableTransitionOptions.withCrossFade())
            .circleCrop()
            .placeholder(com.watchit.R.drawable.placeholder_avatar)
            .into(h.b.ivPhoto)
        h.b.tvName.text = star.name
    }

    class StarDiff : DiffUtil.ItemCallback<Star>() {
        override fun areItemsTheSame(o: Star, n: Star) = o.id == n.id
        override fun areContentsTheSame(o: Star, n: Star) = o == n
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  EPISODE ADAPTER (Series Details)
// ═══════════════════════════════════════════════════════════════════════════════
class EpisodeAdapter(
    private val onClick: (Episode) -> Unit
) : ListAdapter<Episode, EpisodeAdapter.VH>(EpisodeDiff()) {

    inner class VH(val b: ItemEpisodeBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemEpisodeBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, pos: Int) {
        val ep = getItem(pos)
        Glide.with(h.b.ivThumbnail)
            .load(ep.thumbnail)
            .transition(DrawableTransitionOptions.withCrossFade())
            .centerCrop()
            .placeholder(com.watchit.R.drawable.placeholder_thumb)
            .into(h.b.ivThumbnail)
        h.b.tvEpNumber.text = "EP ${ep.episodeNumber}"
        h.b.tvEpTitle.text  = ep.title
        h.b.tvSeason.text   = ep.seasonName
        h.b.tvDuration.text = ep.duration
        h.b.root.setOnClickListener { onClick(ep) }
    }

    class EpisodeDiff : DiffUtil.ItemCallback<Episode>() {
        override fun areItemsTheSame(o: Episode, n: Episode) = o.id == n.id
        override fun areContentsTheSame(o: Episode, n: Episode) = o == n
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  CONTINUE WATCHING ADAPTER
// ═══════════════════════════════════════════════════════════════════════════════
class ContinueWatchingAdapter(
    private val onClick: (ContinueWatching) -> Unit
) : ListAdapter<ContinueWatching, ContinueWatchingAdapter.VH>(ContinueDiff()) {

    inner class VH(val b: ItemContinueWatchingBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemContinueWatchingBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, pos: Int) {
        val item = getItem(pos)
        Glide.with(h.b.ivPoster)
            .load(item.poster)
            .centerCrop()
            .placeholder(com.watchit.R.drawable.placeholder_poster)
            .into(h.b.ivPoster)
        h.b.tvTitle.text   = item.title.cleanDisplayTitle()
        h.b.tvEpisode.text = item.episodeTitle.ifEmpty { item.type.uppercase() }
        val progress = if (item.durationMs > 0)
            ((item.progressMs.toFloat() / item.durationMs) * 100).toInt() else 0
        h.b.progressBar.progress = progress
        h.b.root.setOnClickListener { onClick(item) }
    }

    class ContinueDiff : DiffUtil.ItemCallback<ContinueWatching>() {
        override fun areItemsTheSame(o: ContinueWatching, n: ContinueWatching) = o.id == n.id
        override fun areContentsTheSame(o: ContinueWatching, n: ContinueWatching) = o == n
    }
}
