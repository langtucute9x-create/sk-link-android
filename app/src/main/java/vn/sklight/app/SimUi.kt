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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin


/** Thẻ "Mô phỏng": góc nhìn từ ghế sau xe mui trần, đường phố 3D, báo rẽ trái / phải / quá tốc độ theo đúng cài đặt Báo trước và Thời gian đèn chạy. */
@Composable
fun SimCard() {
    val run = Sim.running.value
    val k = Sim.kind.intValue
    Card {
        GroupTitle(Icons.Filled.DirectionsCar, "Mô phỏng", 16)
        Spacer(Modifier.height(10.dp))
        // khung cảnh: cao theo bề ngang, nhưng không quá 62% chiều cao màn hình (màn hình xe nằm ngang)
        val maxH = (LocalConfiguration.current.screenHeightDp * 0.62f).dp
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val hh = minOf(maxWidth * (760f / 680f), maxH).coerceAtLeast(220.dp)
            Box(Modifier.fillMaxWidth().height(hh).clip(RoundedCornerShape(14.dp)).background(Color(0xFF0B0E14))) {
                Scene(Modifier.fillMaxSize())
                Hud()
            }
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
        val col = when { ak in 3..7 -> Color(0xFF000000 or AlertBridge.showOf(ak).toLong()); ak != 0 -> Color(0xFFF59E0B); else -> Color(0xCC15161C) }
        Box(Modifier.align(Alignment.BottomCenter).clip(RoundedCornerShape(20.dp)).background(col).padding(horizontal = 14.dp, vertical = 6.dp)) { T(txt, 12, FontWeight.ExtraBold, color = Color.White) }
    }
}

private fun rnd(i: Int): Float { val x = sin(i * 127.1) * 43758.5; return (x - kotlin.math.floor(x)).toFloat() }

private const val DW = 680f
private const val DH = 760f
private const val HOR = 240f
private const val FOC = 640f
private const val HC = 1.7f
private const val RW = 5.25f
private const val NR = 3f
private const val FAR = 420f
private val FAC = intArrayOf(0xECE0C8, 0xDECCAC, 0xD2DCE8, 0xE8D2C6, 0xD8E0CE, 0xF4ECDC, 0xCCBAA0, 0xE2D6E2)
private val AWN = listOf(Color(0xFFC0392B), Color(0xFF2E7D5B), Color(0xFF2C6FB3), Color(0xFFD9822B))
private fun shade(rgb: Int, k: Float): Color {
    val r = min(255, (((rgb shr 16) and 255) * k).toInt()); val g = min(255, (((rgb shr 8) and 255) * k).toInt()); val b = min(255, ((rgb and 255) * k).toInt())
    return Color(0xFF000000 or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong())
}
/** Chiếu điểm (x ngang, d cách mắt, y cao) lên khung thiết kế 680 x 760. */
private fun pt(x: Float, d: Float, y: Float = 0f): Offset { val s = FOC / d; return Offset(DW / 2 + x * s, HOR + (HC - y) * s) }

/**
 * Cảnh mô phỏng: góc nhìn người ngồi ghế sau xe mui trần (không kính lái) nhìn lên phía trước.
 * Đường phố phối cảnh thật, nhà 3D hai bên; trong xe có đèn khu vực 1 (2 cửa trước, táp lô) và khu vực 2 (gầm chân) đổi theo cảnh báo đang chạy.
 * Vẽ trên khung thiết kế 680 x 760 rồi co theo bề ngang; khung thấp hơn thì cắt bớt trời và chân ghế.
 */
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
    val turnP = Sim.turn.floatValue
    val speed = Sim.speed.floatValue
    // khu vực 1 = táp lô + cửa, khu vực 2 = gầm chân; mỗi khu có màu thường ngày riêng khi người dùng đặt hai màu
    val uni = S.czone.intValue == 0
    val idle1: Color? = if (!S.on.value) null else rgbColor(if (uni) S.col[0] else S.col[1])
    val idle2: Color? = if (!S.on.value) null else rgbColor(if (uni) S.col[0] else S.col[2])
    val spz = if (ak in 3..7) S.alertZone[ak] else 0
    val zl1 = ak in 1..2 || (ak in 3..7 && (spz == 0 || spz == 1))      // khu 1 có tham gia cảnh báo đang chạy không
    val zl2 = ak in 1..2 || (ak in 3..7 && (spz == 0 || spz == 2))
    val path = remember { Path() }
    val pA = remember { Path() }
    val pB = remember { Path() }
    val pS = remember { Path() }
    val paint = remember { android.graphics.Paint().apply { isAntiAlias = true; isFakeBoldText = true } }
    Canvas(modifier) {
        val sc = size.width / DW
        val vh = size.height / sc
        val oy = if (vh < DH) -(DH - vh) * 0.5f else 0f
        val nc = drawContext.canvas.nativeCanvas
        fun addQuad(p: Path, a: Offset, b: Offset, c: Offset, d: Offset) { p.moveTo(a.x, a.y); p.lineTo(b.x, b.y); p.lineTo(c.x, c.y); p.lineTo(d.x, d.y); p.close() }
        fun quad(a: Offset, b: Offset, c: Offset, d: Offset, col: Color) { path.reset(); addQuad(path, a, b, c, d); drawPath(path, col) }
        fun ln(a: Offset, b: Offset, w: Float, col: Color) = drawLine(col, a, b, w, StrokeCap.Round)
        fun rrect(x: Float, y: Float, w: Float, h: Float, r: Float, col: Color) = drawRoundRect(col, Offset(x, y), Size(w, h), CornerRadius(r))
        fun text(t: String, x: Float, y: Float, sz: Float, col: Int, align: android.graphics.Paint.Align = android.graphics.Paint.Align.CENTER) {
            paint.textSize = sz; paint.color = col; paint.textAlign = align
            nc.drawText(t, x, y + sz * 0.35f, paint)
        }
        val tsd = if (kind == 1) -1f else 1f

        fun bld(sd: Float, bz: Float, idx: Int) {
            val i = if (sd > 0) 1 else 0
            val fl = 3 + (rnd(idx * 2 + i) * 4).toInt()
            val h = fl * 3.4f + 0.6f
            val col = FAC[(rnd(idx * 5 + i + 3) * FAC.size).toInt().coerceIn(0, FAC.size - 1)]
            val x = sd * 8.25f; val z0 = max(bz, NR); val z1 = bz + 14f
            if (z1 <= NR + 1f) return
            if (bz > NR) quad(pt(x, bz), pt(sd * 19f, bz), pt(sd * 19f, bz, h), pt(x, bz, h), shade(col, if (sd < 0) 0.74f else 0.9f))
            quad(pt(x, z0), pt(x, z1), pt(x, z1, h), pt(x, z0, h), shade(col, if (sd < 0) 1f else 0.8f))
            quad(pt(x, z0, h - 0.5f), pt(x, z1, h - 0.5f), pt(x, z1, h), pt(x, z0, h), shade(col, 0.62f))
            if (bz < 150f) {
                val a = max(bz + 0.8f, NR); val b = bz + 13.2f
                if (b > a + 0.5f) {
                    quad(pt(x, a), pt(x, b), pt(x, b, 2.9f), pt(x, a, 2.9f), Color(0xFF2A2E36))
                    quad(pt(x, a, 2.9f), pt(x, b, 2.9f), pt(x, b, 3.5f), pt(x, a, 3.5f), AWN[(((idx + i) % 4) + 4) % 4])
                }
            }
            if (bz < 90f) {
                pA.reset(); pB.reset()
                for (f in 1 until fl) for (j in 0..3) {
                    val za = bz + 1.2f + j * 3.2f; val zb = za + 1.9f
                    if (za < NR) continue
                    val ya = f * 3.4f + 1f; val yb = ya + 1.8f
                    addQuad(if (rnd(idx * 31 + f * 7 + j + i * 3) > 0.75f) pB else pA, pt(x, za, ya), pt(x, zb, ya), pt(x, zb, yb), pt(x, za, yb))
                }
                if (bz > NR && bz < 60f) for (f in 1 until fl) for (j in 0..2) {
                    val xa = sd * (9.6f + j * 3f); val xb = xa + sd * 1.8f; val y1 = f * 3.4f + 1f; val y2 = y1 + 1.8f
                    addQuad(pA, pt(xa, bz, y1), pt(xb, bz, y1), pt(xb, bz, y2), pt(xa, bz, y2))
                }
                drawPath(pA, Color(0xFF3F4D60)); drawPath(pB, Color(0xFF8AA0BA))
            }
        }
        fun tree(sd: Float, d: Float) {
            if (d < 6f) return
            val s = FOC / d; val x = sd * 6.7f
            ln(pt(x, d), pt(x, d, 3f), max(1.5f, 0.3f * s), Color(0xFF5D4630))
            val c = pt(x, d, 4.4f); val r = 1.9f * s
            drawCircle(Color(0xFF2F6A34), r, c)
            drawCircle(Color(0xFF5A9A4A), r * 0.62f, Offset(c.x - r * 0.25f, c.y - r * 0.25f))
        }
        fun lamp(sd: Float, d: Float) {
            if (d < 6f) return
            val s = FOC / d; val x = sd * 5.8f
            ln(pt(x, d), pt(x, d, 7.5f), max(1f, 0.14f * s), Color(0xFF5A5F69))
            ln(pt(x, d, 7.5f), pt(sd * 4.3f, d, 7.7f), max(1f, 0.12f * s), Color(0xFF5A5F69))
            drawCircle(Color(0xFFF3EFE0), max(1.5f, 0.2f * s), pt(sd * 4.3f, d, 7.6f))
        }
        /** Trời, đường, vỉa hè, nhà, cây, biển. [junc] = có ngã rẽ phía trước; [seed] đổi dãy nhà (đường mới sau khi rẽ). */
        fun scenery(junc: Boolean, seed: Int, signs: Boolean) {
            val jd = dist + 3f
            drawRect(Brush.verticalGradient(listOf(Color(0xFF5A9FE4), Color(0xFFDCEDFB)), 0f, HOR), Offset.Zero, Size(DW, HOR + 2f))
            drawOval(Color(0xBFFFFFFF), Offset(80f, 54f), Size(140f, 32f)); drawOval(Color(0xBFFFFFFF), Offset(155f, 70f), Size(100f, 24f))
            drawOval(Color(0xBFFFFFFF), Offset(390f, 35f), Size(160f, 30f)); drawOval(Color(0xBFFFFFFF), Offset(484f, 53f), Size(92f, 22f))
            drawRect(Color(0xFFA8A59C), Offset(0f, HOR), Size(DW, DH - HOR + 10f))
            quad(pt(-RW - 3f, NR), pt(-RW, NR), pt(-RW, FAR), pt(-RW - 3f, FAR), Color(0xFFCBC7BC))
            quad(pt(RW, NR), pt(RW + 3f, NR), pt(RW + 3f, FAR), pt(RW, FAR), Color(0xFFCBC7BC))
            path.reset(); addQuad(path, pt(-RW, NR), pt(RW, NR), pt(RW, FAR), pt(-RW, FAR))
            drawPath(path, Brush.verticalGradient(0f to Color(0xFF70737A), 0.25f to Color(0xFF4B4E55), 1f to Color(0xFF3A3D44), startY = HOR, endY = 480f))
            if (junc && jd + 5f > NR) {
                val d1 = max(jd - 5f, NR); val d2 = jd + 5f
                quad(pt(tsd * RW, d1), pt(tsd * 90f, d1), pt(tsd * 90f, d2), pt(tsd * RW, d2), Color(0xFF4B4E55))
                if (jd - 9.5f > NR) {
                    pA.reset(); var xx = -5f
                    while (xx < 4.6f) { addQuad(pA, pt(xx, jd - 9.5f), pt(xx + 0.5f, jd - 9.5f), pt(xx + 0.5f, jd - 7f), pt(xx, jd - 7f)); xx += 1f }
                    drawPath(pA, Color(0xD9FFFFFF))
                }
            }
            pA.reset()
            addQuad(pA, pt(-RW + 0.3f, NR), pt(-RW + 0.15f, NR), pt(-RW + 0.15f, FAR), pt(-RW + 0.3f, FAR))
            addQuad(pA, pt(RW - 0.3f, NR), pt(RW - 0.15f, NR), pt(RW - 0.15f, FAR), pt(RW - 0.3f, FAR))
            val b8 = kotlin.math.floor(trav / 8f).toInt()
            for (n in 0 until 45) {
                val dz = (b8 + n) * 8f - trav
                if (dz < NR) continue
                addQuad(pA, pt(-1.82f, dz), pt(-1.68f, dz), pt(-1.68f, dz + 3f), pt(-1.82f, dz + 3f))
                addQuad(pA, pt(1.68f, dz), pt(1.82f, dz), pt(1.82f, dz + 3f), pt(1.68f, dz + 3f))
            }
            drawPath(pA, Color(0xD9F5F5EE))
            val rc = Color(0xD11E6BFF)
            if (!junc) quad(pt(-0.9f, NR), pt(0.9f, NR), pt(0.9f, FAR), pt(-0.9f, FAR), rc)
            else if (jd > NR + 0.5f) {
                quad(pt(-0.9f, NR), pt(0.9f, NR), pt(0.9f, jd + 0.9f), pt(-0.9f, jd + 0.9f), rc)
                val a = max(NR, jd - 0.9f)
                quad(pt(tsd * 0.9f, a), pt(tsd * 90f, a), pt(tsd * 90f, jd + 0.9f), pt(tsd * 0.9f, jd + 0.9f), rc)
            }
            val base = kotlin.math.floor(trav / 16f).toInt()
            for (n in 20 downTo -1) {
                val idx = base + n; val bz = idx * 16f - trav
                for (ei in 0..1) {
                    val e = if (ei == 0) -1f else 1f
                    if (junc && e == tsd && bz < jd + 7f && bz + 16f > jd - 7f) continue
                    if (((idx % 2) + 2) % 2 == 0) tree(e, bz + 15f) else lamp(e, bz + 15f)
                    bld(e, bz, idx + seed)
                }
            }
            drawRect(Brush.verticalGradient(0f to Color(0x00DCECFA), 0.72f to Color(0x99DCECFA), 1f to Color(0x00DCECFA), startY = HOR - 70f, endY = HOR + 26f), Offset(0f, HOR - 70f), Size(DW, 96f))
            val sz = dist + 3f
            if (signs && (kind == 4 || kind == 5) && sz > NR + 1f) {
                val s1 = FOC / sz; val lw = max(2f, 0.3f * s1)
                ln(pt(-6f, sz), pt(-6f, sz, 6.6f), lw, Color(0xFF5D6470)); ln(pt(6f, sz), pt(6f, sz, 6.6f), lw, Color(0xFF5D6470))
                quad(pt(-6f, sz, 6.8f), pt(6f, sz, 6.8f), pt(6f, sz, 6.2f), pt(-6f, sz, 6.2f), Color(0xFF6D7482))
                quad(pt(-2f, sz, 6.2f), pt(2f, sz, 6.2f), pt(2f, sz, 4.9f), pt(-2f, sz, 4.9f), Color(0xFF1E56C8))
                if (0.7f * s1 > 8f) { val tp = pt(0f, sz, 5.55f); text(if (kind == 5) "ĐÈN ĐỎ" else "CAMERA", tp.x, tp.y, 0.7f * s1, android.graphics.Color.WHITE) }
                quad(pt(3f, sz, 6.2f), pt(4f, sz, 6.2f), pt(4f, sz, 5.5f), pt(3f, sz, 5.5f), Color(0xFF1F2430))
                drawCircle(if (kind == 5) Color(0xFFFF3B30) else Color(0xFF66B0FF), max(1.5f, 0.22f * s1), pt(3.5f, sz, 5.85f))
            }
            if (signs && (kind == 6 || kind == 7) && sz > NR + 1f) {
                val s2 = FOC / sz
                ln(pt(6.3f, sz), pt(6.3f, sz, 3f), max(2f, 0.14f * s2), Color(0xFF6B7385))
                val c2 = pt(6.3f, sz, 3.4f); val r2 = max(5f, 0.85f * s2)
                drawCircle(Color.White, r2, c2); drawCircle(Color(0xFFE53935), r2, c2, style = Stroke(r2 * 0.26f))
                if (r2 > 7f) text(if (kind == 6) "✕" else if (dist < 60f) "40" else "60", c2.x, c2.y, r2 * 0.9f, android.graphics.Color.parseColor("#111111"))
            }
        }

        val act = ak != 0
        val on = act && (ak in 1..2 || phase)
        val kc = if (ak in 3..7) rgbColor(AlertBridge.showOf(ak)) else Color(0xFFFFC400)
        fun amt(u: Float): Float {
            if (!on) return 0f
            if (ak > 2) return 1f
            val ph = ((clock * 1000f) % 900f) / 900f
            val pos = if (ak == 2) -0.2f + 1.4f * ph else 1.2f - 1.4f * ph
            return max(0.18f, 1f - abs(u - pos) / 0.25f)
        }
        fun led(a: Offset, b: Offset, u: Float, w: Float, zoneLit: Boolean = zl1, idle: Color? = idle1) {
            val m = if (zoneLit) min(1f, amt(u)) else 0f
            ln(a, b, w, Color(0xFF2A2F39))
            if ((!act || !zoneLit) && idle != null) ln(a, b, w, idle.copy(alpha = 0.45f))
            if (m > 0f) { ln(a, b, w * 3f, kc.copy(alpha = 0.22f * m)); ln(a, b, w, kc.copy(alpha = m)) }
        }
        fun glow(x: Float, y: Float, r: Float, a: Float) {
            if (a <= 0f) return
            val c = Offset(x, y)
            drawCircle(Brush.radialGradient(listOf(kc.copy(alpha = 0.5f * min(1f, a)), kc.copy(alpha = 0f)), c, r), r, c)
        }
        fun vent(x: Float, y: Float, r: Float) {
            val c = Offset(x, y)
            drawCircle(Color(0xFF0A0B0E), r, c); drawCircle(Color(0xFFC9CED6), r, c, style = Stroke(2.5f))
            for (a in 0 until 6) drawLine(Color(0xFF4A505B), c, Offset(x + kotlin.math.cos(a * 1.047f) * r * 0.82f, y + sin(a * 1.047f) * r * 0.82f), 1.2f)
            drawCircle(Color(0xFFC9CED6), r * 0.28f, c)
        }
        val leather = Color(0xFFE9DCC2)
        fun door(m: Boolean) {
            fun X(x: Float) = if (m) DW - x else x
            path.reset(); path.moveTo(X(60f), 482f); path.lineTo(X(0f), 594f); path.lineTo(X(0f), 770f); path.lineTo(X(124f), 770f); path.lineTo(X(88f), 550f); path.close()
            drawPath(path, Brush.horizontalGradient(listOf(Color(0xFF37322D), Color(0xFF191715)), X(0f), X(120f)))
            quad(Offset(X(6f), 646f), Offset(X(72f), 568f), Offset(X(86f), 606f), Offset(X(10f), 728f), Color(0xFF8A5631))
            drawLine(Color(0x8CE8D8BC), Offset(X(12f), 650f), Offset(X(70f), 580f), 1.2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f)))
            ln(Offset(X(60f), 482f), Offset(X(0f), 594f), 9f, Color(0xFFE3E6EB)); ln(Offset(X(62f), 488f), Offset(X(4f), 598f), 1.5f, Color.White)
            ln(Offset(X(8f), 609f), Offset(X(63f), 498f), 5f, Color(0xFF6B4529))
            ln(Offset(X(40f), 592f), Offset(X(56f), 562f), 3.5f, Color(0xFFD5DAE1))
            val sp = Offset(X(46f), 694f)
            drawCircle(Color(0xFF121317), 24f, sp); drawCircle(Color(0xFFC9CED6), 24f, sp, style = Stroke(2f))
            for (a in 0 until 3) { val cnt = 10 + a * 4; val rd = 7f + a * 6f
                for (q in 0 until cnt) { val an = q * 6.283f / cnt; drawCircle(Color(0xFF565C67), 1.3f, Offset(sp.x + kotlin.math.cos(an) * rd, sp.y + sin(an) * rd)) } }
        }
        fun seat(cx: Float) {
            ln(Offset(cx - 16f, 632f), Offset(cx - 16f, 618f), 5f, Color(0xFFA9AEB6)); ln(Offset(cx + 16f, 632f), Offset(cx + 16f, 618f), 5f, Color(0xFFA9AEB6))
            val sg = Brush.horizontalGradient(0f to Color(0xFF7A4829), 0.5f to Color(0xFFB47A4E), 1f to Color(0xFF7A4829), startX = cx - 100f, endX = cx + 100f)
            drawRoundRect(sg, Offset(cx - 46f, 564f), Size(92f, 58f), CornerRadius(24f))
            drawRoundRect(leather, Offset(cx - 46f, 564f), Size(92f, 58f), CornerRadius(24f), style = Stroke(2f))
            drawRoundRect(Color(0x38000000), Offset(cx - 33f, 574f), Size(66f, 38f), CornerRadius(15f), style = Stroke(1.5f))
            pS.reset(); pS.moveTo(cx - 100f, 772f); pS.lineTo(cx - 94f, 668f); pS.quadraticBezierTo(cx - 92f, 632f, cx - 58f, 628f); pS.lineTo(cx + 58f, 628f)
            pS.quadraticBezierTo(cx + 92f, 632f, cx + 94f, 668f); pS.lineTo(cx + 100f, 772f); pS.close()
            drawPath(pS, sg)
            clipPath(pS) {
                pA.reset(); var o = -240f
                while (o <= 240f) { pA.moveTo(cx + o, 628f); pA.lineTo(cx + o + 144f, 772f); pA.moveTo(cx + o, 628f); pA.lineTo(cx + o - 144f, 772f); o += 22f }
                drawPath(pA, Color(0x4D000000), style = Stroke(1.5f))
                translate(2f, 0f) { drawPath(pA, Color(0x1FFFFFFF), style = Stroke(1f)) }
                drawRect(Color(0xFF6B3F23), Offset(cx - 104f, 628f), Size(36f, 150f)); drawRect(Color(0xFF6B3F23), Offset(cx + 68f, 628f), Size(36f, 150f))
                drawLine(leather, Offset(cx - 68f, 634f), Offset(cx - 68f, 772f), 1.5f); drawLine(leather, Offset(cx + 68f, 634f), Offset(cx + 68f, 772f), 1.5f)
            }
            drawPath(pS, leather, style = Stroke(2f))
        }

        drawRect(Color(0xFF0B0E14))
        withTransform({ scale(sc, sc, Offset.Zero); translate(0f, oy) }) {
            // ---- ngoài xe ----
            if (kind in 1..2 && turnP > 0f) {
                if (turnP >= 1f) scenery(false, 1000, false)
                else {
                    val ease = turnP * turnP * (3 - 2 * turnP)
                    translate(-tsd * ease * DW, 0f) { clipRect(0f, 0f, DW, DH + 10f) { scenery(true, 0, false) } }
                    translate(tsd * (1f - ease) * DW, 0f) { clipRect(0f, 0f, DW, DH + 10f) { scenery(false, 1000, false) } }
                }
            } else scenery(run && kind in 1..2, 0, run)

            // ---- nắp ca-pô, gương ----
            path.reset(); path.moveTo(126f, 448f); path.quadraticBezierTo(340f, 434f, 554f, 448f); path.lineTo(598f, 482f); path.lineTo(82f, 482f); path.close()
            drawPath(path, Brush.verticalGradient(listOf(Color(0xFFFBFCFD), Color(0xFFB6BCC7)), 440f, 482f))
            drawLine(Color(0x1A000000), Offset(284f, 444f), Offset(258f, 480f), 1.5f); drawLine(Color(0x1A000000), Offset(396f, 444f), Offset(422f, 480f), 1.5f)
            rrect(10f, 440f, 48f, 26f, 11f, Color(0xFFE3E6EB)); rrect(14f, 444f, 40f, 18f, 8f, Color(0xFF55677D))
            rrect(622f, 440f, 48f, 26f, 11f, Color(0xFFE3E6EB)); rrect(626f, 444f, 40f, 18f, 8f, Color(0xFF55677D))

            // ---- táp lô: da tối, chỉ khâu, nẹp gỗ, cửa gió ----
            path.reset(); path.moveTo(0f, 640f); path.lineTo(0f, 524f); path.quadraticBezierTo(18f, 488f, 60f, 482f); path.quadraticBezierTo(340f, 462f, 620f, 482f)
            path.quadraticBezierTo(662f, 488f, 680f, 524f); path.lineTo(680f, 640f); path.close()
            drawPath(path, Brush.verticalGradient(0f to Color(0xFF383B44), 0.35f to Color(0xFF22242A), 1f to Color(0xFF101216), startY = 466f, endY = 640f))
            val dashFx = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))
            path.reset(); path.moveTo(66f, 491f); path.quadraticBezierTo(340f, 472f, 614f, 491f)
            drawPath(path, Color(0x99E8D8BC), style = Stroke(1.3f, pathEffect = dashFx))
            path.reset(); path.moveTo(40f, 540f); path.quadraticBezierTo(340f, 522f, 640f, 540f)
            drawPath(path, Color(0x99E8D8BC), style = Stroke(1.3f, pathEffect = dashFx))
            path.reset(); path.moveTo(62f, 499f); path.quadraticBezierTo(340f, 480f, 618f, 499f); path.lineTo(614f, 511f); path.quadraticBezierTo(340f, 492f, 66f, 511f); path.close()
            drawPath(path, Brush.horizontalGradient(0f to Color(0xFF3D2616), 0.3f to Color(0xFF7D5536), 0.5f to Color(0xFF5A3A22), 0.7f to Color(0xFF84593A), 1f to Color(0xFF3D2616), startX = 62f, endX = 618f))
            path.reset(); path.moveTo(62f, 499f); path.quadraticBezierTo(340f, 480f, 618f, 499f)
            drawPath(path, Color(0x8CFFFFFF), style = Stroke(1f))
            vent(104f, 560f, 13f); vent(576f, 560f, 13f); vent(318f, 566f, 12f); vent(362f, 566f, 12f)

            // ---- màn hình cong: đồng hồ tốc độ + bản đồ ----
            rrect(146f, 424f, 340f, 62f, 13f, Color(0xFF06070A))
            drawRoundRect(Color(0x4DFFFFFF), Offset(146f, 424f), Size(340f, 62f), CornerRadius(13f), style = Stroke(1.2f))
            drawArc(Color(0xFF262B36), 137.5f, 264.7f, false, Offset(175f, 436f), Size(42f, 42f), style = Stroke(4f, cap = StrokeCap.Round))
            drawArc(if (ak == 3) Color(0xFFFF5A5A) else Color(0xFF5FB0FF), 137.5f, 264.7f * (speed / 120f).coerceIn(0f, 1f), false, Offset(175f, 436f), Size(42f, 42f), style = Stroke(4f, cap = StrokeCap.Round))
            text(speed.toInt().toString(), 196f, 458f, 17f, android.graphics.Color.WHITE)
            text("km/h", 254f, 458f, 11f, android.graphics.Color.parseColor("#8F97A6"))
            rrect(294f, 430f, 186f, 50f, 8f, Color(0xFF10223F))
            drawLine(Color(0x1FFFFFFF), Offset(300f, 446f), Offset(474f, 446f), 1f); drawLine(Color(0x1FFFFFFF), Offset(330f, 432f), Offset(330f, 478f), 1f); drawLine(Color(0x1FFFFFFF), Offset(440f, 432f), Offset(440f, 478f), 1f)
            ln(Offset(387f, 474f), Offset(387f, 452f), 4f, Color(0xFF3B8BFF))
            if (run && kind in 1..2 && turnP == 0f) ln(Offset(387f, 452f), Offset(387f + tsd * 44f, 452f), 4f, Color(0xFF3B8BFF)) else ln(Offset(387f, 452f), Offset(387f, 436f), 4f, Color(0xFF3B8BFF))
            path.reset(); path.moveTo(387f, 464f); path.lineTo(381f, 476f); path.lineTo(393f, 476f); path.close(); drawPath(path, Color.White)

            // ---- cửa trước, đèn viền, ánh sáng hắt ----
            door(false); door(true)
            // ---- đèn gầm chân (khu vực 2): ánh sáng hắt từ sàn sau lưng ghế + dải đèn dưới mép táp lô ----
            clipRect(0f, 500f, DW, DH + 10f) {
                for (fi in 0..1) {
                    val cx = if (fi == 0) 200f else 480f
                    val a = if (zl2) min(1f, amt(if (fi == 0) 0.3f else 0.7f)) else 0f
                    val c = Offset(cx, 664f)
                    if (a > 0f) drawCircle(Brush.radialGradient(listOf(kc.copy(alpha = 0.62f * a), kc.copy(alpha = 0f)), c, 200f), 200f, c)
                    else if ((!act || !zl2) && idle2 != null) drawCircle(Brush.radialGradient(listOf(idle2.copy(alpha = 0.3f), idle2.copy(alpha = 0f)), c, 180f), 180f, c)
                }
            }
            led(Offset(98f, 613f), Offset(298f, 613f), 0.3f, 5f, zl2, idle2)
            led(Offset(382f, 613f), Offset(582f, 613f), 0.7f, 5f, zl2, idle2)
            for (i in 0 until 5) {
                val ta = i / 5f; val tb = (i + 1) / 5f; val tm = (ta + tb) / 2f
                led(Offset(10f + 56f * ta, 620f - 112f * ta), Offset(10f + 56f * tb, 620f - 112f * tb), 0.25f * tm, 7f)
                led(Offset(670f - 56f * ta, 620f - 112f * ta), Offset(670f - 56f * tb, 620f - 112f * tb), 1f - 0.25f * tm, 7f)
            }
            fun bez(t: Float): Offset { val a = (1 - t) * (1 - t); val b = 2 * (1 - t) * t; val c = t * t; return Offset(a * 70f + b * 340f + c * 610f, a * 519f + b * 501f + c * 519f) }
            for (i in 0 until 14) led(bez(i / 14f), bez((i + 1) / 14f), 0.25f + 0.5f * (i + 0.5f) / 14f, 6f)
            if (zl1) { glow(36f, 566f, 120f, amt(0.12f)); glow(200f, 518f, 150f, amt(0.4f)); glow(480f, 518f, 150f, amt(0.6f)); glow(644f, 566f, 120f, amt(0.88f)) }

            // ---- vô lăng, bệ giữa, ghế ----
            ln(Offset(124f, 598f), Offset(276f, 598f), 9f, Color(0xFF1B1D22)); ln(Offset(128f, 598f), Offset(272f, 598f), 1.5f, Color(0xFFC9CED6))
            drawOval(Color(0xFF15171B), Offset(120f, 526f), Size(160f, 140f), style = Stroke(15f))
            drawArc(Color(0xFFD8C7A6), -93.5f, 7f, false, Offset(120f, 526f), Size(160f, 140f), style = Stroke(15f))
            drawArc(Color(0x24FFFFFF), 206f, 126f, false, Offset(114f, 520f), Size(172f, 152f), style = Stroke(2f))
            rrect(300f, 598f, 80f, 180f, 12f, Color(0xFF0C0D11))
            drawRoundRect(Color(0xFFAEB4BD), Offset(300f, 598f), Size(80f, 180f), CornerRadius(12f), style = Stroke(1.5f))
            drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFF7D848F)), Offset(336f, 628f), 18f), 15f, Offset(340f, 632f))
            drawCircle(Color(0xFF16181D), 9f, Offset(340f, 632f))
            drawRoundRect(Brush.horizontalGradient(0f to Color(0xFF7A4829), 0.5f to Color(0xFFB47A4E), 1f to Color(0xFF7A4829), startX = 308f, endX = 372f), Offset(308f, 664f), Size(64f, 116f), CornerRadius(10f))
            drawRoundRect(leather, Offset(308f, 664f), Size(64f, 116f), CornerRadius(10f), style = Stroke(1.5f))
            drawLine(Color(0x40000000), Offset(340f, 670f), Offset(340f, 770f), 1f)
            seat(200f); seat(480f)
        }
    }
}
