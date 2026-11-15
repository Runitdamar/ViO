package com.runit.webrecorder

import android.content.Context
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Runs downloads (1-3 at a time, see Settings.maxDownloads) and keeps every VideoItem's progress up to date.
 * Supports pause / resume / cancel and pause-all / resume-all. Finished files go to the folder for their
 * category (Movies/WebRecorder, Music/Vio, Pictures/Vio, Download/Vio).
 */
object DownloadCenter {

    private val pool = ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS, LinkedBlockingQueue<Runnable>()).also {
        it.allowCoreThreadTimeOut(true)
    }
    private val jobs = ConcurrentHashMap<Int, VideoItem>()

    /** Short messages for the UI to show as toasts. */
    @Volatile
    var onEvent: ((String) -> Unit)? = null

    @Volatile
    var onChange: (() -> Unit)? = null

    /** Every video that was ever started in this run, newest first. */
    fun all(): List<VideoItem> = jobs.values.sortedByDescending { it.id }

    fun activeCount(): Int = jobs.values.count { it.isBusy() }

    fun pausedCount(): Int = jobs.values.count { it.state == VideoItem.State.PAUSED }

    fun failedCount(): Int = jobs.values.count { it.state == VideoItem.State.FAILED }

    /** Applies Settings.maxDownloads to the worker pool (safe to call any time). */
    fun applyParallelism() {
        val n = Settings.maxDownloads.coerceIn(1, 3)
        try {
            if (n > pool.maximumPoolSize) {
                pool.maximumPoolSize = n
                pool.corePoolSize = n
            } else {
                pool.corePoolSize = n
                pool.maximumPoolSize = n
            }
        } catch (_: Exception) {
        }
    }

    fun pauseAll() {
        for (it in jobs.values) if (it.isBusy()) it.control = 1
        onChange?.invoke()
    }

    fun resumeAll(ctx: Context) {
        for (it in jobs.values.sortedBy { x -> x.id }) {
            if (it.state == VideoItem.State.PAUSED) enqueue(ctx, it)
        }
    }

    fun retryFailed(ctx: Context) {
        for (it in jobs.values.sortedBy { x -> x.id }) {
            if (it.state == VideoItem.State.FAILED) enqueue(ctx, it)
        }
    }

    /** True if this URL is already being downloaded (or waiting). */
    fun hasActiveUrl(url: String): Boolean = jobs.values.any { it.isActive() && it.url == url }

    fun enqueue(ctx: Context, item: VideoItem, variant: HlsVariant? = null) {
        if (item.isBusy()) return
        if (item.problem != null) {
            onEvent?.invoke(item.problem ?: "Can't download this")
            return
        }
        if (variant != null) item.chosenVariant = variant
        if (item.state != VideoItem.State.PAUSED) {
            item.progress = 0
            item.doneBytes = 0
        }
        item.control = 0
        item.state = VideoItem.State.QUEUED
        item.message = ""
        item.speedBps = 0
        item.etaSec = -1
        jobs[item.id] = item
        val app = ctx.applicationContext
        applyParallelism()
        DownloadService.sync(app)
        onChange?.invoke()
        pool.execute { process(app, item) }
    }

    fun pause(item: VideoItem) {
        if (item.isBusy()) {
            item.control = 1
        }
    }

    fun resume(ctx: Context, item: VideoItem) {
        if (item.state == VideoItem.State.PAUSED) enqueue(ctx, item)
    }

    fun cancel(ctx: Context, item: VideoItem) {
        when {
            item.isBusy() -> item.control = 2
            item.state == VideoItem.State.PAUSED || item.state == VideoItem.State.FAILED -> {
                discard(item)
                item.state = VideoItem.State.IDLE
                item.progress = 0
                item.message = ""
                item.speedBps = 0
                jobs.remove(item.id)
                DownloadService.sync(ctx.applicationContext)
                onChange?.invoke()
            }
            else -> {}
        }
    }

    /** Remove a finished / failed row from the global list. */
    fun forget(item: VideoItem) {
        if (!item.isActive()) {
            jobs.remove(item.id)
            onChange?.invoke()
        }
    }

    fun tempDir(ctx: Context): File {
        val d = File(ctx.getExternalFilesDir(null) ?: ctx.cacheDir, "tmp")
        d.mkdirs()
        return d
    }

    /** Called when the app process starts: leftovers from a killed run can't be resumed. */
    fun cleanTemp(ctx: Context) {
        if (jobs.isNotEmpty()) return
        try {
            tempDir(ctx).listFiles()?.forEach { it.delete() }
        } catch (_: Exception) {
        }
    }

    private fun discard(item: VideoItem) {
        item.engineState = null
        // temp files are named <id>.part / <id>_v.bin / <id>_a.bin / <id>.mp4
        // (the dir is only known with a context, so leftovers are cleaned on next start)
    }

    private fun process(ctx: Context, item: VideoItem) {
        val dir = tempDir(ctx)
        when (item.control) {
            1 -> {
                item.state = VideoItem.State.PAUSED
                item.speedBps = 0
                finishRound(ctx)
                return
            }
            2 -> {
                cleanFiles(dir, item)
                resetToIdle(item)
                finishRound(ctx)
                return
            }
        }

        item.state = VideoItem.State.DOWNLOADING
        item.message = if (item.kind == VideoItem.Kind.HLS) "Preparing..." else ""
        onChange?.invoke()

        try {
            MetadataFetcher.probeNow(item)
            if (item.problem != null) throw java.io.IOException(item.problem)

            val tracker = Tracker(item, item.sizeBytes)
            val result: Pair<File, String> = if (item.kind == VideoItem.Kind.HLS) {
                item.message = ""
                HlsDownloader.download(item, dir, tracker)
            } else {
                Pair(DirectDownloader.download(item, dir, tracker), Naming.directExt(item))
            }

            item.message = "Saving..."
            item.speedBps = 0
            item.etaSec = -1
            onChange?.invoke()
            val name = Naming.finalName(item, result.second)
            val cat = if (item.generic) Library.categoryFor(name, item.mime) else Cat.VIDEO
            val mime = if (item.generic) Library.mimeFor(name, cat) else Naming.mimeFor(result.second)
            val saved = Publisher.publish(ctx, result.first, name, mime, cat)
            item.cat = cat
            cleanFiles(dir, item)
            item.engineState = null
            item.message = saved
            item.progress = 100
            item.state = VideoItem.State.DONE
            onEvent?.invoke("Saved to " + Library.folder(cat) + ": " + saved)
            DownloadService.notifyDone(ctx, saved)
        } catch (e: Interrupted) {
            if (e.kind == 1) {
                item.state = VideoItem.State.PAUSED
                item.speedBps = 0
                item.etaSec = -1
            } else {
                cleanFiles(dir, item)
                resetToIdle(item)
            }
        } catch (e: Exception) {
            item.message = e.message ?: "unknown error"
            item.state = VideoItem.State.FAILED
            item.speedBps = 0
            item.etaSec = -1
            onEvent?.invoke("Download failed: ${item.message}")
        }
        item.control = 0
        finishRound(ctx)
    }

    private fun resetToIdle(item: VideoItem) {
        item.engineState = null
        item.state = VideoItem.State.IDLE
        item.progress = 0
        item.message = ""
        item.speedBps = 0
        item.etaSec = -1
        item.control = 0
        jobs.remove(item.id)
    }

    private fun cleanFiles(dir: File, item: VideoItem) {
        for (n in listOf("${item.id}.part", "${item.id}_v.bin", "${item.id}_a.bin", "${item.id}.mp4")) {
            try {
                File(dir, n).delete()
            } catch (_: Exception) {
            }
        }
    }

    private fun finishRound(ctx: Context) {
        DownloadService.sync(ctx)
        onChange?.invoke()
    }
}
