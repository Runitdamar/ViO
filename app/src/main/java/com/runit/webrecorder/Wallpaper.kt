package com.runit.webrecorder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.ExifInterface
import android.net.Uri
import java.io.File

/** Start page wallpaper: a gradient from the list, or a photo from the gallery. */
object Wallpaper {

    class Preset(val name: String, val c1: Int, val c2: Int)

    val presets = listOf(
        Preset("Aurora", 0xFF4B42F0.toInt(), 0xFF22D3EE.toInt()),
        Preset("Sunset", 0xFFF97316.toInt(), 0xFFDB2777.toInt()),
        Preset("Ocean", 0xFF0EA5E9.toInt(), 0xFF1E3A8A.toInt()),
        Preset("Forest", 0xFF22C55E.toInt(), 0xFF064E3B.toInt()),
        Preset("Candy", 0xFFF472B6.toInt(), 0xFF8B5CF6.toInt()),
        Preset("Peach", 0xFFFDBA74.toInt(), 0xFFFB7185.toInt()),
        Preset("Mono", 0xFF52525B.toInt(), 0xFF09090B.toInt()),
        Preset("Midnight", 0xFF312E81.toInt(), 0xFF0F172A.toInt())
    )

    @Volatile private var photoCache: Bitmap? = null

    private fun file(ctx: Context) = File(ctx.filesDir, "wallpaper.jpg")

    fun gradient(p: Preset): Drawable {
        val g = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(p.c1, p.c2))
        g.shape = GradientDrawable.RECTANGLE
        return g
    }

    /** The picture from the gallery, or null. Cheap after the first call. */
    fun photo(ctx: Context): Bitmap? {
        photoCache?.let { return it }
        val f = file(ctx)
        if (!f.exists()) return null
        val b = try {
            BitmapFactory.decodeFile(f.absolutePath)
        } catch (_: Throwable) {
            null
        }
        photoCache = b
        return b
    }

    /** Copies a gallery picture in at about the size of the screen. Call off the main thread. */
    fun savePhoto(ctx: Context, uri: Uri): Boolean {
        try {
            val dm = ctx.resources.displayMetrics
            val bounds = BitmapFactory.Options()
            bounds.inJustDecodeBounds = true
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

            var sample = 1
            while (bounds.outWidth / (sample * 2) >= dm.widthPixels &&
                bounds.outHeight / (sample * 2) >= dm.heightPixels
            ) sample *= 2
            val opts = BitmapFactory.Options()
            opts.inSampleSize = sample
            var bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return false

            // Phones often store photos sideways and note it in the file: turn it upright
            val orientation = try {
                ctx.contentResolver.openInputStream(uri)?.use {
                    ExifInterface(it).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                } ?: ExifInterface.ORIENTATION_NORMAL
            } catch (_: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }
            val deg = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (deg != 0f) {
                val m = Matrix()
                m.postRotate(deg)
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }

            file(ctx).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            photoCache = bmp
            return true
        } catch (_: Throwable) {
            return false
        }
    }

    fun deletePhoto(ctx: Context) {
        photoCache = null
        try {
            file(ctx).delete()
        } catch (_: Exception) {
        }
    }

    fun hasPhoto(ctx: Context): Boolean = file(ctx).exists()
}
