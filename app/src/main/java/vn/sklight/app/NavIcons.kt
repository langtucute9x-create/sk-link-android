package vn.sklight.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp

// Biểu tượng chỉ đường và biển giới hạn tốc độ, vẽ tay cho đồng bộ với giao diện nổi khối
// (thay cho bộ biểu tượng có sẵn của hệ thống trông hơi cũ).

/**
 * Ô chỉ hướng: nền màu nhấn có chuyển sắc và viền sáng, mũi tên trắng nét tròn.
 * [dir]: "L" rẽ trái, "R" rẽ phải, "U" quay đầu, "O" vòng xuyến, "A" đến nơi, còn lại = đi thẳng.
 */
@Composable
fun TurnIcon(dir: String, d: Dp, accent: Color = Pink) {
    val fx = Fx
    val sh = RoundedCornerShape(d * 0.28f)
    val m = if (fx) Modifier.size(d).shadow(d * 0.10f, sh, ambientColor = accent, spotColor = accent) else Modifier.size(d).clip(sh)
    Canvas(m) {
        val w = size.width
        val cr = CornerRadius(w * 0.28f, w * 0.28f)
        if (fx) {
            drawRoundRect(Brush.linearGradient(listOf(lite(accent, 0.38f), accent, dim(accent, 0.30f)), Offset(0f, 0f), Offset(w, w)), cornerRadius = cr)
            drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0f)), endY = w * 0.6f), cornerRadius = cr, style = Stroke(w * 0.022f))
            drawRoundRect(Color.White.copy(alpha = 0.10f), Offset(w * 0.08f, w * 0.06f), Size(w * 0.84f, w * 0.34f), CornerRadius(w * 0.2f, w * 0.2f))
        } else drawRoundRect(accent, cornerRadius = cr)
        val st = Stroke(w * 0.105f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(x: Float, y: Float) = Offset(x * w, y * w)
        fun head(tip: Offset, a: Offset, b: Offset) = Path().apply { moveTo(a.x, a.y); lineTo(tip.x, tip.y); lineTo(b.x, b.y) }
        fun line(vararg pts: Offset) = Path().apply { moveTo(pts[0].x, pts[0].y); for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y) }
        when (dir) {
            "L", "R" -> scale(if (dir == "R") -1f else 1f, 1f, Offset(w / 2f, w / 2f)) {
                // thân đi lên rồi uốn sang trái
                val body = Path().apply {
                    moveTo(0.62f * w, 0.80f * w); lineTo(0.62f * w, 0.54f * w)
                    quadraticTo(0.62f * w, 0.38f * w, 0.46f * w, 0.38f * w); lineTo(0.27f * w, 0.38f * w)
                }
                drawPath(body, Color.White, style = st)
                drawPath(head(p(0.25f, 0.38f), p(0.38f, 0.25f), p(0.38f, 0.51f)), Color.White, style = st)
            }
            "U" -> {
                val body = Path().apply {
                    moveTo(0.67f * w, 0.80f * w); lineTo(0.67f * w, 0.42f * w)
                    cubicTo(0.67f * w, 0.20f * w, 0.36f * w, 0.20f * w, 0.36f * w, 0.42f * w); lineTo(0.36f * w, 0.66f * w)
                }
                drawPath(body, Color.White, style = st)
                drawPath(head(p(0.36f, 0.69f), p(0.23f, 0.56f), p(0.49f, 0.56f)), Color.White, style = st)
            }
            "O" -> {
                drawCircle(Color.White, 0.15f * w, p(0.5f, 0.52f), style = Stroke(w * 0.09f))
                drawPath(line(p(0.5f, 0.82f), p(0.5f, 0.70f)), Color.White, style = st)
                drawPath(line(p(0.5f, 0.34f), p(0.5f, 0.20f)), Color.White, style = st)
                drawPath(head(p(0.5f, 0.17f), p(0.39f, 0.28f), p(0.61f, 0.28f)), Color.White, style = st)
            }
            "A" -> {
                // ghim vị trí
                val pin = Path().apply {
                    moveTo(0.5f * w, 0.82f * w)
                    cubicTo(0.20f * w, 0.52f * w, 0.26f * w, 0.20f * w, 0.5f * w, 0.20f * w)
                    cubicTo(0.74f * w, 0.20f * w, 0.80f * w, 0.52f * w, 0.5f * w, 0.82f * w)
                    close()
                }
                drawPath(pin, Color.White)
                drawCircle(accent, 0.09f * w, p(0.5f, 0.42f))
            }
            else -> {
                drawPath(line(p(0.5f, 0.80f), p(0.5f, 0.24f)), Color.White, style = st)
                drawPath(head(p(0.5f, 0.21f), p(0.34f, 0.37f), p(0.66f, 0.37f)), Color.White, style = st)
            }
        }
    }
}

/** Biển giới hạn tốc độ: đĩa trắng nổi khối, vành đỏ chuyển sắc, số đậm. [over] = đang chạy quá giới hạn (đĩa ngả đỏ). */
@Composable
fun SpeedSign(limit: Int, d: Dp, over: Boolean = false) {
    val fx = Fx
    val red = Color(0xFFE5202E)
    Box(
        (if (fx) Modifier.size(d).shadow(d * 0.10f, CircleShape, ambientColor = Color.Black, spotColor = if (over) red else Color.Black) else Modifier.size(d).clip(CircleShape))
            .drawBehind {
                val r = size.minDimension / 2f
                val ring = r * 0.22f
                val face = if (over) listOf(Color(0xFFFFF1F2), Color(0xFFFFD5D8)) else listOf(Color.White, Color(0xFFE9EBF1))
                drawCircle(if (fx) Brush.radialGradient(face, Offset(center.x - r * 0.25f, center.y - r * 0.3f), r * 1.4f) else Brush.radialGradient(listOf(face[0], face[0])), r, center)
                drawCircle(
                    if (fx) Brush.sweepGradient(listOf(Color(0xFFFF5A64), red, Color(0xFFB3111D), red, Color(0xFFFF5A64)), center) else Brush.sweepGradient(listOf(red, red), center),
                    r - ring / 2f, center, style = Stroke(ring)
                )
                if (fx) drawCircle(Color.White.copy(alpha = 0.35f), r - ring * 0.18f, center, style = Stroke(ring * 0.12f))
            },
        contentAlignment = Alignment.Center
    ) {
        val fs = d.value * (if (limit >= 100) 0.33f else 0.40f)
        Text("$limit", fontSize = fs.sp, lineHeight = fs.sp, fontWeight = FontWeight.ExtraBold, color = if (over) Color(0xFFB3111D) else Color(0xFF14161C), fontFamily = Inter, maxLines = 1)
    }
}
