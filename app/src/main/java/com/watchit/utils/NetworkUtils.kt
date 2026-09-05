package com.watchit.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.LiveData

// ─── Network LiveData – observes connectivity changes ─────────────────────────
class NetworkLiveData(private val context: Context) : LiveData<Boolean>() {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            postValue(true)
        }
        override fun onLost(network: Network) {
            postValue(false)
        }
        override fun onUnavailable() {
            postValue(false)
        }
    }

    override fun onActive() {
        super.onActive()
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
        // Post current state immediately
        postValue(isConnected())
    }

    override fun onInactive() {
        super.onInactive()
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }

    private fun isConnected(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

// ─── Extension Functions ──────────────────────────────────────────────────────
fun Int.dpToPx(context: Context): Int =
    (this * context.resources.displayMetrics.density).toInt()

fun String.isValidUrl(): Boolean =
    startsWith("http://") || startsWith("https://") || startsWith("rtmp://") ||
    startsWith("udp://") || startsWith("rtp://")

fun Long.toReadableProgress(): String {
    val totalSeconds = this / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}
