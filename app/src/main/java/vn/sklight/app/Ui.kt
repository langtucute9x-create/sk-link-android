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

private fun pal(vararg c: Long) = Pal(Color(c[0]), Color(c[1]), Color(c[2]), Color(c[3]), Color(c[4]), Color(c[5]), Color(c[6]), Color(c[7]), Color(c[8]), Color(c[9]))
// Mỗi phong cách có một bảng màu sáng và một bảng màu tối. Thứ tự: nền trên, nền dưới, thẻ, chữ, chữ phụ, chữ xám, ô, viền, vạch chia, rãnh trượt.
private val LIGHTS = listOf(
    pal(0xFFFCF5F9, 0xFFF2F0FB, 0xFFFFFFFF, 0xFF172032, 0xFF4B5568, 0xFF6B7385, 0xFFF4F6FC, 0xFFDDE3EF, 0xFFEAEEF6, 0xFFE6E9F2),      // Mặc định (giao diện gốc)
    pal(0xFFFFFFFF, 0xFFF7F8FA, 0xFFFFFFFF, 0xFF111827, 0xFF4B5563, 0xFF6B7280, 0xFFF3F4F6, 0xFFE5E7EB, 0xFFEEF0F3, 0xFFE5E7EB),      // Tinh khôi
    pal(0xFFFBF6EE, 0xFFF5EEE2, 0xFFFFFDF8, 0xFF2B2118, 0xFF5C5046, 0xFF7A6E63, 0xFFF6EFE4, 0xFFE6DCCB, 0xFFEFE7DA, 0xFFEADFCD),      // Kem ấm
    pal(0xFFF1F6FB, 0xFFE8F0F8, 0xFFFFFFFF, 0xFF12202F, 0xFF465669, 0xFF66768A, 0xFFF0F5FA, 0xFFD5E0EC, 0xFFE4ECF4, 0xFFDDE7F1),      // Xanh băng
    pal(0xFFF3F8F3, 0xFFE9F2EA, 0xFFFFFFFF, 0xFF15251A, 0xFF46594C, 0xFF68796D, 0xFFF0F6F1, 0xFFD6E3D8, 0xFFE4EEE6, 0xFFDDE9DF)       // Lá non
)
private val DARKS = listOf(
    pal(0xFF0E1118, 0xFF12141C, 0xFF1A1F2B, 0xFFF2F4F8, 0xFFC3C9D6, 0xFF98A1B3, 0xFF242A38, 0xFF343C4D, 0xFF2A3140, 0xFF343C4D),      // Mặc định
    pal(0xFF000000, 0xFF000000, 0xFF0E0F12, 0xFFF4F5F7, 0xFFC5C9D2, 0xFF9AA1AE, 0xFF17191E, 0xFF2A2D35, 0xFF1F2228, 0xFF2A2D35),      // Đen tuyền
    pal(0xFF14100C, 0xFF18130E, 0xFF211A14, 0xFFF6EFE6, 0xFFD2C6B8, 0xFFA99C8D, 0xFF2B231B, 0xFF40352A, 0xFF332A21, 0xFF40352A),      // Nâu ấm
    pal(0xFF0A1220, 0xFF0C1626, 0xFF131E31, 0xFFEEF3FA, 0xFFBECADB, 0xFF93A2B8, 0xFF1B2940, 0xFF2C3D58, 0xFF233349, 0xFF2C3D58),      // Xanh đêm
    pal(0xFF0B1410, 0xFF0D1813, 0xFF14211A, 0xFFEEF6F0, 0xFFBDD0C3, 0xFF93A89A, 0xFF1C2C23, 0xFF2D4236, 0xFF24362C, 0xFF2D4236)       // Rừng đêm
)

/** Cài đặt giao diện: chế độ sáng / tối / tự động, phong cách nền, màu nhấn, bo góc, kiểu chữ, cỡ chữ. Mặc định = giao diện gốc (sáng, hồng SK, Inter). */
object Theme {
    val mode = mutableIntStateOf(0)        // 0 sáng, 1 tối, 2 tự động theo giờ
    val style = mutableIntStateOf(0)       // phong cách nền
    val accent = mutableIntStateOf(0)
    val corner = mutableIntStateOf(1)      // 0 vuông, 1 vừa (gốc), 2 tròn
    val font = mutableIntStateOf(0)
    val scale = mutableIntStateOf(1)
    val fx = mutableStateOf(true)          // hiệu ứng nổi khối + chuyển động (tắt = "Giảm hiệu ứng")
    val nightNow = mutableStateOf(false)   // đang là ban đêm (cho chế độ tự động)
    val MODES = listOf("Sáng", "Tối", "Tự động")
    /** Tên phong cách: (khi sáng, khi tối). */
    val STYLES = listOf("Mặc định" to "Mặc định", "Tinh khôi" to "Đen tuyền", "Kem ấm" to "Nâu ấm", "Xanh băng" to "Xanh đêm", "Lá non" to "Rừng đêm")
    // 6 màu đầu giữ nguyên thứ tự cũ (số thứ tự đã lưu trong máy người dùng)
    val ACCENTS = listOf(
        "Hồng SK" to 0xFE2CAA, "Cam" to 0xE8590C, "Đỏ" to 0xE5334B, "Tím" to 0x7C4DFF, "Xanh dương" to 0x2F6BFF, "Xanh ngọc" to 0x0E9488,
        "Xanh lá" to 0x2E9E44, "Xanh lơ" to 0x0891B2, "Chàm" to 0x4F46E5, "Tím hồng" to 0xC026D3, "Hổ phách" to 0xD98A00, "Xám thép" to 0x526079
    )
    val CORNERS = listOf("Vuông", "Vừa", "Tròn")
    // 3 kiểu đầu giữ nguyên thứ tự cũ. Các kiểu sau là phông có đủ dấu tiếng Việt, đóng sẵn trong app (giấy phép OFL, xem assets/font-licenses.txt).
    val FONTS = listOf("Inter", "Chữ hệ thống", "Chữ có chân", "Be Vietnam Pro", "Nunito", "Montserrat", "Quicksand", "Lexend", "Oswald", "Lora", "Baloo 2")
    val SCALES = listOf("Nhỏ" to 0.9f, "Vừa" to 1f, "Lớn" to 1.12f, "Rất lớn" to 1.25f)
    val dark: Boolean get() = mode.intValue == 1 || (mode.intValue == 2 && nightNow.value)
    val pal: Pal get() = (if (dark) DARKS else LIGHTS)[style.intValue.coerceIn(0, LIGHTS.size - 1)]
    fun palOf(style: Int, dark: Boolean): Pal = (if (dark) DARKS else LIGHTS)[style.coerceIn(0, LIGHTS.size - 1)]
    val scaleF: Float get() = SCALES[scale.intValue.coerceIn(0, SCALES.size - 1)].second
    /** Bán kính bo góc theo kiểu đang chọn; [base] là bán kính của giao diện gốc. */
    fun r(base: Int): Dp = when (corner.intValue) { 0 -> minOf(base, 3).dp; 2 -> (base * 1.8f).dp; else -> base.dp }
    fun reset() { mode.intValue = 0; style.intValue = 0; accent.intValue = 0; corner.intValue = 1; font.intValue = 0; scale.intValue = 1 }
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
private fun fam(vararg f: Pair<Int, Int>) = FontFamily(f.map { Font(it.first, FontWeight(it.second)) })
private val FAMS: List<FontFamily> by lazy {
    listOf(
        InterFam, FontFamily.Default, FontFamily.Serif,
        fam(R.font.bevietnam_400 to 400, R.font.bevietnam_500 to 500, R.font.bevietnam_600 to 600, R.font.bevietnam_700 to 700, R.font.bevietnam_800 to 800),
        fam(R.font.nunito_400 to 400, R.font.nunito_500 to 500, R.font.nunito_600 to 600, R.font.nunito_700 to 700, R.font.nunito_800 to 800),
        fam(R.font.montserrat_400 to 400, R.font.montserrat_500 to 500, R.font.montserrat_600 to 600, R.font.montserrat_700 to 700, R.font.montserrat_800 to 800),
        fam(R.font.quicksand_400 to 400, R.font.quicksand_500 to 500, R.font.quicksand_600 to 600, R.font.quicksand_700 to 700),
        fam(R.font.lexend_400 to 400, R.font.lexend_500 to 500, R.font.lexend_600 to 600, R.font.lexend_700 to 700, R.font.lexend_800 to 800),
        fam(R.font.oswald_400 to 400, R.font.oswald_500 to 500, R.font.oswald_600 to 600, R.font.oswald_700 to 700),
        fam(R.font.lora_400 to 400, R.font.lora_500 to 500, R.font.lora_600 to 600, R.font.lora_700 to 700),
        fam(R.font.baloo_400 to 400, R.font.baloo_500 to 500, R.font.baloo_600 to 600, R.font.baloo_700 to 700, R.font.baloo_800 to 800)
    )
}
/** Phông chữ thứ [i] trong Theme.FONTS (để vẽ ô xem thử). */
fun fontFam(i: Int): FontFamily = FAMS.getOrElse(i) { InterFam }
/** Kiểu chữ đang chọn (tên biến giữ là Inter vì mặc định là Inter). */
val Inter: FontFamily get() = fontFam(Theme.font.intValue)

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
fun GroupTitle(icon: ImageVector, text: String, size: Int = 17) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 30.dp)) {
        Icon(icon, null, tint = Pink, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(10.dp))
        T(text, size, FontWeight.ExtraBold)
    }
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


/** Xếp các thẻ con thành hai cột (thẻ nào vào cột đang ngắn hơn); phần tử đầu (dòng "quay lại") chiếm cả chiều ngang. Dùng cho trang Cài đặt con ở màn ngang. */
@androidx.compose.runtime.Composable
fun Masonry(gap: androidx.compose.ui.unit.Dp = 12.dp, content: @androidx.compose.runtime.Composable () -> Unit) {
    androidx.compose.ui.layout.Layout(content = content) { ms, c ->
        val g = gap.roundToPx()
        val w = ((c.maxWidth - g) / 2).coerceAtLeast(0)
        val cons = androidx.compose.ui.unit.Constraints(minWidth = w, maxWidth = w)
        val full = androidx.compose.ui.unit.Constraints(maxWidth = c.maxWidth)
        var top = 0
        val placed = ArrayList<Triple<androidx.compose.ui.layout.Placeable, Int, Int>>()
        val hs = intArrayOf(0, 0)
        ms.forEachIndexed { i, m ->
            if (i == 0) {
                val p = m.measure(full); placed.add(Triple(p, -1, 0)); top = p.height + g; hs[0] = top; hs[1] = top
            } else {
                val p = m.measure(cons)
                if (p.height == 0) { placed.add(Triple(p, 0, 0)); return@forEachIndexed }
                val col = if (hs[0] <= hs[1]) 0 else 1
                val y = hs[col]; hs[col] += p.height + g
                placed.add(Triple(p, col, y))
            }
        }
        layout(c.maxWidth, (maxOf(hs[0], hs[1]) - g).coerceAtLeast(0)) { placed.forEach { (p, col, y) -> p.placeRelative(if (col <= 0) 0 else w + g, y) } }
    }
}
