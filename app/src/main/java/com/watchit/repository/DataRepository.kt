package com.watchit.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.watchit.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

// ═══════════════════════════════════════════════════════════════════════════════
//  Limits
// ═══════════════════════════════════════════════════════════════════════════════
private const val MOVIES_LIMIT = 500

// ═══════════════════════════════════════════════════════════════════════════════
//  Config Data Models
// ═══════════════════════════════════════════════════════════════════════════════
data class ApiConfig(
    @SerializedName("api_config") val apiConfig: ApiConfigData = ApiConfigData()
)
data class ApiConfigData(
    val liveTvUrl:        String              = "",
    val moviesUrl:        String              = "",
    val seriesUrl:        String              = "",
    val maintenance:      Boolean             = false,
    val movieCategories:  List<MediaCategory> = emptyList(),
    val seriesCategories: List<MediaCategory> = emptyList()
)
data class MediaCategory(
    val name: String = "",
    val url:  String = ""
)

/** Gson null-injection bypass — JsonObject থেকে manually safe parse */
private fun parseApiConfig(json: String): ApiConfigData? {
    return try {
        val root = Gson().fromJson(json, JsonObject::class.java) ?: return null
        val cfg  = root["api_config"]?.asJsonObject ?: return null

        fun JsonObject.str(key: String) = this[key]?.takeIf { !it.isJsonNull }?.asString ?: ""
        fun JsonObject.bool(key: String) = this[key]?.takeIf { !it.isJsonNull }?.asBoolean ?: false
        fun JsonObject.categories(key: String): List<MediaCategory> {
            val arr = this[key]?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
            return arr.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                val obj  = el.asJsonObject
                val url  = obj["url"]?.takeIf  { !it.isJsonNull }?.asString ?: return@mapNotNull null
                val name = obj["name"]?.takeIf { !it.isJsonNull }?.asString ?: ""
                if (url.isBlank()) null else MediaCategory(name = name, url = url)
            }
        }

        ApiConfigData(
            liveTvUrl        = cfg.str("live_tv_url"),
            moviesUrl        = cfg.str("movies_url"),
            seriesUrl        = cfg.str("series_url"),
            maintenance      = cfg.bool("maintenance"),
            movieCategories  = cfg.categories("movie_categories"),
            seriesCategories = cfg.categories("series_categories")
        )
    } catch (e: Exception) { null }
}

// ─── Live TV ──────────────────────────────────────────────────────────────────
// FORMAT A: { "channels": { "Group": [{name, logo, url}] } }
data class LiveTvResponse(
    @SerializedName("channels") val channels: Map<String, List<LiveTvItem>> = emptyMap()
)
data class LiveTvItem(
    @SerializedName("name")  val name:  String = "",
    @SerializedName("logo")  val logo:  String = "",
    @SerializedName("group") val group: String = "",
    @SerializedName("url")   val url:   String = ""
)
// FORMAT B: [{ "type":"category_collection", "category_name":"...", "items":{...} }]
data class LiveTvCategoryCollection(
    @SerializedName("type")          val type:         String                     = "",
    @SerializedName("category_name") val categoryName: String                     = "",
    @SerializedName("items")         val items:        Map<String, LiveTvRawItem> = emptyMap()
)
data class LiveTvRawItem(
    @SerializedName("tvg_logo") val tvgLogo: String          = "",
    @SerializedName("links")    val links:   List<LiveTvLink> = emptyList()
)
data class LiveTvLink(
    @SerializedName("url")     val url:     String = "",
    @SerializedName("quality") val quality: String = ""
)

// ─── Movies ───────────────────────────────────────────────────────────────────
data class MoviesCollectionWrapper(
    @SerializedName("type")          val type:         String                     = "",
    @SerializedName("category_name") val categoryName: String                     = "",
    @SerializedName("total_items")   val totalItems:   Int                        = 0,
    @SerializedName("items")         val items:        Map<String, MovieRawEntry> = emptyMap()
)
data class MovieRawEntry(
    @SerializedName("year")     val year:    String          = "",
    @SerializedName("tvg_logo") val tvgLogo: String          = "",
    @SerializedName("rating")   val rating:  Double          = 0.0,
    @SerializedName("links")    val links:   List<MovieLink> = emptyList()
)
data class MovieLink(
    @SerializedName("url")        val url:       String = "",
    @SerializedName("language")   val language:  String = "",
    @SerializedName("quality")    val quality:   String = "",
    @SerializedName("watch_page") val watchPage: String = "",
    @SerializedName("filename")   val filename:  String = "",
    @SerializedName("source")     val source:    String = ""
)

// ─── Series ───────────────────────────────────────────────────────────────────
data class SeriesItem(
    @SerializedName("year")     val year:    String           = "",
    @SerializedName("tvg_logo") val tvgLogo: String           = "",
    @SerializedName("links")    val links:   List<SeriesLink> = emptyList()
)
data class SeriesLink(
    @SerializedName("url")           val url:          String = "",
    @SerializedName("season")        val season:       Int    = 1,
    @SerializedName("episode")       val episode:      Int    = 1,
    @SerializedName("episode_title") val episodeTitle: String = "",
    @SerializedName("language")      val language:     String = "",
    @SerializedName("quality")       val quality:      String = ""
)

// ─── Result ───────────────────────────────────────────────────────────────────
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String) : Result<Nothing>()
    object Loading : Result<Nothing>()
}

// ═══════════════════════════════════════════════════════════════════════════════
//  DataRepository — সব ধরনের JSON Format Support
// ═══════════════════════════════════════════════════════════════════════════════
class DataRepository private constructor(private val context: Context) {

    private val client: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    private val gson = Gson()
    private var cachedData: AppData? = null

    companion object {
        @Volatile private var INSTANCE: DataRepository? = null
        fun getInstance(context: Context): DataRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: DataRepository(context.applicationContext).also { INSTANCE = it }
            }
    }

    // ─── Main Entry Point ─────────────────────────────────────────────────────
    suspend fun fetchAppData(forceRefresh: Boolean = false): Result<AppData> =
        withContext(Dispatchers.IO) {
            if (!forceRefresh && cachedData != null) {
                return@withContext Result.Success(cachedData!!)
            }

            // Selected server URL থেকে config load করো
            val configUrl = com.watchit.PreferenceManager.getSelectedServerUrl(context)
            val configJson = fetchUrl(configUrl) ?: return@withContext loadFromAssets()

            val config = parseApiConfig(configJson)
                ?: return@withContext loadFromAssets()

            if (config.maintenance) {
                return@withContext Result.Error("সার্ভার মেইনটেন্যান্স চলছে, একটু পরে চেষ্টা করুন")
            }

            val assetsData = loadAssetsData()

            // Live TV
            val channels = parseLiveTv(config.liveTvUrl)

            // Movies — categories অথবা single URL
            val movies: List<Movie> = if (config.movieCategories.isNotEmpty()) {
                config.movieCategories
                    .flatMap { parseMoviesFromUrl(it.url, it.name) }
            } else if (config.moviesUrl.isNotBlank()) {
                parseMoviesFromUrl(config.moviesUrl, "")
            } else emptyList()

            // Series — categories অথবা single URL
            val series: List<Series> = if (config.seriesCategories.isNotEmpty()) {
                config.seriesCategories
                    .flatMap { parseSeriesFromUrl(it.url, it.name) }
            } else if (config.seriesUrl.isNotBlank()) {
                parseSeriesFromUrl(config.seriesUrl, "")
            } else emptyList()

            if (movies.isEmpty() && series.isEmpty() && channels.isEmpty()) {
                cachedData = null
                return@withContext loadFromAssets()
            }

            val finalData = AppData(
                banners    = assetsData?.banners    ?: emptyList(),
                movies     = movies,
                series     = series,
                channels   = channels.ifEmpty { assetsData?.channels ?: emptyList() },
                stars      = assetsData?.stars      ?: emptyList(),
                categories = assetsData?.categories ?: emptyList()
            )
            cachedData = finalData
            Result.Success(finalData)
        }

    // ═══════════════════════════════════════════════════════════════════════════
    //  MOVIES PARSER — সব format auto-detect করে
    // ═══════════════════════════════════════════════════════════════════════════
    //
    //  FORMAT M1 — category_collection wrapper (numeric ID keys):
    //    { "type":"category_collection", "category_name":"Hollywood",
    //      "items": { "9": { "year":"2009","tvg_logo":"...","rating":6.9,
    //                        "links":[{"url":"...","quality":"1080p"}] } } }
    //
    //  FORMAT M2 — title-keyed flat object:
    //    { "Avengers Endgame (2019)": { "tvg_logo":"...","year":"2019","rating":8.4,
    //                                   "links":[{"url":"...","quality":"HD"}] } }
    //
    //  FORMAT M3 — array of objects:
    //    [ { "title":"...", "year":"...", "poster":"...", "stream_url":"...",
    //        "quality":"HD", "rating":"8.4" },  ... ]
    //
    //  FORMAT M4 — M3 variant with "url" instead of "stream_url"
    //
    //  FORMAT M5 — simple name→url map:
    //    { "Movie Name": "https://stream.mp4", ... }
    //
    //  FORMAT M6 — TMDB-style array:
    //    [ { "id":1, "title":"...", "poster_path":"...", "release_date":"2023-01-01",
    //        "vote_average":7.5, "stream_url":"..." } ]
    //
    private fun parseMoviesFromUrl(url: String, categoryLabel: String): List<Movie> {
        if (url.isBlank()) return emptyList()
        val json = fetchUrl(url) ?: return emptyList()
        if (json.isBlank()) return emptyList()

        return try {
            val root: JsonElement = gson.fromJson(json, JsonElement::class.java)

            when {
                // ── Array root → FORMAT M3 / M4 / M6 ──────────────────────────
                root.isJsonArray -> parseMoviesFromArray(root.asJsonArray, categoryLabel)

                // ── Object root ────────────────────────────────────────────────
                root.isJsonObject -> {
                    val obj = root.asJsonObject

                    // FORMAT M1: "type":"category_collection"
                    if (obj.has("type") &&
                        obj["type"].asString == "category_collection" &&
                        obj.has("items")) {
                        parseMoviesFormatM1(obj, categoryLabel)
                    }
                    // FORMAT M5: value is plain string URL
                    else if (obj.entrySet().firstOrNull()?.value?.isJsonPrimitive == true &&
                             obj.entrySet().firstOrNull()?.value?.asJsonPrimitive?.isString == true) {
                        parseMoviesFormatM5(obj, categoryLabel)
                    }
                    // FORMAT M2: value is object with links array
                    else {
                        parseMoviesFormatM2(obj, categoryLabel)
                    }
                }
                else -> emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** FORMAT M1 — category_collection, numeric/string ID → no title */
    private fun parseMoviesFormatM1(obj: JsonObject, categoryLabel: String): List<Movie> {
        val catName = categoryLabel.ifEmpty {
            obj["category_name"]?.asString ?: "Movie"
        }
        val itemsObj = obj["items"]?.asJsonObject ?: return emptyList()
        val result = mutableListOf<Movie>()

        itemsObj.entrySet().take(MOVIES_LIMIT).forEach { (movieId, el) ->
            if (!el.isJsonObject) return@forEach
            val entry = el.asJsonObject
            val links = parseMovieLinks(entry["links"])
            val bestLink = links.firstOrNull { it.url.isNotEmpty() } ?: return@forEach
            val year = entry["year"]?.asString ?: ""
            val rating = safeDouble(entry["rating"])
            result.add(Movie(
                id          = "movie_${catName}_$movieId",
                title       = buildTitle(movieId, catName, year),
                description = "",
                poster      = entry["tvg_logo"]?.asString ?: "",
                banner      = entry["tvg_logo"]?.asString ?: "",
                genre       = "",
                year        = year,
                rating      = if (rating > 0) String.format("%.1f", rating) else "",
                quality     = bestLink.quality.ifEmpty { "HD" },
                duration    = "",
                streamUrl   = bestLink.url,
                trailerUrl  = bestLink.watchPage,
                language    = catName,
                isTrending  = false,
                isFeatured  = false
            ))
        }
        return result
    }

    /** FORMAT M2 — title-keyed flat object */
    private fun parseMoviesFormatM2(obj: JsonObject, categoryLabel: String): List<Movie> {
        val catName = categoryLabel.ifEmpty { "Movie" }
        val result = mutableListOf<Movie>()
        var mid = 1

        obj.entrySet().take(MOVIES_LIMIT).forEach { (rawTitle, el) ->
            if (!el.isJsonObject) return@forEach
            val entry = el.asJsonObject
            val links = parseMovieLinks(entry["links"])
            val bestLink = links.firstOrNull { it.url.isNotEmpty() } ?: return@forEach
            val year = entry["year"]?.asString ?: ""
            val rating = safeDouble(entry["rating"])
            result.add(Movie(
                id          = "movie_${catName}_${mid++}",
                title       = cleanTitle(rawTitle, bestLink.filename),
                description = entry["description"]?.asString ?: "",
                poster      = entry["tvg_logo"]?.asString ?: entry["poster"]?.asString ?: "",
                banner      = entry["tvg_logo"]?.asString ?: entry["banner"]?.asString ?: "",
                genre       = safeGenre(entry["genre"]),
                year        = year,
                rating      = if (rating > 0) String.format("%.1f", rating) else "",
                quality     = bestLink.quality.ifEmpty { "HD" },
                duration    = entry["duration"]?.asString ?: "",
                streamUrl   = bestLink.url,
                trailerUrl  = bestLink.watchPage,
                language    = catName,
                isTrending  = false,
                isFeatured  = false
            ))
        }
        return result
    }

    /** FORMAT M3/M4/M6 — JSON array of movie objects */
    private fun parseMoviesFromArray(arr: JsonArray, categoryLabel: String): List<Movie> {
        val catName = categoryLabel.ifEmpty { "Movie" }
        val result = mutableListOf<Movie>()
        var mid = 1

        arr.take(MOVIES_LIMIT).forEach { el ->
            if (!el.isJsonObject) return@forEach
            val obj = el.asJsonObject

            // stream URL — বিভিন্ন field name support
            val streamUrl = listOf("stream_url","url","video_url","hls_url","mp4_url","link")
                .mapNotNull { obj[it]?.asString?.takeIf { s -> s.isNotBlank() } }
                .firstOrNull() ?: return@forEach

            val title = obj["title"]?.asString
                ?: obj["name"]?.asString
                ?: "Movie $mid"

            val poster = obj["poster"]?.asString
                ?: obj["poster_path"]?.let { p ->
                    if (p.asString.startsWith("http")) p.asString
                    else "https://image.tmdb.org/t/p/w500${p.asString}"
                } ?: obj["thumbnail"]?.asString ?: obj["image"]?.asString ?: ""

            val year = obj["year"]?.asString
                ?: obj["release_date"]?.asString?.take(4)
                ?: obj["release_year"]?.asString ?: ""

            val rating = safeDouble(obj["rating"] ?: obj["vote_average"] ?: obj["imdb_rating"])

            result.add(Movie(
                id          = "movie_${catName}_${mid++}",
                title       = title,
                description = obj["description"]?.asString ?: obj["overview"]?.asString ?: "",
                poster      = poster,
                banner      = obj["banner"]?.asString ?: obj["backdrop_path"]?.asString ?: poster,
                genre       = safeGenre(obj["genre"] ?: obj["genres"]),
                year        = year,
                rating      = if (rating > 0) String.format("%.1f", rating) else "",
                quality     = obj["quality"]?.asString ?: "HD",
                duration    = obj["duration"]?.asString ?: obj["runtime"]?.asString ?: "",
                streamUrl   = streamUrl,
                trailerUrl  = obj["trailer"]?.asString ?: obj["trailer_url"]?.asString ?: "",
                language    = obj["language"]?.asString ?: catName,
                isTrending  = obj["trending"]?.asBoolean ?: false,
                isFeatured  = obj["featured"]?.asBoolean ?: false
            ))
        }
        return result
    }

    /** FORMAT M5 — { "Movie Name": "https://..." } simple map */
    private fun parseMoviesFormatM5(obj: JsonObject, categoryLabel: String): List<Movie> {
        val catName = categoryLabel.ifEmpty { "Movie" }
        val result = mutableListOf<Movie>()
        var mid = 1
        obj.entrySet().take(MOVIES_LIMIT).forEach { (name, urlEl) ->
            val url = urlEl.asString.takeIf { it.isNotBlank() } ?: return@forEach
            result.add(Movie(
                id          = "movie_${catName}_${mid++}",
                title       = cleanTitle(name, ""),
                description = "",
                poster      = "",
                banner      = "",
                genre       = "",
                year        = "",
                rating      = "",
                quality     = "HD",
                duration    = "",
                streamUrl   = url,
                trailerUrl  = "",
                language    = catName,
                isTrending  = false,
                isFeatured  = false
            ))
        }
        return result
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  LIVE TV PARSER — সব format auto-detect করে
    // ═══════════════════════════════════════════════════════════════════════════
    //
    //  FORMAT L1 — object with "channels" key (assets format):
    //    { "channels": { "Bangla": [{ "name":"NTV","logo":"...","url":"..." }] } }
    //
    //  FORMAT L2 — array of category_collection:
    //    [{ "type":"category_collection","category_name":"Bangla",
    //       "items":{ "NTV":{ "tvg_logo":"...","links":[{"url":"..."}] } } }]
    //
    //  FORMAT L3 — flat object: { "Group": [{ "name":"...","url":"..." }] }
    //
    //  FORMAT L4 — simple array: [{ "name":"...","logo":"...","url":"...","group":"..." }]
    //
    //  FORMAT L5 — M3U-style JSON: [{ "tvg_name":"...","tvg_logo":"...","url":"..." }]
    //
    private fun parseLiveTv(url: String): List<Channel> {
        if (url.isBlank()) return loadLiveTvFromAssets()
        val json = fetchUrl(url) ?: return loadLiveTvFromAssets()
        if (json.isBlank()) return loadLiveTvFromAssets()

        return try {
            val root: JsonElement = gson.fromJson(json, JsonElement::class.java)

            val channels = when {
                root.isJsonArray  -> parseLiveTvFromArray(root.asJsonArray)
                root.isJsonObject -> parseLiveTvFromObject(root.asJsonObject)
                else              -> emptyList()
            }

            channels.ifEmpty { loadLiveTvFromAssets() }
        } catch (e: Exception) {
            loadLiveTvFromAssets()
        }
    }

    private fun parseLiveTvFromArray(arr: JsonArray): List<Channel> {
        if (arr.isEmpty) return emptyList()
        val result = mutableListOf<Channel>()
        var id = 1
        val first = arr[0]

        // FORMAT L2 — category_collection array
        if (first.isJsonObject && first.asJsonObject.has("items")) {
            arr.forEach { el ->
                if (!el.isJsonObject) return@forEach
                val obj = el.asJsonObject
                val group = obj["category_name"]?.asString?.trim() ?: "TV"
                val items = obj["items"]?.asJsonObject ?: return@forEach
                items.entrySet().forEach { (channelName, itemEl) ->
                    if (!itemEl.isJsonObject) return@forEach
                    val item = itemEl.asJsonObject
                    val streamUrl = extractFirstUrl(item["links"])
                    if (channelName.isNotEmpty() && streamUrl.isNotEmpty()) {
                        result.add(Channel(
                            id        = "live_${id++}",
                            name      = channelName.trim(),
                            logo      = item["tvg_logo"]?.asString ?: "",
                            streamUrl = streamUrl,
                            category  = group,
                            isLive    = true
                        ))
                    }
                }
            }
            return result
        }

        // FORMAT L4 / L5 — flat array of channel objects
        arr.forEach { el ->
            if (!el.isJsonObject) return@forEach
            val obj = el.asJsonObject
            val name = obj["name"]?.asString
                ?: obj["tvg_name"]?.asString
                ?: obj["channel_name"]?.asString
                ?: return@forEach
            val streamUrl = listOf("url","stream_url","hls_url","link","stream")
                .mapNotNull { obj[it]?.asString?.takeIf { s -> s.isNotBlank() } }
                .firstOrNull() ?: return@forEach
            val logo = obj["logo"]?.asString
                ?: obj["tvg_logo"]?.asString
                ?: obj["icon"]?.asString ?: ""
            val group = obj["group"]?.asString
                ?: obj["category"]?.asString
                ?: obj["group_title"]?.asString ?: "TV"
            result.add(Channel(
                id        = "live_${id++}",
                name      = name.trim(),
                logo      = logo,
                streamUrl = streamUrl,
                category  = group.trim(),
                isLive    = true
            ))
        }
        return result
    }

    private fun parseLiveTvFromObject(obj: JsonObject): List<Channel> {
        val result = mutableListOf<Channel>()
        var id = 1

        // FORMAT L1 — { "channels": { "Group": [...] } }
        if (obj.has("channels")) {
            val channelsEl = obj["channels"]
            if (channelsEl.isJsonObject) {
                channelsEl.asJsonObject.entrySet().forEach { (group, listEl) ->
                    if (!listEl.isJsonArray) return@forEach
                    listEl.asJsonArray.forEach { itemEl ->
                        if (!itemEl.isJsonObject) return@forEach
                        val item = itemEl.asJsonObject
                        val name = item["name"]?.asString ?: return@forEach
                        val url  = item["url"]?.asString  ?: return@forEach
                        if (name.isNotBlank() && url.isNotBlank()) {
                            result.add(Channel(
                                id        = "live_${id++}",
                                name      = name.trim(),
                                logo      = item["logo"]?.asString ?: "",
                                streamUrl = url,
                                category  = group.trim(),
                                isLive    = true
                            ))
                        }
                    }
                }
                return result
            }
        }

        // FORMAT L3 — flat group→array object (no "channels" wrapper)
        obj.entrySet().forEach { (group, listEl) ->
            if (!listEl.isJsonArray) return@forEach
            listEl.asJsonArray.forEach { itemEl ->
                if (!itemEl.isJsonObject) return@forEach
                val item = itemEl.asJsonObject
                val name = item["name"]?.asString ?: item["tvg_name"]?.asString ?: return@forEach
                val url  = listOf("url","stream_url","hls_url","link")
                    .mapNotNull { item[it]?.asString?.takeIf { s -> s.isNotBlank() } }
                    .firstOrNull() ?: return@forEach
                if (name.isNotBlank() && url.isNotBlank()) {
                    result.add(Channel(
                        id        = "live_${id++}",
                        name      = name.trim(),
                        logo      = item["logo"]?.asString ?: item["tvg_logo"]?.asString ?: "",
                        streamUrl = url,
                        category  = group.trim(),
                        isLive    = true
                    ))
                }
            }
        }
        return result
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  SERIES PARSER — সব format auto-detect করে
    // ═══════════════════════════════════════════════════════════════════════════
    //
    //  FORMAT S1 — title-keyed with links array (existing format):
    //    { "Breaking Bad": { "tvg_logo":"...","year":"2008",
    //      "links":[{"url":"...","season":1,"episode":1,"episode_title":"Pilot"}] } }
    //
    //  FORMAT S2 — array of series objects with episodes:
    //    [{ "title":"...","poster":"...","year":"...",
    //       "episodes":[{"season":1,"episode":1,"title":"...","url":"..."}] }]
    //
    //  FORMAT S3 — nested season structure:
    //    { "Series Title": { "poster":"...",
    //      "seasons": { "1": [{"ep":1,"title":"...","url":"..."}] } } }
    //
    //  FORMAT S4 — flat episode list array:
    //    [{ "series":"Breaking Bad","season":1,"episode":1,
    //       "title":"Pilot","url":"...","poster":"..." }]
    //
    //  FORMAT S5 — "type":"series_collection" wrapper (fmftp.net style, single stream per title):
    //    { "type":"series_collection","category_name":"English tv series",
    //      "items": { "Spider-Noir": { "year":"2026","tvg_logo":"...","rating":8.89,
    //                 "genre":["Crime","Drama"],"watch_page":"...","stream_url":"..." } } }
    //  (genre may be a string OR an array — handled by safeGenre())
    //
    private fun parseSeriesFromUrl(url: String, categoryLabel: String): List<Series> {
        if (url.isBlank()) return emptyList()
        val json = fetchUrl(url) ?: return emptyList()
        if (json.isBlank()) return emptyList()

        return try {
            val root: JsonElement = gson.fromJson(json, JsonElement::class.java)
            when {
                root.isJsonArray -> parseSeriesFromArray(root.asJsonArray, categoryLabel)
                root.isJsonObject -> {
                    val obj = root.asJsonObject
                    // FORMAT S5 — "type":"series_collection" wrapper → unwrap "items"
                    if (obj.has("type") &&
                        obj["type"].isJsonPrimitive &&
                        obj["type"].asString == "series_collection" &&
                        obj.has("items") && obj["items"].isJsonObject) {
                        val catName = categoryLabel.ifEmpty {
                            obj["category_name"]?.asString ?: "Series"
                        }
                        parseSeriesFromObject(obj["items"].asJsonObject, catName)
                    } else {
                        parseSeriesFromObject(obj, categoryLabel)
                    }
                }
                else -> emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** FORMAT S1 & S3 — object keyed by title */
    private fun parseSeriesFromObject(obj: JsonObject, categoryLabel: String): List<Series> {
        val catName = categoryLabel.ifEmpty { "Series" }
        val result  = mutableListOf<Series>()
        var sid = 1

        obj.entrySet().forEach { (title, el) ->
            if (!el.isJsonObject) return@forEach
            val item = el.asJsonObject

            val poster  = item["tvg_logo"]?.asString ?: item["poster"]?.asString ?: ""
            val year    = item["year"]?.asString ?: ""
            val seasons = mutableListOf<Season>()

            when {
                // FORMAT S3 — "seasons" key with nested structure
                item.has("seasons") && item["seasons"].isJsonObject -> {
                    item["seasons"].asJsonObject.entrySet()
                        .sortedBy { it.key.toIntOrNull() ?: 0 }
                        .forEach { (seasonNum, epListEl) ->
                            if (!epListEl.isJsonArray) return@forEach
                            val sNum = seasonNum.toIntOrNull() ?: 1
                            val episodes = epListEl.asJsonArray.mapIndexedNotNull { idx, epEl ->
                                if (!epEl.isJsonObject) return@mapIndexedNotNull null
                                val ep = epEl.asJsonObject
                                val epUrl = listOf("url","stream_url","link","hls_url")
                                    .mapNotNull { ep[it]?.asString?.takeIf { s -> s.isNotBlank() } }
                                    .firstOrNull() ?: return@mapIndexedNotNull null
                                val epNum = ep["ep"]?.asInt ?: ep["episode"]?.asInt ?: (idx + 1)
                                Episode(
                                    id            = "ep_${sid}_${sNum}_$epNum",
                                    episodeNumber = epNum,
                                    title         = ep["title"]?.asString ?: "Episode $epNum",
                                    thumbnail     = ep["thumbnail"]?.asString ?: poster,
                                    duration      = ep["duration"]?.asString ?: "",
                                    seasonName    = "Season $sNum",
                                    streamUrl     = epUrl,
                                    description   = ep["description"]?.asString ?: ""
                                )
                            }
                            if (episodes.isNotEmpty())
                                seasons.add(Season(sNum, "Season $sNum", episodes))
                        }
                }

                // FORMAT S1 — "links" array flat
                item.has("links") && item["links"].isJsonArray -> {
                    val seasonMap = mutableMapOf<Int, MutableList<SeriesLink>>()
                    item["links"].asJsonArray.forEach { linkEl ->
                        if (!linkEl.isJsonObject) return@forEach
                        val lk = linkEl.asJsonObject
                        val epUrl = listOf("url","stream_url","link")
                            .mapNotNull { lk[it]?.asString?.takeIf { s -> s.isNotBlank() } }
                            .firstOrNull() ?: return@forEach
                        val sNum  = lk["season"]?.asInt  ?: 1
                        val epNum = lk["episode"]?.asInt ?: 1
                        seasonMap.getOrPut(sNum) { mutableListOf() }.add(
                            SeriesLink(
                                url          = epUrl,
                                season       = sNum,
                                episode      = epNum,
                                episodeTitle = lk["episode_title"]?.asString ?: "",
                                language     = lk["language"]?.asString ?: catName,
                                quality      = lk["quality"]?.asString ?: "HD"
                            )
                        )
                    }
                    seasonMap.entries.sortedBy { it.key }.forEach { (sNum, links) ->
                        val episodes = links.sortedBy { it.episode }.map { lk ->
                            Episode(
                                id            = "ep_${sid}_${sNum}_${lk.episode}",
                                episodeNumber = lk.episode,
                                title         = lk.episodeTitle.ifEmpty { "Episode ${lk.episode}" },
                                thumbnail     = poster,
                                duration      = "",
                                seasonName    = "Season $sNum",
                                streamUrl     = lk.url,
                                description   = ""
                            )
                        }
                        if (episodes.isNotEmpty())
                            seasons.add(Season(sNum, "Season $sNum", episodes))
                    }
                }

                // FORMAT S5 — single "stream_url" (no season/episode breakdown)
                item.has("stream_url") && item["stream_url"].isJsonPrimitive -> {
                    val epUrl = item["stream_url"].asString
                    if (epUrl.isNotBlank()) {
                        seasons.add(
                            Season(
                                1, "Season 1",
                                listOf(
                                    Episode(
                                        id            = "ep_${sid}_1_1",
                                        episodeNumber = 1,
                                        title         = "Full Episode",
                                        thumbnail     = poster,
                                        duration      = "",
                                        seasonName    = "Season 1",
                                        streamUrl     = epUrl,
                                        description   = item["description"]?.asString ?: ""
                                    )
                                )
                            )
                        )
                    }
                }
            }

            if (seasons.isNotEmpty()) {
                result.add(Series(
                    id           = "series_${catName}_${sid++}",
                    title        = title,
                    description  = item["description"]?.asString ?: "",
                    poster       = poster,
                    banner       = item["banner"]?.asString ?: item["backdrop"]?.asString ?: poster,
                    genre        = safeGenre(item["genre"]),
                    year         = year,
                    rating       = safeDouble(item["rating"]).let {
                        if (it > 0) String.format("%.1f", it) else ""
                    },
                    quality      = "HD",
                    language     = item["language"]?.asString ?: catName,
                    trailerUrl   = item["trailer"]?.asString ?: item["watch_page"]?.asString ?: "",
                    totalSeasons = seasons.size,
                    seasons      = seasons,
                    isTrending   = false
                ))
            }
        }
        return result
    }

    /** FORMAT S2 & S4 — JSON array */
    private fun parseSeriesFromArray(arr: JsonArray, categoryLabel: String): List<Series> {
        val catName = categoryLabel.ifEmpty { "Series" }

        // FORMAT S4 — flat episode list: group by series name first
        val firstEl = arr.firstOrNull()
        if (firstEl?.isJsonObject == true && firstEl.asJsonObject.has("series")) {
            val grouped = mutableMapOf<String, MutableList<JsonObject>>()
            arr.forEach { el ->
                if (!el.isJsonObject) return@forEach
                val obj = el.asJsonObject
                val name = obj["series"]?.asString ?: return@forEach
                grouped.getOrPut(name) { mutableListOf() }.add(obj)
            }
            return parseSeriesFromObject(
                JsonObject().also { root ->
                    grouped.forEach { (name, eps) ->
                        val seriesObj = JsonObject()
                        val linksArr  = JsonArray()
                        eps.forEach { ep ->
                            val lk = JsonObject()
                            lk.addProperty("url",           ep["url"]?.asString ?: ep["stream_url"]?.asString ?: "")
                            lk.addProperty("season",        ep["season"]?.asInt ?: 1)
                            lk.addProperty("episode",       ep["episode"]?.asInt ?: 1)
                            lk.addProperty("episode_title", ep["title"]?.asString ?: "")
                            linksArr.add(lk)
                        }
                        seriesObj.add("links", linksArr)
                        seriesObj.addProperty("tvg_logo", eps.firstOrNull()?.get("poster")?.asString ?: "")
                        root.add(name, seriesObj)
                    }
                },
                catName
            )
        }

        // FORMAT S2 — array of series with episodes array
        val result = mutableListOf<Series>()
        var sid = 1
        arr.forEach { el ->
            if (!el.isJsonObject) return@forEach
            val obj    = el.asJsonObject
            val title  = obj["title"]?.asString ?: obj["name"]?.asString ?: return@forEach
            val poster = obj["poster"]?.asString ?: obj["thumbnail"]?.asString ?: ""
            val year   = obj["year"]?.asString ?: ""
            val seasons = mutableListOf<Season>()

            val epsEl = obj["episodes"] ?: obj["links"]
            if (epsEl?.isJsonArray == true) {
                val seasonMap = mutableMapOf<Int, MutableList<JsonObject>>()
                epsEl.asJsonArray.forEach { epEl ->
                    if (!epEl.isJsonObject) return@forEach
                    val ep   = epEl.asJsonObject
                    val sNum = ep["season"]?.asInt ?: 1
                    seasonMap.getOrPut(sNum) { mutableListOf() }.add(ep)
                }
                seasonMap.entries.sortedBy { it.key }.forEach { (sNum, eps) ->
                    val episodes = eps.sortedBy { it["episode"]?.asInt ?: 0 }.mapIndexedNotNull { idx, ep ->
                        val epUrl = listOf("url","stream_url","link","hls_url")
                            .mapNotNull { ep[it]?.asString?.takeIf { s -> s.isNotBlank() } }
                            .firstOrNull() ?: return@mapIndexedNotNull null
                        val epNum = ep["episode"]?.asInt ?: (idx + 1)
                        Episode(
                            id            = "ep_${sid}_${sNum}_$epNum",
                            episodeNumber = epNum,
                            title         = ep["title"]?.asString ?: ep["episode_title"]?.asString ?: "Episode $epNum",
                            thumbnail     = ep["thumbnail"]?.asString ?: poster,
                            duration      = ep["duration"]?.asString ?: "",
                            seasonName    = "Season $sNum",
                            streamUrl     = epUrl,
                            description   = ep["description"]?.asString ?: ""
                        )
                    }
                    if (episodes.isNotEmpty())
                        seasons.add(Season(sNum, "Season $sNum", episodes))
                }
            }

            if (seasons.isNotEmpty()) {
                result.add(Series(
                    id           = "series_${catName}_${sid++}",
                    title        = title,
                    description  = obj["description"]?.asString ?: obj["overview"]?.asString ?: "",
                    poster       = poster,
                    banner       = obj["banner"]?.asString ?: poster,
                    genre        = safeGenre(obj["genre"]),
                    year         = year,
                    rating       = safeDouble(obj["rating"]).let {
                        if (it > 0) String.format("%.1f", it) else ""
                    },
                    quality      = "HD",
                    language     = obj["language"]?.asString ?: catName,
                    trailerUrl   = obj["trailer"]?.asString ?: "",
                    totalSeasons = seasons.size,
                    seasons      = seasons,
                    isTrending   = false
                ))
            }
        }
        return result
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Helper Functions
    // ═══════════════════════════════════════════════════════════════════════════

    /** links array থেকে সব MovieLink parse করো */
    private fun parseMovieLinks(el: JsonElement?): List<MovieLink> {
        if (el == null || !el.isJsonArray) return emptyList()
        return el.asJsonArray.mapNotNull { linkEl ->
            if (!linkEl.isJsonObject) return@mapNotNull null
            val lk = linkEl.asJsonObject
            val url = lk["url"]?.asString ?: return@mapNotNull null
            if (url.isBlank()) return@mapNotNull null
            MovieLink(
                url       = url,
                language  = lk["language"]?.asString  ?: "",
                quality   = lk["quality"]?.asString   ?: "",
                watchPage = lk["watch_page"]?.asString ?: "",
                filename  = lk["filename"]?.asString  ?: "",
                source    = lk["source"]?.asString    ?: ""
            )
        }
    }

    /** links array থেকে প্রথম non-empty URL বের করো */
    private fun extractFirstUrl(el: JsonElement?): String {
        if (el == null || !el.isJsonArray) return ""
        el.asJsonArray.forEach { linkEl ->
            if (linkEl.isJsonObject) {
                val url = linkEl.asJsonObject["url"]?.asString
                if (!url.isNullOrBlank()) return url
            }
        }
        return ""
    }

    private fun safeDouble(el: JsonElement?): Double {
        if (el == null || el.isJsonNull) return 0.0
        return try { el.asDouble } catch (e: Exception) { 0.0 }
    }

    /** "genre" field স্ট্রিং ("Drama") অথবা array (["Drama","Mystery"]) দুই ফরম্যাট থেকেই safe ভাবে parse করে */
    private fun safeGenre(el: JsonElement?): String {
        if (el == null || el.isJsonNull) return ""
        return try {
            when {
                el.isJsonArray -> el.asJsonArray
                    .mapNotNull { g -> g.takeIf { it.isJsonPrimitive }?.asString }
                    .filter { it.isNotBlank() && !it.equals("None", ignoreCase = true) }
                    .joinToString(", ")
                el.isJsonPrimitive -> el.asString
                else -> ""
            }
        } catch (e: Exception) { "" }
    }

    /** FORMAT M1: ID থেকে title তৈরি */
    private fun buildTitle(id: String, category: String, year: String): String {
        val y = if (year.isNotEmpty()) " ($year)" else ""
        return "$category #$id$y"
    }

    /** FORMAT M2: raw key / filename থেকে clean title */
    private fun cleanTitle(rawKey: String, filename: String): String {
        val source = filename.ifEmpty { rawKey }
        val cleaned = source
            .replace(Regex("""\(\d{4}\).*"""), "")
            .replace(Regex("""\[.*?]"""), "")
            .replace(Regex("""\.mkv|\.mp4|\.avi|\.webm""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\d{3,4}p.*"""), "")
            .replace(".", " ").trim()
        return if (cleaned.isNotEmpty())
            cleaned.split(" ").filter { it.isNotEmpty() }
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        else rawKey.replaceFirstChar { it.uppercase() }
    }

    // ─── HTTP Fetch ───────────────────────────────────────────────────────────
    private fun fetchUrl(url: String): String? {
        if (url.isBlank()) return null
        return try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) response.body?.string() else null
        } catch (e: Exception) { null }
    }

    // ─── Assets Loaders ───────────────────────────────────────────────────────
    private fun loadAssetsData(): AppData? = try {
        val json = context.assets.open("data.json").bufferedReader().use { it.readText() }
        gson.fromJson(json, AppData::class.java)
    } catch (e: Exception) { null }

    fun loadLiveTvFromAssets(): List<Channel> {
        return try {
            val json = context.assets.open("live_tv.json").bufferedReader().use { it.readText() }
            val response = gson.fromJson(json, LiveTvResponse::class.java)
            val result = mutableListOf<Channel>()
            var id = 1
            response.channels.forEach { (group, items) ->
                items.forEach { item ->
                    if (item.name.isNotEmpty() && item.url.isNotEmpty()) {
                        result.add(Channel(
                            id        = "live_${id++}",
                            name      = item.name.trim(),
                            logo      = item.logo,
                            streamUrl = item.url,
                            category  = group.trim(),
                            isLive    = true
                        ))
                    }
                }
            }
            result
        } catch (e: Exception) { emptyList() }
    }

    private fun loadFromAssets(): Result<AppData> {
        return try {
            val baseData = loadAssetsData()
                ?: return Result.Error("ডেটা লোড করা যায়নি")
            val liveChannels = loadLiveTvFromAssets()
            val finalData = if (liveChannels.isNotEmpty()) baseData.copy(channels = liveChannels) else baseData
            cachedData = finalData
            Result.Success(finalData)
        } catch (e: Exception) {
            Result.Error("ডেটা লোড করা যায়নি: ${e.message}")
        }
    }

    fun clearCache() { cachedData = null }
}
