package vn.sklight.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

// ===================== KÍCH HOẠT APP + BẢO HÀNH: THEO HỘP ĐÈN (Lệnh 433 SK Core, chốt 24/11/2026) =====================
// Mã nhận biết hộp = địa chỉ Bluetooth của hộp tổng (S.savedMac). Hộp đã kích hoạt thì MỌI máy nối vào đều dùng được, không nhập mã.
// Nối hộp: hỏi SK Core (check_box). Máy này đã từng thấy hộp được kích hoạt (lưu ở prefs "act", khóa "box:<MAC>") thì mở luôn kể cả không có mạng.
// Chưa từng thấy: BẮT BUỘC có mạng ở lần nối đầu tiên (chủ dự án chọn), chưa hỏi được thì khóa điều khiển và báo cần mạng.
// Lệnh 434 (chốt 24/11/2026): thợ nhập mã 4 chữ là GẮN MÃ vào hộp ngay (ngày bảo hành tính từ lúc gắn), chưa cần thông tin khách.
// Thông tin khách (số điện thoại, biển số, dòng xe, đời xe, đơn vị lắp đặt) khai sau bằng set_customer — bắt buộc để có hồ sơ bảo hành,
// nhưng không khóa điều khiển. Hộp còn thiếu hồ sơ thì thẻ Bảo hành nhắc ở mọi máy nối vào hộp.
// App không biết và không hiện giới hạn: chỉ hỏi SK Core. Cổng: POST /api/public/sk-light-activation.
object Act {
    private const val URL_ACT = "https://core.sklight.vn/api/public/sk-light-activation"
    const val HOTLINE = "0868055555"
    private val main = Handler(Looper.getMainLooper())

    val ok = mutableStateOf(false)            // hộp đang nối đã kích hoạt (được điều khiển)
    val at = mutableStateOf("")               // ngày kích hoạt của hộp đang nối, yyyy-MM-dd
    val until = mutableStateOf("")            // ngày hết bảo hành, yyyy-MM-dd
    val needCust = mutableStateOf(false)      // hộp đã gắn mã nhưng chưa có thông tin khách (hồ sơ bảo hành)
    val busy = mutableStateOf(false)
    /** Lệnh 435: tên người dùng mã đại lý / VIP đã nhập trên máy này; rỗng nếu không dùng mã đại lý. Có mã thì dùng app ngay, không cần kích hoạt theo hộp, không hồ sơ bảo hành. */
    val master = mutableStateOf("")
    val masterUntil = mutableStateOf("")      // ngày hết hạn của mã đại lý (yyyy-MM-dd), rỗng = không hết hạn
    private var masterCode = ""
    /** 0 chưa hỏi, 1 đang hỏi SK Core, 2 không có mạng (lần đầu nối hộp này), 3 đã có câu trả lời */
    val checking = mutableIntStateOf(0)
    private var devId = ""
    private var box = ""
    private lateinit var app: Context

    private fun sp(c: Context) = c.getSharedPreferences("act", Context.MODE_PRIVATE)

    fun init(c: Context) {
        app = c.applicationContext
        val p = sp(app)
        // Mã máy Android: giữ nguyên khi xóa app rồi cài lại (cùng khóa ký). SK Core dùng nó để gắn mã của máy đã kích hoạt theo cách cũ vào hộp.
        val aid = try { Settings.Secure.getString(app.contentResolver, Settings.Secure.ANDROID_ID) ?: "" } catch (_: Exception) { "" }
        val mv = p.getString("master", null)
        if (mv != null && mv.contains('|')) { masterCode = mv.substringBefore('|'); master.value = mv.substringAfter('|').ifEmpty { "Đại lý" }; ok.value = true }
        devId = if (aid.length >= 8) "a-$aid" else (p.getString("dev", null) ?: ("r-" + UUID.randomUUID().toString()).also { p.edit().putString("dev", it).apply() })
        if (masterCode.isNotEmpty()) recheckMaster()
    }

    /** Hỏi lại SK Core mã đại lý còn hiệu lực không. Bị khoá hoặc xoá thì máy về chưa kích hoạt; mất mạng thì giữ nguyên. */
    fun recheckMaster() {
        val mc = masterCode
        if (mc.isEmpty() || devId.isEmpty()) return
        Thread {
            try {
                val r = post(JSONObject().put("action", "check_master").put("device_id", devId).put("device_name", devName()).put("code", mc))
                val st = r.optString("status")
                if (st == "ok") main.post { master.value = r.optString("label").ifEmpty { master.value }; masterUntil.value = r.optString("expires_at", ""); sp(app).edit().putString("master", mc + "|" + master.value).apply() }
                else if (st == "locked" || st == "invalid_code" || st == "expired") main.post { clearMaster() }
            } catch (_: Exception) {}
        }.start()
    }

    fun clearMaster() {
        masterCode = ""; master.value = ""; masterUntil.value = ""
        sp(app).edit().remove("master").apply()
        ok.value = false; at.value = ""; until.value = ""; needCust.value = false
        if (box.isNotEmpty()) onConnected(box)
    }

    private fun devName() = (Build.MANUFACTURER + " " + Build.MODEL).trim().take(100)
    private fun key(mac: String) = "box:" + mac.uppercase()

    private fun post(body: JSONObject): JSONObject {
        val u = URL(URL_ACT).openConnection() as HttpURLConnection
        u.connectTimeout = 10000; u.readTimeout = 20000; u.requestMethod = "POST"; u.doOutput = true
        u.setRequestProperty("Content-Type", "application/json")
        u.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val st = if (u.responseCode in 200..299) u.inputStream else u.errorStream
        val txt = st?.bufferedReader()?.use { it.readText() } ?: ""
        return try { JSONObject(txt) } catch (_: Exception) { JSONObject().put("status", "server") }
    }

    private fun store(mac: String, a: String, w: String, need: Boolean = false) {
        sp(app).edit().putString(key(mac), "$a|$w|${if (need) 1 else 0}").apply()
        if (mac.equals(box, true)) { ok.value = true; at.value = a; until.value = w; needCust.value = need }
    }

    private fun forget(mac: String) {
        sp(app).edit().remove(key(mac)).apply()
        if (mac.equals(box, true) && masterCode.isEmpty()) { ok.value = false; at.value = ""; until.value = ""; needCust.value = false }
    }

    /** Gọi khi vừa nối được hộp [mac]. */
    fun onConnected(mac: String) {
        box = mac
        if (masterCode.isNotEmpty()) { ok.value = true; needCust.value = false; checking.intValue = 3; recheckMaster(); return }
        val saved = sp(app).getString(key(mac), null)
        if (saved != null) {
            val p = saved.split('|')
            ok.value = true; at.value = p.getOrElse(0) { "" }; until.value = p.getOrElse(1) { "" }; needCust.value = p.getOrElse(2) { "0" } == "1"
        } else { ok.value = false; at.value = ""; until.value = ""; needCust.value = false }
        recheck()
    }

    /** Hỏi lại SK Core hộp đang nối đã kích hoạt chưa. Mất mạng thì giữ nguyên trạng thái đã lưu. */
    fun recheck() {
        val mac = box
        if (mac.isEmpty() || devId.isEmpty()) return
        checking.intValue = 1
        Thread {
            var st = 2
            try {
                val r = post(JSONObject().put("action", "check_box").put("device_id", devId).put("device_name", devName()).put("box_id", mac))
                if (r.has("activated")) {
                    st = 3
                    val yes = r.optBoolean("activated", false)
                    main.post { if (yes) store(mac, r.optString("activated_at"), r.optString("warranty_until"), r.optBoolean("need_customer", false)) else forget(mac) }
                }
            } catch (_: Exception) {}
            main.post { if (mac == box) checking.intValue = st }
        }.start()
    }

    /** Kết quả trả về qua [done]: ok, need_customer, invalid_code, code_used, phone_used, limit_reached, locked, too_many, invalid_input:<trường>, network, server. */
    fun activate(c: Context, code: String, cust: JSONObject?, defer: Boolean = false, done: (String) -> Unit) {
        if (busy.value) return
        val ac = c.applicationContext
        val mac = box
        busy.value = true
        Thread {
            var res = "network"
            var a = ""; var w = ""; var need = false; var mLabel: String? = null
            try {
                val b = JSONObject().put("action", "activate").put("code", code).put("device_id", devId).put("device_name", devName())
                if (mac.isNotEmpty()) b.put("box_id", mac)
                if (cust != null) b.put("customer", cust)
                if (defer && cust == null && mac.isNotEmpty()) b.put("defer_customer", true)
                val r = post(b)
                res = r.optString("status", "server")
                if (res == "invalid_input") res = "invalid_input:" + r.optString("field")
                a = r.optString("activated_at"); w = r.optString("warranty_until"); need = r.optBoolean("need_customer", false)
                if (res == "ok" && r.optBoolean("master", false)) { mLabel = r.optString("label").ifEmpty { "Đại lý" }; a = r.optString("expires_at", "") }
            } catch (_: Exception) {}
            main.post {
                busy.value = false
                if (res == "ok" && mLabel != null) {
                    masterCode = code.uppercase().filter { it.isLetterOrDigit() }; master.value = mLabel; masterUntil.value = a
                    sp(app).edit().putString("master", masterCode + "|" + mLabel).apply()
                    ok.value = true; needCust.value = false; checking.intValue = 3
                    S.sendBright(); S.sendEffect(); if (L.cur.intValue == 0) Speaker.say(ac, "Đã mở bằng mã đại lý", force = true)
                } else
                if (res == "ok") { checking.intValue = 3; store(mac, a, w, need); S.sendBright(); S.sendEffect(); if (L.cur.intValue == 0) Speaker.say(ac, "Kích hoạt bảo hành thành công", force = true) }
                done(res)
            }
        }.start()
    }

    /** Khai thông tin khách cho hộp đang nối (Lệnh 434). Kết quả qua [done]: ok, phone_used, not_activated, invalid_input:<trường>, network, server. */
    fun setCustomer(cust: JSONObject, done: (String) -> Unit) {
        if (busy.value) return
        val mac = box
        if (mac.isEmpty()) { done("not_activated"); return }
        busy.value = true
        Thread {
            var res = "network"
            var a = ""; var w = ""
            try {
                val r = post(JSONObject().put("action", "set_customer").put("device_id", devId).put("device_name", devName()).put("box_id", mac).put("customer", cust))
                res = r.optString("status", "server")
                if (res == "invalid_input") res = "invalid_input:" + r.optString("field")
                a = r.optString("activated_at"); w = r.optString("warranty_until")
            } catch (_: Exception) {}
            main.post {
                busy.value = false
                if (res == "ok") store(mac, a.ifEmpty { at.value }, w.ifEmpty { until.value }, false)
                done(res)
            }
        }.start()
    }

    fun vn(d: String): String = try { SimpleDateFormat("dd/MM/yyyy", Locale.US).format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(d)!!) } catch (_: Exception) { d }

    /** Số ngày còn bảo hành (tính cả hôm nay); âm là đã hết hạn; null khi chưa có ngày. */
    fun daysLeft(): Int? = try {
        val end = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(until.value)!!.time
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).let { f -> f.parse(f.format(java.util.Date()))!!.time }
        ((end - today) / 86400000L).toInt()
    } catch (_: Exception) { null }

    fun call(c: Context) {
        try { c.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$HOTLINE")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (_: Exception) { Notice.show(c, L.tr("Hotline: 0868 055 555"), true) }
    }
}
