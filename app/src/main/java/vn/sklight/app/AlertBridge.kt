package vn.sklight.app

import android.os.Handler
import android.os.Looper

/** Cảnh báo bằng đèn: nháy bên rẽ khi sắp tới ngã rẽ, nháy đỏ khi quá tốc độ. */
object AlertBridge {
    private val h = Handler(Looper.getMainLooper())
    private var kind = 0          // 0 không, 1 rẽ trái, 2 rẽ phải, 3 quá tốc độ
    private var phase = false
    private var testUntil = 0L
    private var testKind = 0

    @JvmStatic fun onNav(s: NavState) {
        h.post {
            S.navOn.value = true
            S.navDir.value = s.dir.toString()
            S.navDist.intValue = s.distM
            S.navStreet.value = s.street ?: ""
            evaluate()
        }
    }

    @JvmStatic fun onNavOff() { h.post { S.navOn.value = false; S.navDist.intValue = -1; evaluate() } }

    fun onSpeed(kmh: Float) { h.post { S.speedKmh.floatValue = kmh; evaluate() } }

    /** Thử nháy trong 4 giây. */
    fun test(k: Int) {
        testKind = k; testUntil = System.currentTimeMillis() + 4000
        evaluate()
        h.postDelayed({ evaluate() }, 4100)
    }

    private fun wanted(): Int {
        if (System.currentTimeMillis() < testUntil) return testKind
        val lim = S.limit.intValue
        if (lim > 0 && S.speedKmh.floatValue > lim) return 3
        val d = S.navDist.intValue
        if (S.navOn.value && d in 0..S.alertDist.intValue) {
            if (S.navDir.value == "L") return 1
            if (S.navDir.value == "R") return 2
        }
        return 0
    }

    private fun evaluate() {
        val k = wanted()
        if (k == kind) return
        kind = k
        h.removeCallbacks(tick)
        if (k == 0) { S.alertText.value = ""; restore(); return }
        S.alertText.value = when (k) { 1 -> "Sắp rẽ trái"; 2 -> "Sắp rẽ phải"; else -> "Quá tốc độ" }
        if (S.mode.intValue != 0) S.ble.send(Protocol.effect(0, 0, S.speed.intValue), 3)
        phase = false
        h.post(tick)
    }

    private val tick = object : Runnable {
        override fun run() {
            if (kind == 0) return
            phase = !phase
            S.ble.send(frame(kind, phase), 2)
            h.postDelayed(this, if (kind == 3) 250L else 450L)
        }
    }

    private fun base(z: Int): Int = if (S.zone.intValue == 0) S.col[0] else S.col[z]

    private fun frame(k: Int, on: Boolean): ByteArray {
        var c1 = base(1); var c2 = base(2)
        val amber = S.blinkColor.intValue
        val dark = 0x000000
        val left = S.leftZone.intValue
        when (k) {
            1, 2 -> {
                val z = if (k == 1) left else 3 - left
                val v = if (on) amber else dark
                if (z == 1) c1 = v else c2 = v
            }
            3 -> { val v = if (on) 0xFF0000 else dark; c1 = v; c2 = v }
        }
        return Protocol.color(false, c1, c2, S.invert.value)
    }

    private fun restore() {
        if (S.mode.intValue != 0) S.sendEffect() else S.ble.send(S.colorFrame(), 2)
    }
}
