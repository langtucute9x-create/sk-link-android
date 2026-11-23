package vn.sklight.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

// ===================== GIAO DIỆN NỔI KHỐI (đợt 3): đồng hồ tốc độ 2,5D và đồng hồ giờ số lật cho màn hình chờ =====================
// Ý tưởng hình thức tham khảo kho Lucian UX/UI (MIT, github.com/Seokwoooo/lucian-uxui-prompt): mẫu 100, 277. Viết lại bằng Compose.

private val GaugeRed = Color(0xFFE5202E)
private val GaugeDim = Color(0xFF8D909C)

/**
 * Đồng hồ tốc độ 0..160 km/h. Mặt đồng hồ nằm nghiêng 2,5D; kim và vạch chia chỉ ở nửa trên (170° → 370°),
 * con số tốc độ đứng thẳng ở nửa dưới còn trống nên không đè lên kim. [d] = đường kính mặt đồng hồ.
 */
@Composable
fun Gauge25(spd: Int, limit: Int, d: Dp, accent: Color) {
    val fx = Fx
    val over = limit > 0 && spd > limit
    val spec: AnimationSpec<Float> = if (fx) spring(dampingRatio = 0.7f, stiffness = 120f) else snap()
    val v by animateFloatAsState(spd.coerceIn(0, 160).toFloat(), spec, label = "gauge")
    val tm = rememberTextMeasurer()
    val fam = Inter
    val arcCol = if (over) GaugeRed else accent
    Box(Modifier.size(d, if (fx) d * 0.72f else d)) {
        val face = if (fx) Modifier.requiredSize(d).offset(y = -(d * 0.06f)).graphicsLayer {
            rotationX = 46f; transformOrigin = TransformOrigin(0.5f, 0.62f); cameraDistance = 16f
        } else Modifier.fillMaxSize()
        Canvas(face) {
            val u = size.width / 200f
            val c = Offset(100f * u, 100f * u)
            fun pt(value: Float, r: Float): Offset {
                val a = Math.toRadians(170.0 + 200.0 * value / 160.0)
                return Offset(c.x + (cos(a) * r * u).toFloat(), c.y + (sin(a) * r * u).toFloat())
            }
            if (fx) drawOval(Color.Black.copy(alpha = 0.45f), Offset(8f * u, 24f * u), Size(184f * u, 176f * u))
            drawCircle(Brush.verticalGradient(listOf(Color(0xFF191A22), Color(0xFF0D0E13))), 92f * u, c)
            drawCircle(Color(0xFF2C2E3A), 92f * u, c, style = Stroke(1.5f * u))
            val tl = Offset(22f * u, 22f * u)
            val sz = Size(156f * u, 156f * u)
            drawArc(Color(0xFF23242D), 170f, 200f, false, tl, sz, style = Stroke(12f * u, cap = StrokeCap.Round))
            val f = (v / 160f).coerceIn(0f, 1f)
            if (f > 0.004f) {
                if (fx) drawArc(arcCol.copy(alpha = 0.25f), 170f, 200f * f, false, tl, sz, style = Stroke(18f * u, cap = StrokeCap.Round))
                drawArc(arcCol, 170f, 200f * f, false, tl, sz, style = Stroke(12f * u, cap = StrokeCap.Round))
            }
            for (t in 0..16) {
                val value = t * 10f
                val big = t % 2 == 0
                drawLine(if (big) Color(0xFFC9CCD6) else Color(0xFF5A5E70), pt(value, if (big) 62f else 66f), pt(value, 69f), (if (big) 2f else 1f) * u, StrokeCap.Round)
                if (t % 4 == 0) {
                    val lay = tm.measure(AnnotatedString("${t * 10}"), TextStyle(color = GaugeDim, fontSize = (14f * u).toSp(), fontFamily = fam))
                    val p = pt(value, 50f)
                    drawText(lay, topLeft = Offset(p.x - lay.size.width / 2f, p.y - lay.size.height / 2f))
                }
            }
            if (limit in 1..160) drawLine(GaugeRed, pt(limit.toFloat(), 68f), pt(limit.toFloat(), 90f), 4f * u, StrokeCap.Round)
            val tip = pt(v, 64f)
            if (fx) drawLine(arcCol.copy(alpha = 0.3f), c, tip, 8f * u, StrokeCap.Round)
            drawLine(Color.White, c, tip, 3.5f * u, StrokeCap.Round)
            drawCircle(Color(0xFF2A2B35), 9f * u, c)
            drawCircle(Color(0xFF5A5E70), 9f * u, c, style = Stroke(1.5f * u))
        }
        Row(Modifier.align(Alignment.TopCenter).padding(top = d * (if (fx) 0.385f else 0.58f)), verticalAlignment = Alignment.Bottom) {
            val fs = d.value * 0.2f
            Text("$spd", fontSize = fs.sp, lineHeight = fs.sp, fontWeight = FontWeight.ExtraBold, color = if (over) GaugeRed else Color.White, fontFamily = fam, maxLines = 1)
            Spacer(Modifier.width(6.dp))
            Text("km/h", fontSize = (d.value * 0.06f).coerceAtLeast(11f).sp, fontWeight = FontWeight.SemiBold, color = GaugeDim, fontFamily = fam, maxLines = 1, modifier = Modifier.padding(bottom = (d.value * 0.025f).dp))
        }
    }
}

/** Một ô số của đồng hồ lật: đổi số thì ô lật qua trục ngang. */
@Composable
private fun FlipDigit(ch: Char, h: Dp) {
    var shown by remember { mutableStateOf(ch) }
    val rot = remember { Animatable(0f) }
    LaunchedEffect(ch) {
        if (ch != shown) {
            if (Fx) {
                rot.animateTo(-90f, tween(190))
                shown = ch
                rot.snapTo(90f)
                rot.animateTo(0f, tween(190))
            } else shown = ch
        }
    }
    val sh = RoundedCornerShape(h * 0.14f)
    Box(
        Modifier.size(h * 0.68f, h).graphicsLayer { rotationX = rot.value; cameraDistance = 12f }.clip(sh).drawBehind {
            val mid = size.height / 2f
            drawRect(Brush.verticalGradient(listOf(Color(0xFF34353F), Color(0xFF26272F)), endY = mid), Offset.Zero, Size(size.width, mid))
            drawRect(Brush.verticalGradient(listOf(Color(0xFF1D1E26), Color(0xFF15161C)), startY = mid), Offset(0f, mid), Size(size.width, mid))
            drawRect(Color(0xFF07070A), Offset(0f, mid - size.height * 0.012f), Size(size.width, size.height * 0.024f))
            drawRect(Color.White.copy(alpha = 0.12f), Offset.Zero, Size(size.width, 1.dp.toPx()))
        },
        contentAlignment = Alignment.Center
    ) {
        val fs = h.value * 0.68f
        Text("$shown", fontSize = fs.sp, lineHeight = fs.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = Inter, maxLines = 1)
    }
}

/** Đồng hồ giờ kiểu số lật. [time] dạng "HH:MM", [h] = chiều cao ô số. */
@Composable
fun FlipClock(time: String, h: Dp, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(h * 0.07f)) {
        FlipDigit(time.getOrElse(0) { '0' }, h)
        FlipDigit(time.getOrElse(1) { '0' }, h)
        val fs = h.value * 0.6f
        Text(":", fontSize = fs.sp, lineHeight = fs.sp, fontWeight = FontWeight.Bold, color = accent, fontFamily = Inter)
        FlipDigit(time.getOrElse(3) { '0' }, h)
        FlipDigit(time.getOrElse(4) { '0' }, h)
    }
}
