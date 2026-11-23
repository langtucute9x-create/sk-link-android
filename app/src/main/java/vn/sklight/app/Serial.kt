package vn.sklight.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// ===================== TÌM SỐ SERI HỘP (chủ dự án yêu cầu 24/11) =====================
// Mục đích: biết hộp có mã riêng nào ngoài địa chỉ Bluetooth không (để gắn kích hoạt theo hộp, và cho iPhone sau này, vì iPhone không đọc được địa chỉ Bluetooth).
// Đọc: gói quảng bá của hộp (lấy lúc quét) + mọi đặc tính đọc được trên kết nối (gồm dịch vụ Device Information 180A nếu hộp có).

private val KNOWN = mapOf(
    "2a00" to "Tên thiết bị", "2a01" to "Hình thức", "2a23" to "System ID", "2a24" to "Model", "2a25" to "SỐ SERI",
    "2a26" to "Phiên bản phần mềm", "2a27" to "Phiên bản phần cứng", "2a28" to "Phiên bản phần mềm 2", "2a29" to "Nhà sản xuất",
    "2a50" to "PnP ID", "180a" to "Device Information", "1800" to "Generic Access", "1801" to "Generic Attribute", "ae30" to "Điều khiển đèn"
)
private fun short(u: String) = if (u.length >= 8 && u.endsWith("-0000-1000-8000-00805f9b34fb")) u.substring(4, 8) else u
private fun nameOf(u: String) = KNOWN[short(u)]?.let { "$it (${short(u)})" } ?: short(u)
private fun hex(b: ByteArray) = b.joinToString(" ") { "%02X".format(it) }
private fun ascii(b: ByteArray) = b.map { val c = it.toInt() and 0xFF; if (c in 32..126) c.toChar() else '.' }.joinToString("")

/** Tách gói quảng bá thành từng mục (độ dài | loại | dữ liệu). */
private fun parseAdv(b: ByteArray): List<String> {
    val out = ArrayList<String>(); var i = 0
    val types = mapOf(0x01 to "Cờ", 0x02 to "UUID 16 bit", 0x03 to "UUID 16 bit (đủ)", 0x08 to "Tên ngắn", 0x09 to "Tên đầy đủ",
        0x0A to "Công suất phát", 0x16 to "Dữ liệu dịch vụ", 0xFF to "DỮ LIỆU NHÀ SẢN XUẤT", 0x19 to "Hình thức")
    while (i < b.size) {
        val len = b[i].toInt() and 0xFF
        if (len == 0 || i + len > b.size - 1) break
        val t = b[i + 1].toInt() and 0xFF
        val d = b.copyOfRange(i + 2, minOf(b.size, i + 1 + len))
        out.add("• ${types[t] ?: "Loại %02X".format(t)}: ${hex(d)}  [${ascii(d)}]")
        i += len + 1
    }
    return out
}

@Composable
fun SerialPage() {
    val ctx = LocalContext.current
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    var found by remember { mutableStateOf<List<String>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var jl by remember { mutableStateOf<BleLed.JlResult?>(null) }
    var jlBusy by remember { mutableStateOf(false) }
    BackRow(8, "Cài đặt nhà máy")
    Card {
        GroupTitle(Icons.Filled.Fingerprint, "Tìm số seri hộp", 16)
        Spacer(Modifier.height(6.dp))
        Hint("Đọc mọi thông tin Bluetooth hộp cho phép đọc, để xem hộp có mã riêng nào ngoài ID thiết bị không. Chỉ đọc, không thay đổi gì trên hộp.", 12)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FillBtn(if (busy) "Đang đọc…" else "Bắt đầu tìm", Icons.Filled.Search, Pink, Modifier.weight(1f)) {
                if (busy) return@FillBtn
                if (!S.connected.value) { Notice.show(ctx, L.tr("Chưa kết nối hộp đèn.")); return@FillBtn }
                busy = true
                S.ble.probe { list ->
                    val mac = S.savedMac
                    val l = ArrayList<String>()
                    l.add("SK Light ${S.VER} · ${S.devName.value} · ID $mac")
                    if (X.rfModel.isNotEmpty()) l.add("Phiên bản hộp: ${X.rfModel}")
                    l.add("--- Gói quảng bá (lấy lúc quét) ---")
                    val adv = S.ble.adv[mac] ?: ctx.getSharedPreferences("advlog", Context.MODE_PRIVATE).getString(mac, null)
                        ?.chunked(2)?.mapNotNull { it.toIntOrNull(16)?.toByte() }?.toByteArray()
                    if (adv == null) l.add("Chưa có. Bấm Chọn lại ở Thông tin thiết bị để quét lại hộp rồi tìm lại.") else { l.add(hex(adv)); l.addAll(parseAdv(adv)) }
                    l.add("--- Dịch vụ và đặc tính ---")
                    var svc = ""
                    val f = ArrayList<String>()
                    for (p in list) {
                        if (p.svc != svc) { svc = p.svc; l.add("[${nameOf(p.svc)}]") }
                        val v = p.value
                        l.add("  ${nameOf(p.ch)} (${p.props})" + if (v != null) ": ${hex(v)}  [${ascii(v)}]" else "")
                        if (v != null && v.isNotEmpty() && short(p.ch) in listOf("2a25", "2a23", "2a50", "2a24", "2a27", "2a26", "2a29")) f.add("${nameOf(p.ch)}: ${ascii(v).trim('.')} (${hex(v)})")
                    }
                    if (list.isEmpty()) l.add("Không đọc được (hộp chưa trả lời).")
                    lines = l; found = f; busy = false
                }
            }
            FillBtn("Sao chép", Icons.Filled.ContentCopy, Color(0xFFFE9700), Modifier.weight(1f)) {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("serial", (listOf("Kết quả đáng chú ý:") + found + lines).joinToString("\n")))
                Notice.show(ctx, L.tr("Đã sao chép"))
            }
        }
    }
    if (lines.isNotEmpty()) {
        Card {
            T("Kết quả", 15, FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            if (found.isEmpty()) T("Hộp không có dịch vụ Device Information (số seri, System ID). Xem phần gói quảng bá bên dưới, nhất là DỮ LIỆU NHÀ SẢN XUẤT.", 13, color = SubTxt)
            else for (x in found) T(x, 13, FontWeight.Bold, AccentText)
            Spacer(Modifier.height(10.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Theme.r(8))).background(ChipBg).padding(10.dp)) {
                for (x in lines) T(x, 11, color = Txt)
            }
            Spacer(Modifier.height(8.dp))
            Hint("Bấm Sao chép rồi gửi kết quả cho kỹ thuật. Muốn so sánh, làm lại với một hộp khác: mã nào khác nhau giữa hai hộp mới là mã riêng.", 12)
        }
    }
    // ----- Thử đọc ID hộp qua kênh riêng của chip Bluetooth (để iPhone nhận ra từng hộp) -----
    Card {
        GroupTitle(Icons.Filled.Memory, "Thử đọc ID hộp qua kênh chip", 16)
        Spacer(Modifier.height(6.dp))
        Hint("Hỏi chip Bluetooth của hộp địa chỉ của chính nó, để sau này iPhone cũng nhận ra từng hộp. Chỉ hỏi, không thay đổi gì trên hộp. Mất khoảng 20 giây, trong lúc đó đèn tạm không nhận lệnh.", 12)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FillBtn(if (jlBusy) "Đang thử…" else "Bắt đầu thử", Icons.Filled.Search, Pink, Modifier.weight(1f)) {
                if (jlBusy) return@FillBtn
                if (!S.connected.value) { Notice.show(ctx, L.tr("Chưa kết nối hộp đèn.")); return@FillBtn }
                jlBusy = true
                S.ble.jlProbe { r -> jl = r; jlBusy = false }
            }
            FillBtn("Sao chép", Icons.Filled.ContentCopy, Color(0xFFFE9700), Modifier.weight(1f)) {
                val r = jl ?: return@FillBtn
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val head = listOf("SK Light ${S.VER} · ${S.devName.value} · ID ${S.savedMac}", "Kênh trả lời: ${r.channel ?: "không có"}", "Địa chỉ BLE hộp tự báo: ${r.bleAddr ?: "không có"}", "Địa chỉ EDR hộp tự báo: ${r.edrAddr ?: "không có"}")
                cm.setPrimaryClip(ClipData.newPlainText("jl", (head + r.log).joinToString("\n")))
                Notice.show(ctx, L.tr("Đã sao chép"))
            }
        }
    }
    jl?.let { r ->
        Card {
            T("Kết quả thử kênh chip", 15, FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            val id = S.savedMac.uppercase()
            fun same(a: String?) = a != null && (a == id || a.split(":").reversed().joinToString(":") == id)
            when {
                same(r.bleAddr) -> T("ĐỌC ĐƯỢC ID hộp: ${r.bleAddr}. TRÙNG với ID thiết bị, iPhone dùng được kích hoạt theo hộp.", 13, FontWeight.Bold, AccentText)
                same(r.edrAddr) -> T("ĐỌC ĐƯỢC ID hộp ở ô địa chỉ thứ hai: ${r.edrAddr}. TRÙNG với ID thiết bị.", 13, FontWeight.Bold, AccentText)
                r.bleAddr != null || r.edrAddr != null -> T("Hộp có trả địa chỉ (${r.bleAddr ?: "-"} / ${r.edrAddr ?: "-"}) nhưng KHÁC ID thiết bị $id. Gửi kết quả cho kỹ thuật.", 13, FontWeight.Bold, color = Txt)
                r.channel != null -> T("Hộp có trả lời ở kênh ${r.channel} nhưng không kèm địa chỉ. Gửi kết quả cho kỹ thuật.", 13, color = SubTxt)
                else -> T("Chưa đọc được: hộp không trả lời ở kênh nào. Gửi kết quả cho kỹ thuật.", 13, color = SubTxt)
            }
            Spacer(Modifier.height(10.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Theme.r(8))).background(ChipBg).padding(10.dp)) {
                for (x in r.log) T(x, 11, color = Txt)
            }
            Spacer(Modifier.height(8.dp))
            Hint("Bấm Sao chép rồi gửi kết quả cho kỹ thuật.", 12)
        }
    }
}
