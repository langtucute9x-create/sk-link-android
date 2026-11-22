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
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Các trang cài đặt bổ sung (ghép nối hộp phụ, hướng chạy, màu chào mừng, nhà máy, chức năng gốc xe, chẩn đoán). Chưa chạy thử trên hộp thật. */
object X {
    val ver = mutableIntStateOf(0)
    var pairRaw by mutableIntStateOf(-1)                 // byte trạng thái ghép nối đọc từ hộp (khối 03), -1 = chưa đọc
    var pairing by mutableStateOf(false)
    var pairLeft by mutableIntStateOf(0)
    var p09: IntArray? = null                            // khối 09: số ô trước, RGB, số ô sau, RGB
    val diag = mutableStateListOf<String>()

    private val dirBit = intArrayOf(5, 4, 0, 1, 2, 3)    // đoạn -> bit, cùng thứ tự khối 08

    fun onPair(b: Int) { pairRaw = b; ver.intValue++ }
    fun on09(v: IntArray) { p09 = v; ver.intValue++ }
    fun on07() { ver.intValue++ }
    fun onDiag(d: ByteArray) { diag.add(d.joinToString(" ") { "%02X".format(it) }); if (diag.size > 40) diag.removeAt(0) }

    fun paired() = if (pairRaw < 0) -1 else
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

    fun send09() {
        val p = p09 ?: return
        S.ble.send(Protocol.frame(Protocol.T_WRITE, 0x09, p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7]), 24, true)
    }
    fun pickWelcome(reverse: Boolean, idx: Int, argb: Int) {
        val p = (p09 ?: IntArray(8)).copyOf()
        val o = if (reverse) 4 else 0
        p[o] = idx + 1; p[o + 1] = (argb shr 16) and 0xFF; p[o + 2] = (argb shr 8) and 0xFF; p[o + 3] = argb and 0xFF
        p09 = p; ver.intValue++
        send09()
    }
}

private val PRE = intArrayOf(-65536, -256, -16711936, -16711681, -16776961, -65281, -7601668, -65504, -11530240)

private fun need(ctx: Context) {
    S.refreshBlocks()
    Toast.makeText(ctx, L.tr("Chưa đọc được cài đặt từ hộp. Kiểm tra kết nối rồi thử lại."), Toast.LENGTH_SHORT).show()
}

@Composable
fun BackRow(to: Int, label: String) {
    Row(
        Modifier.height(44.dp).clip(RoundedCornerShape(8.dp)).background(CardBg).border(1.dp, Bd, RoundedCornerShape(8.dp)).clickable { S.subPage.intValue = to }.padding(start = 10.dp, end = 14.dp),
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
        Kv("Số hộp phụ đã ghép", if (n < 0) "—" else "$n")
        Kv("Số lượng đã cài", if (S.fac == null) "—" else "${S.fac!![1] and 0x0F}")
        Kv("Trạng thái", if (X.pairing) "Đang ghép nối… còn ${X.pairLeft} giây" else "Chưa ghép")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FillBtn(if (X.pairing) "Dừng" else "Bắt đầu ghép nối", Icons.Filled.BluetoothSearching, if (X.pairing) Color(0xFFF44336) else Pink, Modifier.weight(1f)) {
                if (X.pairing) X.stopPair() else X.startPair()
            }
            FillBtn("Xóa hộp phụ", Icons.Filled.Delete, Color(0xFFF44336), Modifier.weight(1f)) { confirm = true }
        }
        Spacer(Modifier.height(8.dp))
        T("Bật nguồn hộp phụ cần ghép, bấm Bắt đầu rồi đợi tối đa 60 giây.", 12, color = SubTxt)
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
        val names = listOf("Chưa gán", "Khác", "", "", "Trước trái", "Trước phải", "Sau trái", "Sau phải")
        for (i in 0 until 4) {
            val p = S.boxPos[i]
            Kv("Hộp phụ ${i + 1}", when { p == -1 -> "Chưa gán"; p == 0 -> "Khác"; p in 2..5 -> listOf("Trước trái", "Trước phải", "Sau trái", "Sau phải")[p - 2]; else -> names[0] })
        }
        Spacer(Modifier.height(6.dp))
        T("Gán hộp vào cửa ở tab Chào mừng (chạm hộp phụ trên sơ đồ xe).", 12, color = SubTxt)
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
        T("Chọn Thuận hoặc Ngược cho từng khu vực.", 12, color = SubTxt)
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
@Composable
fun WelcomeColorPage() {
    val ctx = LocalContext.current
    X.ver.intValue
    BackRow(0, "Cài đặt")
    for (rev in listOf(false, true)) {
        Card {
            GroupTitle(Icons.Filled.Palette, if (rev) "Màu chào mừng — chiều ngược (khóa xe)" else "Màu chào mừng — chiều thuận (mở xe)", 15)
            Spacer(Modifier.height(10.dp))
            val num = X.p09?.get(if (rev) 4 else 0) ?: -1
            Grid(PRE.indices.toList(), 5) { i, m ->
                val on = num == i + 1
                Box(
                    m.height(42.dp).clip(RoundedCornerShape(6.dp)).background(Color(PRE[i]))
                        .border(if (on) 3.dp else 1.dp, if (on) Txt else Bd, RoundedCornerShape(6.dp))
                        .clickable { X.pickWelcome(rev, i, PRE[i]) },
                    contentAlignment = Alignment.Center
                ) { if (on) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
            }
        }
    }
    T("Chiều thuận và chiều ngược có thể chọn hai màu khác nhau. Dùng khi chế độ chào mừng là Tùy chỉnh.", 12, color = SubTxt)
    if (X.p09 == null) FillBtn("Đọc lại từ hộp", Icons.Filled.Refresh, Pink, Modifier.fillMaxWidth()) { need(ctx) }
}

// ===================== CÀI ĐẶT NHÀ MÁY =====================
@Composable
fun FactoryPage() {
    val ctx = LocalContext.current
    X.ver.intValue; S.blkVer.intValue
    var confirm by remember { mutableStateOf(false) }
    BackRow(0, "Cài đặt")
    Card {
        GroupTitle(Icons.Filled.Build, "Cài đặt nhà máy", 16)
        Spacer(Modifier.height(6.dp))
        SwitchRow("Chào mừng khi mở cửa lái chính", "Hiệu ứng chào mừng chạy khi mở cửa lái", S.blkBit(3, 0x08)) { if (!S.setBlk(3, 0x08, if (it) 0x08 else 0)) need(ctx) }
        Divider()
        Spacer(Modifier.height(10.dp))
        T("Phiên bản hộp", 15, FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            val known = S.fac != null
            Chip("Toàn màu", known && X.facBits(0, 0x40) == 0, Modifier.weight(1f), 44.dp, 13) { if (!X.setFac(0, 0x40, 0)) need(ctx) }
            Chip("Ảo ảnh", known && X.facBits(0, 0x40) != 0, Modifier.weight(1f), 44.dp, 13) { if (!X.setFac(0, 0x40, 0x40)) need(ctx) }
        }
        Spacer(Modifier.height(6.dp))
        T("Đổi phiên bản có thể làm hộp hiện màu khác, chỉ đổi khi biết hộp thuộc loại nào.", 12, color = SubTxt)
    }
    Card {
        Feature(Icons.Filled.DirectionsCar, "Chức năng gốc xe", "Cảnh báo theo tín hiệu xe: xi nhan, phanh tay, dây an toàn, cửa…") { S.subPage.intValue = 9 }
        Feature(Icons.Filled.Info, "Thông tin chẩn đoán", "Đọc dữ liệu chẩn đoán từ hộp") { S.subPage.intValue = 10 }
        Feature(Icons.Filled.Settings, "Cấu hình cửa xe", "Xóa cấu hình gán hộp phụ vào cửa") { confirm = true }
    }
    Card {
        T("Chưa có trong app này", 14, FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        T("Nâng cấp phần mềm hộp, Khôi phục cài đặt gốc và Chọn loại xe dễ làm hỏng hộp nên chưa làm. Cần thì dùng app gốc của hãng.", 12, color = SubTxt)
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text(L.tr("Xóa cấu hình cửa xe?")) },
        text = { Text(L.tr("Các hộp phụ sẽ về trạng thái chưa gán cửa, cần gán lại ở tab Chào mừng.")) },
        confirmButton = { TextButton({ confirm = false; S.resetDoors() }) { Text(L.tr("Xóa")) } },
        dismissButton = { TextButton({ confirm = false }) { Text(L.tr("Hủy")) } }
    )
}

// ===================== CHỨC NĂNG GỐC XE =====================
@Composable
fun CarFuncPage() {
    val ctx = LocalContext.current
    S.blkVer.intValue
    BackRow(8, "Cài đặt nhà máy")
    Card {
        GroupTitle(Icons.Filled.DirectionsCar, "Chức năng gốc xe", 16)
        Spacer(Modifier.height(4.dp))
        T("Hộp tự báo bằng đèn khi nhận tín hiệu CAN từ xe.", 12, color = SubTxt)
        val rows = listOf(
            Triple(0, 0x80, "Cảnh báo xi nhan") to "Nháy đèn theo xi nhan",
            Triple(0, 0x40, "Cảnh báo phanh tay") to "Báo khi chưa nhả phanh tay",
            Triple(0, 0x20, "Cảnh báo dây an toàn") to "Báo khi chưa cài dây",
            Triple(0, 0x10, "Đèn theo đèn xe") to "Đèn hộp sáng theo đèn pha/đèn vị trí",
            Triple(0, 0x08, "Cảnh báo cửa") to "Báo khi cửa chưa đóng",
            Triple(0, 0x04, "Cảnh báo radar") to "Báo khi gần vật cản",
            Triple(0, 0x02, "Hiệu ứng vòng tua") to "Đèn chạy theo vòng tua máy",
            Triple(0, 0x01, "Cảnh báo quá tốc độ") to "Báo khi vượt ngưỡng tốc độ bên dưới",
            Triple(1, 0x20, "Ẩn đèn gốc của xe") to "Ẩn đèn gốc khi hộp đang chạy",
            Triple(1, 0x10, "Nháy đôi") to "Nháy hai lần mỗi nhịp",
            Triple(1, 0x08, "Điều hòa") to "Đèn đổi màu theo nhiệt độ điều hòa",
            Triple(1, 0x02, "Cảnh báo điểm mù") to "Báo khi có xe trong điểm mù",
            Triple(1, 0x01, "Cảnh báo áp suất lốp") to "Báo khi lốp thiếu hơi"
        )
        for ((k, sub) in rows) {
            val (i, mask, title) = k
            Divider()
            SwitchRow(title, sub, S.blkBit(i, mask)) { if (!S.setBlk(i, mask, if (it) mask else 0)) need(ctx) }
        }
    }
    Card {
        T("Kiểu hiển thị", 15, FontWeight.Bold)
        val known = S.blk != null
        val groups = listOf(
            Triple("Kiểu cảnh báo cửa", 2, 4) to 3,
            Triple("Kiểu cảnh báo radar", 2, 0) to 3,
            Triple("Kiểu cảnh báo xi nhan", 5, 4) to 1,
            Triple("Kiểu cảnh báo quá tốc độ", 5, 0) to 3
        )
        for ((g, maxV) in groups) {
            val (title, idx, shift) = g
            val base = if (idx == 5 && shift == 4) 1 else 0   // xi nhan: app gốc dùng giá trị 1 và 2
            Spacer(Modifier.height(10.dp))
            T(title, 13, FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            val mask = (if (idx == 1) 0x03 else 0x0F) shl shift
            val cur = if (known) ((S.blk!![idx] and mask) shr shift) else -1
            Grid((0..maxV).toList(), 4) { v, m -> Chip("Kiểu ${v + 1}", cur == v + base, m, 42.dp, 12) { if (!S.setBlk(idx, mask, (v + base) shl shift)) need(ctx) } }
        }
    }
    Card {
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
        T("Chỉ đọc. Dữ liệu thô từ hộp, gửi cho kỹ thuật khi cần.", 12, color = SubTxt)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FillBtn("Đọc dữ liệu", Icons.Filled.Refresh, Pink, Modifier.weight(1f)) { X.diag.clear(); S.ble.send(Protocol.frame(Protocol.T_READ, 0xAC, 0x00)) }
            FillBtn("Sao chép", Icons.Filled.ContentCopy, Color(0xFFFE9700), Modifier.weight(1f)) {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("diag", X.diag.joinToString("\n")))
                Toast.makeText(ctx, L.tr("Đã sao chép"), Toast.LENGTH_SHORT).show()
            }
        }
        Spacer(Modifier.height(10.dp))
        if (X.diag.isEmpty()) T("Chưa có dữ liệu.", 13, color = SubTxt) else for (l in X.diag) T(l, 11, color = Txt)
    }
}
