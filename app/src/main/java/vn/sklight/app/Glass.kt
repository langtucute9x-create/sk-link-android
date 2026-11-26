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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

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

// ===================== HIỆU ỨNG HỌC TỪ THREEUI (MIT, github.com/MengTo/threeui), chủ dự án chọn số 1 và 4 ngày 27/11 =====================
// Chỉ lấy ý tưởng hình thức (Holo Card, Umbra Chrome Button), viết lại bằng Compose. Tắt cùng "Giảm hiệu ứng". Chưa thử trên máy thật.

/** Thẻ nghiêng 3D theo ngón tay, có vệt sáng tại chỗ chạm. Chỉ quan sát thao tác chạm, không giành của nút bên trong. */
fun Modifier.tilt3d(): Modifier = composed {
    if (!Fx) return@composed Modifier
    var tx by remember { mutableFloatStateOf(0f) }       // -1..1 theo chiều ngang
    var ty by remember { mutableFloatStateOf(0f) }
    var down by remember { mutableStateOf(false) }
    var at by remember { mutableStateOf(Offset.Zero) }
    val ax by animateFloatAsState(if (down) tx else 0f, spring(dampingRatio = 0.6f, stiffness = 380f), label = "tiltX")
    val ay by animateFloatAsState(if (down) ty else 0f, spring(dampingRatio = 0.6f, stiffness = 380f), label = "tiltY")
    val glow by animateFloatAsState(if (down) 1f else 0f, tween(180), label = "tiltGlow")
    pointerInput(Unit) {
        awaitEachGesture {
            val d = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            fun set(p: Offset) { at = p; tx = ((p.x / size.width) * 2f - 1f).coerceIn(-1f, 1f); ty = ((p.y / size.height) * 2f - 1f).coerceIn(-1f, 1f) }
            set(d.position); down = true
            try {
                while (true) {
                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                    val c = ev.changes.firstOrNull { it.id == d.id } ?: break
                    if (!c.pressed) break
                    set(c.position)
                }
            } finally { down = false }
        }
    }.graphicsLayer { rotationY = ax * 11f; rotationX = -ay * 11f; cameraDistance = 10f * density }
        .drawWithContent {
            drawContent()
            if (glow > 0.01f) drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.30f * glow), Color.Transparent), at, size.minDimension * 0.9f), size.minDimension * 0.9f, at)
        }
}

/** Vệt sáng lướt ngang qua nút khi nhấn (đặt SAU clip / nền của nút để vệt sáng nằm gọn trong nút). */
fun Modifier.pressSweep(src: InteractionSource): Modifier = composed {
    if (!Fx) return@composed Modifier
    val pressed by src.collectIsPressedAsState()
    val k = remember { Animatable(1f) }
    LaunchedEffect(pressed) { if (pressed) { k.snapTo(0f); k.animateTo(1f, tween(450, easing = LinearEasing)) } }
    drawWithContent {
        drawContent()
        val v = k.value
        if (v > 0f && v < 1f) {
            val bw = size.height * 1.1f
            val x = -bw + (size.width + 2f * bw) * v
            drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent), Offset(x - bw / 2f, 0f), Offset(x + bw / 2f, size.height * 0.4f)), Offset(x - bw, 0f), Size(2f * bw, size.height))
        }
    }
}

/**
 * Vòng quay 3D cho hàng cuộn ngang (số 2, học từ carousel của Volta Atelier): ô ở giữa hàng đứng thẳng và to nhất,
 * càng ra hai mép càng xoay nghiêng, nhỏ và mờ dần, nên khi vuốt hàng trông như một vòng tròn đang quay.
 */
fun Modifier.arc3d(scroll: androidx.compose.foundation.ScrollState, rowW: () -> Int): Modifier = composed {
    if (!Fx) return@composed Modifier
    var cx by remember { mutableFloatStateOf(0f) }
    onGloballyPositioned { cx = it.positionInParent().x + it.size.width / 2f }.graphicsLayer {
        val w = rowW().toFloat()
        if (w > 0f) {
            val d = (((cx - scroll.value) - w / 2f) / (w / 2f)).coerceIn(-1.2f, 1.2f)
            val a = kotlin.math.abs(d)
            rotationY = -d * 42f
            val s = 1.06f - a * 0.20f
            scaleX = s; scaleY = s
            alpha = (1f - a * 0.40f).coerceIn(0.35f, 1f)
            cameraDistance = 9f * density
        }
    }
}
