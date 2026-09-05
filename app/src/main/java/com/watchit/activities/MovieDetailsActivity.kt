package com.watchit.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.watchit.PreferenceManager
import com.watchit.R
import com.watchit.databinding.ActivityMovieDetailsBinding
import com.watchit.models.Movie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class MovieDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMovieDetailsBinding
    private lateinit var movie: Movie

    companion object {
        const val EXTRA_MOVIE = "movie"
        private const val TMDB_API_KEY = "bcea496389dc5e4b3711b11123ea7b7f"
        private const val TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p/w185"
        private const val MIN_DESC_LENGTH = 80
    }

    data class CastMember(val name: String, val character: String, val photoPath: String)

    // TMDB থেকে পাওয়া trailer URL
    private var tmdbTrailerUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMovieDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        movie = intent.getParcelableExtra(EXTRA_MOVIE) ?: run { finish(); return }
        bindData()
    }

    private fun bindData() {
        Glide.with(this)
            .load(movie.banner.ifEmpty { movie.poster })
            .centerCrop()
            .into(binding.ivBanner)
        Glide.with(this).load(movie.poster).into(binding.ivPoster)

        binding.tvTitle.text   = extractCleanMovieTitle(movie.title)
        binding.tvGenre.text   = movie.genre
        binding.tvYear.text    = movie.year
        binding.tvRating.text  = "⭐ ${movie.rating}"
        binding.tvQuality.text = movie.quality

        // Description: যথেষ্ট থাকলে সরাসরি দেখাও
        if (movie.description.trim().length >= MIN_DESC_LENGTH) {
            binding.tvDescription.text = movie.description
            binding.layoutAiLoading.visibility = View.GONE
            binding.tvTmdbBadge.visibility = View.GONE
        } else {
            if (movie.description.isNotBlank()) {
                binding.tvDescription.text = movie.description
            }
        }

        // TMDB: description + cast + trailer সব একসাথে
        fetchTmdbData()

        updateFavIcon()

        binding.btnFavorite.setOnClickListener {
            PreferenceManager.toggleFavoriteMovie(this, movie)
            updateFavIcon()
        }

        binding.btnWatch.setOnClickListener {
            startActivity(Intent(this, VideoPlayerActivity::class.java).apply {
                putExtra(VideoPlayerActivity.EXTRA_STREAM_URL, movie.streamUrl)
                putExtra(VideoPlayerActivity.EXTRA_CONTENT_ID, movie.id)
                putExtra(VideoPlayerActivity.EXTRA_TITLE, movie.title)
                putExtra(VideoPlayerActivity.EXTRA_POSTER, movie.poster)
                putExtra(VideoPlayerActivity.EXTRA_TYPE, "movie")
            })
        }

        // Trailer button: JSON url থাকলে সেটা, না থাকলে TMDB url ব্যবহার করবে
        binding.btnTrailer.setOnClickListener {
            val url = when {
                movie.trailerUrl.isNotEmpty() -> movie.trailerUrl
                tmdbTrailerUrl != null        -> tmdbTrailerUrl!!
                else                          -> null
            }
            if (url != null) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }

        binding.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  TMDB: description + cast + trailer একসাথে fetch
    // ─────────────────────────────────────────────────────────────────────────

    private fun fetchTmdbData() {
        val needsDescription = movie.description.trim().length < MIN_DESC_LENGTH

        if (needsDescription) {
            binding.layoutAiLoading.visibility = View.VISIBLE
        }
        binding.layoutCastLoading.visibility = View.VISIBLE
        binding.tvTmdbBadge.visibility = View.GONE

        lifecycleScope.launch {
            // Step 1: TMDB movie id
            val tmdbId = withContext(Dispatchers.IO) { searchTmdbId() }

            if (tmdbId == -1) {
                binding.layoutAiLoading.visibility = View.GONE
                binding.layoutCastLoading.visibility = View.GONE
                if (movie.description.isBlank()) {
                    binding.tvDescription.text = "এই মুভি সম্পর্কে কোনো বিবরণ পাওয়া যায়নি।"
                }
                return@launch
            }

            // Step 2: description, cast, trailer — parallel fetch
            val descJob    = async(Dispatchers.IO) { if (needsDescription) fetchOverview(tmdbId) else null }
            val castJob    = async(Dispatchers.IO) { fetchCast(tmdbId) }
            val trailerJob = async(Dispatchers.IO) { fetchTrailer(tmdbId) }

            val description = descJob.await()
            val castList    = castJob.await()
            val trailerUrl  = trailerJob.await()

            // ── Description ───────────────────────────────────────────────
            binding.layoutAiLoading.visibility = View.GONE
            if (needsDescription) {
                if (!description.isNullOrBlank()) {
                    binding.tvDescription.text = description
                    binding.tvTmdbBadge.visibility = View.VISIBLE
                } else if (movie.description.isBlank()) {
                    binding.tvDescription.text = "এই মুভি সম্পর্কে কোনো বিবরণ পাওয়া যায়নি।"
                }
            }

            // ── Cast ──────────────────────────────────────────────────────
            binding.layoutCastLoading.visibility = View.GONE
            if (castList.isNotEmpty()) {
                binding.rvCast.layoutManager =
                    LinearLayoutManager(this@MovieDetailsActivity, LinearLayoutManager.HORIZONTAL, false)
                binding.rvCast.adapter = CastAdapter(castList)
                binding.layoutCast.visibility = View.VISIBLE
            }

            // ── Trailer ───────────────────────────────────────────────────
            if (trailerUrl != null) {
                tmdbTrailerUrl = trailerUrl
                // JSON এ trailer না থাকলে TMDB টা দিয়ে button active করো
                if (movie.trailerUrl.isEmpty()) {
                    binding.btnTrailer.alpha = 1f
                    binding.btnTrailer.isEnabled = true
                }
            } else if (movie.trailerUrl.isEmpty()) {
                // কোথাও trailer নেই — button dim করো
                binding.btnTrailer.alpha = 0.4f
                binding.btnTrailer.isEnabled = false
            }
        }
    }

    // ─── TMDB API functions ───────────────────────────────────────────────────

    private fun searchTmdbId(): Int {
        return try {
            val encodedTitle = URLEncoder.encode(cleanTitle(movie.title), "UTF-8")
            val url = "https://api.themoviedb.org/3/search/movie" +
                    "?api_key=$TMDB_API_KEY&query=$encodedTitle&language=en-US&page=1"
            val json = httpGet(url) ?: return -1
            val results = JSONObject(json).getJSONArray("results")
            if (results.length() == 0) return -1

            val movieYear = movie.year.trim()
            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                val itemYear = item.optString("release_date", "").take(4)
                if (movieYear.isNotEmpty() && itemYear == movieYear) {
                    return item.getInt("id")
                }
            }
            results.getJSONObject(0).getInt("id")
        } catch (e: Exception) { -1 }
    }

    private fun fetchOverview(tmdbId: Int): String? {
        return try {
            val url = "https://api.themoviedb.org/3/movie/$tmdbId?api_key=$TMDB_API_KEY&language=en-US"
            val json = httpGet(url) ?: return null
            val overview = JSONObject(json).optString("overview", "").trim()
            if (overview.length >= 30) overview else null
        } catch (e: Exception) { null }
    }

    private fun fetchCast(tmdbId: Int): List<CastMember> {
        return try {
            val url = "https://api.themoviedb.org/3/movie/$tmdbId/credits?api_key=$TMDB_API_KEY"
            val json = httpGet(url) ?: return emptyList()
            val castArray = JSONObject(json).getJSONArray("cast")
            val list = mutableListOf<CastMember>()
            val limit = minOf(10, castArray.length())
            for (i in 0 until limit) {
                val item = castArray.getJSONObject(i)
                list.add(CastMember(
                    name      = item.optString("name", ""),
                    character = item.optString("character", ""),
                    photoPath = item.optString("profile_path", "")
                ))
            }
            list
        } catch (e: Exception) { emptyList() }
    }

    /**
     * TMDB Videos API থেকে YouTube trailer key নিয়ে আসে।
     * type="Trailer" ও site="YouTube" — এই দুটো match করলেই নেয়।
     */
    private fun fetchTrailer(tmdbId: Int): String? {
        return try {
            val url = "https://api.themoviedb.org/3/movie/$tmdbId/videos?api_key=$TMDB_API_KEY&language=en-US"
            val json = httpGet(url) ?: return null
            val results = JSONObject(json).getJSONArray("results")

            // প্রথমে Official Trailer খোঁজো
            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                if (item.optString("type") == "Trailer" &&
                    item.optString("site") == "YouTube" &&
                    item.optString("official") == "true") {
                    val key = item.optString("key", "")
                    if (key.isNotEmpty()) return "https://www.youtube.com/watch?v=$key"
                }
            }

            // Official না পেলে যেকোনো Trailer নাও
            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                if (item.optString("type") == "Trailer" &&
                    item.optString("site") == "YouTube") {
                    val key = item.optString("key", "")
                    if (key.isNotEmpty()) return "https://www.youtube.com/watch?v=$key"
                }
            }

            // Trailer না থাকলে Teaser নাও
            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                if (item.optString("type") == "Teaser" &&
                    item.optString("site") == "YouTube") {
                    val key = item.optString("key", "")
                    if (key.isNotEmpty()) return "https://www.youtube.com/watch?v=$key"
                }
            }

            null
        } catch (e: Exception) { null }
    }

    // ─── Cast Adapter (inline) ────────────────────────────────────────────────

    inner class CastAdapter(private val cast: List<CastMember>) :
        RecyclerView.Adapter<CastAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val ivPhoto: ImageView    = view.findViewById(R.id.ivCastPhoto)
            val tvName: TextView      = view.findViewById(R.id.tvCastName)
            val tvCharacter: TextView = view.findViewById(R.id.tvCharacterName)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_cast, parent, false))

        override fun getItemCount() = cast.size

        override fun onBindViewHolder(h: VH, pos: Int) {
            val m = cast[pos]
            h.tvName.text      = m.name
            h.tvCharacter.text = m.character
            val photoUrl = if (m.photoPath.isNotEmpty()) "$TMDB_IMAGE_BASE${m.photoPath}" else null
            Glide.with(h.ivPhoto)
                .load(photoUrl)
                .transform(CircleCrop())
                .transition(DrawableTransitionOptions.withCrossFade())
                .placeholder(R.drawable.placeholder_avatar)
                .error(R.drawable.placeholder_avatar)
                .into(h.ivPhoto)
        }
    }

    // ─── Utility ──────────────────────────────────────────────────────────────

    private fun extractCleanMovieTitle(title: String): String {
        val idx = title.indexOf('#')
        return if (idx != -1) title.substring(idx + 1).trim() else title.trim()
    }

    private fun cleanTitle(title: String): String {
        return extractCleanMovieTitle(title)
            .replace(Regex("\\(\\d{4}\\)"), "")
            .replace(Regex("\\[.*?]"), "")
            .replace(Regex("(?i)\\b(dubbed|hindi|bangla|bengali|tamil|telugu|malayalam|web-?dl|bluray|hdrip|hdcam|cam|hd|4k)\\b"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun httpGet(urlStr: String): String? {
        return try {
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode == 200)
                conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
            else null
        } catch (e: Exception) { null }
    }

    private fun updateFavIcon() {
        val isFav = PreferenceManager.isFavoriteMovie(this, movie.id)
        binding.btnFavorite.setImageResource(
            if (isFav) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_outline
        )
    }
}
