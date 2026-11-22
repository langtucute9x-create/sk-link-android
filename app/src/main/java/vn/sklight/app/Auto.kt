package vn.sklight.app

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableIntStateOf
import kotlin.math.cos
import kotlin.random.Random

/**
 * Chế độ tự tạo chạy bằng app: app gửi lệnh màu (01) liên tục, hộp không biết gì về các chế độ này.
 * [main] = id chế độ (>= 100) điều khiển cả hai khu; [z2] = kiểu riêng cho khu vực 2 (0 không dùng, 1 thở, 2 chớp, 3 cầu vồng, 4 ngược màu khu 1).
 * Dừng khi chọn chế độ/màu khác, mất kết nối hoặc có cảnh báo đang chạy (cảnh báo được ưu tiên). Không lưu qua lần mở app sau.
 */
object Auto {
    val main = mutableIntStateOf(0)
    val z2 = mutableIntStateOf(0)
    val Z2_NAMES = listOf("Màu thở", "Màu chớp", "Màu cầu vồng", "Ngược màu khu 1")
    private val h = Handler(Looper.getMainLooper())
    private var t0 = 0L
    private val tick = object : Runnable { override fun run() { step() } }

    fun running() = main.intValue != 0 || z2.intValue != 0

    /** Bắt đầu chế độ tự tạo [id]: đưa hộp về Đơn sắc để lệnh màu có tác dụng. */
    fun startMain(id: Int) {
        z2.intValue = 0
        main.intValue = id
        if (S.connected.value) S.ble.send(Protocol.effect(S.sens.intValue, S.m1, 0, 0, S.speed.intValue), 3, true)
        kick()
    }

    fun startZ2(k: Int) {
        if (main.intValue != 0) { main.intValue = 0; if (S.mode.intValue >= 100) S.mode.intValue = 0 }
        z2.intValue = k
        kick()
    }

    private fun kick() { t0 = System.currentTimeMillis(); h.removeCallbacks(tick); h.post(tick) }

    /** Rời chế độ tự tạo vì sắp gửi lệnh khác (không gửi lại màu). */
    fun leave() {
        h.removeCallbacks(tick)
        main.intValue = 0; z2.intValue = 0
    }

    /** Dừng và trả đèn về màu/hiệu ứng đang đặt. */
    fun stop() {
        val was = running()
        leave()
        if (!was) return
        if (S.mode.intValue >= 100) S.mode.intValue = 0
        try { if (S.connected.value) { S.ble.send(S.colorFrame(), 2, true); if (S.mode.intValue != 0) S.sendEffect() } } catch (_: Exception) {}
    }

    private fun base1() = if (S.czone.intValue == 0) S.col[0] else S.col[1]
    private fun base2() = if (S.czone.intValue == 0) S.col[0] else S.col[2]

    private fun step() {
        if (!running()) return
        if (!S.connected.value) { leave(); return }
        val el = System.currentTimeMillis() - t0
        val sp = 0.4 + S.speed.intValue * 0.2
        val t = (el * sp).toLong()
        var a = base1(); var b = base2(); var uni = false
        val m = main.intValue
        if (m != 0) {
            when (m) {
                100 -> { val hue = (t / 25.0) % 360; a = hsv(hue, 1.0); b = hsv(hue + 120, 1.0) }
                101 -> { val f = wave(t, 4000); a = scale(base1(), 0.05f + 0.95f * f); b = scale(base2(), 0.05f + 0.95f * f) }
                102 -> { val f = wave(t, 4000); a = scale(base1(), 0.05f + 0.95f * f); b = scale(base2(), 0.05f + 0.95f * (1 - f)) }
                103 -> { a = scale(0xFF5A00, 0.35f + 0.65f * Random.nextFloat()); b = scale(0xFF3C00, 0.35f + 0.65f * Random.nextFloat()) }
                104 -> { val p = (t / 350) % 2; a = if (p == 0L) 0xFF0000 else 0; b = if (p == 0L) 0 else 0x0000FF }
                105 -> { val v = if (Random.nextFloat() < 0.12f) 0xFFFFFF else 0x0A0A28; a = v; b = v; uni = true }
                106 -> { val f = wave(t, 8000); a = mix(0x0040FF, 0x00FFD0, f); b = mix(0x00FFD0, 0x0040FF, f) }
                107 -> {
                    val cols = intArrayOf(0xFF6A00, 0xFF2CAA, 0x8E5CFF)
                    val p = (t % 9000).toInt(); val i = p / 3000; val i2 = ((p + 4500) % 9000) / 3000
                    a = mix(cols[i], cols[(i + 1) % 3], (p % 3000) / 3000f)
                    val p2 = (p + 4500) % 9000
                    b = mix(cols[i2], cols[(i2 + 1) % 3], (p2 % 3000) / 3000f)
                }
            }
        } else {
            when (z2.intValue) {
                1 -> b = scale(base2(), 0.06f + 0.94f * wave(t, 3000))
                2 -> b = if (t % 1000L < 500L) base2() else 0
                3 -> b = hsv((t / 25.0) % 360, 1.0)
                4 -> { val x = base1(); b = ((255 - ((x shr 16) and 255)) shl 16) or ((255 - ((x shr 8) and 255)) shl 8) or (255 - (x and 255)) }
            }
        }
        if (S.alertKind.intValue == 0) S.ble.send(Protocol.color(uni, a, b, S.invert.value), 2, true)
        h.postDelayed(tick, 200)
    }

    private fun wave(t: Long, period: Int): Float = (0.5 - 0.5 * cos(2 * Math.PI * (t % period) / period)).toFloat()
    private fun ch(x: Int, y: Int, f: Float) = (x + (y - x) * f).toInt().coerceIn(0, 255)
    private fun mix(x: Int, y: Int, f: Float): Int =
        (ch((x shr 16) and 255, (y shr 16) and 255, f) shl 16) or (ch((x shr 8) and 255, (y shr 8) and 255, f) shl 8) or ch(x and 255, y and 255, f)
    private fun scale(x: Int, f: Float) = mix(0, x, f)
    private fun hsv(h: Double, s: Double): Int {
        val c = android.graphics.Color.HSVToColor(floatArrayOf(((h % 360 + 360) % 360).toFloat(), s.toFloat(), 1f))
        return c and 0xFFFFFF
    }
}
