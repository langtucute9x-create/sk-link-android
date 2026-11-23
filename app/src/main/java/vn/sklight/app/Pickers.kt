package vn.sklight.app

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap

// ===================== HỘP CHỌN HỘP ĐÈN + HỘP CHỌN APP CHIA ĐÔI (mẫu A, chủ dự án chọn 24/11) =====================

/** "Pocket Link CTH2-10" → ("Hộp đèn số 10", là hộp hàm gốc?). Chủ dự án yêu cầu không hiện chữ CTH2 / CZH2. */
private fun friendly(name: String): Pair<String, Boolean> {
    val czh = name.contains("CZH2", true)
    val num = Regex("(?:CTH2|CZH2)[- ]?(\\w+)", RegexOption.IGNORE_CASE).find(name)?.groupValues?.get(1) ?: ""
    return (L.tr(if (czh) "Hộp hàm gốc" else "Hộp đèn") + if (num.isNotEmpty()) " " + L.tr("số") + " " + num else "") to czh
}

/** Mức sóng 1..4 và chữ mô tả. */
private fun level(rssi: Int): Pair<Int, String> = when {
    rssi >= -60 -> 4 to "Rất gần"
    rssi >= -70 -> 3 to "Gần"
    rssi >= -80 -> 2 to "Vừa"
    else -> 1 to "Xa"
}

@Composable
private fun Bars(n: Int, c: Color) {
    Row(Modifier.height(16.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        for (i in 1..4) Box(Modifier.width(4.dp).height((4 * i).dp).clip(RoundedCornerShape(1.dp)).background(if (i <= n) c else Bd))
    }
}

@Composable
private fun Pulse(on: Boolean) {
    val a = Pink
    val t = rememberInfiniteTransition(label = "pulse")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "p")
    Canvas(Modifier.size(34.dp)) {
        val c = center
        if (on && Fx) drawCircle(a.copy(alpha = (1f - p) * 0.6f), radius = size.minDimension * (0.18f + 0.32f * p), center = c)
        drawCircle(if (on) a else Grey, radius = size.minDimension * 0.14f, center = c)
    }
}

/** Mẫu A: bảng trượt từ dưới để tìm và chọn hộp đèn. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevDialog(onClose: () -> Unit) {
    val close = { S.ble.stopScan(); onClose() }
    val list = S.devices.sortedByDescending { it.rssi }
    var sel by remember { mutableStateOf("") }
    val cur = sel.ifEmpty { list.firstOrNull { it.mac == S.savedMac }?.mac ?: list.firstOrNull()?.mac ?: "" }
    ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = CardBg) {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 22.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    T("Tìm hộp đèn", 19, FontWeight.ExtraBold)
                    T(if (S.scanning.value) "Đang quét · bật nguồn xe và đứng gần hộp" else "Đã quét xong", 12, color = Grey)
                }
                Pulse(S.scanning.value)
            }
            Spacer(Modifier.height(10.dp))
            if (list.isEmpty()) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Theme.r(12))).background(ChipBg).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    T(if (S.scanning.value) "Đang tìm hộp đèn…" else "Chưa thấy hộp nào", 14, FontWeight.Bold, align = TextAlign.Center)
                    Spacer(Modifier.height(4.dp))
                    T("Kiểm tra hộp đã có điện, Bluetooth và vị trí của máy đang bật, và không có máy khác đang nối với hộp.", 12, color = Grey, align = TextAlign.Center)
                }
            }
            for (d in list) {
                val (title, czh) = friendly(d.name)
                val (lv, lvTxt) = level(d.rssi)
                val on = d.mac == cur
                val sh = RoundedCornerShape(Theme.r(12))
                Column(
                    Modifier.padding(top = 8.dp).fillMaxWidth().clip(sh).background(if (on) mix(Pink, 0.07f) else CardBg)
                        .border(if (on) 2.dp else 1.dp, if (on) Pink else Bd, sh).clickable { sel = d.mac }.padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).clip(RoundedCornerShape(Theme.r(10))).background(if (czh) ChipBg else tint(0.18f)), contentAlignment = Alignment.Center) {
                            Icon(if (czh) Icons.Filled.DirectionsCar else Icons.Filled.Lightbulb, null, tint = if (czh) SubTxt else Pink, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            T(title, 15, FontWeight.Bold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                T("Pocket Link", 12, color = Grey)
                                if (d.mac == S.savedMac) {
                                    Spacer(Modifier.width(6.dp))
                                    Box(Modifier.clip(CircleShape).background(tint(0.16f)).padding(horizontal = 8.dp, vertical = 1.dp)) { T("Đã dùng trước", 11, FontWeight.SemiBold, AccentText) }
                                }
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) { Bars(lv, Pink); T(lvTxt, 11, color = Grey) }
                    }
                    if (on) {
                        Spacer(Modifier.height(10.dp))
                        FillBtn("Kết nối", Icons.Filled.Link, Pink, Modifier.fillMaxWidth()) { S.userOff = false; S.ble.stopScan(); S.ble.connect(d.mac); onClose() }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.clip(RoundedCornerShape(Theme.r(8))).clickable { S.ble.stopScan(); S.ble.scan() }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Refresh, null, tint = SubTxt, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); T("Quét lại", 13, FontWeight.SemiBold, SubTxt)
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.clip(RoundedCornerShape(Theme.r(8))).clickable { close() }.padding(8.dp)) { T("Đóng", 13, FontWeight.SemiBold, SubTxt) }
            }
        }
    }
}

@Composable
private fun appIcon(pkg: String): ImageBitmap? {
    val ctx = LocalContext.current
    return remember(pkg) { try { ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() } catch (_: Exception) { null } }
}

/** Mẫu A: xem trước chia đôi + hàng app (dùng khi máy chưa tự hiện danh sách app đang mở). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitPicker() {
    if (!Split.picking.value) return
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val allowed = remember(tick) { Split.usageAllowed(ctx) }
    var all by remember { mutableStateOf(false) }
    val list = remember(all, allowed, tick) { Split.apps(ctx, allowed && !all) }
    var sel by remember { mutableStateOf<Split.App?>(null) }
    val cur = sel ?: list.firstOrNull()
    ModalBottomSheet(onDismissRequest = { Split.picking.value = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = CardBg) {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 22.dp).verticalScroll(rememberScrollState())) {
            T("Chia đôi màn hình", 19, FontWeight.ExtraBold)
            T("Chọn app sẽ nằm cạnh SK Light", 12, color = Grey)
            Spacer(Modifier.height(12.dp))
            // xem trước: SK Light một nửa, app đã chọn nửa kia
            Row(Modifier.fillMaxWidth().height(70.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(Theme.r(10))).background(tint(0.16f)), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lightbulb, null, tint = Pink, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); T("SK Light", 13, FontWeight.Bold, AccentText)
                }
                Row(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(Theme.r(10))).background(ChipBg).border(1.dp, Bd, RoundedCornerShape(Theme.r(10))).padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    val ic = cur?.let { appIcon(it.pkg) }
                    if (ic != null) Image(ic, null, Modifier.size(24.dp)) else Icon(Icons.Filled.Apps, null, tint = Grey, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(6.dp))
                    T(cur?.name ?: "Chọn app", 13, FontWeight.Bold, maxLines = 1)
                }
            }
            Spacer(Modifier.height(14.dp))
            T(if (allowed && !all) "Đang mở gần đây" else "Ứng dụng", 13, FontWeight.Bold, SubTxt)
            Spacer(Modifier.height(8.dp))
            if (list.isEmpty()) T("Không tìm thấy app nào.", 13, color = Grey)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list, key = { it.pkg }) { a ->
                    val on = a.pkg == cur?.pkg
                    Column(Modifier.width(74.dp).clip(RoundedCornerShape(Theme.r(10))).clickable { sel = a }.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(54.dp).clip(RoundedCornerShape(Theme.r(14))).border(if (on) 2.dp else 0.dp, if (on) Pink else Color.Transparent, RoundedCornerShape(Theme.r(14))).padding(5.dp), contentAlignment = Alignment.Center) {
                            val ic = appIcon(a.pkg)
                            if (ic != null) Image(ic, a.name, Modifier.fillMaxSize()) else Icon(Icons.Filled.Apps, a.name, tint = Grey)
                        }
                        Spacer(Modifier.height(4.dp))
                        T(a.name, 11, if (on) FontWeight.Bold else FontWeight.Normal, if (on) Txt else SubTxt, align = TextAlign.Center, maxLines = 2)
                    }
                }
            }
            if (!allowed) {
                Spacer(Modifier.height(10.dp))
                Hint("Để chỉ hiện app đang mở, cho SK Light quyền xem mức sử dụng ứng dụng. Bật Hỗ trợ tiếp cận thì máy tự hiện danh sách app đang mở.", 12)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlineBtn("Cho phép xem app đang mở", Modifier.weight(1.6f)) {
                        try { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
                    }
                    OutlineBtn("Kiểm tra lại", Modifier.weight(1f)) { tick++ }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (allowed) Box(Modifier.clip(RoundedCornerShape(Theme.r(8))).clickable { all = !all; sel = null }.padding(8.dp)) {
                    T(if (all) "Chỉ hiện app đang mở" else "Hiện mọi ứng dụng", 13, FontWeight.SemiBold, SubTxt)
                }
                Spacer(Modifier.weight(1f))
                FillBtn("Chia đôi", Icons.Filled.VerticalSplit, Pink, Modifier.widthIn(min = 140.dp)) { cur?.let { Split.open(ctx, it.pkg) } }
            }
        }
    }
}
