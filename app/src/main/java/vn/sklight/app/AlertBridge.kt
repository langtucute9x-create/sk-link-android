package vn.sklight.app

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.util.regex.Pattern

/**
 * Cảnh báo bằng đèn, chỉ dùng lệnh màu (01).
 * Sắp rẽ trái / phải: hiệu ứng vàng của hộp (Trôi đa sắc kiểu 3) chạy theo hướng rẽ. Quá tốc độ: khu được chọn (mặc định cả xe) nháy đỏ.
 * Loại cảnh báo: 0 không, 1 rẽ trái, 2 rẽ phải, 3 quá tốc độ, 4 camera, 5 camera đèn đỏ, 6 biển cấm, 7 giới hạn tốc độ giảm.
 */
object AlertBridge {
    private val h = Handler(Looper.getMainLooper())
    private var kind = 0
    private var testUntil = 0L
    private var testKind = 0
    private val SPEED = Pattern.compile("(\\d{2,3})\\s*km\\s*/\\s*h", Pattern.CASE_INSENSITIVE)

    @JvmStatic fun onNav(s: NavState) { if (Sim.running.value) return; applyNav(s) }
    /** Dữ liệu dẫn đường giả của trang Mô phỏng; đi qua đúng bộ cảnh báo thật. */
    fun simNav(s: NavState) = applyNav(s)
    fun simOff() { h.post { S.navOn.value = false; S.navDist.intValue = -1; evaluate() } }

    private fun applyNav(s: NavState) {
        h.post {
            S.navOn.value = true
            S.navDir.value = s.dir.toString()
            S.navDist.intValue = s.distM
            S.navStreet.value = s.street ?: ""
            S.navApp.value = s.pkg ?: ""
            evaluate()
        }
    }

    /** Tính lại cảnh báo khi tốc độ hoặc giới hạn thay đổi. */
    @JvmStatic fun refresh() { h.post { evaluate() } }

    @JvmStatic fun onNavOff() { if (Sim.running.value) return; h.post { S.navOn.value = false; S.navDist.intValue = -1; evaluate() } }

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
        testKind = k; testUntil = System.currentTimeMillis() + 4000
        evaluate()
        h.postDelayed({ evaluate() }, 4100)
    }

    /** Khu [zone] (1 hoặc 2) có đang nháy trong cảnh báo hiện tại không. */
    fun lit(zone: Int): Boolean = when (S.alertKind.intValue) {
        1, 2 -> true
        3, 4, 5, 6, 7 -> S.alertZone[S.alertKind.intValue] == 0 || S.alertZone[S.alertKind.intValue] == zone
        else -> false
    }

    // ---- cảnh báo biển (4-7): nháy theo thời gian ngắn, mỗi loại có thời gian nghỉ riêng ----
    // màu, nhịp nháy (ms), thời lượng (ms), nghỉ giữa hai lần (ms)
    private val EV = mapOf(4 to longArrayOf(0, 400, 6000, 20000), 5 to longArrayOf(0, 160, 5000, 20000),
        6 to longArrayOf(0, 300, 2000, 15000), 7 to longArrayOf(0, 300, 2000, 8000))
    /** Bảng màu đèn cảnh báo, người dùng chọn theo chỉ số. */
    // PAL = giá trị gửi cho hộp: màu thuần, không pha kênh khác (0xFF2D2D trên đèn ra đỏ hồng, chủ dự án báo 25/11). PAL_SHOW = màu vẽ trên màn hình.
    // Đỏ, xanh lá, xanh dương, trắng là màu thuần nên chắc; vàng, cam, tím trên đèn thật chưa kiểm chứng.
    val PAL = intArrayOf(0xFFFF00, 0xFF4000, 0xFF0000, 0x00FF00, 0x0000FF, 0x8C01FC, 0xFFFFFF)
    val PAL_SHOW = intArrayOf(0xFFC400, 0xFF8A00, 0xFF2D2D, 0x22C55E, 0x3B82F6, 0xA855F7, 0xFFFFFF)
    fun showOf(k: Int): Int = PAL_SHOW[S.alertCol[k.coerceIn(0, 7)].coerceIn(0, PAL_SHOW.size - 1)]
    val PAL_NAMES = listOf("Vàng", "Cam", "Đỏ", "Xanh lá", "Xanh dương", "Tím", "Trắng")
    fun colorOf(k: Int): Int = PAL[S.alertCol[k.coerceIn(0, 7)].coerceIn(0, PAL.size - 1)]
    private var evKind = 0
    private var evUntil = 0L
    private val evLast = LongArray(8)
    private fun evOn(k: Int) = Sim.running.value && Sim.kind.intValue == k || when (k) {
        4 -> S.camWarn.value; 5 -> S.redCamWarn.value; 6 -> S.banWarn.value; 7 -> S.limitChgWarn.value; else -> false }
    /** Một biển cảnh báo vừa thấy. Bật công tắc tương ứng thì nháy đèn ngắn. */
    @JvmStatic fun event(k: Int) {
        val e = EV[k] ?: return
        h.post {
            val now = System.currentTimeMillis()
            if (Sim.running.value && Sim.kind.intValue != k) return@post          // đang mô phỏng loại khác
            if (!evOn(k) || now - evLast[k] < e[2] + e[3] || now < evUntil) return@post
            evLast[k] = now; evKind = k; evUntil = now + e[2]
            h.postDelayed({ evaluate() }, e[2] + 60)
            evaluate()
        }
    }
    /** Dừng mô phỏng: xoá cảnh báo biển đang chạy và thời gian nghỉ. */
    fun evClear() { h.post { evUntil = 0; evKind = 0; evLast.fill(0); evaluate() } }

    private var expiredKind = 0                   // bên rẽ đã hết thời gian báo, không báo lại cho tới khi qua chỗ rẽ
    private val expire = Runnable {
        if (System.currentTimeMillis() >= testUntil && (kind == 1 || kind == 2)) { expiredKind = kind; evaluate() }
    }

    private fun wanted(): Int {
        if (System.currentTimeMillis() < testUntil) return testKind
        val d = S.navDist.intValue
        var turn = 0
        // đang mô phỏng thì chỉ xét đúng loại đang mô phỏng: dữ liệu dẫn đường / tốc độ thật không được chen vào
        val simK = if (Sim.running.value) Sim.kind.intValue else 0
        if (simK in 0..2 && (S.warnOn.value || simK != 0) && S.navOn.value && d in 0..S.alertDist.intValue) {
            if (S.navDir.value == "L") turn = 1
            if (S.navDir.value == "R") turn = 2
        }
        if (turn == 0) expiredKind = 0
        if (turn != 0 && turn != expiredKind) return turn
        if (System.currentTimeMillis() < evUntil) return evKind
        // Quá tốc độ: vượt giới hạn quá 3 km/h thì báo, xuống bằng giới hạn thì tắt
        if (simK == 3) return if (Math.round(Sim.speed.floatValue) > (if (kind == 3) Sim.LIMIT else Sim.LIMIT + 3)) 3 else 0
        val lim = S.speedLimit.intValue
        if (simK == 0 && S.speedWarn.value && lim > 0) {
            val v = if (S.appSpeed.intValue >= 0) S.appSpeed.intValue else Math.round(S.speedKmh.floatValue)
            if (v > (if (kind == 3) lim else lim + 3)) return 3
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
        // Không dùng lệnh cấu hình cửa (04) để nháy: nó làm lệch đồng bộ hộp với xe.
        if (k == 1 || k == 2) {
            // Sắp rẽ: chỉ dùng kiểu đèn chạy theo hướng rẽ (đã bỏ kiểu nháy vàng cả xe). Chưa đọc được hướng chạy từ hộp thì hỏi lại rồi thử tiếp.
            S.alertPhase.value = true
            tryFlow(k, 4)
            if (S.alertMaxSec.intValue > 0 && System.currentTimeMillis() >= testUntil) h.postDelayed(expire, S.alertMaxSec.intValue * 1000L)
            return
        }
        // Quá tốc độ: nháy đỏ bằng lệnh màu, đưa hộp về Đơn sắc trước
        if (S.mode.intValue != 0) S.ble.send(Protocol.effect(S.sens.intValue, S.m1, 0, 0, S.speed.intValue), 3)
        S.alertPhase.value = false
        h.post(tick)
    }

    // ---- kiểu "đèn chạy theo hướng rẽ": hiệu ứng Trôi đa sắc kiểu 3 (vàng) của hộp, tốc độ tối đa, đổi hướng chạy theo bên rẽ ----
    /** Hiệu ứng của hộp dùng khi báo rẽ: (id chế độ, tham số). 0 = Trôi đa sắc kiểu 3 (vàng), 1 = Đuôi sao chổi vàng, 2 = Cuộn đủ màu kiểu 3. */
    val TURN_FX = listOf(10 to 3, 13 to 2, 12 to 3)
    /** Tên hiện trên app, theo chỉ số của TURN_FX: Kiểu 1 = Đuôi sao chổi vàng, Kiểu 2 = Cuộn đủ màu chế độ 3, Kiểu 3 = Trôi đa sắc chế độ 3. */
    val TURN_NAMES = listOf("Kiểu 3", "Kiểu 1", "Kiểu 2")
    private var flow = false                       // cảnh báo đang chạy bằng hiệu ứng của hộp
    private var hold: IntArray? = null             // khối 07 của người dùng, giữ lại để trả về sau cảnh báo
    private const val DIR_BITS = 0x3F              // 6 đoạn: cửa trước trái, trước phải, sau trái, sau phải, táp lô giữa, đồng hồ

    private fun tryFlow(k: Int, left: Int) {
        if (kind != k || flow) return
        if (startFlow(k) || left <= 0) return
        h.postDelayed({ tryFlow(k, left - 1) }, 700)
    }

    /** Trả về false nếu chưa đọc được hướng chạy từ hộp (đã gửi lệnh hỏi lại). */
    private fun startFlow(k: Int): Boolean {
        val p = hold ?: S.p07 ?: run { S.ble.send(Protocol.query(0x07)); return false }
        if (hold == null) { hold = p.copyOf(); S.holdP07(p) }
        // rẽ trái và rẽ phải dùng hai hướng ngược nhau; bên nào ứng với bit nào thì thử trên xe rồi chỉnh bằng công tắc Đảo chiều
        val left = (k == 1) != S.turnFlip.value
        val d1 = (p[0] and DIR_BITS.inv()) or (if (left) DIR_BITS else 0)
        S.ble.send(Protocol.block07(d1, p[1], p[2]), 23, true)
        val fx = S.turnFx.intValue.coerceIn(0, TURN_FX.size - 1)
        val fr = Protocol.effect(S.sens.intValue, S.m1, TURN_FX[fx].first, TURN_FX[fx].second, S.turnSpd[fx].coerceIn(1, 10))
        S.ble.send(fr, 3, true)
        flow = true
        // Chủ dự án báo (23/11): Kiểu 2 chạy ra Cuộn đủ màu kiểu 1 thay vì kiểu 3. Phỏng đoán: khi vừa đổi sang chế độ khác, hộp bỏ qua
        // tham số kiểu con ở khung đầu và chỉ nhận ở khung sau (ở trang Đèn người dùng luôn chạm ô chế độ rồi mới chạm kiểu con, tức hai khung).
        // Vì vậy gửi lại đúng khung đó thêm hai lần.
        for (d in longArrayOf(300, 900)) h.postDelayed({ if (flow && kind == k) S.ble.send(fr, 3, true) }, d)
        return true
    }

    private fun stopFlow() {
        flow = false
        val p = hold
        if (p != null) { S.ble.send(Protocol.block07(p[0], p[1], p[2]), 23, true); S.p07 = p.copyOf() }
        hold = null
        S.holdP07(null)
        // trả hộp về chế độ đang đặt (Đơn sắc cũng phải gửi lại vì hộp đang ở Trôi đa sắc)
        if (S.mode.intValue != 0) S.sendEffect()
        else { S.ble.send(Protocol.effect(S.sens.intValue, S.m1, 0, 0, S.speed.intValue), 3, true); S.ble.send(S.colorFrame(), 2) }
    }

    /** Kết thúc cảnh báo đang chạy: trả đèn về như cũ. */
    private fun stop() {
        h.removeCallbacks(tick)
        h.removeCallbacks(expire)
        S.alertPhase.value = false
        if (flow) { stopFlow(); return }
        if (kind == 0) return
        if (S.mode.intValue != 0) S.sendEffect() else S.ble.send(S.colorFrame(), 2)
    }

    private val tick = object : Runnable {
        override fun run() {
            if (kind < 3) return                       // rẽ chạy bằng hiệu ứng hộp; còn lại nháy bằng lệnh màu
            val on = !S.alertPhase.value
            S.alertPhase.value = on
            // Quá tốc độ: khu được chọn nháy đỏ
            val ev = EV[kind]
            val v = if (on) colorOf(kind) else 0x000000
            val uni = S.czone.intValue == 0
            val b1 = if (uni) S.col[0] else S.col[1]
            val b2 = if (uni) S.col[0] else S.col[2]
            val z = S.alertZone[kind.coerceIn(0, 7)]
            val c1 = if (z == 0 || z == 1) v else b1
            val c2 = if (z == 0 || z == 2) v else b2
            S.ble.send(Protocol.color(z == 0, c1, c2, S.invert.value), 2)
            h.postDelayed(this, EV[kind]?.get(1) ?: 250L)
        }
    }

    private fun toast(t: String) = Notice.show(S.app, L.tr(t), true)
}
