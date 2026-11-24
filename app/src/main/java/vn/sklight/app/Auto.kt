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
    /** id chế độ = SEQ_BASE + vị trí trong Seqs.list (chuỗi màu người dùng tự đặt). */
    const val SEQ_BASE = 200
    val Z2_NAMES = listOf("Màu thở", "Màu chớp", "Màu cầu vồng", "Ngược màu khu 1")
    private val h = Handler(Looper.getMainLooper())
    private var t0 = 0L
    private val tick = object : Runnable { override fun run() { step() } }

    fun running() = main.intValue != 0 || z2.intValue != 0
    private var wasConn = false

    /** Chế độ lấy màu đang chọn làm gốc: đổi màu thì chế độ vẫn chạy với màu mới. */
    fun usesBase() = if (main.intValue != 0) main.intValue == 101 || main.intValue == 102 || main.intValue == 114 else z2.intValue == 1 || z2.intValue == 2 || z2.intValue == 4

    /** Bắt đầu chế độ tự tạo [id] (điều khiển cả hai khu): đưa hộp về Đơn sắc để lệnh màu có tác dụng. Kiểu vùng 2 đang chọn vẫn được nhớ, chạy lại khi rời chế độ này. */
    fun startMain(id: Int) {
        main.intValue = id
        if (S.connected.value) S.ble.send(Protocol.effect(S.sens.intValue, S.m1, 0, 0, S.speed.intValue), 3, true)
        kick()
    }

    /**
     * Chọn kiểu riêng cho khu vực 2. Kiểu này được nhớ (cả khi đổi chế độ, đổi màu, tắt mở app) cho tới khi chọn lại Tĩnh / Tự động / Nhấp nháy của hộp.
     * Chế độ màu vùng 2 của hộp phải về Tĩnh, nếu không hộp sẽ đè lên màu app gửi.
     */
    fun startZ2(k: Int) {
        if (main.intValue != 0) { main.intValue = 0; if (S.mode.intValue >= 100) S.mode.intValue = 0 }
        if (S.z2.intValue != 0) S.setZ2(0)
        z2.intValue = k
        S.saveSettings()
        kick()
    }

    /** Bỏ kiểu vùng 2 của app (người dùng chọn lại kiểu của hộp, hoặc bối cảnh đặt kiểu của hộp). [send] = gửi lại màu đang đặt. */
    fun clearZ2(send: Boolean = true) {
        if (z2.intValue == 0) return
        z2.intValue = 0
        S.saveSettings()
        if (main.intValue != 0) return
        h.removeCallbacks(tick)
        try { if (send && S.connected.value) S.ble.send(S.colorFrame(), 2, true) } catch (_: Exception) {}
    }

    /** Gọi lúc mở app: kiểu vùng 2 đã nhớ thì chạy lại (tự chờ tới khi kết nối xong). */
    fun resume() { if (running()) kick() }

    private fun kick() { t0 = System.currentTimeMillis(); wasConn = S.connected.value; h.removeCallbacks(tick); h.post(tick) }

    /** Rời chế độ tự tạo cả xe vì sắp gửi lệnh khác (không gửi lại màu). Kiểu vùng 2 đã chọn vẫn chạy tiếp. */
    fun leave() {
        main.intValue = 0
        if (z2.intValue == 0) h.removeCallbacks(tick)
    }

    private fun base1() = if (S.czone.intValue == 0) S.col[0] else S.col[1]
    private fun base2() = if (S.czone.intValue == 0) S.col[0] else S.col[2]

    private fun step() {
        if (!running()) return
        // mất kết nối hoặc đang tắt đèn: giữ chế độ, chờ rồi chạy tiếp
        if (!S.connected.value) { wasConn = false; h.postDelayed(tick, 1000); return }
        if (!wasConn) {          // vừa nối lại: hộp có thể đang ở hiệu ứng khác
            wasConn = true
            if (main.intValue != 0) S.ble.send(Protocol.effect(S.sens.intValue, S.m1, 0, 0, S.speed.intValue), 3)
        }
        if (!S.on.value) { h.postDelayed(tick, 500); return }
        if (System.currentTimeMillis() < S.syncUntil) { h.postDelayed(tick, 300); return }      // vừa kết nối: chờ app đọc xong trạng thái của hộp
        val el = System.currentTimeMillis() - t0
        val sp = 0.4 + S.speed.intValue * 0.2
        val t = (el * sp).toLong()
        var a = base1(); var b = base2(); var uni = false
        var next = 200L                                    // nhịp gửi kế tiếp (ms)
        val m = main.intValue
        if (m >= SEQ_BASE) {
            // chuỗi màu người dùng tự đặt
            val q = if (m == SEQ_BASE + 99) Seqs.preview else Seqs.list.getOrNull(m - SEQ_BASE)
            if (q == null || q.colors.size < 2) { main.intValue = 0; S.mode.intValue = 0; if (z2.intValue != 0) h.postDelayed(tick, 200); return }
            val n = q.colors.size
            val d = if (q.smooth) 1800.0 else 700.0        // thời gian mỗi màu ở tốc độ giữa
            fun at(pos: Double): Int {
                val k = Math.floor(pos).toInt()
                val i0 = ((k % n) + n) % n
                return if (q.smooth) mix(q.colors[i0], q.colors[(i0 + 1) % n], (pos - k).toFloat()) else q.colors[i0]
            }
            val pos = t / d
            a = at(pos)
            b = if (q.split) at(pos + n / 2.0) else a
            uni = !q.split
            if (!q.smooth) next = 100L
        } else if (m != 0) {
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
                    val p = (t % 9000).toInt(); val i = p / 3000
                    a = mix(cols[i], cols[(i + 1) % 3], (p % 3000) / 3000f)
                    val p2 = (p + 4500) % 9000; val i2 = p2 / 3000
                    b = mix(cols[i2], cols[(i2 + 1) % 3], (p2 % 3000) / 3000f)
                }
                108 -> {      // Nến: cam ấm lung linh, mỗi khu trôi về một mức sáng ngẫu nhiên, thỉnh thoảng chao mạnh
                    if (Random.nextFloat() < 0.35f) n1 = if (Random.nextFloat() < 0.1f) 0.35f else 0.7f + 0.3f * Random.nextFloat()
                    if (Random.nextFloat() < 0.35f) n2 = if (Random.nextFloat() < 0.1f) 0.35f else 0.7f + 0.3f * Random.nextFloat()
                    c1 += (n1 - c1) * 0.5f; c2 += (n2 - c2) * 0.5f
                    a = scale(0xFF8A30, c1); b = scale(0xFF9A3C, c2); next = 120L
                }
                109 -> {      // Nhịp tim: hai nhịp đập liền nhau rồi nghỉ
                    val p = (t % 1300).toInt()
                    fun bump(c: Int, w: Int): Float { val x = (p - c).toFloat() / w; return Math.exp((-x * x).toDouble()).toFloat() }
                    val f = maxOf(bump(120, 80), 0.75f * bump(420, 90))
                    val v = scale(0xFF1030, 0.07f + 0.93f * f); a = v; b = v; uni = true; next = 80L
                }
                110 -> { a = mix(0x00FF8A, 0x8A2BFF, wave(t, 9000)); b = mix(0x00D5FF, 0x00FF8A, wave(t + 3000, 11000)) }      // Cực quang
                111 -> {      // Disco: mỗi nhịp đổi sang một cặp màu rực ngẫu nhiên
                    val beat = t / 520
                    if (beat != lastBeat) { lastBeat = beat; val hue = Random.nextInt(360).toDouble(); d1 = hsv(hue, 1.0); d2 = hsv(hue + 90 + Random.nextInt(180), 1.0) }
                    a = d1; b = d2; next = 100L
                }
                112 -> { val f = wave(t, 3200); a = mix(0xFF1A1A, 0xFFC800, f); b = mix(0xFFC800, 0xFF1A1A, f) }      // Tết: đỏ - vàng đổi chỗ mượt
                113 -> {      // Giáng sinh: đỏ - xanh lá đổi chỗ, thỉnh thoảng lóe trắng
                    val p = (t / 900) % 2
                    a = if (p == 0L) 0xFF1A1A else 0x00C040; b = if (p == 0L) 0x00C040 else 0xFF1A1A
                    if (t % 5400 < 150) { a = 0xFFFFFF; b = 0xFFFFFF }
                }
                114 -> {      // Thư giãn: sáng dần 4 giây, tối dần 6 giây theo màu đang chọn (không theo thanh tốc độ)
                    val p = (el % 10000).toInt()
                    val f = if (p < 4000) (0.5 - 0.5 * cos(Math.PI * p / 4000.0)).toFloat() else (0.5 + 0.5 * cos(Math.PI * (p - 4000) / 6000.0)).toFloat()
                    a = scale(base1(), 0.06f + 0.94f * f); b = scale(base2(), 0.06f + 0.94f * f)
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
        // Không đánh dấu là "lệnh đặt": nếu đánh dấu, mọi câu trả lời của hộp bị bỏ qua suốt lúc chế độ này chạy và các trang Cài đặt không đọc được hộp.
        if (S.alertKind.intValue == 0) S.ble.send(Protocol.color(uni, a, b, S.invert.value), 2)
        h.postDelayed(tick, next)
    }

    // trạng thái riêng của vài chế độ
    private var n1 = 1f; private var n2 = 1f; private var c1 = 1f; private var c2 = 1f      // nến
    private var lastBeat = -1L; private var d1 = 0xFF0000; private var d2 = 0x0000FF          // disco

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
