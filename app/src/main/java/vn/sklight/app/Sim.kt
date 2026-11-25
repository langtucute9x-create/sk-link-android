package vn.sklight.app

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf

/**
 * Mô phỏng xe chạy trên đường thật để xem đèn báo rẽ / quá tốc độ hoạt động đúng như cài đặt.
 * Không có đường tắt: mô phỏng đưa "khoảng cách tới chỗ rẽ" và "tốc độ" giả vào đúng bộ cảnh báo thật (AlertBridge),
 * nên đèn trên hộp (nếu đã kết nối) chạy y như khi dẫn đường, theo Báo trước bao nhiêu mét và Thời gian đèn chạy.
 */
object Sim {
    val running = mutableStateOf(false)
    val kind = mutableIntStateOf(0)            // 1 rẽ trái, 2 rẽ phải, 3 quá tốc độ
    val dist = mutableFloatStateOf(0f)         // mét tới chỗ rẽ
    val speed = mutableFloatStateOf(0f)        // km/h
    val travelled = mutableFloatStateOf(0f)    // mét đã đi (cho vạch kẻ đường chạy)
    val turn = mutableFloatStateOf(0f)         // 0..1 tiến độ rẽ
    val sec = mutableFloatStateOf(0f)          // giây từ lúc bắt đầu

    const val LIMIT = 50
    private val h = Handler(Looper.getMainLooper())
    private var t0 = 0L
    private var last = 0L
    private var turnAt = -1f
    private var savedLimit = 0
    private var savedApp = -1
    private var startDist = 0f

    private val step = object : Runnable {
        override fun run() {
            if (!running.value) return
            val now = System.currentTimeMillis()
            val dt = ((now - last) / 1000f).coerceIn(0f, 0.2f); last = now
            sec.floatValue = (now - t0) / 1000f
            if (kind.intValue == 3) speedTick(dt) else turnTick(dt)
            h.postDelayed(this, 40)
        }
    }

    private fun move(dt: Float, kmh: Float) { speed.floatValue = kmh; travelled.floatValue += kmh / 3.6f * dt }

    private fun turnTick(dt: Float) {
        if (turnAt < 0f) {
            val d = dist.floatValue
            // chạy 40 km/h, giảm còn 28 km/h khi gần chỗ rẽ
            val v = if (d < 40f) 28f + (d / 40f) * 12f else 40f
            move(dt, v)
            dist.floatValue = d - v / 3.6f * dt
            val s = NavState(); s.on = true; s.dir = if (kind.intValue == 1) 'L' else 'R'; s.distM = dist.floatValue.toInt().coerceAtLeast(0); s.street = "Đường mô phỏng"; s.time = System.currentTimeMillis()
            AlertBridge.simNav(s)
            if (dist.floatValue <= 0f) { dist.floatValue = 0f; turnAt = sec.floatValue; AlertBridge.simOff() }
        } else {
            val p = ((sec.floatValue - turnAt) / 2.2f).coerceIn(0f, 1f)
            turn.floatValue = p
            move(dt, 26f)
            if (p >= 1f && sec.floatValue - turnAt > 3.2f) stop()
        }
    }

    private fun speedTick(dt: Float) {
        // 45 km/h -> vượt lên 68 km/h trong 6 giây -> giữ 6 giây -> giảm về 45 km/h trong 4 giây
        val t = sec.floatValue
        val v = when {
            t < 3f -> 45f
            t < 9f -> 45f + (t - 3f) / 6f * 23f
            t < 15f -> 68f
            t < 19f -> 68f - (t - 15f) / 4f * 23f
            else -> 45f
        }
        move(dt, v)
        S.appSpeed.intValue = Math.round(v)
        AlertBridge.refresh()
        if (t > 22f) stop()
    }

    fun start(k: Int) {
        stop()
        kind.intValue = k
        running.value = true
        turn.floatValue = 0f; turnAt = -1f; sec.floatValue = 0f; travelled.floatValue = 0f
        startDist = (S.alertDist.intValue + 90).toFloat()
        dist.floatValue = startDist
        speed.floatValue = if (k == 3) 45f else 40f
        if (k == 3) { savedLimit = S.speedLimit.intValue; savedApp = S.appSpeed.intValue; S.speedLimit.intValue = LIMIT }
        t0 = System.currentTimeMillis(); last = t0
        h.post(step)
    }

    fun stop() {
        h.removeCallbacks(step)
        if (!running.value) return
        val k = kind.intValue
        running.value = false
        if (k == 3) { S.speedLimit.intValue = savedLimit; S.appSpeed.intValue = savedApp }
        AlertBridge.simOff()
        Limit.update()
        AlertBridge.refresh()
    }

    /** Chỗ rẽ nằm cách bao nhiêu mét vào lúc đèn bắt đầu báo (để vẽ mốc trên thanh tiến trình). */
    fun total() = startDist
}
