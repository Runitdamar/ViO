package com.runit.webrecorder

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.util.Rational
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.RepeatModeUtil
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

/**
 * Vio's own player: video and audio, a play queue (next / previous / shuffle / repeat),
 * speed, picture-in-picture and background audio. Also plays files straight out of the vault.
 */
class PlayerActivity : AppCompatActivity() {

    companion object {
        private const val X_URI = "uri"
        private const val X_TITLE = "title"
        private const val X_UA = "ua"
        private const val X_REFERER = "referer"
        private const val X_COOKIE = "cookie"
        private const val X_HLS = "hls"
        private const val X_URIS = "uris"
        private const val X_TITLES = "titles"
        private const val X_INDEX = "index"
        private const val X_AUDIO = "audio"
        private const val X_VAULT = "vault"
        private const val X_SHUFFLE = "shuffle"

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

        /** Plays a queue of saved files. [vault] = the URIs are vio-vault://<id> entries. */
        fun startList(
            ctx: Context,
            uris: List<String>,
            titles: List<String>,
            index: Int,
            audio: Boolean,
            vault: Boolean = false,
            shuffle: Boolean = false
        ) {
            if (uris.isEmpty()) return
            val i = Intent(ctx, PlayerActivity::class.java)
            i.putExtra(X_URIS, uris.toTypedArray())
            i.putExtra(X_TITLES, titles.toTypedArray())
            i.putExtra(X_INDEX, index)
            i.putExtra(X_AUDIO, audio)
            i.putExtra(X_VAULT, vault)
            i.putExtra(X_SHUFFLE, shuffle)
            ctx.startActivity(i)
        }
    }

    private lateinit var playerView: PlayerView
    private lateinit var topBar: View
    private lateinit var btnAudio: TextView
    private lateinit var titleView: TextView
    private lateinit var audioPanel: View
    private lateinit var audioTitle: TextView
    private lateinit var audioIndex: TextView
    private var player: ExoPlayer? = null
    private var bgAudio = false
    private var audioMode = false
    private var vaultMode = false
    private var titles: List<String> = emptyList()
    private var currentTitle = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vaultMode = intent.getBooleanExtra(X_VAULT, false)
        if (vaultMode) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_player)

        playerView = findViewById(R.id.playerView)
        topBar = findViewById(R.id.playerTopBar)
        btnAudio = findViewById(R.id.btnAudio)
        titleView = findViewById(R.id.playerTitle)
        audioPanel = findViewById(R.id.audioPanel)
        audioTitle = findViewById(R.id.audioTitle)
        audioIndex = findViewById(R.id.audioIndex)

        val list = intent.getStringArrayExtra(X_URIS)
        val single = intent.getStringExtra(X_URI)
        if (list == null && single == null) {
            finish()
            return
        }
        audioMode = intent.getBooleanExtra(X_AUDIO, false)
        bgAudio = audioMode

        findViewById<View>(R.id.playerBack).setOnClickListener { finish() }
        val pip = findViewById<ImageView>(R.id.btnPip)
        pip.setOnClickListener { enterPip() }
        btnAudio.setOnClickListener {
            bgAudio = !bgAudio
            updateAudioButton()
            toast(if (bgAudio) "Audio keeps playing when you leave or lock the screen" else "Background audio off")
        }
        updateAudioButton()
        playerView.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { vis -> topBar.visibility = vis }
        )
        playerView.setShowShuffleButton(true)
        playerView.setRepeatToggleModes(RepeatModeUtil.REPEAT_TOGGLE_MODE_ONE or RepeatModeUtil.REPEAT_TOGGLE_MODE_ALL)
        if (audioMode) {
            pip.visibility = View.GONE
            audioPanel.visibility = View.VISIBLE
            playerView.setControllerShowTimeoutMs(0)
            playerView.setControllerHideOnTouch(false)
        }

        if (list != null) {
            titles = (intent.getStringArrayExtra(X_TITLES) ?: emptyArray()).toList()
            startQueue(list.toList(), intent.getIntExtra(X_INDEX, 0), intent.getBooleanExtra(X_SHUFFLE, false))
        } else {
            titles = listOf(intent.getStringExtra(X_TITLE) ?: "")
            startSingle(single!!)
        }
    }

    private fun buildPlayer(factory: DefaultMediaSourceFactory): ExoPlayer {
        val p = ExoPlayer.Builder(this).setMediaSourceFactory(factory).build()
        p.setWakeMode(C.WAKE_MODE_NETWORK)
        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                toast("Can't play this: " + (error.message ?: "error"))
                if (p.hasNextMediaItem()) {
                    p.seekToNextMediaItem()
                    p.prepare()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                showTitle(p.currentMediaItemIndex, p.mediaItemCount)
            }
        })
        player = p
        PlayerHolder.player = p
        playerView.player = p
        return p
    }

    private fun startSingle(uri: String) {
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)
        intent.getStringExtra(X_UA)?.let { http.setUserAgent(it) }
        val headers = HashMap<String, String>()
        intent.getStringExtra(X_REFERER)?.let { headers["Referer"] = it }
        intent.getStringExtra(X_COOKIE)?.let { headers["Cookie"] = it }
        if (headers.isNotEmpty()) http.setDefaultRequestProperties(headers)

        val p = buildPlayer(DefaultMediaSourceFactory(DefaultDataSource.Factory(this, http)))
        val item = MediaItem.Builder().setUri(Uri.parse(uri))
        if (intent.getBooleanExtra(X_HLS, false)) item.setMimeType("application/x-mpegURL")
        p.setMediaItem(item.build())
        showTitle(0, 1)
        p.prepare()
        p.playWhenReady = true
    }

    private fun startQueue(uris: List<String>, index: Int, shuffle: Boolean) {
        val factory = if (vaultMode) {
            DefaultMediaSourceFactory(VaultDataSource.Factory(this))
        } else {
            DefaultMediaSourceFactory(DefaultDataSource.Factory(this))
        }
        val p = buildPlayer(factory)
        val items = ArrayList<MediaItem>()
        for (i in uris.indices) {
            val md = MediaMetadata.Builder().setTitle(titles.getOrElse(i) { "" }).build()
            items.add(MediaItem.Builder().setUri(Uri.parse(uris[i])).setMediaMetadata(md).build())
        }
        val start = index.coerceIn(0, uris.size - 1)
        p.setMediaItems(items, start, C.TIME_UNSET)
        if (shuffle) p.shuffleModeEnabled = true
        showTitle(start, uris.size)
        p.prepare()
        p.playWhenReady = true
    }

    private fun showTitle(index: Int, count: Int) {
        val t = titles.getOrElse(index) { "" }
        currentTitle = t
        titleView.text = t
        audioTitle.text = t
        audioIndex.text = if (count > 1) "${index + 1} / $count" else ""
    }

    private fun updateAudioButton() {
        btnAudio.text = if (bgAudio) "Audio: ON" else "Audio: OFF"
    }

    private fun enterPip() {
        if (audioMode) return
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
        if (p.isPlaying && !bgAudio && !audioMode && !vaultMode) enterPip()
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
                // never show a vault file's name in a notification
                svc.putExtra("title", if (vaultMode) "Vio" else currentTitle.ifEmpty { "Vio" })
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
