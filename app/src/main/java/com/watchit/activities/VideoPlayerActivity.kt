package com.watchit.activities

import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.rtmp.RtmpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.smoothstreaming.SsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import com.watchit.databinding.ActivityVideoPlayerBinding
import com.watchit.PreferenceManager
import com.watchit.models.ContinueWatching
import com.google.android.material.snackbar.Snackbar

class VideoPlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVideoPlayerBinding
    private var player: ExoPlayer? = null
    private var streamUrl: String = ""
    private var contentId: String = ""
    private var contentTitle: String = ""
    private var contentPoster: String = ""
    private var contentType: String = ""
    private var episodeTitle: String = ""
    private var isFullscreen = false

    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hideTopBar() }
    private val HIDE_DELAY = 3000L

    companion object {
        const val EXTRA_STREAM_URL    = "stream_url"
        const val EXTRA_CONTENT_ID    = "content_id"
        const val EXTRA_TITLE         = "title"
        const val EXTRA_POSTER        = "poster"
        const val EXTRA_TYPE          = "type"
        const val EXTRA_EPISODE_TITLE = "episode_title"

        private const val MIME_HLS  = MimeTypes.APPLICATION_M3U8
        private const val MIME_DASH = MimeTypes.APPLICATION_MPD
        private const val MIME_SS   = MimeTypes.APPLICATION_SS
        private const val MIME_MKV  = MimeTypes.VIDEO_MATROSKA
        private const val MIME_MP4  = MimeTypes.VIDEO_MP4
        private const val MIME_WEBM = MimeTypes.VIDEO_WEBM
        private const val MIME_TS   = "video/mp2t"
        private const val MIME_AVI  = "video/avi"
        private const val MIME_FLV  = "video/x-flv"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
        binding = ActivityVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        streamUrl     = intent.getStringExtra(EXTRA_STREAM_URL)    ?: ""
        contentId     = intent.getStringExtra(EXTRA_CONTENT_ID)    ?: ""
        contentTitle  = intent.getStringExtra(EXTRA_TITLE)         ?: "WatchIT"
        contentPoster = intent.getStringExtra(EXTRA_POSTER)        ?: ""
        contentType   = intent.getStringExtra(EXTRA_TYPE)          ?: "movie"
        episodeTitle  = intent.getStringExtra(EXTRA_EPISODE_TITLE) ?: ""

        binding.tvTitle.text = contentTitle
        binding.btnBack.setOnClickListener { onBackPressed() }
        binding.btnFullscreen.setOnClickListener { toggleFullscreen() }
        binding.root.setOnClickListener { showTopBarTemporarily() }

        if (streamUrl.isBlank()) {
            Toast.makeText(this, "Invalid stream URL", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        initPlayer()
    }

    private fun showTopBarTemporarily() {
        binding.topBar.visibility = View.VISIBLE
        binding.topBar.alpha = 1f
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, HIDE_DELAY)
    }

    private fun hideTopBar() {
        binding.topBar.animate()
            .alpha(0f)
            .setDuration(300)
            .withEndAction {
                binding.topBar.visibility = View.GONE
                binding.topBar.alpha = 1f
            }.start()
    }

    private fun initPlayer() {
        val cleanUrl = streamUrl.trim()

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(
                "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"
            )
            .setConnectTimeoutMs(30_000)
            .setReadTimeoutMs(60_000)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(mapOf(
                "Referer" to getReferer(cleanUrl),
                "Origin"  to getOrigin(cleanUrl),
                "Accept"  to "*/*",
                "Range"   to "bytes=0-"
            ))

        val defaultDataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(defaultDataSourceFactory))
            .build()
            .also { exo ->
                binding.playerView.player = exo
                binding.playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT

                val savedProgress = PreferenceManager.getProgress(this, contentId)
                val mediaSource = buildMediaSource(cleanUrl, httpDataSourceFactory, defaultDataSourceFactory)
                exo.setMediaSource(mediaSource)
                exo.prepare()
                if (savedProgress > 0L) exo.seekTo(savedProgress)
                exo.playWhenReady = true

                // Audio track button (exo_custom_controls.xml এ আছে)
                binding.playerView.findViewById<View>(
                    resources.getIdentifier("btnAudioTrack", "id", packageName)
                )?.setOnClickListener { showAudioTrackDialog(exo) }

                exo.addListener(object : Player.Listener {
                    override fun onTracksChanged(tracks: Tracks) {
                        val audioCount = tracks.groups.count { it.type == C.TRACK_TYPE_AUDIO }
                        binding.playerView.findViewById<View>(
                            resources.getIdentifier("btnAudioTrack", "id", packageName)
                        )?.alpha = if (audioCount > 1) 1f else 0.4f
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        val msg = when (error.errorCode) {
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ->
                                "Network error. Check your connection."
                            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                                "Stream unavailable (HTTP error)."
                            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ->
                                "Unsupported video format."
                            PlaybackException.ERROR_CODE_TIMEOUT ->
                                "Connection timed out."
                            else -> "Playback error: ${error.message}"
                        }
                        Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG)
                            .setAction("Retry") { retryPlayback() }
                            .show()
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        binding.progressBar.visibility =
                            if (state == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
                    }
                })
            }

        showTopBarTemporarily()
    }

    // Audio Track selection dialog
    private fun showAudioTrackDialog(exo: ExoPlayer) {
        val audioGroups = exo.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }

        if (audioGroups.isEmpty()) {
            Toast.makeText(this, "No audio tracks available", Toast.LENGTH_SHORT).show()
            return
        }

        val trackNames = audioGroups.mapIndexed { index, group ->
            val fmt = group.getTrackFormat(0)
            val lang = fmt.language?.uppercase() ?: ""
            val label = fmt.label ?: ""
            val ch = when (fmt.channelCount) {
                1 -> "Mono"; 2 -> "Stereo"; 6 -> "5.1"; 8 -> "7.1"; else -> ""
            }
            listOfNotNull(
                lang.ifEmpty { null },
                label.ifEmpty { null },
                ch.ifEmpty { null }
            ).joinToString(" · ").ifEmpty { "Audio Track ${index + 1}" }
        }.toTypedArray()

        val selectedIndex = audioGroups.indexOfFirst { it.isSelected }.coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle("Audio Track")
            .setSingleChoiceItems(trackNames, selectedIndex) { dialog, which ->
                val override = TrackSelectionOverride(audioGroups[which].mediaTrackGroup, 0)
                exo.trackSelectionParameters = exo.trackSelectionParameters
                    .buildUpon()
                    .setOverrideForType(override)
                    .build()
                dialog.dismiss()
                Toast.makeText(this, "Audio: ${trackNames[which]}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun buildMediaSource(
        url: String,
        httpFactory: DefaultHttpDataSource.Factory,
        defaultFactory: DefaultDataSource.Factory
    ): MediaSource {
        val lower = url.lowercase()
        return when {
            lower.startsWith("rtsp://") ->
                RtspMediaSource.Factory().createMediaSource(MediaItem.Builder().setUri(url).build())
            lower.startsWith("rtmp://") || lower.startsWith("rtmps://") ->
                ProgressiveMediaSource.Factory(RtmpDataSource.Factory()).createMediaSource(MediaItem.fromUri(url))
            lower.contains(".m3u8") || lower.contains("m3u8") ->
                HlsMediaSource.Factory(httpFactory).createMediaSource(
                    MediaItem.Builder().setUri(url).setMimeType(MIME_HLS).build())
            lower.contains(".mpd") ->
                DashMediaSource.Factory(httpFactory).createMediaSource(
                    MediaItem.Builder().setUri(url).setMimeType(MIME_DASH).build())
            lower.contains(".ism") ->
                SsMediaSource.Factory(httpFactory).createMediaSource(
                    MediaItem.Builder().setUri(url).setMimeType(MIME_SS).build())
            lower.contains(".mkv") ->
                ProgressiveMediaSource.Factory(httpFactory).createMediaSource(
                    MediaItem.Builder().setUri(Uri.parse(url)).setMimeType(MIME_MKV).build())
            lower.contains(".webm") ->
                ProgressiveMediaSource.Factory(httpFactory).createMediaSource(
                    MediaItem.Builder().setUri(Uri.parse(url)).setMimeType(MIME_WEBM).build())
            lower.contains(".ts") && !lower.contains("?") ->
                ProgressiveMediaSource.Factory(httpFactory).createMediaSource(
                    MediaItem.Builder().setUri(url).setMimeType(MIME_TS).build())
            lower.contains(".avi") ->
                ProgressiveMediaSource.Factory(defaultFactory).createMediaSource(
                    MediaItem.Builder().setUri(url).setMimeType(MIME_AVI).build())
            lower.contains(".flv") ->
                ProgressiveMediaSource.Factory(httpFactory).createMediaSource(
                    MediaItem.Builder().setUri(url).setMimeType(MIME_FLV).build())
            lower.startsWith("content://") || lower.startsWith("file://") ->
                ProgressiveMediaSource.Factory(defaultFactory).createMediaSource(MediaItem.fromUri(Uri.parse(url)))
            else ->
                DefaultMediaSourceFactory(defaultFactory).createMediaSource(MediaItem.fromUri(url))
        }
    }

    private fun retryPlayback() {
        player?.let { exo ->
            exo.stop(); exo.clearMediaItems()
            val cleanUrl = streamUrl.trim()
            val httpFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36")
                .setConnectTimeoutMs(30_000).setReadTimeoutMs(60_000)
                .setAllowCrossProtocolRedirects(true)
                .setDefaultRequestProperties(mapOf(
                    "Referer" to getReferer(cleanUrl), "Origin" to getOrigin(cleanUrl),
                    "Accept" to "*/*", "Range" to "bytes=0-"))
            val defaultFactory = DefaultDataSource.Factory(this, httpFactory)
            exo.setMediaSource(buildMediaSource(cleanUrl, httpFactory, defaultFactory))
            exo.prepare(); exo.play()
        }
    }

    private fun getReferer(url: String) = try { val u = Uri.parse(url); "${u.scheme}://${u.host}/" } catch (e: Exception) { "" }
    private fun getOrigin(url: String)  = try { val u = Uri.parse(url); "${u.scheme}://${u.host}"  } catch (e: Exception) { "" }

    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        requestedOrientation = if (isFullscreen)
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    override fun onPause() {
        super.onPause()
        hideHandler.removeCallbacks(hideRunnable)
        player?.let { exo ->
            if (contentId.isNotEmpty()) {
                PreferenceManager.saveContinueWatching(this, ContinueWatching(
                    id = contentId, title = contentTitle, poster = contentPoster,
                    type = contentType, streamUrl = streamUrl,
                    progressMs = exo.currentPosition,
                    durationMs = exo.duration.coerceAtLeast(0L),
                    episodeTitle = episodeTitle
                ))
            }
        }
        player?.pause()
    }

    override fun onResume() { super.onResume(); player?.play(); showTopBarTemporarily() }
    override fun onStop()   { super.onStop();   player?.stop() }
    override fun onDestroy() {
        super.onDestroy()
        hideHandler.removeCallbacks(hideRunnable)
        player?.release(); player = null
    }
}
