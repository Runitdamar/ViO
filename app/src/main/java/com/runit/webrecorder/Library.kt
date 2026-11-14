package com.runit.webrecorder

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Size
import java.io.File

/** A saved video (a download or a screen recording) in Movies/WebRecorder. */
class LibItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val size: Long,
    val durationMs: Long,
    val dateSec: Long,
    val path: String?
)

object Library {

    private val VIDEO_EXTS = setOf("mp4", "webm", "mkv", "mov", "m4v", "3gp", "ts")

    fun list(ctx: Context): List<LibItem> {
        val out = ArrayList<LibItem>()
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val proj = arrayOf(
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.SIZE,
                    MediaStore.Video.Media.DURATION,
                    MediaStore.Video.Media.DATE_ADDED
                )
                val sel = MediaStore.Video.Media.RELATIVE_PATH + " LIKE ? AND " +
                    MediaStore.Video.Media.IS_PENDING + " = 0"
                val args = arrayOf("Movies/WebRecorder%")
                val cursor = ctx.contentResolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    proj,
                    sel,
                    args,
                    MediaStore.Video.Media.DATE_ADDED + " DESC"
                )
                cursor?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getLong(0)
                        out.add(
                            LibItem(
                                id,
                                ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id),
                                c.getString(1) ?: "video",
                                c.getLong(2),
                                c.getLong(3),
                                c.getLong(4),
                                null
                            )
                        )
                    }
                }
            } else {
                val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                val files = dir?.listFiles() ?: emptyArray()
                for (f in files.sortedByDescending { it.lastModified() }) {
                    if (!f.isFile || f.extension.lowercase() !in VIDEO_EXTS) continue
                    out.add(
                        LibItem(
                            f.absolutePath.hashCode().toLong(),
                            Uri.fromFile(f),
                            f.name,
                            f.length(),
                            0L,
                            f.lastModified() / 1000,
                            f.absolutePath
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    /** A small preview image (call from a background thread). */
    fun thumb(ctx: Context, item: LibItem): Bitmap? {
        try {
            if (Build.VERSION.SDK_INT >= 29 && item.path == null) {
                return ctx.contentResolver.loadThumbnail(item.uri, Size(240, 240), null)
            }
        } catch (_: Exception) {
        }
        val r = MediaMetadataRetriever()
        return try {
            if (item.path != null) r.setDataSource(item.path) else r.setDataSource(ctx, item.uri)
            r.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (_: Exception) {
            null
        } finally {
            try {
                r.release()
            } catch (_: Exception) {
            }
        }
    }

    /** Deletes the file. Returns false if Android wants the user's OK first. */
    fun delete(ctx: Context, item: LibItem): Boolean {
        return try {
            if (item.path != null) File(item.path).delete()
            else ctx.contentResolver.delete(item.uri, null, null) > 0
        } catch (_: Exception) {
            false
        }
    }
}
