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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.io.File
import java.util.Calendar
import kotlin.math.roundToInt

private val DeepPink = Color(0xFFC4127F)
private val RAINBOW_DOT = listOf(Color(0xFFFE2B48), Color(0xFFFED119), Color(0xFF40E01E), Color(0xFF1AF4EA), Color(0xFF4E78FF), Color(0xFF8A01FF))

private fun sceneBrush(sc: Scene): Brush = if (sc.rainbow) Brush.horizontalGradient(RAINBOW_DOT) else SolidColor(rgbColor(sc.c1))

// ===================== BỐI CẢNH MỘT CHẠM =====================
/** Hàng bối cảnh ở đầu trang Đèn: chạm để dùng, "Lưu" để giữ cài đặt đang dùng, giữ lâu bối cảnh tự tạo để sửa. */
@Composable
fun SceneRow() {
    val ctx = LocalContext.current
    var saving by remember { mutableStateOf(false) }
    var edit by remember { mutableIntStateOf(-1) }           // vị trí trong Scenes.customs đang sửa
    val all = Scenes.all()
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        all.forEachIndexed { i, sc ->
            val on = S.sceneSel.intValue == i
            val tap by rememberUpdatedState({ Scenes.apply(i) })
            val hold by rememberUpdatedState({ if (sc.custom) edit = i - Scenes.presets.size })
            Row(
                Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(if (on) Color(0xFFFFE9F6) else ChipBg)
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
        Row(
            Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(Color.White).border(1.dp, Pink, RoundedCornerShape(22.dp))
                .clickable { if (Scenes.customs.size >= Scenes.MAX_CUSTOM) Toast.makeText(ctx, "Đã đủ ${Scenes.MAX_CUSTOM} bối cảnh tự tạo. Giữ lâu một bối cảnh để xóa bớt.", Toast.LENGTH_LONG).show() else saving = true }
                .padding(start = 10.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Add, "Lưu cài đặt đang dùng thành bối cảnh mới", tint = DeepPink, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            T("Lưu", 13, FontWeight.Bold, DeepPink)
        }
    }
    if (saving) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { saving = false },
            confirmButton = { TextButton(onClick = { Scenes.saveCurrent(name); saving = false }) { T("Lưu", 14, FontWeight.Bold, Pink) } },
            dismissButton = { TextButton(onClick = { saving = false }) { T("Hủy", 14, FontWeight.Bold) } },
            title = { T("Lưu thành bối cảnh của tôi", 17, FontWeight.Bold) },
            text = {
                Column {
                    T("Lưu lại màu từng khu vực, chế độ, tốc độ và độ sáng đang dùng.", 13, color = SubTxt)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(name, { name = it.take(18) }, singleLine = true, placeholder = { T("Tên bối cảnh, ví dụ: Đi làm", 14, color = Grey) })
                }
            }
        )
    }
    val cur = Scenes.customs.getOrNull(edit)
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
private val IDLE_STYLES = listOf("Đồng hồ + tốc độ", "Chỉ đồng hồ", "Tốc độ lớn", "Ảnh của tôi")
private fun photoFile(ctx: Context) = File(ctx.filesDir, "idle.jpg")

@Composable
fun DriveCard() {
    val ctx = LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                ctx.contentResolver.openInputStream(uri)?.use { inp -> photoFile(ctx).outputStream().use { inp.copyTo(it) } }
                S.idlePhotoVer.intValue++
                Toast.makeText(ctx, "Đã đặt ảnh làm màn hình chờ.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { Toast.makeText(ctx, "Không đọc được ảnh này.", Toast.LENGTH_SHORT).show() }
        }
    }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.DirectionsCar, null, tint = Pink, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            T("Khi lái xe", 16, FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        SwitchRow("Tự kết nối khi khởi động máy", "Màn hình xe bật lên là tự nối hộp đèn đã chọn, không cần mở app", S.autoBoot.value) {
            S.autoBoot.value = it; S.saveSettings(); S.syncService(ctx)
        }
        Divider()
        SwitchRow("Tự dịu đèn ban đêm", "Trời tối đèn tự giảm sáng, sáng ra trở lại. Không cần đấu dây", S.nightOn.value) {
            S.nightOn.value = it; S.saveSettings(); Night.tick()
        }
        if (S.nightOn.value) {
            S.placeVer.intValue                       // đọc để vẽ lại khi có vị trí
            listOf("Theo giờ mặt trời lặn và mọc (tự tính theo vị trí)", "Theo giờ tự đặt").forEachIndexed { i, label ->
                val on = S.nightMode.intValue == i
                Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { S.nightMode.intValue = i; S.saveSettings(); Night.tick() }, verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(18.dp).border(2.dp, if (on) Pink else Color(0xFFC5CBD9), CircleShape), contentAlignment = Alignment.Center) {
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
                Box(Modifier.height(27.dp).widthIn(min = 52.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFFFED8EF)).border(1.dp, Color(0xFFFF7CC8), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                    T("${S.nightBri.intValue}%", 14, FontWeight.ExtraBold, DeepPink)
                }
            }
            SkSlider((S.nightBri.intValue - 5) / 95f, { Night.setLevel(5 + (it * 19).roundToInt() * 5) }, Pink, Color(0xFFFED8EF), Pink,
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 6.dp), 8.dp, 21.dp)
            val w = Night.window()
            val noPlace = S.nightMode.intValue == 0 && Night.sunToday() == null
            T(
                (if (noPlace) "Chưa có vị trí của máy, tạm dùng giờ tự đặt: " else "Hôm nay dịu đèn ") + "từ ${Night.hm(w.first)} đến ${Night.hm(w.second)}. Ban đêm chỉnh độ sáng tay thì app nhớ mức đó cho ban đêm.",
                11, color = Grey, modifier = Modifier.padding(top = 6.dp, bottom = 10.dp)
            )
        }
        Divider()
        SwitchRow("Màn hình chờ khi xe chạy", "Xe đang chạy và 10 giây không chạm thì chuyển sang màn hình chờ", S.idleOn.value) { S.idleOn.value = it; S.saveSettings() }
        T("Kiểu màn hình chờ", 13, FontWeight.Bold, SubTxt, Modifier.padding(top = 2.dp, bottom = 8.dp))
        Grid(IDLE_STYLES.indices.toList(), 2) { i, m -> Chip(IDLE_STYLES[i], S.idleStyle.intValue == i, m, 44.dp, 12) { S.idleStyle.intValue = i; S.saveSettings() } }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 10.dp, bottom = 10.dp)) {
            if (S.idleStyle.intValue == 3) OutlineBtn("Chọn ảnh", Modifier.weight(1f)) { try { pick.launch("image/*") } catch (e: Exception) { Toast.makeText(ctx, "Máy không có trình chọn ảnh.", Toast.LENGTH_SHORT).show() } }
            OutlineBtn("Xem thử", Modifier.weight(1f)) { S.idleShow.value = true }
        }
        Divider()
        SwitchRow("Nút nổi trên màn hình", "Nút SK nổi trên ứng dụng khác: bật tắt, độ sáng, bối cảnh. Cần cấp quyền hiển thị trên ứng dụng khác", S.floatOn.value) {
            S.floatOn.value = it; S.saveSettings(); S.syncService(ctx)
            if (it && !Settings.canDrawOverlays(ctx)) {
                Toast.makeText(ctx, "Bật quyền \"Hiển thị trên ứng dụng khác\" cho SK Light rồi quay lại.", Toast.LENGTH_LONG).show()
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

@Composable
private fun Big(text: String, size: Float) {
    Text(text, fontSize = size.sp, lineHeight = size.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, fontFamily = Inter, maxLines = 1)
}

private fun dirIcon(d: String): ImageVector = when (d) {
    "L" -> Icons.Filled.TurnLeft; "R" -> Icons.Filled.TurnRight; "U" -> Icons.Filled.UTurnLeft; "O" -> Icons.Filled.RoundaboutRight; "A" -> Icons.Filled.Flag; else -> Icons.Filled.Straight
}
private fun dirText(d: String) = when (d) { "L" -> "Rẽ trái"; "R" -> "Rẽ phải"; "U" -> "Quay đầu"; "O" -> "Vòng xuyến"; "A" -> "Đến nơi"; "S" -> "Đi thẳng"; else -> "" }

@Composable
fun IdleScreen(wide: Boolean) {
    val ctx = LocalContext.current
    val cfg = LocalConfiguration.current
    val view = LocalView.current
    DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        var n = 0
        while (true) { now = System.currentTimeMillis(); if (n % 3 == 0) Media.refresh(ctx); n++; delay(1000) }
    }
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val time = "%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    val date = DAYS[cal.get(Calendar.DAY_OF_WEEK) - 1] + ", %02d/%02d".format(cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1)
    val style = S.idleStyle.intValue
    val photo = remember(S.idlePhotoVer.intValue, style) { if (style == 3) loadPhoto(ctx) else null }
    val uni = S.czone.intValue == 0
    val fx = S.mode.intValue != 0                       // đang chạy hiệu ứng nhiều màu: dùng hai màu nhấn cố định
    val cL = if (!S.on.value) Color(0xFF3A4050) else if (fx) Color(0xFFFE2B48) else rgbColor(if (uni) S.col[0] else S.col[1])
    val cR = if (!S.on.value) Color(0xFF3A4050) else if (fx) Color(0xFF4E78FF) else rgbColor(if (uni) S.col[0] else S.col[2])
    val exit = { S.idleShow.value = false; S.lastTouch = System.currentTimeMillis() }
    Box(Modifier.fillMaxSize().background(Color(0xFF0D0F15)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = exit)) {
        if (photo != null) {
            Image(photo.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Color(0x73000000)))
        } else Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension * 0.8f
            val a = Offset(0f, size.height * 0.6f)
            val b = Offset(size.width, size.height * 0.6f)
            drawCircle(Brush.radialGradient(listOf(cL.copy(alpha = 0.55f), Color.Transparent), a, r), r, a)
            drawCircle(Brush.radialGradient(listOf(cR.copy(alpha = 0.55f), Color.Transparent), b, r), r, b)
        }
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = if (wide) 40.dp else 20.dp, vertical = 22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.sk_logo), "SK Ambient Light", Modifier.height(if (wide) 34.dp else 26.dp), contentScale = ContentScale.Fit)
                Spacer(Modifier.width(14.dp))
                T(date, if (wide) 16 else 13, FontWeight.SemiBold, DimTxt, Modifier.weight(1f))
                Box(
                    Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).border(1.dp, Color(0x59FFFFFF), RoundedCornerShape(22.dp)).clickable(onClick = exit).padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) { T(if (wide) "Về trang điều khiển" else "Điều khiển", 14, FontWeight.Bold, Color.White) }
            }
            val big = if (wide) cfg.screenHeightDp * 0.27f else cfg.screenWidthDp * 0.26f
            val spd = S.speedKmh.floatValue.roundToInt()
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when {
                    style == 1 || style == 3 -> Big(time, big * 1.2f)
                    style == 2 -> Column(horizontalAlignment = Alignment.CenterHorizontally) { Big("$spd", big * 1.2f); T("km/h", 20, FontWeight.SemiBold, DimTxt) }
                    wide -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Big(time, big)
                        Column(horizontalAlignment = Alignment.End) { Big("$spd", big * 0.72f); T("km/h", 20, FontWeight.SemiBold, DimTxt) }
                    }
                    else -> Column(horizontalAlignment = Alignment.CenterHorizontally) { Big(time, big); Spacer(Modifier.height(10.dp)); Big("$spd", big * 0.62f); T("km/h", 18, FontWeight.SemiBold, DimTxt) }
                }
            }
            if (S.navOn.value) {
                val d = S.navDist.intValue
                val dist = if (d < 0) "" else if (d >= 1000) "${d / 100 / 10.0} km · " else "$d m · "
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                    Icon(dirIcon(S.navDir.value), null, tint = Color.White, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(dist + dirText(S.navDir.value) + (if (S.navStreet.value.isEmpty()) "" else " · " + S.navStreet.value), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = Inter, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (S.song.value.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                    Icon(Icons.Filled.MusicNote, null, tint = DimTxt, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(S.song.value, fontSize = 16.sp, color = DimTxt, fontFamily = Inter, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
