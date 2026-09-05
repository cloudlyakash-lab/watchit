package com.watchit.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.watchit.models.*
import com.watchit.repository.DataRepository
import com.watchit.repository.Result
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════════════════════════════════════════
//  HOME VIEW MODEL
// ═══════════════════════════════════════════════════════════════════════════════
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DataRepository.getInstance(application)

    private val _isLoading     = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage  = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage

    private val _banners       = MutableLiveData<List<Banner>>(emptyList())
    val banners: LiveData<List<Banner>> = _banners

    private val _popularMovies = MutableLiveData<List<Movie>>(emptyList())
    val popularMovies: LiveData<List<Movie>> = _popularMovies

    private val _trendingSeries = MutableLiveData<List<Series>>(emptyList())
    val trendingSeries: LiveData<List<Series>> = _trendingSeries

    private val _featuredChannels = MutableLiveData<List<Channel>>(emptyList())
    val featuredChannels: LiveData<List<Channel>> = _featuredChannels

    private val _popularStars  = MutableLiveData<List<Star>>(emptyList())
    val popularStars: LiveData<List<Star>> = _popularStars

    init { loadData() }

    fun loadData(forceRefresh: Boolean = false) {
        _isLoading.value   = true
        _errorMessage.value = null
        viewModelScope.launch {
            when (val result = repository.fetchAppData(forceRefresh)) {
                is Result.Success -> {
                    val data = result.data
                    _banners.postValue(data.banners)
                    _popularMovies.postValue(data.movies.take(10))
                    _trendingSeries.postValue(data.series.filter { it.isTrending }.take(10))
                    _featuredChannels.postValue(data.channels.take(8))
                    _popularStars.postValue(data.stars.take(10))
                    _isLoading.postValue(false)
                }
                is Result.Error -> {
                    _errorMessage.postValue(result.message)
                    _isLoading.postValue(false)
                }
                Result.Loading -> _isLoading.postValue(true)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  MOVIES VIEW MODEL  — Genre + Year filter সহ
// ═══════════════════════════════════════════════════════════════════════════════
class MoviesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DataRepository.getInstance(application)

    /** সম্পূর্ণ original list — ফিল্টারের ভিত্তি */
    private val _allMovies = MutableLiveData<List<Movie>>(emptyList())

    /** UI-তে দেখানো হয় (Genre ও Year ফিল্টার প্রয়োগের পর) */
    private val _movies = MutableLiveData<List<Movie>>(emptyList())
    val movies: LiveData<List<Movie>> = _movies

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    // Category chip list (Hollywood / Bollywood / Hindi Dubbed / ...)
    private val _genres = MutableLiveData<List<String>>(emptyList())
    val genres: LiveData<List<String>> = _genres

    private val _years = MutableLiveData<List<String>>(emptyList())
    val years: LiveData<List<String>> = _years

    private var selectedCategory = "সব"
    private var selectedYear     = "সব"

    init { loadMovies() }

    fun loadMovies(forceRefresh: Boolean = false) {
        _isLoading.value = true
        viewModelScope.launch {
            when (val result = repository.fetchAppData(forceRefresh)) {
                is Result.Success -> {
                    val all = result.data.movies
                    _allMovies.postValue(all)

                    // Category: Movie.language থেকে unique list
                    val categoryList = all
                        .map { it.language.trim() }
                        .filter { it.isNotEmpty() }
                        .distinctBy { it.lowercase() }
                        .sortedBy { it.lowercase() }
                    _genres.postValue(categoryList)

                    val yearList = all
                        .map { it.year.trim() }
                        .filter { it.isNotEmpty() }
                        .distinct()
                        .sortedByDescending { it }
                    _years.postValue(yearList)

                    selectedCategory = "সব"
                    selectedYear     = "সব"
                    _movies.postValue(all)
                    _isLoading.postValue(false)
                }
                is Result.Error -> _isLoading.postValue(false)
                Result.Loading  -> {}
            }
        }
    }

    fun filterByGenre(category: String) {
        selectedCategory = category
        applyFilters()
    }

    fun filterByYear(year: String) {
        selectedYear = year
        applyFilters()
    }

    private fun applyFilters() {
        val all = _allMovies.value ?: return
        _movies.postValue(
            all.filter { movie ->
                val catOk = selectedCategory == "সব" ||
                    movie.language.trim().lowercase() == selectedCategory.trim().lowercase()
                val yearOk = selectedYear == "সব" ||
                    movie.year.trim() == selectedYear.trim()
                catOk && yearOk
            }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  SERIES VIEW MODEL  — Genre + Year filter সহ
// ═══════════════════════════════════════════════════════════════════════════════
// ═══════════════════════════════════════════════════════════════════════════════
//  SERIES VIEW MODEL — Category filter সহ
//  Series.language = category (English Series / Hindi Dubbed Series / Hindi Tv Series)
// ═══════════════════════════════════════════════════════════════════════════════
class SeriesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DataRepository.getInstance(application)

    private val _allSeries = MutableLiveData<List<Series>>(emptyList())

    private val _series = MutableLiveData<List<Series>>(emptyList())
    val series: LiveData<List<Series>> = _series

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    // Category chip list (English Series, Hindi Dubbed Series, Hindi Tv Series)
    private val _genres = MutableLiveData<List<String>>(emptyList())
    val genres: LiveData<List<String>> = _genres

    private val _years = MutableLiveData<List<String>>(emptyList())
    val years: LiveData<List<String>> = _years

    private var selectedCategory = "সব"
    private var selectedYear     = "সব"

    init { loadSeries() }

    fun loadSeries(forceRefresh: Boolean = false) {
        _isLoading.value = true
        viewModelScope.launch {
            when (val result = repository.fetchAppData(forceRefresh)) {
                is Result.Success -> {
                    val all = result.data.series
                    _allSeries.postValue(all)

                    // Category: Series.language থেকে unique list
                    val categoryList = all
                        .map { it.language.trim() }
                        .filter { it.isNotEmpty() }
                        .distinctBy { it.lowercase() }
                        .sortedBy { it.lowercase() }
                    _genres.postValue(categoryList)

                    val yearList = all
                        .map { it.year.trim() }
                        .filter { it.isNotEmpty() }
                        .distinct()
                        .sortedByDescending { it }
                    _years.postValue(yearList)

                    selectedCategory = "সব"
                    selectedYear     = "সব"
                    _series.postValue(all)
                    _isLoading.postValue(false)
                }
                is Result.Error -> _isLoading.postValue(false)
                Result.Loading  -> {}
            }
        }
    }

    fun filterByGenre(category: String) {
        selectedCategory = category
        applyFilters()
    }

    fun filterByYear(year: String) {
        selectedYear = year
        applyFilters()
    }

    private fun applyFilters() {
        val all = _allSeries.value ?: return
        _series.postValue(
            all.filter { s ->
                val catOk = selectedCategory == "সব" ||
                    s.language.trim().lowercase() == selectedCategory.trim().lowercase()
                val yearOk = selectedYear == "সব" ||
                    s.year.trim() == selectedYear.trim()
                catOk && yearOk
            }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  LIVE TV VIEW MODEL
// ═══════════════════════════════════════════════════════════════════════════════
class LiveTVViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DataRepository.getInstance(application)

    private val _channels = MutableLiveData<List<Channel>>(emptyList())
    val channels: LiveData<List<Channel>> = _channels

    private val _filteredChannels = MutableLiveData<List<Channel>>(emptyList())
    val filteredChannels: LiveData<List<Channel>> = _filteredChannels

    private val _categories = MutableLiveData<List<String>>(emptyList())
    val categories: LiveData<List<String>> = _categories

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    init { loadChannels() }

    fun loadChannels(forceRefresh: Boolean = false) {
        _isLoading.value = true
        viewModelScope.launch {
            when (val result = repository.fetchAppData(forceRefresh)) {
                is Result.Success -> {
                    val all = result.data.channels
                    _channels.postValue(all)
                    _filteredChannels.postValue(all)
                    val cats = all
                        .map { it.category.trim() }
                        .filter { it.isNotEmpty() }
                        .distinctBy { it.lowercase() }
                        .sortedBy { it.lowercase() }
                    _categories.postValue(cats)
                    _isLoading.postValue(false)
                }
                is Result.Error -> _isLoading.postValue(false)
                Result.Loading  -> {}
            }
        }
    }

    fun filterByCategory(category: String) {
        val all = _channels.value ?: return
        _filteredChannels.value = if (category == "All") all
        else all.filter { it.category.trim().lowercase() == category.trim().lowercase() }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  SEARCH VIEW MODEL
// ═══════════════════════════════════════════════════════════════════════════════
class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DataRepository.getInstance(application)
    private val _results = MutableLiveData<Triple<List<Movie>, List<Series>, List<Channel>>>(
        Triple(emptyList(), emptyList(), emptyList()))
    val results: LiveData<Triple<List<Movie>, List<Series>, List<Channel>>> = _results
    private var appData: AppData? = null

    init {
        viewModelScope.launch {
            val result = repository.fetchAppData()
            if (result is Result.Success) appData = result.data
        }
    }

    fun search(query: String) {
        val data = appData ?: return
        val q = query.lowercase().trim()
        if (q.isEmpty()) {
            _results.value = Triple(emptyList(), emptyList(), emptyList())
            return
        }
        val movies   = data.movies.filter  { it.title.lowercase().contains(q) || it.genre.lowercase().contains(q) }
        val series   = data.series.filter  { it.title.lowercase().contains(q) || it.genre.lowercase().contains(q) }
        val channels = data.channels.filter { it.name.lowercase().contains(q) || it.category.lowercase().contains(q) }
        _results.value = Triple(movies, series, channels)
    }
}
