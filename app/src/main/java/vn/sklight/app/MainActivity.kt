package vn.sklight.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        S.init(this)
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = Pink)) { Root() } }
    }
}

@Composable
fun Root() {
    var tab by remember { mutableIntStateOf(0) }
    var pickDev by remember { mutableStateOf(false) }
    val perms = remember {
        val l = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 31) { l.add(Manifest.permission.BLUETOOTH_SCAN); l.add(Manifest.permission.BLUETOOTH_CONNECT) }
        l.toTypedArray()
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (S.savedMac.isEmpty()) { S.ble.scan(); pickDev = true } else S.autoConnect()
    }
    LaunchedEffect(Unit) { launcher.launch(perms) }

    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFFCF5F9), Color(0xFFF2F0FB)))).systemBarsPadding()) {
        // menu cố định trên cùng, nội dung cuộn bên dưới
        Row(Modifier.padding(start = 13.dp, end = 13.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            PowerBtn()
            Spacer(Modifier.width(8.dp))
            Tabs(tab, Modifier.weight(1f)) { tab = it }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 13.dp, end = 13.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (tab) {
                0 -> LedPage()
                1 -> WelcomePage()
                else -> SettingsPage { S.ble.scan(); pickDev = true }
            }
        }
    }
    if (pickDev) DevDialog { pickDev = false }
}

@Composable
fun StatusRow(onPick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(onClick = onPick).padding(horizontal = 2.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (S.connected.value) OkGreen else Color(0xFFB8BCC6)))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            T(if (S.devName.value.isEmpty()) "Chưa chọn hộp đèn" else S.devName.value, 15, FontWeight.Bold)
            T(S.conn.value, 12, color = Grey)
        }
    }
}

@Composable
fun PowerBtn() {
    val on = S.on.value
    Box(
        Modifier.size(52.dp).clip(CircleShape).background(if (on) Pink else Color.White).border(1.dp, if (on) Pink else Bd, CircleShape).clickable { S.setPower(!on) },
        contentAlignment = Alignment.Center
    ) { Icon(Icons.Filled.PowerSettingsNew, if (on) "Tắt đèn" else "Bật đèn", tint = if (on) Color.White else Grey, modifier = Modifier.size(26.dp)) }
}

@Composable
fun Tabs(sel: Int, modifier: Modifier = Modifier, onPick: (Int) -> Unit) {
    val items = listOf("Đèn" to Icons.Outlined.Lightbulb, "Chào mừng" to Icons.Filled.PanTool, "Cài đặt" to Icons.Filled.Settings)
    Row(
        modifier.clip(RoundedCornerShape(10.dp)).background(Color.White).border(1.dp, Bd, RoundedCornerShape(10.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEachIndexed { i, (label, icon) ->
            val on = sel == i
            Row(
                Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(8.dp)).background(if (on) Pink else Color.Transparent).clickable { onPick(i) },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
            ) {
                Icon(icon, null, tint = if (on) Color.White else SubTxt, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(5.dp))
                T(label, 12, if (on) FontWeight.Bold else FontWeight.SemiBold, if (on) Color.White else Txt)
            }
        }
    }
}

// ===================== TRANG ĐÈN =====================
@Composable
fun LedPage() {
    Card {
        ZoneTabs(S.zone.intValue) { S.setZoneSel(it) }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.LightMode, null, tint = Txt, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            T("Độ sáng", 16, FontWeight.Bold, modifier = Modifier.weight(1f))
            T("${S.bri[S.zone.intValue]}%", 15, FontWeight.ExtraBold, Pink)
        }
        SkSlider(S.bri[S.zone.intValue] / 100f, { S.setBright((it * 100).roundToInt()) }, Pink, Color(0xFFE6E9F2), Color.White,
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 10.dp))
        Divider(16.dp, 14.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Palette, null, tint = Pink, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            T("Chọn màu", 17, FontWeight.Bold, modifier = Modifier.weight(1f))
            Box(Modifier.size(35.dp, 22.dp).clip(RoundedCornerShape(6.dp)).background(rgbColor(S.col[S.czone.intValue])).border(1.dp, Bd, RoundedCornerShape(6.dp)))
        }
        Spacer(Modifier.height(12.dp))
        ZoneTabs(S.czone.intValue) { S.setColorZone(it) }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            ColorWheel()
            Swatches(Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        val c = S.col[S.czone.intValue]
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Tune, null, tint = Grey, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            T("Màu hiện tại: RGB(${(c shr 16) and 255}, ${(c shr 8) and 255}, ${c and 255})", 12, color = Grey)
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().border(1.dp, Bd, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.GraphicEq, null, tint = Pink, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                T("Chọn chế độ màu vùng 2 huyền ảo", 13, FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            Row {
                listOf("Màu tĩnh", "Màu tự động", "Màu nhấp nháy").forEachIndexed { i, t ->
                    val on = S.z2.intValue == i
                    Row(Modifier.weight(1f).heightIn(min = 40.dp).clickable { S.setZ2(i) }, verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(18.dp).border(2.dp, if (on) Pink else Color(0xFFC5CBD9), CircleShape), contentAlignment = Alignment.Center) {
                            if (on) Box(Modifier.size(8.dp).clip(CircleShape).background(Pink))
                        }
                        Spacer(Modifier.width(6.dp))
                        T(t, 11, if (on) FontWeight.Bold else FontWeight.Medium, if (on) Txt else Grey)
                    }
                }
            }
        }
    }
    for (g in Effects.groups) EffectGroupCard(g)
    Card {
        GroupTitle(Icons.Filled.Tune, "Hiệu ứng", 19)
        Spacer(Modifier.height(14.dp))
        ValueRow(Icons.Filled.Speed, Cyan, "Tốc độ nháy & chạy", S.speed.intValue, Color(0xFFD9FBF9), Cyan, Color(0xFF12C9C0))
        SliderRow("Chậm", "Nhanh") { m ->
            SkSlider((S.speed.intValue - 1) / 9f, { S.setSpeed(1 + (it * 9).roundToInt()) }, Cyan, Color(0xFFD9FBF9), Cyan, m, 8.dp, 21.dp, 10)
        }
        Divider(14.dp, 14.dp)
        ValueRow(Icons.Filled.GraphicEq, Pink, "Độ nhạy nhịp điệu", S.sens.intValue, Color(0xFFFED8EF), Color(0xFFFF7CC8), Pink)
        SliderRow("Thấp", "Cao") { m ->
            SkSlider(S.sens.intValue / 7f, { S.setSens((it * 7).roundToInt()) }, Pink, Color(0xFFFED8EF), Pink, m, 8.dp, 21.dp, 8)
        }
    }
}

@Composable
fun ValueRow(icon: ImageVector, iconTint: Color, title: String, value: Int, boxBg: Color, boxBd: Color, numColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        T(title, 15, FontWeight.Bold, modifier = Modifier.weight(1f))
        Box(Modifier.size(36.dp, 27.dp).clip(RoundedCornerShape(6.dp)).background(boxBg).border(1.dp, boxBd, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
            T("$value", 14, FontWeight.ExtraBold, numColor)
        }
    }
}

@Composable
fun SliderRow(left: String, right: String, slider: @Composable (Modifier) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp)) {
        T(left, 11, color = Grey, modifier = Modifier.width(44.dp))
        slider(Modifier.weight(1f))
        T(right, 11, color = Grey, modifier = Modifier.width(44.dp), align = TextAlign.End)
    }
}

@Composable
fun EffectGroupCard(g: EffectGroup) {
    Card {
        GroupTitle(g.icon, g.name)
        Spacer(Modifier.height(14.dp))
        Grid(g.items, 3, 9.dp, 11.dp) { e, m ->
            val on = S.mode.intValue == e.id
            Row(
                m.height(56.dp).clip(RoundedCornerShape(8.dp)).background(if (on) mix(e.color, 0.5f) else ChipBg)
                    .border(if (on) 2.dp else 1.dp, if (on) e.color else Bd, RoundedCornerShape(8.dp)).clickable { S.pickEffect(e) }
                    .padding(start = 10.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(e.icon, null, tint = e.color, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                T(e.name, 12, if (on) FontWeight.ExtraBold else FontWeight.SemiBold, if (on) Txt else SubTxt)
            }
        }
        val cur = g.items.firstOrNull { it.id == S.mode.intValue && it.subs.isNotEmpty() }
        if (cur != null) {
            Spacer(Modifier.height(13.dp))
            Grid(cur.subs.indices.toList(), 3) { i, m ->
                val on = S.sub[cur.id] == i
                Box(
                    m.height(51.dp).clip(RoundedCornerShape(8.dp)).background(if (on) mix(cur.color, 0.2f) else ChipBg)
                        .border(if (on) 2.dp else 1.dp, if (on) cur.color else Bd, RoundedCornerShape(8.dp)).clickable { S.pickSub(cur, i) }.padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) { T(cur.subs[i], 11, if (on) FontWeight.ExtraBold else FontWeight.SemiBold, if (on) Txt else SubTxt, align = TextAlign.Center) }
            }
        }
    }
}

@Composable
fun ColorWheel() {
    val z = S.czone.intValue
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(S.col[z] or (0xFF shl 24), hsv)
    Canvas(
        Modifier.size(168.dp)
            .pointerInput(Unit) { detectTapGestures { o -> wheelPick(o, size.width.toFloat()) } }
            .pointerInput(Unit) { detectDragGestures { ch, _ -> wheelPick(ch.position, size.width.toFloat()) } }
    ) {
        val r = min(size.width, size.height) / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        val hues = (0..6).map { Color(android.graphics.Color.HSVToColor(floatArrayOf((it * 60f) % 360f, 1f, 1f))) }
        drawCircle(Brush.sweepGradient(hues, c), r, c)
        drawCircle(Brush.radialGradient(listOf(Color.White, Color.Transparent), c, r * 0.72f), r, c)
        val a = Math.toRadians(hsv[0].toDouble())
        val k = r * 0.93f * hsv[1]
        val p = Offset(c.x + (cos(a) * k).toFloat(), c.y + (sin(a) * k).toFloat())
        drawCircle(Color(0xFF5A5A5A), 10.dp.toPx(), p)
        drawCircle(Color.White, 8.dp.toPx(), p)
    }
}

private fun wheelPick(o: Offset, w: Float) {
    val r = w / 2f
    val dx = o.x - r
    val dy = o.y - r
    var h = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
    if (h < 0) h += 360f
    val s = (sqrt(dx * dx + dy * dy) / r).coerceIn(0f, 1f)
    S.setColor(android.graphics.Color.HSVToColor(floatArrayOf(h, s, 1f)) and 0xFFFFFF)
}

private val PRESETS = listOf(0xFF2A48, 0xFF7A1A, 0xFFE114, 0x41E01F, 0x17F4EA, 0x4E78FF, 0xFFFFFF, 0x8A00FF)

@Composable
fun Swatches(modifier: Modifier = Modifier) {
    val cur = S.col[S.czone.intValue]
    val cells: List<Int> = PRESETS + S.customs + listOf(-1)      // -1 = ô thêm màu
    Column(modifier, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        cells.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                row.forEach { c ->
                    if (c < 0) {
                        Box(Modifier.size(39.dp).clip(RoundedCornerShape(6.dp)).background(Color.White).border(1.dp, Bd, RoundedCornerShape(6.dp)).clickable { S.addCustom() }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Add, "Thêm màu tùy chỉnh", tint = Txt, modifier = Modifier.size(20.dp))
                        }
                    } else {
                        val on = c == cur
                        Box(
                            Modifier.size(39.dp).clip(RoundedCornerShape(6.dp)).background(rgbColor(c))
                                .border(if (on) 2.dp else 1.dp, if (on) Txt else Bd, RoundedCornerShape(6.dp))
                                .pointerInput(c) { detectTapGestures(onLongPress = { if (!PRESETS.contains(c)) S.removeCustom(c) }, onTap = { S.setColor(c) }) },
                            contentAlignment = Alignment.Center
                        ) { if (on) Icon(Icons.Filled.Check, null, tint = if (c == 0xFFFFFF) Txt else Color.White, modifier = Modifier.size(20.dp)) }
                    }
                }
            }
        }
    }
}

// ===================== TRANG CHÀO MỪNG =====================
private val SEG_NAMES = listOf("Bảng đồng hồ trái", "Bảng đồng hồ phải", "Cửa trước trái", "Cửa trước phải", "Cửa sau trái", "Cửa sau phải")
private val SEG_SHORT = listOf("", "", "Trước trái", "Trước phải", "Sau trái", "Sau phải")
private val STARTS = listOf("Bảng đồng hồ trái" to 0, "Bảng đồng hồ phải" to 1, "Cửa trước trái" to 2, "Cửa sau trái" to 4, "Cửa sau phải" to 5)

@Composable
fun WelcomePage() {
    val ctx = LocalContext.current
    val ver = S.blkVer.intValue      // đọc để vẽ lại khi hộp trả lời
    fun needBlock() = Toast.makeText(ctx, "Chưa đọc được cài đặt chào mừng từ hộp (v$ver). Kiểm tra kết nối rồi thử lại.", Toast.LENGTH_SHORT).show()
    Card {
        SwitchRow("Hiệu ứng chào mừng", "Bật/Tắt", S.blkBit(1, 0x04)) { if (!S.setBlk(1, 0x04, if (it) 0x04 else 0)) { S.refreshBlocks(); needBlock() } }
        Divider()
        // Chỉ bật được khi Hiệu ứng chào mừng đang bật. Tắt = cả 5 hộp chạy cùng lúc, bật = chào lần lượt từng cửa.
        SwitchRow("Chào xung quanh xe", "Hiệu ứng chào xung quanh xe", S.aroundOn.value, enabled = S.blkBit(1, 0x04)) {
            S.aroundOn.value = it
            Toast.makeText(ctx, "Công tắc này chưa gửi lệnh tới hộp (chưa biết mã lệnh).", Toast.LENGTH_SHORT).show()
        }
    }
    Card {
        GroupTitle(Icons.Filled.Tune, "Hiệu ứng chào xung quanh xe", 16)
        Spacer(Modifier.height(12.dp))
        val cur = (S.blk?.get(3) ?: 0) and 0x07
        val modes = listOf("Chế độ 1" to 0, "Chế độ 2" to 1, "Chế độ 3" to 2, "Chế độ 4" to 3, "Chế độ 5" to 4, "Chế độ 6" to 5, "Tùy chỉnh" to 7)
        Grid(modes, 4) { (label, v), m -> Chip(label, S.blk != null && cur == v, m, 48.dp, 12) { if (!S.setBlk(3, 0x07, v)) { S.refreshBlocks(); needBlock() } } }
    }
    Card {
        GroupTitle(Icons.Filled.Speed, "Tốc độ chào mừng liền mạch", 16)
        Spacer(Modifier.height(12.dp))
        Grid(listOf(0, 1, 2, 3), 4) { v, m -> Chip("Tốc độ ${v + 1}", S.wSpeed.intValue == v, m, 48.dp, 12) { S.setWSpeed(v) } }
    }
    Card {
        GroupTitle(Icons.Filled.MeetingRoom, "Sơ đồ xe", 16)
        Spacer(Modifier.height(8.dp))
        T("Chạm một hộp phụ: đèn ở cửa gắn hộp đó sẽ nhấp nháy. Thấy cửa nào nháy thì chạm vào cửa đó trên sơ đồ. Giữ lâu một hộp để bỏ gán.", 12, color = Grey)
        Spacer(Modifier.height(12.dp))
        Grid(listOf(1, 2, 3, 4), 4, 8.dp) { n, m ->
            val on = S.pickBox.intValue == n
            val p = S.boxPos[n - 1]
            Column(
                m.height(56.dp).clip(RoundedCornerShape(8.dp)).background(if (on) Pink else ChipBg).border(1.dp, if (on) Pink else Bd, RoundedCornerShape(8.dp))
                    .pointerInput(n) { detectTapGestures(onLongPress = { S.unassignBox(n) }, onTap = { S.flashBox(n) }) },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
            ) {
                T("Hộp phụ $n", 12, FontWeight.Bold, if (on) Color.White else Txt)
                T(if (on) "Đang nháy" else if (p >= 2) SEG_SHORT[p] else if (p == 0) "Khác" else "Chưa gán", 10, color = if (on) Color.White else Grey)
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(64.dp, 64.dp, 24.dp, 24.dp)).background(Color(0xFFFAFBFE))
                .border(2.dp, Bd, RoundedCornerShape(64.dp, 64.dp, 24.dp, 24.dp)).padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            T("ĐẦU XE", 11, FontWeight.Bold, Grey, Modifier.fillMaxWidth(), TextAlign.Center)
            Grid(listOf(0, 1, 2, 3, 4, 5), 2) { i, m -> SegTile(i, m) }
            T("ĐUÔI XE", 11, FontWeight.Bold, Grey, Modifier.fillMaxWidth(), TextAlign.Center)
        }
        Spacer(Modifier.height(8.dp))
        T("Số LED: chạm − / + để chỉnh từng bóng, giữ nút để chạy nhanh, chạm vào số để gõ thẳng.", 11, color = Grey)
        Spacer(Modifier.height(16.dp))
        T("Vị trí bắt đầu chào mừng", 13, FontWeight.Bold, SubTxt)
        Spacer(Modifier.height(10.dp))
        Grid(STARTS, 2) { (label, code), m -> Chip(label, S.wStart.intValue == code, m, 44.dp, 12) { S.setWStart(code) } }
        Spacer(Modifier.height(16.dp))
        var ask by remember { mutableStateOf(false) }
        Box(Modifier.fillMaxWidth().height(43.dp).clip(RoundedCornerShape(6.dp)).background(Color.White).border(1.dp, Pink, RoundedCornerShape(6.dp)).clickable { ask = true }, contentAlignment = Alignment.Center) {
            T("Khôi phục mặc định", 13, FontWeight.Bold, Pink)
        }
        if (ask) AlertDialog(
            onDismissRequest = { ask = false },
            confirmButton = { TextButton(onClick = { S.resetDoors(); ask = false }) { T("Khôi phục", 14, FontWeight.Bold, Pink) } },
            dismissButton = { TextButton(onClick = { ask = false }) { T("Hủy", 14, FontWeight.Bold) } },
            title = { T("Xác nhận khôi phục mặc định", 17, FontWeight.Bold) },
            text = { T("Bạn có chắc muốn khôi phục cấu hình cửa xe của tất cả hộp phụ về mặc định?", 14) }
        )
    }
}

@Composable
fun OutlineBtn(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.height(44.dp).clip(RoundedCornerShape(8.dp)).background(Color.White).border(1.dp, Bd, RoundedCornerShape(8.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        T(text, 12, FontWeight.SemiBold)
    }
}

@Composable
fun SegTile(i: Int, modifier: Modifier) {
    val door = i >= 2
    val picking = S.pickBox.intValue > 0
    val box = S.boxPos.indexOf(i)
    var edit by remember { mutableStateOf(false) }
    Column(
        modifier.alpha(if (!door && picking) 0.45f else 1f).clip(RoundedCornerShape(10.dp)).background(Color.White)
            .border(if (door && picking) 2.dp else 1.dp, if (door && picking) Pink else Bd, RoundedCornerShape(10.dp))
            .then(if (door && picking) Modifier.clickable { S.assignBox(i) } else Modifier).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 30.dp)) {
            T(SEG_NAMES[i], 12, FontWeight.Bold, modifier = Modifier.weight(1f))
            if (!door) T("Hộp chính", 10, color = Grey)
            else if (picking) Box(Modifier.clip(RoundedCornerShape(9.dp)).border(1.dp, Pink, RoundedCornerShape(9.dp)).padding(horizontal = 7.dp, vertical = 2.dp)) { T("Chọn", 10, FontWeight.Bold, Pink) }
            else if (box >= 0) Box(Modifier.clip(RoundedCornerShape(9.dp)).background(Pink).padding(horizontal = 7.dp, vertical = 2.dp)) { T("Hộp ${box + 1}", 10, FontWeight.Bold, Color.White) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RepeatBtn(Icons.Filled.Remove, "Giảm số LED ${SEG_NAMES[i]}") { S.setLed(i, S.leds[i] - 1) }
            Column(Modifier.weight(1f).clickable { edit = true }, horizontalAlignment = Alignment.CenterHorizontally) {
                T("${S.leds[i]}", 18, FontWeight.ExtraBold, Pink)
                T("LED", 10, color = Grey)
            }
            RepeatBtn(Icons.Filled.Add, "Tăng số LED ${SEG_NAMES[i]}") { S.setLed(i, S.leds[i] + 1) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DirBtn(Icons.Filled.ArrowBack, "${SEG_NAMES[i]}: hướng trái", !S.wDir[i], Modifier.weight(1f)) { S.setWDir(i, false) }
            DirBtn(Icons.Filled.ArrowForward, "${SEG_NAMES[i]}: hướng phải", S.wDir[i], Modifier.weight(1f)) { S.setWDir(i, true) }
        }
    }
    if (edit) {
        var txt by remember { mutableStateOf(S.leds[i].toString()) }
        AlertDialog(
            onDismissRequest = { edit = false },
            confirmButton = { TextButton(onClick = { txt.toIntOrNull()?.let { S.setLed(i, it) }; edit = false }) { T("Xong", 14, FontWeight.Bold, Pink) } },
            dismissButton = { TextButton(onClick = { edit = false }) { T("Hủy", 14, FontWeight.Bold) } },
            title = { T("Số lượng LED: ${SEG_NAMES[i]}", 16, FontWeight.Bold) },
            text = { OutlinedTextField(txt, { v -> txt = v.filter { it.isDigit() }.take(3) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) }
        )
    }
}

@Composable
fun DirBtn(icon: ImageVector, desc: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(44.dp).clip(RoundedCornerShape(10.dp)).background(if (on) Pink else Color(0xFFFFE9F6))
            .border(1.dp, if (on) Pink else Color(0xFFFF9AD5), RoundedCornerShape(10.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, tint = if (on) Color.White else Pink, modifier = Modifier.size(20.dp)) }
}

// ===================== TRANG CÀI ĐẶT =====================
@Composable
fun SettingsPage(onPick: () -> Unit) {
    val ctx = LocalContext.current
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Bluetooth, null, tint = Pink, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            T("Thông tin thiết bị", 16, FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        Kv("Trạng thái kết nối", S.conn.value)
        Kv("Tên thiết bị", if (S.devName.value.isEmpty()) "—" else S.devName.value)
        Kv("ID thiết bị", if (S.savedMac.isEmpty()) "—" else S.savedMac)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FillBtn("Ngắt kết nối", Icons.Filled.BluetoothDisabled, Color(0xFFF44336), Modifier.weight(1f)) { S.ble.disconnect() }
            FillBtn("Chọn lại", Icons.Filled.BluetoothSearching, Color(0xFFFE9700), Modifier.weight(1f), onPick)
        }
    }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Settings, null, tint = Pink, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            T("Tính năng thiết bị", 16, FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        val later = { Toast.makeText(ctx, "Trang này sẽ làm sau.", Toast.LENGTH_SHORT).show() }
        Feature(Icons.Filled.BluetoothSearching, "Ghép nối hộp phụ", "Ghép nối và quản lý thiết bị hộp phụ", later)
        Feature(Icons.Filled.ArrowForward, "Hướng ánh sáng chạy", "Cài đặt hướng ánh sáng chạy của từng khu vực", later)
        Feature(Icons.Filled.Mic, "Giọng nói AI", "Điều khiển đèn viền bằng giọng nói", later)
        Feature(Icons.Outlined.Palette, "Cài đặt giao diện", "Tự động", later)
        Feature(Icons.Filled.Language, "Cài đặt ngôn ngữ", "Thay đổi ngôn ngữ", later)
        Feature(Icons.Filled.Build, "Cài đặt nhà máy", "Cài đặt nâng cao như loại xe, chức năng gốc xe, chẩn đoán", later)
    }
    T("SK Light 0.2.4", 12, color = Grey, modifier = Modifier.fillMaxWidth(), align = TextAlign.Center)
}

@Composable
fun Kv(k: String, v: String) {
    Row(Modifier.heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically) {
        T(k, 13, modifier = Modifier.weight(1f))
        T(v, 13, FontWeight.SemiBold)
    }
}

@Composable
fun FillBtn(text: String, icon: ImageVector, bg: Color, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.height(43.dp).clip(RoundedCornerShape(6.dp)).background(bg).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        T(text, 13, FontWeight.Bold, Color.White)
    }
}

@Composable
fun Feature(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 76.dp).clickable(onClick = onClick).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color(0xFF3F4656), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) { T(title, 16); T(sub, 13, color = SubTxt) }
        Icon(Icons.Filled.ChevronRight, null, tint = Color(0xFF3F4656), modifier = Modifier.size(20.dp))
    }
}

@Composable
fun DevDialog(onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = { S.ble.stopScan(); onClose() },
        confirmButton = { TextButton(onClick = { S.ble.stopScan(); onClose() }) { T("Đóng", 14, FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = { S.ble.scan() }) { T(if (S.scanning.value) "Đang quét thiết bị..." else "Quét lại", 14, FontWeight.Bold, Pink) } },
        title = { T("Chọn hộp đèn", 17, FontWeight.Bold) },
        text = {
            Column {
                if (S.devices.isEmpty()) T(if (S.scanning.value) "Đang tìm hộp CTH2 / CZH2…" else "Không thấy hộp nào. Bật nguồn hộp đèn rồi quét lại.", 13, color = Grey)
                for (d in S.devices.sortedByDescending { it.rssi }) {
                    Row(Modifier.fillMaxWidth().clickable { S.ble.connect(d.mac); onClose() }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { T(d.name, 15, FontWeight.SemiBold); T(d.mac, 11, color = Grey) }
                        T("${d.rssi} dBm", 12, color = Grey)
                    }
                }
            }
        }
    )
}
