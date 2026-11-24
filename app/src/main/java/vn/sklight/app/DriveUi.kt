package vn.sklight.app

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.io.File
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val DeepPink: Color get() = AccentText
private val RAINBOW_DOT = listOf(Color(0xFFFE2B48), Color(0xFFFED119), Color(0xFF40E01E), Color(0xFF1AF4EA), Color(0xFF4E78FF), Color(0xFF8A01FF))

private fun sceneBrush(sc: Scene): Brush = if (sc.rainbow) Brush.horizontalGradient(RAINBOW_DOT) else SolidColor(rgbColor(sc.c1))

// ===================== BỐI CẢNH YÊU THÍCH =====================
/** Hàng bối cảnh ở đầu trang Đèn: "Thêm" để lưu cài đặt đang dùng và xóa bớt; chạm để dùng; giữ lâu để đổi tên, cập nhật, xóa. */
@Composable
fun SceneRow() {
    var manage by remember { mutableStateOf(false) }
    var edit by remember { mutableIntStateOf(-1) }
    val all = Scenes.all()
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(CardBg).border(1.dp, Pink, RoundedCornerShape(22.dp))
                .clickable { manage = true }.padding(start = 10.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Add, "Thêm hoặc xóa bối cảnh yêu thích", tint = DeepPink, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            T("Thêm", 13, FontWeight.Bold, DeepPink)
        }
        all.forEachIndexed { i, sc ->
            val on = S.sceneSel.intValue == i
            val tap by rememberUpdatedState({ Scenes.apply(i) })
            val hold by rememberUpdatedState({ edit = i })
            Row(
                Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(if (on) tint(0.12f) else ChipBg)
                    .border(1.dp, if (on) Pink else Bd, RoundedCornerShape(22.dp))
                    .pointerInput(Unit) { detectTapGestures(onLongPress = { hold() }, onTap = { tap() }) }
                    .padding(start = 10.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(16.dp).clip(CircleShape).background(sceneBrush(sc)).border(1.dp, Color(0x26172032), CircleShape))
                Spacer(Modifier.width(8.dp))
                T(sc.name, 13, if (on) FontWeight.ExtraBold else FontWeight.SemiBold)
            }
        }
    }
    if (manage) {
        var name by remember { mutableStateOf("") }
        val full = all.size >= Scenes.MAX
        AlertDialog(
            onDismissRequest = { manage = false },
            confirmButton = { TextButton(onClick = { manage = false }) { T("Xong", 14, FontWeight.Bold, Pink) } },
            title = { T("Bối cảnh yêu thích", 17, FontWeight.Bold) },
            text = {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(name, { name = it.take(18) }, singleLine = true, placeholder = { T("Tên bối cảnh, ví dụ: Đi làm", 14, color = Grey) })
                    Box(
                        Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(Theme.r(8))).background(if (full) Grey else Pink).clickable { if (!full && Scenes.saveCurrent(name)) name = "" },
                        contentAlignment = Alignment.Center
                    ) { T(if (full) "Đã đủ ${Scenes.MAX} bối cảnh, xóa bớt để thêm" else "Lưu cài đặt đang dùng", 13, FontWeight.Bold, Color.White) }
                    T("Đang có", 13, FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                    if (all.isEmpty()) T("Chưa có bối cảnh nào.", 13, color = Grey)
                    all.forEachIndexed { i, sc ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(16.dp).clip(CircleShape).background(sceneBrush(sc)).border(1.dp, Color(0x26172032), CircleShape))
                            Spacer(Modifier.width(10.dp))
                            T(sc.name, 14, FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Box(Modifier.size(40.dp).clip(CircleShape).clickable { Scenes.delete(i) }, contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Close, "Xóa bối cảnh ${sc.name}", tint = Grey, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    OutlineBtn("Khôi phục 5 bối cảnh có sẵn", Modifier.fillMaxWidth()) { Scenes.restore() }
                }
            }
        )
    }
    val cur = all.getOrNull(edit)
    if (cur != null) {
        var name by remember(edit) { mutableStateOf(cur.name) }
        AlertDialog(
            onDismissRequest = { edit = -1 },
            confirmButton = { TextButton(onClick = { Scenes.rename(edit, name); edit = -1 }) { T("Lưu tên", 14, FontWeight.Bold, Pink) } },
            dismissButton = { TextButton(onClick = { edit = -1 }) { T("Đóng", 14, FontWeight.Bold) } },
            title = { T("Bối cảnh: ${cur.name}", 17, FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(name, { name = it.take(18) }, singleLine = true)
                    OutlineBtn("Cập nhật theo cài đặt đang dùng", Modifier.fillMaxWidth()) { Scenes.update(edit); edit = -1 }
                    OutlineBtn("Xóa bối cảnh này", Modifier.fillMaxWidth()) { Scenes.delete(edit); edit = -1 }
                }
            }
        )
    }
}

// ===================== THẺ "KHI LÁI XE" TRONG CÀI ĐẶT =====================
// 4 kiểu đầu giữ nguyên thứ tự cũ (số thứ tự đã lưu trong máy người dùng)
val IDLE_STYLES = listOf("Đồng hồ + tốc độ", "Chỉ đồng hồ", "Tốc độ lớn", "Ảnh của tôi", "Đồng hồ kim", "Vòng tốc độ", "Dẫn đường", "Nhạc đang phát", "Tối giản", "Đồng hồ 2,5D", "Số lật")
fun photoFile(ctx: Context) = File(ctx.filesDir, "idle.jpg")

@Composable
fun DriveCard() {
    val ctx = LocalContext.current
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.DirectionsCar, null, tint = Pink, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            T("Khi lái xe", 16, FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        SwitchRow("Tự kết nối khi khởi động máy", "", S.autoBoot.value) {
            S.autoBoot.value = it; S.saveSettings(); S.syncService(ctx)
        }
        Divider()
        SwitchRow("Tự dịu đèn ban đêm", "", S.nightOn.value) {
            S.nightOn.value = it; S.saveSettings(); Night.tick()
        }
        if (S.nightOn.value) {
            S.placeVer.intValue                       // đọc để vẽ lại khi có vị trí
            listOf("Theo giờ mặt trời lặn và mọc (tự tính theo vị trí)", "Theo giờ tự đặt").forEachIndexed { i, label ->
                val on = S.nightMode.intValue == i
                Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { S.nightMode.intValue = i; S.saveSettings(); Night.tick() }, verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(18.dp).border(2.dp, if (on) Pink else Grey, CircleShape), contentAlignment = Alignment.Center) {
                        if (on) Box(Modifier.size(8.dp).clip(CircleShape).background(Pink))
                    }
                    Spacer(Modifier.width(10.dp))
                    T(label, 13, if (on) FontWeight.Bold else FontWeight.Medium)
                }
            }
            if (S.nightMode.intValue == 1) {
                fun ask(cur: Int, set: (Int) -> Unit) = TimePickerDialog(ctx, { _, h, m -> set(h * 60 + m); S.saveSettings(); Night.tick() }, cur / 60, cur % 60, true).show()
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                    OutlineBtn("Từ ${Night.hm(S.nightFrom.intValue)}", Modifier.weight(1f)) { ask(S.nightFrom.intValue) { S.nightFrom.intValue = it } }
                    OutlineBtn("Đến ${Night.hm(S.nightTo.intValue)}", Modifier.weight(1f)) { ask(S.nightTo.intValue) { S.nightTo.intValue = it } }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                T("Độ sáng ban đêm", 14, FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Box(Modifier.height(27.dp).widthIn(min = 52.dp).clip(RoundedCornerShape(Theme.r(6))).background(tint(0.2f)).border(1.dp, tint(0.6f), RoundedCornerShape(Theme.r(6))).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                    T("${S.nightBri.intValue}%", 14, FontWeight.ExtraBold, DeepPink)
                }
            }
            SkSlider((S.nightBri.intValue - 5) / 95f, { Night.setLevel(5 + (it * 19).roundToInt() * 5) }, Pink, tint(0.2f), Pink,
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 6.dp), 8.dp, 21.dp)
            val w = Night.window()
            val noPlace = S.nightMode.intValue == 0 && Night.sunToday() == null
            T(
                (if (noPlace) "Chưa có vị trí của máy, tạm dùng giờ tự đặt: " else "Hôm nay dịu đèn ") + "từ ${Night.hm(w.first)} đến ${Night.hm(w.second)}. Ban đêm chỉnh độ sáng tay thì app nhớ mức đó cho ban đêm.",
                11, color = Grey, modifier = Modifier.padding(top = 6.dp, bottom = 10.dp)
            )
        }
        Divider()
        SwitchRow("Màn hình chờ khi xe chạy", "", S.idleOn.value) { S.idleOn.value = it; S.saveSettings() }
        if (S.idleOn.value) {
            val v = maxOf(S.speedKmh.floatValue.roundToInt(), S.appSpeed.intValue)
            T("Tốc độ đang đọc được: $v km/h. Màn hình chờ chỉ tự mở khi xe chạy từ 10 km/h; xe đứng yên thì không mở.", 11, color = Grey)
        }
        Divider()
        SwitchRow("Nút nổi trên màn hình", "", S.floatOn.value) {
            S.floatOn.value = it; S.saveSettings(); S.syncService(ctx)
            if (it && !Settings.canDrawOverlays(ctx)) {
                Notice.show(ctx, L.tr("Bật quyền \"Hiển thị trên ứng dụng khác\" cho SK Light rồi quay lại."), true)
                try { ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {}
            }
        }
    }
}

// ===================== MÀN HÌNH CHỜ KHI LÁI =====================
private val DimTxt = Color(0xFFB9C0CE)
private val DAYS = listOf("Chủ nhật", "Thứ hai", "Thứ ba", "Thứ tư", "Thứ năm", "Thứ sáu", "Thứ bảy")

private fun loadPhoto(ctx: Context): Bitmap? = try {
    val f = photoFile(ctx)
    if (!f.exists()) null else {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var k = 1
        while (maxOf(o.outWidth, o.outHeight) / k > 2048) k *= 2
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = k })
    }
} catch (e: Throwable) { null }

private val LimitRed = Color(0xFFE5202E)

@Composable
private fun Big(text: String, size: Float, color: Color = Color.White) {
    Text(text, fontSize = size.sp, lineHeight = size.sp, fontWeight = FontWeight.ExtraBold, color = color, fontFamily = Inter, maxLines = 1)
}

private fun dirIcon(d: String): ImageVector = when (d) {
    "L" -> Icons.Filled.TurnLeft; "R" -> Icons.Filled.TurnRight; "U" -> Icons.Filled.UTurnLeft; "O" -> Icons.Filled.RoundaboutRight; "A" -> Icons.Filled.Flag; else -> Icons.Filled.Straight
}
private fun dirText(d: String) = when (d) { "L" -> "Rẽ trái"; "R" -> "Rẽ phải"; "U" -> "Quay đầu"; "O" -> "Vòng xuyến"; "A" -> "Đến nơi"; "S" -> "Đi thẳng"; else -> "" }

/** Biển giới hạn tốc độ: vòng tròn viền đỏ. */
@Composable
private fun LimitSign(limit: Int, d: Dp) {
    Box(Modifier.size(d).clip(CircleShape).background(Color.White).border(d / 9, LimitRed, CircleShape), contentAlignment = Alignment.Center) {
        Text("$limit", fontSize = (d.value * 0.4f).sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF14161C), fontFamily = Inter, maxLines = 1)
    }
}

/** Thẻ chỉ đường trên màn hình chờ, hiện khi app bản đồ đang dẫn đường. [hero] = bản lớn của kiểu "Dẫn đường". */
@Composable
private fun NavCard(hero: Boolean, wide: Boolean) {
    val d = S.navDist.intValue
    val dist = if (d < 0) "—" else if (d >= 1000) "${d / 100 / 10.0} km" else "$d m"
    val box = if (hero) (if (wide) 120.dp else 100.dp) else 60.dp
    val turn = L.tr(dirText(S.navDir.value))
    val line = listOf(turn, S.navStreet.value).filter { it.isNotEmpty() }.joinToString(" · ")
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0x1FFFFFFF)).border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(18.dp)).padding(if (hero) 18.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TurnIcon(S.navDir.value, box)
        Spacer(Modifier.width(if (hero) 18.dp else 14.dp))
        Column(Modifier.weight(1f)) {
            val fs = if (hero) (if (wide) 64 else 50) else 30
            Text(dist, fontSize = fs.sp, lineHeight = fs.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, fontFamily = Inter, maxLines = 1)
            if (line.isNotEmpty()) Text(line, fontSize = (if (hero) 22 else 16).sp, fontWeight = FontWeight.SemiBold, color = Color.White, fontFamily = Inter, maxLines = if (hero) 2 else 1, overflow = TextOverflow.Ellipsis)
            if (hero && S.navApp.value.isNotEmpty()) Text(S.navApp.value, fontSize = 13.sp, color = DimTxt, fontFamily = Inter, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (S.speedLimit.intValue > 0) { Spacer(Modifier.width(10.dp)); SpeedSign(S.speedLimit.intValue, if (hero) 76.dp else 52.dp, (if (S.appSpeed.intValue >= 0) S.appSpeed.intValue else S.speedKmh.floatValue.roundToInt()) > S.speedLimit.intValue) }
    }
}

/** Đồng hồ kim. */
@Composable
private fun AnalogClock(cal: Calendar, d: Dp, accent: Color) {
    val hh = cal.get(Calendar.HOUR); val mm = cal.get(Calendar.MINUTE); val ss = cal.get(Calendar.SECOND)
    Canvas(Modifier.size(d)) {
        val c = center
        val r = size.minDimension / 2f
        fun pt(deg: Double, k: Float): Offset { val a = Math.toRadians(deg - 90.0); return Offset(c.x + (cos(a) * r * k).toFloat(), c.y + (sin(a) * r * k).toFloat()) }
        drawCircle(Color(0x14FFFFFF), r, c)
        drawCircle(Color(0x59FFFFFF), r * 0.99f, c, style = Stroke(r * 0.02f))
        for (i in 0 until 60) {
            val five = i % 5 == 0
            drawLine(if (five) Color.White else Color(0x80FFFFFF), pt(i * 6.0, if (five) 0.84f else 0.9f), pt(i * 6.0, 0.94f), strokeWidth = r * (if (five) 0.024f else 0.01f), cap = StrokeCap.Round)
        }
        drawLine(Color.White, c, pt((hh + mm / 60.0) * 30.0, 0.5f), strokeWidth = r * 0.05f, cap = StrokeCap.Round)
        drawLine(Color.White, c, pt((mm + ss / 60.0) * 6.0, 0.74f), strokeWidth = r * 0.032f, cap = StrokeCap.Round)
        drawLine(accent, pt(ss * 6.0 + 180.0, 0.12f), pt(ss * 6.0, 0.82f), strokeWidth = r * 0.013f, cap = StrokeCap.Round)
        drawCircle(accent, r * 0.045f, c)
    }
}

/** Vòng tốc độ 0..160 km/h, có vạch đỏ ở mức giới hạn nếu biết. */
@Composable
private fun SpeedGauge(spd: Int, limit: Int, d: Dp, cA: Color, cB: Color) {
    val over = limit > 0 && spd > limit
    Box(Modifier.size(d), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = size.minDimension * 0.07f
            val tl = Offset(sw, sw)
            val sz = Size(size.width - sw * 2, size.height - sw * 2)
            drawArc(Color(0x26FFFFFF), 150f, 240f, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
            val f = (spd / 160f).coerceIn(0f, 1f)
            if (f > 0.005f) drawArc(if (over) SolidColor(LimitRed) else Brush.horizontalGradient(listOf(cA, cB)), 150f, 240f * f, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
            if (limit in 1..160) {
                val a = Math.toRadians(150.0 + 240.0 * limit / 160.0)
                val r = size.minDimension / 2f
                val c = center
                drawLine(LimitRed, Offset(c.x + (cos(a) * (r - sw * 2.2f)).toFloat(), c.y + (sin(a) * (r - sw * 2.2f)).toFloat()), Offset(c.x + (cos(a) * (r - sw * 0.3f)).toFloat(), c.y + (sin(a) * (r - sw * 0.3f)).toFloat()), strokeWidth = sw * 0.35f, cap = StrokeCap.Round)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Big("$spd", d.value * 0.34f, if (over) LimitRed else Color.White)
            T("km/h", 18, FontWeight.SemiBold, DimTxt)
        }
    }
}

@Composable
fun IdleScreen(wide: Boolean, preview: Boolean = false) {
    val ctx = LocalContext.current
    val cfg = LocalConfiguration.current
    val view = LocalView.current
    if (!preview) DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        var n = 0
        while (true) { now = System.currentTimeMillis(); if (n % 3 == 0) Media.refresh(ctx); n++; delay(1000) }
    }
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val time = "%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    val date = L.tr(DAYS[cal.get(Calendar.DAY_OF_WEEK) - 1]) + ", %02d/%02d".format(cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1)
    val style = S.idleStyle.intValue
    val plain = style == 8                               // tối giản: nền đen, không nút, không hàng bối cảnh
    val photo = remember(S.idlePhotoVer.intValue, style) { if (style == 3) loadPhoto(ctx) else null }
    val uni = S.czone.intValue == 0
    val fx = S.mode.intValue != 0                       // đang chạy hiệu ứng nhiều màu: dùng hai màu nhấn cố định
    val cL = if (!S.on.value) Color(0xFF3A4050) else if (fx) Color(0xFFFE2B48) else rgbColor(if (uni) S.col[0] else S.col[1])
    val cR = if (!S.on.value) Color(0xFF3A4050) else if (fx) Color(0xFF4E78FF) else rgbColor(if (uni) S.col[0] else S.col[2])
    val exit = { if (!preview) { S.idleShow.value = false; S.lastTouch = System.currentTimeMillis() } }
    val nextStyle = { S.idleStyle.intValue = (style + 1) % IDLE_STYLES.size; S.saveSettings(); S.lastTouch = System.currentTimeMillis() }
    val nav = S.navOn.value
    val spd = if (S.appSpeed.intValue >= 0) S.appSpeed.intValue else S.speedKmh.floatValue.roundToInt()
    val limit = S.speedLimit.intValue
    val spdColor = if (limit > 0 && spd > limit) LimitRed else Color.White
    Box(Modifier.fillMaxSize().background(if (plain) Color.Black else Color(0xFF0D0F15)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = exit)) {
        if (photo != null) {
            Image(photo.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Color(0x73000000)))
        } else if (!plain) Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension * 0.8f
            val a = Offset(0f, size.height * 0.6f)
            val b = Offset(size.width, size.height * 0.6f)
            drawCircle(Brush.radialGradient(listOf(cL.copy(alpha = 0.55f), Color.Transparent), a, r), r, a)
            drawCircle(Brush.radialGradient(listOf(cR.copy(alpha = 0.55f), Color.Transparent), b, r), r, b)
        }
        Column((if (preview) Modifier.fillMaxSize() else Modifier.fillMaxSize().systemBarsPadding()).padding(horizontal = if (wide) 40.dp else 20.dp, vertical = 22.dp)) {
            if (!plain) Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.sk_logo), "SK Ambient Light", Modifier.height(if (wide) 34.dp else 26.dp), contentScale = ContentScale.Fit)
                Spacer(Modifier.width(14.dp))
                T(date, if (wide) 16 else 13, FontWeight.SemiBold, DimTxt, Modifier.weight(1f))
                // đổi kiểu màn hình chờ ngay tại đây: mỗi lần chạm sang kiểu kế tiếp
                Row(
                    Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).border(1.dp, Color(0x59FFFFFF), RoundedCornerShape(22.dp))
                        .clickable(enabled = !preview, onClick = nextStyle).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Autorenew, "Đổi kiểu màn hình chờ", tint = Color.White, modifier = Modifier.size(20.dp))
                    if (wide) { Spacer(Modifier.width(8.dp)); T(IDLE_STYLES[style.coerceIn(0, IDLE_STYLES.size - 1)], 14, FontWeight.Bold, Color.White) }
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).border(1.dp, Color(0x59FFFFFF), RoundedCornerShape(22.dp)).clickable(onClick = exit).padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) { T(if (wide) "Về trang điều khiển" else "Điều khiển", 14, FontWeight.Bold, Color.White) }
            }
            val big = if (wide) cfg.screenHeightDp * 0.27f else cfg.screenWidthDp * 0.26f
            val round = (if (wide) cfg.screenHeightDp * 0.5f else cfg.screenWidthDp * 0.68f).dp       // cỡ đồng hồ kim / vòng tốc độ
            val accent = if (S.on.value) cL else Pink
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when {
                    style == 1 || style == 3 -> Big(time, big * 1.2f)
                    style == 2 -> Column(horizontalAlignment = Alignment.CenterHorizontally) { Big("$spd", big * 1.2f, spdColor); T("km/h", 20, FontWeight.SemiBold, DimTxt) }
                    style == 4 -> if (wide) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        AnalogClock(cal, round, accent)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) { Big("$spd", big * 0.72f, spdColor); T("km/h", 20, FontWeight.SemiBold, DimTxt) }
                    } else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AnalogClock(cal, round, accent); Spacer(Modifier.height(14.dp)); Big("$spd", big * 0.5f, spdColor); T("km/h", 16, FontWeight.SemiBold, DimTxt)
                    }
                    style == 5 -> {
                        val a = if (S.on.value) cL else Color.White
                        val b = if (S.on.value) cR else Color.White
                        if (wide) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) { SpeedGauge(spd, limit, round, a, b); Big(time, big * 0.8f) }
                        else Column(horizontalAlignment = Alignment.CenterHorizontally) { SpeedGauge(spd, limit, round, a, b); Spacer(Modifier.height(10.dp)); Big(time, big * 0.5f) }
                    }
                    style == 6 && nav -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.Center) {
                        NavCard(true, wide)
                        Spacer(Modifier.height(18.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Big(time, big * 0.42f)
                            Spacer(Modifier.weight(1f))
                            Big("$spd", big * 0.42f, spdColor)
                            Spacer(Modifier.width(8.dp))
                            T("km/h", 16, FontWeight.SemiBold, DimTxt)
                        }
                    }
                    style == 7 -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.MusicNote, null, tint = accent, modifier = Modifier.size(if (wide) 56.dp else 46.dp))
                        Spacer(Modifier.height(10.dp))
                        val fs = if (wide) 40 else 28
                        Text(if (S.song.value.isEmpty()) L.tr("Chưa phát nhạc") else S.song.value, fontSize = fs.sp, lineHeight = (fs * 1.2f).sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = Inter, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        Big(time, big * 0.5f, Color(0xD9FFFFFF))
                    }
                    style == 8 -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Big(time, big * 0.8f, Color(0xB3FFFFFF))
                        Spacer(Modifier.height(6.dp))
                        T(date, 15, FontWeight.Medium, Color(0xFF8A919E))
                        if (spd > 0) { Spacer(Modifier.height(10.dp)); Text("$spd km/h", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = if (spdColor == LimitRed) LimitRed else Color(0xFF8A919E), fontFamily = Inter) }
                    }
                    style == 9 -> if (wide) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        Gauge25(spd, limit, round * 1.2f, accent)
                        FlipClock(time, (big * 0.75f).dp, accent)
                    } else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Gauge25(spd, limit, round * 1.1f, accent); Spacer(Modifier.height(16.dp)); FlipClock(time, (big * 0.6f).dp, accent)
                    }
                    style == 10 -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FlipClock(time, (if (wide) big * 1.3f else big * 0.85f).dp, accent)
                        if (spd > 0) { Spacer(Modifier.height(14.dp)); Text("$spd km/h", fontSize = (if (wide) 30 else 22).sp, fontWeight = FontWeight.Bold, color = spdColor, fontFamily = Inter) }
                    }
                    wide -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Big(time, big)
                        Column(horizontalAlignment = Alignment.End) { Big("$spd", big * 0.72f, spdColor); T("km/h", 20, FontWeight.SemiBold, DimTxt) }
                    }
                    else -> Column(horizontalAlignment = Alignment.CenterHorizontally) { Big(time, big); Spacer(Modifier.height(10.dp)); Big("$spd", big * 0.62f, spdColor); T("km/h", 18, FontWeight.SemiBold, DimTxt) }
                }
            }
            if (style == 6 && !nav) T("Chưa có chỉ đường. Mở app bản đồ và bắt đầu dẫn đường, chỉ dẫn sẽ hiện lớn ở đây.", 13, color = DimTxt, modifier = Modifier.padding(bottom = 10.dp))
            // app bản đồ đang dẫn đường: mọi kiểu đều có thẻ chỉ đường (kiểu "Dẫn đường" đã hiện bản lớn ở trên)
            if (nav && style != 6) { NavCard(false, wide); Spacer(Modifier.height(10.dp)) }
            if (S.song.value.isNotEmpty() && style != 7 && !plain) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                    Icon(Icons.Filled.MusicNote, null, tint = DimTxt, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(S.song.value, fontSize = 16.sp, color = DimTxt, fontFamily = Inter, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (!plain) Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Scenes.all().forEachIndexed { i, sc ->
                    val on = S.sceneSel.intValue == i
                    Row(
                        Modifier.height(50.dp).clip(RoundedCornerShape(25.dp)).background(if (on) Color(0x38FFFFFF) else Color(0x14FFFFFF))
                            .border(1.dp, if (on) Color.White else Color(0x40FFFFFF), RoundedCornerShape(25.dp))
                            .clickable { Scenes.apply(i); S.lastTouch = System.currentTimeMillis() }.padding(start = 14.dp, end = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(18.dp).clip(CircleShape).background(sceneBrush(sc)))
                        Spacer(Modifier.width(10.dp))
                        T(sc.name, 15, FontWeight.Bold, Color.White)
                    }
                }
            }
        }
        // kiểu Tối giản không có hàng nút phía trên: để một nút mờ ở góc để vẫn đổi được kiểu
        if (plain && !preview) Box(
            Modifier.align(Alignment.TopEnd).systemBarsPadding().padding(10.dp).size(46.dp).clip(CircleShape).clickable(onClick = nextStyle),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.Autorenew, "Đổi kiểu màn hình chờ", tint = Color(0x66FFFFFF), modifier = Modifier.size(22.dp)) }
    }
}

// ===================== LOGO =====================
/** Logo SK ở cuối mỗi trang (màn dọc). */
@Composable
fun LogoFooter() {
    Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.sk_logo), "SK Ambient Light", Modifier.height(34.dp).alpha(0.95f), contentScale = ContentScale.Fit)
    }
}
