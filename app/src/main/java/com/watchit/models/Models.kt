package com.watchit.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

// ─── Movie Model ────────────────────────────────────────────────────────────
@Parcelize
data class Movie(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val poster: String = "",
    val banner: String = "",
    val genre: String = "",
    val year: String = "",
    val rating: String = "",
    val quality: String = "HD",   // HD, CAM, WEB-DL, 4K
    val duration: String = "",
    val streamUrl: String = "",
    val trailerUrl: String = "",
    val language: String = "",
    val isTrending: Boolean = false,
    val isFeatured: Boolean = false
) : Parcelable

// ─── Series Model ───────────────────────────────────────────────────────────
@Parcelize
data class Series(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val poster: String = "",
    val banner: String = "",
    val genre: String = "",
    val year: String = "",
    val rating: String = "",
    val quality: String = "HD",
    val language: String = "",
    val trailerUrl: String = "",
    val totalSeasons: Int = 1,
    val seasons: List<Season> = emptyList(),
    val isTrending: Boolean = false
) : Parcelable

// ─── Season Model ───────────────────────────────────────────────────────────
@Parcelize
data class Season(
    val seasonNumber: Int = 1,
    val seasonName: String = "",
    val episodes: List<Episode> = emptyList()
) : Parcelable

// ─── Episode Model ──────────────────────────────────────────────────────────
@Parcelize
data class Episode(
    val id: String = "",
    val episodeNumber: Int = 1,
    val title: String = "",
    val thumbnail: String = "",
    val duration: String = "",
    val seasonName: String = "",
    val streamUrl: String = "",
    val description: String = ""
) : Parcelable

// ─── Channel Model ──────────────────────────────────────────────────────────
@Parcelize
data class Channel(
    val id: String = "",
    val name: String = "",
    val logo: String = "",
    val streamUrl: String = "",
    val category: String = "",   // Indian, Bangla, News, Sports, Entertainment, Kids
    val isLive: Boolean = true
) : Parcelable

// ─── Banner Model ───────────────────────────────────────────────────────────
@Parcelize
data class Banner(
    val id: String = "",
    val title: String = "",
    val image: String = "",
    val type: String = "",       // movie / series
    val itemId: String = ""
) : Parcelable

// ─── Star Model ─────────────────────────────────────────────────────────────
@Parcelize
data class Star(
    val id: String = "",
    val name: String = "",
    val photo: String = ""
) : Parcelable

// ─── Category Model ─────────────────────────────────────────────────────────
@Parcelize
data class Category(
    val id: String = "",
    val name: String = "",
    val icon: String = ""
) : Parcelable

// ─── Continue Watching Model ─────────────────────────────────────────────────
@Parcelize
data class ContinueWatching(
    val id: String = "",
    val title: String = "",
    val poster: String = "",
    val type: String = "",          // movie / series / live
    val streamUrl: String = "",
    val progressMs: Long = 0L,
    val durationMs: Long = 0L,
    val episodeTitle: String = "",
    val lastWatched: Long = 0L
) : Parcelable

// ─── App Data (GitHub JSON root) ─────────────────────────────────────────────
data class AppData(
    val banners: List<Banner> = emptyList(),
    val movies: List<Movie> = emptyList(),
    val series: List<Series> = emptyList(),
    val channels: List<Channel> = emptyList(),
    val stars: List<Star> = emptyList(),
    val categories: List<Category> = emptyList()
)
