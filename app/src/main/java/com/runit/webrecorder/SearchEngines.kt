package com.runit.webrecorder

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** A search engine. [searchUrl] and [suggestUrl] use %s where the query goes. */
class Engine(val name: String, val searchUrl: String, val suggestUrl: String?, val custom: Boolean)

/** Built-in and user-added search engines, plus address-bar suggestions. */
object SearchEngines {

    private lateinit var prefs: SharedPreferences

    private val builtIn = listOf(
        Engine(
            "Google", "https://www.google.com/search?q=%s",
            "https://suggestqueries.google.com/complete/search?client=firefox&q=%s", false
        ),
        Engine(
            "DuckDuckGo", "https://duckduckgo.com/?q=%s",
            "https://duckduckgo.com/ac/?type=list&q=%s", false
        ),
        Engine(
            "Bing", "https://www.bing.com/search?q=%s",
            "https://api.bing.com/osjson.aspx?query=%s", false
        ),
        Engine(
            "Brave", "https://search.brave.com/search?q=%s",
            "https://search.brave.com/api/suggest?q=%s", false
        ),
        Engine(
            "Ecosia", "https://www.ecosia.org/search?q=%s",
            "https://ac.ecosia.org/autocomplete?type=list&q=%s", false
        )
    )

    fun init(ctx: Context) {
        if (!::prefs.isInitialized) {
            prefs = ctx.applicationContext.getSharedPreferences("vio_engines", Context.MODE_PRIVATE)
        }
    }

    private fun customs(): List<Engine> {
        val out = ArrayList<Engine>()
        try {
            val arr = JSONArray(prefs.getString("custom", "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(Engine(o.getString("n"), o.getString("u"), null, true))
            }
        } catch (_: Exception) {
        }
        return out
    }

    fun all(): List<Engine> = builtIn + customs()

    fun current(): Engine {
        val name = prefs.getString("current", "Google")
        return all().firstOrNull { it.name == name } ?: builtIn[0]
    }

    fun setCurrent(name: String) {
        prefs.edit().putString("current", name).apply()
    }

    /** Adds your own engine. [url] must contain %s. Returns an error text, or null if it worked. */
    fun addCustom(name: String, url: String): String? {
        val n = name.trim()
        val u = url.trim()
        if (n.isEmpty()) return "Type a name"
        if (!u.startsWith("http://") && !u.startsWith("https://")) return "Address must start with https://"
        if (!u.contains("%s")) return "Address must contain %s where the search words go"
        if (all().any { it.name.equals(n, true) }) return "That name is already used"
        val list = customs().toMutableList()
        list.add(Engine(n, u, null, true))
        saveCustoms(list)
        return null
    }

    fun removeCustom(name: String) {
        val list = customs().filter { it.name != name }
        saveCustoms(list)
        if (prefs.getString("current", "Google") == name) setCurrent("Google")
    }

    private fun saveCustoms(list: List<Engine>) {
        val arr = JSONArray()
        for (e in list) {
            val o = JSONObject()
            o.put("n", e.name)
            o.put("u", e.searchUrl)
            arr.put(o)
        }
        prefs.edit().putString("custom", arr.toString()).apply()
    }

    fun searchUrl(query: String): String =
        current().searchUrl.replace("%s", URLEncoder.encode(query, "UTF-8"))

    /**
     * Suggestions for what the user is typing (blocking: call from a background thread).
     * Engines answer in the OpenSearch format: ["query", ["one", "two", ...]].
     */
    fun suggestions(query: String): List<String> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        // custom engines have no suggestion address: use DuckDuckGo's
        val template = current().suggestUrl ?: builtIn[1].suggestUrl ?: return emptyList()
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(template.replace("%s", URLEncoder.encode(q, "UTF-8"))).openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            if (conn.responseCode !in 200..299) return emptyList()
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONArray(text)
            val arr = root.optJSONArray(1) ?: return emptyList()
            val out = ArrayList<String>()
            for (i in 0 until minOf(arr.length(), 8)) {
                val s = arr.optString(i, "")
                if (s.isNotEmpty()) out.add(s)
            }
            out
        } catch (_: Exception) {
            emptyList()
        } finally {
            try {
                conn?.disconnect()
            } catch (_: Exception) {
            }
        }
    }
}
