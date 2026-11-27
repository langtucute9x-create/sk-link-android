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


/** Thẻ "Mô phỏng": góc nhìn từ trên cao phía sau xe mui trần (thấy hai cánh cửa và khu để chân), đường phố 3D, báo rẽ trái / phải / quá tốc độ theo đúng cài đặt Báo trước và Thời gian đèn chạy. */
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
            val hh = minOf(maxWidth * (DH / DW), maxH).coerceAtLeast(220.dp)
            Box(Modifier.fillMaxWidth().height(hh).clip(RoundedCornerShape(14.dp)).background(Color(0xFF0B0E14))) {
                Scene(Modifier.fillMaxSize())
                Hud()
            }
        }
        Spacer(Modifier.height(8.dp))
        SimStatus()
        Spacer(Modifier.height(6.dp))
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

/** Dòng trạng thái đèn, đặt ngay dưới hình mô phỏng (trước 1.4.113 nằm đè lên đáy hình). */
@Composable
private fun SimStatus() {
    val run = Sim.running.value
    val k = Sim.kind.intValue
    val ak = S.alertKind.intValue
    // đếm giờ đèn báo: ghi lại lúc đèn bắt đầu chạy
    var lit0 by remember { mutableLongStateOf(0L) }
    LaunchedEffect(ak, run) { lit0 = if (run && ak != 0) System.currentTimeMillis() else 0L }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(run) { while (run) { now = System.currentTimeMillis(); kotlinx.coroutines.delay(100) } }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // trạng thái đèn
        val txt = when {
            ak == 1 || ak == 2 -> {
                val left = if (S.alertMaxSec.intValue > 0 && lit0 > 0) max(0, S.alertMaxSec.intValue - ((now - lit0) / 1000).toInt()) else -1
                (if (ak == 1) "ĐÈN BÁO RẼ TRÁI" else "ĐÈN BÁO RẼ PHẢI") + (if (left >= 0) " · còn $left giây" else "")
            }
            ak == 3 -> "QUÁ TỐC ĐỘ · ĐÈN NHÁY"
            ak == 4 -> "CAMERA PHÍA TRƯỚC · ĐÈN NHÁY"
            ak == 5 -> "CAMERA ĐÈN ĐỎ · ĐÈN NHÁY"
            ak == 6 -> "BIỂN CẤM · ĐÈN NHÁY"
            ak == 7 -> "GIỚI HẠN GIẢM · ĐÈN NHÁY"
            ak == 8 -> "QUÁ GẦN XE TRƯỚC · ĐÈN NHÁY"
            ak == 9 -> "GẦN XE TRƯỚC · ĐÈN NHÁY"
            !run && S.navOn.value -> "Đang dẫn đường · ${S.navApp.value}"
            !run -> "Chọn một tình huống bên dưới"
            k == 3 || k >= 4 -> "Đang chạy trong giới hạn"
            Sim.turn.floatValue > 0f -> "Đã qua chỗ rẽ · đèn về như cũ"
            Sim.dist.floatValue > S.alertDist.intValue -> "Chưa tới điểm báo trước (${S.alertDist.intValue} m)"
            else -> "Đèn báo đã hết thời gian chạy"
        }
        val col = when { ak in 3..9 -> Color(0xFF000000 or AlertBridge.showOf(ak).toLong()); ak != 0 -> Color(0xFFF59E0B); else -> Color(0xCC15161C) }
        Box(Modifier.clip(RoundedCornerShape(20.dp)).background(col).padding(horizontal = 14.dp, vertical = 6.dp)) { T(txt, 12, FontWeight.ExtraBold, color = Color.White) }
    }
}

@Composable
private fun Hud() {
    val run = Sim.running.value
    val k = Sim.kind.intValue
    val ak = S.alertKind.intValue
    // Không chạy mô phỏng thì hình hiện dữ liệu thật: tốc độ, biển giới hạn, hướng rẽ và khoảng cách của app dẫn đường.
    val spd = if (run) Sim.speed.floatValue.toInt() else S.carSpeed()
    val lim = if (run) (if (k == 3) Sim.LIMIT else 0) else S.speedLimit.intValue
    val over = lim > 0 && spd > lim
    Box(Modifier.fillMaxSize().padding(8.dp)) {
        // tốc độ + biển giới hạn
        Row(Modifier.align(Alignment.TopStart), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xCC15161C)).padding(horizontal = 10.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                T(spd.toString(), 22, FontWeight.ExtraBold, color = if (over || ak == 3) Color(0xFFFF5252) else Color.White)
                T("km/h", 10, color = Color(0xFFB8BCC6))
            }
            if (lim > 0) { Spacer(Modifier.width(8.dp)); SpeedSign(lim, 42.dp, over) }
        }
        // góc phải: mô phỏng thì khoảng cách tới điểm mô phỏng; bình thường thì chỉ đường thật
        if (run && k != 3) Column(Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(12.dp)).background(Color(0xCC15161C)).padding(horizontal = 12.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            T(if (Sim.turn.floatValue > 0f) "Đang rẽ" else "Còn ${Sim.dist.floatValue.toInt().coerceAtLeast(0)} m", 18, FontWeight.ExtraBold, color = Color.White)
            T(if (k == 1) "◀ rẽ trái" else if (k == 2) "rẽ phải ▶" else "phía trước", 11, color = Color(0xFFB8BCC6))
        }
        else if (!run && S.navOn.value) Row(Modifier.align(Alignment.TopEnd).fillMaxWidth(0.62f).clip(RoundedCornerShape(12.dp)).background(Color(0xCC15161C)).padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            val dir = S.navDir.value
            TurnIcon(dir, 40.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                val d = S.navDist.intValue
                T(if (d < 0) "—" else if (d >= 1000) "${d / 100 / 10.0} km" else "$d m", 18, FontWeight.ExtraBold, color = Color.White, maxLines = 1)
                val dt = L.tr(when (dir) { "L" -> "Rẽ trái"; "R" -> "Rẽ phải"; "U" -> "Quay đầu"; "O" -> "Vòng xuyến"; "A" -> "Đến nơi"; "S" -> "Đi thẳng"; else -> "Chưa rõ hướng" })
                T(if (S.navStreet.value.isEmpty()) dt else "$dt · ${S.navStreet.value}", 11, color = Color(0xFFB8BCC6), maxLines = 1)
            }
        }
    }
}

private fun rnd(i: Int): Float { val x = sin(i * 127.1) * 43758.5; return (x - kotlin.math.floor(x)).toFloat() }

private const val DW = 680f
private const val DH = 716f          // 1.4.113: cắt dải ghế sau ở đáy hình (chủ dự án yêu cầu), cảnh sát hơn
private const val HOR = 240f
private const val FOC = 640f
private const val HC = 2.4f          // mắt cao 2,4 m: góc nhìn từ trên cao phía sau xe (1.4.112)
private const val RW = 5.25f
private const val NR = 3f
private const val FAR = 420f
private val FAC = intArrayOf(0xB8704F, 0xD9C7A3, 0xB9BDC4, 0xE6DCC8, 0x9A5444, 0xCFC8BC, 0xC98F6A, 0xAEB9C6, 0xE9D2B8, 0x8E6A55)      // gạch đỏ, đá be, bê tông xám, kem (kiểu phố trong game lái xe)
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
    val speed = if (run) Sim.speed.floatValue else S.carSpeed().toFloat()
    // khu vực 1 = táp lô + cửa, khu vực 2 = gầm chân; mỗi khu có màu thường ngày riêng khi người dùng đặt hai màu
    val uni = S.czone.intValue == 0
    val idle1: Color? = if (!S.on.value) null else rgbColor(if (uni) S.col[0] else S.col[1])
    val idle2: Color? = if (!S.on.value) null else rgbColor(if (uni) S.col[0] else S.col[2])
    // độ sáng đèn trong hình theo độ sáng đang đặt (không luôn tối đa)
    val bf1 = (S.bri[if (S.zone.intValue == 0) 0 else 1] / 100f).coerceIn(0.12f, 1f)
    val bf2 = (S.bri[if (S.zone.intValue == 0) 0 else 2] / 100f).coerceIn(0.12f, 1f)
    val spz = if (ak in 3..9) S.alertZone[ak] else 0
    val zl1 = ak in 1..2 || (ak in 3..9 && (spz == 0 || spz == 1))      // khu 1 có tham gia cảnh báo đang chạy không
    val zl2 = ak in 1..2 || (ak in 3..9 && (spz == 0 || spz == 2))
    val path = remember { Path() }
    val pA = remember { Path() }
    val pB = remember { Path() }
    val pS = remember { Path() }
    val paint = remember { android.graphics.Paint().apply { isAntiAlias = true; isFakeBoldText = true } }
    Canvas(modifier) {
        val sc = size.width / DW
        val vh = size.height / sc
        val oy = if (vh < DH) -(DH - vh) * 0.85f else 0f      // khung thấp: cắt chủ yếu phần trời, giữ nội thất
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

        /** Một ngôi nhà phố (dựng lại 1.4.113): 3 đến 8 tầng, gờ mái, bồn nước, cửa hàng kính có biển hiệu, cửa sổ có khung. */
        fun bld(sd: Float, bz: Float, idx: Int) {
            val i = if (sd > 0) 1 else 0
            val fl = 3 + (rnd(idx * 2 + i) * 6).toInt()
            val h = fl * 3.3f + 1.2f
            val col = FAC[(rnd(idx * 5 + i + 3) * FAC.size).toInt().coerceIn(0, FAC.size - 1)]
            val x = sd * 8.25f; val z0 = max(bz, NR); val z1 = bz + 14f
            if (z1 <= NR + 1f) return
            val lit = if (sd < 0) 1f else 0.84f                     // dãy bên trái đón nắng
            if (bz > NR) quad(pt(x, bz), pt(sd * 19f, bz), pt(sd * 19f, bz, h), pt(x, bz, h), shade(col, lit * 0.7f))
            quad(pt(x, z0), pt(x, z1), pt(x, z1, h), pt(x, z0, h), shade(col, lit))
            quad(pt(x, z0, h - 0.45f), pt(x, z1, h - 0.45f), pt(x, z1, h), pt(x, z0, h), shade(col, lit * 0.6f))
            quad(pt(x, z0, h), pt(x, z1, h), pt(x, z1, h + 0.28f), pt(x, z0, h + 0.28f), shade(col, lit * 1.14f))
            if (bz > NR && bz < 130f && rnd(idx * 13 + i) > 0.45f) {
                val za = bz + 3f + rnd(idx * 17 + i) * 5f; val xr = x + sd * 1.2f
                quad(pt(xr, za, h + 0.28f), pt(xr, za + 2.2f, h + 0.28f), pt(xr, za + 2.2f, h + 1.9f), pt(xr, za, h + 1.9f), Color(0xFF8E959F))
                quad(pt(xr, za, h + 1.9f), pt(xr, za + 2.2f, h + 1.9f), pt(xr, za + 2.2f, h + 2.1f), pt(xr, za, h + 2.1f), Color(0xFF6C737D))
            }
            if (bz < 150f) {
                val a = max(bz + 0.6f, NR); val b = bz + 13.4f
                if (b > a + 0.5f) {
                    quad(pt(x, a), pt(x, b), pt(x, b, 3.0f), pt(x, a, 3.0f), Color(0xFF1F2A3A))
                    quad(pt(x, a, 1.1f), pt(x, b, 1.1f), pt(x, b, 2.7f), pt(x, a, 2.7f), Color(0xFF3B5270))
                    pA.reset()
                    for (j in 1..3) { val zz = bz + 0.6f + j * 3.2f; if (zz > NR) addQuad(pA, pt(x, zz), pt(x, zz + 0.2f), pt(x, zz + 0.2f, 3.0f), pt(x, zz, 3.0f)) }
                    drawPath(pA, shade(col, lit * 0.5f))
                    quad(pt(x, a, 3.0f), pt(x, b, 3.0f), pt(x, b, 3.45f), pt(x, a, 3.45f), AWN[(((idx + i) % 4) + 4) % 4])
                    val sa = max(bz + 3f, NR); val sb = bz + 11f
                    if (sb > sa + 1f) quad(pt(x, sa, 3.6f), pt(x, sb, 3.6f), pt(x, sb, 4.5f), pt(x, sa, 4.5f), Color(0xFFF4F1E8))
                }
            }
            if (bz < 110f) {
                pA.reset(); pB.reset(); pS.reset()
                for (f in 1 until fl) for (j in 0..3) {
                    val za = bz + 1.1f + j * 3.2f; val zb = za + 2.0f
                    if (za < NR + 0.2f) continue
                    val ya = f * 3.3f + 1.9f; val yb = ya + 1.9f
                    addQuad(pS, pt(x, za - 0.2f, ya - 0.2f), pt(x, zb + 0.2f, ya - 0.2f), pt(x, zb + 0.2f, yb + 0.16f), pt(x, za - 0.2f, yb + 0.16f))
                    addQuad(if (rnd(idx * 31 + f * 7 + j + i * 3) > 0.7f) pB else pA, pt(x, za, ya), pt(x, zb, ya), pt(x, zb, yb), pt(x, za, yb))
                }
                if (bz > NR && bz < 70f) for (f in 1 until fl) for (j in 0..2) {
                    val xa = sd * (9.6f + j * 3f); val xb = xa + sd * 1.8f; val y1 = f * 3.3f + 1.9f; val y2 = y1 + 1.9f
                    addQuad(pS, pt(xa - sd * 0.18f, bz, y1 - 0.2f), pt(xb + sd * 0.18f, bz, y1 - 0.2f), pt(xb + sd * 0.18f, bz, y2 + 0.16f), pt(xa - sd * 0.18f, bz, y2 + 0.16f))
                    addQuad(pA, pt(xa, bz, y1), pt(xb, bz, y1), pt(xb, bz, y2), pt(xa, bz, y2))
                }
                drawPath(pS, shade(col, lit * 1.2f)); drawPath(pA, Color(0xFF34435C)); drawPath(pB, Color(0xFF86A3C8))
            }
        }
        /** Một cây bên đường (dựng lại 1.4.113): bóng đổ, thân thon, cành, tán nhiều cụm lá sáng tối; mỗi cây một cỡ. */
        fun tree(sd: Float, d: Float, idx: Int) {
            if (d < 6f) return
            val s = FOC / d; val x = sd * 6.7f
            val k = 0.85f + rnd(idx * 7 + (if (sd > 0) 3 else 1)) * 0.5f
            val foot = pt(x, d)
            drawOval(Color(0x38000000), Offset(foot.x - 1.9f * k * s, foot.y - 0.22f * s), Size(3.8f * k * s, 0.5f * s))
            quad(pt(x - 0.45f, d), pt(x + 0.45f, d), pt(x + 0.45f, d, 0.12f), pt(x - 0.45f, d, 0.12f), Color(0xFF8D8A82))
            quad(pt(x - 0.17f, d), pt(x + 0.17f, d), pt(x + 0.09f, d, 2.8f * k), pt(x - 0.09f, d, 2.8f * k), Color(0xFF6B4E32))
            val bw = max(1f, 0.1f * s)
            ln(pt(x, d, 2.2f * k), pt(x - 0.8f * k, d, 3.3f * k), bw, Color(0xFF5D4630)); ln(pt(x, d, 2.4f * k), pt(x + 0.7f * k, d, 3.4f * k), bw, Color(0xFF5D4630))
            val cy = 4.3f * k
            fun leaf(dx: Float, dy: Float, r: Float, col: Long) = drawCircle(Color(col), r * k * s, pt(x + dx * k, d, cy + dy * k))
            leaf(0f, -0.35f, 1.55f, 0xFF28552B); leaf(-0.95f, 0.05f, 1.15f, 0xFF2F6A34); leaf(0.95f, 0f, 1.2f, 0xFF357438)
            leaf(-0.3f, 0.8f, 1.2f, 0xFF4C8F45); leaf(0.55f, 0.9f, 1f, 0xFF63A856); leaf(-0.5f, 1.35f, 0.55f, 0xCC8ACB70)
        }
        fun lamp(sd: Float, d: Float) {
            if (d < 6f) return
            val s = FOC / d; val x = sd * 5.8f
            ln(pt(x, d), pt(x, d, 7.5f), max(1f, 0.14f * s), Color(0xFF5A5F69))
            ln(pt(x, d, 7.5f), pt(sd * 4.3f, d, 7.7f), max(1f, 0.12f * s), Color(0xFF5A5F69))
            drawCircle(Color(0xFFF3EFE0), max(1.5f, 0.2f * s), pt(sd * 4.3f, d, 7.6f))
        }
        /** Đuôi một xe đang chạy phía trước, cách mắt [d] mét, lệch ngang [x] mét. */
        fun veh(x: Float, d: Float, pickup: Boolean) {
            if (d < NR + 3f) return
            val s = FOC / d
            val tyre = Color(0xFF16171B)
            fun R(x0: Float, x1: Float, y0: Float, y1: Float, col: Color, rad: Float = 0.08f) {
                val a = pt(x + x0, d, y1); val b = pt(x + x1, d, y0)
                drawRoundRect(col, a, Size(b.x - a.x, b.y - a.y), CornerRadius(rad * s))
            }
            drawOval(Color(0x59000000), pt(x - 1.05f, d, 0.12f), Size(2.1f * s, 0.24f * s))
            if (pickup) {
                R(-0.95f, -0.62f, 0f, 0.55f, tyre); R(0.62f, 0.95f, 0f, 0.55f, tyre)
                R(-1f, 1f, 0.42f, 1.22f, Color(0xFFA51C28)); R(-0.86f, 0.86f, 0.62f, 1.12f, Color(0xFF6E0F18), 0.04f)
                R(-0.8f, 0.8f, 1.2f, 1.92f, Color(0xFFB3202C), 0.14f); R(-0.62f, 0.62f, 1.36f, 1.8f, Color(0xFF263042), 0.08f)
                R(-0.99f, -0.86f, 0.66f, 1.1f, Color(0xFFFF3340), 0.03f); R(0.86f, 0.99f, 0.66f, 1.1f, Color(0xFFFF3340), 0.03f)
                R(-0.9f, 0.9f, 0.3f, 0.44f, Color(0xFFC9CED6), 0.04f)
            } else {
                R(-0.9f, -0.6f, 0f, 0.5f, tyre); R(0.6f, 0.9f, 0f, 0.5f, tyre)
                R(-0.92f, 0.92f, 0.3f, 0.98f, Color(0xFFF2F4F7), 0.18f); R(-0.72f, 0.72f, 0.92f, 1.42f, Color(0xFFDDE1E8), 0.2f); R(-0.6f, 0.6f, 1f, 1.34f, Color(0xFF2B3444), 0.1f)
                R(-0.9f, -0.5f, 0.7f, 0.86f, Color(0xFFD8242E), 0.05f); R(0.5f, 0.9f, 0.7f, 0.86f, Color(0xFFD8242E), 0.05f)
            }
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
                    if (((idx % 2) + 2) % 2 == 0) tree(e, bz + 15f, idx) else lamp(e, bz + 15f)
                    bld(e, bz, idx + seed)
                }
            }
            drawRect(Brush.verticalGradient(0f to Color(0x00DCECFA), 0.75f to Color(0x47DCECFA), 1f to Color(0x00DCECFA), startY = HOR - 40f, endY = HOR + 14f), Offset(0f, HOR - 40f), Size(DW, 54f))
            val sz = dist + 3f
            // hai xe chạy phía trước (kiểu game lái xe): bán tải đỏ cùng làn, xe trắng làn trái; lúc gần lúc xa
            val d1 = 24f + 5f * sin(clock * 0.5f); val d2 = 46f + 10f * sin(clock * 0.3f + 1f)
            val hasSign = signs && kind in 4..7 && sz > NR + 1f
            if (!hasSign || d2 > sz) veh(-3.5f, d2, false)
            if (!hasSign || d1 > sz) veh(0f, d1, true)
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
            if (hasSign) { if (d2 <= sz) veh(-3.5f, d2, false); if (d1 <= sz) veh(0f, d1, true) }
        }

        val act = ak != 0
        val on = act && (ak in 1..2 || phase)
        val kc = if (ak in 3..9) rgbColor(AlertBridge.showOf(ak)) else Color(0xFFFFC400)
        fun amt(u: Float): Float {
            if (!on) return 0f
            if (ak > 2) return 1f
            val ph = ((clock * 1000f) % 900f) / 900f
            val pos = if (ak == 2) -0.2f + 1.4f * ph else 1.2f - 1.4f * ph
            return max(0.18f, 1f - abs(u - pos) / 0.25f)
        }
        fun led(a: Offset, b: Offset, u: Float, w: Float, zoneLit: Boolean = zl1, idle: Color? = idle1, f: Float = bf1) {
            val m = if (zoneLit) min(1f, amt(u)) * f else 0f
            ln(a, b, w, Color(0xFF2A2F39))
            if ((!act || !zoneLit) && idle != null) { ln(a, b, w * 3f, idle.copy(alpha = 0.20f * f)); ln(a, b, w, idle.copy(alpha = 0.95f * f)); ln(a, b, w * 0.3f, Color.White.copy(alpha = 0.55f * f)) }
            if (m > 0f) { ln(a, b, w * 3f, kc.copy(alpha = 0.22f * m)); ln(a, b, w, kc.copy(alpha = m)) }
        }
        /** Quầng sáng hắt của một khu đèn: màu cảnh báo khi khu đó đang báo, không thì màu thường ngày. */
        fun halo(x: Float, y: Float, r: Float, u: Float, zoneLit: Boolean, idle: Color?, f: Float, k: Float) {
            val c = Offset(x, y)
            val a = if (zoneLit) min(1f, amt(u)) else 0f
            if (a > 0f) drawCircle(Brush.radialGradient(listOf(kc.copy(alpha = k * a * f), kc.copy(alpha = 0f)), c, r), r, c)
            else if ((!act || !zoneLit) && idle != null) drawCircle(Brush.radialGradient(listOf(idle.copy(alpha = k * 0.8f * f), idle.copy(alpha = 0f)), c, r), r, c)
        }
        fun poly(vararg v: Float) { path.reset(); path.moveTo(v[0], v[1]); var i = 2; while (i < v.size) { path.lineTo(v[i], v[i + 1]); i += 2 }; path.close() }
        val lea = listOf(Color(0xFF9A4458), Color(0xFF6B2A3A), Color(0xFF3D1420))       // da đỏ nâu
        val trim = listOf(Color(0xFF2A2C3A), Color(0xFF0C0C11))
        val dark = Color(0xFF101116)
        /** Dải đèn chạy theo đường gấp khúc [v] (x, y, x, y…); [uOf] cho vị trí ngang 0..1 của từng đoạn để đèn báo rẽ chạy đuổi. */
        fun ledLine(w: Float, zoneLit: Boolean, idle: Color?, f: Float, uOf: (Float, Float) -> Float, vararg v: Float) {
            var i = 0
            while (i + 3 < v.size) {
                val a = Offset(v[i], v[i + 1]); val b = Offset(v[i + 2], v[i + 3])
                val n = max(1, (kotlin.math.hypot(b.x - a.x, b.y - a.y) / 40f).toInt())
                for (j in 0 until n) {
                    val p = Offset(a.x + (b.x - a.x) * j / n, a.y + (b.y - a.y) * j / n); val q = Offset(a.x + (b.x - a.x) * (j + 1) / n, a.y + (b.y - a.y) * (j + 1) / n)
                    led(p, q, uOf((p.x + q.x) / 2f, (p.y + q.y) / 2f), w, zoneLit, idle, f)
                }
                i += 2
            }
        }
        /** Dải đèn theo đường cong bậc hai. */
        fun ledQ(x0: Float, y0: Float, cx: Float, cy: Float, x1: Float, y1: Float, w: Float) {
            fun q(t: Float) = Offset((1 - t) * (1 - t) * x0 + 2 * (1 - t) * t * cx + t * t * x1, (1 - t) * (1 - t) * y0 + 2 * (1 - t) * t * cy + t * t * y1)
            for (i in 0 until 10) { val a = q(i / 10f); val b = q((i + 1) / 10f); led(a, b, (a.x + b.x) / 2560f, w) }
        }
        /** Cửa trước + khu để chân một bên ([m] = bên phải, vẽ đối xứng). */
        fun side(m: Boolean) {
            fun X(x: Float) = if (m) 1280f - x else x
            poly(X(128f), 348f, X(150f), 352f, X(40f), 720f, X(0f), 720f, X(0f), 560f)
            drawPath(path, Brush.horizontalGradient(listOf(Color(0xFFC9CED8), Color(0xFFF7F8FB)), X(0f), X(150f)))
            path.reset(); path.moveTo(X(96f), 300f); path.quadraticBezierTo(X(150f), 286f, X(176f), 312f); path.lineTo(X(168f), 336f); path.lineTo(X(104f), 330f); path.close()
            drawPath(path, dark)
            poly(X(205f), 332f, X(250f), 432f, X(172f), 720f, X(40f), 720f, X(150f), 352f)
            drawPath(path, Brush.verticalGradient(trim, 332f, 720f))
            poly(X(162f), 372f, X(198f), 372f, X(236f), 440f, X(168f), 700f, X(80f), 700f)
            drawPath(path, Brush.verticalGradient(lea, 372f, 700f))
            halo(X(150f), 540f, 190f, if (m) 0.9f else 0.1f, zl1, idle1, bf1, 0.5f)
            poly(X(176f), 470f, X(214f), 470f, X(196f), 560f, X(150f), 560f)
            drawPath(path, Color(0xD9101116))
            val sp = Offset(X(172f), 412f)
            drawCircle(Color(0xFF0C0C11), 17f, sp); drawCircle(Color(0xFF4A4F66), 17f, sp, style = Stroke(2f)); drawCircle(Color(0xFF2C3044), 8f, sp, style = Stroke(2f))
            // khu để chân: thảm trần trám, sáng theo đèn gầm (khu vực 2)
            poly(X(250f), 432f, X(586f), 432f, X(578f), 542f, X(268f), 542f)
            drawPath(path, Brush.verticalGradient(listOf(Color(0xFF1A0A12), Color(0xFF4A1A2C)), 432f, 542f))
            clipPath(path) {
                pA.reset(); var o = -200f
                while (o <= 520f) { pA.moveTo(X(250f + o), 432f); pA.lineTo(X(250f + o + 110f), 542f); pA.moveTo(X(250f + o + 110f), 432f); pA.lineTo(X(250f + o), 542f); o += 22f }
                drawPath(pA, Color(0x66C96A86), style = Stroke(1.2f))
                halo(X(420f), 492f, 230f, if (m) 0.67f else 0.33f, zl2, idle2, bf2, 0.85f)
            }
            // dải đèn viền quanh ốp cửa (khu vực 1), chạy từ đầu xe ra sau
            ledLine(5f, zl1, idle1, bf1, { _, y -> val k = 0.2f * (1f - ((y - 364f) / 344f).coerceIn(0f, 1f)); if (m) 1f - k else k }, X(160f), 364f, X(202f), 364f, X(244f), 440f, X(178f), 708f)
        }
        /** Ghế trước nhìn từ sau, đèn gầm ghế. */
        fun seat(m: Boolean) {
            fun X(x: Float) = if (m) 1280f - x else x
            pS.reset(); pS.moveTo(X(292f), 566f); pS.quadraticBezierTo(X(296f), 542f, X(346f), 540f); pS.lineTo(X(494f), 540f); pS.quadraticBezierTo(X(544f), 542f, X(548f), 566f)
            pS.lineTo(X(562f), 668f); pS.lineTo(X(278f), 668f); pS.close()
            drawPath(pS, Brush.verticalGradient(lea, 540f, 668f))
            path.reset(); path.moveTo(X(322f), 580f); path.quadraticBezierTo(X(420f), 566f, X(518f), 580f); path.lineTo(X(526f), 650f); path.lineTo(X(314f), 650f); path.close()
            drawPath(path, Color(0x99C9788A), style = Stroke(1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))))
            val hx = if (m) 1280f - 352f - 136f else 352f
            drawRoundRect(Brush.verticalGradient(lea, 498f, 558f), Offset(hx, 498f), Size(136f, 60f), CornerRadius(24f))
            path.reset(); path.moveTo(X(366f), 508f); path.quadraticBezierTo(X(420f), 498f, X(474f), 508f)
            drawPath(path, Color(0x2EFFFFFF), style = Stroke(2f))
            ledLine(5f, zl2, idle2, bf2, { x, _ -> x / 1280f }, X(290f), 664f, X(550f), 664f)
        }
        /** Nội thất xe mui trần nhìn từ trên cao phía sau, vẽ trên khung 1280 x 720 (theo bản demo chủ dự án duyệt 27/11). */
        fun cabin() {
            // nắp ca-pô nhìn qua kính, kính lái, khung kính, cột A, gương trong xe
            path.reset(); path.moveTo(268f, 288f); path.quadraticBezierTo(640f, 262f, 1012f, 288f); path.lineTo(1055f, 330f); path.quadraticBezierTo(640f, 296f, 225f, 330f); path.close()
            drawPath(path, Color(0xFFEEF0F5))
            path.reset(); path.moveTo(250f, 262f); path.quadraticBezierTo(640f, 228f, 1030f, 262f); path.lineTo(1075f, 330f); path.quadraticBezierTo(640f, 300f, 205f, 330f); path.close()
            drawPath(path, Color(0x389FC4EE))
            path.reset(); path.moveTo(250f, 262f); path.quadraticBezierTo(640f, 228f, 1030f, 262f)
            drawPath(path, dark, style = Stroke(11f, cap = StrokeCap.Round)); drawPath(path, Color(0xCC8D93A6), style = Stroke(1.5f))
            ln(Offset(250f, 262f), Offset(205f, 332f), 12f, dark); ln(Offset(1030f, 262f), Offset(1075f, 332f), 12f, dark)
            rrect(596f, 246f, 88f, 24f, 8f, Color(0xFF0D0E13))
            // sàn xe kín bên dưới mọi chi tiết (bản 1.4.112 hở các khe nên thấy vạch đường xanh xuyên qua sàn)
            poly(205f, 330f, 1075f, 330f, 1152f, 348f, 1280f, 560f, 1280f, 720f, 0f, 720f, 0f, 560f, 128f, 348f)
            drawPath(path, Color(0xFF140A10))
            // mặt trên táp lô + dải đèn sát kính
            path.reset(); path.moveTo(205f, 330f); path.quadraticBezierTo(640f, 300f, 1075f, 330f); path.lineTo(1060f, 372f); path.quadraticBezierTo(640f, 345f, 220f, 372f); path.close()
            drawPath(path, Color(0xFF14151C))
            ledQ(640f, 322f, 430f, 322f, 228f, 341f, 4f); ledQ(640f, 322f, 850f, 322f, 1052f, 341f, 4f)
            // màn hình giữa: bản đồ, đường đi rẽ theo tình huống
            rrect(632f, 330f, 16f, 16f, 0f, Color(0xFF0A0A0E)); rrect(586f, 292f, 108f, 46f, 6f, Color(0xFF07080C))
            drawRoundRect(Color(0xFF3A3F55), Offset(586f, 292f), Size(108f, 46f), CornerRadius(6f), style = Stroke(2f))
            rrect(592f, 298f, 96f, 34f, 3f, Color(0xFF102A5C))
            ln(Offset(640f, 328f), Offset(640f, 314f), 4f, Color(0xFF7FE0FF))
            if (run && kind in 1..2 && turnP == 0f) ln(Offset(640f, 314f), Offset(640f + tsd * 34f, 314f), 4f, Color(0xFF7FE0FF)) else ln(Offset(640f, 314f), Offset(640f, 302f), 4f, Color(0xFF7FE0FF))
            // mặt táp lô, cụm đồng hồ, nẹp carbon, dải đèn ngang táp lô
            path.reset(); path.moveTo(220f, 372f); path.quadraticBezierTo(640f, 345f, 1060f, 372f); path.lineTo(1045f, 432f); path.quadraticBezierTo(640f, 410f, 235f, 432f); path.close()
            drawPath(path, Brush.verticalGradient(trim, 350f, 432f))
            halo(400f, 392f, 300f, 0.3f, zl1, idle1, bf1, 0.28f); halo(880f, 392f, 300f, 0.7f, zl1, idle1, bf1, 0.28f)
            drawOval(Color(0xFF08090D), Offset(336f, 346f), Size(188f, 56f))
            for (gx in floatArrayOf(398f, 462f)) { drawCircle(Color(0xFF0D0D12), 17f, Offset(gx, 378f)); drawCircle(Color(0xFFFF7A1A), 17f, Offset(gx, 378f), style = Stroke(2.5f)) }
            val na = Math.toRadians((150.0 + 240.0 * (speed / 160f).coerceIn(0f, 1f)))
            ln(Offset(398f, 378f), Offset(398f + (kotlin.math.cos(na) * 13).toFloat(), 378f + (sin(na) * 13).toFloat()), 2.5f, Color(0xFFFF3B30))
            ln(Offset(462f, 378f), Offset(472f, 366f), 2.5f, Color(0xFFFF3B30))
            rrect(760f, 372f, 240f, 20f, 6f, Color(0xFF23252E))
            ledQ(585f, 383f, 410f, 385f, 242f, 402f, 4f); ledQ(695f, 383f, 870f, 385f, 1038f, 402f, 4f)
            // hai cửa + hai khu để chân
            side(false); side(true)
            rrect(500f, 470f, 34f, 50f, 6f, Color(0xFF0C0C10)); rrect(440f, 476f, 30f, 44f, 6f, Color(0xFF0C0C10))
            drawRoundRect(Color(0xFF555A70), Offset(500f, 470f), Size(34f, 50f), CornerRadius(6f), style = Stroke(1.5f)); drawRoundRect(Color(0xFF555A70), Offset(440f, 476f), Size(30f, 44f), CornerRadius(6f), style = Stroke(1.5f))
            // sàn sau lưng ghế trước sáng theo đèn gầm
            halo(640f, 690f, 520f, 0.5f, zl2, idle2, bf2, 0.6f)
            // bệ giữa
            poly(585f, 356f, 695f, 356f, 712f, 664f, 568f, 664f)
            drawPath(path, Brush.verticalGradient(trim, 356f, 664f))
            clipPath(path) { pA.reset(); var o = 300f; while (o < 1100f) { pA.moveTo(o, 356f); pA.lineTo(o - 320f, 676f); o += 9f }; drawPath(pA, Color(0x24FFFFFF), style = Stroke(1.5f)) }
            rrect(598f, 364f, 84f, 16f, 4f, Color(0xFF08090D)); rrect(598f, 388f, 84f, 26f, 4f, Color(0xFF08090D))
            drawCircle(Color(0xFFFF7A1A), 6f, Offset(616f, 401f)); drawCircle(Color(0xFFFF7A1A), 6f, Offset(664f, 401f)); rrect(630f, 395f, 20f, 12f, 2f, Color(0xFF7FE0FF))
            rrect(622f, 470f, 36f, 52f, 14f, Color(0xFF0B0B10)); drawRoundRect(Color(0xFF8D93A6), Offset(622f, 470f), Size(36f, 52f), CornerRadius(14f), style = Stroke(1.5f)); drawCircle(Color(0xFF2A2D3A), 9f, Offset(640f, 486f))
            for (cxh in floatArrayOf(618f, 662f)) { drawCircle(Color(0xFF07080B), 15f, Offset(cxh, 570f)); drawCircle(Color(0xFF555A70), 15f, Offset(cxh, 570f), style = Stroke(1.5f)) }
            drawRoundRect(Brush.verticalGradient(lea, 606f, 654f), Offset(596f, 606f), Size(88f, 48f), CornerRadius(10f))
            ledLine(4f, zl1, idle1, bf1, { _, _ -> 0.45f }, 585f, 434f, 568f, 662f); ledLine(4f, zl1, idle1, bf1, { _, _ -> 0.55f }, 695f, 434f, 712f, 662f)
            // vô lăng
            drawOval(Color(0xFF0B0B0F), Offset(348f, 378f), Size(164f, 120f), style = Stroke(18f))
            drawOval(Color(0xB35A6078), Offset(348f, 378f), Size(164f, 120f), style = Stroke(1.5f))
            ln(Offset(352f, 440f), Offset(404f, 442f), 14f, Color(0xFF0B0B0F)); ln(Offset(508f, 440f), Offset(456f, 442f), 14f, Color(0xFF0B0B0F)); ln(Offset(430f, 496f), Offset(430f, 458f), 14f, Color(0xFF0B0B0F))
            drawOval(dark, Offset(402f, 424f), Size(56f, 40f)); drawOval(Color(0xFF5A6078), Offset(402f, 424f), Size(56f, 40f), style = Stroke(1.5f)); drawCircle(Color(0xFFC9CED8), 7f, Offset(430f, 444f))
            seat(false); seat(true)
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

            // ---- nội thất: khung 1280 x 720 của bản demo, ép vào bề ngang 680 và đặt xuống nửa dưới khung hình ----
            withTransform({ translate(0f, 172f); scale(DW / 1280f, 0.8f, Offset.Zero) }) { cabin() }
        }
    }
}
