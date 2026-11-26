package vn.sklight.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

// Hình xem trước của từng kiểu nút nổi + màn hình nổi (vẽ lại gần đúng bằng Compose; cái thật vẽ bằng View trong Float.kt).

private val PvDark = Color(0xFF15161C)
private val PvKey = Color(0xFF22232C)
private val PvDots = listOf(Color(0xFF1AF4EA), Color(0xFFFE2B48), Color(0xFFFF7A1A), Color(0xFF8A00FF))

@Composable
private fun PvKeyBtn(icon: ImageVector, bg: Brush, shape: Shape, tint: Color = Color.White, d: Dp = 28.dp, bd: Color = Color.Transparent) {
    Box(Modifier.size(d).clip(shape).background(bg).border(1.dp, bd, shape), contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint, modifier = Modifier.size(d * 0.56f)) }
}
private fun solid(c: Color): Brush = Brush.verticalGradient(listOf(c, c))

@Composable
private fun PvDotsRow(n: Int = 4) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until n) Box(Modifier.size(18.dp).clip(CircleShape).background(PvDots[i]).border(if (i == 1) 2.dp else 1.dp, if (i == 1) Color.White else Color(0x66FFFFFF), CircleShape))
    }
}

@Composable
private fun PvBubble(st: Int, led: Color) {
    val base = Modifier.size(40.dp).clip(CircleShape)
    val m = when (st) {
        1 -> base.background(Color(0x40FFFFFF)).border(1.dp, Color(0xB3FFFFFF), CircleShape)
        2 -> base.background(PvDark).border(2.dp, led, CircleShape)
        3 -> base.background(led).border(2.5.dp, Color.White, CircleShape)
        4 -> base.background(Brush.linearGradient(listOf(lite(led, 0.5f), led, dim(led, 0.4f))))
        5 -> base.background(Color(0xFF0B0B0E)).border(2.dp, led, CircleShape)
        else -> base.background(Color.White).border(2.dp, led, CircleShape)
    }
    // có chọn nhân vật: hình xem trước hiện đúng nhân vật đó thay cho vòng tròn SK
    if (S.floatMascot.intValue > 0) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { c -> android.widget.FrameLayout(c) },
            update = { fl -> val k = S.floatMascot.intValue; if (fl.tag != k) { fl.tag = k; Mascot.free(fl.getChildAt(0) as? android.webkit.WebView); fl.removeAllViews(); Mascot.view(fl.context, k)?.let { fl.addView(it, android.widget.FrameLayout.LayoutParams(-1, -1)) } } },
            modifier = Modifier.size(56.dp)
        )
        return
    }
    Box(m, contentAlignment = Alignment.Center) {
        if (st >= 3) Box(Modifier.size(26.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) { Text("SK", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = led) }
        else Text("SK", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = if (st == 0) led else Color.White)
    }
}

/** Hình xem trước kiểu [st] (0..5): nút nổi và bảng đang mở, đặt trên nền giả app bản đồ. */
@Composable
fun FloatPreview(st: Int, modifier: Modifier = Modifier) {
    val led = Pink
    val sh = RoundedCornerShape(Theme.r(10))
    Box(modifier.fillMaxWidth().height(176.dp).clip(sh).background(Color(0xFF1B2330)).border(1.dp, Bd, sh)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            drawLine(Color(0xFF263142), Offset(w * 0.18f, 0f), Offset(w * 0.3f, h), 14.dp.toPx())
            drawLine(Color(0xFF263142), Offset(0f, h * 0.25f), Offset(w, h * 0.55f), 12.dp.toPx())
            drawLine(Color(0xFF2E3A4D), Offset(0f, h * 0.85f), Offset(w * 0.62f, h * 0.3f), 22.dp.toPx(), StrokeCap.Round)
            drawLine(Color(0xFF3F8CFF), Offset(0f, h * 0.85f), Offset(w * 0.62f, h * 0.3f), 5.dp.toPx(), StrokeCap.Round)
        }
        val circle: Shape = CircleShape
        when (st) {
            3 -> Box(Modifier.align(Alignment.CenterEnd).padding(end = 14.dp).size(150.dp, 160.dp)) {
                // nút nổi ở giữa cạnh phải, 5 nút xòe thành cung bên trái
                val icons = listOf(Icons.Filled.Mic, Icons.Filled.Add, Icons.Filled.PowerSettingsNew, Icons.Filled.Remove, Icons.Filled.Palette)
                icons.forEachIndexed { i, ic ->
                    val g = Math.toRadians(125.0 + 110.0 * i / 4)
                    val x = 130 + (cos(g) * 76).toInt() - 15
                    val y = 80 - (sin(g) * 76).toInt() - 15
                    Box(Modifier.offset(x.dp, y.dp)) { PvKeyBtn(ic, solid(PvDark), circle, if (i == 0 || i == 2) led else Color.White, 30.dp, Color(0xFF3A3D4A)) }
                }
                Box(Modifier.align(Alignment.CenterEnd)) { PvBubble(st, led) }
            }
            5 -> Column(Modifier.align(Alignment.CenterEnd).padding(end = 18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Column(
                    Modifier.clip(RoundedCornerShape(19.dp)).background(Color(0xFF0B0B0E)).border(1.5.dp, led, RoundedCornerShape(19.dp)).padding(horizontal = 5.dp, vertical = 5.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    for ((k, ic) in listOf(Icons.Filled.Mic, Icons.Filled.Palette, Icons.Filled.Add, Icons.Filled.Remove, Icons.Filled.PowerSettingsNew).withIndex())
                        PvKeyBtn(ic, solid(Color(0xFF1E1F26)), circle, if (k == 0 || k == 4) led else Color.White, 22.dp)
                }
                PvBubble(st, led)
            }
            else -> Row(Modifier.align(Alignment.CenterEnd).padding(end = 14.dp), verticalAlignment = if (st == 2) Alignment.CenterVertically else Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (st) {
                    2 -> Row(
                        Modifier.clip(RoundedCornerShape(24.dp)).background(PvDark).border(1.dp, Color(0xFF2C2E3A), RoundedCornerShape(24.dp)).padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        PvKeyBtn(Icons.Filled.PowerSettingsNew, solid(PvKey), circle, led)
                        PvKeyBtn(Icons.Filled.Remove, solid(PvKey), circle)
                        PvKeyBtn(Icons.Filled.Add, solid(PvKey), circle)
                        PvDotsRow(3)
                        PvKeyBtn(Icons.Filled.Mic, solid(PvKey), circle, led)
                    }
                    0 -> Column(
                        Modifier.width(170.dp).clip(RoundedCornerShape(10.dp)).background(Color.White).border(1.dp, Color(0xFFDDE3EF), RoundedCornerShape(10.dp)).padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            PvChip("Tắt đèn", Modifier.width(54.dp)); PvChip("−", Modifier.width(26.dp))
                            Text("70%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC4127F), modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            PvChip("+", Modifier.width(26.dp))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { PvChip("Thư giãn", Modifier.weight(1f)); PvChip("Thể thao", Modifier.weight(1f), true) }
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { PvChip("Ban đêm", Modifier.weight(1f)); PvChip("Đón khách", Modifier.weight(1f)) }
                    }
                    else -> {
                        val glass = st == 1
                        val psh = RoundedCornerShape(if (glass) 16.dp else 13.dp)
                        val kb = if (glass) solid(Color(0x29FFFFFF)) else Brush.verticalGradient(listOf(Color(0xFF34353F), Color(0xFF1B1C22)))
                        val ks: Shape = if (glass) CircleShape else RoundedCornerShape(8.dp)
                        Column(
                            Modifier.width(166.dp).clip(psh)
                                .background(if (glass) solid(Color(0xDB141620)) else Brush.verticalGradient(listOf(Color(0xFF272833), Color(0xFF121318))))
                                .border(1.dp, if (glass) Color(0x40FFFFFF) else Color(0xFF3A3D4A), psh).padding(9.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                PvKeyBtn(Icons.Filled.PowerSettingsNew, kb, ks, led, 30.dp, if (glass) Color.Transparent else Color(0xFF454857))
                                PvKeyBtn(Icons.Filled.Remove, kb, ks, Color.White, 30.dp, if (glass) Color.Transparent else Color(0xFF454857))
                                Text("70%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                PvKeyBtn(Icons.Filled.Add, kb, ks, Color.White, 30.dp, if (glass) Color.Transparent else Color(0xFF454857))
                            }
                            PvDotsRow(4)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PvKeyBtn(Icons.Filled.Mic, kb, ks, led, 30.dp, if (glass) Color.Transparent else Color(0xFF454857))
                                Spacer(Modifier.weight(1f))
                                Text("Thể thao", fontSize = 10.sp, color = Color(0xFFC3C9D6))
                            }
                        }
                    }
                }
                PvBubble(st, led)
            }
        }
    }
}

@Composable
private fun PvChip(text: String, modifier: Modifier, sel: Boolean = false) {
    val sh = RoundedCornerShape(6.dp)
    Box(modifier.height(24.dp).clip(sh).background(if (sel) Color(0xFFFFE9F6) else Color(0xFFF4F6FC)).border(1.dp, if (sel) Color(0xFFFE2CAA) else Color(0xFFDDE3EF), sh), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 9.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, color = Color(0xFF172032), maxLines = 1)
    }
}
