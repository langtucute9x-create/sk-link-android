package vn.sklight.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val CAM = 14f
private val ASPHALT = Color(0xFF3A3F4B)
private val AMBER = Color(0xFFFFB300)

/** Thẻ "Mô phỏng": xe chạy trên đường 3D, báo rẽ trái / phải / quá tốc độ theo đúng cài đặt Báo trước và Thời gian đèn chạy. */
@Composable
fun SimCard() {
    val run = Sim.running.value
    val k = Sim.kind.intValue
    Card {
        GroupTitle(Icons.Filled.DirectionsCar, "Mô phỏng", 16)
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().aspectRatio(680f / 600f).clip(RoundedCornerShape(14.dp)).background(Color(0xFFE9EFF7))) {
            Scene(Modifier.fillMaxSize())
            Hud()
        }
        Spacer(Modifier.height(8.dp))
        val maxS = S.alertMaxSec.intValue
        T(
            if (k in 4..7) when (k) {
                4 -> "Xe chạy 45 km/h, gặp camera phạt nguội: đèn nháy khoảng 6 giây."
                5 -> "Xe chạy 45 km/h, gặp camera vượt đèn đỏ: đèn nháy nhanh khoảng 5 giây."
                6 -> "Xe chạy 45 km/h, gặp biển cấm (cấm vượt...): đèn nháy 2 giây."
                else -> "Xe chạy 45 km/h, giới hạn tụt từ 60 xuống 40: đèn nháy 2 giây."
            }
            else if (k == 3) "Xe chạy 45 km/h rồi vượt giới hạn ${Sim.LIMIT} km/h: khu được chọn nháy, về dưới giới hạn thì tắt."
            else "Xe chạy tới chỗ rẽ. Đèn báo bắt đầu khi còn ${S.alertDist.intValue} m, " + (if (maxS > 0) "chạy tối đa $maxS giây." else "chạy tới khi qua chỗ rẽ."),
            12, color = Grey
        )
        if (!S.connected.value) T("Chưa kết nối hộp: chỉ mô phỏng trên màn hình, đèn thật không sáng.", 12, color = Grey)
        Spacer(Modifier.height(10.dp))
        Grid(listOf("Rẽ trái" to 1, "Rẽ phải" to 2, "Quá tốc độ" to 3, "Camera" to 4, "Camera đèn đỏ" to 5, "Biển cấm" to 6, "Giới hạn giảm" to 7), 3) { (label, v), m -> Chip(label, run && k == v, m, 44.dp, 12) { Sim.start(v) } }
        if (run) { Spacer(Modifier.height(8.dp)); OutlineBtn("Dừng mô phỏng", Modifier.fillMaxWidth()) { Sim.stop() } }
    }
    DisposableEffect(Unit) { onDispose { Sim.stop() } }
}

@Composable
private fun Hud() {
    val run = Sim.running.value
    val k = Sim.kind.intValue
    val ak = S.alertKind.intValue
    // đếm giờ đèn báo: ghi lại lúc đèn bắt đầu chạy
    var lit0 by remember { mutableLongStateOf(0L) }
    LaunchedEffect(ak, run) { lit0 = if (run && ak != 0) System.currentTimeMillis() else 0L }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(run) { while (run) { now = System.currentTimeMillis(); kotlinx.coroutines.delay(100) } }
    Box(Modifier.fillMaxSize().padding(8.dp)) {
        // tốc độ + biển giới hạn
        Row(Modifier.align(Alignment.TopStart), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xCC15161C)).padding(horizontal = 10.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                T(Sim.speed.floatValue.toInt().toString(), 22, FontWeight.ExtraBold, color = if (k == 3 && ak == 3) Color(0xFFFF5252) else Color.White)
                T("km/h", 10, color = Color(0xFFB8BCC6))
            }
            if (k == 3 && run) {
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color.White).then(Modifier), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(34.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(0xFFE53935)), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(25.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color.White), contentAlignment = Alignment.Center) { T("${Sim.LIMIT}", 13, FontWeight.ExtraBold, color = Color(0xFF111111)) }
                    }
                }
            }
        }
        // khoảng cách tới chỗ rẽ
        if (k != 3 && run) Column(Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(12.dp)).background(Color(0xCC15161C)).padding(horizontal = 12.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            T(if (Sim.turn.floatValue > 0f) "Đang rẽ" else "Còn ${Sim.dist.floatValue.toInt().coerceAtLeast(0)} m", 18, FontWeight.ExtraBold, color = Color.White)
            T(if (k == 1) "◀ rẽ trái" else if (k == 2) "rẽ phải ▶" else "phía trước", 11, color = Color(0xFFB8BCC6))
        }
        // trạng thái đèn
        val txt = when {
            !run -> "Chọn một tình huống bên dưới"
            ak == 1 || ak == 2 -> {
                val left = if (S.alertMaxSec.intValue > 0 && lit0 > 0) max(0, S.alertMaxSec.intValue - ((now - lit0) / 1000).toInt()) else -1
                (if (ak == 1) "ĐÈN BÁO RẼ TRÁI" else "ĐÈN BÁO RẼ PHẢI") + (if (left >= 0) " · còn $left giây" else "")
            }
            ak == 3 -> "QUÁ TỐC ĐỘ · ĐÈN NHÁY"
            ak == 4 -> "CAMERA PHÍA TRƯỚC · ĐÈN NHÁY"
            ak == 5 -> "CAMERA ĐÈN ĐỎ · ĐÈN NHÁY"
            ak == 6 -> "BIỂN CẤM · ĐÈN NHÁY"
            ak == 7 -> "GIỚI HẠN GIẢM · ĐÈN NHÁY"
            k == 3 || k >= 4 -> "Đang chạy trong giới hạn"
            Sim.turn.floatValue > 0f -> "Đã qua chỗ rẽ · đèn về như cũ"
            Sim.dist.floatValue > S.alertDist.intValue -> "Chưa tới điểm báo trước (${S.alertDist.intValue} m)"
            else -> "Đèn báo đã hết thời gian chạy"
        }
        val col = when { ak in 3..7 -> Color(0xFF000000 or AlertBridge.colorOf(ak).toLong()); ak != 0 -> Color(0xFFF59E0B); else -> Color(0xCC15161C) }
        Box(Modifier.align(Alignment.BottomCenter).clip(RoundedCornerShape(20.dp)).background(col).padding(horizontal = 14.dp, vertical = 6.dp)) { T(txt, 12, FontWeight.ExtraBold, color = Color.White) }
    }
}

private fun rnd(i: Int): Float { val x = sin(i * 127.1) * 43758.5; return (x - kotlin.math.floor(x)).toFloat() }

/** Cảnh mô phỏng kiểu Isometric: nhìn chéo từ trên cao, xe chạy lên phía trên bên phải. Khoảng cách được nén lại cho vừa khung. */
@Composable
private fun Scene(modifier: Modifier) {
    val ak = S.alertKind.intValue
    val phase = S.alertPhase.value
    var clock by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { while (true) { clock = (System.currentTimeMillis() % 100000L) / 1000f; kotlinx.coroutines.delay(40) } }
    val trav = Sim.travelled.floatValue
    val dist = Sim.dist.floatValue
    val kind = Sim.kind.intValue
    val run = Sim.running.value
    Canvas(modifier) {
        val sc = size.width / 680f
        val cz = 11.6f; val rw = 7f; val nr = cz - 5f; val far = 240f
        val isTurn = run && kind in 1..2
        val tside = if (kind == 1) -1f else 1f
        val jz = dist + cz
        fun P(x: Float, z: Float, y: Float = 0f): Offset {
            val u = x * 0.5f; val w = (z - cz) * 0.12f; val s = 15f * sc
            return Offset(size.width / 2 - 190f * sc + (w + u) * 0.866f * s, size.height - 150f * sc - (w - u) * 0.5f * s - y * s * 0.8f)
        }
        fun poly(pts: List<Offset>, c: Color) {
            val path = Path(); pts.forEachIndexed { i, o -> if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }; path.close()
            drawPath(path, c)
        }
        fun seg(a: Offset, b: Offset, w: Float, c: Color) = drawLine(c, a, b, w * sc, StrokeCap.Round)
        fun box(x0: Float, x1: Float, z0: Float, z1: Float, h: Float, top: Color, near: Color, side: Color) {
            poly(listOf(P(x1, z0), P(x1, z1), P(x1, z1, h), P(x1, z0, h)), side)
            poly(listOf(P(x0, z0), P(x1, z0), P(x1, z0, h), P(x0, z0, h)), near)
            poly(listOf(P(x0, z0, h), P(x1, z0, h), P(x1, z1, h), P(x0, z1, h)), top)
        }
        drawRect(Color(0xFFE9EFF7))
        val road = Color(0xFF6B7487)
        poly(listOf(P(-rw, nr), P(rw, nr), P(rw, far), P(-rw, far)), road)
        if (isTurn && jz > 0) {
            val z1 = max(jz - rw, nr); val z2 = jz + rw
            poly(listOf(P(tside * rw, z1), P(tside * 60f, z1), P(tside * 60f, z2), P(tside * rw, z2)), road)
        }
        seg(P(-rw, nr), P(-rw, far), 3f, Color.White); seg(P(rw, nr), P(rw, far), 3f, Color.White)
        for (n in 0 until 45) {
            val dz = ((trav / 8f).toInt() + n) * 8f - trav + cz
            if (dz < nr || dz > far - 4f) continue
            seg(P(0f, dz), P(0f, dz + 3.5f), 3f, Color.White)
        }
        // nhà hai bên, xa tới gần
        val items = ArrayList<IntArray>()
        for (n in 0 until 40) {
            val idx = (trav / 22f).toInt() + n
            val bz = idx * 22f - trav + cz
            if (bz < nr + 4f || bz > far) continue
            for (i in 0..1) {
                val sd = if (i == 1) 1f else -1f
                if (isTurn && sd == tside && abs(bz - jz) < rw + 13f) continue
                items.add(intArrayOf((bz * 100).toInt(), idx, i))
            }
        }
        items.sortByDescending { it[0] }
        for (it in items) {
            val bz = it[0] / 100f; val idx = it[1]; val i = it[2]; val sd = if (i == 1) 1f else -1f
            val h = 5f + rnd(idx * 2 + i) * 14f; val w = 9f + rnd(idx + i * 7) * 8f; val gx = rw + 4f + rnd(idx * 3 + i) * 4f
            val x0 = if (sd < 0) -(gx + w) else gx; val x1 = if (sd < 0) -gx else gx + w
            box(x0, x1, bz - 14f, bz, h, Color.White, Color(0xFFC8D3E2), Color(0xFFDDE5F0))
        }
        // tuyến đường xanh
        val pts = if (isTurn) listOf(2.2f to nr + 2f, 2.2f to jz - 5f, (if (tside > 0) 5f else -5f) to jz, tside * 70f to jz) else listOf(2.2f to nr + 2f, 2.2f to far)
        val dense = ArrayList<Pair<Float, Float>>()
        for (i in 0 until pts.size - 1) {
            val a = pts[i]; val b = pts[i + 1]
            val len = kotlin.math.hypot(b.first - a.first, b.second - a.second); val m = max(2, kotlin.math.ceil(len / 6f).toInt())
            for (j in 0 until m) dense.add(a.first + (b.first - a.first) * j / m to a.second + (b.second - a.second) * j / m)
        }
        dense.add(pts.last())
        for (i in 0 until dense.size - 1) seg(P(dense[i].first, dense[i].second), P(dense[i + 1].first, dense[i + 1].second), 12f, Color(0xFF1E6BFF))
        for (i in 0 until dense.size - 1) seg(P(dense[i].first, dense[i].second), P(dense[i + 1].first, dense[i + 1].second), 4f, Color(0x8CFFFFFF))
        // biển báo bên lề phải
        val sz = dist + cz
        if (run && kind in 4..7 && sz > nr && sz < far) {
            val a = P(rw + 2.5f, sz); val b = P(rw + 2.5f, sz, 3.6f); val r = 16f * sc
            seg(a, b, 3f, Color(0xFF8B93A3))
            drawCircle(Color.White, r, b)
            drawCircle(if (kind == 4 || kind == 5) Color(0xFF1E6BFF) else Color(0xFFE53935), r, b, style = Stroke(r * 0.3f))
            val p = android.graphics.Paint().apply { isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER; color = android.graphics.Color.parseColor("#111111"); textSize = r * 0.95f; isFakeBoldText = true }
            drawContext.canvas.nativeCanvas.drawText(when (kind) { 4 -> "CAM"; 5 -> "ĐỎ"; 6 -> "✕"; else -> if (dist < 60f) "40" else "60" }, b.x, b.y + r * 0.33f, p)
        }
        // xe và đèn báo
        val kc = if (ak in 3..7) Color(0xFF000000 or AlertBridge.colorOf(ak).toLong()) else Color(0xFFFFC400)
        val on = ak != 0 && (ak in 1..2 || phase)
        val pulse = 0.5f + 0.5f * sin(clock * 8.3f)
        fun lit(sd: Int) = on && (ak > 2 || (if (ak == 1) -1 else 1) == sd)
        val gp = P(2.2f, cz)
        if (on) drawCircle(Brush.radialGradient(listOf(kc.copy(alpha = if (ak in 1..2) 0.35f + 0.25f * pulse else 0.6f), Color.Transparent), gp, 110f * sc), 110f * sc, gp)
        box(1f, 3.4f, cz - 2.2f, cz + 2.2f, 1.5f, Color(0xFFFF5A5F), Color(0xFFFF5A5F), Color(0xFFE04B50))
        box(1.3f, 3.1f, cz - 0.9f, cz + 1.4f, 2.3f, Color(0xFF2A3142), Color(0xFF2A3142), Color(0xFF2A3142))
        seg(P(1.3f, cz - 2.2f, 0.95f), P(2.3f, cz - 2.2f, 0.95f), 7f, if (lit(-1)) kc else Color(0xFF8A1F2A))
        seg(P(3.1f, cz - 2.2f, 0.95f), P(2.1f, cz - 2.2f, 0.95f), 7f, if (lit(1)) kc else Color(0xFF8A1F2A))
    }
}
