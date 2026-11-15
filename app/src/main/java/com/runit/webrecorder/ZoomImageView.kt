package com.runit.webrecorder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.ImageView

/** An ImageView you can pinch to zoom, drag around and double-tap to zoom in / out. */
class ZoomImageView @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null
) : ImageView(ctx, attrs) {

    private val m = Matrix()
    private val vals = FloatArray(9)
    private var baseScale = 1f
    private var bmp: Bitmap? = null

    /** Called on a single tap (used to show / hide the toolbars). */
    var onTap: (() -> Unit)? = null

    init {
        scaleType = ScaleType.MATRIX
    }

    private val scaler = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            zoomBy(detector.scaleFactor, detector.focusX, detector.focusY)
            return true
        }
    })

    private val gestures = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (bmp == null) return false
            m.postTranslate(-distanceX, -distanceY)
            fixBounds()
            imageMatrix = m
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (bmp == null) return false
            val cur = currentScale()
            val target = if (cur > baseScale * 1.2f) baseScale else baseScale * 2.5f
            zoomBy(target / cur, e.x, e.y)
            return true
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            onTap?.invoke()
            return true
        }
    })

    fun setBitmap(b: Bitmap?) {
        bmp = b
        setImageBitmap(b)
        resetFit()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        resetFit()
    }

    private fun currentScale(): Float {
        m.getValues(vals)
        return vals[Matrix.MSCALE_X]
    }

    private fun resetFit() {
        val b = bmp ?: return
        if (width == 0 || height == 0) return
        val s = minOf(width.toFloat() / b.width, height.toFloat() / b.height)
        baseScale = s
        m.reset()
        m.postScale(s, s)
        m.postTranslate((width - b.width * s) / 2f, (height - b.height * s) / 2f)
        imageMatrix = m
    }

    private fun zoomBy(factor: Float, fx: Float, fy: Float) {
        val cur = currentScale()
        val target = (cur * factor).coerceIn(baseScale, baseScale * 6f)
        val f = target / cur
        m.postScale(f, f, fx, fy)
        fixBounds()
        imageMatrix = m
    }

    private fun fixBounds() {
        val b = bmp ?: return
        val r = RectF(0f, 0f, b.width.toFloat(), b.height.toFloat())
        m.mapRect(r)
        var dx = 0f
        var dy = 0f
        val vw = width.toFloat()
        val vh = height.toFloat()
        dx = if (r.width() <= vw) (vw - r.width()) / 2f - r.left
        else if (r.left > 0f) -r.left
        else if (r.right < vw) vw - r.right
        else 0f
        dy = if (r.height() <= vh) (vh - r.height()) / 2f - r.top
        else if (r.top > 0f) -r.top
        else if (r.bottom < vh) vh - r.bottom
        else 0f
        m.postTranslate(dx, dy)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaler.onTouchEvent(event)
        if (!scaler.isInProgress) gestures.onTouchEvent(event)
        return true
    }
}
