package vn.sklight.app

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.util.regex.Pattern

/**
 * Cảnh báo bằng đèn (bản thử).
 * Rẽ trái / rẽ phải: cho các hộp phụ gắn ở cửa bên đó nháy bằng lệnh "nháy hộp phụ" (04 | hộp << 4 | 0),
 * hết cảnh báo thì gửi lại mã vị trí của hộp. Quá tốc độ: cả xe nháy đỏ bằng lệnh màu.
 * Loại cảnh báo: 0 không, 1 rẽ trái, 2 rẽ phải, 3 quá tốc độ, 12..15 = thử riêng một cửa (10 + đoạn trên sơ đồ).
 */
object AlertBridge {
    private val h = Handler(Looper.getMainLooper())
    private var kind = 0
    private var testUntil = 0L
    private var testKind = 0
    private var flashing = listOf<Int>()          // các hộp phụ đang được cho nháy
    private val SPEED = Pattern.compile("(\\d{2,3})\\s*km\\s*/\\s*h", Pattern.CASE_INSENSITIVE)

    @JvmStatic fun onNav(s: NavState) {
        h.post {
            S.navOn.value = true
            S.navDir.value = s.dir.toString()
            S.navDist.intValue = s.distM
            S.navStreet.value = s.street ?: ""
            S.navApp.value = s.pkg ?: ""
            evaluate()
        }
    }

    @JvmStatic fun onNavOff() { h.post { S.navOn.value = false; S.navDist.intValue = -1; evaluate() } }

    /** Ghi lại thông báo thô của app dẫn đường để xem trong trang Dẫn đường. */
    @JvmStatic fun onRaw(s: NavState) {
        val parts = listOf(s.title, s.text, s.sub, s.big).map { it ?: "" }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return
        val body = parts.joinToString(" | ")
        val line = (s.pkg ?: "") + ": " + body
        val m = SPEED.matcher(body)
        val kmh = if (m.find()) m.group(1)?.toIntOrNull() ?: 0 else 0
        h.post {
            if (S.navLog.firstOrNull() != line) {
                S.navLog.add(0, line)
                while (S.navLog.size > 15) S.navLog.removeAt(S.navLog.size - 1)
            }
            if (kmh > 0) S.signSeen.value = "$kmh km/h (${s.pkg})"
        }
    }

    /** Thử nháy trong 4 giây. */
    fun test(k: Int) {
        if (!S.connected.value) { toast("Chưa kết nối hộp đèn."); return }
        if (k != 3 && boxesFor(k).isEmpty()) {
            toast("Chưa gán hộp phụ nào vào cửa này. Gán ở Sơ đồ xe trong trang Chào mừng trước.")
            return
        }
        testKind = k; testUntil = System.currentTimeMillis() + 4000
        evaluate()
        h.postDelayed({ evaluate() }, 4100)
    }

    /** Các hộp phụ (1..4) gắn ở những cửa thuộc loại cảnh báo [k]. Đoạn trên sơ đồ: 2 trước trái, 3 trước phải, 4 sau trái, 5 sau phải. */
    fun boxesFor(k: Int): List<Int> {
        val segs = when (k) { 1 -> listOf(2, 4); 2 -> listOf(3, 5); in 12..15 -> listOf(k - 10); else -> emptyList() }
        return (1..4).filter { S.boxPos[it - 1] in segs }
    }

    /** Cửa [seg] (2..5) có đang nằm trong cảnh báo hiện tại không. */
    fun lit(seg: Int): Boolean = when (val k = S.alertKind.intValue) {
        1 -> seg == 2 || seg == 4
        2 -> seg == 3 || seg == 5
        3 -> true
        in 12..15 -> seg == k - 10
        else -> false
    }

    private fun wanted(): Int {
        if (System.currentTimeMillis() < testUntil) return testKind
        val d = S.navDist.intValue
        if (S.warnOn.value && S.navOn.value && d in 0..S.alertDist.intValue) {
            if (S.navDir.value == "L") return 1
            if (S.navDir.value == "R") return 2
        }
        return 0
    }

    private fun evaluate() {
        val k = wanted()
        if (k == kind) return
        stop()
        kind = k
        S.alertKind.intValue = k
        if (k == 0) return
        if (k == 3) {
            if (S.mode.intValue != 0) S.ble.send(Protocol.effect(S.sens.intValue, S.z2.intValue, 0, 0, S.speed.intValue), 3)
        } else {
            flashing = boxesFor(k)
            for (n in flashing) S.ble.send(Protocol.door(n, 0))
        }
        S.alertPhase.value = false
        h.post(tick)
    }

    /** Kết thúc cảnh báo đang chạy: trả đèn về như cũ. */
    private fun stop() {
        h.removeCallbacks(tick)
        S.alertPhase.value = false
        if (kind == 3) {
            if (S.mode.intValue != 0) S.sendEffect() else S.ble.send(S.colorFrame(), 2)
        } else {
            for (n in flashing) { val seg = S.boxPos[n - 1]; if (seg >= 2) S.ble.send(Protocol.door(n, seg - 1)) }
        }
        flashing = emptyList()
    }

    private val tick = object : Runnable {
        override fun run() {
            if (kind == 0) return
            val on = !S.alertPhase.value
            S.alertPhase.value = on
            if (kind == 3) {
                val v = if (on) 0xFF0000 else 0x000000
                S.ble.send(Protocol.color(true, v, v, S.invert.value), 2)
            }
            h.postDelayed(this, if (kind == 3) 250L else 450L)
        }
    }

    private fun toast(t: String) = Toast.makeText(S.app, t, Toast.LENGTH_LONG).show()
}
