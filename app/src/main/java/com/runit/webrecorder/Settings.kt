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

    // ---- appearance ----

    /** 0 = follow the phone, 1 = light, 2 = dark. */
    var themeMode: Int
        get() = prefs.getInt("theme_mode", 0).coerceIn(0, 2)
        set(v) = prefs.edit().putInt("theme_mode", v).apply()

    /** Index into the accent colour list (see VioTheme). */
    var accent: Int
        get() = prefs.getInt("accent", 0).coerceIn(0, 9)
        set(v) = prefs.edit().putInt("accent", v).apply()

    /** App font: 0 default, 1 serif, 2 monospace, 3 condensed. */
    var uiFont: Int
        get() = prefs.getInt("ui_font", 0).coerceIn(0, 3)
        set(v) = prefs.edit().putInt("ui_font", v).apply()

    /** App text size in percent of the phone's own setting. */
    var uiTextScale: Int
        get() = prefs.getInt("ui_text_scale", 100).coerceIn(80, 140)
        set(v) = prefs.edit().putInt("ui_text_scale", v).apply()

    /** Text size on web pages, percent. */
    var pageTextZoom: Int
        get() = prefs.getInt("page_text_zoom", 100).coerceIn(50, 250)
        set(v) = prefs.edit().putInt("page_text_zoom", v).apply()

    /** Font for pages that don't pick their own: 0 default, 1 serif, 2 monospace. */
    var pageFont: Int
        get() = prefs.getInt("page_font", 0).coerceIn(0, 2)
        set(v) = prefs.edit().putInt("page_font", v).apply()

    // ---- wallpaper ----

    /** 0 none, 1 gradient, 2 photo. */
    var wallpaperKind: Int
        get() = prefs.getInt("wall_kind", 0).coerceIn(0, 2)
        set(v) = prefs.edit().putInt("wall_kind", v).apply()

    var wallpaperPreset: Int
        get() = prefs.getInt("wall_preset", 0)
        set(v) = prefs.edit().putInt("wall_preset", v).apply()

    /** How much the wallpaper is darkened so text stays readable (0-70). */
    var wallpaperDim: Int
        get() = prefs.getInt("wall_dim", 30).coerceIn(0, 70)
        set(v) = prefs.edit().putInt("wall_dim", v.coerceIn(0, 70)).apply()

    // ---- start page layout ----

    /** Order of the start page blocks: greeting, search, tiles, shortcuts, recent. */
    var homeOrder: String
        get() = prefs.getString("home_order", "greeting,search,tiles,shortcuts,recent") ?: "greeting,search,tiles,shortcuts,recent"
        set(v) = prefs.edit().putString("home_order", v).apply()

    var showGreeting: Boolean
        get() = prefs.getBoolean("show_greeting", true)
        set(v) = prefs.edit().putBoolean("show_greeting", v).apply()

    var showTiles: Boolean
        get() = prefs.getBoolean("show_tiles", true)
        set(v) = prefs.edit().putBoolean("show_tiles", v).apply()

    /** Shortcuts per row (3-5). */
    var shortcutCols: Int
        get() = prefs.getInt("shortcut_cols", 4).coerceIn(3, 5)
        set(v) = prefs.edit().putInt("shortcut_cols", v.coerceIn(3, 5)).apply()

    // ---- toolbar ----

    var addressBottom: Boolean
        get() = prefs.getBoolean("address_bottom", false)
        set(v) = prefs.edit().putBoolean("address_bottom", v).apply()

    /** Hide the bottom Home / Tabs / More bar while a page is open. */
    var hideBottomBar: Boolean
        get() = prefs.getBoolean("hide_bottom_bar", false)
        set(v) = prefs.edit().putBoolean("hide_bottom_bar", v).apply()

    var hideBtnShield: Boolean
        get() = prefs.getBoolean("hide_btn_shield", false)
        set(v) = prefs.edit().putBoolean("hide_btn_shield", v).apply()

    var hideBtnTools: Boolean
        get() = prefs.getBoolean("hide_btn_tools", false)
        set(v) = prefs.edit().putBoolean("hide_btn_tools", v).apply()

    var hideBtnSite: Boolean
        get() = prefs.getBoolean("hide_btn_site", false)
        set(v) = prefs.edit().putBoolean("hide_btn_site", v).apply()

    // ---- gestures ----

    fun gesture(key: String): String? = prefs.getString("gesture_$key", null)

    fun setGesture(key: String, value: String?) {
        val e = prefs.edit()
        if (value == null) e.remove("gesture_$key") else e.putString("gesture_$key", value)
        e.apply()
    }
}
