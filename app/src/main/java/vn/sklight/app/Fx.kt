package vn.sklight.app

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ===================== GIAO DIỆN NỔI KHỐI (đợt 1) =====================
// Ý tưởng hình thức tham khảo kho Lucian UX/UI (MIT, github.com/Seokwoooo/lucian-uxui-prompt): mẫu 001, 013, 017, 020, 039.
// Viết lại hoàn toàn bằng Compose và bảng màu của app; không sao chép mã.

/** Hiệu ứng nổi khối đang bật. Tắt bằng công tắc "Giảm hiệu ứng" trong Cài đặt giao diện: mọi thứ vẽ phẳng như bản cũ. */
val Fx: Boolean get() = Theme.fx.value

fun lite(c: Color, k: Float) = lerp(c, Color.White, k)
fun dim(c: Color, k: Float) = lerp(c, Color.Black, k)
private val Shine = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0f)))

/** Nền một ô bấm: màu nhấn khi [on], màu ô khi không. Bật hiệu ứng thì có chuyển sắc, viền sáng phía trên và quầng màu khi đang chọn. */
fun Modifier.block(on: Boolean, shape: Shape, idle: Color = ChipBg, accent: Color = Pink): Modifier {
    val a = accent
    if (!Fx) return clip(shape).background(if (on) a else idle).border(1.dp, if (on) a else Bd, shape)
    val base = if (on) shadow(5.dp, shape, clip = false, ambientColor = a, spotColor = a) else this
    val fill = if (on) Brush.verticalGradient(listOf(lite(a, 0.30f), a, dim(a, 0.22f)))
    else Brush.verticalGradient(listOf(lite(idle, if (Theme.dark) 0.07f else 0.6f), dim(idle, if (Theme.dark) 0.12f else 0.03f)))
    return base.clip(shape).background(fill).border(1.dp, if (on) Shine else SolidColor(Bd), shape)
}

/** Quầng màu [c] quanh ô đang chọn (chỉ khi bật hiệu ứng). Đặt trước clip / background. */
fun Modifier.glow(on: Boolean, c: Color, shape: Shape): Modifier = if (on && Fx) shadow(5.dp, shape, clip = false, ambientColor = c, spotColor = c) else this

/** Rung rất nhẹ khi bấm các nút chính (máy không có bộ rung thì không có gì xảy ra; tắt cùng "Giảm hiệu ứng"). */
@Composable
fun rememberTick(): () -> Unit {
    val h = LocalHapticFeedback.current
    return { if (Fx) try { h.performHapticFeedback(HapticFeedbackType.TextHandleMove) } catch (e: Exception) {} }
}

/** Nền nút tròn (nguồn, micro): quả cầu màu nhấn khi [on]. */
fun Modifier.orb(on: Boolean): Modifier {
    val a = Pink
    val idle = CardBg
    if (!Fx) return clip(CircleShape).background(if (on) a else idle).border(1.dp, if (on) a else Bd, CircleShape)
    val dark = Theme.dark
    val glow = if (on) a else Color.Black
    return shadow(if (on) 7.dp else 3.dp, CircleShape, clip = false, ambientColor = glow, spotColor = glow)
        .clip(CircleShape)
        .drawBehind {
            if (on) drawCircle(Brush.radialGradient(listOf(lite(a, 0.45f), a, dim(a, 0.35f)), center = Offset(size.width * 0.34f, size.height * 0.26f), radius = size.width * 0.85f))
            else drawRect(Brush.verticalGradient(listOf(lite(idle, if (dark) 0.10f else 0f), dim(idle, if (dark) 0.10f else 0.05f))))
        }
        .border(1.dp, if (on) Shine else SolidColor(Bd), CircleShape)
}

/** Nút lún nhẹ khi bấm. */
@Composable
fun pressScale(src: InteractionSource, to: Float = 0.92f): Modifier {
    if (!Fx) return Modifier
    val p by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (p) to else 1f, spring(dampingRatio = 0.55f, stiffness = 700f), label = "press")
    return Modifier.graphicsLayer { scaleX = s; scaleY = s }
}

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val sh = RoundedCornerShape(Theme.r(10))
    val c = CardBg
    val m = if (Fx) {
        val dark = Theme.dark
        modifier.fillMaxWidth().shadow(if (dark) 6.dp else 4.dp, sh)
            .background(Brush.verticalGradient(listOf(lite(c, if (dark) 0.05f else 0f), dim(c, if (dark) 0.10f else 0.015f))))
            .border(1.dp, Brush.verticalGradient(listOf(if (dark) lite(Bd, 0.18f) else Bd, Bd)), sh)
    } else modifier.fillMaxWidth().clip(sh).background(c).border(1.dp, Bd, sh)
    Column(m.padding(15.dp), content = content)
}

@Composable
fun Chip(text: String, sel: Boolean, modifier: Modifier = Modifier, h: Dp = 41.dp, size: Int = 13, onClick: () -> Unit) {
    val sh = RoundedCornerShape(Theme.r(8))
    Box(
        modifier.height(h).block(sel, sh).clickable(onClick = onClick).padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) { FitT(text, size, if (sel) FontWeight.Bold else FontWeight.SemiBold, if (sel) Color.White else Txt) }
}

/**
 * Chữ tự co cho vừa ô: thử một dòng trước, thu nhỏ dần tới 80%; vẫn không vừa thì cho xuống tối đa [lines] dòng rồi thu nhỏ tiếp tới 62%.
 * Dùng cho chữ trong ô có kích thước cố định (ô chọn, ô chế độ) để không bao giờ bị cắt mất chữ khi cỡ chữ lớn hoặc ô hẹp.
 */
@Composable
fun FitT(text: String, size: Int, weight: FontWeight = FontWeight.Normal, color: Color = Txt, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Center, lines: Int = 2) {
    val str = L.tr(text)
    val base = size * Theme.scaleF
    var k by remember(str, base) { mutableFloatStateOf(1f) }
    var wrap by remember(str, base) { mutableStateOf(false) }
    var ok by remember(str, base) { mutableStateOf(false) }
    Text(
        str, modifier = modifier.drawWithContent { if (ok) drawContent() },
        fontSize = (base * k).sp, lineHeight = (base * k * 1.2f).sp, fontWeight = weight, color = color, fontFamily = Inter, textAlign = align,
        maxLines = if (wrap) lines else 1, softWrap = wrap, overflow = TextOverflow.Clip,
        onTextLayout = { r ->
            if (!r.hasVisualOverflow) ok = true
            else if (!wrap && k <= 0.82f && lines > 1) { wrap = true; k = 1f }
            else if (k > 0.62f) k -= 0.07f
            else ok = true
        }
    )
}

// ----- nút nguồn + thanh menu -----
@Composable
fun PowerBtn(size: Int = 46) {
    val on = S.on.value
    val src = remember { MutableInteractionSource() }
    val tick = rememberTick()
    Box(
        Modifier.size(size.dp).then(pressScale(src)).orb(on).clickable(src, LocalIndication.current) { tick(); S.setPower(!on) },
        contentAlignment = Alignment.Center
    ) { Icon(Icons.Filled.PowerSettingsNew, if (on) "Tắt đèn" else "Bật đèn", tint = if (on) Color.White else Grey, modifier = Modifier.size((size / 2).dp)) }
}

private fun trough(): Brush = if (Fx) Brush.verticalGradient(listOf(dim(CardBg, if (Theme.dark) 0.30f else 0.07f), CardBg)) else SolidColor(CardBg)

/** Viên màu nhấn nằm dưới mục đang chọn của thanh menu. */
private fun DrawScope.pill(a: Color, fx: Boolean, o: Offset, s: Size, r: Float) {
    val cr = CornerRadius(r, r)
    if (!fx) { drawRoundRect(a, o, s, cr); return }
    drawRoundRect(a.copy(alpha = 0.28f), Offset(o.x, o.y + 2.dp.toPx()), s, cr)
    drawRoundRect(Brush.verticalGradient(listOf(lite(a, 0.30f), a, dim(a, 0.22f)), startY = o.y, endY = o.y + s.height), o, s, cr)
    drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0f)), startY = o.y, endY = o.y + s.height), o, s, cr, style = Stroke(1.dp.toPx()))
}

@Composable
private fun tabPos(sel: Int): Float {
    val spec: AnimationSpec<Float> = if (Fx) spring(dampingRatio = 0.62f, stiffness = 420f) else snap()
    val pos by animateFloatAsState(sel.toFloat(), spec, label = "tab")
    return pos
}

/** Menu dọc cho màn ngang. */
@Composable
fun TabsV(sel: Int, onPick: (Int) -> Unit) {
    val fx = Fx
    val a = Pink
    val pos = tabPos(sel)
    val sh = RoundedCornerShape(Theme.r(10))
    val rad = Theme.r(8)
    val tick = rememberTick()
    Column(
        Modifier.clip(sh).background(trough()).border(1.dp, Bd, sh).padding(4.dp).drawBehind {
            val h = 46.dp.toPx()
            pill(a, fx, Offset(0f, pos * (h + 4.dp.toPx())), Size(size.width, h), rad.toPx())
        },
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        TAB_ITEMS.forEachIndexed { i, (label, icon) ->
            Box(Modifier.size(44.dp, 46.dp).clip(RoundedCornerShape(rad)).clickable { if (sel != i) tick(); onPick(i) }, contentAlignment = Alignment.Center) {
                Icon(icon, label, tint = if (sel == i) Color.White else SubTxt, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
fun Tabs(sel: Int, modifier: Modifier = Modifier, height: Int = 40, onPick: (Int) -> Unit) {
    val fx = Fx
    val a = Pink
    val pos = tabPos(sel)
    val sh = RoundedCornerShape(Theme.r(10))
    val rad = Theme.r(8)
    val n = TAB_ITEMS.size
    val tick = rememberTick()
    Row(
        modifier.clip(sh).background(trough()).border(1.dp, Bd, sh).padding(if (height < 36) 3.dp else 4.dp).drawBehind {
            val gap = 4.dp.toPx()
            val w = (size.width - gap * (n - 1)) / n
            pill(a, fx, Offset(pos * (w + gap), 0f), Size(w, size.height), rad.toPx())
        },
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        TAB_ITEMS.forEachIndexed { i, (label, icon) ->
            Row(
                Modifier.weight(1f).height(height.dp).clip(RoundedCornerShape(rad)).clickable { if (sel != i) tick(); onPick(i) },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
            ) { Icon(icon, label, tint = if (sel == i) Color.White else SubTxt, modifier = Modifier.size(if (height < 36) 20.dp else 22.dp)) }
        }
    }
}

// ----- thanh trượt -----
/** Thanh trượt vẽ tay. [frac] 0..1. [label] = chữ hiện trong bong bóng phía trên núm lúc đang kéo (chỉ khi bật hiệu ứng). */
@Composable
fun SkSlider(
    frac: Float, onFrac: (Float) -> Unit, color: Color, rest: Color, thumb: Color, modifier: Modifier = Modifier,
    trackH: Dp = 9.dp, thumbD: Dp = 25.dp, dots: Int = 0, label: String? = null
) {
    val cb by rememberUpdatedState(onFrac)
    val fx = Fx
    var drag by remember { mutableStateOf(false) }
    var lean by remember { mutableFloatStateOf(0f) }
    val tilt by animateFloatAsState(lean, spring(dampingRatio = 0.5f, stiffness = 300f), label = "tilt")
    val pop by animateFloatAsState(if (drag && fx && label != null) 1f else 0f, label = "pop")
    LaunchedEffect(lean) { if (lean != 0f) { delay(140); lean = 0f } }
    val tm = rememberTextMeasurer()
    val fam = Inter
    val ink = if (color.luminance() > 0.6f) Color(0xFF172032) else Color.White
    val lay = remember(label, fam, ink) {
        if (label == null) null else tm.measure(AnnotatedString(label), TextStyle(color = ink, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = fam))
    }
    Canvas(
        modifier.height(30.dp)
            .pointerInput(Unit) { detectTapGestures { o -> val r = thumbD.toPx() / 2f; cb(((o.x - r) / (size.width - 2f * r)).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                // chỉ bắt thao tác kéo NGANG: vuốt dọc ngang qua thanh trượt thì trang vẫn cuộn, không đổi nhầm giá trị
                detectHorizontalDragGestures(onDragStart = { drag = true }, onDragEnd = { drag = false; lean = 0f }, onDragCancel = { drag = false; lean = 0f }) { ch, dx ->
                    lean = (-dx * 1.5f).coerceIn(-16f, 16f)
                    val r = thumbD.toPx() / 2f
                    cb(((ch.position.x - r) / (size.width - 2f * r)).coerceIn(0f, 1f))
                }
            }
    ) {
        val r = thumbD.toPx() / 2f
        val w = size.width - 2f * r
        val cy = size.height / 2f
        val th = trackH.toPx()
        val f = frac.coerceIn(0f, 1f)
        val x = r + w * f
        val top = cy - th / 2f
        val cr = CornerRadius(th / 2f, th / 2f)
        if (fx) {
            drawRoundRect(Brush.verticalGradient(listOf(dim(rest, 0.22f), rest), startY = top, endY = top + th), Offset(r, top), Size(w, th), cr)
            val g = 2.dp.toPx()
            drawRoundRect(color.copy(alpha = 0.28f), Offset(r, top - g), Size(w * f, th + 2f * g), CornerRadius(th / 2f + g, th / 2f + g))
            drawRoundRect(Brush.verticalGradient(listOf(lite(color, 0.35f), color), startY = top, endY = top + th), Offset(r, top), Size(w * f, th), cr)
        } else {
            drawRoundRect(rest, Offset(r, top), Size(w, th), cr)
            drawRoundRect(color, Offset(r, top), Size(w * f, th), cr)
        }
        if (dots > 1) for (i in 0 until dots) drawCircle(Color(0xBFFFFFFF), 2.dp.toPx(), Offset(r + w * i / (dots - 1), cy))
        if (fx) {
            drawCircle(Color.Black.copy(alpha = 0.28f), r, Offset(x, cy + 2.5.dp.toPx()))
            drawCircle(Brush.radialGradient(listOf(lite(thumb, 0.6f), thumb, dim(thumb, 0.28f)), center = Offset(x - r * 0.3f, cy - r * 0.35f), radius = r * 1.5f), r, Offset(x, cy))
        } else {
            drawCircle(Color(0x40172032), r, Offset(x, cy + 1.5.dp.toPx()))
            drawCircle(thumb, r, Offset(x, cy))
        }
        if (pop > 0.01f && lay != null) {
            val bw = maxOf(lay.size.width + 14.dp.toPx(), 34.dp.toPx())
            val bh = 22.dp.toPx()
            val by = cy - r - 7.dp.toPx() - bh
            withTransform({ rotate(tilt, Offset(x, cy)); scale(pop, pop, Offset(x, by + bh)) }) {
                drawRoundRect(color, Offset(x - bw / 2f, by), Size(bw, bh), CornerRadius(7.dp.toPx(), 7.dp.toPx()))
                drawText(lay, topLeft = Offset(x - lay.size.width / 2f, by + (bh - lay.size.height) / 2f))
            }
        }
    }
}

/** Nút bấm một lần hoặc giữ để chạy nhanh. */
@Composable
fun RepeatBtn(icon: ImageVector, desc: String, modifier: Modifier = Modifier, onStep: () -> Unit) {
    val cb by rememberUpdatedState(onStep)
    val scope = rememberCoroutineScope()
    var down by remember { mutableStateOf(false) }
    val sh = RoundedCornerShape(Theme.r(8))
    val lift = if (Fx) Modifier.graphicsLayer { translationY = if (down) 2.dp.toPx() else 0f }.shadow(if (down) 0.dp else 3.dp, sh, clip = false) else Modifier
    Box(
        modifier.size(44.dp, 40.dp).then(lift).block(false, sh)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    down = true
                    cb()
                    val job = scope.launch { delay(400); while (true) { cb(); delay(60) } }
                    tryAwaitRelease()
                    job.cancel()
                    down = false
                })
            },
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, tint = Txt, modifier = Modifier.size(18.dp)) }
}

@Composable
fun SkSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    if (!Fx) {
        Box(
            Modifier.size(52.dp, 32.dp).clip(RoundedCornerShape(16.dp)).background(if (on) Pink else Color(0xFFE6E0E9))
                .border(2.dp, if (on) Pink else Color(0xFF79747E), RoundedCornerShape(16.dp)).clickable { onChange(!on) },
            contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(Modifier.padding(horizontal = if (on) 4.dp else 6.dp).size(if (on) 24.dp else 16.dp).clip(CircleShape).background(if (on) Color.White else Color(0xFF79747E)))
        }
        return
    }
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val t by animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "sw")
    val stretch by animateFloatAsState(if (pressed) 1f else 0f, label = "swp")
    val a = Pink
    val off = SoftTrack
    val tick = rememberTick()
    Box(Modifier.size(52.dp, 32.dp).clip(RoundedCornerShape(16.dp)).clickable(src, null) { tick(); onChange(!on) }.drawBehind {
        val h = size.height
        val base = lerp(off, a, t.coerceIn(0f, 1f))
        drawRoundRect(Brush.verticalGradient(listOf(dim(base, 0.30f), base)), cornerRadius = CornerRadius(h / 2f, h / 2f))
        val d = 24.dp.toPx()
        val kw = d + 8.dp.toPx() * stretch
        val pad = 4.dp.toPx()
        val x = pad + (size.width - 2f * pad - kw) * t
        val y = (h - d) / 2f
        val cr = CornerRadius(d / 2f, d / 2f)
        drawRoundRect(Color.Black.copy(alpha = 0.30f), Offset(x, y + 2.dp.toPx()), Size(kw, d), cr)
        drawRoundRect(
            Brush.radialGradient(listOf(Color.White, Color(0xFFE2E4EA), Color(0xFFA9ADB8)), center = Offset(x + kw * 0.35f, y + d * 0.28f), radius = kw * 0.9f),
            Offset(x, y), Size(kw, d), cr
        )
    })
}

/** Bóng màu nổi khối trong bảng chọn màu; bóng đang chọn to hơn, có vòng viền và quầng sáng theo màu của nó. */
@Composable
fun ColorOrb(col: Color, on: Boolean, gestures: Modifier, content: @Composable BoxScope.() -> Unit) {
    val sc by animateFloatAsState(if (on) 1.12f else 1f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "orb")
    val ring = Txt
    val gapC = CardBg
    Box(
        Modifier.size(39.dp).graphicsLayer { scaleX = sc; scaleY = sc }
            .shadow(if (on) 9.dp else 3.dp, CircleShape, clip = false, ambientColor = col, spotColor = col)
            .clip(CircleShape)
            .drawBehind {
                drawCircle(Brush.radialGradient(listOf(lite(col, 0.55f), col, dim(col, 0.32f)), center = Offset(size.width * 0.35f, size.height * 0.28f), radius = size.width * 0.85f))
                if (on) {
                    val rr = size.minDimension / 2f
                    drawCircle(ring, rr - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
                    drawCircle(gapC, rr - 3.dp.toPx(), style = Stroke(2.dp.toPx()))
                }
            }
            .then(gestures),
        contentAlignment = Alignment.Center, content = content
    )
}

// ----- các nút chữ dùng chung -----
@Composable
fun OutlineBtn(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    Box(
        modifier.height(44.dp).then(pressScale(src, 0.96f)).block(false, RoundedCornerShape(Theme.r(8)), CardBg).clickable(src, LocalIndication.current, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { T(text, 12, FontWeight.SemiBold) }
}

@Composable
fun FillBtn(text: String, icon: ImageVector, bg: Color, modifier: Modifier, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    Row(
        modifier.height(43.dp).then(pressScale(src, 0.96f)).block(true, RoundedCornerShape(Theme.r(6)), accent = bg).clickable(src, LocalIndication.current, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        T(text, 13, FontWeight.Bold, Color.White)
    }
}

/** Nút mũi tên chọn hướng ánh sáng chạy. */
@Composable
fun DirBtn(icon: ImageVector, desc: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val sh = RoundedCornerShape(Theme.r(10))
    val m = if (on) modifier.height(44.dp).block(true, sh) else modifier.height(44.dp).clip(sh).background(tint(0.12f)).border(1.dp, tint(0.5f), sh)
    Box(m.clickable(onClick = onClick), contentAlignment = Alignment.Center) { Icon(icon, desc, tint = if (on) Color.White else Pink, modifier = Modifier.size(20.dp)) }
}
