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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
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
    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig); Split.inSplit.value = isInMultiWindowMode
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); Notice.winFocus = hasFocus }
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
            // Nhiều màn hình ô tô đặt cỡ chữ hệ thống rất lớn (1,3–1,6 lần) làm chữ tràn khỏi ô. App có mục "Cỡ chữ" riêng nên chỉ nhận tối đa 1,15 lần từ máy.
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, minOf(d.fontScale, 1.15f))) {
                MaterialTheme(colorScheme = scheme) { Root() }
            }
        }
    }

    /** Đang hiện màn hình báo lỗi của lần mở trước: không chạy các phần khác của app. */
    private var crashShown = false

    override fun onStart() { super.onStart(); if (crashShown) return; Gps.start(this); S.autoConnect(); S.syncService(this) }
    override fun onStop() { if (!crashShown) { Gps.stop(); S.syncService(this) }; super.onStop() }
    override fun onResume() { super.onResume(); if (crashShown) return; Split.inSplit.value = isInMultiWindowMode; S.appVisible = true; S.lastTouch = System.currentTimeMillis(); FloatButton.sync(this)
        // vừa bấm mic ở màn hình nổi: nghe ngay khi app mở lên
        if (S.pendingMic) { S.pendingMic = false; if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) Voice.start(this) }
    }
    override fun onPause() { if (!crashShown) { S.appVisible = false; Voice.close(); FloatButton.sync(this) }; super.onPause() }
}

@Composable
fun Root() {
    var tab by remember { mutableIntStateOf(0) }
    InfoBtn.on = tab == 1
    var pickDev by remember { mutableStateOf(false) }
    val ctx0 = LocalContext.current
    val perms = remember {
        val l = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 31) { l.add(Manifest.permission.BLUETOOTH_SCAN); l.add(Manifest.permission.BLUETOOTH_CONNECT) }
        if (Build.VERSION.SDK_INT >= 33) l.add(Manifest.permission.POST_NOTIFICATIONS)
        l.toTypedArray()
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        // lần đầu mở app: xin luôn quyền đọc thông báo + Hỗ trợ tiếp cận (phải bật trong Cài đặt máy), xong mới chọn hộp đèn
        val needSetup = !Setup.asked(ctx0) && !(Setup.notifOn(ctx0) && Setup.accOn(ctx0))
        if (S.savedMac.isEmpty()) { if (needSetup) Setup.pickAfter = true else { S.ble.scan(); pickDev = true } } else S.autoConnect()
        if (needSetup) Setup.show.value = true
        Gps.start(ctx0); S.syncService(ctx0)
    }
    LaunchedEffect(Unit) { launcher.launch(perms) }
    LaunchedEffect(Unit) { delay(4000); Updater.auto(ctx0) }
    // nút Quay lại của máy: thoát màn hình chờ, hoặc ra khỏi trang con
    BackHandler(enabled = S.idleShow.value || S.subPage.intValue != 0) { if (S.idleShow.value) S.idleShow.value = false else S.subPage.intValue = 0 }
    // xe đang chạy và 10 giây không chạm: tự chuyển sang màn hình chờ
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            if (S.idleOn.value && !S.idleShow.value && maxOf(S.speedKmh.floatValue, S.appSpeed.intValue.toFloat()) >= 10f && System.currentTimeMillis() - S.lastTouch > S.idleDelay.intValue * 1000L) S.idleShow.value = true
        }
    }

    val cfg = LocalConfiguration.current
    val wide = cfg.screenWidthDp > cfg.screenHeightDp && cfg.screenWidthDp >= 600
    val frame = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Theme.pal.bgTop, Theme.pal.bgBot))).systemBarsPadding()
    val pick: () -> Unit = { S.ble.scan(); pickDev = true }
    LaunchedEffect(S.pickNow.value) { if (S.pickNow.value) { S.pickNow.value = false; pick() } }
    // đã nối hộp đèn mà máy chưa kích hoạt: ba trang điều khiển nhường chỗ cho trang nhập mã, Cài đặt vẫn mở được
    val lock = S.connected.value && !Act.ok.value
    // chưa nối hộp đèn: chỉ còn Cài đặt cơ bản, để người ngoài không xem được tính năng của app
    val gated = !S.connected.value || !Act.ok.value          // chưa nối hộp hoặc chưa nhập mã kích hoạt
    val unplugged = !S.connected.value
    LaunchedEffect(S.subPage.intValue) { if (S.subPage.intValue == 13 && tab < 3) tab = 3 }
    LaunchedEffect(unplugged) { if (unplugged && S.dev.value) S.dev.value = false }   // chưa nối hộp thì tắt chế độ nghiên cứu
    LaunchedEffect(gated, S.subPage.intValue) { if (gated && S.subPage.intValue !in intArrayOf(0, 1, 2, 12, 13) && !(S.dev.value && S.connected.value && S.subPage.intValue == 11)) S.subPage.intValue = 0 }
    CompositionLocalProvider(LocalCompact provides wide) {
    Box(Modifier.fillMaxSize().pointerInput(Unit) {
        awaitPointerEventScope { while (true) { awaitPointerEvent(PointerEventPass.Initial); S.lastTouch = System.currentTimeMillis() } }
    }) {
    if (S.idleShow.value) IdleScreen(wide) else
    if (wide) {
        // màn ngang (màn hình ô tô): nội dung chia hai cột cuộn riêng; menu nằm trên (mặc định) hoặc thành cột bên trái (chọn ở Cài đặt giao diện)
        val topMenu = S.menuTop.value
        val low = cfg.screenHeightDp < 420              // màn xe thấp: hàng menu trên nhỏ lại cho đỡ tốn chỗ
        val roomy = cfg.screenHeightDp >= 520
        val mid = !roomy && cfg.screenHeightDp >= 430
        val sideW = if (topMenu) 0 else if (roomy) 70 else if (mid) 64 else 54
        val avail = cfg.screenWidthDp - 24 - (if (topMenu) 0 else sideW + 12)
        val leftW = (avail * 0.43f).coerceIn(346f, 392f)
        val cols = if (avail - leftW - 12f >= 470f) 4 else 3
        val body: @Composable ColumnScope.() -> Unit = {
            UpdateBanner()
            if (!gated) Strip()
            if (!gated) VoicePanel()
            key(tab, S.subPage.intValue, lock) {
              androidx.compose.runtime.CompositionLocalProvider(LocalScrollKey provides (if (S.subPage.intValue == 0) "t$tab" else null)) {
                if (unplugged && tab < 3) TwoCol(leftW.dp, { PlugCard() }, { }) else if (lock && tab < 3) WideOne { ActivationPage(true) } else when (tab) {
                    0 -> TwoCol(leftW.dp, { PlugCard(); SceneRow(); LedMain(); EffectTuneCard() }, { LedEffects(cols, false) })
                    1 -> TwoCol(leftW.dp, { WelcomeTop() }, { WelcomeDiagram() })
                    2 -> {
                        val (granted, open) = navAccess()
                        TwoCol(leftW.dp, { NavTop(granted, open) { tab = 1 } }, { NavBottom(granted, open) })
                    }
                    else -> if (S.subPage.intValue == 13) WideOne { ExtraPage(13) }
                    else if (S.subPage.intValue >= 5) WideSub { ExtraPage(S.subPage.intValue) }
                    else if (S.subPage.intValue == 4) WideSub { VoicePage() }
                    else if (S.subPage.intValue == 3) WideSub { CustomTestPage() }
                    else if (S.subPage.intValue == 2) WideSub { LanguagePage() }
                    else if (S.subPage.intValue == 1) TwoCol(leftW.dp, { AppearanceTheme() }, { AppearanceIdle() })
                    else TwoCol(leftW.dp, { SettingsDevice(pick); if (S.connected.value) WarrantyCard(); if (!gated) DriveCard() }, { SettingsFeatures() })
                }
              }
            }
        }
        if (topMenu) Column(frame.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Menu trên: logo, nguồn, bốn trang (có tên), nút mở màn hình chờ, nút mic to ở góc phải
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Image(painterResource(R.drawable.sk_mark), "SK Ambient Light", Modifier.width(if (low) 34.dp else 42.dp), contentScale = ContentScale.Fit)
                PowerBtn(if (low) 42 else 52)
                Tabs(tab, Modifier.weight(1f), if (low) 38 else 46, true) { tab = it; S.subPage.intValue = 0 }
                SplitBtn(if (low) 42 else 52)
                if (!gated) IdleBtn(if (low) 42 else 52)
                if (L.cur.intValue == 0 && !gated) MicBtn(if (low) 46 else 60)
            }
            body()
        } else Row(frame.padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Menu trái: nguồn ở trên cùng, bốn trang, nút mở màn hình chờ; nút mic to ở góc dưới, logo nhỏ dưới cùng
            Column(Modifier.width(sideW.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PowerBtn(if (roomy) 56 else 46)
                TabsV(tab, roomy) { tab = it; S.subPage.intValue = 0 }
                if (roomy || mid) { SplitBtn(if (roomy) 56 else 46); if (!gated) IdleBtn(if (roomy) 56 else 46) }
                Spacer(Modifier.weight(1f))
                if (L.cur.intValue == 0 && !gated) MicBtn(if (roomy) 66 else if (mid) 60 else 46)
                Image(painterResource(R.drawable.sk_mark), "SK Ambient Light", Modifier.width(if (roomy || mid) 40.dp else 48.dp), contentScale = ContentScale.Fit)
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) { body() }
        }
    } else {
        Column(frame) {
            // menu cố định trên cùng, nội dung cuộn bên dưới
            Box {
            Row(Modifier.padding(start = 13.dp, end = 13.dp, top = 3.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                PowerBtn(36)
                Spacer(Modifier.width(6.dp))
                Tabs(tab, Modifier.weight(1f), 30) { tab = it; S.subPage.intValue = 0 }
                if (L.cur.intValue == 0 && !gated) { Spacer(Modifier.width(6.dp)); MicBtn(36) }
            }
            // chấm nhỏ ở góc trên phải: xanh = đã kết nối Bluetooth với hộp đèn, xám = chưa
            Box(Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 4.dp).size(8.dp).clip(CircleShape).background(if (S.connected.value) OkGreen else Color(0xFFB8BCC6)))
            }
            UpdateBanner(Modifier.padding(start = 13.dp, end = 13.dp, bottom = 4.dp))
            if (!gated) Strip(Modifier.padding(start = 13.dp, end = 13.dp, bottom = 6.dp))
            if (!gated) VoicePanel(Modifier.padding(start = 13.dp, end = 13.dp, bottom = 6.dp))
            Column(
                Modifier.weight(1f).verticalScroll(if (S.subPage.intValue == 0) ScrollMem.get("p$tab") else remember(tab, S.subPage.intValue) { ScrollState(0) }).padding(start = 13.dp, end = 13.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (unplugged && tab < 3) PlugCard() else if (lock && tab < 3) ActivationPage(true) else when (tab) {
                    0 -> LedPage()
                    1 -> WelcomePage()
                    2 -> NavPage { tab = 1 }
                    else -> if (S.subPage.intValue >= 5) ExtraPage(S.subPage.intValue) else if (S.subPage.intValue == 4) VoicePage() else if (S.subPage.intValue == 3) CustomTestPage() else if (S.subPage.intValue == 2) LanguagePage() else if (S.subPage.intValue == 1) AppearancePage() else SettingsPage(pick)
                }
                LogoFooter()
            }
        }
    }
    SplitPicker()
    NoticeHost(Modifier.align(Alignment.BottomCenter))
    }
    }
    SetupSheet(Setup.show.value) { Setup.show.value = false; if (Setup.pickAfter) { Setup.pickAfter = false; S.ble.scan(); pickDev = true } }
    if (pickDev) DevDialog { pickDev = false }
    UpdateDialog()
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

val TAB_ITEMS: List<Pair<String, ImageVector>> =
    listOf("Đèn" to Icons.Outlined.Lightbulb, "Chào mừng" to Icons.Filled.PanTool, "Dẫn đường" to Icons.Filled.Navigation, "Cài đặt" to Icons.Filled.Settings)

/** Trang cài đặt con ở màn ngang: dùng hết chiều ngang, các thẻ tự xếp thành hai cột. */
/** Một trang đơn (nhập mã, thông tin bảo hành): một cột, căn giữa, không chia hai nửa như WideSub. */
@Composable
fun ColumnScope.WideOne(content: @Composable () -> Unit) {
    Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 4.dp, end = 4.dp, bottom = 10.dp), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()) { content() }
    }
}

@Composable
fun ColumnScope.WideSub(content: @Composable () -> Unit) {
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 4.dp, end = 4.dp, bottom = 10.dp)) { Masonry(12.dp, content) }
}

/** Nhớ vị trí cuộn của các trang chính để quay lại từ trang con không bị về đầu trang. */
object ScrollMem {
    private val m = HashMap<String, ScrollState>()
    fun get(k: String): ScrollState = m.getOrPut(k) { ScrollState(0) }
}
val LocalScrollKey = androidx.compose.runtime.compositionLocalOf<String?> { null }

/** Hai cột cuộn riêng cho màn ngang. */
@Composable
fun ColumnScope.TwoCol(leftW: Dp, left: @Composable () -> Unit, right: @Composable () -> Unit) {
    // lề 4dp quanh mỗi cột để bóng đổ của thẻ không bị cắt ở mép vùng cuộn; khoảng cách giữa hai cột vẫn là 12dp
    val sk = LocalScrollKey.current
    val sl = if (sk == null) rememberScrollState() else ScrollMem.get("$sk-l")
    val sr = if (sk == null) rememberScrollState() else ScrollMem.get("$sk-r")
    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(Modifier.width(leftW + 8.dp).fillMaxHeight().verticalScroll(sl).padding(start = 4.dp, end = 4.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { left() }
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(sr).padding(start = 4.dp, end = 4.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { right() }
    }
}

// ===================== TRANG ĐÈN =====================
@Composable
fun LedPage() { PlugCard(); SceneRow(); LedMain(); LedEffects(3) }

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
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 10.dp), label = "${S.bri[S.zone.intValue]}%")
        Divider(16.dp, 14.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Palette, null, tint = Pink, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            T("Chọn màu", 17, FontWeight.Bold, modifier = Modifier.weight(1f))
            Box(Modifier.size(35.dp, 22.dp).clip(RoundedCornerShape(Theme.r(6))).background(rgbColor(S.col[S.czone.intValue])).border(1.dp, Bd, RoundedCornerShape(Theme.r(6))))
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
        Column(Modifier.fillMaxWidth().border(1.dp, Bd, RoundedCornerShape(Theme.r(8))).padding(horizontal = 12.dp, vertical = 11.dp)) {
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
                        Auto.clearZ2()
                        if (!S.setZ2(i)) { S.refreshBlocks(); Notice.show(S.app, L.tr("Chưa đọc được cài đặt từ hộp. Kiểm tra kết nối rồi thử lại.")) }
                    } else if (!S.connected.value) Notice.show(S.app, L.tr("Chưa kết nối hộp đèn."))
                    else Auto.startZ2(i - 2)
                }, verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(18.dp).border(2.dp, if (on) Pink else Grey, CircleShape), contentAlignment = Alignment.Center) {
                        if (on) Box(Modifier.size(8.dp).clip(CircleShape).background(Pink))
                    }
                    Spacer(Modifier.width(6.dp))
                    T(opts[i], 11, if (on) FontWeight.Bold else FontWeight.Medium, if (on) Txt else Grey)
                }
            }
            if (Auto.z2.intValue != 0) { Spacer(Modifier.height(4.dp)); Hint("Kiểu thêm do app điều khiển: được nhớ khi đổi chế độ, đổi màu; chạy khi app còn mở và đang kết nối.", 11) }
        }
    }
}

/** Các nhóm chế độ + thẻ Hiệu ứng. [cols] = số cột của lưới chế độ. */
@Composable
fun LedEffects(cols: Int, tune: Boolean = true) {       // tune = false: màn ngang, thẻ Tốc độ / Độ nhạy đã nằm cố định ở cột trái
    // Màn dọc: thẻ Tốc độ / Độ nhạy luôn nằm DƯỚI CÙNG trang Đèn như app gốc (chủ dự án yêu cầu 23/11; bỏ kiểu đi theo nhóm chế độ của 0.5.53).
    PinnedCard(cols)
    Effects.groups.forEachIndexed { i, g -> EffectGroupCard(g, cols, i == 0 && S.pins.isEmpty()) }
    MyModesCard(cols)
    if (tune) EffectTuneCard()
}

/** Tốc độ nháy & chạy + độ nhạy nhịp điệu. */
@Composable
fun EffectTuneCard() {
    Card {
        GroupTitle(Icons.Filled.Tune, "Hiệu ứng", 19)
        Spacer(Modifier.height(14.dp))
        ValueRow(Icons.Filled.Speed, Cyan, "Tốc độ nháy & chạy", S.speed.intValue, Color(0xFFD9FBF9), Cyan, Color(0xFF12C9C0))
        SliderRow("Chậm", "Nhanh") { m ->
            SkSlider((S.speed.intValue - 1) / 9f, { S.setSpeed(1 + (it * 9).roundToInt()) }, Cyan, Color(0xFFD9FBF9), Cyan, m, 8.dp, 21.dp, 10, "${S.speed.intValue}")
        }
        Divider(14.dp, 14.dp)
        ValueRow(Icons.Filled.GraphicEq, Pink, "Độ nhạy nhịp điệu", S.sens.intValue, tint(0.2f), tint(0.6f), AccentText)
        SliderRow("Thấp", "Cao") { m ->
            SkSlider(S.sens.intValue / 7f, { S.setSens((it * 7).roundToInt()) }, Pink, tint(0.2f), Pink, m, 8.dp, 21.dp, 8, "${S.sens.intValue}")
        }
    }
}

@Composable
fun ValueRow(icon: ImageVector, iconTint: Color, title: String, value: Int, boxBg: Color, boxBd: Color, numColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        T(title, 15, FontWeight.Bold, modifier = Modifier.weight(1f))
        Box(Modifier.size(36.dp, 27.dp).clip(RoundedCornerShape(Theme.r(6))).background(boxBg).border(1.dp, boxBd, RoundedCornerShape(Theme.r(6))), contentAlignment = Alignment.Center) {
            T("$value", 14, FontWeight.ExtraBold, numColor)
        }
    }
}

@Composable
fun SliderRow(left: String, right: String, slider: @Composable (Modifier) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp)) {
        T(left, 11, color = Grey, modifier = Modifier.widthIn(min = 40.dp).padding(end = 4.dp))
        slider(Modifier.weight(1f))
        T(right, 11, color = Grey, modifier = Modifier.widthIn(min = 40.dp).padding(start = 4.dp), align = TextAlign.End)
    }
}

/** Một ô chế độ. Chạm = chạy; chạm giữ = ghim lên đầu trang / bỏ ghim. */
@Composable
fun EffectTile(e: Effect, m: Modifier) {
    val ctx = LocalContext.current
    val on = S.mode.intValue == e.id
    val sh = RoundedCornerShape(Theme.r(8))
    Row(
        m.height(56.dp).glow(on, e.color, sh).clip(sh).background(if (on) mix(e.color, 0.5f) else ChipBg)
            .border(if (on) 2.dp else 1.dp, if (on) e.color else Bd, sh)
            .pointerInput(e.id) {
                detectTapGestures(
                    onLongPress = {
                        when (S.togglePin(e.id)) {
                            1 -> Notice.show(ctx, L.tr("Đã ghim lên đầu trang: ${e.name}"))
                            0 -> Notice.show(ctx, L.tr("Đã bỏ ghim: ${e.name}"))
                            else -> Notice.show(ctx, L.tr("Đã ghim đủ ${S.MAX_PINS} chế độ, bỏ bớt để ghim thêm."))
                        }
                    },
                    onTap = { S.pickEffect(e) }
                )
            }
            .padding(start = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(e.icon, null, tint = e.color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        // tên xuống dòng theo từ (tối đa 3 dòng trong ô cao 56), chỉ thu nhỏ khi thật sự không vừa; không ngắt giữa từ
        FitT(e.name, 12, if (on) FontWeight.ExtraBold else FontWeight.SemiBold, if (on) Txt else SubTxt, Modifier.weight(1f), TextAlign.Start, 3, true)
        if (S.pins.contains(e.id)) Icon(Icons.Filled.PushPin, null, tint = Grey, modifier = Modifier.align(Alignment.Top).padding(top = 6.dp, end = 2.dp).size(11.dp))
        Effects.numOf(e.id)?.let { T("$it", 10, FontWeight.Bold, Grey, Modifier.align(Alignment.Top).padding(start = 2.dp, top = 5.dp)) }
    }
}

/** Các chế độ đã ghim, nằm trên cùng phần chế độ. */
@Composable
fun PinnedCard(cols: Int) {
    val list = S.pins.mapNotNull { Effects.byId(it) }
    if (list.isEmpty()) return
    Card {
        GroupTitle(Icons.Filled.PushPin, "ĐÃ GHIM")
        Spacer(Modifier.height(14.dp))
        Grid(list, cols, 9.dp, 11.dp) { e, m -> EffectTile(e, m) }
    }
}

const val APP_RUN_NOTE = "Các chế độ do app điều khiển (từ Cầu vồng mượt đến Thư giãn): chạy khi app còn mở và đang kết nối."

@Composable
fun EffectGroupCard(g: EffectGroup, cols: Int = 3, hint: Boolean = false) {
    Card {
        GroupTitle(g.icon, g.name)
        Spacer(Modifier.height(14.dp))
        Grid(g.items, cols, 9.dp, 11.dp) { e, m -> EffectTile(e, m) }
        if (hint) { Spacer(Modifier.height(8.dp)); Hint("Mẹo: chạm giữ một chế độ để ghim lên đầu trang.", 11) }
        if (g.items.any { it.id >= 100 }) { Spacer(Modifier.height(8.dp)); Hint(APP_RUN_NOTE, 11) }
        val cur = g.items.firstOrNull { it.id == S.mode.intValue && it.subs.isNotEmpty() }
        if (cur != null) {
            Spacer(Modifier.height(13.dp))
            Grid(cur.subs.indices.toList(), cols) { i, m ->
                val on = S.sub[cur.id] == i
                Box(
                    m.height(51.dp).glow(on, cur.color, RoundedCornerShape(Theme.r(8))).clip(RoundedCornerShape(Theme.r(8))).background(if (on) mix(cur.color, 0.2f) else ChipBg)
                        .border(if (on) 2.dp else 1.dp, if (on) cur.color else Bd, RoundedCornerShape(Theme.r(8))).clickable { S.pickSub(cur, i) }.padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) { FitT(cur.subs[i], 11, if (on) FontWeight.ExtraBold else FontWeight.SemiBold, if (on) Txt else SubTxt, lines = 3, wrapFirst = true) }
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
                        Box(Modifier.size(39.dp).clip(RoundedCornerShape(Theme.r(6))).background(CardBg).border(1.dp, Bd, RoundedCornerShape(Theme.r(6))).clickable { S.addCustom() }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Add, "Thêm màu tùy chỉnh", tint = Txt, modifier = Modifier.size(20.dp))
                        }
                    } else {
                        val on = c == cur
                        if (Fx) ColorOrb(rgbColor(c), on, Modifier.pointerInput(c) { detectTapGestures(onLongPress = { if (!PRESETS.contains(c)) S.removeCustom(c) }, onTap = { S.setColor(c) }) }) {
                            if (on) Icon(Icons.Filled.Check, null, tint = if (c == 0xFFFFFF || c == 0xFFE114) Color(0xFF172032) else Color.White, modifier = Modifier.size(20.dp))
                        } else Box(
                            Modifier.size(39.dp).clip(RoundedCornerShape(Theme.r(6))).background(rgbColor(c))
                                .border(if (on) 2.dp else 1.dp, if (on) Txt else Bd, RoundedCornerShape(Theme.r(6)))
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
    fun needBlock() = Notice.show(ctx, L.tr("Chưa đọc được cài đặt chào mừng từ hộp (v$ver). Kiểm tra kết nối rồi thử lại."))
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
    Card {
        GroupTitle(Icons.Filled.MeetingRoom, "Vị trí bắt đầu chào mừng", 16)
        Spacer(Modifier.height(12.dp))
        Grid(STARTS, 2) { (label, code), m -> Chip(label, S.wStart.intValue == code, m, 44.dp, 12) { S.setWStart(code) } }
    }
}

/** Sơ đồ xe: ghép hộp phụ, số LED, hướng chạy. */
@Composable
fun WelcomeDiagram() {
    Card {
        GroupTitle(Icons.Filled.MeetingRoom, "Sơ đồ xe", 16)
        Spacer(Modifier.height(8.dp))
        Hint("Chạm một hộp phụ: đèn ở cửa gắn hộp đó sẽ nhấp nháy. Thấy cửa nào nháy thì chạm hình xe đang mở cửa đó. Giữ lâu một hộp để bỏ gán.", 12)
        Spacer(Modifier.height(12.dp))
        Grid(listOf(1, 2, 3, 4), 4, 8.dp) { n, m ->
            val on = S.pickBox.intValue == n
            val p = S.boxPos[n - 1]
            Column(
                m.height(56.dp).clip(RoundedCornerShape(Theme.r(8))).background(if (on) Pink else ChipBg).border(1.dp, if (on) Pink else Bd, RoundedCornerShape(Theme.r(8)))
                    .pointerInput(n) { detectTapGestures(onLongPress = { S.unassignBox(n) }, onTap = { S.flashBox(n) }) },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
            ) {
                T("Hộp phụ $n", 12, FontWeight.Bold, if (on) Color.White else Txt)
                T(if (on) "Đang nháy" else if (p >= 2) SEG_SHORT[p] else if (p == 0) "Khác" else "Theo hộp", 10, color = if (on) Color.White else Grey)
            }
        }
        Spacer(Modifier.height(12.dp))
        StepDiagram()
        Spacer(Modifier.height(8.dp))
        Hint("Số LED: chạm − / + để chỉnh từng bóng, giữ nút để chạy nhanh, chạm vào số để gõ thẳng. Chạm hình xe hoặc ‹ › để đổi đoạn.", 11)
        Spacer(Modifier.height(16.dp))
        var ask by remember { mutableStateOf(false) }
        Box(Modifier.fillMaxWidth().height(43.dp).clip(RoundedCornerShape(Theme.r(6))).background(CardBg).border(1.dp, Pink, RoundedCornerShape(Theme.r(6))).clickable { ask = true }, contentAlignment = Alignment.Center) {
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
fun SegTile(i: Int, modifier: Modifier) {
    val door = i >= 2
    val picking = S.pickBox.intValue > 0
    val box = S.boxPos.indexOf(i)
    var edit by remember { mutableStateOf(false) }
    Column(
        modifier.alpha(if (!door && picking) 0.45f else 1f).clip(RoundedCornerShape(Theme.r(10))).background(CardBg)
            .border(if (door && picking) 2.dp else 1.dp, if (door && picking) Pink else Bd, RoundedCornerShape(Theme.r(10)))
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

val SEG_COLORS = listOf(Color(0xFFD4537E), Color(0xFF378ADD), Color(0xFF1D9E75), Color(0xFFBA7517), Color(0xFF7F77DD), Color(0xFFD85A30))

/** Sơ đồ xe kiểu "Từng bước": mỗi lần chỉnh một đoạn, nút to. */
@Composable
fun StepDiagram() {
    var sel by remember { mutableIntStateOf(0) }
    val i = sel
    val col = SEG_COLORS[i]
    val door = i >= 2
    val picking = S.pickBox.intValue > 0
    val box = S.boxPos.indexOf(i)
    var edit by remember { mutableStateOf(false) }
    CarRow(i) { sel = it }
    Spacer(Modifier.height(10.dp))
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Theme.r(14))).background(CardBg).border(1.5.dp, col, RoundedCornerShape(Theme.r(14))).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).block(false, RoundedCornerShape(Theme.r(10))).clickable { sel = (i + 5) % 6 }, contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.ArrowBack, "Đoạn trước", tint = Txt, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                T(SEG_NAMES[i], 16, FontWeight.Bold, align = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                if (!door) T("Hộp chính", 11, color = Grey)
                else if (picking) Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Pink).clickable { S.assignBox(i) }.padding(horizontal = 12.dp, vertical = 5.dp)) { T("Gán hộp này vào cửa", 12, FontWeight.Bold, Color.White) }
                else if (box >= 0) Box(Modifier.clip(RoundedCornerShape(10.dp)).background(col).padding(horizontal = 10.dp, vertical = 3.dp)) { T("Hộp ${box + 1}", 11, FontWeight.Bold, Color.White) }
                else T("Theo hộp", 11, color = Grey)
            }
            Box(Modifier.size(44.dp).block(false, RoundedCornerShape(Theme.r(10))).clickable { sel = (i + 1) % 6 }, contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.ArrowForward, "Đoạn sau", tint = Txt, modifier = Modifier.size(20.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RepeatBtn(Icons.Filled.Remove, "Giảm số LED ${SEG_NAMES[i]}", Modifier.size(52.dp, 50.dp)) { S.setLed(i, S.leds[i] - 1) }
            Column(Modifier.widthIn(min = 80.dp).clickable { edit = true }, horizontalAlignment = Alignment.CenterHorizontally) {
                T("${S.leds[i]}", 24, FontWeight.ExtraBold, col)
                T("LED", 11, color = Grey)
            }
            RepeatBtn(Icons.Filled.Add, "Tăng số LED ${SEG_NAMES[i]}", Modifier.size(52.dp, 50.dp)) { S.setLed(i, S.leds[i] + 1) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DirBtn(Icons.Filled.ArrowBack, "${SEG_NAMES[i]}: hướng trái", !S.wDir[i], Modifier.width(56.dp).height(50.dp)) { S.setWDir(i, false) }
            DirBtn(Icons.Filled.ArrowForward, "${SEG_NAMES[i]}: hướng phải", S.wDir[i], Modifier.width(56.dp).height(50.dp)) { S.setWDir(i, true) }
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
    else if (k != 0) {          // hai thanh = khu vực 1 và khu vực 2
        val c = SolidColor(if (ph) (if (k == 3) ALERT_RED else AMBER) else DARK)
        if (AlertBridge.lit(1)) l = c
        if (AlertBridge.lit(2)) r = c
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
    val open: () -> Unit = { Setup.show.value = true }       // mở bảng hướng dẫn 2 bước (có cách gỡ "cài đặt bị hạn chế")
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
    SplitCard()

    // ---- đang dẫn đường ----
    Card {
        if (!granted) {
            T("Chưa cấp quyền đọc thông báo", 15, FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Hint("App cần quyền này để đọc hướng rẽ và khoảng cách từ thông báo của Google Maps, Vietmap.", 12)
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(43.dp).clip(RoundedCornerShape(Theme.r(6))).background(Pink).clickable(onClick = openAccess), contentAlignment = Alignment.Center) {
                T("Cấp quyền đọc thông báo", 13, FontWeight.Bold, Color.White)
            }
        } else if (!S.navOn.value) {
            T("Chưa có chỉ đường", 15, FontWeight.Bold)
        } else {
            val dir = S.navDir.value
            Row(verticalAlignment = Alignment.CenterVertically) {
                TurnIcon(dir, 68.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    T("Đang dẫn đường · ${S.navApp.value}", 12, FontWeight.Bold, Color(0xFF2E8B3D))
                    val d = S.navDist.intValue
                    T(if (d < 0) "—" else if (d >= 1000) "${d / 100 / 10.0} km" else "$d m", 30, FontWeight.ExtraBold)
                    val dt = L.tr(when (dir) { "L" -> "Rẽ trái"; "R" -> "Rẽ phải"; "U" -> "Quay đầu"; "O" -> "Vòng xuyến"; "A" -> "Đến nơi"; "S" -> "Đi thẳng"; else -> "Chưa rõ hướng" })
                    T(if (S.navStreet.value.isEmpty()) dt else "$dt · ${S.navStreet.value}", 15, FontWeight.SemiBold)
                }
                val cur = if (S.appSpeed.intValue >= 0) S.appSpeed.intValue else Math.round(S.speedKmh.floatValue)
                val lim = S.speedLimit.intValue
                val over = lim > 0 && cur > lim
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (lim > 0) SpeedSign(lim, 52.dp, over) else T("Chưa có biển", 11, color = Grey)
                    Spacer(Modifier.height(4.dp))
                    T("$cur km/h", 15, FontWeight.ExtraBold, if (over) Color(0xFFE53935) else Txt)
                }
            }
        }
        if (k != 0) {
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(Theme.r(8))).background(tint(0.12f)).border(1.dp, tint(0.5f), RoundedCornerShape(Theme.r(8))).padding(horizontal = 12.dp, vertical = 9.dp)) {
                T(when (k) { 1 -> "Sắp rẽ trái: đèn vàng đang chạy theo hướng rẽ"; 2 -> "Sắp rẽ phải: đèn vàng đang chạy theo hướng rẽ"; else -> "Quá tốc độ: đang nháy đỏ" }, 13, FontWeight.Bold, AccentText)
            }
        }
    }

    // ---- cảnh báo rẽ ----
    Card {
        GroupTitle(Icons.Filled.NotificationsNone, "Cảnh báo rẽ", 16)
        Spacer(Modifier.height(8.dp))
        SwitchRow("Báo đèn khi sắp rẽ", "", S.warnOn.value) { S.warnOn.value = it; S.saveSettings(); S.syncService() }
        SwitchRow("Đảo chiều chạy", "", S.turnFlip.value) { S.turnFlip.value = it; S.saveSettings() }
        Spacer(Modifier.height(10.dp))
        T("Hiệu ứng khi báo rẽ", 14, FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        val tf = S.turnFx.intValue.coerceIn(0, AlertBridge.TURN_FX.size - 1)
        Grid(listOf(1, 2, 0), 3) { n, m -> Chip(AlertBridge.TURN_NAMES[n], tf == n, m, 44.dp, 12) { S.turnFx.intValue = n; S.saveSettings() } }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
            T("Tốc độ đèn chạy", 14, FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Box(Modifier.height(27.dp).widthIn(min = 40.dp).clip(RoundedCornerShape(Theme.r(6))).background(tint(0.2f)).border(1.dp, tint(0.6f), RoundedCornerShape(Theme.r(6))).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                T("${S.turnSpd[tf]}", 14, FontWeight.ExtraBold, Pink)
            }
        }
        SkSlider((S.turnSpd[tf] - 1) / 9f, { S.turnSpd[tf] = 1 + (it * 9).roundToInt(); S.saveSettings() }, Pink, tint(0.2f), Pink,
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp), 8.dp, 21.dp, 10, "${S.turnSpd[tf]}")
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            T("Báo trước", 14, FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Box(Modifier.height(27.dp).widthIn(min = 58.dp).clip(RoundedCornerShape(Theme.r(6))).background(tint(0.2f)).border(1.dp, tint(0.6f), RoundedCornerShape(Theme.r(6))).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                T("${S.alertDist.intValue} m", 14, FontWeight.ExtraBold, Pink)
            }
        }
        SkSlider((S.alertDist.intValue - 50) / 450f, { S.alertDist.intValue = 50 + (it * 45).roundToInt() * 10; S.saveSettings() }, Pink, tint(0.2f), Pink,
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp), 8.dp, 21.dp, label = "${S.alertDist.intValue} m")
        Spacer(Modifier.height(10.dp))
        var open by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            T("Thời gian đèn chạy", 14, FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Box {
                Row(
                    Modifier.height(34.dp).clip(RoundedCornerShape(Theme.r(8))).background(tint(0.2f)).border(1.dp, tint(0.6f), RoundedCornerShape(Theme.r(8)))
                        .clickable { open = true }.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    T(if (S.alertMaxSec.intValue == 0) "Tới khi rẽ xong" else "${S.alertMaxSec.intValue} giây", 14, FontWeight.ExtraBold, Pink)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Filled.ArrowDropDown, null, tint = Pink, modifier = Modifier.size(20.dp))
                }
                androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    (listOf(0) + (1..20)).forEach { sec ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { T(if (sec == 0) "Tới khi rẽ xong" else "$sec giây", 14, if (S.alertMaxSec.intValue == sec) FontWeight.ExtraBold else FontWeight.Medium, if (S.alertMaxSec.intValue == sec) Pink else Txt) },
                            onClick = { S.alertMaxSec.intValue = sec; S.saveSettings(); open = false }
                        )
                    }
                }
            }
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
    }

    // ---- cảnh báo quá tốc độ ----
    Card {
        GroupTitle(Icons.Filled.NotificationsNone, "Cảnh báo quá tốc độ", 16)
        Spacer(Modifier.height(8.dp))
        SwitchRow("Nháy đỏ khi quá tốc độ", "", S.speedWarn.value) { S.speedWarn.value = it; S.saveSettings(); S.syncService() }
        SwitchRow("Tra giới hạn tốc độ theo vị trí", "Dùng khi không đọc được biển từ app bản đồ. Cần mạng; gửi vị trí tới máy chủ OpenStreetMap, nhiều đoạn đường chưa có dữ liệu.", S.limitOsm.value) { S.limitOsm.value = it; S.saveSettings(); if (!it) Limit.clearOsm() }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((label, z) in listOf("Khu vực 1" to 1, "Khu vực 2" to 2, "Cả xe" to 0))
                Chip(label, S.speedZone.intValue == z, Modifier.weight(1f), 44.dp, 12) { S.speedZone.intValue = z; S.saveSettings() }
        }
        if (S.dev.value && S.ocrNote.value.isNotEmpty()) T(S.ocrNote.value, 11, color = Grey)
        if (S.dev.value) S.signThumb.value?.let { bmp ->
            Spacer(Modifier.height(6.dp))
            T("Ảnh biển app đang đọc:", 11, color = Grey)
            androidx.compose.foundation.Image(bitmap = bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.height(90.dp))
        }
    }

    // ---- quyền còn thiếu: chỉ hiện khi chưa cấp đủ; cấp xong thì thẻ tự ẩn ----
    var accOn by remember { mutableStateOf(true) }
    val openAcc = { try { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {} }
    LaunchedEffect(Unit) { while (true) { accOn = (Settings.Secure.getString(ctx.contentResolver, "enabled_accessibility_services") ?: "").contains(ctx.packageName); delay(1000) } }
    if (!granted || !accOn) Card {
        GroupTitle(Icons.Filled.Navigation, "Cần cấp quyền", 16)
        Spacer(Modifier.height(6.dp))
        T("Quyền đọc thông báo dùng cho Google Maps, Vietmap. Quyền Hỗ trợ tiếp cận dùng cho app không có thông báo như Gofa.", 12, color = Grey)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!granted) OutlineBtn("Cấp quyền thông báo", Modifier.weight(1f), openAccess)
            if (!accOn) OutlineBtn("Bật Hỗ trợ tiếp cận", Modifier.weight(1f)) { Setup.show.value = true }
        }
    }

    // ---- phần kiểm tra cho kỹ thuật: chỉ có ở chế độ kỹ thuật (chạm 7 lần vào dòng phiên bản ở trang Cài đặt, chế độ nghiên cứu) ----
    if (S.dev.value) Card {
        GroupTitle(Icons.Filled.Navigation, "Đọc app dẫn đường", 16)
        Spacer(Modifier.height(8.dp))
        var open by remember { mutableStateOf(false) }
        Kv("Quyền đọc thông báo", if (granted) "Đã cấp" else "Chưa cấp")
        Kv("Quyền Hỗ trợ tiếp cận", if (accOn) "Đã bật" else "Chưa bật")
        if (!granted || !accOn) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!granted) OutlineBtn("Cấp quyền thông báo", Modifier.weight(1f), openAccess)
                if (!accOn) OutlineBtn("Bật Hỗ trợ tiếp cận", Modifier.weight(1f)) { Setup.show.value = true }
            }
        }
        SwitchRow("Tự đọc khi dùng Gofa và app bản đồ", "Cho app dẫn đường không có thông báo. Chỉ đọc màn hình của app bản đồ, cần quyền Hỗ trợ tiếp cận.", S.screenAuto.value) { S.screenAuto.value = it; S.saveSettings() }
        Divider()
        Row(Modifier.fillMaxWidth().heightIn(min = 46.dp).clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            T("Kiểm tra (dành cho kỹ thuật)", 14, FontWeight.Bold, modifier = Modifier.weight(1f))
            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = SubTxt, modifier = Modifier.size(22.dp))
        }
        if (open) {
            // thông báo của app dẫn đường
            T("Thông báo của app dẫn đường", 13, FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Kv("Biển báo tốc độ thấy được", if (S.signSeen.value.isEmpty()) "Chưa thấy" else S.signSeen.value)
            if (S.navLog.isEmpty()) T("Chưa ghi được thông báo nào.", 12, color = Grey)
            for (line in S.navLog) {
                Box(Modifier.padding(bottom = 6.dp).fillMaxWidth().clip(RoundedCornerShape(Theme.r(6))).background(ChipBg).padding(horizontal = 9.dp, vertical = 7.dp)) { T(line, 11, color = SubTxt) }
            }
            OutlineBtn("Sao chép thông báo để gửi", Modifier.fillMaxWidth()) {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("SK Light", "SK Light ${S.VER}, thông báo app dẫn đường:\n" + S.navLog.joinToString("\n")))
                Notice.show(ctx, L.tr("Đã sao chép ${S.navLog.size} dòng."))
            }
            Divider(12.dp, 10.dp)
            // đọc chữ trên màn hình
            T("Đọc chữ trên màn hình", 13, FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Hint("Bấm Bắt đầu, mở app dẫn đường vài giây rồi quay lại đây. Chữ chỉ đọc trên máy, không gửi đi đâu, tự tắt sau 5 phút.", 12)
            Kv("Đang đọc thử", if (S.screenOn.value) "Có" else "Không")
            Kv("Đọc được", if (S.screenInfo.value.isEmpty()) "Chưa có" else S.screenInfo.value)
            Kv("App đọc gần nhất", if (S.screenPkg.value.isEmpty()) "Chưa có" else S.screenPkg.value)
            if (S.screenLines.isEmpty()) T("Chưa đọc được chữ nào.", 12, color = Grey)
            for (line in S.screenLines.take(80)) {
                Box(Modifier.padding(bottom = 4.dp).fillMaxWidth().clip(RoundedCornerShape(Theme.r(6))).background(ChipBg).padding(horizontal = 9.dp, vertical = 5.dp)) { T(line, 11, color = SubTxt) }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlineBtn(if (S.screenOn.value) "Dừng đọc" else "Bắt đầu đọc", Modifier.weight(1f)) {
                    if (S.screenOn.value) S.screenOn.value = false
                    else { S.screenUntil = System.currentTimeMillis() + 300_000; S.screenLines.clear(); S.screenOn.value = true }
                }
                OutlineBtn("Sao chép để gửi", Modifier.weight(1f)) {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("SK Light", "SK Light ${S.VER}, chữ trên màn hình (${S.screenPkg.value}):\n" + S.screenLines.joinToString("\n")))
                    Notice.show(ctx, L.tr("Đã sao chép ${S.screenLines.size} dòng."))
                }
            }
        }
    }
}

// ===================== TRANG CÀI ĐẶT =====================
@Composable
fun SettingsPage(onPick: () -> Unit) { SettingsDevice(onPick); if (S.connected.value) WarrantyCard(); if (S.connected.value && Act.ok.value) DriveCard(); SettingsFeatures() }

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
        if (X.rfModel.isNotEmpty()) Kv("Phiên bản hộp", X.rfModel)
        if (X.canModel.isNotEmpty()) Kv("Phiên bản CAN", X.canModel)
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
        SetTitle(Icons.Filled.PhoneAndroid, "Ứng dụng")
        Feature(Icons.Outlined.Palette, "Cài đặt giao diện", "") { S.subPage.intValue = 1 }
        Feature(Icons.Filled.Language, "Cài đặt ngôn ngữ", L.NAMES[L.cur.intValue]) { S.subPage.intValue = 2 }
        Feature(Icons.Filled.MenuBook, "Cẩm nang sử dụng", "") { S.subPage.intValue = 12 }
        RunModeRow()
    }
    if (S.connected.value && Act.ok.value) Card {
        SetTitle(Icons.Filled.Build, "Lắp đặt")
        // Chức năng gốc xe chưa kiểm chứng xong trên xe thật nhưng chủ dự án yêu cầu luôn hiện (sẽ hoàn thiện sau)
        if (!S.commonBox()) Feature(Icons.Filled.DirectionsCar, "Chức năng gốc xe", "") { S.subPage.intValue = 9 }
        Feature(Icons.Filled.Mic, "Trợ lý SK Light (AI)", "") { S.subPage.intValue = 4 }
        Feature(Icons.Filled.ArrowForward, "Hướng ánh sáng chạy", "") { S.subPage.intValue = 6 }
        Feature(Icons.Filled.Build, "Cài đặt nhà máy", "") { S.subPage.intValue = 8 }
    }
    if (S.connected.value && !Act.ok.value && S.dev.value) Card {
        SetTitle(Icons.Filled.Science, "Nghiên cứu")
        Feature(Icons.Filled.DirectionsCar, "Chọn loại xe", "") { S.subPage.intValue = 11 }
    }
    if (S.connected.value && Act.ok.value && S.dev.value) Card {
        SetTitle(Icons.Filled.Science, "Kiểm tra")
        Feature(Icons.Filled.PhotoCamera, "Chụp trạng thái hộp", "") { S.subPage.intValue = 15 }
        Feature(Icons.Filled.Fingerprint, "Tìm số seri hộp", "") { S.subPage.intValue = 14 }
        Feature(Icons.Filled.BluetoothSearching, "Thử hộp phụ", "") { S.subPage.intValue = 16 }
        Feature(Icons.Filled.Science, "Thử chế độ tự tạo", "") { S.subPage.intValue = 3 }
    }
    Card {
        Feature(Icons.Filled.SystemUpdate, "Kiểm tra cập nhật", if (Updater.checking.value) "Đang kiểm tra…" else if (Updater.info.value != null) "Có bản mới ${Updater.info.value?.name}" else "Đang dùng ${S.VER}") { Updater.check(ctx, true) }
    }
    // chạm 7 lần liên tiếp vào dòng phiên bản để bật / tắt chế độ nghiên cứu (hiện thẻ Kiểm tra và công cụ nghiên cứu)
    var taps by remember { mutableIntStateOf(0) }
    var askPw by remember { mutableStateOf(false) }
    var pw by remember { mutableStateOf("") }
    var pwErr by remember { mutableStateOf(false) }
    T(if (S.dev.value) "SK Light ${S.VER} · chế độ nghiên cứu" else "SK Light ${S.VER}", 12, color = Grey, align = TextAlign.Center, modifier = Modifier.fillMaxWidth().clickable(
        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null
    ) {
        if (!S.connected.value) return@clickable
        taps++
        if (taps >= 7) {
            taps = 0
            if (S.dev.value) { S.dev.value = false; Notice.show(ctx, L.tr("Đã tắt chế độ nghiên cứu.")) } else { pw = ""; pwErr = false; askPw = true }
        }
    }.padding(vertical = 8.dp))
    if (askPw) androidx.compose.material3.AlertDialog(
        onDismissRequest = { askPw = false },
        title = { T("Chế độ nghiên cứu", 16, FontWeight.ExtraBold) },
        text = {
            Column {
                T("Nhập mật khẩu để bật.", 13, color = Grey)
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.OutlinedTextField(pw, { pw = it.filter { c -> c.isLetterOrDigit() }.take(12); pwErr = false }, singleLine = true, isError = pwErr,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                    label = { T("Mật khẩu", 12, color = Grey) })
                if (pwErr) T("Mật khẩu chưa đúng.", 12, color = androidx.compose.ui.graphics.Color(0xFFE53935))
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                if (pw == "00000" && S.connected.value) { S.dev.value = true; askPw = false; Notice.show(ctx, L.tr("Đã bật chế độ nghiên cứu.")) } else pwErr = true
            }) { T("Mở", 14, FontWeight.Bold, color = Pink) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { askPw = false }) { T("Hủy", 14, color = Grey) } }
    )
}

@Composable
fun SetTitle(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Pink, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        T(text, 16, FontWeight.Bold)
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
fun Kv(k: String, v: String) {
    Row(Modifier.heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically) {
        T(k, 13, modifier = Modifier.weight(1f))
        T(v, 13, FontWeight.SemiBold)
    }
}


@Composable
fun Feature(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = if (sub.isEmpty()) 60.dp else 76.dp).clickable(onClick = onClick).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = SubTxt, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) { T(title, 16); if (sub.isNotEmpty()) T(sub, 13, color = SubTxt) }
        Icon(Icons.Filled.ChevronRight, null, tint = SubTxt, modifier = Modifier.size(20.dp))
    }
}

