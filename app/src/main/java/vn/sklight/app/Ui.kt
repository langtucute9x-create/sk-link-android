package vn.sklight.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Bảng màu của một giao diện (sáng hoặc tối). */
class Pal(val bgTop: Color, val bgBot: Color, val card: Color, val txt: Color, val sub: Color, val grey: Color, val chip: Color, val bd: Color, val div: Color, val soft: Color)

private val LIGHT = Pal(Color(0xFFFCF5F9), Color(0xFFF2F0FB), Color.White, Color(0xFF172032), Color(0xFF4B5568), Color(0xFF6B7385), Color(0xFFF4F6FC), Color(0xFFDDE3EF), Color(0xFFEAEEF6), Color(0xFFE6E9F2))
private val NIGHT = Pal(Color(0xFF0E1118), Color(0xFF12141C), Color(0xFF1A1F2B), Color(0xFFF2F4F8), Color(0xFFC3C9D6), Color(0xFF98A1B3), Color(0xFF242A38), Color(0xFF343C4D), Color(0xFF2A3140), Color(0xFF343C4D))

/** Cài đặt giao diện: chế độ sáng / tối / tự động, màu nhấn, kiểu chữ, cỡ chữ. Mặc định = giao diện gốc (sáng, hồng SK, Inter). */
object Theme {
    val mode = mutableIntStateOf(0)        // 0 sáng, 1 tối, 2 tự động theo giờ
    val accent = mutableIntStateOf(0)
    val font = mutableIntStateOf(0)
    val scale = mutableIntStateOf(1)
    val nightNow = mutableStateOf(false)   // đang là ban đêm (cho chế độ tự động)
    val MODES = listOf("Sáng", "Tối", "Tự động")
    val ACCENTS = listOf("Hồng SK" to 0xFE2CAA, "Cam" to 0xE8590C, "Đỏ" to 0xE5334B, "Tím" to 0x7C4DFF, "Xanh dương" to 0x2F6BFF, "Xanh ngọc" to 0x0E9488)
    val FONTS = listOf("Inter", "Chữ hệ thống", "Chữ có chân")
    val SCALES = listOf("Nhỏ" to 0.9f, "Vừa" to 1f, "Lớn" to 1.12f, "Rất lớn" to 1.25f)
    val dark: Boolean get() = mode.intValue == 1 || (mode.intValue == 2 && nightNow.value)
    val pal: Pal get() = if (dark) NIGHT else LIGHT
    val scaleF: Float get() = SCALES[scale.intValue.coerceIn(0, SCALES.size - 1)].second
    fun reset() { mode.intValue = 0; accent.intValue = 0; font.intValue = 0; scale.intValue = 1 }
}

/** Màu nhấn (tên biến giữ là Pink vì mặc định là hồng SK). */
val Pink: Color get() = rgbColor(Theme.ACCENTS[Theme.accent.intValue.coerceIn(0, Theme.ACCENTS.size - 1)].second)
val Txt: Color get() = Theme.pal.txt
val SubTxt: Color get() = Theme.pal.sub
val Grey: Color get() = Theme.pal.grey
val ChipBg: Color get() = Theme.pal.chip
val Bd: Color get() = Theme.pal.bd
val Div: Color get() = Theme.pal.div
val CardBg: Color get() = Theme.pal.card
val SoftTrack: Color get() = Theme.pal.soft
/** Chữ màu nhấn đặt trên nền thẻ: đậm hơn ở giao diện sáng, nhạt hơn ở giao diện tối cho dễ đọc. */
val AccentText: Color get() = if (Theme.dark) lerp(Pink, Color.White, 0.3f) else lerp(Pink, Color.Black, 0.22f)
val Cyan = Color(0xFF1AF4EA)
val OkGreen = Color(0xFF4DAE50)

private val InterFam = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal), Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold), Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_extrabold, FontWeight.ExtraBold)
)
/** Kiểu chữ đang chọn (tên biến giữ là Inter vì mặc định là Inter). */
val Inter: FontFamily get() = when (Theme.font.intValue) { 1 -> FontFamily.Default; 2 -> FontFamily.Serif; else -> InterFam }

/** Pha màu [c] với nền thẻ: k = 1 giữ nguyên, k = 0 thành màu nền thẻ. */
fun mix(c: Color, k: Float) = lerp(CardBg, c, k)
/** Màu nhấn pha với nền thẻ theo tỉ lệ [k] (nền ô đang chọn, viền nhạt…). */
fun tint(k: Float) = lerp(CardBg, Pink, k)
fun rgbColor(rgb: Int) = Color(0xFF000000 or (rgb.toLong() and 0xFFFFFF))

@Composable
fun T(text: String, size: Int = 14, weight: FontWeight = FontWeight.Normal, color: Color = Txt, modifier: Modifier = Modifier, align: TextAlign? = null) {
    val k = Theme.scaleF
    Text(L.tr(text), modifier = modifier, fontSize = (size * k).sp, fontWeight = weight, color = color, fontFamily = Inter, textAlign = align, lineHeight = (size * 1.25f * k).sp)
}

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CardBg)
            .border(1.dp, Bd, RoundedCornerShape(10.dp)).padding(15.dp),
        content = content
    )
}

@Composable
fun GroupTitle(icon: ImageVector, text: String, size: Int = 17) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 30.dp)) {
        Icon(icon, null, tint = Pink, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(10.dp))
        T(text, size, FontWeight.ExtraBold)
    }
}

@Composable
fun Chip(text: String, sel: Boolean, modifier: Modifier = Modifier, h: Dp = 41.dp, size: Int = 13, onClick: () -> Unit) {
    Box(
        modifier.height(h).clip(RoundedCornerShape(8.dp)).background(if (sel) Pink else ChipBg)
            .border(1.dp, if (sel) Pink else Bd, RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) { T(text, size, if (sel) FontWeight.Bold else FontWeight.SemiBold, if (sel) Color.White else Txt, align = TextAlign.Center) }
}

/** Lưới đều cột: mỗi ô nhận Modifier.weight(1f). */
@Composable
fun <X> Grid(items: List<X>, cols: Int, gap: Dp = 9.dp, rowGap: Dp = gap, cell: @Composable (X, Modifier) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(rowGap)) {
        items.chunked(cols).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEach { cell(it, Modifier.weight(1f)) }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun ZoneTabs(sel: Int, onPick: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        listOf("Đồng nhất", "Khu vực 1", "Khu vực 2").forEachIndexed { i, t -> Chip(t, sel == i, Modifier.weight(1f)) { onPick(i) } }
    }
}

/** Thanh trượt vẽ tay cho giống app gốc. [frac] 0..1. */
@Composable
fun SkSlider(
    frac: Float, onFrac: (Float) -> Unit, color: Color, rest: Color, thumb: Color, modifier: Modifier = Modifier,
    trackH: Dp = 9.dp, thumbD: Dp = 25.dp, dots: Int = 0
) {
    val cb by rememberUpdatedState(onFrac)
    Canvas(
        modifier.height(30.dp)
            .pointerInput(Unit) { detectTapGestures { o -> val r = thumbD.toPx() / 2f; cb(((o.x - r) / (size.width - 2f * r)).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) { detectDragGestures { ch, _ -> val r = thumbD.toPx() / 2f; cb(((ch.position.x - r) / (size.width - 2f * r)).coerceIn(0f, 1f)) } }
    ) {
        val r = thumbD.toPx() / 2f
        val w = size.width - 2f * r
        val cy = size.height / 2f
        val th = trackH.toPx()
        val f = frac.coerceIn(0f, 1f)
        drawRoundRect(rest, Offset(r, cy - th / 2f), Size(w, th), CornerRadius(th / 2f, th / 2f))
        drawRoundRect(color, Offset(r, cy - th / 2f), Size(w * f, th), CornerRadius(th / 2f, th / 2f))
        if (dots > 1) for (i in 0 until dots) drawCircle(Color(0xBFFFFFFF), 2.dp.toPx(), Offset(r + w * i / (dots - 1), cy))
        drawCircle(Color(0x40172032), r, Offset(r + w * f, cy + 1.5.dp.toPx()))
        drawCircle(thumb, r, Offset(r + w * f, cy))
    }
}

/** Nút bấm một lần hoặc giữ để chạy nhanh. */
@Composable
fun RepeatBtn(icon: ImageVector, desc: String, modifier: Modifier = Modifier, onStep: () -> Unit) {
    val cb by rememberUpdatedState(onStep)
    val scope = rememberCoroutineScope()
    Box(
        modifier.size(44.dp, 40.dp).clip(RoundedCornerShape(8.dp)).background(ChipBg).border(1.dp, Bd, RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    cb()
                    val job = scope.launch { delay(400); while (true) { cb(); delay(60) } }
                    tryAwaitRelease()
                    job.cancel()
                })
            },
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, tint = Txt, modifier = Modifier.size(18.dp)) }
}

@Composable
fun SkSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    Box(
        Modifier.size(52.dp, 32.dp).clip(RoundedCornerShape(16.dp)).background(if (on) Pink else Color(0xFFE6E0E9))
            .border(2.dp, if (on) Pink else Color(0xFF79747E), RoundedCornerShape(16.dp)).clickable { onChange(!on) },
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(Modifier.padding(horizontal = if (on) 4.dp else 6.dp).size(if (on) 24.dp else 16.dp).clip(CircleShape).background(if (on) Color.White else Color(0xFF79747E)))
    }
}

@Composable
fun SwitchRow(title: String, sub: String, on: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 58.dp).alpha(if (enabled) 1f else 0.45f)) {
        Column(Modifier.weight(1f)) { T(title, 15, FontWeight.Bold); T(sub, 12, color = Grey) }
        SkSwitch(on && enabled) { if (enabled) onChange(it) }
    }
}

@Composable
fun Divider(top: Dp = 0.dp, bottom: Dp = 0.dp) {
    Box(Modifier.padding(top = top, bottom = bottom).fillMaxWidth().height(1.dp).background(Div))
}
