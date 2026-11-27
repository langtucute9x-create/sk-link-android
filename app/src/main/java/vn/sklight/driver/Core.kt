package vn.sklight.driver

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import kotlin.math.roundToInt

/** Màu và vài hàm dựng giao diện dùng chung (kiểu nền đen, xanh lá nhạt, chữ trắng). */
object Ui {
    const val GREEN = 0xFFA6F08F.toInt()
    const val AMBER = 0xFFFFC043.toInt()
    const val RED = 0xFFFF6A62.toInt()
    const val GRAY = 0xFFD6D8DC.toInt()
    const val MUTED = 0xFFB9BCC2.toInt()
    const val PINK = 0xFFFE2CAA.toInt()
    const val CARD = 0xFF161617.toInt()
    const val PANEL = 0xE618181A.toInt()
    const val LINE = 0x33FFFFFF
    const val WHITE = 0xFFFFFFFF.toInt()
    const val BLACK = 0xFF000000.toInt()

    fun levelColor(l: Int) = when (l) { 1, 4 -> GREEN; 2 -> AMBER; 3 -> RED; else -> GRAY }

    fun dp(c: Context, v: Float) = (v * c.resources.displayMetrics.density).roundToInt()

    fun bg(color: Int, radius: Float, stroke: Int = 0, strokeW: Int = 0) = GradientDrawable().apply {
        setColor(color); cornerRadius = radius; if (strokeW > 0) setStroke(strokeW, stroke)
    }

    fun text(c: Context, s: String, size: Float, color: Int = WHITE, bold: Boolean = false) = TextView(c).apply {
        text = s; textSize = size; setTextColor(color); if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    /** Nút viên thuốc. filled = nền trắng chữ đen, không thì viền mờ chữ trắng. */
    fun pill(c: Context, s: String, filled: Boolean = false, onClick: () -> Unit): Button = Button(c).apply {
        text = s; isAllCaps = false; textSize = 15f; typeface = Typeface.DEFAULT_BOLD; stateListAnimator = null
        minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0
        setPadding(dp(c, 20f), 0, dp(c, 20f), 0)
        stylePill(this, filled)
        setOnClickListener { onClick() }
    }

    fun stylePill(b: Button, filled: Boolean, accent: Int = WHITE) {
        val r = dp(b.context, 26f).toFloat()
        if (filled) { b.background = bg(accent, r); b.setTextColor(BLACK) }
        else { b.background = bg(0xFF1B1B1D.toInt(), r, 0x55FFFFFF, dp(b.context, 1f)); b.setTextColor(WHITE) }
    }

    fun card(c: Context) = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        background = bg(CARD, dp(c, 20f).toFloat(), LINE, dp(c, 1f))
        setPadding(dp(c, 18f), dp(c, 14f), dp(c, 18f), dp(c, 14f))
    }

    /** Một dòng cài đặt: tên + mô tả bên trái, điều khiển bên phải. */
    fun row(c: Context, title: String, sub: String, control: View?): LinearLayout = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(c, 56f)
        val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        col.addView(text(c, title, 15f, WHITE, true))
        if (sub.isNotEmpty()) col.addView(text(c, sub, 12f, MUTED))
        addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        if (control != null) addView(control, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(c, 12f) })
    }

    fun soon(c: Context) = text(c, "Sắp có", 13f, MUTED, true).apply {
        background = bg(0, dp(c, 16f).toFloat(), 0x55FFFFFF, dp(c, 1f)); setPadding(dp(c, 14f), dp(c, 6f), dp(c, 14f), dp(c, 6f))
    }

    fun switch(c: Context, on: Boolean, onSet: (Boolean) -> Unit) = Switch(c).apply {
        isChecked = on
        val st = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        thumbTintList = ColorStateList(st, intArrayOf(WHITE, 0xFFCFCFD4.toInt()))
        trackTintList = ColorStateList(st, intArrayOf(GREEN, 0xFF55555A.toInt()))
        setOnCheckedChangeListener { _, v -> onSet(v) }
    }

    /** Ô chọn một trong nhiều: nút đang chọn nền trắng chữ đen. */
    fun seg(c: Context, labels: List<String>, sel: Int, onPick: (Int) -> Unit): LinearLayout {
        val box = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            background = bg(0xFF0A0A0B.toInt(), dp(c, 26f).toFloat(), 0x44FFFFFF, dp(c, 1f))
            setPadding(dp(c, 4f), dp(c, 4f), dp(c, 4f), dp(c, 4f))
        }
        val btns = ArrayList<Button>()
        fun paint(s: Int) = btns.forEachIndexed { i, b ->
            b.background = bg(if (i == s) WHITE else 0, dp(c, 22f).toFloat()); b.setTextColor(if (i == s) BLACK else WHITE)
        }
        labels.forEachIndexed { i, l ->
            val b = Button(c).apply {
                text = l; isAllCaps = false; textSize = 14f; typeface = Typeface.DEFAULT_BOLD; stateListAnimator = null
                minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0
                setPadding(dp(c, 16f), 0, dp(c, 16f), 0)
                setOnClickListener { paint(i); onPick(i) }
            }
            btns.add(b); box.addView(b, LinearLayout.LayoutParams(-2, dp(c, 44f)))
        }
        paint(sel)
        return box
    }
}

/** Thông số cân chỉnh và cài đặt, lưu trong SharedPreferences "skdriver". */
class Cfg(c: Context) {
    private val sp: SharedPreferences = c.getSharedPreferences("skdriver", Context.MODE_PRIVATE)
    @Volatile var horizon = sp.getFloat("horizon", 0.5f)     // chân trời: 0 = mép trên hình, 1 = mép dưới
    @Volatile var vx = sp.getFloat("vx", 0f)                 // hướng thẳng lệch khỏi giữa hình, tính theo bề rộng hình
    @Volatile var laneK = sp.getFloat("laneK", 1.4f)         // nửa bề rộng làn (điểm ảnh) trên mỗi điểm ảnh dưới chân trời
    @Volatile var camH = sp.getFloat("camH", 1.25f)          // độ cao camera (m)
    @Volatile var vfov = sp.getFloat("vfov", 50f)            // góc nhìn dọc (độ)
    @Volatile var gapSec = sp.getFloat("gap", 2f)            // khoảng cách tối thiểu theo giây
    @Volatile var startKmh = sp.getInt("startKmh", 40)       // bắt đầu cảnh báo từ tốc độ này
    @Volatile var beep = sp.getBoolean("beep", true)
    @Volatile var thermal = sp.getBoolean("thermal", true)
    @Volatile var link = sp.getBoolean("link", true)         // gửi mức cảnh báo sang SK Light
    @Volatile var gpu = sp.getBoolean("gpu", false)
    @Volatile var kinds = sp.getInt("kinds", 0x1F)           // bit theo Kind: 0 ô tô, 1 xe máy, 2 xe đạp, 3 người, 4 động vật

    fun save() {
        sp.edit().putFloat("horizon", horizon).putFloat("vx", vx).putFloat("laneK", laneK).putFloat("camH", camH).putFloat("vfov", vfov)
            .putFloat("gap", gapSec).putInt("startKmh", startKmh).putBoolean("beep", beep).putBoolean("thermal", thermal)
            .putBoolean("gpu", gpu).putBoolean("link", link).putInt("kinds", kinds or 1).apply()
    }
}

/** Nhóm vật thể mà mô hình (bộ COCO) nhận ra được. Cây cối và vật cản lạ không có trong mô hình. */
object Kind {
    val ALLOW = listOf("car", "truck", "bus", "motorcycle", "bicycle", "person", "dog", "cat", "horse", "cow", "sheep")
    val GROUPS = listOf("Ô tô", "Xe máy", "Xe đạp", "Người", "Động vật")
    fun of(name: String) = when (name) {
        "car", "truck", "bus" -> 0
        "motorcycle" -> 1
        "bicycle" -> 2
        "person" -> 3
        "dog", "cat", "horse", "cow", "sheep" -> 4
        else -> -1
    }
    fun label(name: String) = when (name) {
        "car" -> "Ô tô"; "truck" -> "Xe tải"; "bus" -> "Xe buýt"; "motorcycle" -> "Xe máy"; "bicycle" -> "Xe đạp"
        "person" -> "Người đi bộ"; "dog" -> "Chó"; "cat" -> "Mèo"; "horse" -> "Ngựa"; "cow" -> "Bò"; "sheep" -> "Cừu"
        else -> name
    }
    fun warn(kind: Int) = when (kind) {
        2 -> "Quá gần xe đạp phía trước!"
        3 -> "Có người phía trước!"
        4 -> "Có động vật phía trước!"
        else -> "Quá gần! Giữ khoảng cách"
    }
}

class Box(val r: RectF, val name: String, val kind: Int, val score: Float, val dist: Float)

/** Kết quả một khung hình. Toạ độ theo điểm ảnh của [bmp]. Mức: 0 không có vật, 1 an toàn, 2 gần, 3 quá gần, 4 chưa đủ tốc độ hoặc chưa có GPS. */
class Frame(
    val bmp: Bitmap, val boxes: List<Box>, val lead: Box?, val dist: Float, val err: Float, val speed: Float, val minD: Float,
    val level: Int, val fps: Float, val inferMs: Float, val hy: Float, val vpx: Float, val laneK: Float, val hf: Float,
    val gpu: Boolean, val hot: Boolean, val startKmh: Int
)
