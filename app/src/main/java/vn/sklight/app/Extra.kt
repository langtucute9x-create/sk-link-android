package vn.sklight.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Các trang cài đặt bổ sung (ghép nối hộp phụ, hướng chạy, màu chào mừng, nhà máy, chức năng gốc xe, chẩn đoán). Chưa chạy thử trên hộp thật. */
object X {
    val ver = mutableIntStateOf(0)
    var pairRaw by mutableIntStateOf(-1)                 // byte trạng thái ghép nối đọc từ hộp (khối 03), -1 = chưa đọc
    var pairing by mutableStateOf(false)
    var pairLeft by mutableIntStateOf(0)
    var p09: IntArray? = null                            // khối 09: số ô trước, RGB, số ô sau, RGB
    val diag = mutableStateListOf<String>()               // dòng chữ chẩn đoán đã giải mã
    val diagRaw = mutableStateListOf<String>()            // khung thô (hex)
    private val diagParts = java.util.TreeMap<Int, java.util.TreeMap<Int, ByteArray>>()

    // loại xe: danh sách nằm trong hộp, mỗi loại hộp một danh sách riêng. (hãng, dòng, tên); dòng 0 = tên hãng
    val cars = mutableStateListOf<Triple<Int, Int, String>>()
    val carRaw = mutableStateListOf<String>()
    private val carParts = java.util.TreeMap<Int, java.util.TreeMap<Int, ByteArray>>()
    var carCur by mutableIntStateOf(-1)                  // (hãng << 8) | dòng đang đặt trong hộp, -1 = chưa đọc
    var carTick by mutableIntStateOf(0)
    var carLoading by mutableStateOf(false)
    var resetState by mutableIntStateOf(0)               // 0 chưa làm, 1 đã gửi đang chờ, 2 hộp báo xong

    private val dirBit = intArrayOf(5, 4, 0, 1, 2, 3)    // đoạn -> bit, cùng thứ tự khối 08

    /** Kết nối mới (có thể là hộp khác): bỏ những gì đọc từ hộp trước. */
    fun clear() { rfModel = ""; canModel = ""; pairRaw = -1; p09 = null; sup = null; cars.clear(); carRaw.clear(); carParts.clear(); carCur = -1; diag.clear(); diagRaw.clear(); diagParts.clear(); doorProbe = ""; resetState = 0; ver.intValue++ }
    fun onPair(b: Int) { pairRaw = b; ver.intValue++ }
    // mã phiên bản do hộp gửi (app gốc gọi là "RF Model" = 7C 8F và "CAN Model" = 7C 90)
    var rfModel by mutableStateOf("")
    var canModel by mutableStateOf("")
    fun onModel(sub: Int, text: String) { val t = text.trim { it <= ' ' }; if (sub == 0x8F) rfModel = t else canModel = t }
    fun on09(v: IntArray) { p09 = v; ver.intValue++ }
    fun on07() { ver.intValue++ }

    private fun hex(d: ByteArray) = d.joinToString(" ") { "%02X".format(it) }
    /** Chữ từ hộp: cùng bảng mã "Unicode" (UTF-16) như app gốc. */
    private fun text(parts: java.util.TreeMap<Int, ByteArray>): String {
        var n = 0; for (b in parts.values) n += b.size
        val all = ByteArray(n); var o = 0
        for (b in parts.values) { System.arraycopy(b, 0, all, o, b.size); o += b.size }
        val cs = try { java.nio.charset.Charset.forName("Unicode") } catch (e: Exception) { Charsets.UTF_16 }
        return String(all, cs).trim { it <= ' ' || it == '\uFEFF' }
    }
    private fun slice(d: ByteArray, from: Int, len: Int): ByteArray {
        val end = minOf(from + len, d.size - 1)          // byte cuối là ck
        return if (end > from) d.copyOfRange(from, end) else ByteArray(0)
    }

    /** Khung chẩn đoán: 2E AC dài | nhóm | (tổng khung << 4 | khung) | dài | chữ. */
    fun onDiagFrame(d: ByteArray) {
        if (d.size < 7) return
        if (diagRaw.size < 80) diagRaw.add(hex(d))
        val series = d[3].toInt() and 0xFF; val fr = d[4].toInt() and 0x0F; val len = d[5].toInt() and 0xFF
        diagParts.getOrPut(series) { java.util.TreeMap() }[fr] = slice(d, 6, len)
        diag.clear()
        for (p in diagParts.values) { val t = text(p); if (t.isNotEmpty()) diag.add(t) }
    }
    fun readDiag() { diag.clear(); diagRaw.clear(); diagParts.clear(); S.ble.send(Protocol.frame(Protocol.T_READ, 0xAC, 0x00)) }

    /** Khung danh sách loại xe: 2E 8C dài | hãng | dòng | (tổng khung << 4 | khung) | dài | tên. */
    fun onCarFrame(d: ByteArray) {
        if (d.size < 8) return
        if (carRaw.size < 400) carRaw.add(hex(d))
        val series = d[3].toInt() and 0xFF; val model = d[4].toInt() and 0xFF
        val fr = d[5].toInt() and 0x0F; val len = d[6].toInt() and 0xFF
        carParts.getOrPut((series shl 8) or model) { java.util.TreeMap() }[fr] = slice(d, 7, len)
        cars.clear()
        for ((k, p) in carParts) cars.add(Triple(k shr 8, k and 0xFF, text(p)))
        carTick++
    }
    fun onCarCur(series: Int, model: Int) { carCur = (series shl 8) or model; ver.intValue++ }
    fun readCars() {
        cars.clear(); carRaw.clear(); carParts.clear(); carLoading = true; carTick++
        S.ble.send(Protocol.frame(Protocol.T_READ, 0x8C, 0x00))
        S.ble.send(Protocol.query(0x0F))
    }
    fun carName(key: Int): String {
        if (key < 0) return "—"
        val sName = CarNames.tr(cars.firstOrNull { it.first == key shr 8 && it.second == 0 }?.third ?: "")
        val mName = if (key and 0xFF == 0) "" else CarNames.tr(cars.firstOrNull { it.first == key shr 8 && it.second == key and 0xFF }?.third ?: "")
        val t = listOf(sName, mName).filter { it.isNotEmpty() }.joinToString(" · ")
        return if (t.isEmpty()) "hãng ${key shr 8}, dòng ${key and 0xFF}" else t
    }
    /** Đặt loại xe: 0F | hãng | dòng (mã lấy nguyên từ danh sách hộp gửi về). Sau đó hỏi lại hộp. */
    fun setCar(series: Int, model: Int) {
        S.ble.send(Protocol.frame(Protocol.T_WRITE, 0x0F, series, model))
        carCur = -1; ver.intValue++
        h.postDelayed({ S.ble.send(Protocol.query(0x0F)) }, 700)
    }

    private val h = android.os.Handler(android.os.Looper.getMainLooper())
    /** Khôi phục cài đặt gốc của hộp: 0E F1. Hộp trả lời 0E F1 khi xong. */
    fun factoryReset() {
        resetState = 1
        S.ble.send(Protocol.frame(Protocol.T_WRITE, 0x0E, 0xF1))
        for (i in 0 until 4) S.boxPos[i] = -1
        S.pickBox.intValue = 0
        S.saveSettings()
        h.postDelayed({ pairRaw = -1; carCur = -1; S.refreshBlocks(); S.ble.send(Protocol.query(0x03)) }, 2000)
    }
    fun onReset() { if (resetState == 1) resetState = 2 }

    // ---- chụp trạng thái hộp: hỏi mọi khối đọc được rồi ghi lại nguyên văn, để so trước / sau khi đổi một cài đặt ----
    @Volatile var snapOn = false
    private val snapCur = java.util.TreeMap<String, String>()
    /** Các lần chụp, mỗi lần: dòng đầu là giờ chụp, các dòng sau "khối: dữ liệu". Nhớ qua lần mở app, tối đa 12 lần. */
    val snaps = mutableStateListOf<String>()
    var snapBusy by mutableStateOf(false)
    // các khối hỏi thử. Chỉ là lệnh ĐỌC (90 7C xx); khối nào hộp không có thì hộp tự bỏ qua. Không hỏi 0E (khối khôi phục gốc).
    private val SNAP_SUBS = (0x00..0x20).filter { it != 0x0E } + listOf(0x8D, 0x8F, 0x90)

    fun onSnapFrame(d: ByteArray) {
        if (d.size < 5 || (d[0].toInt() and 0xFF) != 0x2E) return
        val t = d[1].toInt() and 0xFF
        if (t == 0x8C || t == 0xAC) return
        val key = "%02X %02X".format(t, d[3].toInt() and 0xFF)
        val body = d.copyOfRange(4, d.size - 1).joinToString(" ") { "%02X".format(it) }
        snapCur[key] = body
    }
    fun snapshot() {
        if (snapBusy) return
        snapBusy = true; snapCur.clear(); snapOn = true
        for (q in SNAP_SUBS) S.ble.send(Protocol.query(q))
        S.ble.send(Protocol.frame(Protocol.T_READ, 0x9C, 0x01))
        h.postDelayed({
            snapOn = false
            val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
            while (snaps.size >= 12) snaps.removeAt(0)
            snaps.add(time + "\n" + snapCur.map { it.key + ": " + it.value }.joinToString("\n"))
            S.saveSnaps(snaps.joinToString("\u0001"))
            snapBusy = false
        }, 4500)
    }
    fun loadSnaps(text: String) { snaps.clear(); snaps.addAll(text.split("\u0001").filter { it.isNotBlank() }) }
    fun clearSnaps() { snaps.clear(); S.saveSnaps("") }
    private fun blocks(snap: String) = snap.split("\n").drop(1).filter { it.contains(": ") }.associate { it.substringBefore(": ") to it.substringAfter(": ") }
    /** Các khối khác nhau giữa lần chụp [i] và lần trước nó: "khối: trước -> sau". */
    fun snapDiff(i: Int): List<String> {
        if (i <= 0 || i >= snaps.size) return emptyList()
        val a = blocks(snaps[i - 1]); val b = blocks(snaps[i])
        return (a.keys + b.keys).toSortedSet().filter { a[it] != b[it] }.map { it + ": " + (a[it] ?: "(không có)") + "  ->  " + (b[it] ?: "(không có)") }
    }
    fun snapText(): String {
        val sb = StringBuilder("SK Light ${S.VER}, chụp trạng thái hộp ${S.devName.value}\n")
        snaps.forEachIndexed { i, sn ->
            sb.append("--- lần ${i + 1} lúc ").append(sn).append("\n")
            if (i > 0) { sb.append("--- khác lần ${i}:\n"); for (l in snapDiff(i)) sb.append(l).append("\n") }
        }
        return sb.toString()
    }

    /** Hộp báo xe hỗ trợ chức năng gốc nào (trả lời 9C 01): b1 cùng thứ tự bit với b1 khối 05; b2: bit4 đèn khẩn cấp, bit3 điều hòa, bit2 chào mừng, bit1 điểm mù, bit0 áp suất lốp. */
    var sup: IntArray? = null
    fun onSup(b1: Int, b2: Int) { sup = intArrayOf(b1, b2); ver.intValue++ }

    // Thử hỏi hộp vị trí cửa của hộp phụ (7C 04). App gốc không có lệnh đọc này, nên chỉ ghi lại hộp trả lời gì.
    var doorProbe by mutableStateOf("")
    private var probing = false
    fun probeDoor() {
        probing = true; acked = false; doorProbe = "Đang hỏi hộp…"
        S.ble.send(Protocol.query(0x04))
        h.postDelayed({ if (probing) { probing = false; doorProbe = if (acked) "Hộp chỉ báo đã nhận (FF), không gửi vị trí." else "Hộp không trả lời." } }, 1500)
    }
    fun onDoorRaw(d: ByteArray) { if (probing) { probing = false; doorProbe = hex(d) } }
    private var acked = false
    fun onNack(b: Int) {
        if (!probing) return
        if (b == 0xFF) { acked = true; return }           // FF = đã nhận, chờ tiếp khung dữ liệu
        probing = false
        doorProbe = (if (b == 0xF3) "Hộp báo không hỗ trợ lệnh này" else if (b == 0xFC) "Hộp báo đang bận" else "Hộp báo lỗi") + " (%02X).".format(b)
    }

    fun paired() = if (pairRaw < 0 || (pairRaw and 0xC0) shr 6 > 1) -1 else
        (pairRaw and 1) or (((pairRaw shr 1) and 1) shl 1) or (((pairRaw shr 2) and 1) shl 2) or (((pairRaw shr 5) and 1) shl 3)
    fun pairState() = if (pairRaw < 0) -1 else (pairRaw and 0xC0) shr 6

    /** Ghép nối tự động tất cả hộp phụ (60 giây). 03 | 0x48. */
    fun startPair() { pairing = true; pairLeft = 60; S.ble.send(Protocol.frame(Protocol.T_WRITE, 0x03, 0x48)) }
    fun stopPair() { pairing = false }
    /** Xóa hộp phụ: 03 | 0xC0 (đúng như app gốc gửi khi không chọn ghép tự động). */
    fun clearBoxes() { S.ble.send(Protocol.frame(Protocol.T_WRITE, 0x03, 0xC0)); pairRaw = -1; ver.intValue++; S.ble.send(Protocol.query(0x03)) }

    /** Đọc-sửa-ghi khối 0D, trả về false nếu chưa đọc được khối từ hộp. */
    fun setFac(i: Int, mask: Int, value: Int): Boolean {
        val f = S.fac ?: return false
        val n = f.copyOf(); n[i] = (n[i] and mask.inv()) or (value and mask)
        S.fac = n; ver.intValue++
        if (i == 0) S.z2.intValue = ((n[0] shr 3) and 3).coerceAtMost(2)
        S.ble.send(Protocol.factory(n[0], n[1]), 22, true)
        return true
    }
    fun facBits(i: Int, mask: Int) = (S.fac?.get(i) ?: 0) and mask

    /** Hướng ánh sáng chạy từng đoạn (khối 07). true = bit bật. */
    fun flow(seg: Int): Boolean = ((S.p07?.get(0) ?: 0) shr dirBit[seg]) and 1 == 1
    fun setFlow(seg: Int, on: Boolean): Boolean {
        val p = S.p07 ?: return false
        var d = p[0]
        d = if (on) d or (1 shl dirBit[seg]) else d and (1 shl dirBit[seg]).inv()
        val n = intArrayOf(d, p[1], p[2]); S.p07 = n; ver.intValue++
        S.ble.send(Protocol.block07(n[0], n[1], n[2]), 23, true)
        return true
    }

    /** Mô-đun giọng nói của hộp: byte giữa khối 07, bit0 = thông báo chi tiết (0 = im lặng), bit1 = giọng (0 nữ, 1 nam). Đọc-sửa-ghi. */
    fun voiceBit(mask: Int): Boolean = (S.p07?.get(1) ?: 0) and mask != 0
    fun setVoiceBit(mask: Int, on: Boolean): Boolean {
        val p = S.p07 ?: return false
        val n = intArrayOf(p[0], if (on) p[1] or mask else p[1] and mask.inv(), p[2]); S.p07 = n; ver.intValue++
        S.ble.send(Protocol.block07(n[0], n[1], n[2]), 23, true)
        return true
    }

    fun send09() {
        val p = p09 ?: return
        S.ble.send(Protocol.frame(Protocol.T_WRITE, 0x09, p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7]), 24, true)
    }
    /** Đọc-sửa-ghi: chưa đọc được khối 09 thì không gửi (tránh xóa màu của chiều còn lại). */
    fun pickWelcome(reverse: Boolean, idx: Int, argb: Int): Boolean {
        val p = (p09 ?: run { S.ble.send(Protocol.query(0x09)); return false }).copyOf()
        val o = if (reverse) 4 else 0
        // Cùng cách gửi màu với lệnh màu 01 (đảo 255-x chỉ khi S.invert). 00 00 00 = tắt nên tránh.
        var r = (argb shr 16) and 0xFF; var g = (argb shr 8) and 0xFF; var b = argb and 0xFF
        if (S.invert.value) { r = 255 - r; g = 255 - g; b = 255 - b }
        if (r == 0 && g == 0 && b == 0) { r = 1; g = 1; b = 1 }
        p[o] = idx + 1; p[o + 1] = r; p[o + 2] = g; p[o + 3] = b
        p09 = p; ver.intValue++
        send09()
        return true
    }
}

// 10 màu chào mừng của app gốc: giá trị gửi cho hộp (đã cân theo LED) và màu hiện trên màn hình
private val PRE = intArrayOf(0xFF0000, 0xFFFF00, 0x00FF00, 0x00FFFF, 0x0000FF, 0xFF00FF, 0x8C01FC, 0xFF0020, 0x501000, 0x808080)
private val PRE_SHOW = intArrayOf(0xFF0000, 0xFFE600, 0x00D93A, 0x00D5E6, 0x1E4BFF, 0xFF2CD6, 0x8C01FC, 0xFF2E63, 0xFF7A00, 0xFFFFFF)

private fun need(ctx: Context) {
    S.refreshBlocks()
    Notice.show(ctx, L.tr("Chưa đọc được cài đặt từ hộp. Kiểm tra kết nối rồi thử lại."))
}

@Composable
fun BackRow(to: Int, label: String) {
    Row(
        Modifier.height(44.dp).block(false, RoundedCornerShape(Theme.r(8)), CardBg).clickable { S.subPage.intValue = to }.padding(start = 10.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.ArrowBack, null, tint = Txt, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        T(label, 13, FontWeight.Bold)
    }
}

@Composable
fun ExtraPage(sp: Int) {
    X.ver.intValue
    LaunchedEffect(sp) { S.refreshBlocks(); S.ble.send(Protocol.query(0x03)) }
    when (sp) {
        5 -> PairPage()
        6 -> FlowPage()
        7 -> WelcomeColorPage()
        8 -> FactoryPage()
        9 -> CarFuncPage()
        11 -> CarModelPage()
        else -> DiagPage()
    }
}

// ===================== GHÉP NỐI HỘP PHỤ =====================
@Composable
fun PairPage() {
    val ctx = LocalContext.current
    X.ver.intValue
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(X.pairing) {
        while (X.pairing && X.pairLeft > 0) {
            delay(1000); X.pairLeft--; S.ble.send(Protocol.query(0x03))
        }
        X.pairing = false
    }
    BackRow(0, "Cài đặt")
    Card {
        GroupTitle(Icons.Filled.BluetoothSearching, "Ghép nối hộp phụ", 16)
        Spacer(Modifier.height(8.dp))
        val n = X.paired()
        val dots = if (n > 0 || X.pairing) n else ((S.fac?.get(1) ?: 0) and 0x0F)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            PairRadar(X.pairing, dots.coerceIn(0, 4), Modifier.size(104.dp))
            Column(Modifier.weight(1f)) {
                Kv("Số hộp phụ của xe", if (S.fac == null) "—" else "${S.fac!![1] and 0x0F}")
                if (n > 0) Kv("Hộp vừa ghép được", "$n")
                Kv("Trạng thái", if (X.pairing) "Đang quét… còn ${X.pairLeft} giây" else "Chưa ghép")
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FillBtn(if (X.pairing) "Dừng" else "Bắt đầu ghép nối", Icons.Filled.BluetoothSearching, if (X.pairing) Color(0xFFF44336) else Pink, Modifier.weight(1f)) {
                if (X.pairing) X.stopPair() else X.startPair()
            }
            FillBtn("Xóa hộp phụ", Icons.Filled.Delete, Color(0xFFF44336), Modifier.weight(1f)) { confirm = true }
        }
        Spacer(Modifier.height(8.dp))
        Hint("Bật nguồn hộp phụ cần ghép, bấm Bắt đầu rồi đợi tối đa 60 giây.", 12)
    }
    Card {
        GroupTitle(Icons.Filled.Tune, "Số lượng hộp phụ", 16)
        Spacer(Modifier.height(10.dp))
        val cur = (S.fac?.get(1) ?: -1) and 0x0F
        Grid(listOf(0, 1, 2, 3, 4), 5) { v, m -> Chip("$v", S.fac != null && cur == v, m, 44.dp, 14) { if (!X.setFac(1, 0x0F, v)) need(ctx) } }
    }
    Card {
        GroupTitle(Icons.Filled.DirectionsCar, "Vị trí các hộp phụ", 16)
        Spacer(Modifier.height(6.dp))
        for (i in 0 until 4) {
            val p = S.boxPos[i]
            Kv("Hộp phụ ${i + 1}", when { p == 0 -> "Khác"; p in 2..5 -> listOf("Trước trái", "Trước phải", "Sau trái", "Sau phải")[p - 2]; else -> "Theo hộp" })
        }
        Spacer(Modifier.height(6.dp))
        Hint("Vị trí cửa đã học được lưu trong hộp điều khiển. Cài lại app hoặc đổi điện thoại thì hộp vẫn chạy đúng như đã học, app chỉ hiện \"Theo hộp\" vì không đọc lại được. Chỉ gán lại (tab Chào mừng, chạm hộp phụ trên sơ đồ xe) khi muốn đổi.", 12)
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text(L.tr("Xóa hộp phụ?")) },
        text = { Text(L.tr("Tất cả hộp phụ đã ghép sẽ bị xóa khỏi hộp chính, phải ghép lại từ đầu.")) },
        confirmButton = { TextButton({ confirm = false; X.clearBoxes() }) { Text(L.tr("Xóa")) } },
        dismissButton = { TextButton({ confirm = false }) { Text(L.tr("Hủy")) } }
    )
}

// ===================== HƯỚNG ÁNH SÁNG CHẠY =====================
@Composable
fun FlowPage() {
    val ctx = LocalContext.current
    X.ver.intValue
    BackRow(0, "Cài đặt")
    Card {
        GroupTitle(Icons.Filled.ArrowForward, "Hướng ánh sáng chạy", 16)
        Spacer(Modifier.height(4.dp))
        Hint("Chọn Thuận hoặc Ngược cho từng khu vực.", 12)
        val names = listOf("Bảng đồng hồ trái", "Bảng đồng hồ phải", "Cửa trước trái", "Cửa trước phải", "Cửa sau trái", "Cửa sau phải")
        for (s in 0 until 6) {
            Spacer(Modifier.height(10.dp))
            T(names[s], 14, FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                val known = S.p07 != null
                Chip("Thuận", known && !X.flow(s), Modifier.weight(1f), 44.dp, 13) { if (!X.setFlow(s, false)) need(ctx) }
                Chip("Ngược", known && X.flow(s), Modifier.weight(1f), 44.dp, 13) { if (!X.setFlow(s, true)) need(ctx) }
            }
        }
    }
}

// ===================== MÀU CHÀO MỪNG TÙY CHỈNH =====================
/** Hai hàng màu (chiều thuận, chiều ngược) cho chế độ chào mừng Tùy chỉnh. Đặt trong một Card có sẵn. */
@Composable
fun WelcomeColors() {
    val ctx = LocalContext.current
    X.ver.intValue
    LaunchedEffect(Unit) { if (X.p09 == null && S.connected.value) S.ble.send(Protocol.query(0x09)) }
    for (rev in listOf(false, true)) {
        Spacer(Modifier.height(12.dp))
        T(if (rev) "Màu chiều ngược" else "Màu chiều thuận", 13, FontWeight.Bold, SubTxt)
        Spacer(Modifier.height(8.dp))
        val num = X.p09?.get(if (rev) 4 else 0) ?: -1
        Grid(PRE.indices.toList(), 5) { i, m ->
            val on = num == i + 1
            Box(
                m.height(40.dp).clip(RoundedCornerShape(Theme.r(6))).background(rgbColor(PRE_SHOW[i]))
                    .border(if (on) 3.dp else 1.dp, if (on) Txt else Bd, RoundedCornerShape(Theme.r(6)))
                    .clickable { if (!X.pickWelcome(rev, i, PRE[i])) need(ctx) },
                contentAlignment = Alignment.Center
            ) { if (on) Icon(Icons.Filled.Check, null, tint = if (i == 1 || i == 9) Color(0xFF14142B) else Color.White, modifier = Modifier.size(20.dp)) }
        }
    }
}

@Composable
fun WelcomeColorPage() {
    BackRow(0, "Cài đặt")
    Card { GroupTitle(Icons.Filled.Palette, "Màu chào mừng tùy chỉnh", 16); WelcomeColors() }
}

// ===================== CÀI ĐẶT NHÀ MÁY =====================
@Composable
fun FactoryPage() {
    val ctx = LocalContext.current
    X.ver.intValue; S.blkVer.intValue
    var reset by remember { mutableIntStateOf(0) }      // 0 không, 1 hỏi lần một, 2 hỏi lần hai
    BackRow(0, "Cài đặt")
    Card {
        GroupTitle(Icons.Filled.Build, "Cài đặt nhà máy", 16)
        Spacer(Modifier.height(6.dp))
        // Theo app gốc: bit 6 của b1 khối 0D = bản hộp ảo ảnh, 0 "Thể thao" (sport), 1 "Ánh sáng" (light & shadow).
        T("Phiên bản hộp ảo ảnh", 15, FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            val known = S.fac != null
            Chip("Thể thao", known && X.facBits(0, 0x40) == 0, Modifier.weight(1f), 44.dp, 13) { if (!X.setFac(0, 0x40, 0)) need(ctx) }
            Chip("Ánh sáng", known && X.facBits(0, 0x40) != 0, Modifier.weight(1f), 44.dp, 13) { if (!X.setFac(0, 0x40, 0x40)) need(ctx) }
        }
        Spacer(Modifier.height(6.dp))
        Hint("Chỉ đổi khi biết hộp thuộc bản nào: chọn sai thì một số hiệu ứng chạy không đúng.", 12)
        Divider(10.dp, 10.dp)
        T("Màu gửi cho hộp", 15, FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Chip("Bình thường", !S.invert.value, Modifier.weight(1f), 44.dp, 13) { S.setInvert(false) }
            Chip("Đảo màu", S.invert.value, Modifier.weight(1f), 44.dp, 13) { S.setInvert(true) }
        }
        Spacer(Modifier.height(6.dp))
        Hint("Để Bình thường. Chỉ đổi sang Đảo màu nếu chọn đỏ mà đèn ra xanh băng.", 12)
    }
    Card {
        Feature(Icons.Filled.DirectionsCar, "Chọn loại xe", if (X.carCur >= 0 && X.cars.isNotEmpty()) X.carName(X.carCur) else "Chọn đúng xe để hộp nhận tín hiệu từ xe") { S.subPage.intValue = 11 }
        Feature(Icons.Filled.Info, "Thông tin chẩn đoán", "Đọc thông tin chẩn đoán từ hộp") { S.subPage.intValue = 10 }
    }
    Card {
        T("Đặt lại", 15, FontWeight.Bold)
        Feature(Icons.Filled.Refresh, "Khôi phục cài đặt gốc", "Đưa hộp về cài đặt ban đầu của nhà sản xuất") {
            if (!S.connected.value) Notice.show(ctx, L.tr("Chưa kết nối hộp đèn.")) else reset = 1
        }
        if (X.resetState != 0) T(if (X.resetState == 2) "Hộp báo đã khôi phục xong." else "Đã gửi lệnh khôi phục, đang chờ hộp trả lời…", 12, FontWeight.SemiBold, SubTxt)
    }
    Card {
        T("Chụp trạng thái hộp (kiểm tra)", 15, FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Hint("Dùng để tìm một cài đặt nằm ở đâu trong hộp: chụp một lần, đổi đúng MỘT thứ (bằng app gốc hoặc nút cơ), kết nối lại rồi chụp tiếp. Làm lần lượt cho từng thứ cần tìm, cuối cùng bấm Sao chép để gửi một lần. App chỉ đọc, không ghi gì vào hộp. Các lần chụp được nhớ kể cả khi thoát app.", 12)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            FillBtn(if (X.snapBusy) "Đang chụp…" else "Chụp lần ${X.snaps.size + 1}", Icons.Filled.Refresh, Pink, Modifier.weight(1.4f)) {
                if (!S.connected.value) Notice.show(ctx, L.tr("Chưa kết nối hộp đèn.")) else X.snapshot()
            }
            OutlineBtn("Xóa hết", Modifier.weight(1f)) { X.clearSnaps() }
        }
        Spacer(Modifier.height(8.dp))
        if (X.snaps.isEmpty()) T("Chưa chụp lần nào.", 12, color = SubTxt)
        X.snaps.forEachIndexed { i, sn ->
            val diff = X.snapDiff(i)
            T("Lần ${i + 1} lúc ${sn.substringBefore("\n")}" + (if (i == 0) "" else if (diff.isEmpty()) ": giống lần ${i}" else ": khác lần ${i} ở ${diff.size} khối"), 13, FontWeight.Bold)
            for (l in diff) T(l, 11, color = SubTxt)
        }
        Spacer(Modifier.height(8.dp))
        OutlineBtn("Sao chép để gửi", Modifier.fillMaxWidth()) {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("snap", X.snapText()))
            Notice.show(ctx, L.tr("Đã sao chép"))
        }
    }
    Card {
        T("Chưa có trong app này", 14, FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Hint("Nâng cấp phần mềm hộp cần tệp phần mềm của nhà sản xuất và dễ làm hỏng hộp nếu nạp lỗi, nên chưa làm. Cần thì dùng app gốc của hãng.", 12)
    }
    if (reset == 1) AlertDialog(
        onDismissRequest = { reset = 0 },
        title = { Text(L.tr("Khôi phục cài đặt gốc?")) },
        text = { Text(L.tr("Hộp sẽ xóa hết cài đặt: hộp phụ đã ghép, vị trí cửa, số LED, loại xe, chào mừng. Phải cài lại từ đầu.")) },
        confirmButton = { TextButton({ reset = 2 }) { Text(L.tr("Tiếp tục")) } },
        dismissButton = { TextButton({ reset = 0 }) { Text(L.tr("Hủy")) } }
    )
    if (reset == 2) AlertDialog(
        onDismissRequest = { reset = 0 },
        title = { Text(L.tr("Chắc chắn khôi phục?")) },
        text = { Text(L.tr("Không hoàn tác được. Giữ nguyên nguồn điện của hộp cho tới khi xong.")) },
        confirmButton = { TextButton({ reset = 0; X.factoryReset() }) { Text(L.tr("Khôi phục")) } },
        dismissButton = { TextButton({ reset = 0 }) { Text(L.tr("Hủy")) } }
    )
}

// ===================== CHỨC NĂNG GỐC XE =====================
@Composable
fun CarFuncPage() {
    val ctx = LocalContext.current
    S.blkVer.intValue
    BackRow(0, "Cài đặt")
    X.ver.intValue
    LaunchedEffect(Unit) { if (S.connected.value) S.ble.send(Protocol.frame(Protocol.T_READ, 0x9C, 0x01)) }
    val sup = X.sup
    // Giống app gốc: chỉ hiện chức năng mà hộp báo xe này hỗ trợ. Chưa đọc được thì hiện hết.
    fun ok(i: Int, mask: Int): Boolean = when {
        sup == null -> true
        i == 0 -> sup[0] and mask != 0
        mask == 0x20 -> sup[0] and 0x10 != 0            // ẩn đèn gốc: đi kèm "đèn theo đèn xe"
        else -> sup[1] and mask != 0
    }
    val rows = listOf(
        Triple(0, 0x80, "Cảnh báo xi nhan") to "Nháy đèn theo xi nhan",
        Triple(1, 0x10, "Nhắc nhở đèn hazard") to "Nháy theo đèn khẩn cấp",
        Triple(0, 0x40, "Cảnh báo phanh tay") to "Báo khi chưa nhả phanh tay",
        Triple(0, 0x20, "Cảnh báo dây an toàn") to "Báo khi chưa cài dây",
        Triple(0, 0x08, "Cảnh báo cửa") to "Báo khi cửa chưa đóng",
        Triple(0, 0x04, "Cảnh báo radar") to "Báo khi gần vật cản",
        Triple(1, 0x02, "Cảnh báo điểm mù") to "Báo khi có xe trong điểm mù",
        Triple(0, 0x01, "Cảnh báo quá tốc độ") to "Báo khi vượt ngưỡng tốc độ bên dưới",
        Triple(1, 0x01, "Cảnh báo áp suất lốp") to "Báo khi lốp thiếu hơi",
        Triple(0, 0x02, "Hiệu ứng vòng tua") to "Đèn chạy theo vòng tua máy",
        Triple(1, 0x08, "Chế độ khí hậu") to "Đèn đổi màu theo nhiệt độ điều hòa",
        Triple(0, 0x10, "Đèn theo đèn xe") to "Đèn hộp sáng theo đèn pha/đèn vị trí",
        Triple(1, 0x20, "Ẩn đèn gốc của xe") to "Ẩn đèn gốc khi hộp đang chạy"
    ).filter { ok(it.first.first, it.first.second) }
    Card {
        GroupTitle(Icons.Filled.DirectionsCar, "Chức năng gốc xe", 16)
        Spacer(Modifier.height(4.dp))
        T(if (sup == null) "Hộp tự báo bằng đèn khi nhận tín hiệu từ xe. Chưa đọc được xe hỗ trợ gì nên đang hiện tất cả; mục xe không hỗ trợ thì bật cũng không có tác dụng."
          else if (rows.isEmpty()) "Hộp báo xe này (theo loại xe đang đặt) không hỗ trợ chức năng gốc nào."
          else "Chỉ hiện các chức năng mà hộp báo xe này hỗ trợ (theo loại xe đang đặt).", 12, color = SubTxt)
        for ((k, sub) in rows) {
            val (i, mask, title) = k
            Divider()
            SwitchRow(title, sub, S.blkBit(i, mask)) { if (!S.setBlk(i, mask, if (it) mask else 0)) need(ctx) }
        }
        Divider()
        SwitchRow("Chào mừng khi mở cửa lái chính", "Hiệu ứng chào mừng chạy khi mở cửa lái", S.blkBit(3, 0x08)) { if (!S.setBlk(3, 0x08, if (it) 0x08 else 0)) need(ctx) }
    }
    // Theo app gốc: mỗi cảnh báo có 2 kiểu (ghi 0 / 1 vào nửa byte tương ứng); "đèn theo đèn xe" có 4 kiểu (2 bit cao của b2).
    // (tên, vị trí byte, số bit dịch, mặt nạ, số kiểu) + điều kiện hiện
    val groups = listOf(
        listOf("Chế độ đèn xi-nhan", 5, 4, 0xF0, 2) to ok(0, 0x80),
        listOf("Kiểu cảnh báo cửa", 2, 4, 0xF0, 2) to ok(0, 0x08),
        listOf("Kiểu cảnh báo radar", 2, 0, 0x0F, 2) to ok(0, 0x04),
        listOf("Kiểu cảnh báo quá tốc độ", 5, 0, 0x0F, 2) to ok(0, 0x01),
        listOf("Kiểu đèn theo đèn xe", 1, 6, 0xC0, 4) to ok(0, 0x10)
    ).filter { it.second }.map { it.first }
    if (groups.isNotEmpty()) Card {
        T("Kiểu hiển thị", 15, FontWeight.Bold)
        val known = S.blk != null
        for (g in groups) {
            val title = g[0] as String; val idx = g[1] as Int; val shift = g[2] as Int; val mask = g[3] as Int; val n = g[4] as Int
            Spacer(Modifier.height(10.dp))
            T(title, 13, FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            val cur = if (known) ((S.blk!![idx] and mask) shr shift) else -1
            Grid((0 until n).toList(), 4) { v, m -> Chip("Chế độ ${v + 1}", cur == v, m, 42.dp, 12) { if (!S.setBlk(idx, mask, v shl shift)) need(ctx) } }
        }
    }
    if (ok(0, 0x01)) Card {
        GroupTitle(Icons.Filled.Speed, "Ngưỡng quá tốc độ của hộp", 16)
        Spacer(Modifier.height(6.dp))
        val v = (S.blk?.get(4) ?: 0).coerceIn(30, 180)
        Kv("Ngưỡng", if (S.blk == null) "—" else "${S.blk!![4]} km/h")
        SkSlider((v - 30) / 150f, { f -> if (!S.setBlk(4, 0xFF, 30 + (f * 15).roundToInt() * 10)) need(ctx) }, Pink, SoftTrack, Color.White, Modifier.fillMaxWidth(), 8.dp, 21.dp, 15)
    }
}

// ===================== THÔNG TIN CHẨN ĐOÁN =====================
@Composable
fun DiagPage() {
    val ctx = LocalContext.current
    BackRow(8, "Cài đặt nhà máy")
    Card {
        GroupTitle(Icons.Filled.Info, "Thông tin chẩn đoán", 16)
        Spacer(Modifier.height(6.dp))
        Hint("Chỉ đọc. Thông tin do hộp gửi về, sao chép gửi cho kỹ thuật khi cần.", 12)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FillBtn("Đọc dữ liệu", Icons.Filled.Refresh, Pink, Modifier.weight(1f)) { X.readDiag() }
            FillBtn("Sao chép", Icons.Filled.ContentCopy, Color(0xFFFE9700), Modifier.weight(1f)) {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("diag", (X.diag + listOf("--- khung thô ---") + X.diagRaw).joinToString("\n")))
                Notice.show(ctx, L.tr("Đã sao chép"))
            }
        }
        Spacer(Modifier.height(10.dp))
        if (X.diag.isEmpty()) T(if (X.diagRaw.isEmpty()) "Chưa có dữ liệu." else "Đã nhận ${X.diagRaw.size} khung nhưng chưa đọc được chữ. Bấm Sao chép để gửi.", 13, color = SubTxt)
        else for (l in X.diag) T(l, 12, color = Txt)
    }
}

// ===================== CHỌN LOẠI XE =====================
@Composable
fun CarModelPage() {
    val ctx = LocalContext.current
    X.ver.intValue
    var open by remember { mutableIntStateOf(-1) }       // hãng đang mở
    var ask by remember { mutableIntStateOf(-1) }        // (hãng << 8) | dòng đang hỏi xác nhận
    LaunchedEffect(Unit) { if (S.connected.value) X.readCars() }
    LaunchedEffect(X.carTick) { delay(1500); X.carLoading = false }      // hết 1,5 giây không có khung mới = xong, như app gốc
    BackRow(8, "Cài đặt nhà máy")
    Card {
        GroupTitle(Icons.Filled.DirectionsCar, "Chọn loại xe", 16)
        Spacer(Modifier.height(6.dp))
        Kv("Loại xe đang đặt", if (X.carCur < 0) "—" else X.carName(X.carCur))
        val series = X.cars.count { it.second == 0 }
        Kv("Danh sách", if (X.carLoading) "Đang đọc từ hộp…" else if (X.cars.isEmpty()) "Hộp chưa gửi danh sách" else "$series hãng, ${X.cars.size - series} dòng xe")
        Spacer(Modifier.height(4.dp))
        Hint("Danh sách này nằm trong hộp đang kết nối: mỗi loại hộp hỗ trợ một nhóm xe riêng. Hộp gửi tên tiếng Trung, app đổi sang tên quốc tế; tên nào còn chữ Trung thì bấm Sao chép gửi SK để bổ sung.", 12)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FillBtn("Đọc lại", Icons.Filled.Refresh, Pink, Modifier.weight(1f)) {
                if (!S.connected.value) Notice.show(ctx, L.tr("Chưa kết nối hộp đèn.")) else X.readCars()
            }
            FillBtn("Sao chép", Icons.Filled.ContentCopy, Color(0xFFFE9700), Modifier.weight(1f)) {
                val lines = X.cars.map { "${it.first}/${it.second}: ${it.third} = ${CarNames.tr(it.third)}" } + listOf("đang đặt: ${X.carCur}", "--- khung thô ---") + X.carRaw
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("cars", lines.joinToString("\n")))
                Notice.show(ctx, L.tr("Đã sao chép"))
            }
        }
    }
    val ids = X.cars.map { it.first }.distinct()
    if (ids.isNotEmpty()) Card {
        for ((n, sId) in ids.withIndex()) {
            if (n > 0) Divider()
            val name = X.cars.firstOrNull { it.first == sId && it.second == 0 }?.third?.ifEmpty { null }?.let { CarNames.tr(it) } ?: "Hãng $sId"
            val models = X.cars.filter { it.first == sId && it.second != 0 }
            val cur = X.carCur >= 0 && X.carCur shr 8 == sId
            Row(
                Modifier.fillMaxWidth().heightIn(min = 50.dp).clickable { if (models.isEmpty()) ask = sId shl 8 else open = if (open == sId) -1 else sId },
                verticalAlignment = Alignment.CenterVertically
            ) {
                T(name, 15, FontWeight.Bold, if (cur) Pink else Txt, Modifier.weight(1f))
                if (models.isNotEmpty()) Icon(if (open == sId) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = SubTxt, modifier = Modifier.size(22.dp))
                else if (cur) Icon(Icons.Filled.Check, null, tint = Pink, modifier = Modifier.size(20.dp))
            }
            if (open == sId) for (m in models) {
                val on = X.carCur == (sId shl 8) or m.second
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { ask = (sId shl 8) or m.second }.padding(start = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    T(if (m.third.isEmpty()) "Dòng ${m.second}" else CarNames.tr(m.third), 14, if (on) FontWeight.Bold else FontWeight.Normal, if (on) Pink else Txt, Modifier.weight(1f))
                    if (on) Icon(Icons.Filled.Check, null, tint = Pink, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
    if (ask >= 0) AlertDialog(
        onDismissRequest = { ask = -1 },
        title = { Text(L.tr("Đặt loại xe?")) },
        text = { Text(X.carName(ask)) },
        confirmButton = { TextButton({ val k = ask; ask = -1; X.setCar(k shr 8, k and 0xFF) }) { Text(L.tr("Đặt")) } },
        dismissButton = { TextButton({ ask = -1 }) { Text(L.tr("Hủy")) } }
    )
}

// ===================== GIỌNG NÓI CỦA HỘP =====================
/** Cài đặt mô-đun giọng nói cắm trong hộp điều khiển: giọng nữ / nam, im lặng / thông báo chi tiết. */
@Composable
fun BoxVoiceCard() {
    val ctx = LocalContext.current
    X.ver.intValue
    LaunchedEffect(Unit) { if (S.p07 == null && S.connected.value) S.ble.send(Protocol.query(0x07)) }
    val known = S.p07 != null
    Card {
        GroupTitle(Icons.Filled.RecordVoiceOver, "Bạn đồng hành giọng nói", 16)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for ((male, face, label) in listOf(Triple(false, "👩", "Giọng nữ tiêu chuẩn"), Triple(true, "👨", "Giọng nam tiêu chuẩn"))) {
                val on = known && X.voiceBit(0x02) == male
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(Theme.r(10))).background(if (on) tint(0.1f) else ChipBg)
                        .border(if (on) 2.dp else 1.dp, if (on) Pink else Bd, RoundedCornerShape(Theme.r(10)))
                        .clickable { if (!X.setVoiceBit(0x02, male)) need(ctx) }.padding(vertical = 10.dp, horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(face, fontSize = 30.sp)
                    Spacer(Modifier.height(4.dp))
                    T(label, 13, if (on) FontWeight.Bold else FontWeight.SemiBold, if (on) Pink else SubTxt, align = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
        Divider(14.dp, 12.dp)
        GroupTitle(Icons.Filled.VolumeUp, "Phương thức thông báo", 16)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Chip("Im lặng", known && !X.voiceBit(0x01), Modifier.weight(1f), 44.dp, 13) { if (!X.setVoiceBit(0x01, false)) need(ctx) }
            Chip("Thông báo chi tiết", known && X.voiceBit(0x01), Modifier.weight(1f), 44.dp, 13) { if (!X.setVoiceBit(0x01, true)) need(ctx) }
        }
        Spacer(Modifier.height(8.dp))
        T(if (!known) "Chưa đọc được cài đặt giọng nói từ hộp."
          else if (X.voiceBit(0x01)) "Thông báo chi tiết: thiết bị sẽ thông báo phản hồi và gợi ý trạng thái."
          else "Im lặng: thiết bị làm theo lệnh nhưng không nói lại.", 12, color = SubTxt)
    }
}
