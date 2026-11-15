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
import android.webkit.MimeTypeMap
import java.io.File
import java.util.Locale

enum class Cat(val label: String) {
    VIDEO("Video"),
    AUDIO("Audio"),
    DOC("Docs"),
    IMAGE("Images"),
    OTHER("Other")
}

/** A saved file (a download or a screen recording). */
class LibItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val size: Long,
    val durationMs: Long,
    val dateSec: Long,
    val path: String?,
    val cat: Cat,
    val mime: String?
) {
    val title: String get() = name.substringBeforeLast('.')
    val ext: String get() = name.substringAfterLast('.', "").lowercase(Locale.US)
}

object Library {

    private val VIDEO_EXTS = setOf("mp4", "webm", "mkv", "mov", "m4v", "3gp", "ts", "avi", "flv", "wmv")
    private val AUDIO_EXTS = setOf("mp3", "m4a", "aac", "wav", "ogg", "opus", "flac", "wma", "amr", "mid")
    private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")
    private val DOC_EXTS = setOf(
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "csv", "epub", "odt", "md", "html"
    )

    fun categoryFor(name: String, mime: String?): Cat {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
        val m = (mime ?: "").lowercase(Locale.US)
        return when {
            ext in VIDEO_EXTS -> Cat.VIDEO
            ext in AUDIO_EXTS -> Cat.AUDIO
            ext in IMAGE_EXTS -> Cat.IMAGE
            ext in DOC_EXTS -> Cat.DOC
            m.startsWith("video/") -> Cat.VIDEO
            m.startsWith("audio/") -> Cat.AUDIO
            m.startsWith("image/") -> Cat.IMAGE
            m == "application/pdf" || m.startsWith("text/") -> Cat.DOC
            else -> Cat.OTHER
        }
    }

    /** A MIME type that matches the category (MediaStore insists on it). */
    fun mimeFor(name: String, cat: Cat): String {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
        val guess = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        val prefix = when (cat) {
            Cat.VIDEO -> "video/"
            Cat.AUDIO -> "audio/"
            Cat.IMAGE -> "image/"
            else -> ""
        }
        if (guess != null && guess.startsWith(prefix)) return guess
        return when (cat) {
            Cat.VIDEO -> if (ext == "ts") "video/mp2t" else "video/mp4"
            Cat.AUDIO -> "audio/mpeg"
            Cat.IMAGE -> "image/jpeg"
            else -> guess ?: "application/octet-stream"
        }
    }

    /** MediaStore collection + folder for a category. */
    fun collection(cat: Cat): Uri = when (cat) {
        Cat.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        Cat.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        Cat.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        else -> if (Build.VERSION.SDK_INT >= 29) MediaStore.Downloads.EXTERNAL_CONTENT_URI
        else MediaStore.Files.getContentUri("external")
    }

    fun folder(cat: Cat): String = when (cat) {
        Cat.VIDEO -> Environment.DIRECTORY_MOVIES + "/WebRecorder"
        Cat.AUDIO -> Environment.DIRECTORY_MUSIC + "/Vio"
        Cat.IMAGE -> Environment.DIRECTORY_PICTURES + "/Vio"
        else -> Environment.DIRECTORY_DOWNLOADS + "/Vio"
    }

    private fun legacyDirName(cat: Cat): String = when (cat) {
        Cat.VIDEO -> Environment.DIRECTORY_MOVIES
        Cat.AUDIO -> Environment.DIRECTORY_MUSIC
        Cat.IMAGE -> Environment.DIRECTORY_PICTURES
        else -> Environment.DIRECTORY_DOWNLOADS
    }

    fun legacyDir(ctx: Context, cat: Cat): File {
        val d = ctx.getExternalFilesDir(legacyDirName(cat)) ?: ctx.filesDir
        d.mkdirs()
        return d
    }

    fun list(ctx: Context): List<LibItem> {
        val out = ArrayList<LibItem>()
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                for (cat in listOf(Cat.VIDEO, Cat.AUDIO, Cat.IMAGE, Cat.DOC)) {
                    try {
                        queryCollection(ctx, cat, out)
                    } catch (_: Exception) {
                    }
                }
            } else {
                for (cat in Cat.values()) {
                    if (cat == Cat.OTHER) continue
                    val files = legacyDir(ctx, cat).listFiles() ?: continue
                    for (f in files) {
                        if (!f.isFile) continue
                        val c = categoryFor(f.name, null)
                        out.add(
                            LibItem(
                                f.absolutePath.hashCode().toLong(), Uri.fromFile(f), f.name, f.length(),
                                0L, f.lastModified() / 1000, f.absolutePath, c, null
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
        }
        return out.sortedByDescending { it.dateSec }
    }

    private fun queryCollection(ctx: Context, cat: Cat, out: MutableList<LibItem>) {
        val base = collection(cat)
        val proj = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.MIME_TYPE
        )
        val sel = MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ? AND " +
            MediaStore.MediaColumns.IS_PENDING + " = 0"
        val args = arrayOf(folder(cat) + "%")
        val cursor = ctx.contentResolver.query(base, proj, sel, args, MediaStore.MediaColumns.DATE_ADDED + " DESC")
        cursor?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val name = c.getString(1) ?: "file"
                val mime = c.getString(5)
                val real = if (cat == Cat.DOC) categoryFor(name, mime).let { if (it == Cat.DOC) it else Cat.OTHER } else cat
                out.add(
                    LibItem(
                        id, ContentUris.withAppendedId(base, id), name, c.getLong(2), c.getLong(3),
                        c.getLong(4), null, real, mime
                    )
                )
            }
        }
    }

    /** A small preview image (call from a background thread). */
    fun thumb(ctx: Context, item: LibItem): Bitmap? {
        if (item.cat != Cat.VIDEO && item.cat != Cat.IMAGE && item.cat != Cat.AUDIO) return null
        try {
            if (Build.VERSION.SDK_INT >= 29 && item.path == null) {
                return ctx.contentResolver.loadThumbnail(item.uri, Size(240, 240), null)
            }
        } catch (_: Exception) {
        }
        if (item.cat != Cat.VIDEO) return null
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

    /** Free space on the phone's storage, in bytes. */
    fun freeBytes(): Long = try {
        Environment.getExternalStorageDirectory().usableSpace
    } catch (_: Exception) {
        0L
    }
}
