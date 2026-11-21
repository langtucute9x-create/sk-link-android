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

val Pink = Color(0xFFFE2CAA)
val Txt = Color(0xFF172032)
val SubTxt = Color(0xFF4B5568)
val Grey = Color(0xFF6B7385)
val ChipBg = Color(0xFFF4F6FC)
val Bd = Color(0xFFDDE3EF)
val Div = Color(0xFFEAEEF6)
val Cyan = Color(0xFF1AF4EA)
val OkGreen = Color(0xFF4DAE50)

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal), Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold), Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_extrabold, FontWeight.ExtraBold)
)

/** Pha màu với trắng: k = 1 giữ nguyên, k = 0 thành trắng. */
fun mix(c: Color, k: Float) = Color(1f - (1f - c.red) * k, 1f - (1f - c.green) * k, 1f - (1f - c.blue) * k)
fun rgbColor(rgb: Int) = Color(0xFF000000 or (rgb.toLong() and 0xFFFFFF))

@Composable
fun T(text: String, size: Int = 14, weight: FontWeight = FontWeight.Normal, color: Color = Txt, modifier: Modifier = Modifier, align: TextAlign? = null) {
    Text(text, modifier = modifier, fontSize = size.sp, fontWeight = weight, color = color, fontFamily = Inter, textAlign = align, lineHeight = (size * 1.25f).sp)
}

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color.White)
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
