package vn.sklight.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sqrt

val Orange = Color(0xFFFF8A00)
val Bg = Color(0xFF090C12)
val CardC = Color(0xFF141A25)
val Green = Color(0xFF46E18C)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        S.init(this)
        try { startForegroundService(Intent(this, DriveService::class.java)) } catch (_: Exception) {}
        setContent { SkTheme { Root() } }
    }
}

@Composable
fun SkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Orange, onPrimary = Color.Black, background = Bg, surface = CardC,
            onSurface = Color(0xFFECF1FA), onBackground = Color(0xFFECF1FA), secondary = Green,
            surfaceVariant = Color(0xFF242C3C)
        ),
        content = content
    )
}

@Composable
fun Root() {
    var tab by remember { mutableIntStateOf(0) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val perms = remember {
        val l = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 31) { l.add(Manifest.permission.BLUETOOTH_SCAN); l.add(Manifest.permission.BLUETOOTH_CONNECT) }
        if (Build.VERSION.SDK_INT >= 33) l.add(Manifest.permission.POST_NOTIFICATIONS)
        l.toTypedArray()
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        try { ctx.startForegroundService(Intent(ctx, DriveService::class.java)) } catch (_: Exception) {}
        S.autoConnect()
    }
    LaunchedEffect(Unit) { launcher.launch(perms) }

    Scaffold(
        containerColor = Bg,
        bottomBar = {
            NavigationBar(containerColor = CardC) {
                val items = listOf("Đèn" to Icons.Filled.Lightbulb, "Hàm gốc" to Icons.Filled.DirectionsCar,
                    "Đường" to Icons.Filled.Navigation, "Cài đặt" to Icons.Filled.Settings)
                items.forEachIndexed { i, (t, ic) ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(ic, t) }, label = { Text(t, fontSize = 12.sp) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Color.Black, indicatorColor = Orange))
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().background(Bg)) {
            when (tab) { 0 -> LedTab(); 1 -> CarTab(); 2 -> NavTab(); else -> SettingsTab() }
        }
    }
}

@Composable
fun Card2(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardC).padding(14.dp), content = content)
}

@Composable
fun Title(t: String) = Text(t, fontSize = 12.sp, color = Color(0xFF808AA0), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))

// ===================== TAB ĐÈN =====================
@Composable
fun LedTab() {
    var pick by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Spacer(Modifier.height(6.dp)) }
        item {
            Card2 {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(if (S.connected.value) Green else Color(0xFF808AA0)))
                    Spacer(Modifier.width(8.dp))
                    Text(S.conn.value, Modifier.weight(1f), fontSize = 15.sp)
                    Button(onClick = { S.ble.scan(); pick = true }) { Icon(Icons.Filled.Bluetooth, null); Spacer(Modifier.width(4.dp)); Text("Chọn đèn") }
                }
            }
        }
        item {
            Card2 {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Bật đèn", Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Switch(checked = S.on.value, onCheckedChange = { S.setPower(it) })
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Đồng nhất", "Khu 1", "Khu 2").forEachIndexed { i, t ->
                        FilterChip(selected = S.zone.intValue == i, onClick = { S.setZoneSel(i) }, label = { Text(t) })
                    }
                }
                Title("ĐỘ SÁNG  ${S.bri[S.zone.intValue]}%")
                Slider(value = S.bri[S.zone.intValue].toFloat(), onValueChange = { S.setBright(it.toInt()) }, valueRange = 0f..100f)
            }
        }
        item {
            Card2 {
                Title("MÀU ĐƠN SẮC")
                ColorWheel(Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(10.dp))
                val presets = listOf(0xFF2A48, 0xFF7A1A, 0xFFE114, 0x41E01F, 0x17F4EA, 0x4E78FF, 0x8A00FF, 0xFFFFFF)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    presets.forEach { c ->
                        Box(Modifier.size(34.dp).clip(CircleShape).background(Color(0xFF000000 or c.toLong())).clickable { S.setColor(c) })
                    }
                }
            }
        }
        item {
            Card2 {
                Title("TỐC ĐỘ HIỆU ỨNG  ${S.speed.intValue}")
                Slider(value = S.speed.intValue.toFloat(), onValueChange = { S.setSpeed(it.toInt()) }, valueRange = 1f..10f, steps = 8)
            }
        }
        for (g in Effects.groups) {
            item { Title(g.name) }
            items(g.items.chunked(2)) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (e in row) EffectCell(e, Modifier.weight(1f))
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
    if (pick) DevDialog { pick = false }
}

@Composable
fun EffectCell(e: Effect, modifier: Modifier) {
    val sel = S.mode.intValue == e.id
    Box(
        modifier.height(48.dp).clip(RoundedCornerShape(12.dp))
            .background(if (sel) Orange else CardC).clickable { S.pickEffect(e) }.padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(e.name, Modifier.weight(1f), color = if (sel) Color.Black else Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (e.subs > 0 && sel) Text("${S.sub[e.id] + 1}/${e.subs}", color = Color.Black, fontSize = 12.sp)
        }
    }
}

@Composable
fun DevDialog(onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = { S.ble.stopScan(); onClose() },
        confirmButton = { TextButton(onClick = { S.ble.stopScan(); onClose() }) { Text("Đóng") } },
        dismissButton = { TextButton(onClick = { S.ble.scan() }) { Text(if (S.scanning.value) "Đang quét…" else "Quét lại") } },
        title = { Text("Chọn hộp đèn") },
        text = {
            Column {
                if (S.devices.isEmpty()) Text(if (S.scanning.value) "Đang tìm CTH2 / CZH2…" else "Không thấy hộp nào. Bật nguồn hộp và quét lại.", color = Color(0xFF808AA0))
                for (d in S.devices.sortedByDescending { it.name.contains("CZH2") }) {
                    Row(Modifier.fillMaxWidth().clickable { S.ble.connect(d.mac); onClose() }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(d.name, fontWeight = FontWeight.SemiBold); Text(d.mac, fontSize = 11.sp, color = Color(0xFF808AA0)) }
                        Text("${d.rssi} dBm", fontSize = 12.sp, color = Color(0xFF808AA0))
                    }
                }
            }
        }
    )
}

@Composable
fun ColorWheel(modifier: Modifier = Modifier) {
    val z = S.zone.intValue
    val size = 220.dp
    val hue = S.hue[z]; val sat = S.sat[z]
    Canvas(
        modifier.size(size)
            .pointerInput(z) { detectTapGestures { o -> wheelPick(o, this.size.width.toFloat()) } }
            .pointerInput(z) { detectDragGestures { ch, _ -> wheelPick(ch.position, this.size.width.toFloat()) } }
    ) {
        val r = min(this.size.width, this.size.height) / 2f
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        val hues = (0..6).map { Color(android.graphics.Color.HSVToColor(floatArrayOf((it * 60f) % 360f, 1f, 1f))) }
        drawCircle(Brush.sweepGradient(hues, c), r, c)
        drawCircle(Brush.radialGradient(listOf(Color.White, Color.Transparent), c, r), r, c)
        val a = Math.toRadians(hue.toDouble())
        val p = Offset(c.x + (kotlin.math.cos(a) * sat * r).toFloat(), c.y + (kotlin.math.sin(a) * sat * r).toFloat())
        drawCircle(Color.Black, 12f, p)
        drawCircle(Color.White, 9f, p)
    }
}

private fun wheelPick(o: Offset, w: Float) {
    val r = w / 2f
    val dx = o.x - r; val dy = o.y - r
    val d = sqrt(dx * dx + dy * dy)
    var h = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
    if (h < 0) h += 360f
    val s = (d / r).coerceIn(0f, 1f)
    val rgb = android.graphics.Color.HSVToColor(floatArrayOf(h, s, 1f)) and 0xFFFFFF
    S.setColor(rgb, h, s)
}

// ===================== TAB HÀM GỐC =====================
@Composable
fun CarTab() {
    val ver = S.blkVer.intValue   // đọc để cập nhật khi hộp trả lời
    val ok = S.blk != null
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Spacer(Modifier.height(6.dp)) }
        item {
            Card2 {
                Text("Hàm gốc và đèn chào mừng", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(if (ok) "Đã đọc từ hộp (v$ver)" else "Chưa đọc được. Kết nối hộp CZH2 rồi bấm Đọc lại.", color = Color(0xFF808AA0), fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = { S.refreshBlocks() }) { Text("Đọc lại từ hộp") }
            }
        }
        if (ok) {
            item { Title("NHẮC NHỞ (suy từ thao tác, cần kiểm chứng)") }
            val sw = listOf(
                Triple("Cảnh báo radar", 0, 0x04), Triple("Nhắc quá tốc độ", 0, 0x01), Triple("Nhắc thắt dây an toàn", 0, 0x20),
                Triple("Nhắc phanh tay", 0, 0x40), Triple("Nhắc cửa", 0, 0x08), Triple("Nhắc bật đèn", 0, 0x80),
                Triple("Đèn xe theo sau", 0, 0x10), Triple("Đèn theo điều hòa", 1, 0x08), Triple("Nhắc flash đôi", 1, 0x10)
            )
            items(sw) { (t, i, m) -> SwitchRow(t, S.blkBit(i, m)) { S.setBlkBit(i, m, it) } }
            item { Title("ĐÈN CHÀO MỪNG") }
            item { SwitchRow("Bật đèn chào mừng", S.blkBit(1, 0x04)) { S.setBlkBit(1, 0x04, it) } }
            item {
                Card2 {
                    val cur = (S.blk!![3] and 0x07)
                    Text("Chế độ chào mừng", fontSize = 15.sp)
                    Spacer(Modifier.height(6.dp))
                    for (row in (0..7).toList().filter { it != 6 }.chunked(4)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (m in row) FilterChip(selected = cur == m, onClick = { S.setBlkField(3, 0x07, m) }, label = { Text(if (m == 7) "Tùy chỉnh" else "${m + 1}") })
                        }
                    }
                }
            }
            item {
                Card2 {
                    Text("Ngưỡng tốc độ nhắc: ${S.blk!![4]} km/h", fontSize = 15.sp)
                    Slider(value = S.blk!![4].toFloat(), onValueChange = { S.setBlkByte(4, it.toInt()) }, valueRange = 40f..150f)
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
fun SwitchRow(t: String, v: Boolean, onChange: (Boolean) -> Unit) {
    Card2 {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t, Modifier.weight(1f), fontSize = 15.sp)
            Switch(checked = v, onCheckedChange = onChange)
        }
    }
}

// ===================== TAB ĐƯỜNG =====================
@Composable
fun NavTab() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Spacer(Modifier.height(6.dp)) }
        item {
            Card2 {
                Text(if (S.navOn.value) "Đang dẫn đường" else "Chưa có dẫn đường", color = if (S.navOn.value) Green else Color(0xFF808AA0), fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                val d = when (S.navDir.value) { "L" -> "⬅ Rẽ trái"; "R" -> "Rẽ phải ➡"; "S" -> "⬆ Đi thẳng"; "U" -> "↩ Quay đầu"; "O" -> "⟳ Vòng xuyến"; "A" -> "Đến nơi"; else -> "—" }
                Text(if (S.navOn.value) d else "—", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(if (S.navDist.intValue >= 0) "${S.navDist.intValue} m" else "", fontSize = 22.sp, color = Orange)
                Text(S.navStreet.value, fontSize = 15.sp)
                if (S.alertText.value.isNotEmpty()) { Spacer(Modifier.height(6.dp)); Text("⚠ " + S.alertText.value, color = Orange, fontWeight = FontWeight.Bold) }
            }
        }
        item {
            Card2 {
                Text("Tốc độ GPS: ${S.speedKmh.floatValue.toInt()} km/h", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Title(if (S.limit.intValue > 0) "GIỚI HẠN ${S.limit.intValue} km/h (nháy đỏ khi vượt)" else "GIỚI HẠN TỐC ĐỘ: TẮT")
                Slider(value = S.limit.intValue.toFloat(), onValueChange = { S.limit.intValue = (it / 5).toInt() * 5; S.saveSettings() }, valueRange = 0f..120f)
            }
        }
        item {
            Card2 {
                Text("Quyền đọc thông báo", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("Cần bật cho SK Light để đọc hướng rẽ từ Google Maps / Vietmap. Máy ColorOS: vào Thông tin ứng dụng → ⋮ → Cho phép cài đặt bị hạn chế trước.", fontSize = 13.sp, color = Color(0xFF808AA0))
                Spacer(Modifier.height(8.dp))
                Button(onClick = { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Mở cài đặt quyền") }
            }
        }
        item {
            Card2 {
                Text("Thử nháy đèn (4 giây)", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { AlertBridge.test(1) }) { Text("Trái") }
                    OutlinedButton(onClick = { AlertBridge.test(2) }) { Text("Phải") }
                    OutlinedButton(onClick = { AlertBridge.test(3) }) { Text("Quá tốc độ") }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ===================== TAB CÀI ĐẶT =====================
@Composable
fun SettingsTab() {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Spacer(Modifier.height(6.dp)) }
        item {
            SwitchRow("Đảo màu RGB (hộp hiện màu bù)", S.invert.value) { S.invert.value = it; S.saveSettings(); S.ble.send(S.colorFrame(), 2) }
        }
        item {
            Card2 {
                Text("Bên trái xe là khu nào", fontSize = 15.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = S.leftZone.intValue == 1, onClick = { S.leftZone.intValue = 1; S.saveSettings() }, label = { Text("Khu 1 = trái") })
                    FilterChip(selected = S.leftZone.intValue == 2, onClick = { S.leftZone.intValue = 2; S.saveSettings() }, label = { Text("Khu 2 = trái") })
                }
            }
        }
        item {
            Card2 {
                Text("Nháy báo rẽ khi còn ≤ ${S.alertDist.intValue} m", fontSize = 15.sp)
                Slider(value = S.alertDist.intValue.toFloat(), onValueChange = { S.alertDist.intValue = (it / 10).toInt() * 10; S.saveSettings() }, valueRange = 50f..500f)
            }
        }
        item {
            Card2 {
                Text("Hộp đã nhớ", fontSize = 15.sp)
                Text(if (S.savedMac.isEmpty()) "Chưa có" else S.savedMac, color = Color(0xFF808AA0), fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { S.autoConnect() }) { Text("Kết nối lại") }
                    OutlinedButton(onClick = { S.ble.disconnect() }) { Text("Ngắt") }
                }
            }
        }
        item { Text("SK Light 0.1.0", color = Color(0xFF808AA0), fontSize = 12.sp, modifier = Modifier.padding(8.dp)) }
    }
}
