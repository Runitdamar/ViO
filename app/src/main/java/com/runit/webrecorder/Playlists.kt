package com.runit.webrecorder

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class PlaylistEntry(val uri: String, val title: String)

class Playlist(val name: String, val items: MutableList<PlaylistEntry>)

/** Playlists of saved audio / video, kept in SharedPreferences as JSON (local to this phone). */
object Playlists {

    private lateinit var prefs: SharedPreferences

    fun init(ctx: Context) {
        if (!::prefs.isInitialized) {
            prefs = ctx.applicationContext.getSharedPreferences("vio_playlists", Context.MODE_PRIVATE)
        }
    }

    fun all(): List<Playlist> {
        val out = ArrayList<Playlist>()
        try {
            val arr = JSONArray(prefs.getString("lists", "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val items = ArrayList<PlaylistEntry>()
                val ia = o.optJSONArray("items") ?: JSONArray()
                for (j in 0 until ia.length()) {
                    val e = ia.getJSONObject(j)
                    items.add(PlaylistEntry(e.getString("uri"), e.optString("title", "")))
                }
                out.add(Playlist(o.getString("name"), items))
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun save(lists: List<Playlist>) {
        val arr = JSONArray()
        for (p in lists) {
            val o = JSONObject()
            o.put("name", p.name)
            val ia = JSONArray()
            for (e in p.items) {
                val eo = JSONObject()
                eo.put("uri", e.uri)
                eo.put("title", e.title)
                ia.put(eo)
            }
            o.put("items", ia)
            arr.put(o)
        }
        prefs.edit().putString("lists", arr.toString()).apply()
    }

    fun find(name: String): Playlist? = all().firstOrNull { it.name == name }

    /** Creates a playlist; returns false if the name is empty or taken. */
    fun create(name: String): Boolean {
        val n = name.trim()
        if (n.isEmpty()) return false
        val lists = all().toMutableList()
        if (lists.any { it.name.equals(n, true) }) return false
        lists.add(Playlist(n, ArrayList()))
        save(lists)
        return true
    }

    fun rename(old: String, newName: String): Boolean {
        val n = newName.trim()
        if (n.isEmpty()) return false
        val lists = all()
        if (lists.any { it.name.equals(n, true) && it.name != old }) return false
        val target = lists.firstOrNull { it.name == old } ?: return false
        val renamed = Playlist(n, target.items)
        save(lists.map { if (it.name == old) renamed else it })
        return true
    }

    fun delete(name: String) {
        save(all().filter { it.name != name })
    }

    /** Adds a file to a playlist (no duplicates). */
    fun addTo(name: String, uri: String, title: String): Boolean {
        val lists = all()
        val p = lists.firstOrNull { it.name == name } ?: return false
        if (p.items.any { it.uri == uri }) return false
        p.items.add(PlaylistEntry(uri, title))
        save(lists)
        return true
    }

    fun removeAt(name: String, index: Int) {
        val lists = all()
        val p = lists.firstOrNull { it.name == name } ?: return
        if (index in p.items.indices) p.items.removeAt(index)
        save(lists)
    }
}
