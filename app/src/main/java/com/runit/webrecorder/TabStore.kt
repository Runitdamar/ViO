package com.runit.webrecorder

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class ClosedTab(val title: String, val url: String, val time: Long)

/** A pinned tab as saved between launches. */
class SavedTab(val title: String, val url: String, val group: String?)

/**
 * Remembers recently closed tabs and pinned tabs on the phone.
 * Private tabs are never saved.
 */
object TabStore {

    private lateinit var prefs: SharedPreferences
    private const val MAX_CLOSED = 20

    fun init(ctx: Context) {
        if (!::prefs.isInitialized) {
            prefs = ctx.applicationContext.getSharedPreferences("vio_tabs", Context.MODE_PRIVATE)
        }
    }

    fun closed(): List<ClosedTab> {
        val out = ArrayList<ClosedTab>()
        try {
            val arr = JSONArray(prefs.getString("closed", "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(ClosedTab(o.optString("t"), o.getString("u"), o.optLong("ts")))
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun saveClosed(list: List<ClosedTab>) {
        val arr = JSONArray()
        for (c in list) {
            val o = JSONObject()
            o.put("t", c.title)
            o.put("u", c.url)
            o.put("ts", c.time)
            arr.put(o)
        }
        prefs.edit().putString("closed", arr.toString()).apply()
    }

    fun pushClosed(title: String, url: String) {
        if (!url.startsWith("http")) return
        val list = closed().toMutableList()
        list.removeAll { it.url == url }
        list.add(0, ClosedTab(title.ifBlank { url }, url, System.currentTimeMillis()))
        while (list.size > MAX_CLOSED) list.removeAt(list.size - 1)
        saveClosed(list)
    }

    fun removeClosed(url: String) {
        saveClosed(closed().filter { it.url != url })
    }

    fun clearClosed() {
        saveClosed(emptyList())
    }

    fun pinned(): List<SavedTab> {
        val out = ArrayList<SavedTab>()
        try {
            val arr = JSONArray(prefs.getString("pinned", "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(SavedTab(o.optString("t"), o.getString("u"), o.optString("g").ifEmpty { null }))
            }
        } catch (_: Exception) {
        }
        return out
    }

    fun savePinned(list: List<SavedTab>) {
        val arr = JSONArray()
        for (p in list) {
            val o = JSONObject()
            o.put("t", p.title)
            o.put("u", p.url)
            o.put("g", p.group ?: "")
            arr.put(o)
        }
        prefs.edit().putString("pinned", arr.toString()).apply()
    }
}
