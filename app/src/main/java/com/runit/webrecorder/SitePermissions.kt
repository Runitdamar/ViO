package com.runit.webrecorder

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/** What a site may use. */
enum class Perm(val label: String, val key: String) {
    CAMERA("Camera", "c"),
    MIC("Microphone", "m"),
    LOCATION("Location", "l")
}

/** ASK = ask each time. */
enum class Decision(val label: String) { ASK("Ask"), ALLOW("Allow"), BLOCK("Block") }

/**
 * Per-site camera / microphone / location choices. Saved on the phone for normal tabs.
 * Private tabs never save anything: their choices are forgotten when the app closes.
 */
object SitePermissions {

    private lateinit var prefs: SharedPreferences
    private val session = HashMap<String, MutableMap<Perm, Decision>>()

    fun init(ctx: Context) {
        if (!::prefs.isInitialized) {
            prefs = ctx.applicationContext.getSharedPreferences("vio_site_perms", Context.MODE_PRIVATE)
        }
    }

    private fun load(): JSONObject = try {
        JSONObject(prefs.getString("sites", "{}") ?: "{}")
    } catch (_: Exception) {
        JSONObject()
    }

    fun get(host: String, perm: Perm, isPrivate: Boolean = false): Decision {
        if (isPrivate) return session[host]?.get(perm) ?: Decision.ASK
        val o = load().optJSONObject(host) ?: return Decision.ASK
        return Decision.values().getOrElse(o.optInt(perm.key, 0)) { Decision.ASK }
    }

    fun set(host: String, perm: Perm, d: Decision, isPrivate: Boolean = false) {
        if (host.isEmpty()) return
        if (isPrivate) {
            session.getOrPut(host) { HashMap() }[perm] = d
            return
        }
        val all = load()
        val o = all.optJSONObject(host) ?: JSONObject()
        if (d == Decision.ASK) o.remove(perm.key) else o.put(perm.key, d.ordinal)
        if (o.length() == 0) all.remove(host) else all.put(host, o)
        prefs.edit().putString("sites", all.toString()).apply()
    }

    fun reset(host: String) {
        val all = load()
        all.remove(host)
        prefs.edit().putString("sites", all.toString()).apply()
    }

    fun resetAll() {
        prefs.edit().putString("sites", "{}").apply()
        session.clear()
    }

    /** Sites that have at least one saved choice. */
    fun savedHosts(): List<String> {
        val all = load()
        val out = ArrayList<String>()
        val it = all.keys()
        while (it.hasNext()) out.add(it.next())
        return out.sorted()
    }
}
