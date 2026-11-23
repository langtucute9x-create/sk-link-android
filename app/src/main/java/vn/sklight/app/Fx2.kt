package vn.sklight.app

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

// ===================== GIAO DIỆN NỔI KHỐI (đợt 2): hình xe 2,5D, radar ghép nối, phích cắm =====================
// Ý tưởng hình thức tham khảo kho Lucian UX/UI (MIT, github.com/Seokwoooo/lucian-uxui-prompt): mẫu 090, 186, 202. Viết lại bằng Compose.

private val SEG_TINY = listOf("Đồng hồ T", "Đồng hồ P", "Trước T", "Trước P", "Sau T", "Sau P")
private fun metal(dark: Boolean) = if (dark) listOf(Color(0xFF3A3D4B), Color(0xFF5A5E70), Color(0xFF2E303C)) else listOf(Color(0xFFB4B9C6), Color(0xFFE6E8EF), Color(0xFFA4A9B7))

@Composable
private fun blinkAlpha(run: Boolean, ms: Int): Float {
    if (!run) return 1f
    val tr = rememberInfiniteTransition(label = "blink")
    val a by tr.animateFloat(1f, 0.3f, infiniteRepeatable(tween(ms, easing = LinearEasing), RepeatMode.Reverse), label = "blinkA")
    return a
}

@Composable
private fun popAnim(on: Boolean): Float {
    val spec: AnimationSpec<Float> = if (Fx) spring(dampingRatio = 0.4f, stiffness = 300f) else snap()
    val v by animateFloatAsState(if (on) 1f else 0f, spec, label = "pop")
    return v
}

/** Hình xe nhìn từ trên, nghiêng 2,5D. Đoạn 0, 1 = nửa bảng đồng hồ trái / phải sáng; 2..5 = một cửa (mở rộng khi [on], hé khi không). */
@Composable
fun CarIcon(seg: Int, on: Boolean, col: Color, modifier: Modifier = Modifier) {
    val fx = Fx
    val left = seg % 2 == 0
    val spec: AnimationSpec<Float> = if (fx) spring(dampingRatio = 0.5f, stiffness = 300f) else snap()
    val open by animateFloatAsState(if (on) 40f else 16f, spec, label = "door")
    val lift by animateFloatAsState(if (on) 1f else 0f, spec, label = "lift")
    val dark = Theme.dark
    val line = if (on) col else Grey
    val body = metal(dark)
    val glass = if (dark) Color(0xBF0B0B0E) else Color(0x8C172032)
    val roof = Color.White.copy(alpha = if (dark) 0.10f else 0.45f)
    val tilt = if (fx) Modifier.graphicsLayer { rotationX = 32f - 10f * lift; val s = 1f + 0.08f * lift; scaleX = s; scaleY = s } else Modifier
    Canvas(modifier.then(tilt)) {
        val u = size.width / 80f
        if (fx) drawOval(
            Brush.radialGradient(listOf(Color.Black.copy(alpha = if (dark) 0.6f else 0.22f), Color.Black.copy(alpha = 0f)), center = Offset(40f * u, 62f * u), radius = 46f * u),
            Offset(6f * u, 10f * u), Size(68f * u, 104f * u)
        )
        val cr = CornerRadius(14f * u, 14f * u)
        drawRoundRect(Brush.horizontalGradient(body, startX = 22f * u, endX = 58f * u), Offset(22f * u, 8f * u), Size(36f * u, 94f * u), cr)
        drawRoundRect(line, Offset(22f * u, 8f * u), Size(36f * u, 94f * u), cr, style = Stroke(1.6f * u))
        drawPath(Path().apply { moveTo(27f * u, 27f * u); quadraticTo(40f * u, 20f * u, 53f * u, 27f * u); lineTo(50f * u, 38f * u); lineTo(30f * u, 38f * u); close() }, glass)
        drawPath(Path().apply { moveTo(30f * u, 80f * u); lineTo(50f * u, 80f * u); lineTo(53f * u, 88f * u); quadraticTo(40f * u, 94f * u, 27f * u, 88f * u); close() }, glass)
        drawRoundRect(roof, Offset(29f * u, 42f * u), Size(22f * u, 34f * u), CornerRadius(5f * u, 5f * u))
        if (seg < 2) {
            val x1 = if (left) 27f else 40f
            val p1 = Offset(x1 * u, 40f * u)
            val p2 = Offset((x1 + 13f) * u, 40f * u)
            drawLine(col.copy(alpha = 0.3f), p1, p2, (if (on) 7f else 4f) * u, StrokeCap.Round)
            drawLine(col, p1, p2, 2.6f * u, StrokeCap.Round)
        } else {
            val hx = if (left) 22f else 58f
            val y = if (seg < 4) 38f else 64f
            val p1 = Offset(hx * u, y * u)
            val p2 = Offset(hx * u, (y + 24f) * u)
            rotate(if (left) open else -open, p1) {
                drawLine(col.copy(alpha = 0.3f), p1, p2, (if (on) 9f else 6f) * u, StrokeCap.Round)
                drawLine(col, p1, p2, 4f * u, StrokeCap.Round)
            }
        }
    }
}

/** Hàng 6 hình xe chọn đoạn của sơ đồ xe. Đang chọn hộp phụ (hộp đang nháy) thì 4 hình cửa nháy viền, chạm hình nào là gán hộp vào cửa đó. */
@Composable
fun CarRow(sel: Int, onPick: (Int) -> Unit) {
    val picking = S.pickBox.intValue > 0
    val blink = blinkAlpha(Fx && picking, 600)
    val dark = Theme.dark
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (k in 0..5) {
            val on = k == sel
            val col = SEG_COLORS[k]
            val ask = picking && k >= 2 && !on
            val sh = RoundedCornerShape(Theme.r(10))
            val box = S.boxPos.indexOf(k)
            Box(
                Modifier.weight(1f).clip(sh)
                    .background(if (on) dim(CardBg, if (dark) 0.30f else 0.06f) else Color.Transparent)
                    .border(if (on) 1.5.dp else 1.dp, if (on) col else if (ask) Pink.copy(alpha = blink) else Color.Transparent, sh)
                    .clickable { if (picking && k >= 2) S.assignBox(k); onPick(k) }
                    .padding(top = 2.dp, bottom = 4.dp)
            ) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    // số hộp phụ nằm ngay góc hình xe (không trôi ra góc ô khi ô rộng), cỡ chữ cố định theo cỡ vòng tròn
                    Box(contentAlignment = Alignment.TopEnd) {
                        CarIcon(k, on, col, Modifier.widthIn(max = 58.dp).fillMaxWidth().aspectRatio(80f / 112f))
                        if (box >= 0 && k >= 2) Box(Modifier.size(18.dp).clip(CircleShape).background(col), contentAlignment = Alignment.Center) {
                            val fs = with(LocalDensity.current) { 11.dp.toSp() }
                            Text("${box + 1}", fontSize = fs, lineHeight = fs, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = Inter, maxLines = 1)
                        }
                    }
                    FitT(SEG_TINY[k], 10, if (on) FontWeight.Bold else FontWeight.Normal, if (on) Txt else Grey, lines = 1)
                }
            }
        }
    }
}

/** Radar ghép nối hộp phụ: quay khi [active]; mỗi hộp đã thấy là một chấm (chỉ thể hiện số lượng, không phải vị trí thật). */
@Composable
fun PairRadar(active: Boolean, found: Int, modifier: Modifier = Modifier) {
    val fx = Fx
    val a = Pink
    val dark = Theme.dark
    val line = Bd
    val ang = if (fx && active) {
        val tr = rememberInfiniteTransition(label = "radar")
        val v by tr.animateFloat(0f, 360f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "radarA")
        v
    } else 0f
    val pops = floatArrayOf(popAnim(found > 0), popAnim(found > 1), popAnim(found > 2), popAnim(found > 3))
    Canvas(modifier.then(if (fx) Modifier.graphicsLayer { rotationX = 26f } else Modifier)) {
        val r = size.minDimension / 2f
        val c = center
        drawCircle(Brush.radialGradient(if (dark) listOf(Color(0xFF191A22), Color(0xFF0B0B0E)) else listOf(Color(0xFFFFFFFF), Color(0xFFDDE3EF)), c, r), r, c)
        for (k in 1..3) drawCircle(a.copy(alpha = 0.28f), r * k / 3.3f, c, style = Stroke(1.dp.toPx()))
        if (fx && active) rotate(ang, c) {
            drawCircle(Brush.sweepGradient(0f to a.copy(alpha = 0f), 0.72f to a.copy(alpha = 0f), 0.995f to a.copy(alpha = 0.55f), 1f to a.copy(alpha = 0f), center = c), r, c)
        }
        drawCircle(line, r - 0.5.dp.toPx(), c, style = Stroke(1.dp.toPx()))
        drawCircle(a, 3.dp.toPx(), c)
        val px = floatArrayOf(-0.45f, 0.48f, -0.34f, 0.42f)
        val py = floatArrayOf(-0.40f, -0.48f, 0.45f, 0.38f)
        for (i in 0..3) if (pops[i] > 0.01f) {
            val o = Offset(c.x + px[i] * r, c.y + py[i] * r)
            val rr = 6.dp.toPx() * pops[i]
            drawCircle(a.copy(alpha = 0.3f), rr * 1.7f, o)
            drawCircle(Brush.radialGradient(listOf(Color.White, lite(a, 0.4f), a), Offset(o.x - rr * 0.3f, o.y - rr * 0.35f), rr * 1.4f), rr, o)
        }
    }
}

/** Thẻ mỏng đầu trang Đèn khi chưa kết nối với hộp đèn: phích cắm rời ổ; nối được thì cắm vào, đèn xanh, rồi thẻ tự ẩn. Chạm để thử nối lại. */
@Composable
fun PlugCard() {
    val con = S.connected.value
    var show by remember { mutableStateOf(!con) }
    LaunchedEffect(con) { if (!con) show = true else { delay(1400); show = false } }
    if (!show) return
    val fx = Fx
    val a = Pink
    val dark = Theme.dark
    val spec: AnimationSpec<Float> = if (fx) spring(dampingRatio = 0.5f, stiffness = 250f) else snap()
    val t by animateFloatAsState(if (con) 1f else 0f, spec, label = "plug")
    val zap = blinkAlpha(fx && !con, 450)
    val none = S.savedMac.isEmpty()
    val body = metal(dark)
    val cable = if (dark) Color(0xFF4A4D5C) else Color(0xFF8E93A3)
    Card(Modifier.clickable { S.autoConnect() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(112.dp, 42.dp)) {
                val u = size.width / 150f
                drawRoundRect(Brush.horizontalGradient(body, startX = 104f * u, endX = 144f * u), Offset(104f * u, 8f * u), Size(40f * u, 40f * u), CornerRadius(9f * u, 9f * u))
                drawRoundRect(Color(0xFF0B0B0E), Offset(104f * u, 20f * u), Size(9f * u, 5f * u), CornerRadius(2f * u, 2f * u))
                drawRoundRect(Color(0xFF0B0B0E), Offset(104f * u, 31f * u), Size(9f * u, 5f * u), CornerRadius(2f * u, 2f * u))
                drawCircle(if (con) OkGreen else Color(0xFF7A7E8E), 5f * u, Offset(132f * u, 28f * u))
                translate(left = (-22f + 26f * t) * u) {
                    drawPath(
                        Path().apply { moveTo(0f, 28f * u); cubicTo(16f * u, 28f * u, 14f * u, 46f * u, 30f * u, 40f * u); cubicTo(46f * u, 34f * u, 44f * u, 28f * u, 56f * u, 28f * u) },
                        cable, style = Stroke(5f * u, cap = StrokeCap.Round)
                    )
                    drawRoundRect(a, Offset(54f * u, 14f * u), Size(34f * u, 28f * u), CornerRadius(8f * u, 8f * u))
                    drawRoundRect(Color.White.copy(alpha = 0.28f), Offset(56f * u, 15f * u), Size(30f * u, 8f * u), CornerRadius(4f * u, 4f * u))
                    drawRoundRect(Color(0xFFC9CCD6), Offset(88f * u, 20f * u), Size(14f * u, 5f * u), CornerRadius(1.5f * u, 1.5f * u))
                    drawRoundRect(Color(0xFFC9CCD6), Offset(88f * u, 31f * u), Size(14f * u, 5f * u), CornerRadius(1.5f * u, 1.5f * u))
                }
                if (!con) {
                    val z = Color(0xFFFFB300).copy(alpha = zap)
                    drawLine(z, Offset(88f * u, 6f * u), Offset(84f * u, 12f * u), 2f * u, StrokeCap.Round)
                    drawLine(z, Offset(96f * u, 4f * u), Offset(96f * u, 11f * u), 2f * u, StrokeCap.Round)
                    drawLine(z, Offset(88f * u, 50f * u), Offset(84f * u, 44f * u), 2f * u, StrokeCap.Round)
                    drawLine(z, Offset(96f * u, 52f * u), Offset(96f * u, 45f * u), 2f * u, StrokeCap.Round)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                T(if (con) "Đã kết nối" else if (none) "Chưa chọn hộp đèn" else "Mất kết nối với hộp đèn", 15, FontWeight.Bold)
                T(if (con) S.devName.value else if (none) "Bấm Chọn hộp đèn để tìm hộp." else S.conn.value.ifEmpty { "Đang tự thử nối lại…" }, 12, color = Grey)
            }
        }
        if (!con) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!none) FillBtn("Kết nối", Icons.Filled.Bluetooth, a, Modifier.weight(1f)) { S.userOff = false; S.autoConnect() }
                OutlineBtn("Chọn hộp đèn", Modifier.weight(1f)) { S.pickNow.value = true }
            }
        }
    }
}
