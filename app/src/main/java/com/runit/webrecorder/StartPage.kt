package com.runit.webrecorder

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class Shortcut(val title: String, val url: String)

/** The shortcuts shown on the start page (kept in order, up to 12). */
object StartPage {

    private lateinit var prefs: SharedPreferences
    const val MAX = 12

    fun init(ctx: Context) {
        if (!::prefs.isInitialized) {
            prefs = ctx.applicationContext.getSharedPreferences("vio_start", Context.MODE_PRIVATE)
        }
    }

    fun all(): List<Shortcut> {
        val out = ArrayList<Shortcut>()
        try {
            val arr = JSONArray(prefs.getString("shortcuts", "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(Shortcut(o.optString("t"), o.getString("u")))
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun save(list: List<Shortcut>) {
        val arr = JSONArray()
        for (s in list) {
            val o = JSONObject()
            o.put("t", s.title)
            o.put("u", s.url)
            arr.put(o)
        }
        prefs.edit().putString("shortcuts", arr.toString()).apply()
    }

    /** Turns "example.com" into "https://example.com". Returns null if it isn't an address. */
    fun cleanUrl(input: String): String? {
        val t = input.trim()
        if (t.isEmpty() || t.contains(' ')) return null
        if (t.startsWith("http://") || t.startsWith("https://")) return t
        if (!t.contains('.')) return null
        return "https://$t"
    }

    fun add(title: String, url: String): String? {
        val u = cleanUrl(url) ?: return "Type a web address, like example.com"
        val list = all().toMutableList()
        if (list.size >= MAX) return "You can have up to $MAX shortcuts"
        if (list.any { it.url == u }) return "That page is already on your start page"
        val name = title.trim().ifEmpty { u.substringAfter("://").removePrefix("www.").substringBefore('/') }
        list.add(Shortcut(name, u))
        save(list)
        return null
    }

    fun has(url: String): Boolean = all().any { it.url == url }

    fun update(index: Int, title: String, url: String): String? {
        val u = cleanUrl(url) ?: return "Type a web address, like example.com"
        val list = all().toMutableList()
        if (index !in list.indices) return null
        val name = title.trim().ifEmpty { u.substringAfter("://").removePrefix("www.").substringBefore('/') }
        list[index] = Shortcut(name, u)
        save(list)
        return null
    }

    fun remove(index: Int) {
        val list = all().toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            save(list)
        }
    }

    fun move(index: Int, delta: Int) {
        val list = all().toMutableList()
        val to = index + delta
        if (index !in list.indices || to !in list.indices) return
        val s = list.removeAt(index)
        list.add(to, s)
        save(list)
    }
}
