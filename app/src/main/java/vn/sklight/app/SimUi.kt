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
        Box(Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF9CC9F2))) {
            Scene(Modifier.fillMaxSize())
            Hud()
        }
        Spacer(Modifier.height(8.dp))
        val maxS = S.alertMaxSec.intValue
        T(
            if (k == 3) "Xe chạy 45 km/h rồi vượt giới hạn ${Sim.LIMIT} km/h: khu được chọn nháy đỏ, về dưới giới hạn thì tắt."
            else "Xe chạy tới chỗ rẽ. Đèn báo bắt đầu khi còn ${S.alertDist.intValue} m, " + (if (maxS > 0) "chạy tối đa $maxS giây." else "chạy tới khi qua chỗ rẽ."),
            12, color = Grey
        )
        if (!S.connected.value) T("Chưa kết nối hộp: chỉ mô phỏng trên màn hình, đèn thật không sáng.", 12, color = Grey)
        Spacer(Modifier.height(10.dp))
        Grid(listOf("Rẽ trái" to 1, "Rẽ phải" to 2, "Quá tốc độ" to 3), 3) { (label, v), m -> Chip(label, run && k == v, m, 44.dp, 12) { Sim.start(v) } }
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
            T(if (Sim.turn.floatValue > 0f) "Đang rẽ" else "Còn ${Sim.dist.floatValue.toInt()} m", 18, FontWeight.ExtraBold, color = Color.White)
            T(if (k == 1) "◀ rẽ trái" else "rẽ phải ▶", 11, color = Color(0xFFB8BCC6))
        }
        // trạng thái đèn
        val txt = when {
            !run -> "Chọn một tình huống bên dưới"
            ak == 1 || ak == 2 -> {
                val left = if (S.alertMaxSec.intValue > 0 && lit0 > 0) max(0, S.alertMaxSec.intValue - ((now - lit0) / 1000).toInt()) else -1
                (if (ak == 1) "ĐÈN BÁO RẼ TRÁI" else "ĐÈN BÁO RẼ PHẢI") + (if (left >= 0) " · còn $left giây" else "")
            }
            ak == 3 -> "QUÁ TỐC ĐỘ · ĐÈN NHÁY ĐỎ"
            k == 3 -> "Đang chạy trong giới hạn"
            Sim.turn.floatValue > 0f -> "Đã qua chỗ rẽ · đèn về như cũ"
            Sim.dist.floatValue > S.alertDist.intValue -> "Chưa tới điểm báo trước (${S.alertDist.intValue} m)"
            else -> "Đèn báo đã hết thời gian chạy"
        }
        val col = when { ak == 3 -> Color(0xFFE53935); ak != 0 -> Color(0xFFF59E0B); else -> Color(0xCC15161C) }
        Box(Modifier.align(Alignment.BottomCenter).clip(RoundedCornerShape(20.dp)).background(col).padding(horizontal = 14.dp, vertical = 6.dp)) { T(txt, 12, FontWeight.ExtraBold, color = Color.White) }
    }
}

private fun f(z: Float) = CAM / (z + CAM)

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
    val turn = Sim.turn.floatValue
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val hy = h * 0.36f; val by = h * 1.02f
        val side = if (kind == 1) -1f else 1f
        val ease = turn * turn * (3 - 2 * turn)
        val cx = w / 2f - side * ease * w * 0.95f
        fun sy(z: Float) = hy + (by - hy) * f(z)
        fun px(z: Float, m: Float) = m * (w * 0.40f / 3.5f) * f(z)
        // trời + đất
        drawRect(Brush.verticalGradient(listOf(Color(0xFF7FB6EE), Color(0xFFE3F2FF)), 0f, hy), size = Size(w, hy))
        drawRect(Brush.verticalGradient(listOf(Color(0xFF8DBB6B), Color(0xFF5E9548)), hy, h), topLeft = Offset(0f, hy), size = Size(w, h - hy))
        // nhà và cây hai bên đường, từ xa tới gần
        var z = ((trav % 22f) * -1f) + 22f * 12
        val wob = if (run) trav else 0f
        var idx = (trav / 22f).toInt() + 12
        while (z > 0.8f) {
            val y = sy(z); val sc = f(z)
            for (sd in listOf(-1f, 1f)) {
                val gx = cx + sd * px(z, 6.5f + ((idx + (if (sd > 0) 1 else 0)) % 3) * 1.5f)
                val bw = px(z, 5f); val bh = px(z, 4.5f + ((idx * 7 + (if (sd > 0) 3 else 0)) % 4))
                val c = listOf(Color(0xFFE8D9C2), Color(0xFFD9E4EE), Color(0xFFF1C6B4), Color(0xFFCFE0C8))[(idx + (if (sd > 0) 2 else 0)) % 4]
                if ((idx + (if (sd > 0) 1 else 0)) % 2 == 0) {
                    drawRect(c, Offset(gx - bw / 2, y - bh), Size(bw, bh))
                    drawRect(Color(0x33000000), Offset(gx - bw / 2, y - bh), Size(bw, bh * 0.12f))
                    drawRect(Color(0xFF6B7F99), Offset(gx - bw * 0.3f, y - bh * 0.7f), Size(bw * 0.6f, bh * 0.22f))
                } else {
                    drawRect(Color(0xFF7A5230), Offset(gx - bw * 0.05f, y - bh * 0.35f), Size(bw * 0.1f, bh * 0.35f))
                    drawCircle(Color(0xFF3E8E41), bw * 0.42f, Offset(gx, y - bh * 0.6f))
                    drawCircle(Color(0xFF58A95A), bw * 0.3f, Offset(gx - bw * 0.1f, y - bh * 0.7f))
                }
            }
            z -= 22f; idx -= 1
        }
        // mặt đường
        val zf = 400f
        val road = Path().apply {
            moveTo(cx - px(zf, 3.5f), sy(zf)); lineTo(cx + px(zf, 3.5f), sy(zf)); lineTo(cx + px(0f, 3.5f), by); lineTo(cx - px(0f, 3.5f), by); close()
        }
        drawPath(road, ASPHALT)
        // vỉa hè mép đường
        for (sd in listOf(-1f, 1f)) drawLine(Color(0xFFD9D9D9), Offset(cx + sd * px(zf, 3.5f), sy(zf)), Offset(cx + sd * px(0f, 3.5f), by), strokeWidth = 3f)
        // ngã rẽ
        val showJ = kind != 3 && run
        val jz = dist
        if (showJ && jz < 380f) {
            val z1 = max(jz - 3.6f, 0.2f); val z2 = jz + 3.6f
            if (z2 > 0.4f) {
                val ya = sy(z2); val yb = sy(z1)
                drawRect(ASPHALT, Offset(0f, ya), Size(w, yb - ya))
                drawLine(Color(0xFFD9D9D9), Offset(0f, ya), Offset(w, ya), 2f)
                // vạch dừng
                val zs = z2 + 0.01f
                // vạch sang đường
                for (i in -6..6) { val xx = cx + px(jz, i * 0.55f); drawRect(Color(0x66FFFFFF), Offset(xx - px(jz, 0.12f), sy(jz - 3f)), Size(px(jz, 0.24f), max(sy(jz - 2.2f) - sy(jz - 3f), 1f)).takeIf { abs(i) <= 5 } ?: Size(0f, 0f)) }
            }
        }
        // vạch kẻ giữa đường
        var d = -(trav % 8f)
        while (d < 120f) {
            val z0 = max(d, 0.3f); val z1 = d + 3.2f
            val inJ = showJ && z1 > jz - 3.6f && z0 < jz + 3.6f
            if (z1 > 0.3f && !inJ) {
                val p = Path().apply { moveTo(cx - px(z0, 0.09f), sy(z0)); lineTo(cx + px(z0, 0.09f), sy(z0)); lineTo(cx + px(z1, 0.09f), sy(z1)); lineTo(cx - px(z1, 0.09f), sy(z1)); close() }
                drawPath(p, Color(0xFFF5F5F5))
            }
            d += 8f
        }
        // mũi tên rẽ sơn trên mặt đường (trước ngã rẽ)
        if (showJ && jz > 12f && jz < 90f) arrow(kind, cx, jz - 9f, ::sy, ::px)
        // xe
        car(w, h, ak, phase, clock, kind, ease, side)
        // mini xe nhìn từ trên xuống: các khu đèn
        topCar(Offset(w - 52.dp.toPx(), h - 62.dp.toPx()), ak, phase, clock, kind)
    }
}

private fun DrawScope.arrow(kind: Int, cx: Float, z: Float, sy: (Float) -> Float, px: (Float, Float) -> Float) {
    val dirX = if (kind == 1) -1f else 1f
    fun pt(x: Float, zz: Float) = Offset(cx + px(zz, x), sy(zz))
    val p = Path().apply {
        val a = pt(-0.35f * 1f, z - 3.2f); val b = pt(0.35f, z - 3.2f)
        moveTo(a.x, a.y); lineTo(b.x, b.y)
        val c = pt(0.35f, z); lineTo(c.x, c.y)
        val e = pt(1.1f * dirX.let { if (it > 0) 1f else 1f }, z); lineTo(pt(0.35f + 1.1f, z).x, pt(0.35f + 1.1f, z).y)
        close()
    }
    // mũi tên đơn giản: thân thẳng + đầu mũi nghiêng về phía rẽ
    val body = Path().apply {
        val a = pt(-0.3f, z - 4f); val b = pt(0.3f, z - 4f); val c = pt(0.3f, z - 0.8f); val e = pt(-0.3f, z - 0.8f)
        moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(e.x, e.y); close()
    }
    drawPath(body, Color(0xFFF5F5F5))
    val tip = Path().apply {
        val l = pt(-1.3f, z - 0.8f); val r = pt(1.3f, z - 0.8f); val t = pt(dirX * 1.6f, z + 1.6f)
        moveTo(l.x, l.y); lineTo(r.x, r.y); lineTo(t.x, t.y); close()
    }
    drawPath(tip, Color(0xFFF5F5F5))
}

private fun DrawScope.car(w: Float, h: Float, ak: Int, phase: Boolean, clock: Float, kind: Int, ease: Float, side: Float) {
    val cw = w * 0.30f; val ch = cw * 0.78f
    val cx = w / 2f; val bottom = h - 10f
    val tilt = if (kind != 3) side * 9f * sin(PI.toFloat() * ease) else 0f
    rotate(tilt, Offset(cx, bottom)) {
        val left = cx - cw / 2; val top = bottom - ch
        // bóng
        drawOval(Color(0x55000000), Offset(left - cw * 0.04f, bottom - ch * 0.08f), Size(cw * 1.08f, ch * 0.16f))
        // bánh
        drawRoundRect(Color(0xFF15161C), Offset(left - cw * 0.02f, bottom - ch * 0.30f), Size(cw * 0.12f, ch * 0.30f), CornerRadius(8f))
        drawRoundRect(Color(0xFF15161C), Offset(left + cw * 0.90f, bottom - ch * 0.30f), Size(cw * 0.12f, ch * 0.30f), CornerRadius(8f))
        // thân dưới
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFE53935), Color(0xFF8E1515)), top + ch * 0.42f, bottom), Offset(left, top + ch * 0.42f), Size(cw, ch * 0.54f), CornerRadius(cw * 0.1f))
        // cabin
        val cab = Path().apply {
            moveTo(left + cw * 0.14f, top + ch * 0.46f); lineTo(left + cw * 0.22f, top + ch * 0.06f)
            quadraticBezierTo(cx, top - ch * 0.04f, left + cw * 0.78f, top + ch * 0.06f)
            lineTo(left + cw * 0.86f, top + ch * 0.46f); close()
        }
        drawPath(cab, Brush.verticalGradient(listOf(Color(0xFF23364B), Color(0xFF0E1824)), top, top + ch * 0.46f))
        drawPath(Path().apply { moveTo(left + cw * 0.26f, top + ch * 0.1f); lineTo(left + cw * 0.46f, top + ch * 0.1f); lineTo(left + cw * 0.38f, top + ch * 0.42f); lineTo(left + cw * 0.2f, top + ch * 0.42f); close() }, Color(0x22FFFFFF))
        // đèn LED nội thất quanh cửa kính
        val leftOn = ak == 3 || (ak == 1)
        val rightOn = ak == 3 || (ak == 2)
        val ledCol = if (ak == 3) (if (phase) Color(0xFFFF1744) else Color(0x33FF1744)) else AMBER
        fun glow(x0: Float, y0: Float, x1: Float, y1: Float, flow: Boolean) {
            val seg = 8
            for (i in 0 until seg) {
                val t0 = i / seg.toFloat(); val t1 = (i + 1) / seg.toFloat()
                val a = if (flow) { val head = (clock * 1.6f) % 1f; val dd = ((t0 - head) + 1f) % 1f; 0.25f + 0.75f * (1f - min(dd * 2.2f, 1f)) } else 1f
                val c = ledCol.copy(alpha = ledCol.alpha * a)
                val p0 = Offset(x0 + (x1 - x0) * t0, y0 + (y1 - y0) * t0); val p1 = Offset(x0 + (x1 - x0) * t1, y0 + (y1 - y0) * t1)
                drawLine(c.copy(alpha = c.alpha * 0.25f), p0, p1, strokeWidth = 14f, cap = StrokeCap.Round)
                drawLine(c, p0, p1, strokeWidth = 5f, cap = StrokeCap.Round)
            }
        }
        val turning = ak == 1 || ak == 2
        if (leftOn) glow(left + cw * 0.14f, top + ch * 0.46f, left + cw * 0.22f, top + ch * 0.08f, turning)
        if (rightOn) glow(left + cw * 0.86f, top + ch * 0.46f, left + cw * 0.78f, top + ch * 0.08f, turning)
        if (ak == 3) drawRoundRect(ledCol.copy(alpha = 0.18f), Offset(left, top), Size(cw, ch), CornerRadius(cw * 0.1f))
        // đèn hậu + biển số
        drawRoundRect(Color(0xFFFF5252), Offset(left + cw * 0.04f, top + ch * 0.55f), Size(cw * 0.2f, ch * 0.1f), CornerRadius(6f))
        drawRoundRect(Color(0xFFFF5252), Offset(left + cw * 0.76f, top + ch * 0.55f), Size(cw * 0.2f, ch * 0.1f), CornerRadius(6f))
        drawRoundRect(Color(0xFFF5F5F5), Offset(cx - cw * 0.14f, top + ch * 0.66f), Size(cw * 0.28f, ch * 0.13f), CornerRadius(4f))
        drawRoundRect(Color(0xFF15161C), Offset(left + cw * 0.04f, top + ch * 0.84f), Size(cw * 0.92f, ch * 0.1f), CornerRadius(6f))
    }
}

/** Xe nhìn từ trên xuống, sáu khu đèn: bốn cửa, táp lô, đồng hồ. */
private fun DrawScope.topCar(o: Offset, ak: Int, phase: Boolean, clock: Float, kind: Int) {
    val w = 44.dp.toPx(); val h = 56.dp.toPx()
    drawRoundRect(Color(0xAA15161C), o - Offset(4.dp.toPx(), 4.dp.toPx()), Size(w + 8.dp.toPx(), h + 8.dp.toPx()), CornerRadius(12.dp.toPx()))
    drawRoundRect(Color(0xFF5A5F70), o, Size(w, h), CornerRadius(12.dp.toPx()), style = Stroke(2f))
    val red = Color(0xFFFF1744); val on = if (phase) red else red.copy(alpha = 0.25f)
    fun seg(x: Float, y: Float, sw: Float, sh: Float, lit: Boolean, col: Color) {
        drawRoundRect(if (lit) col else Color(0xFF3A3F4B), o + Offset(x, y), Size(sw, sh), CornerRadius(3.dp.toPx()))
    }
    val turnL = ak == 1; val turnR = ak == 2; val sp = ak == 3
    val head = (clock * 1.6f) % 1f
    fun fl(t: Float): Boolean { val dd = ((t - head) + 1f) % 1f; return dd < 0.45f }
    val sw = 5.dp.toPx(); val L = 3.dp.toPx(); val R = w - 3.dp.toPx() - sw
    // thứ tự chạy: sau -> trước
    seg(L, h * 0.62f, sw, h * 0.24f, sp && true || (turnL && fl(0.1f)), if (sp) on else AMBER)
    seg(L, h * 0.30f, sw, h * 0.24f, sp || (turnL && fl(0.45f)), if (sp) on else AMBER)
    seg(R, h * 0.62f, sw, h * 0.24f, sp || (turnR && fl(0.1f)), if (sp) on else AMBER)
    seg(R, h * 0.30f, sw, h * 0.24f, sp || (turnR && fl(0.45f)), if (sp) on else AMBER)
    seg(w * 0.25f, h * 0.12f, w * 0.5f, 4.dp.toPx(), sp || ((turnL || turnR) && fl(0.8f)), if (sp) on else AMBER)
    seg(w * 0.25f, h * 0.20f, w * 0.5f, 4.dp.toPx(), sp || ((turnL || turnR) && fl(0.9f)), if (sp) on else AMBER)
}
