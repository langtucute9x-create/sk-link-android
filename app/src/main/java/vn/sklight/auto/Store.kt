package vn.sklight.auto

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Mot trang web da luu (hien thanh o tren man hinh xe). */
data class Site(val name: String, val url: String)

/** Mot video trong may (uri dang content://, da giu quyen doc lau dai). */
data class Vid(val name: String, val uri: String)

/** Luu danh sach trang va video bang SharedPreferences. */
object Store {
    private const val PREFS = "skauto"
    private const val K_SITES = "sites"
    private const val K_VIDS = "videos"

    private val defaults = listOf(
        Site("YouTube", "https://m.youtube.com/"),
        Site("TikTok", "https://www.tiktok.com/foryou"),
        Site("Google", "https://www.google.com/"),
    )

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun sites(c: Context): List<Site> {
        val raw = prefs(c).getString(K_SITES, null) ?: return defaults
        return try {
            val a = JSONArray(raw)
            val out = ArrayList<Site>()
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out.add(Site(o.getString("n"), o.getString("u")))
            }
            out
        } catch (e: Exception) {
            defaults
        }
    }

    fun saveSites(c: Context, list: List<Site>) {
        val a = JSONArray()
        for (s in list) a.put(JSONObject().put("n", s.name).put("u", s.url))
        prefs(c).edit().putString(K_SITES, a.toString()).apply()
    }

    fun videos(c: Context): List<Vid> {
        val raw = prefs(c).getString(K_VIDS, null) ?: return emptyList()
        return try {
            val a = JSONArray(raw)
            val out = ArrayList<Vid>()
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out.add(Vid(o.getString("n"), o.getString("u")))
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveVideos(c: Context, list: List<Vid>) {
        val a = JSONArray()
        for (v in list) a.put(JSONObject().put("n", v.name).put("u", v.uri))
        prefs(c).edit().putString(K_VIDS, a.toString()).apply()
    }
}

/** Cau noi giua man hinh dien thoai va phien Android Auto dang chay. */
object Hub {
    @Volatile
    var renderer: Renderer? = null

    /** Dia chi cho mo khi xe ket noi (dat tu dien thoai luc chua co xe). */
    @Volatile
    var pending: String? = null

    fun takePending(): String? {
        val p = pending
        pending = null
        return p
    }

    /** Bien chu nguoi dung go thanh dia chi: co dau cham va khong co khoang trang thi coi la trang web, con lai thi tim tren YouTube. */
    fun toUrl(text: String): String {
        val t = text.trim()
        if (t.startsWith("http://") || t.startsWith("https://")) return t
        if (t.contains('.') && !t.contains(' ')) return "https://$t"
        return "https://m.youtube.com/results?search_query=" + java.net.URLEncoder.encode(t, "UTF-8")
    }
}

/** Trang chu ve tren man hinh xe: o lon de cham. */
object HomePage {
    const val HOST = "skauto.home"
    const val URL = "https://skauto.home/"

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    fun html(c: Context): String {
        val sb = StringBuilder()
        sb.append("<!doctype html><html><head><meta charset=\"utf-8\">")
        sb.append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
        sb.append("<style>")
        sb.append("body{margin:0;padding:24px 28px;background:#101014;color:#fff;font-family:sans-serif}")
        sb.append("h1{font-size:26px;margin:0 0 6px;color:#FE2CAA}h2{font-size:18px;margin:26px 0 10px;color:#aaa;font-weight:normal}")
        sb.append(".g{display:flex;flex-wrap:wrap;gap:14px}")
        sb.append("a{display:flex;align-items:center;justify-content:center;text-align:center;box-sizing:border-box;")
        sb.append("min-width:200px;max-width:320px;min-height:96px;padding:14px 18px;border-radius:16px;background:#22222a;")
        sb.append("color:#fff;font-size:22px;text-decoration:none;overflow:hidden}")
        sb.append("a.v{background:#2a1c26;font-size:18px}p{color:#888;font-size:16px}")
        sb.append("</style></head><body><h1>SK Auto</h1>")
        sb.append("<h2>Trang web</h2><div class=\"g\">")
        for (s in Store.sites(c)) {
            sb.append("<a href=\"").append(esc(s.url)).append("\">").append(esc(s.name)).append("</a>")
        }
        sb.append("</div><h2>Video trong máy</h2>")
        val vids = Store.videos(c)
        if (vids.isEmpty()) {
            sb.append("<p>Chưa có video. Mở app SK Auto trên điện thoại để chọn video.</p>")
        } else {
            sb.append("<div class=\"g\">")
            for ((i, v) in vids.withIndex()) {
                sb.append("<a class=\"v\" href=\"").append(URL).append("v/").append(i).append("\">").append(esc(v.name)).append("</a>")
            }
            sb.append("</div>")
        }
        sb.append("</body></html>")
        return sb.toString()
    }
}
