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
    fun activate(c: Context, code: String, name: String?, phone: String?, place: String?, done: (String) -> Unit) {
        if (busy.value) return
        val app = c.applicationContext
        busy.value = true
        Thread {
            var res = "network"
            var a = ""; var w = ""
            try {
                val b = JSONObject().put("action", "activate").put("code", code).put("device_id", devId).put("device_name", devName())
                if (name != null) b.put("customer", JSONObject().put("name", name).put("phone", phone ?: "").put("place", place ?: ""))
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

/** Thẻ Bảo hành ở trang Cài đặt: chỉ hiện ngày kích hoạt và số ngày còn bảo hành. Chưa kích hoạt thì có nút nhập mã. */
@Composable
fun WarrantyCard() {
    Card {
        GroupTitle(Icons.Filled.VerifiedUser, "Bảo hành", 16)
        Spacer(Modifier.height(8.dp))
        if (Act.ok.value) {
            val left = Act.daysLeft()
            Kv("Ngày kích hoạt", if (Act.at.value.isEmpty()) "—" else Act.vn(Act.at.value))
            Kv("Còn bảo hành", when { left == null -> "—"; left < 0 -> L.tr("Đã hết hạn"); else -> "$left " + L.tr("ngày") })
        } else {
            T("App chưa kích hoạt trên máy này.", 13, color = SubTxt)
            Spacer(Modifier.height(10.dp))
            OutlineBtn("Nhập mã kích hoạt", Modifier.fillMaxWidth()) { S.subPage.intValue = 13 }
        }
    }
}

/** Trang nhập mã. [locked] = đang hiện thay cho trang điều khiển vì đã nối hộp đèn mà máy chưa kích hoạt. */
@Composable
fun ActivationPage(locked: Boolean) {
    val ctx = LocalContext.current
    var code by remember { mutableStateOf("") }
    var need by remember { mutableStateOf(false) }      // mã dùng lần đầu: cần thông tin khách
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var place by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    var hot by remember { mutableStateOf(false) }       // hiện nút gọi hotline
    if (!locked) BackRow(0, "Cài đặt")
    if (Act.ok.value) { WarrantyCard(); return }
    Card {
        GroupTitle(Icons.Filled.Lock, "App chưa kích hoạt trên máy này", 16)
        Spacer(Modifier.height(6.dp))
        T(if (locked) "Đã kết nối với hộp đèn. Nhập mã kích hoạt để mở khóa điều khiển." else "Nhập mã kích hoạt để dùng app điều khiển đèn trên máy này.", 13, color = SubTxt)
        Spacer(Modifier.height(4.dp))
        T("Mã kích hoạt in trên tem dán ở vỏ hộp sản phẩm. Cần có mạng khi kích hoạt.", 12, color = Grey)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(code, { v -> code = v.uppercase().filter { it.isLetterOrDigit() || it == '-' || it == ' ' }.take(20); err = ""; hot = false },
            singleLine = true, enabled = !need && !Act.busy.value, modifier = Modifier.fillMaxWidth(),
            label = { T("Mã kích hoạt", 12, color = Grey) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii))
        if (need) {
            Spacer(Modifier.height(10.dp))
            T("Mã này kích hoạt lần đầu. Điền thông tin để đăng ký bảo hành 24 tháng.", 13, FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(name, { name = it.take(80); err = "" }, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { T("Tên khách hàng", 12, color = Grey) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(phone, { v -> phone = v.filter { it.isDigit() || it == '+' }.take(14); err = "" }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                label = { T("Số điện thoại", 12, color = Grey) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(place, { place = it.take(200); err = "" }, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { T("Nơi lắp đặt", 12, color = Grey) },
                placeholder = { T("Tên cửa hàng hoặc địa chỉ lắp đèn", 13, color = Grey) })
        }
        if (err.isNotEmpty()) { Spacer(Modifier.height(10.dp)); T(err, 13, FontWeight.SemiBold, Color(0xFFF44336)) }
        Spacer(Modifier.height(12.dp))
        val go: () -> Unit = go@{
            val raw = code.filter { it.isLetterOrDigit() }
            if (raw.length < 8) { err = L.tr("Mã kích hoạt chưa đủ ký tự."); return@go }
            if (need) {
                if (name.isBlank()) { err = L.tr("Chưa nhập tên khách hàng."); return@go }
                if (phone.filter { it.isDigit() }.length < 10) { err = L.tr("Số điện thoại chưa đúng."); return@go }
                if (place.isBlank()) { err = L.tr("Chưa nhập nơi lắp đặt."); return@go }
            }
            err = ""; hot = false
            Act.activate(ctx, raw, if (need) name.trim() else null, phone, place.trim()) { r ->
                when {
                    r == "ok" -> Notice.show(ctx, L.tr("Đã kích hoạt. Bảo hành đến ") + Act.vn(Act.until.value), true)
                    r == "need_customer" -> need = true
                    r == "invalid_code" -> err = L.tr("Mã kích hoạt không đúng. Kiểm tra lại mã trên tem.")
                    r == "phone_used" -> { err = L.tr("Số điện thoại này đã kích hoạt bảo hành cho một bộ sản phẩm khác."); hot = true }
                    r == "limit_reached" -> { err = L.tr("Mã đã hết lượt kích hoạt."); hot = true }
                    r == "locked" -> { err = L.tr("Mã này đang bị khóa."); hot = true }
                    r == "too_many" -> err = L.tr("Nhập sai quá nhiều lần. Hãy thử lại sau 15 phút.")
                    r == "invalid_input:phone" -> err = L.tr("Số điện thoại chưa đúng.")
                    r == "invalid_input:name" -> err = L.tr("Chưa nhập tên khách hàng.")
                    r == "invalid_input:place" -> err = L.tr("Chưa nhập nơi lắp đặt.")
                    r == "network" -> err = L.tr("Không kết nối được máy chủ. Kiểm tra mạng rồi thử lại.")
                    else -> { err = L.tr("Máy chủ đang bận. Hãy thử lại sau ít phút."); hot = true }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (need) OutlineBtn("Đổi mã", Modifier.weight(1f)) { if (!Act.busy.value) { need = false; err = "" } }
            FillBtn(if (Act.busy.value) "Đang kiểm tra…" else if (need) "Kích hoạt bảo hành" else "Kích hoạt", Icons.Filled.VerifiedUser, Pink, Modifier.weight(1.6f), go)
        }
        if (hot) {
            Spacer(Modifier.height(10.dp))
            FillBtn("Gọi hotline 0868 055 555", Icons.Filled.Call, OkGreen, Modifier.fillMaxWidth()) { Act.call(ctx) }
        }
    }
    if (locked) T("Các trang điều khiển đèn mở lại ngay sau khi kích hoạt. Trang Cài đặt vẫn dùng được.", 12, color = Grey)
}
