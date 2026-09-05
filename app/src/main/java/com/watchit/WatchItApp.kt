package com.watchit

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.appcompat.app.AppCompatDelegate
import com.watchit.crash.CrashHandler
import com.watchit.models.ContinueWatching
import com.watchit.models.Movie
import com.watchit.models.Series
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class WatchItApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        CrashHandler.init(this)

        // ✅ App শুরুতেই saved theme apply করো
        val isDark = PreferenceManager.isDarkMode(this)
        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    companion object {
        lateinit var instance: WatchItApp
            private set
    }
}

// ─── Preference Manager ───────────────────────────────────────────────────────
object PreferenceManager {
    private const val PREF_NAME = "watchit_prefs"
    private const val KEY_DARK_MODE = "dark_mode"
    private const val KEY_FAVORITES_MOVIES = "fav_movies"
    private const val KEY_FAVORITES_SERIES = "fav_series"
    private const val KEY_CONTINUE_WATCHING = "continue_watching"
    private const val KEY_SELECTED_SERVER = "selected_server"

    // ── Server URLs ────────────────────────────────────────────────────────────
    val SERVER_URLS = mapOf(
        1 to "https://raw.githubusercontent.com/jahid2177/Mydemoproject/refs/heads/main/server1.json",
        2 to "https://raw.githubusercontent.com/jahid2177/Mydemoproject/refs/heads/main/server2.json",
        3 to "https://raw.githubusercontent.com/jahid2177/Mydemoproject/refs/heads/main/server3.json"
    )

    fun getSelectedServer(context: Context): Int =
        prefs(context).getInt(KEY_SELECTED_SERVER, 1)

    fun setSelectedServer(context: Context, server: Int) =
        prefs(context).edit().putInt(KEY_SELECTED_SERVER, server).apply()

    fun getSelectedServerUrl(context: Context): String =
        SERVER_URLS[getSelectedServer(context)] ?: SERVER_URLS[1]!!

    private val gson = Gson()

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun isDarkMode(context: Context) = prefs(context).getBoolean(KEY_DARK_MODE, true)
    fun setDarkMode(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(KEY_DARK_MODE, enabled).apply()

    fun getFavoriteMovies(context: Context): MutableList<Movie> {
        val json = prefs(context).getString(KEY_FAVORITES_MOVIES, null) ?: return mutableListOf()
        return gson.fromJson(json, object : TypeToken<MutableList<Movie>>() {}.type)
    }
    fun toggleFavoriteMovie(context: Context, movie: Movie) {
        val list = getFavoriteMovies(context)
        if (list.any { it.id == movie.id }) list.removeAll { it.id == movie.id }
        else list.add(movie)
        prefs(context).edit().putString(KEY_FAVORITES_MOVIES, gson.toJson(list)).apply()
    }
    fun isFavoriteMovie(context: Context, movieId: String) =
        getFavoriteMovies(context).any { it.id == movieId }

    fun getFavoriteSeries(context: Context): MutableList<Series> {
        val json = prefs(context).getString(KEY_FAVORITES_SERIES, null) ?: return mutableListOf()
        return gson.fromJson(json, object : TypeToken<MutableList<Series>>() {}.type)
    }
    fun toggleFavoriteSeries(context: Context, series: Series) {
        val list = getFavoriteSeries(context)
        if (list.any { it.id == series.id }) list.removeAll { it.id == series.id }
        else list.add(series)
        prefs(context).edit().putString(KEY_FAVORITES_SERIES, gson.toJson(list)).apply()
    }
    fun isFavoriteSeries(context: Context, seriesId: String) =
        getFavoriteSeries(context).any { it.id == seriesId }

    fun getContinueWatchingList(context: Context): MutableList<ContinueWatching> {
        val json = prefs(context).getString(KEY_CONTINUE_WATCHING, null) ?: return mutableListOf()
        return gson.fromJson(json, object : TypeToken<MutableList<ContinueWatching>>() {}.type)
    }
    fun saveContinueWatching(context: Context, item: ContinueWatching) {
        val list = getContinueWatchingList(context)
        list.removeAll { it.id == item.id }
        list.add(0, item.copy(lastWatched = System.currentTimeMillis()))
        if (list.size > 20) list.removeLastOrNull()
        prefs(context).edit().putString(KEY_CONTINUE_WATCHING, gson.toJson(list)).apply()
    }
    fun getProgress(context: Context, id: String): Long =
        getContinueWatchingList(context).firstOrNull { it.id == id }?.progressMs ?: 0L
}

// ─── Network Utils ────────────────────────────────────────────────────────────
object NetworkUtils {
    fun isConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
