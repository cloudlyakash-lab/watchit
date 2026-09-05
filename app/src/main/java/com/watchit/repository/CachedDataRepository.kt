package com.watchit.repository

import android.content.Context
import com.google.gson.Gson
import com.watchit.models.AppData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

private const val GITHUB_DATA_URL =
    "https://raw.githubusercontent.com/YOUR_USERNAME/watchit-data/main/data.json"

private const val CACHE_TTL_MS = 15 * 60 * 1000L
private const val PREF_NAME = "watchit_data_cache"
private const val KEY_APP_DATA = "app_data_json"
private const val KEY_CACHE_TIME = "cache_time"

class CachedDataRepository private constructor(private val context: Context) {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val gson = Gson()
    private var memoryCache: AppData? = null
    private var memoryCacheTime: Long = 0L

    private val prefs by lazy {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    companion object {
        @Volatile private var INSTANCE: CachedDataRepository? = null
        fun getInstance(context: Context): CachedDataRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: CachedDataRepository(context.applicationContext).also { INSTANCE = it }
            }
    }

    suspend fun fetchAppData(forceRefresh: Boolean = false): Result<AppData> =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()

            // 1. Memory cache
            if (!forceRefresh && memoryCache != null && now - memoryCacheTime < CACHE_TTL_MS) {
                return@withContext Result.Success(memoryCache!!)
            }

            // 2. Try network
            return@withContext try {
                val request = Request.Builder().url(GITHUB_DATA_URL).build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string()
                        ?: return@withContext Result.Error("Empty response")
                    val data = gson.fromJson(body, AppData::class.java)

                    memoryCache = data
                    memoryCacheTime = now
                    saveToPrefs(data)

                    Result.Success(data)
                } else {
                    loadFromPrefs() ?: Result.Error("HTTP ${response.code}")
                }
            } catch (e: Exception) {
                // 3. Fallback to SharedPrefs cache
                loadFromPrefs() ?: memoryCache?.let { Result.Success(it) }
                ?: Result.Error(e.message ?: "Unknown error")
            }
        }

    private fun saveToPrefs(data: AppData) {
        prefs.edit()
            .putString(KEY_APP_DATA, gson.toJson(data))
            .putLong(KEY_CACHE_TIME, System.currentTimeMillis())
            .apply()
    }

    private fun loadFromPrefs(): Result<AppData>? {
        val json = prefs.getString(KEY_APP_DATA, null) ?: return null
        val time = prefs.getLong(KEY_CACHE_TIME, 0L)
        return try {
            val data = gson.fromJson(json, AppData::class.java)
            memoryCache = data
            memoryCacheTime = time
            Result.Success(data)
        } catch (e: Exception) {
            null
        }
    }

    fun clearCache() {
        memoryCache = null
        memoryCacheTime = 0L
        prefs.edit().clear().apply()
    }
}
