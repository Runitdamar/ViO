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
}
