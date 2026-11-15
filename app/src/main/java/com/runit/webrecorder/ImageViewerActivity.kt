package com.runit.webrecorder

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.InputStream
import java.util.concurrent.Executors

/** Built-in image viewer: pinch / double-tap to zoom, swipe buttons to move between images. */
class ImageViewerActivity : AppCompatActivity() {

    companion object {
        private const val X_URIS = "uris"
        private const val X_NAMES = "names"
        private const val X_INDEX = "index"
        private const val X_VAULT = "vault"
        private const val MAX_SIDE = 2400

        /** [uris] are content/file URIs, or vio-vault://<id> when [vault] is true. */
        fun start(ctx: Context, uris: List<String>, names: List<String>, index: Int, vault: Boolean) {
            val i = Intent(ctx, ImageViewerActivity::class.java)
            i.putExtra(X_URIS, uris.toTypedArray())
            i.putExtra(X_NAMES, names.toTypedArray())
            i.putExtra(X_INDEX, index)
            i.putExtra(X_VAULT, vault)
            ctx.startActivity(i)
        }
    }

    private lateinit var zoom: ZoomImageView
    private lateinit var topBar: View
    private lateinit var bottomBar: View
    private lateinit var title: TextView
    private lateinit var counter: TextView
    private lateinit var loading: TextView

    private var uris: List<String> = emptyList()
    private var names: List<String> = emptyList()
    private var index = 0
    private var vault = false
    private var generation = 0
    private val exec = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        uris = intent.getStringArrayExtra(X_URIS)?.toList() ?: emptyList()
        names = intent.getStringArrayExtra(X_NAMES)?.toList() ?: emptyList()
        index = intent.getIntExtra(X_INDEX, 0)
        vault = intent.getBooleanExtra(X_VAULT, false)
        if (uris.isEmpty()) {
            finish()
            return
        }
        if (vault) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_image)
        zoom = findViewById(R.id.imgZoom)
        topBar = findViewById(R.id.imgTopBar)
        bottomBar = findViewById(R.id.imgBottomBar)
        title = findViewById(R.id.imgTitle)
        counter = findViewById(R.id.imgCounter)
        loading = findViewById(R.id.imgLoading)

        findViewById<View>(R.id.imgBack).setOnClickListener { finish() }
        findViewById<View>(R.id.imgPrev).setOnClickListener { go(-1) }
        findViewById<View>(R.id.imgNext).setOnClickListener { go(1) }
        zoom.onTap = {
            val vis = if (topBar.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            topBar.visibility = vis
            bottomBar.visibility = if (uris.size > 1) vis else View.GONE
        }
        bottomBar.visibility = if (uris.size > 1) View.VISIBLE else View.GONE
        index = index.coerceIn(0, uris.size - 1)
        show()
    }

    private fun go(delta: Int) {
        val n = index + delta
        if (n < 0 || n >= uris.size) return
        index = n
        show()
    }

    private fun show() {
        title.text = names.getOrElse(index) { "" }.substringBeforeLast('.')
        counter.text = "${index + 1} / ${uris.size}"
        loading.visibility = View.VISIBLE
        val gen = ++generation
        val u = uris[index]
        val app = applicationContext
        val isVault = vault
        exec.execute {
            var bmp: Bitmap? = null
            var err: String? = null
            try {
                bmp = if (isVault) {
                    val id = Uri.parse(u).host ?: ""
                    val e = Vault.entry(app, id) ?: throw IllegalStateException("Not found")
                    decodeFile(Vault.decryptToTemp(app, e))
                } else {
                    decodeUri(app, Uri.parse(u))
                }
            } catch (e: OutOfMemoryError) {
                err = "Image is too large"
            } catch (e: Exception) {
                err = "Can't open this image"
            }
            runOnUiThread {
                if (gen != generation || isFinishing) return@runOnUiThread
                loading.visibility = View.GONE
                if (bmp != null) zoom.setBitmap(bmp)
                else Toast.makeText(this, err ?: "Can't open this image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sampleFor(w: Int, h: Int): Int {
        var s = 1
        while (w / s > MAX_SIDE || h / s > MAX_SIDE) s *= 2
        return s
    }

    private fun decodeFile(f: File): Bitmap? {
        val o = BitmapFactory.Options()
        o.inJustDecodeBounds = true
        BitmapFactory.decodeFile(f.absolutePath, o)
        val o2 = BitmapFactory.Options()
        o2.inSampleSize = sampleFor(o.outWidth, o.outHeight)
        val b = BitmapFactory.decodeFile(f.absolutePath, o2) ?: return null
        val rot = try {
            f.inputStream().use { rotationOf(it) }
        } catch (_: Exception) {
            0
        }
        return rotate(b, rot)
    }

    private fun decodeUri(ctx: Context, uri: Uri): Bitmap? {
        val o = BitmapFactory.Options()
        o.inJustDecodeBounds = true
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        val o2 = BitmapFactory.Options()
        o2.inSampleSize = sampleFor(o.outWidth, o.outHeight)
        val b = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) }
            ?: return null
        val rot = try {
            ctx.contentResolver.openInputStream(uri)?.use { rotationOf(it) } ?: 0
        } catch (_: Exception) {
            0
        }
        return rotate(b, rot)
    }

    private fun rotationOf(input: InputStream): Int {
        val ex = ExifInterface(input)
        return when (ex.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }

    private fun rotate(b: Bitmap, deg: Int): Bitmap {
        if (deg == 0) return b
        val m = Matrix()
        m.postRotate(deg.toFloat())
        return try {
            Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
        } catch (_: Exception) {
            b
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        exec.shutdownNow()
        if (vault) Vault.clearTemp(applicationContext)
    }
}
