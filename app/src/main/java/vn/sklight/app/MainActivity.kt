package vn.sklight.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.asImageBitmap
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
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
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Crash.install(this)
        val report = Crash.take(this)
        if (report != null) { crashShown = true; Crash.show(this, report); return }
        crashShown = false
        S.init(this)
        setContent {
            val scheme = if (Theme.dark) darkColorScheme(primary = Pink, surface = CardBg, surfaceContainerHigh = CardBg, onSurface = Txt, onSurfaceVariant = SubTxt)
            else lightColorScheme(primary = Pink)
            MaterialTheme(colorScheme = scheme) { Root() }
        }
    }

    /** Đang hiện màn hình báo lỗi của lần mở trước: không chạy các phần khác của app. */
    private var crashShown = false

    override fun onStart() { super.onStart(); if (crashShown) return; Gps.start(this); S.syncService(this) }
    override fun onStop() { if (!crashShown) Gps.stop(); super.onStop() }
    override fun onResume() { super.onResume(); if (crashShown) return; S.appVisible = true; S.lastTouch = System.currentTimeMillis(); FloatButton.sync(this) }
    override fun onPause() { if (!crashShown) { S.appVisible = false; Voice.close(); FloatButton.sync(this) }; super.onPause() }
}

@Composable
fun Root() {
    var tab by remember { mutableIntStateOf(0) }
    var pickDev by remember { mutableStateOf(false) }
    val ctx0 = LocalContext.current
    val perms = remember {
        val l = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 31) { l.add(Manifest.permission.BLUETOOTH_SCAN); l.add(Manifest.permission.BLUETOOTH_CONNECT) }
        if (Build.VERSION.SDK_INT >= 33) l.add(Manifest.permission.POST_NOTIFICATIONS)
        l.toTypedArray()
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (S.savedMac.isEmpty()) { S.ble.scan(); pickDev = true } else S.autoConnect()
        Gps.start(ctx0); S.syncService(ctx0)
    }
    LaunchedEffect(Unit) { launcher.launch(perms) }
    // nút Quay lại của máy: thoát màn hình chờ, hoặc ra khỏi trang con
    BackHandler(enabled = S.idleShow.value || S.subPage.intValue != 0) { if (S.idleShow.value) S.idleShow.value = false else S.subPage.intValue = 0 }
    // xe đang chạy và 10 giây không chạm: tự chuyển sang màn hình chờ
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            if (S.idleOn.value && !S.idleShow.value && S.speedKmh.floatValue >= 10f && System.currentTimeMillis() - S.lastTouch > 10000) S.idleShow.value = true
        }
    }

    val cfg = LocalConfiguration.current
    val wide = cfg.screenWidthDp > cfg.screenHeightDp && cfg.screenWidthDp >= 600
    val frame = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Theme.pal.bgTop, Theme.pal.bgBot))).systemBarsPadding()
    val pick: () -> Unit = { S.ble.scan(); pickDev = true }
    Box(Modifier.fillMaxSize().pointerInput(Unit) {
        awaitPointerEventScope { while (true) { awaitPointerEvent(PointerEventPass.Initial); S.lastTouch = System.currentTimeMillis() } }
    }) {
    if (S.idleShow.value) IdleScreen(wide) else
    if (wide) {
        // màn ngang (màn hình ô tô): menu dọc bên trái, nội dung chia hai cột cuộn riêng
        val avail = cfg.screenWidthDp - 24 - 54 - 12
        val leftW = (avail * 0.43f).coerceIn(346f, 392f)
        val cols = if (avail - leftW - 12f >= 470f) 4 else 3
        Row(frame.padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.width(54.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PowerBtn()
                TabsV(tab) { tab = it; S.subPage.intValue = 0 }
                if (L.cur.intValue == 0) MicBtn()
                Spacer(Modifier.weight(1f))
                Image(painterResource(R.drawable.sk_mark), "SK Ambient Light", Modifier.width(48.dp), contentScale = ContentScale.Fit)
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Strip()
                VoicePanel()
                key(tab, S.subPage.intValue) {
                    when (tab) {
                        0 -> TwoCol(leftW.dp, { SceneRow(); LedMain() }, { LedEffects(cols) })
                        1 -> TwoCol(leftW.dp, { WelcomeTop() }, { WelcomeDiagram() })
                        2 -> {
                            val (granted, open) = navAccess()
                            TwoCol(leftW.dp, { NavTop(granted, open) { tab = 1 } }, { NavBottom(granted, open) })
                        }
                        else -> if (S.subPage.intValue >= 5) TwoCol(leftW.dp, { ExtraPage(S.subPage.intValue) }, { })
                        else if (S.subPage.intValue == 4) TwoCol(leftW.dp, { VoicePage() }, { })
                        else if (S.subPage.intValue == 3) TwoCol(leftW.dp, { CustomTestPage() }, { })
                        else if (S.subPage.intValue == 2) TwoCol(leftW.dp, { LanguagePage() }, { })
                        else if (S.subPage.intValue == 1) TwoCol(leftW.dp, { AppearanceTheme() }, { AppearanceIdle() })
                        else TwoCol(leftW.dp, { SettingsDevice(pick); DriveCard() }, { SettingsFeatures() })
                    }
                }
            }
        }
    } else {
        Column(frame) {
            // menu cố định trên cùng, nội dung cuộn bên dưới
            Row(Modifier.padding(start = 13.dp, end = 13.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                PowerBtn()
                Spacer(Modifier.width(8.dp))
                Tabs(tab, Modifier.weight(1f)) { tab = it; S.subPage.intValue = 0 }
                if (L.cur.intValue == 0) { Spacer(Modifier.width(8.dp)); MicBtn() }
            }
            Strip(Modifier.padding(start = 13.dp, end = 13.dp, bottom = 6.dp))
            VoicePanel(Modifier.padding(start = 13.dp, end = 13.dp, bottom = 6.dp))
            Column(
                Modifier.weight(1f).verticalScroll(remember(tab, S.subPage.intValue) { ScrollState(0) }).padding(start = 13.dp, end = 13.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (tab) {
                    0 -> LedPage()
                    1 -> WelcomePage()
                    2 -> NavPage { tab = 1 }
                    else -> if (S.subPage.intValue >= 5) ExtraPage(S.subPage.intValue) else if (S.subPage.intValue == 4) VoicePage() else if (S.subPage.intValue == 3) CustomTestPage() else if (S.subPage.intValue == 2) LanguagePage() else if (S.subPage.intValue == 1) AppearancePage() else SettingsPage(pick)
                }
                LogoFooter()
            }
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
        Modifier.size(46.dp).clip(CircleShape).background(if (on) Pink else CardBg).border(1.dp, if (on) Pink else Bd, CircleShape).clickable { S.setPower(!on) },
        contentAlignment = Alignment.Center
    ) { Icon(Icons.Filled.PowerSettingsNew, if (on) "Tắt đèn" else "Bật đèn", tint = if (on) Color.White else Grey, modifier = Modifier.size(24.dp)) }
}

private val TAB_ITEMS: List<Pair<String, ImageVector>> =
    listOf("Đèn" to Icons.Outlined.Lightbulb, "Chào mừng" to Icons.Filled.PanTool, "Dẫn đường" to Icons.Filled.Navigation, "Cài đặt" to Icons.Filled.Settings)

/** Hai cột cuộn riêng cho màn ngang. */
@Composable
fun ColumnScope.TwoCol(leftW: Dp, left: @Composable () -> Unit, right: @Composable () -> Unit) {
    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.width(leftW).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) { left() }
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) { right() }
    }
}

/** Menu dọc cho màn ngang. */
@Composable
fun TabsV(sel: Int, onPick: (Int) -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(10.dp)).background(CardBg).border(1.dp, Bd, RoundedCornerShape(10.dp)).padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        TAB_ITEMS.forEachIndexed { i, (label, icon) ->
            val on = sel == i
            Box(
                Modifier.size(44.dp, 46.dp).clip(RoundedCornerShape(8.dp)).background(if (on) Pink else Color.Transparent).clickable { onPick(i) },
                contentAlignment = Alignment.Center
            ) { Icon(icon, label, tint = if (on) Color.White else SubTxt, modifier = Modifier.size(22.dp)) }
        }
    }
}

@Composable
fun Tabs(sel: Int, modifier: Modifier = Modifier, onPick: (Int) -> Unit) {
    val items = TAB_ITEMS
    Row(
        modifier.clip(RoundedCornerShape(10.dp)).background(CardBg).border(1.dp, Bd, RoundedCornerShape(10.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEachIndexed { i, (label, icon) ->
            val on = sel == i
            Row(
                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(8.dp)).background(if (on) Pink else Color.Transparent).clickable { onPick(i) },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
            ) {
                Icon(icon, label, tint = if (on) Color.White else SubTxt, modifier = Modifier.size(22.dp))
            }
        }
    }
}

// ===================== TRANG ĐÈN =====================
@Composable
fun LedPage() { SceneRow(); LedMain(); LedEffects(3) }

/** Khu vực + độ sáng + chọn màu. */
@Composable
fun LedMain() {
    Card {
        ZoneTabs(S.zone.intValue) { S.setZoneSel(it) }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.LightMode, null, tint = Txt, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            T("Độ sáng", 16, FontWeight.Bold, modifier = Modifier.weight(1f))
            T("${S.bri[S.zone.intValue]}%", 15, FontWeight.ExtraBold, Pink)
        }
        SkSlider(S.bri[S.zone.intValue] / 100f, { S.setBright((it * 100).roundToInt()) }, Pink, SoftTrack, Color.White,
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
            val opts = listOf("Màu tĩnh", "Màu tự động", "Màu nhấp nháy") + Auto.Z2_NAMES
            Grid(opts.indices.toList(), 3, 0.dp, 0.dp) { i, m ->
                val hw = i < 3
                val on = if (hw) (S.z2.intValue == i && Auto.z2.intValue == 0) else Auto.z2.intValue == i - 2
                Row(m.heightIn(min = 40.dp).clickable {
                    if (hw) {
                        if (Auto.z2.intValue != 0) Auto.stop()
                        if (!S.setZ2(i)) { S.refreshBlocks(); Toast.makeText(S.app, L.tr("Chưa đọc được cài đặt từ hộp. Kiểm tra kết nối rồi thử lại."), Toast.LENGTH_SHORT).show() }
                    } else if (!S.connected.value) Toast.makeText(S.app, L.tr("Chưa kết nối hộp đèn."), Toast.LENGTH_SHORT).show()
                    else Auto.startZ2(i - 2)
                }, verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(18.dp).border(2.dp, if (on) Pink else Grey, CircleShape), contentAlignment = Alignment.Center) {
                        if (on) Box(Modifier.size(8.dp).clip(CircleShape).background(Pink))
                    }
                    Spacer(Modifier.width(6.dp))
                    T(opts[i], 11, if (on) FontWeight.Bold else FontWeight.Medium, if (on) Txt else Grey)
                }
            }
            if (Auto.z2.intValue != 0) { Spacer(Modifier.height(4.dp)); T("Kiểu thêm do app điều khiển, chạy khi app còn mở.", 11, color = Grey) }
        }
    }
}

/** Các nhóm chế độ + thẻ Hiệu ứng. [cols] = số cột của lưới chế độ. */
@Composable
fun LedEffects(cols: Int) {
    for (g in Effects.groups) EffectGroupCard(g, cols)
    Card {
        GroupTitle(Icons.Filled.Tune, "Hiệu ứng", 19)
        Spacer(Modifier.height(14.dp))
        ValueRow(Icons.Filled.Speed, Cyan, "Tốc độ nháy & chạy", S.speed.intValue, Color(0xFFD9FBF9), Cyan, Color(0xFF12C9C0))
        SliderRow("Chậm", "Nhanh") { m ->
            SkSlider((S.speed.intValue - 1) / 9f, { S.setSpeed(1 + (it * 9).roundToInt()) }, Cyan, Color(0xFFD9FBF9), Cyan, m, 8.dp, 21.dp, 10)
        }
        Divider(14.dp, 14.dp)
        ValueRow(Icons.Filled.GraphicEq, Pink, "Độ nhạy nhịp điệu", S.sens.intValue, tint(0.2f), tint(0.6f), AccentText)
        SliderRow("Thấp", "Cao") { m ->
            SkSlider(S.sens.intValue / 7f, { S.setSens((it * 7).roundToInt()) }, Pink, tint(0.2f), Pink, m, 8.dp, 21.dp, 8)
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
fun EffectGroupCard(g: EffectGroup, cols: Int = 3) {
    Card {
        GroupTitle(g.icon, g.name)
        Spacer(Modifier.height(14.dp))
        Grid(g.items, cols, 9.dp, 11.dp) { e, m ->
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
            Grid(cur.subs.indices.toList(), cols) { i, m ->
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
                        Box(Modifier.size(39.dp).clip(RoundedCornerShape(6.dp)).background(CardBg).border(1.dp, Bd, RoundedCornerShape(6.dp)).clickable { S.addCustom() }, contentAlignment = Alignment.Center) {
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
fun WelcomePage() { WelcomeTop(); WelcomeDiagram() }

/** Công tắc chào mừng + chế độ + tốc độ. */
@Composable
fun WelcomeTop() {
    val ctx = LocalContext.current
    val ver = S.blkVer.intValue      // đọc để vẽ lại khi hộp trả lời
    fun needBlock() = Toast.makeText(ctx, L.tr("Chưa đọc được cài đặt chào mừng từ hộp (v$ver). Kiểm tra kết nối rồi thử lại."), Toast.LENGTH_SHORT).show()
    Card {
        SwitchRow("Hiệu ứng chào mừng", "Bật/Tắt", S.blkBit(1, 0x04)) { if (!S.setBlk(1, 0x04, if (it) 0x04 else 0)) { S.refreshBlocks(); needBlock() } }
        Divider()
        // Chỉ bật được khi Hiệu ứng chào mừng đang bật. Tắt = cả 5 hộp chạy cùng lúc, bật = chào lần lượt từng cửa.
        // Bit 0x10 của b4 khối 05 = công tắc "liền mạch" trong app gốc của hộp (chưa kiểm chứng trên hộp thật).
        SwitchRow("Chào xung quanh xe", "Hiệu ứng chào xung quanh xe", S.blkBit(3, 0x10), enabled = S.blkBit(1, 0x04)) {
            if (!S.setBlk(3, 0x10, if (it) 0x10 else 0)) { S.refreshBlocks(); needBlock() }
        }
    }
    Card {
        GroupTitle(Icons.Filled.Tune, "Hiệu ứng chào xung quanh xe", 16)
        Spacer(Modifier.height(12.dp))
        val cur = (S.blk?.get(3) ?: 0) and 0x07
        val modes = listOf("Chế độ 1" to 0, "Chế độ 2" to 1, "Chế độ 3" to 2, "Chế độ 4" to 3, "Chế độ 5" to 4, "Chế độ 6" to 5, "Tùy chỉnh" to 7)
        Grid(modes, 4) { (label, v), m -> Chip(label, S.blk != null && cur == v, m, 48.dp, 12) { if (!S.setBlk(3, 0x07, v)) { S.refreshBlocks(); needBlock() } } }
        if (S.blk != null && cur == 7) WelcomeColors()
    }
    Card {
        GroupTitle(Icons.Filled.Speed, "Tốc độ chào mừng liền mạch", 16)
        Spacer(Modifier.height(12.dp))
        Grid(listOf(0, 1, 2, 3), 4) { v, m -> Chip("Tốc độ ${v + 1}", S.wSpeed.intValue == v, m, 48.dp, 12) { S.setWSpeed(v) } }
    }
}

/** Sơ đồ xe: ghép hộp phụ, số LED, hướng chạy, vị trí bắt đầu. */
@Composable
fun WelcomeDiagram() {
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
                T(if (on) "Đang nháy" else if (p >= 2) SEG_SHORT[p] else if (p == 0) "Khác" else "Theo hộp", 10, color = if (on) Color.White else Grey)
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(64.dp, 64.dp, 24.dp, 24.dp)).background(ChipBg)
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
        Box(Modifier.fillMaxWidth().height(43.dp).clip(RoundedCornerShape(6.dp)).background(CardBg).border(1.dp, Pink, RoundedCornerShape(6.dp)).clickable { ask = true }, contentAlignment = Alignment.Center) {
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
    Box(modifier.height(44.dp).clip(RoundedCornerShape(8.dp)).background(CardBg).border(1.dp, Bd, RoundedCornerShape(8.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
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
        modifier.alpha(if (!door && picking) 0.45f else 1f).clip(RoundedCornerShape(10.dp)).background(CardBg)
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
        modifier.height(44.dp).clip(RoundedCornerShape(10.dp)).background(if (on) Pink else tint(0.12f))
            .border(1.dp, if (on) Pink else tint(0.5f), RoundedCornerShape(10.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, tint = if (on) Color.White else Pink, modifier = Modifier.size(20.dp)) }
}

// ===================== DẢI MÀU XEM TRƯỚC =====================
private val RAINBOW = listOf(Color(0xFFFE2B48), Color(0xFFFED119), Color(0xFF40E01E), Color(0xFF1AF4EA), Color(0xFF4E78FF), Color(0xFF8A01FF))
private val NAME_COLOR = listOf("đỏ" to 0xFE2B48, "vàng" to 0xFFE115, "xanh lá" to 0x40E01E, "xanh lam" to 0x1AF4EA, "xanh dương" to 0x4E78FF, "tím" to 0x8A01FF, "trắng" to 0xFFFFFF)
private val AMBER = Color(0xFFFF9600)
private val ALERT_RED = Color(0xFFFE2B48)
private val DARK = Color(0xFF2A2F3A)

/** Dải màu dưới menu: nửa trái = khu vực 1, nửa phải = khu vực 2; đổi theo màu, chế độ và cảnh báo đang chạy. */
@Composable
fun Strip(modifier: Modifier = Modifier) {
    val uni = S.czone.intValue == 0
    var l: Brush = SolidColor(rgbColor(if (uni) S.col[0] else S.col[1]))
    var r: Brush = SolidColor(rgbColor(if (uni) S.col[0] else S.col[2]))
    val k = S.alertKind.intValue
    val ph = S.alertPhase.value
    if (!S.on.value) { l = SolidColor(Color(0xFFD5D9E3)); r = l }
    else if (k == 3) { l = SolidColor(if (ph) ALERT_RED else DARK); r = l }
    else if (k != 0) {
        val c = SolidColor(if (ph) AMBER else DARK)
        if (AlertBridge.lit(2) || AlertBridge.lit(4)) l = c
        if (AlertBridge.lit(3) || AlertBridge.lit(5)) r = c
    } else if (S.mode.intValue != 0) {
        val e = Effects.byId(S.mode.intValue)
        val name = (if (e != null) e.subs.getOrNull(S.sub.getOrElse(e.id) { 0 }) ?: "" else "").lowercase()
        val hit = NAME_COLOR.firstOrNull { name.endsWith(it.first) }
        l = if (hit != null) SolidColor(rgbColor(hit.second)) else Brush.horizontalGradient(RAINBOW)
        if (S.z2.intValue == 1) r = Brush.horizontalGradient(RAINBOW)
    }
    Row(modifier.fillMaxWidth().height(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(l).border(1.dp, Bd, RoundedCornerShape(4.dp)))
        Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(r).border(1.dp, Bd, RoundedCornerShape(4.dp)))
    }
}

// ===================== TRANG DẪN ĐƯỜNG (BẢN THỬ) =====================
private fun listenerOn(ctx: Context): Boolean =
    (Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: "").contains(ctx.packageName)

private fun boxText(seg: Int): String {
    val b = S.boxPos.indexOf(seg)
    return if (b >= 0) "Hộp phụ ${b + 1}" else "Theo hộp"
}

/** Quyền đọc thông báo đã cấp chưa (tự kiểm tra lại mỗi giây) + hàm mở trang cấp quyền của máy. */
@Composable
fun navAccess(): Pair<Boolean, () -> Unit> {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(listenerOn(ctx)) }
    LaunchedEffect(Unit) { while (true) { granted = listenerOn(ctx); delay(1000) } }
    val open: () -> Unit = {
        try { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (e: Exception) { Toast.makeText(ctx, L.tr("Không mở được trang cấp quyền. Vào Cài đặt máy, tìm \"Truy cập thông báo\"."), Toast.LENGTH_LONG).show() }
    }
    return granted to open
}

@Composable
fun NavPage(toWelcome: () -> Unit) {
    val (granted, open) = navAccess()
    NavTop(granted, open, toWelcome)
    NavBottom(granted, open)
}

/** Trạng thái dẫn đường + cảnh báo rẽ. */
@Composable
fun NavTop(granted: Boolean, openAccess: () -> Unit, toWelcome: () -> Unit) {
    val k = S.alertKind.intValue

    // ---- đang dẫn đường ----
    Card {
        if (!granted) {
            T("Chưa cấp quyền đọc thông báo", 15, FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            T("App cần quyền này để đọc hướng rẽ và khoảng cách từ thông báo của Google Maps, Vietmap.", 12, color = Grey)
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(43.dp).clip(RoundedCornerShape(6.dp)).background(Pink).clickable(onClick = openAccess), contentAlignment = Alignment.Center) {
                T("Cấp quyền đọc thông báo", 13, FontWeight.Bold, Color.White)
            }
        } else if (!S.navOn.value) {
            T("Chưa có chỉ đường", 15, FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            T("Mở Google Maps hoặc Vietmap, bắt đầu dẫn đường rồi quay lại đây.", 12, color = Grey)
        } else {
            val dir = S.navDir.value
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(68.dp).clip(RoundedCornerShape(16.dp)).background(Pink), contentAlignment = Alignment.Center) {
                    Icon(when (dir) { "L" -> Icons.Filled.TurnLeft; "R" -> Icons.Filled.TurnRight; "U" -> Icons.Filled.UTurnLeft; "O" -> Icons.Filled.RoundaboutRight; "A" -> Icons.Filled.Flag; else -> Icons.Filled.Straight },
                        null, tint = Color.White, modifier = Modifier.size(40.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    T("Đang dẫn đường · ${S.navApp.value}", 12, FontWeight.Bold, Color(0xFF2E8B3D))
                    val d = S.navDist.intValue
                    T(if (d < 0) "—" else if (d >= 1000) "${d / 100 / 10.0} km" else "$d m", 30, FontWeight.ExtraBold)
                    val dt = L.tr(when (dir) { "L" -> "Rẽ trái"; "R" -> "Rẽ phải"; "U" -> "Quay đầu"; "O" -> "Vòng xuyến"; "A" -> "Đến nơi"; "S" -> "Đi thẳng"; else -> "Chưa rõ hướng" })
                    T(if (S.navStreet.value.isEmpty()) dt else "$dt · ${S.navStreet.value}", 15, FontWeight.SemiBold)
                }
            }
        }
        if (k != 0) {
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(tint(0.12f)).border(1.dp, tint(0.5f), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 9.dp)) {
                T(when (k) { 1 -> "Sắp rẽ trái: các cửa bên trái đang nháy"; 2 -> "Sắp rẽ phải: các cửa bên phải đang nháy"; 3 -> "Quá tốc độ: đang nháy đỏ"; else -> "Đang thử: ${SEG_NAMES[k - 10]}" }, 13, FontWeight.Bold)
            }
        }
    }

    // ---- cảnh báo rẽ ----
    Card {
        GroupTitle(Icons.Filled.NotificationsNone, "Cảnh báo rẽ", 16)
        Spacer(Modifier.height(8.dp))
        SwitchRow("Nháy đèn khi sắp rẽ", "Rẽ bên nào thì các cửa bên đó nháy", S.warnOn.value) { S.warnOn.value = it; S.saveSettings(); S.syncService() }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            T("Báo trước", 14, FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Box(Modifier.height(27.dp).widthIn(min = 58.dp).clip(RoundedCornerShape(6.dp)).background(tint(0.2f)).border(1.dp, tint(0.6f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                T("${S.alertDist.intValue} m", 14, FontWeight.ExtraBold, Pink)
            }
        }
        SkSlider((S.alertDist.intValue - 50) / 450f, { S.alertDist.intValue = 50 + (it * 45).roundToInt() * 10; S.saveSettings() }, Pink, tint(0.2f), Pink,
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp), 8.dp, 21.dp)
        Spacer(Modifier.height(14.dp))
        Column(Modifier.fillMaxWidth().border(1.dp, Bd, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row { T("Bên trái", 13, FontWeight.Bold, modifier = Modifier.width(78.dp)); T("Cửa trước trái (${boxText(2)}), cửa sau trái (${boxText(4)})", 13, color = SubTxt) }
            Row { T("Bên phải", 13, FontWeight.Bold, modifier = Modifier.width(78.dp)); T("Cửa trước phải (${boxText(3)}), cửa sau phải (${boxText(5)})", 13, color = SubTxt) }
        }
        Box(Modifier.padding(top = 10.dp).heightIn(min = 44.dp).clickable(onClick = toWelcome), contentAlignment = Alignment.CenterStart) {
            T("Gán hộp phụ vào cửa ở sơ đồ xe", 13, FontWeight.Bold, Pink)
        }
    }

}

/** Thử nháy + thông báo của app dẫn đường. */
@Composable
fun NavBottom(granted: Boolean, openAccess: () -> Unit) {
    val ctx = LocalContext.current
    val k = S.alertKind.intValue

    // ---- thử nháy ----
    Card {
        GroupTitle(Icons.Filled.AutoAwesome, "Thử nháy 4 giây", 16)
        Spacer(Modifier.height(12.dp))
        Grid(listOf("Rẽ trái" to 1, "Rẽ phải" to 2, "Quá tốc độ" to 3), 3) { (label, v), m -> Chip(label, k == v, m, 44.dp, 12) { AlertBridge.test(v) } }
        Spacer(Modifier.height(12.dp))
        Grid(listOf(2, 3, 4, 5), 2, 8.dp) { seg, m ->
            val on = AlertBridge.lit(seg) && S.alertPhase.value
            val red = k == 3
            Column(
                m.heightIn(min = 46.dp).clip(RoundedCornerShape(8.dp)).background(if (on) (if (red) ALERT_RED else AMBER) else ChipBg)
                    .border(1.dp, Bd, RoundedCornerShape(8.dp)).clickable { AlertBridge.test(10 + seg) }.padding(vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
            ) {
                T(SEG_NAMES[seg], 12, FontWeight.Bold, if (on && red) Color.White else Txt)
                T(boxText(seg), 10, color = if (on && red) Color.White else if (on) Txt else Grey)
            }
        }
        Spacer(Modifier.height(8.dp))
        T("Chạm một cửa để thử riêng cửa đó. Rẽ trái, rẽ phải và từng cửa dùng lệnh nháy của hộp phụ; quá tốc độ thì cả xe nháy đỏ.", 11, color = Grey)
    }

    // ---- cảnh báo quá tốc độ ----
    Card {
        GroupTitle(Icons.Filled.NotificationsNone, "Cảnh báo quá tốc độ", 16)
        Spacer(Modifier.height(8.dp))
        SwitchRow("Nháy đỏ khi quá tốc độ", "Nháy đỏ liên tục khi chạy quá giới hạn trên biển báo", S.speedWarn.value) { S.speedWarn.value = it; S.saveSettings(); S.syncService() }
        Spacer(Modifier.height(6.dp))
        T("Khu nháy đỏ khi quá tốc độ (các khu còn lại giữ nguyên màu):", 12, color = Grey)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((label, z) in listOf("Khu vực 1" to 1, "Khu vực 2" to 2, "Cả xe" to 0))
                Chip(label, S.speedZone.intValue == z, Modifier.weight(1f), 44.dp, 12) { S.speedZone.intValue = z; S.saveSettings() }
        }
        Spacer(Modifier.height(6.dp))
        Kv("Tốc độ xe", if (S.appSpeed.intValue >= 0) "${S.appSpeed.intValue} km/h (từ app dẫn đường)" else "${Math.round(S.speedKmh.floatValue)} km/h (GPS)")
        Kv("Giới hạn đọc được", if (S.speedLimit.intValue > 0) "${S.speedLimit.intValue} km/h" else "Chưa có")
        if (S.ocrNote.value.isNotEmpty()) T(S.ocrNote.value, 11, color = Grey)
        S.signThumb.value?.let { bmp ->
            Spacer(Modifier.height(6.dp))
            T("Ảnh biển app đang đọc:", 11, color = Grey)
            androidx.compose.foundation.Image(bitmap = bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.height(90.dp))
        }
        Spacer(Modifier.height(6.dp))
        T("Giới hạn tốc độ hiện có khi dùng app dẫn đường không có thông báo (như Gofa) và đã bật \"Tự đọc\" ở phần Đọc chữ trên màn hình bên dưới.", 11, color = Grey)
    }

    // ---- thông báo app dẫn đường ----
    Card {
        GroupTitle(Icons.Filled.Navigation, "Thông báo của app dẫn đường", 16)
        Spacer(Modifier.height(8.dp))
        T("Phần kiểm tra: app ghi lại nguyên văn thông báo của Google Maps, Vietmap để biết đọc được những gì (hướng rẽ, khoảng cách, biển báo tốc độ).", 12, color = Grey)
        Spacer(Modifier.height(8.dp))
        Kv("Quyền đọc thông báo", if (granted) "Đã cấp" else "Chưa cấp")
        Kv("Biển báo tốc độ thấy được", if (S.signSeen.value.isEmpty()) "Chưa thấy" else S.signSeen.value)
        Spacer(Modifier.height(8.dp))
        if (S.navLog.isEmpty()) T("Chưa ghi được thông báo nào.", 12, color = Grey)
        for (line in S.navLog) {
            Box(Modifier.padding(bottom = 6.dp).fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(ChipBg).padding(horizontal = 9.dp, vertical = 7.dp)) { T(line, 11, color = SubTxt) }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlineBtn("Mở cài đặt quyền", Modifier.weight(1f), openAccess)
            OutlineBtn("Sao chép để gửi", Modifier.weight(1f)) {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("SK Light", "SK Light 0.5.4, thông báo app dẫn đường:\n" + S.navLog.joinToString("\n")))
                Toast.makeText(ctx, L.tr("Đã sao chép ${S.navLog.size} dòng."), Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ---- đọc chữ trên màn hình (app không có thông báo) ----
    Card {
        GroupTitle(Icons.Filled.Navigation, "Đọc chữ trên màn hình (thử)", 16)
        Spacer(Modifier.height(8.dp))
        T("Dành cho app dẫn đường không có thông báo như Gofa. Bật quyền, bấm Bắt đầu, mở app dẫn đường vài giây rồi quay lại đây. Chữ chỉ đọc trên máy, không gửi đi đâu, tự tắt sau 5 phút.", 12, color = Grey)
        Spacer(Modifier.height(8.dp))
        var accOn by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { while (true) { accOn = (Settings.Secure.getString(ctx.contentResolver, "enabled_accessibility_services") ?: "").contains(ctx.packageName); delay(1000) } }
        Kv("Quyền Hỗ trợ tiếp cận", if (accOn) "Đã bật" else "Chưa bật")
        SwitchRow("Tự đọc khi dùng Gofa và app bản đồ", "Chỉ đọc màn hình của app bản đồ, các app khác bỏ qua", S.screenAuto.value) { S.screenAuto.value = it; S.saveSettings() }
        Kv("Đang đọc thử", if (S.screenOn.value) "Có" else "Không")
        Kv("Đọc được", if (S.screenInfo.value.isEmpty()) "Chưa có" else S.screenInfo.value)
        Kv("App đọc gần nhất", if (S.screenPkg.value.isEmpty()) "Chưa có" else S.screenPkg.value)
        Spacer(Modifier.height(8.dp))
        if (S.screenLines.isEmpty()) T("Chưa đọc được chữ nào.", 12, color = Grey)
        for (line in S.screenLines.take(80)) {
            Box(Modifier.padding(bottom = 4.dp).fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(ChipBg).padding(horizontal = 9.dp, vertical = 5.dp)) { T(line, 11, color = SubTxt) }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlineBtn("Mở cài đặt quyền", Modifier.weight(1f)) {
                try { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {}
            }
            OutlineBtn(if (S.screenOn.value) "Dừng đọc" else "Bắt đầu đọc", Modifier.weight(1f)) {
                if (S.screenOn.value) S.screenOn.value = false
                else { S.screenUntil = System.currentTimeMillis() + 300_000; S.screenLines.clear(); S.screenOn.value = true }
            }
        }
        Spacer(Modifier.height(6.dp))
        OutlineBtn("Sao chép để gửi", Modifier.fillMaxWidth()) {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("SK Light", "SK Light 0.5.12, chữ trên màn hình (${S.screenPkg.value}):\n" + S.screenLines.joinToString("\n")))
            Toast.makeText(ctx, L.tr("Đã sao chép ${S.screenLines.size} dòng."), Toast.LENGTH_SHORT).show()
        }
    }
}

// ===================== TRANG CÀI ĐẶT =====================
@Composable
fun SettingsPage(onPick: () -> Unit) { SettingsDevice(onPick); DriveCard(); SettingsFeatures() }

@Composable
fun SettingsDevice(onPick: () -> Unit) {
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
            FillBtn("Ngắt kết nối", Icons.Filled.BluetoothDisabled, Color(0xFFF44336), Modifier.weight(1f)) { S.userOff = true; S.ble.disconnect() }
            FillBtn("Chọn lại", Icons.Filled.BluetoothSearching, Color(0xFFFE9700), Modifier.weight(1f), onPick)
        }
    }
}

@Composable
fun SettingsFeatures() {
    val ctx = LocalContext.current
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Settings, null, tint = Pink, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            T("Tính năng thiết bị", 16, FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Feature(Icons.Filled.BluetoothSearching, "Ghép nối hộp phụ", "Ghép nối và quản lý thiết bị hộp phụ") { S.subPage.intValue = 5 }
        Feature(Icons.Filled.ArrowForward, "Hướng ánh sáng chạy", "Cài đặt hướng ánh sáng chạy của từng khu vực") { S.subPage.intValue = 6 }
        Feature(Icons.Filled.Mic, "Giọng nói AI", "Điều khiển đèn viền bằng giọng nói") { S.subPage.intValue = 4 }
        Feature(Icons.Outlined.Palette, "Cài đặt giao diện", "${Theme.MODES[Theme.mode.intValue]} · ${Theme.ACCENTS[Theme.accent.intValue].first} · màn hình chờ") { S.subPage.intValue = 1 }
        Feature(Icons.Filled.Language, "Cài đặt ngôn ngữ", L.NAMES[L.cur.intValue]) { S.subPage.intValue = 2 }
        Feature(Icons.Filled.Science, "Thử chế độ tự tạo", "Xem hộp đèn chuyển màu liên tục có mượt không (bản thử)") { S.subPage.intValue = 3 }
        Feature(Icons.Filled.Build, "Cài đặt nhà máy", "Cài đặt nâng cao như chức năng gốc xe, phiên bản hộp, chẩn đoán") { S.subPage.intValue = 8 }
        Feature(Icons.Filled.MenuBook, "Cẩm nang sử dụng", "Hướng dẫn sử dụng và hướng dẫn cài đặt chi tiết") {
            Toast.makeText(ctx, L.tr("Nội dung cẩm nang sẽ có khi app hoàn thiện."), Toast.LENGTH_SHORT).show()
        }
    }
    T("SK Light 0.5.21 (bản thử)", 12, color = Grey, modifier = Modifier.fillMaxWidth(), align = TextAlign.Center)
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
        Icon(icon, null, tint = SubTxt, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) { T(title, 16); T(sub, 13, color = SubTxt) }
        Icon(Icons.Filled.ChevronRight, null, tint = SubTxt, modifier = Modifier.size(20.dp))
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
