package vn.sklight.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ===================== THÔNG BÁO XẾP CHỒNG, THANH TIẾN ĐỘ, HÌNH TRẠNG THÁI TRỐNG =====================
// Ý tưởng hình thức tham khảo kho Lucian UX/UI (MIT, github.com/Seokwoooo/lucian-uxui-prompt): mẫu 070, 082, 088. Viết lại bằng Compose.

/**
 * Thông báo ngắn của app. Khi app đang mở và cửa sổ chính đang được chọn thì hiện thẻ xếp chồng ở cuối màn hình;
 * còn lại (app chạy nền, đang mở hộp thoại hay danh sách thả xuống) thì dùng thông báo của hệ thống để chắc chắn nhìn thấy.
 */
object Notice {
    class Item(val id: Long, val text: String)
    val items = mutableStateListOf<Item>()
    @Volatile var winFocus = false
    private val main = Handler(Looper.getMainLooper())
    private var seq = 0L

    fun show(c: Context, text: String, long: Boolean = false) {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post { show(c, text, long) }; return }
        if (!(S.appVisible && winFocus)) {
            try { Toast.makeText(c.applicationContext, text, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show() } catch (e: Exception) {}
            return
        }
        if (items.any { it.text == text }) return
        val item = Item(++seq, text)
        items.add(item)
        while (items.size > 3) items.removeAt(0)
        main.postDelayed({ items.remove(item) }, if (long) 4500L else 2600L)
    }
}

@Composable
fun NoticeHost(modifier: Modifier = Modifier) {
    if (Notice.items.isEmpty()) return
    Column(modifier.navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val n = Notice.items.size
        Notice.items.forEachIndexed { i, item -> key(item.id) { NoticeCard(item, n - 1 - i) } }
    }
}

@Composable
private fun NoticeCard(item: Notice.Item, depth: Int) {
    val a = remember { Animatable(if (Fx) 0f else 1f) }
    LaunchedEffect(Unit) { if (Fx) a.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 400f)) }
    val back by animateFloatAsState(1f - 0.05f * depth, label = "noticeDepth")
    val sh = RoundedCornerShape(14.dp)
    Row(
        Modifier.widthIn(max = 440.dp)
            .graphicsLayer { val v = a.value; alpha = (v * (1f - 0.18f * depth)).coerceIn(0f, 1f); translationY = (1f - v) * 24.dp.toPx(); scaleX = back; scaleY = back }
            .shadow(8.dp, sh).background(Color(0xF21B1C24)).border(1.dp, Color(0x26FFFFFF), sh)
            .clickable { Notice.items.remove(item) }.padding(start = 12.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(Pink))
        Spacer(Modifier.width(10.dp))
        Text(item.text, fontSize = (13 * Theme.scaleF).sp, lineHeight = (17 * Theme.scaleF).sp, fontWeight = FontWeight.SemiBold, color = Color.White, fontFamily = Inter)
    }
}

/** Thanh tiến độ: rãnh lõm, phần đã chạy màu nhấn. [frac] 0..1. */
@Composable
fun SkProgress(frac: Float, modifier: Modifier = Modifier) {
    val f by animateFloatAsState(frac.coerceIn(0f, 1f), label = "progress")
    val a = Pink
    val sh = RoundedCornerShape(5.dp)
    Box(modifier.fillMaxWidth().height(10.dp).clip(sh).background(if (Fx) Brush.verticalGradient(listOf(dim(SoftTrack, 0.22f), SoftTrack)) else Brush.verticalGradient(listOf(SoftTrack, SoftTrack)))) {
        Box(Modifier.fillMaxWidth(f).fillMaxHeight().clip(sh).background(if (Fx) Brush.verticalGradient(listOf(lite(a, 0.35f), a)) else Brush.verticalGradient(listOf(a, a))))
    }
}

/** Hình nhỏ cho thẻ "Chế độ của tôi" khi chưa có chế độ nào: ba quả cầu màu chồng lên nhau. */
@Composable
fun EmptyOrbs(modifier: Modifier = Modifier) {
    val fx = Fx
    Canvas(modifier.size(76.dp, 44.dp)) {
        val r = size.height * 0.42f
        val cy = size.height * 0.52f
        val cols = listOf(Color(0xFFFE2B48), Color(0xFF4E78FF), Color(0xFFFFC400))
        val xs = listOf(r + 2.dp.toPx(), size.width / 2f, size.width - r - 2.dp.toPx())
        for (i in listOf(0, 2, 1)) {
            val c = Offset(xs[i], if (i == 1) cy - 3.dp.toPx() else cy)
            if (fx) {
                drawCircle(cols[i].copy(alpha = 0.25f), r * 1.18f, c)
                drawCircle(Brush.radialGradient(listOf(lite(cols[i], 0.55f), cols[i], dim(cols[i], 0.3f)), Offset(c.x - r * 0.3f, c.y - r * 0.35f), r * 1.5f), r, c)
            } else drawCircle(cols[i], r, c)
        }
    }
}
