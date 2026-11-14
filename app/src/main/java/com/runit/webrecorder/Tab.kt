package com.runit.webrecorder

import android.webkit.WebView
import java.util.concurrent.atomic.AtomicInteger

/**
 * One browser tab: its own WebView, its own list of detected videos.
 * A private tab saves nothing to Recent and does not use the page cache.
 */
class Tab(
    val id: Int,
    val isPrivate: Boolean,
    val webView: WebView,
    val sniffer: VideoSniffer
) {
    var title: String = ""
    var url: String = ""
    var lastUsed: Long = System.currentTimeMillis()

    /** Ads / trackers blocked on the current page. */
    val blocked = AtomicInteger(0)

    /** If we upgraded this page to https: the original http address, to fall back to if it fails. */
    @Volatile var httpFallback: String? = null

    /** An http address we already tried to upgrade and failed: load it as is. */
    @Volatile var skipUpgradeUrl: String? = null

    /** A tab that has not loaded anything yet (shows the start page). */
    val isBlank: Boolean
        get() = url.isEmpty()
}
