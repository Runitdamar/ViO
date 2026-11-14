package com.runit.webrecorder

import android.content.Context
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

/**
 * Ad / tracker blocking, HTTPS upgrade and fingerprint protection.
 * The blocklist is a plain list of host names (assets/blocklist.txt).
 */
object Shields {

    @Volatile
    private var hosts: HashSet<String> = HashSet()

    @Volatile
    var loaded = false
        private set

    /** Loads the list on a background thread. Until it finishes, nothing is blocked. */
    fun load(ctx: Context) {
        if (loaded) return
        val app = ctx.applicationContext
        Thread {
            try {
                val set = HashSet<String>(100_000)
                app.assets.open("blocklist.txt").bufferedReader().useLines { lines ->
                    for (l in lines) {
                        val t = l.trim()
                        if (t.isNotEmpty()) set.add(t)
                    }
                }
                hosts = set
            } catch (_: Exception) {
            }
            loaded = true
        }.start()
    }

    fun size(): Int = hosts.size

    /** True if [host] or one of its parent domains is on the list. */
    fun isBlockedHost(host: String): Boolean {
        var h = host.lowercase()
        val set = hosts
        if (set.isEmpty()) return false
        while (true) {
            if (set.contains(h)) return true
            val i = h.indexOf('.')
            if (i < 0) return false
            h = h.substring(i + 1)
            if (!h.contains('.')) return false
        }
    }

    /** Same website? (the page's own requests are never blocked) */
    fun sameSite(a: String, b: String): Boolean = registrable(a) == registrable(b)

    fun registrable(host: String): String {
        val parts = host.lowercase().split('.')
        if (parts.size <= 2) return host.lowercase()
        val second = parts[parts.size - 2]
        val take = if (second in setOf("co", "com", "org", "net", "gov", "ac", "edu") && parts.last().length == 2) 3 else 2
        return parts.takeLast(take).joinToString(".")
    }

    /** Whether this request should be blocked. */
    fun shouldBlock(requestHost: String?, pageHost: String?, isMainFrame: Boolean): Boolean {
        if (isMainFrame || requestHost.isNullOrEmpty()) return false
        if (!Settings.blockAds) return false
        if (!pageHost.isNullOrEmpty()) {
            if (Settings.shieldsOffFor(pageHost)) return false
            if (sameSite(requestHost, pageHost)) return false
        }
        return isBlockedHost(requestHost)
    }

    fun emptyResponse(): WebResourceResponse =
        WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))

    /** http:// -> https:// (not for local addresses). Returns null if no upgrade applies. */
    fun upgrade(url: String): String? {
        if (!Settings.httpsUpgrade) return null
        if (!url.startsWith("http://")) return null
        val host = url.substring(7).substringBefore('/').substringBefore(':').substringBefore('?')
        if (host == "localhost" || host.startsWith("127.") || host.startsWith("192.168.") ||
            host.startsWith("10.") || host.endsWith(".local")
        ) return null
        return "https://" + url.substring(7)
    }

    /** Chrome-style reduced user agent: hides the phone model. */
    fun reduceUa(ua: String): String =
        ua.replace(Regex("\\(Linux; Android ([0-9.]+); [^)]*\\)"), "(Linux; Android \$1; K)")

    /** YouTube serves ads from its own servers, so a host list can't catch them. This removes the ad data from the player info instead. */
    const val YT_JS = """
(function() {
  var host = location.hostname;
  if (host.indexOf('youtube.com') < 0 && host.indexOf('youtube-nocookie.com') < 0) return;
  var keys = ['adPlacements', 'playerAds', 'adSlots', 'adBreakHeartbeatParams'];
  function prune(o) {
    if (!o || typeof o !== 'object') return o;
    for (var i = 0; i < keys.length; i++) {
      if (keys[i] in o) { try { delete o[keys[i]]; } catch (e) {} }
    }
    if (o.playerResponse) prune(o.playerResponse);
    return o;
  }
  var jp = JSON.parse;
  JSON.parse = function() { return prune(jp.apply(this, arguments)); };
  if (window.Response && Response.prototype.json) {
    var rj = Response.prototype.json;
    Response.prototype.json = function() { return rj.apply(this, arguments).then(prune); };
  }
  var pr;
  try {
    Object.defineProperty(window, 'ytInitialPlayerResponse', {
      configurable: true,
      get: function() { return pr; },
      set: function(v) { pr = prune(v); }
    });
  } catch (e) {}
  var mutedByUs = false;
  setInterval(function() {
    var p = document.querySelector('.html5-video-player.ad-showing, #movie_player.ad-showing');
    var skip = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, .ytm-skip-ad-button');
    if (skip) skip.click();
    var v = document.querySelector('video');
    if (p && v && isFinite(v.duration) && v.duration > 0) {
      v.muted = true;
      mutedByUs = true;
      v.currentTime = v.duration;
    } else if (!p && mutedByUs && v) {
      v.muted = false;
      mutedByUs = false;
    }
  }, 400);
})();
"""

    /** Script that runs before the page's own scripts (needs a recent WebView). */
    fun docStartScript(offHosts: Set<String>): String? {
        if (!Settings.blockAds && !Settings.fingerprintProtection) return null
        val sb = StringBuilder()
        sb.append("(function(){var off=")
        sb.append(org.json.JSONArray(offHosts.toList()).toString())
        sb.append(";")
        sb.append(GUARD_JS)
        if (Settings.blockAds) {
            sb.append(YT_JS)
            sb.append(COSMETIC_JS)
        }
        if (Settings.fingerprintProtection) sb.append(FINGERPRINT_JS)
        sb.append("})();")
        return sb.toString()
    }

    private const val GUARD_JS = """var h=(location.hostname||'').replace(/^www\./,'');if(off.indexOf(h)>=0)return;"""

    /** Hides common ad containers that are left behind on the page. */
    const val COSMETIC_JS = """
(function() {
  if (window.__vioCosmetic) return;
  window.__vioCosmetic = true;
  var css = 'ins.adsbygoogle,.adsbygoogle,[id^="google_ads_"],[id^="div-gpt-ad"],' +
    'iframe[src*="doubleclick.net"],iframe[src*="googlesyndication.com"],iframe[src*="adservice"],' +
    '.ad-slot,.ad-banner,.advert,.advertisement,.sponsored-ad,[data-ad-slot],[data-google-query-id]' +
    '{display:none !important;}';
  function add() {
    var root = document.head || document.documentElement;
    if (!root) return false;
    var s = document.createElement('style');
    s.textContent = css;
    root.appendChild(s);
    return true;
  }
  if (!add()) document.addEventListener('DOMContentLoaded', add);
})();
"""

    /** Adds small noise to canvas readouts and hides hardware details used for fingerprinting. */
    const val FINGERPRINT_JS = """
(function() {
  if (window.__vioFp) return;
  window.__vioFp = true;
  var seed = Math.floor(Math.random() * 255) + 1;
  try {
    Object.defineProperty(navigator, 'hardwareConcurrency', {get: function() { return 4; }});
    Object.defineProperty(navigator, 'deviceMemory', {get: function() { return 4; }});
  } catch (e) {}
  function noisy(data) {
    for (var i = 0; i < data.length; i += 4 * 97) {
      data[i] = (data[i] + seed) & 255;
    }
  }
  try {
    var gid = CanvasRenderingContext2D.prototype.getImageData;
    CanvasRenderingContext2D.prototype.getImageData = function() {
      var img = gid.apply(this, arguments);
      noisy(img.data);
      return img;
    };
    var tdu = HTMLCanvasElement.prototype.toDataURL;
    HTMLCanvasElement.prototype.toDataURL = function() {
      try {
        var c = document.createElement('canvas');
        c.width = this.width;
        c.height = this.height;
        var x = c.getContext('2d');
        x.drawImage(this, 0, 0);
        var img = gid.call(x, 0, 0, c.width, c.height);
        noisy(img.data);
        x.putImageData(img, 0, 0);
        return tdu.apply(c, arguments);
      } catch (e) {
        return tdu.apply(this, arguments);
      }
    };
  } catch (e) {}
  try {
    var gp = WebGLRenderingContext.prototype.getParameter;
    WebGLRenderingContext.prototype.getParameter = function(p) {
      if (p === 37445) return 'Google Inc.';
      if (p === 37446) return 'ANGLE';
      return gp.call(this, p);
    };
  } catch (e) {}
})();
"""
}
