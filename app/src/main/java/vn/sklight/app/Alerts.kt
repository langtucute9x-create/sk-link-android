package vn.sklight.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Một mục cảnh báo: công tắc; bật lên thì mở phần chỉnh (màu đèn và các thiết lập riêng). */
@Composable
private fun AlertItem(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit, panel: @Composable ColumnScope.() -> Unit) {
    SwitchRow(title, sub, on, onChange = onChange)
    if (on) Column(
        Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(Theme.r(10))).background(tint(0.08f)).padding(12.dp),
        content = panel
    )
}

@Composable
private fun ColorPick(kind: Int) {
    T("Màu đèn", 13, FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    val sel = S.alertCol[kind]
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
        AlertBridge.PAL_SHOW.forEachIndexed { i, c ->
            Box(
                Modifier.weight(1f).aspectRatio(1f).clip(CircleShape).background(Color(0xFF000000 or c.toLong()))
                    .border(if (i == sel) 3.dp else 1.dp, if (i == sel) Txt else Div, CircleShape)
                    .clickable { S.alertCol[kind] = i; S.saveSettings() }
            )
        }
    }
    Spacer(Modifier.height(4.dp))
    T(AlertBridge.PAL_NAMES[sel.coerceIn(0, AlertBridge.PAL.size - 1)], 12, color = Grey)
}

@Composable
private fun ValueRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
        T(label, 14, FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Box(Modifier.height(27.dp).widthIn(min = 58.dp).clip(RoundedCornerShape(Theme.r(6))).background(tint(0.2f)).border(1.dp, tint(0.6f), RoundedCornerShape(Theme.r(6))).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
            T(value, 14, FontWeight.ExtraBold, Pink)
        }
    }
}

/** Cảnh báo rẽ, quá tốc độ, camera, camera đèn đỏ, biển cấm, giới hạn giảm: một thẻ, bật mục nào mở cài đặt mục đó. */
@Composable
fun AlertsCard() {
    Card {
        GroupTitle(Icons.Filled.NotificationsNone, "Cảnh báo", 16)
        Spacer(Modifier.height(4.dp))

        AlertItem("Báo rẽ", "Đèn chạy theo hướng rẽ.", S.warnOn.value, { S.warnOn.value = it; S.saveSettings(); S.syncService() }) {
            T("Màu đèn báo rẽ là màu của hiệu ứng đã chọn.", 12, color = Grey)
            Spacer(Modifier.height(8.dp))
            SwitchRow("Đảo chiều chạy", "", S.turnFlip.value) { S.turnFlip.value = it; S.saveSettings() }
            T("Hiệu ứng khi báo rẽ", 14, FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            val tf = S.turnFx.intValue.coerceIn(0, AlertBridge.TURN_FX.size - 1)
            Grid(listOf(1, 2, 0), 3) { n, m -> Chip(AlertBridge.TURN_NAMES[n], tf == n, m, 44.dp, 12) { S.turnFx.intValue = n; S.saveSettings() } }
            ValueRow("Tốc độ đèn chạy", "${S.turnSpd[tf]}")
            SkSlider((S.turnSpd[tf] - 1) / 9f, { S.turnSpd[tf] = 1 + (it * 9).roundToInt(); S.saveSettings() }, Pink, tint(0.2f), Pink,
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp), 8.dp, 21.dp, 10, "${S.turnSpd[tf]}")
            ValueRow("Báo trước", "${S.alertDist.intValue} m")
            SkSlider((S.alertDist.intValue - 50) / 250f, { S.alertDist.intValue = 50 + (it * 5).roundToInt() * 50; S.saveSettings() }, Pink, tint(0.2f), Pink,
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp), 8.dp, 21.dp, label = "${S.alertDist.intValue} m")
            Spacer(Modifier.height(10.dp))
            val open = remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                T("Thời gian đèn chạy", 14, FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Box {
                    Row(
                        Modifier.height(34.dp).clip(RoundedCornerShape(Theme.r(8))).background(tint(0.2f)).border(1.dp, tint(0.6f), RoundedCornerShape(Theme.r(8)))
                            .clickable { open.value = true }.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        T(if (S.alertMaxSec.intValue == 0) "Tới khi rẽ xong" else "${S.alertMaxSec.intValue} giây", 14, FontWeight.ExtraBold, Pink)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Filled.ArrowDropDown, null, tint = Pink, modifier = Modifier.size(20.dp))
                    }
                    DropdownMenu(expanded = open.value, onDismissRequest = { open.value = false }) {
                        (listOf(0) + (1..20)).forEach { sec ->
                            DropdownMenuItem(
                                text = { T(if (sec == 0) "Tới khi rẽ xong" else "$sec giây", 14, if (S.alertMaxSec.intValue == sec) FontWeight.ExtraBold else FontWeight.Medium, if (S.alertMaxSec.intValue == sec) Pink else Txt) },
                                onClick = { S.alertMaxSec.intValue = sec; S.saveSettings(); open.value = false }
                            )
                        }
                    }
                }
            }
        }

        AlertItem("Quá tốc độ", "Nháy khi vượt giới hạn tốc độ.", S.speedWarn.value, { S.speedWarn.value = it; S.saveSettings(); S.syncService() }) {
            ColorPick(3)
            Spacer(Modifier.height(10.dp))
            ZonePick(3)
            if (S.dev.value && S.ocrNote.value.isNotEmpty()) T(S.ocrNote.value, 11, color = Grey)
            if (S.dev.value) S.signThumb.value?.let { bmp ->
                Spacer(Modifier.height(6.dp))
                T("Ảnh biển app đang đọc:", 11, color = Grey)
                Image(bitmap = bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.height(90.dp))
            }
            if (S.dev.value) S.arrowThumb.value?.let { bmp ->
                Spacer(Modifier.height(6.dp))
                T("Hình mũi tên rẽ app đang đọc:", 11, color = Grey)
                Image(bitmap = bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.height(60.dp))
            }
        }

        AlertItem("Camera phạt nguội / AI / đo tốc độ", "Báo khi biển camera còn gần.", S.camWarn.value, { S.camWarn.value = it; S.saveSettings(); S.syncService() }) {
            ColorPick(4)
            ValueRow("Báo camera khi còn", "${S.camDist.intValue} m")
            SkSlider((S.camDist.intValue - 100) / 200f, { S.camDist.intValue = 100 + (it * 4).roundToInt() * 50; S.saveSettings() }, Pink, tint(0.2f), Pink,
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp), 8.dp, 21.dp, label = "${S.camDist.intValue} m")
            Spacer(Modifier.height(10.dp))
            ZonePick(4)
        }

        AlertItem("Camera vượt đèn đỏ", "Nháy nhanh.", S.redCamWarn.value, { S.redCamWarn.value = it; S.saveSettings(); S.syncService() }) {
            ColorPick(5); Spacer(Modifier.height(10.dp)); ZonePick(5)
        }

        AlertItem("Biển cấm (cấm vượt...)", "Nháy 2 giây.", S.banWarn.value, { S.banWarn.value = it; S.saveSettings(); S.syncService() }) {
            ColorPick(6); Spacer(Modifier.height(10.dp)); ZonePick(6)
        }

        AlertItem("Giới hạn tốc độ giảm", "Nháy khi giới hạn tụt từ 10 km/h trở lên.", S.limitChgWarn.value, { S.limitChgWarn.value = it; S.saveSettings(); S.syncService() }) {
            ColorPick(7); Spacer(Modifier.height(10.dp)); ZonePick(7)
        }

        Spacer(Modifier.height(2.dp))
        T("Cảnh báo theo biển đọc từ ảnh màn hình nên có thể nhầm hoặc sót, cần thử trên xe thật.", 12, color = Grey)
    }
}

/** Khu nháy của riêng một loại cảnh báo ([kind] 3..7): khu vực 1 (táp lô, cửa), khu vực 2 (gầm chân) hoặc cả xe. */
@Composable
private fun ZonePick(kind: Int) {
    T("Khu nháy", 13, FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((label, z) in listOf("Khu vực 1" to 1, "Khu vực 2" to 2, "Cả xe" to 0))
            Chip(label, S.alertZone[kind] == z, Modifier.weight(1f), 44.dp, 12) { S.alertZone[kind] = z; S.saveSettings() }
    }
}
