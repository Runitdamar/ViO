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

    /** Hides common ad containers that are left behind on the page. */
    const val COSMETIC_JS = """
(function() {
  if (window.__vioCosmetic) return;
  window.__vioCosmetic = true;
  var css = 'ins.adsbygoogle,.adsbygoogle,[id^="google_ads_"],[id^="div-gpt-ad"],' +
    'iframe[src*="doubleclick.net"],iframe[src*="googlesyndication.com"],iframe[src*="adservice"],' +
    '.ad-slot,.ad-banner,.advert,.advertisement,.sponsored-ad,[data-ad-slot],[data-google-query-id]' +
    '{display:none !important;}';
  var s = document.createElement('style');
  s.textContent = css;
  (document.head || document.documentElement).appendChild(s);
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
