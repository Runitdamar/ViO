package com.runit.webrecorder

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.text.InputType
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.util.concurrent.Executors

/**
 * Built-in PDF viewer (Android's PdfRenderer): scroll through pages, pinch or use +/- to zoom,
 * tap the page number to jump. Pages are drawn only when they are on screen to save memory.
 */
class PdfViewerActivity : AppCompatActivity() {

    companion object {
        private const val X_URI = "uri"
        private const val X_NAME = "name"
        private const val X_VAULT_ID = "vault_id"
        private const val MAX_BITMAP_W = 1800

        fun start(ctx: Context, uri: String, name: String) {
            val i = Intent(ctx, PdfViewerActivity::class.java)
            i.putExtra(X_URI, uri)
            i.putExtra(X_NAME, name)
            ctx.startActivity(i)
        }

        fun startVault(ctx: Context, vaultId: String, name: String) {
            val i = Intent(ctx, PdfViewerActivity::class.java)
            i.putExtra(X_VAULT_ID, vaultId)
            i.putExtra(X_NAME, name)
            ctx.startActivity(i)
        }
    }

    private val exec = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())

    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private var vaultMode = false

    private lateinit var vScroll: ScrollView
    private lateinit var hScroll: HorizontalScrollView
    private lateinit var pagesBox: LinearLayout
    private lateinit var pageLabel: TextView
    private lateinit var loadingText: TextView

    private val views = ArrayList<ImageView>()
    private val ratios = ArrayList<Float>() // height / width of each page
    private val bitmaps = HashMap<Int, Bitmap>()
    private val rendering = HashSet<Int>()
    private var zoom = 1f
    private var pinch = 1f
    private var closed = false

    private val scaler by lazy {
        ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                pinch *= detector.scaleFactor
                pinch = pinch.coerceIn(0.5f, 3f)
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                setZoom((zoom * pinch).coerceIn(1f, 3f))
                pinch = 1f
            }
        })
    }

    private val visibleCheck = Runnable { updateVisible() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vaultId = intent.getStringExtra(X_VAULT_ID)
        vaultMode = vaultId != null
        if (vaultMode) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_pdf)
        vScroll = findViewById(R.id.pdfVScroll)
        hScroll = findViewById(R.id.pdfHScroll)
        pagesBox = findViewById(R.id.pdfPages)
        pageLabel = findViewById(R.id.pdfPage)
        loadingText = findViewById(R.id.pdfLoading)
        findViewById<TextView>(R.id.pdfTitle).text = (intent.getStringExtra(X_NAME) ?: "PDF").substringBeforeLast('.')
        findViewById<View>(R.id.pdfBack).setOnClickListener { finish() }
        findViewById<View>(R.id.pdfZoomIn).setOnClickListener { setZoom((zoom + 0.5f).coerceAtMost(3f)) }
        findViewById<View>(R.id.pdfZoomOut).setOnClickListener { setZoom((zoom - 0.5f).coerceAtLeast(1f)) }
        pageLabel.setOnClickListener { askPage() }
        vScroll.setOnScrollChangeListener { _, _, _, _, _ ->
            updateLabel()
            ui.removeCallbacks(visibleCheck)
            ui.postDelayed(visibleCheck, 90)
        }
        open(vaultId)
    }

    private fun open(vaultId: String?) {
        val app = applicationContext
        val uriStr = intent.getStringExtra(X_URI)
        exec.execute {
            var err: String? = null
            try {
                val fd: ParcelFileDescriptor = if (vaultId != null) {
                    val e = Vault.entry(app, vaultId) ?: throw IllegalStateException("Not found")
                    ParcelFileDescriptor.open(Vault.decryptToTemp(app, e), ParcelFileDescriptor.MODE_READ_ONLY)
                } else {
                    app.contentResolver.openFileDescriptor(Uri.parse(uriStr), "r")
                        ?: throw IllegalStateException("Can't open")
                }
                pfd = fd
                val r = PdfRenderer(fd)
                renderer = r
                for (i in 0 until r.pageCount) {
                    val p = r.openPage(i)
                    val ratio = if (p.width > 0) p.height.toFloat() / p.width.toFloat() else 1.414f
                    p.close()
                    ratios.add(ratio)
                }
            } catch (e: SecurityException) {
                err = "This PDF is password-protected"
            } catch (e: Exception) {
                err = "Can't open this PDF"
            }
            ui.post {
                if (closed) return@post
                if (err != null || ratios.isEmpty()) {
                    Toast.makeText(this, err ?: "This PDF has no pages", Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    loadingText.visibility = View.GONE
                    buildPages()
                }
            }
        }
    }

    private fun pageWidthPx(): Int {
        val base = if (vScroll.width > 0) vScroll.width else resources.displayMetrics.widthPixels
        return (base * zoom).toInt()
    }

    private fun buildPages() {
        pagesBox.removeAllViews()
        views.clear()
        val w = pageWidthPx()
        for (i in ratios.indices) {
            val iv = ImageView(this)
            iv.setBackgroundColor(Color.WHITE)
            iv.scaleType = ImageView.ScaleType.FIT_XY
            val lp = LinearLayout.LayoutParams(w, (w * ratios[i]).toInt())
            lp.bottomMargin = (8 * resources.displayMetrics.density).toInt()
            pagesBox.addView(iv, lp)
            views.add(iv)
        }
        updateLabel()
        vScroll.post { updateVisible() }
    }

    private fun setZoom(z: Float) {
        if (views.isEmpty() || z == zoom) return
        val total = pagesBox.height.coerceAtLeast(1)
        val frac = vScroll.scrollY.toFloat() / total
        zoom = z
        val w = pageWidthPx()
        for (i in views.indices) {
            val lp = views[i].layoutParams as LinearLayout.LayoutParams
            lp.width = w
            lp.height = (w * ratios[i]).toInt()
            views[i].layoutParams = lp
            views[i].setImageBitmap(null)
        }
        releaseAll()
        pagesBox.post {
            vScroll.scrollTo(0, (pagesBox.height * frac).toInt())
            updateVisible()
        }
    }

    private fun releaseAll() {
        for (b in bitmaps.values) {
            try {
                b.recycle()
            } catch (_: Exception) {
            }
        }
        bitmaps.clear()
    }

    private fun updateLabel() {
        if (views.isEmpty()) return
        val mid = vScroll.scrollY + vScroll.height / 2
        var cur = 0
        for (i in views.indices) {
            if (views[i].top <= mid) cur = i else break
        }
        pageLabel.text = "${cur + 1} / ${views.size}"
    }

    private fun updateVisible() {
        if (closed || views.isEmpty()) return
        val top = vScroll.scrollY - vScroll.height / 2
        val bottom = vScroll.scrollY + vScroll.height + vScroll.height / 2
        val keepTop = vScroll.scrollY - vScroll.height * 2
        val keepBottom = vScroll.scrollY + vScroll.height * 3
        for (i in views.indices) {
            val v = views[i]
            val vt = v.top
            val vb = v.bottom
            if (vb >= top && vt <= bottom) {
                if (!bitmaps.containsKey(i) && !rendering.contains(i)) renderPage(i)
            } else if ((vb < keepTop || vt > keepBottom) && bitmaps.containsKey(i)) {
                v.setImageBitmap(null)
                bitmaps.remove(i)?.recycle()
            }
        }
    }

    private fun renderPage(i: Int) {
        val r = renderer ?: return
        rendering.add(i)
        val w = minOf(pageWidthPx(), MAX_BITMAP_W)
        val h = (w * ratios[i]).toInt().coerceAtLeast(1)
        val zoomAtStart = zoom
        exec.execute {
            var bmp: Bitmap? = null
            try {
                if (!closed) {
                    val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    b.eraseColor(Color.WHITE)
                    val p = r.openPage(i)
                    try {
                        p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    } finally {
                        p.close()
                    }
                    bmp = b
                }
            } catch (_: Throwable) {
                bmp = null
            }
            ui.post {
                rendering.remove(i)
                val b = bmp
                if (b == null) return@post
                if (closed || zoomAtStart != zoom || i >= views.size) {
                    b.recycle()
                    if (!closed && i < views.size) updateVisible()
                    return@post
                }
                bitmaps.put(i, b)?.recycle()
                views[i].setImageBitmap(b)
            }
        }
    }

    private fun askPage() {
        if (views.isEmpty()) return
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER
        input.hint = "1 - ${views.size}"
        AlertDialog.Builder(this)
            .setTitle("Go to page")
            .setView(input)
            .setPositiveButton("Go") { _, _ ->
                val n = input.text.toString().toIntOrNull()
                if (n != null && n in 1..views.size) {
                    vScroll.scrollTo(0, views[n - 1].top)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        scaler.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onDestroy() {
        closed = true
        ui.removeCallbacksAndMessages(null)
        // close on the render thread so no page is open while the renderer is closed
        exec.execute {
            try {
                renderer?.close()
            } catch (_: Exception) {
            }
            try {
                pfd?.close()
            } catch (_: Exception) {
            }
        }
        exec.shutdown()
        releaseAll()
        if (vaultMode) {
            val app = applicationContext
            Thread {
                try {
                    Thread.sleep(300)
                } catch (_: Exception) {
                }
                Vault.clearTemp(app)
            }.start()
        }
        super.onDestroy()
    }
}
