package com.runit.webrecorder

import android.content.Context
import android.content.SharedPreferences

/** User settings (privacy + lock), stored in SharedPreferences. */
object Settings {

    private lateinit var prefs: SharedPreferences

    fun init(ctx: Context) {
        if (!::prefs.isInitialized) {
            prefs = ctx.applicationContext.getSharedPreferences("vio_settings", Context.MODE_PRIVATE)
        }
    }

    var blockAds: Boolean
        get() = prefs.getBoolean("block_ads", true)
        set(v) = prefs.edit().putBoolean("block_ads", v).apply()

    var httpsUpgrade: Boolean
        get() = prefs.getBoolean("https_upgrade", true)
        set(v) = prefs.edit().putBoolean("https_upgrade", v).apply()

    var blockThirdPartyCookies: Boolean
        get() = prefs.getBoolean("block_3p_cookies", true)
        set(v) = prefs.edit().putBoolean("block_3p_cookies", v).apply()

    var fingerprintProtection: Boolean
        get() = prefs.getBoolean("fingerprint", false)
        set(v) = prefs.edit().putBoolean("fingerprint", v).apply()

    var lockApp: Boolean
        get() = prefs.getBoolean("lock_app", false)
        set(v) = prefs.edit().putBoolean("lock_app", v).apply()

    var lockPrivate: Boolean
        get() = prefs.getBoolean("lock_private", false)
        set(v) = prefs.edit().putBoolean("lock_private", v).apply()

    /** Sites where the user switched Shields off. */
    private fun offSet(): MutableSet<String> =
        HashSet(prefs.getStringSet("shields_off", emptySet()) ?: emptySet())

    fun shieldsOffAll(): Set<String> = offSet()

    fun shieldsOffFor(host: String): Boolean = offSet().contains(host)

    fun setShieldsOff(host: String, off: Boolean) {
        val set = offSet()
        if (off) set.add(host) else set.remove(host)
        prefs.edit().putStringSet("shields_off", set).apply()
    }

    /** How many downloads run at the same time (1-3). */
    var maxDownloads: Int
        get() = prefs.getInt("max_downloads", 2).coerceIn(1, 3)
        set(v) = prefs.edit().putInt("max_downloads", v.coerceIn(1, 3)).apply()

    // ---- browsing ----

    var swipeNav: Boolean
        get() = prefs.getBoolean("swipe_nav", true)
        set(v) = prefs.edit().putBoolean("swipe_nav", v).apply()

    var pullRefresh: Boolean
        get() = prefs.getBoolean("pull_refresh", true)
        set(v) = prefs.edit().putBoolean("pull_refresh", v).apply()

    var searchSuggestions: Boolean
        get() = prefs.getBoolean("search_suggest", true)
        set(v) = prefs.edit().putBoolean("search_suggest", v).apply()

    // ---- start page ----

    var showShortcuts: Boolean
        get() = prefs.getBoolean("show_shortcuts", true)
        set(v) = prefs.edit().putBoolean("show_shortcuts", v).apply()

    var showRecent: Boolean
        get() = prefs.getBoolean("show_recent", true)
        set(v) = prefs.edit().putBoolean("show_recent", v).apply()

    /** Name shown in the start page greeting ("Good morning, Runit."). Empty = no name. */
    var greetName: String
        get() = prefs.getString("greet_name", "") ?: ""
        set(v) = prefs.edit().putString("greet_name", v.trim()).apply()

    // ---- desktop mode (per site) ----

    private fun desktopSet(): MutableSet<String> =
        HashSet(prefs.getStringSet("desktop_hosts", emptySet()) ?: emptySet())

    fun isDesktop(host: String): Boolean = host.isNotEmpty() && desktopSet().contains(host)

    fun setDesktop(host: String, on: Boolean) {
        if (host.isEmpty()) return
        val set = desktopSet()
        if (on) set.add(host) else set.remove(host)
        prefs.edit().putStringSet("desktop_hosts", set).apply()
    }
}
