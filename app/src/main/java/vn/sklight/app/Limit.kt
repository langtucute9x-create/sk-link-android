package vn.sklight.app

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.cos
import kotlin.math.hypot

/**
 * Giới hạn tốc độ của đoạn đường đang chạy, từ hai nguồn:
 * 1) biển đọc được trên màn hình app bản đồ (ScreenReader), tươi trong 10 giây;
 * 2) dữ liệu OpenStreetMap theo vị trí GPS (tag maxspeed), tra qua Overpass khi không có nguồn 1. Cần mạng; nhiều đoạn đường chưa có dữ liệu.
 * Kết quả ghi vào [S.speedLimit] (0 = chưa biết). Chưa kiểm chứng trên xe thật.
 */
object Limit {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var scr = 0
    @Volatile private var scrAt = 0L
    @Volatile private var osm = 0
    @Volatile private var osmAt = 0L
    private var qAt = 0L
    private var qLat = Double.NaN
    private var qLon = Double.NaN
    @Volatile private var busy = false

    /** Biển đọc được trên màn hình (km/h). */
    fun setScreen(v: Int) { scr = v; scrAt = System.currentTimeMillis(); update() }
    fun clearScreen() { scr = 0; update() }
    fun clearOsm() { osm = 0; update() }

    fun update() {
        val now = System.currentTimeMillis()
        val v = if (scr > 0 && now - scrAt < 10000) scr else if (S.limitOsm.value && osm > 0 && now - osmAt < 90000) osm else 0
        main.post { if (S.speedLimit.intValue != v) { S.speedLimit.intValue = v; AlertBridge.refresh() } }
    }

    /** Gọi mỗi lần GPS có vị trí mới. */
    fun onLocation(lat: Double, lon: Double, kmh: Float) {
        update()
        if (!S.limitOsm.value || kmh < 5f || busy) return
        val now = System.currentTimeMillis()
        if (now - qAt < 12000) return
        if (!qLat.isNaN() && now - qAt < 60000 && dist(lat, lon, qLat, qLon) < 40.0) return
        qAt = now; qLat = lat; qLon = lon; busy = true
        Thread {
            try { osm = query(lat, lon); osmAt = System.currentTimeMillis() } catch (_: Exception) { /* mất mạng: giữ giá trị cũ tới khi hết hạn */ }
            busy = false
            update()
        }.start()
    }

    private fun dist(a: Double, b: Double, c: Double, d: Double): Double {
        val k = 111320.0
        return hypot((a - c) * k, (b - d) * k * cos(Math.toRadians(a)))
    }

    /** Khoảng cách (m) từ điểm tới đoạn thẳng AB, tính trên mặt phẳng cục bộ. */
    private fun segDist(lat: Double, lon: Double, a: DoubleArray, b: DoubleArray): Double {
        val k = 111320.0; val kx = k * cos(Math.toRadians(lat))
        val px = (lon - a[1]) * kx; val py = (lat - a[0]) * k
        val bx = (b[1] - a[1]) * kx; val by = (b[0] - a[0]) * k
        val l2 = bx * bx + by * by
        val t = if (l2 < 1e-6) 0.0 else ((px * bx + py * by) / l2).coerceIn(0.0, 1.0)
        return hypot(px - bx * t, py - by * t)
    }

    private fun parse(s: String): Int {
        val t = s.trim().lowercase()
        Regex("^(\\d{2,3})(\\s*km/h)?$").find(t)?.let { return it.groupValues[1].toInt().takeIf { v -> v in 5..130 } ?: 0 }
        Regex("^(\\d{2,3})\\s*mph$").find(t)?.let { return Math.round(it.groupValues[1].toInt() * 1.609).toInt().takeIf { v -> v in 5..130 } ?: 0 }
        return 0
    }

    private fun query(lat: Double, lon: Double): Int {
        val q = "[out:json][timeout:6];way(around:30,$lat,$lon)[\"highway\"][\"maxspeed\"];out tags geom;"
        val c = URL("https://overpass-api.de/api/interpreter").openConnection() as HttpURLConnection
        c.connectTimeout = 4000; c.readTimeout = 7000; c.requestMethod = "POST"; c.doOutput = true
        c.setRequestProperty("User-Agent", "SK-Light/${S.VER}")
        c.outputStream.use { it.write(("data=" + URLEncoder.encode(q, "UTF-8")).toByteArray()) }
        if (c.responseCode != 200) { c.disconnect(); return 0 }
        val body = c.inputStream.bufferedReader().use { it.readText() }
        c.disconnect()
        val els = JSONObject(body).optJSONArray("elements") ?: return 0
        var best = 0; var bestD = Double.MAX_VALUE
        for (i in 0 until els.length()) {
            val e = els.getJSONObject(i)
            val v = parse(e.optJSONObject("tags")?.optString("maxspeed") ?: "")
            if (v == 0) continue
            val g = e.optJSONArray("geometry") ?: continue
            var d = Double.MAX_VALUE
            for (j in 0 until g.length() - 1) {
                val p = g.getJSONObject(j); val n = g.getJSONObject(j + 1)
                d = minOf(d, segDist(lat, lon, doubleArrayOf(p.getDouble("lat"), p.getDouble("lon")), doubleArrayOf(n.getDouble("lat"), n.getDouble("lon"))))
            }
            if (d < bestD) { bestD = d; best = v }
        }
        return if (bestD <= 30.0) best else 0
    }
}
