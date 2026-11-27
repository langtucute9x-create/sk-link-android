package vn.sklight.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Liên kết SK Driver (app cảnh báo khoảng cách, gói vn.sklight.driver, cùng máy).
 * SK Driver gửi broadcast đích danh khoảng 5 lần mỗi giây: level (0 không có vật, 1 an toàn, 2 gần, 3 quá gần, 4 chưa đủ tốc độ),
 * dist (mét), cam (lúc bắt đầu xử lý khung hình), sent (lúc gửi); hai mốc thời gian theo đồng hồ elapsedRealtime dùng chung của máy.
 * SK Light chỉ nháy đèn theo mức nhận được (loại cảnh báo 8 = quá gần, 9 = gần trong AlertBridge); mọi tính toán khoảng cách nằm ở SK Driver.
 */
object DriverLink {
    const val PKG = "vn.sklight.driver"
    val level = mutableIntStateOf(0)
    val dist = mutableFloatStateOf(-1f)
    val lastAt = mutableLongStateOf(0L)            // lúc nhận tín hiệu gần nhất (elapsedRealtime); 0 = chưa nhận lần nào
    val hopMs = mutableIntStateOf(-1)              // SK Driver gửi -> SK Light nhận (làm mượt)
    val camMs = mutableIntStateOf(-1)              // camera bắt đầu xử lý khung hình -> SK Light nhận (làm mượt)
    private var lastKind = 0

    /** Có tín hiệu trong 3 giây gần đây. */
    fun fresh(now: Long = SystemClock.elapsedRealtime()) = lastAt.longValue != 0L && now - lastAt.longValue <= 3000

    /** Loại cảnh báo đang cần chạy theo SK Driver: 8 quá gần, 9 gần, 0 không. */
    fun kind(): Int {
        if (!S.drvOn.value || lastAt.longValue == 0L) return 0
        val age = SystemClock.elapsedRealtime() - lastAt.longValue
        if (age > (if (S.drvLostOff.value) 3000 else 30000)) return 0
        return when (level.intValue) { 3 -> 8; 2 -> 9; else -> 0 }
    }

    fun onSignal(lv: Int, d: Float, cam: Long, sent: Long) {
        val now = SystemClock.elapsedRealtime()
        level.intValue = lv.coerceIn(0, 4); dist.floatValue = d; lastAt.longValue = now
        fun mix(old: Int, v: Long): Int { val x = v.coerceIn(0L, 5000L).toInt(); return if (old < 0) x else (old * 3 + x + 2) / 4 }
        if (sent in 1..now) hopMs.intValue = mix(hopMs.intValue, now - sent)
        if (cam in 1..now) camMs.intValue = mix(camMs.intValue, now - cam)
        val k = kind()
        if (k != lastKind) { lastKind = k; AlertBridge.refresh() }
    }

    /** Công tắc trong trang Khoảng cách vừa đổi: xét lại cảnh báo ngay. */
    fun changed() { lastKind = kind(); AlertBridge.refresh() }

    fun installed(c: Context): Boolean = try { c.packageManager.getPackageInfo(PKG, 0); true } catch (_: Throwable) { false }
    fun open(c: Context) { try { c.packageManager.getLaunchIntentForPackage(PKG)?.let { c.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } } catch (_: Throwable) {} }
}

/** Nhận tín hiệu của SK Driver. Khai báo trong manifest kèm quyền chữ ký: chỉ app ký cùng khóa với SK Light mới gửi tới được. */
class DriverReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        try {
            S.init(c)
            DriverLink.onSignal(i.getIntExtra("level", 0), i.getFloatExtra("dist", -1f), i.getLongExtra("cam", 0L), i.getLongExtra("sent", 0L))
        } catch (_: Throwable) {}
    }
}

// ===================== TRANG KHOẢNG CÁCH (SK DRIVER) =====================
/** Trạng thái SK Driver: có tín hiệu không, mức, số mét, độ trễ đo được. */
@Composable
fun DriverTop() {
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var inst by remember { mutableStateOf(DriverLink.installed(ctx)) }
    LaunchedEffect(Unit) { var n = 0; while (true) { delay(500); now = SystemClock.elapsedRealtime(); if (++n % 6 == 0) inst = DriverLink.installed(ctx) } }
    val seen = DriverLink.lastAt.longValue != 0L
    val live = seen && now - DriverLink.lastAt.longValue <= 3000
    val lv = if (live) DriverLink.level.intValue else 0
    val red = Color(0xFFE5484D)
    if (inst) { DriverMirror(); Spacer(Modifier.height(10.dp)) }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(if (live) OkGreen else if (seen) red else Color(0xFFB8BCC6)))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                T("SK Driver", 16, FontWeight.ExtraBold)
                T(if (live) "Đang nhận tín hiệu" else if (seen) "Mất tín hiệu, đèn đã về như cũ" else if (inst) "Chưa nhận tín hiệu. Mở SK Driver để bắt đầu." else "Máy này chưa cài app SK Driver.", 12, color = Grey)
            }
            val (bg, fg, name) = when (lv) {
                3 -> Triple(mix(red, 0.16f), red, "Quá gần")
                2 -> Triple(mix(Color(0xFFF59E0B), 0.2f), Color(0xFFB45309), "Gần")
                1 -> Triple(mix(OkGreen, 0.16f), OkGreen, "An toàn")
                4 -> Triple(tint(0.12f), Grey, "Chưa đủ tốc độ")
                else -> Triple(tint(0.12f), Grey, if (live) "Không có xe trước" else "Không có tín hiệu")
            }
            Box(Modifier.height(28.dp).clip(RoundedCornerShape(14.dp)).background(bg).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) { T(name, 12, FontWeight.ExtraBold, fg) }
        }
        Spacer(Modifier.height(10.dp))
        val d = DriverLink.dist.floatValue
        Row(verticalAlignment = Alignment.Bottom) {
            T(if (live && d > 0f && lv != 0) d.toInt().toString() else "--", 46, FontWeight.ExtraBold, if (lv == 3) red else Txt)
            Spacer(Modifier.width(6.dp))
            T("m", 18, FontWeight.Bold, Grey, Modifier.padding(bottom = 8.dp))
        }
        Spacer(Modifier.height(10.dp))
        T("Độ trễ tín hiệu (đo trên máy này)", 13, FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        LagRow("SK Driver gửi sang SK Light", if (live && DriverLink.hopMs.intValue >= 0) "${DriverLink.hopMs.intValue} ms" else "--")
        LagRow("Từ lúc camera xử lý khung hình tới SK Light", if (live && DriverLink.camMs.intValue >= 0) "${DriverLink.camMs.intValue} ms" else "--")
        Spacer(Modifier.height(4.dp))
        Hint("Chưa gồm thời gian SK Light gửi lệnh xuống hộp đèn qua Bluetooth: app không đo được đoạn này, ước khoảng 40 đến 100 ms.", 12)
        if (inst && !live) {
            Spacer(Modifier.height(10.dp))
            FillBtn("Mở SK Driver", Icons.Filled.DirectionsCar, Pink, Modifier.fillMaxWidth()) { DriverLink.open(ctx) }
        }
    }
}

@Composable
private fun LagRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 30.dp)) {
        T(label, 13, modifier = Modifier.weight(1f))
        T(value, 14, FontWeight.ExtraBold, Pink)
    }
}

/** Cài đặt nháy đèn theo SK Driver: công tắc chung, rồi màu / khu nháy / kiểu của từng mức. */
@Composable
fun DriverBottom() {
    Card {
        GroupTitle(Icons.Filled.NotificationsNone, "Cảnh báo khoảng cách", 16)
        Spacer(Modifier.height(4.dp))
        SwitchRow("Nháy đèn khi SK Driver báo", "Đèn đổi theo mức Gần và Quá gần do app SK Driver gửi sang.", S.drvOn.value) { S.drvOn.value = it; S.saveSettings(); DriverLink.changed() }
        SwitchRow("Nhường cảnh báo rẽ", "Đang báo rẽ, báo biển hoặc quá tốc độ thì mức Gần chờ. Mức Quá gần luôn chen vào.", S.drvYield.value, S.drvOn.value) { S.drvYield.value = it; S.saveSettings(); DriverLink.changed() }
        SwitchRow("Tự trả đèn về cũ khi mất tín hiệu", "Sau 3 giây không nhận được tín hiệu. Tắt thì đèn giữ cảnh báo cuối tối đa 30 giây.", S.drvLostOff.value, S.drvOn.value) { S.drvLostOff.value = it; S.saveSettings(); DriverLink.changed() }
        Spacer(Modifier.height(2.dp))
        T("SK Light chỉ đổi đèn theo tín hiệu nhận được. Khoảng cách do SK Driver ước lượng bằng camera, là hỗ trợ tham khảo, không phải hệ thống an toàn.", 12, color = Grey)
    }
    DriverLevel(8, "Mức Quá gần", "Xe trước ở dưới khoảng cách tối thiểu.")
    DriverLevel(9, "Mức Gần", "Còn trong 5 m trên khoảng cách tối thiểu.")
    Card { T("Mức An toàn và Chưa đủ tốc độ: đèn giữ nguyên.", 12, color = Grey) }
}

@Composable
private fun DriverLevel(kind: Int, title: String, sub: String) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(rgbColor(AlertBridge.showOf(kind))))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) { T(title, 15, FontWeight.ExtraBold); Hint(sub, 12) }
        }
        Spacer(Modifier.height(10.dp))
        ColorPick(kind)
        Spacer(Modifier.height(10.dp))
        ZonePick(kind)
        Spacer(Modifier.height(10.dp))
        T("Kiểu nháy", 13, FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Chớp", "Thở").forEachIndexed { i, name -> Chip(name, S.drvStyle[kind - 8] == i, Modifier.weight(1f), 44.dp, 12) { S.drvStyle[kind - 8] = i; S.saveSettings() } }
        }
        Spacer(Modifier.height(12.dp))
        FillBtn("Thử nháy 4 giây", Icons.Filled.PlayArrow, Pink, Modifier.fillMaxWidth()) { AlertBridge.test(kind) }
    }
}
