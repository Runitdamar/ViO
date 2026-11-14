package com.runit.webrecorder

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.util.Rational
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView

/** Holds the player so the background-audio notification can stop it. */
object PlayerHolder {
    @Volatile
    var player: ExoPlayer? = null
}

/** Vio's own video player: speed, picture-in-picture, background audio. */
class PlayerActivity : AppCompatActivity() {

    companion object {
        private const val X_URI = "uri"
        private const val X_TITLE = "title"
        private const val X_UA = "ua"
        private const val X_REFERER = "referer"
        private const val X_COOKIE = "cookie"
        private const val X_HLS = "hls"

        fun start(
            ctx: Context,
            uri: String,
            title: String,
            hls: Boolean = false,
            ua: String? = null,
            referer: String? = null,
            cookie: String? = null
        ) {
            val i = Intent(ctx, PlayerActivity::class.java)
            i.putExtra(X_URI, uri)
            i.putExtra(X_TITLE, title)
            i.putExtra(X_HLS, hls)
            if (ua != null) i.putExtra(X_UA, ua)
            if (referer != null) i.putExtra(X_REFERER, referer)
            if (cookie != null) i.putExtra(X_COOKIE, cookie)
            ctx.startActivity(i)
        }
    }

    private lateinit var playerView: PlayerView
    private lateinit var topBar: View
    private lateinit var btnAudio: TextView
    private var player: ExoPlayer? = null
    private var bgAudio = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        playerView = findViewById(R.id.playerView)
        topBar = findViewById(R.id.playerTopBar)
        btnAudio = findViewById(R.id.btnAudio)

        val uri = intent.getStringExtra(X_URI)
        if (uri == null) {
            finish()
            return
        }
        findViewById<TextView>(R.id.playerTitle).text = intent.getStringExtra(X_TITLE) ?: ""
        findViewById<View>(R.id.playerBack).setOnClickListener { finish() }
        findViewById<ImageView>(R.id.btnPip).setOnClickListener { enterPip() }
        btnAudio.setOnClickListener {
            bgAudio = !bgAudio
            updateAudioButton()
            toast(if (bgAudio) "Audio keeps playing when you leave or lock the screen" else "Background audio off")
        }
        updateAudioButton()
        playerView.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { vis -> topBar.visibility = vis }
        )

        startPlayer(uri)
    }

    private fun startPlayer(uri: String) {
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)
        intent.getStringExtra(X_UA)?.let { http.setUserAgent(it) }
        val headers = HashMap<String, String>()
        intent.getStringExtra(X_REFERER)?.let { headers["Referer"] = it }
        intent.getStringExtra(X_COOKIE)?.let { headers["Cookie"] = it }
        if (headers.isNotEmpty()) http.setDefaultRequestProperties(headers)

        val factory = DefaultMediaSourceFactory(DefaultDataSource.Factory(this, http))
        val p = ExoPlayer.Builder(this).setMediaSourceFactory(factory).build()
        p.setWakeMode(C.WAKE_MODE_NETWORK)

        val item = MediaItem.Builder().setUri(Uri.parse(uri))
        if (intent.getBooleanExtra(X_HLS, false)) item.setMimeType("application/x-mpegURL")
        p.setMediaItem(item.build())

        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                toast("Can't play this video: " + (error.message ?: "error"))
            }
        })
        p.prepare()
        p.playWhenReady = true

        player = p
        PlayerHolder.player = p
        playerView.player = p
    }

    private fun updateAudioButton() {
        btnAudio.text = if (bgAudio) "Audio: ON" else "Audio: OFF"
    }

    private fun enterPip() {
        try {
            val vs = player?.videoSize
            var w = vs?.width ?: 0
            var h = vs?.height ?: 0
            if (w <= 0 || h <= 0) {
                w = 16
                h = 9
            }
            var ratio = w.toFloat() / h.toFloat()
            if (ratio > 2.39f) ratio = 2.39f
            if (ratio < 0.42f) ratio = 0.42f
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational((ratio * 1000).toInt(), 1000))
                .build()
            enterPictureInPictureMode(params)
        } catch (_: Exception) {
            toast("Picture-in-picture isn't available")
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val p = player ?: return
        if (p.isPlaying && !bgAudio) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        playerView.useController = !isInPictureInPictureMode
        topBar.visibility = if (isInPictureInPictureMode) View.GONE else topBar.visibility
    }

    override fun onStart() {
        super.onStart()
        val p = player ?: return
        playerView.player = p
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
            .build()
        stopService(Intent(this, PlaybackService::class.java))
        hideSystemBars()
    }

    override fun onStop() {
        val p = player
        if (p != null) {
            if (bgAudio && p.isPlaying) {
                // Keep the sound going, drop the picture
                p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
                    .build()
                playerView.player = null
                val svc = Intent(this, PlaybackService::class.java)
                svc.putExtra("title", intent.getStringExtra(X_TITLE) ?: "Vio")
                try {
                    startForegroundService(svc)
                } catch (_: Exception) {
                }
            } else {
                p.pause()
            }
        }
        super.onStop()
    }

    override fun onDestroy() {
        stopService(Intent(this, PlaybackService::class.java))
        player?.release()
        player = null
        PlayerHolder.player = null
        super.onDestroy()
    }

    private fun hideSystemBars() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
