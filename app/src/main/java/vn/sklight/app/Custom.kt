package vn.sklight.app

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Science
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.cos

/**
 * Bản thử cho "chế độ tự tạo": app tự gửi lệnh màu liên tục trong 20 giây để xem hộp đèn theo kịp tới đâu.
 * Chỉ dùng lệnh màu (01) có sẵn; chạy xong trả đèn về màu hoặc hiệu ứng đang đặt.
 */
object SeqTest {
    val kind = mutableIntStateOf(0)                 // 0 không chạy, 1 chuyển dần 3 màu, 2 thở một màu, 3 hai khu vực so le, 4 thở riêng khu vực 2, 5 chớp riêng khu vực 2
    val rate = mutableIntStateOf(1)                 // vị trí trong RATES
    val sent = mutableIntStateOf(0)
    val left = mutableIntStateOf(0)                 // giây còn lại
    val c1 = mutableIntStateOf(0)                   // màu đang gửi cho khu vực 1 và 2
    val c2 = mutableIntStateOf(0)
    val RATES = listOf(2, 5, 10, 20)                // số lệnh mỗi giây
    val NAMES = listOf("Chuyển dần 3 màu", "Thở một màu", "Hai khu vực so le", "Thở riêng khu vực 2", "Chớp riêng khu vực 2")
    const val SECS = 20
    private val h = Handler(Looper.getMainLooper())
    private var t0 = 0L
    private val tick = Runnable { step() }

    fun start(k: Int) {
        if (!S.connected.value) { Toast.makeText(S.app, L.tr("Chưa kết nối hộp đèn."), Toast.LENGTH_SHORT).show(); return }
        h.removeCallbacks(tick)
        // kiểu 1..3: đang chạy hiệu ứng thì đưa hộp về Đơn sắc để lệnh màu có tác dụng. Kiểu 4, 5 để nguyên khu vực 1 đang chạy gì thì chạy.
        if (k <= 3 && S.mode.intValue != 0) S.ble.send(Protocol.effect(S.sens.intValue, S.m1, 0, 0, S.speed.intValue), 3)
        kind.intValue = k
        sent.intValue = 0
        left.intValue = SECS
        t0 = System.currentTimeMillis()
        h.post(tick)
    }

    /** Dừng và trả đèn về như trước khi thử. */
    fun stop() {
        h.removeCallbacks(tick)
        if (kind.intValue == 0) return
        kind.intValue = 0
        left.intValue = 0
        try {
            if (S.connected.value) { S.ble.send(S.colorFrame(), 2); if (S.mode.intValue != 0) S.sendEffect() }
        } catch (e: Exception) {}
    }

    private fun step() {
        val k = kind.intValue
        if (k == 0) return
        val el = System.currentTimeMillis() - t0
        if (el >= SECS * 1000L || !S.connected.value) { stop(); return }
        left.intValue = SECS - (el / 1000L).toInt()
        val a: Int; val b: Int; val uni: Boolean
        when (k) {
            1 -> {
                val cols = intArrayOf(0xFF0000, 0x0000FF, 0x00FF00)
                val p = (el % 6000L).toInt()
                val i = p / 2000
                a = mixRgb(cols[i], cols[(i + 1) % 3], (p % 2000) / 2000f); b = a; uni = true
            }
            2 -> { a = scaleRgb(0xFE2CAA, 0.06f + 0.94f * wave(el, 3000)); b = a; uni = true }
            3 -> { val f = wave(el, 3000); a = mixRgb(0xFF0000, 0x0000FF, f); b = mixRgb(0x0000FF, 0xFF0000, f); uni = false }
            else -> {
                // giữ màu khu vực 1, chỉ đổi khu vực 2 quanh màu đang đặt của nó
                val one = S.czone.intValue == 0
                a = if (one) S.col[0] else S.col[1]
                val base = if (one) S.col[0] else S.col[2]
                b = if (k == 4) scaleRgb(base, 0.06f + 0.94f * wave(el, 3000)) else if (el % 1000L < 500L) base else 0
                uni = false
            }
        }
        c1.intValue = a; c2.intValue = b
        if (S.alertKind.intValue == 0) {            // đang có cảnh báo thì nhường
            S.ble.send(Protocol.color(uni, a, b, S.invert.value), 2, true)
            sent.intValue++
        }
        h.postDelayed(tick, (1000 / RATES[rate.intValue.coerceIn(0, RATES.size - 1)]).toLong())
    }

    /** Sóng 0..1..0 với chu kỳ [period] ms. */
    private fun wave(el: Long, period: Int): Float = (0.5 - 0.5 * cos(2 * Math.PI * (el % period) / period)).toFloat()

    private fun ch(x: Int, y: Int, f: Float) = (x + (y - x) * f).toInt().coerceIn(0, 255)
    private fun mixRgb(x: Int, y: Int, f: Float): Int =
        (ch((x shr 16) and 255, (y shr 16) and 255, f) shl 16) or (ch((x shr 8) and 255, (y shr 8) and 255, f) shl 8) or ch(x and 255, y and 255, f)
    private fun scaleRgb(x: Int, f: Float): Int = mixRgb(0, x, f)
}

// ===================== THỬ CHẾ ĐỘ TỰ TẠO (bản thử) =====================
@Composable
fun CustomTestPage() {
    DisposableEffect(Unit) { onDispose { SeqTest.stop() } }
    BackToSettings()
    Card {
        GroupTitle(Icons.Filled.Science, "Thử chế độ tự tạo", 16)
        Spacer(Modifier.height(6.dp))
        T("App tự gửi lệnh đổi màu liên tục trong 20 giây để xem hộp đèn chuyển màu có mượt không. Hết giờ hoặc bấm Dừng thì đèn trở về như cũ.", 12, color = Grey)
        Spacer(Modifier.height(14.dp))
        T("Số lệnh mỗi giây", 13, FontWeight.Bold, SubTxt)
        Spacer(Modifier.height(8.dp))
        Grid(SeqTest.RATES.indices.toList(), 4) { i, m -> Chip("${SeqTest.RATES[i]}", SeqTest.rate.intValue == i, m, 44.dp, 13) { SeqTest.rate.intValue = i } }
        Spacer(Modifier.height(14.dp))
        T("Kiểu thử", 13, FontWeight.Bold, SubTxt)
        Spacer(Modifier.height(8.dp))
        Grid(listOf(1, 2, 3), 3) { k, m -> Chip(SeqTest.NAMES[k - 1], SeqTest.kind.intValue == k, m, 52.dp, 12) { SeqTest.start(k) } }
        Spacer(Modifier.height(14.dp))
        val run = SeqTest.kind.intValue != 0
        T("Màu app đang gửi (trái: khu vực 1, phải: khu vực 2)", 11, color = Grey)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().height(26.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, Bd, RoundedCornerShape(8.dp))) {
            Box(Modifier.weight(1f).fillMaxHeight().background(if (run) rgbColor(SeqTest.c1.intValue) else ChipBg))
            Box(Modifier.weight(1f).fillMaxHeight().background(if (run) rgbColor(SeqTest.c2.intValue) else ChipBg))
        }
        Spacer(Modifier.height(8.dp))
        T(
            if (run) "Đang chạy: còn ${SeqTest.left.intValue} giây, đã gửi ${SeqTest.sent.intValue} lệnh"
            else if (SeqTest.sent.intValue > 0) "Đã dừng. Đã gửi ${SeqTest.sent.intValue} lệnh." else "Chưa chạy.",
            12, FontWeight.SemiBold, SubTxt
        )
        Spacer(Modifier.height(10.dp))
        Chip("Dừng", false, Modifier.fillMaxWidth(), 44.dp, 13) { SeqTest.stop() }
        Spacer(Modifier.height(10.dp))
        T("Thử lần lượt 2, 5, 10, 20 lệnh mỗi giây với từng kiểu. Ghi lại mức nào đèn chuyển mượt, mức nào bị giật, đứng hình hoặc sai màu.", 11, color = Grey)
    }
    Card {
        GroupTitle(Icons.Filled.Science, "Thử riêng khu vực 2", 16)
        Spacer(Modifier.height(10.dp))
        T("App tự điều khiển", 13, FontWeight.Bold, SubTxt)
        Spacer(Modifier.height(6.dp))
        T("App giữ màu khu vực 1, chỉ đổi màu khu vực 2 trong 20 giây. Thử cả lúc khu vực 1 để Đơn sắc và lúc đang chạy một hiệu ứng. Số lệnh mỗi giây lấy theo ô chọn ở thẻ trên.", 12, color = Grey)
        Spacer(Modifier.height(8.dp))
        Grid(listOf(4, 5), 2) { k, m -> Chip(SeqTest.NAMES[k - 1], SeqTest.kind.intValue == k, m, 52.dp, 12) { SeqTest.start(k) } }
        Spacer(Modifier.height(8.dp))
        if (SeqTest.kind.intValue >= 4) { T("Đang chạy: còn ${SeqTest.left.intValue} giây, đã gửi ${SeqTest.sent.intValue} lệnh", 12, FontWeight.SemiBold, SubTxt); Spacer(Modifier.height(8.dp)) }
        Chip("Dừng", false, Modifier.fillMaxWidth(), 44.dp, 13) { SeqTest.stop() }
    }
}
