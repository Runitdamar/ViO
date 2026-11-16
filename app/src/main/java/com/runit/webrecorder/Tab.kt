package com.runit.webrecorder

import android.view.View
import android.webkit.WebView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.webkit.ScriptHandler
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

    /** Pinned tabs stay at the top of the tab list and are kept for the next launch. */
    var pinned = false

    /** Name of the tab group this tab belongs to (null = no group). */
    var group: String? = null

    /** Desktop site mode is on for this tab right now. */
    var desktop = false

    /** What the WebView's user agent / viewport were before desktop mode. */
    var mobileUa: String = ""
    var mobileWide = false
    var mobileOverview = false

    /** The view that is added to the screen: the WebView wrapped in a pull-to-refresh layout. */
    var holder: View? = null
    var refresher: SwipeRefreshLayout? = null

    /** The tab that opened this one (a link or window.open), 0 if none. */
    var openerId = 0

    /** A restored (pinned) tab that has not loaded its page yet: loads when first opened. */
    var pendingLoad: String? = null

    /** Ads / trackers blocked on the current page. */
    val blocked = AtomicInteger(0)

    /** Scripts registered to run at the start of every page. */
    val scripts = ArrayList<ScriptHandler>()

    /** If we upgraded this page to https: the original http address, to fall back to if it fails. */
    @Volatile var httpFallback: String? = null

    /** An http address we already tried to upgrade and failed: load it as is. */
    @Volatile var skipUpgradeUrl: String? = null

    /** A tab that has not loaded anything yet (shows the start page). */
    val isBlank: Boolean
        get() = url.isEmpty()
}
