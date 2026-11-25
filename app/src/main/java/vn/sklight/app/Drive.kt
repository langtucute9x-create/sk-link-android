package vn.sklight.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/** Tự dịu đèn ban đêm: tới giờ tối thì hạ độ sáng xuống mức ban đêm, sáng ra trả lại mức ban ngày. */
object Night {
    var active = false
        private set

    /** Giờ mặt trời mọc và lặn hôm nay (phút tính từ 0 giờ, giờ của máy), null nếu chưa có vị trí. */
    fun sunToday(): Pair<Int, Int>? {
        val lat = S.lat; val lon = S.lon
        if (lat.isNaN() || lon.isNaN()) return null
        val cal = Calendar.getInstance()
        val g = 2 * Math.PI / 365 * (cal.get(Calendar.DAY_OF_YEAR) - 1)
        val eq = 229.18 * (0.000075 + 0.001868 * cos(g) - 0.032077 * sin(g) - 0.014615 * cos(2 * g) - 0.040849 * sin(2 * g))
        val d = 0.006918 - 0.399912 * cos(g) + 0.070257 * sin(g) - 0.006758 * cos(2 * g) + 0.000907 * sin(2 * g) - 0.002697 * cos(3 * g) + 0.00148 * sin(3 * g)
        val la = Math.toRadians(lat)
        val c = cos(Math.toRadians(90.833)) / (cos(la) * cos(d)) - tan(la) * tan(d)
        if (c < -1 || c > 1) return null
        val ha = Math.toDegrees(acos(c))
        val tz = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60000.0
        val rise = (720 - 4 * (lon + ha) - eq + tz).roundToInt()
        val set = (720 - 4 * (lon - ha) - eq + tz).roundToInt()
        return Pair(((rise % 1440) + 1440) % 1440, ((set % 1440) + 1440) % 1440)
    }

    /** Khoảng giờ dịu đèn đang áp dụng: (bắt đầu, kết thúc) tính bằng phút. */
    fun window(): Pair<Int, Int> {
        if (S.nightMode.intValue == 0) { val s = sunToday(); if (s != null) return Pair(s.second, s.first) }
        return Pair(S.nightFrom.intValue, S.nightTo.intValue)
    }

    fun hm(m: Int) = "%02d:%02d".format(m / 60, m % 60)

    /** Bây giờ có nằm trong khoảng giờ ban đêm không (dùng cho dịu đèn và giao diện tự động). */
    fun isNightNow(): Boolean {
        val c = Calendar.getInstance()
        val now = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        val (from, to) = window()
        return if (from <= to) now in from until to else now >= from || now < to
    }

    /** Gọi định kỳ và mỗi khi đổi cài đặt. */
    fun tick() {
        val night = S.nightOn.value && isNightNow()
        if (night) {
            if (active) return
            active = true
            if (S.dayBri == null) S.dayBri = intArrayOf(S.bri[0], S.bri[1], S.bri[2])
            for (i in 0..2) S.bri[i] = S.nightBri.intValue
        } else {
            if (!active && S.dayBri == null) return
            active = false
            S.dayBri?.let { d -> for (i in 0..2) S.bri[i] = d[i] }
            S.dayBri = null
        }
        S.saveSettings()
        if (S.connected.value && S.on.value) S.sendBright()
    }

    /** Đổi mức ban đêm bằng thanh trượt trong Cài đặt. */
    fun setLevel(v: Int) {
        S.nightBri.intValue = v
        if (active) { for (i in 0..2) S.bri[i] = v; if (S.connected.value && S.on.value) S.sendBright() }
        S.saveSettings()
    }
}

/** Tốc độ và vị trí từ GPS của máy (chỉ chạy khi app đang mở). */
@SuppressLint("MissingPermission")
object Gps {
    private var lm: LocationManager? = null
    private val ll = object : LocationListener {
        override fun onLocationChanged(l: Location) {
            S.speedKmh.floatValue = if (l.hasSpeed()) l.speed * 3.6f else 0f
            if (l.hasSpeed()) S.gpsAt = System.currentTimeMillis()
            S.setPlace(l.latitude, l.longitude)
            Limit.onLocation(l.latitude, l.longitude, S.speedKmh.floatValue)
            LimitSign.sync()
            AlertBridge.refresh()                               // xét quá tốc độ ngay khi GPS có số mới, không chờ lần đọc màn hình kế tiếp
        }
        @Deprecated("old") override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
        override fun onProviderEnabled(p: String) {}
        override fun onProviderDisabled(p: String) {}
    }

    fun start(ctx: Context) {
        if (lm != null) return
        if (ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        try {
            val m = ctx.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)) {
                val l = try { m.getLastKnownLocation(p) } catch (e: Exception) { null }
                if (l != null) { S.setPlace(l.latitude, l.longitude); break }
            }
            m.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, ll)   // 0 = nhanh nhất máy cho phép, để số km/h bớt trễ
            lm = m
        } catch (e: Exception) { /* máy không có GPS: bỏ qua, tốc độ để 0 */ }
    }

    fun stop() {
        try { lm?.removeUpdates(ll) } catch (_: Exception) {}
        lm = null
        S.speedKmh.floatValue = 0f; S.gpsAt = 0L
    }
}

/** Tên bài hát đang phát trên máy (cần quyền đọc thông báo; không có quyền thì để trống). */
object Media {
    fun refresh(ctx: Context) {
        try {
            val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val c = msm.getActiveSessions(ComponentName(ctx, NavListener::class.java))
                .firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            val md = c?.metadata
            val t = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
            val a = md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
            S.song.value = if (t.isEmpty()) "" else if (a.isEmpty()) t else "$t · $a"
        } catch (e: Exception) { S.song.value = "" }
    }
}
