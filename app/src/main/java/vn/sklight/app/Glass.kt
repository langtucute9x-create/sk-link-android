package vn.sklight.app

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// ===================== GIAO DIỆN "KÍNH NEON 3D" (hướng B, chủ dự án duyệt 27/11) =====================
// Nền tím than, thẻ kính nổi khối (xem Card trong Fx.kt), viền neon chạy quanh khung, logo SK phát sáng.
// Chưa thử trên máy thật. "Giảm hiệu ứng" trong Cài đặt giao diện tắt mọi chuyển động ở đây.

private val NEON = listOf(Color(0xFFFF2D8F), Color(0xFF7C3AED), Color(0xFF22D3EE), Color(0xFF34D399), Color(0xFFFACC15))

/** Màu neon tại vị trí [p] (0..1) trên vòng màu. */
private fun neonAt(p: Float): Color {
    val x = ((p % 1f) + 1f) % 1f * NEON.size
    val i = x.toInt() % NEON.size
    return lerp(NEON[i], NEON[(i + 1) % NEON.size], x - x.toInt())
}

/** Dải màu neon khép kín (màu đầu = màu cuối), xoay theo [phase] 0..1. */
private fun neonSweep(phase: Float, center: Offset): Brush {
    val n = 12
    return Brush.sweepGradient(List(n + 1) { neonAt(it / n.toFloat() + phase) }, center)
}

/** Pha chạy của neon: quay đều khi bật hiệu ứng, đứng yên khi "Giảm hiệu ứng". */
@Composable
private fun neonPhase(ms: Int): State<Float> {
    if (!Fx) return remember { mutableFloatStateOf(0f) }
    val tr = rememberInfiniteTransition(label = "neon")
    return tr.animateFloat(0f, 1f, infiniteRepeatable(tween(ms, easing = LinearEasing)), label = "neonPhase")
}

/** Nền trang: phong cách Kính neon dùng quầng tím ở góc trên trái đổ xuống xanh than; các phong cách khác giữ nền chuyển sắc dọc như cũ. */
fun Modifier.appBg(): Modifier {
    if (!Theme.glass) return background(Brush.verticalGradient(listOf(Theme.pal.bgTop, Theme.pal.bgBot)))
    return drawBehind {
        drawRect(Color(0xFF050814))
        val r = size.maxDimension * 0.95f
        drawRect(Brush.radialGradient(listOf(Color(0xFF3A1D6E), Color(0xFF0B1230), Color(0xFF050814)), Offset(size.width * 0.18f, size.height * 0.05f), r))
        drawRect(Brush.radialGradient(listOf(Color(0x3322D3EE), Color.Transparent), Offset(size.width * 0.95f, size.height), size.minDimension * 0.9f))
    }
}

/** Viền neon chạy quanh khung màn hình. Vẽ trên một lớp riêng, không nhận chạm, nên lúc chạy chỉ lớp này vẽ lại. */
@Composable
fun NeonFrame() {
    if (!Theme.glass) return
    val phase = neonPhase(9000)
    Box(Modifier.fillMaxSize().systemBarsPadding().graphicsLayer { }.drawBehind {
        val b = neonSweep(phase.value, center)
        val cr = CornerRadius(10.dp.toPx(), 10.dp.toPx())
        fun ring(w: Dp, a: Float) {
            val px = w.toPx()
            drawRoundRect(b, Offset(px / 2f, px / 2f), Size(size.width - px, size.height - px), cr, style = Stroke(px), alpha = a)
        }
        ring(7.dp, 0.16f); ring(4.dp, 0.30f); ring(1.5.dp, 1f)
    })
}

/** Vòng neon tròn quay quanh logo. */
private fun DrawScope.neonRing(phase: Float, w: Float) {
    val b = neonSweep(phase, center)
    val r = size.minDimension / 2f
    drawCircle(b, r - w * 2f, style = Stroke(w * 4f), alpha = 0.22f)
    drawCircle(b, r - w * 2f, style = Stroke(w))
}

/** Logo SK nhỏ ở góc các trang (vị trí 4): chỉ logo, không nền, không quầng (chủ dự án yêu cầu 27/11). */
@Composable
fun LogoMark(width: Dp) {
    Image(painterResource(R.drawable.sk_mark), "SK Ambient Light", Modifier.width(width), contentScale = ContentScale.Fit)
}

/** Logo SK màu trắng "thở" trên màn hình chờ (vị trí 2), không nền. */
@Composable
fun LogoBreath(height: Dp, dimmed: Boolean = false) {
    val k = if (Fx) rememberInfiniteTransition(label = "breath").animateFloat(0.45f, 1f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathA")
    else remember { mutableFloatStateOf(0.9f) }
    Image(painterResource(R.drawable.sk_logo), "SK Ambient Light", Modifier.height(height).graphicsLayer { alpha = k.value * (if (dimmed) 0.5f else 1f) },
        contentScale = ContentScale.Fit, colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White))
}

/** Màn mở app (vị trí 1): logo SK sáng dần trong vòng neon chạy, tự tắt sau khoảng 1,8 giây hoặc khi chạm. Mỗi lần mở app hiện một lần. */
@Composable
fun Splash() {
    var show by remember { mutableStateOf(!S.splashDone) }
    if (!show) return
    val phase = neonPhase(2600)
    val inA = remember { Animatable(0f) }
    val outA = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        S.splashDone = true
        inA.animateTo(1f, tween(if (Fx) 700 else 1))
        delay(1100)
        outA.animateTo(0f, tween(if (Fx) 350 else 1))
        show = false
    }
    Box(
        Modifier.fillMaxSize().graphicsLayer { alpha = outA.value }.background(Color(0xFF050814))
            .drawBehind { drawRect(Brush.radialGradient(listOf(Color(0xFF3A1D6E), Color(0xFF0B1230), Color(0xFF050814)), center, size.maxDimension * 0.7f)) }
            .clickable(remember { MutableInteractionSource() }, null) { show = false },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer { alpha = inA.value; val s = 0.86f + 0.14f * inA.value; scaleX = s; scaleY = s }) {
            Box(Modifier.size(190.dp).drawBehind { neonRing(phase.value, 3.dp.toPx()) }, contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.sk_mark), "SK Ambient Light", Modifier.width(104.dp), contentScale = ContentScale.Fit)
            }
            Spacer(Modifier.height(20.dp))
            Text("SK AMBIENT LIGHT", fontSize = 14.sp, letterSpacing = 5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFCBD5F5), fontFamily = Inter)
        }
    }
}

/**
 * Công tắc dạng nút đèn (chủ dự án đề xuất 27/11): bật thì sáng màu nhấn, tắt thì tối, giống nút nguồn ở góc trên bên trái.
 * [label] dùng cho trình đọc màn hình.
 */
@Composable
fun PowerToggle(on: Boolean, label: String, size: Int = 50, onChange: (Boolean) -> Unit) {
    val src = remember { MutableInteractionSource() }
    val tick = rememberTick()
    Box(
        Modifier.size(size.dp).then(pressScale(src)).orb(on).clickable(src, LocalIndication.current) { tick(); onChange(!on) },
        contentAlignment = Alignment.Center
    ) { Icon(Icons.Filled.PowerSettingsNew, L.tr(label) + ": " + L.tr(if (on) "đang bật" else "đang tắt"), tint = if (on) Color.White else Grey, modifier = Modifier.size((size / 2).dp)) }
}

/** Một dòng cài đặt có công tắc dạng nút đèn: tên, trạng thái "Đang bật / Đang tắt", nút ở bên phải. */
@Composable
fun PowerRow(title: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 56.dp)) {
        Column(Modifier.weight(1f)) {
            T(title, 15, FontWeight.Bold)
            T(if (on) "Đang bật" else "Đang tắt", 12, FontWeight.SemiBold, if (on) AccentText else Grey)
        }
        Spacer(Modifier.width(10.dp))
        SkSwitch(on, onChange)
    }
}
