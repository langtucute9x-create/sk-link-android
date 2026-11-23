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

// ===================== KÍCH HOẠT APP + BẢO HÀNH =====================
// Quy tắc (tài liệu claude/sk-light-kich-hoat-bao-hanh.md): mã chỉ mở khóa app trên MỘT máy, không gắn với hộp đèn.
// App không biết và không hiện giới hạn số lượt: chỉ hỏi SK Core được hay không. Cổng: POST /api/public/sk-light-activation.
// Máy chưa kích hoạt: app mở bình thường; nối được hộp đèn thì khóa điều khiển (không gửi lệnh ghi xuống hộp) tới khi nhập mã.
object Act {
    private const val URL_ACT = "https://core.sklight.vn/api/public/sk-light-activation"
    const val HOTLINE = "0868055555"
    private val main = Handler(Looper.getMainLooper())

    val ok = mutableStateOf(false)            // máy này đã kích hoạt
    val at = mutableStateOf("")               // ngày kích hoạt đầu tiên của mã, yyyy-MM-dd
    val until = mutableStateOf("")            // ngày hết bảo hành, yyyy-MM-dd
    val busy = mutableStateOf(false)
    private var devId = ""

    private fun sp(c: Context) = c.getSharedPreferences("act", Context.MODE_PRIVATE)

    fun init(c: Context) {
        val app = c.applicationContext
        val p = sp(app)
        ok.value = p.getBoolean("ok", false); at.value = p.getString("at", "") ?: ""; until.value = p.getString("until", "") ?: ""
        // Mã máy Android: giữ nguyên khi xóa app rồi cài lại (cùng khóa ký), nên SK Core nhận ra máy cũ và không tính thêm lượt.
        val aid = try { Settings.Secure.getString(app.contentResolver, Settings.Secure.ANDROID_ID) ?: "" } catch (_: Exception) { "" }
        devId = if (aid.length >= 8) "a-$aid" else (p.getString("dev", null) ?: ("r-" + UUID.randomUUID().toString()).also { p.edit().putString("dev", it).apply() })
        if (!ok.value) recheck(app)
    }

    private fun devName() = (Build.MANUFACTURER + " " + Build.MODEL).trim().take(100)

    private fun post(body: JSONObject): JSONObject {
        val u = URL(URL_ACT).openConnection() as HttpURLConnection
        u.connectTimeout = 10000; u.readTimeout = 20000; u.requestMethod = "POST"; u.doOutput = true
        u.setRequestProperty("Content-Type", "application/json")
        u.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val st = if (u.responseCode in 200..299) u.inputStream else u.errorStream
        val txt = st?.bufferedReader()?.use { it.readText() } ?: ""
        return try { JSONObject(txt) } catch (_: Exception) { JSONObject().put("status", "server") }
    }

    private fun store(c: Context, a: String, w: String) {
        ok.value = true; at.value = a; until.value = w
        sp(c).edit().putBoolean("ok", true).putString("at", a).putString("until", w).apply()
    }

    /** Cài lại app trên máy đã kích hoạt: SK Core nhận ra mã máy và mở khóa luôn, không cần nhập lại mã. Không có mạng thì bỏ qua, lần mở sau hỏi lại. */
    fun recheck(c: Context) {
        val app = c.applicationContext
        if (ok.value || devId.isEmpty()) return
        Thread {
            try {
                val r = post(JSONObject().put("action", "check_device").put("device_id", devId).put("device_name", devName()))
                if (r.optBoolean("activated", false)) main.post { store(app, r.optString("activated_at"), r.optString("warranty_until")) }
            } catch (_: Exception) {}
        }.start()
    }

    /** Kết quả trả về qua [done]: ok, need_customer, invalid_code, phone_used, limit_reached, locked, too_many, invalid_input:<trường>, network, server. */
    fun activate(c: Context, code: String, cust: JSONObject?, done: (String) -> Unit) {
        if (busy.value) return
        val app = c.applicationContext
        busy.value = true
        Thread {
            var res = "network"
            var a = ""; var w = ""
            try {
                val b = JSONObject().put("action", "activate").put("code", code).put("device_id", devId).put("device_name", devName())
                if (cust != null) b.put("customer", cust)
                val r = post(b)
                res = r.optString("status", "server")
                if (res == "invalid_input") res = "invalid_input:" + r.optString("field")
                a = r.optString("activated_at"); w = r.optString("warranty_until")
            } catch (_: Exception) {}
            main.post {
                busy.value = false
                if (res == "ok") { store(app, a, w); S.sendBright(); S.sendEffect(); if (L.cur.intValue == 0) Speaker.say(app, "Kích hoạt bảo hành thành công", force = true) }
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
