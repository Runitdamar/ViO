package com.runit.webrecorder

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/** A thin segmented bar: one coloured piece per category, then the empty (free) part. */
class StorageBar @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null
) : View(ctx, attrs) {

    private var sizes: LongArray = LongArray(0)
    private var colors: IntArray = IntArray(0)
    private var freeBytes = 0L
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clip = Path()
    private val rect = RectF()

    fun set(sizes: LongArray, colors: IntArray, freeBytes: Long) {
        this.sizes = sizes
        this.colors = colors
        this.freeBytes = freeBytes
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val r = h / 2f
        rect.set(0f, 0f, w, h)
        clip.reset()
        clip.addRoundRect(rect, r, r, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clip)
        paint.color = 0xFFE8E8EE.toInt()
        canvas.drawRect(rect, paint)
        var total = freeBytes
        for (s in sizes) total += s
        if (total > 0) {
            var x = 0f
            for (i in sizes.indices) {
                if (sizes[i] <= 0) continue
                val seg = w * sizes[i].toFloat() / total.toFloat()
                paint.color = colors[i]
                canvas.drawRect(x, 0f, x + maxOf(seg, 2f), h, paint)
                x += seg
            }
        }
        canvas.restore()
    }
}
