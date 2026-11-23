package vn.sklight.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Nút nổi + màn hình nổi (bảng điều khiển nhỏ) hiện trên ứng dụng khác. Vẽ bằng View thường vì nằm ngoài cửa sổ của app.
 * Kiểu chọn ở Cài đặt giao diện ([S.floatStyle]): 0 kiểu gốc (có chữ), 1 kính mờ, 2 thanh viên thuốc, 3 quạt tròn, 4 khối nổi 3D, 5 cột neon.
 */
@SuppressLint("StaticFieldLeak", "SetTextI18n")
object FloatButton {
    val STYLES = listOf("Kiểu gốc", "Kính mờ", "Thanh viên thuốc", "Quạt tròn", "Khối nổi 3D", "Cột neon")
    private var root: LinearLayout? = null
    private var bubble: ImageView? = null
    private var shownStyle = -1
    private var shifted = 0                      // số px đã kéo cửa sổ lên khi mở bảng (để nút nổi đứng yên), trả lại khi đóng
    private const val PINK = 0xFFFE2CAA.toInt()
    private const val TXT = 0xFF172032.toInt()
    private const val BD = 0xFFDDE3EF.toInt()
    private const val CHIP = 0xFFF4F6FC.toInt()
    private const val DARK = 0xFF15161C.toInt()
    private const val KEY = 0xFF22232C.toInt()
    private val RAINBOW = intArrayOf(0xFFFE2B48.toInt(), 0xFFFED119.toInt(), 0xFF40E01E.toInt(), 0xFF1AF4EA.toInt(), 0xFF4E78FF.toInt(), 0xFF8A01FF.toInt())

    /** Hiện khi công tắc bật, đã có quyền hiển thị trên ứng dụng khác, và app không đang mở trên màn hình. */
    fun sync(ctx: Context) {
        val c = ctx.applicationContext
        val want = S.floatOn.value && !S.appVisible && try { Settings.canDrawOverlays(c) } catch (e: Exception) { false }
        if (!want) { hide(c); return }
        if (root != null && shownStyle != S.floatStyle.intValue) hide(c)
        try { show(c) } catch (e: Exception) { hide(c) }
    }

    fun hide(ctx: Context) {
        val r = root ?: return
        try { (ctx.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(r) } catch (_: Exception) {}
        root = null; bubble = null; shownStyle = -1; shifted = 0
    }

    private fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()
    private fun box(color: Int, stroke: Int, radius: Float, sw: Int = 2) = GradientDrawable().apply { setColor(color); setStroke(sw, stroke); cornerRadius = radius }
    private fun oval(color: Int, stroke: Int = 0, sw: Int = 0) = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color); if (sw > 0) setStroke(sw, stroke) }

    /** Màu đèn đang bật (cho viền nút nổi): xám khi tắt, màu nhấn khi đang chạy hiệu ứng nhiều màu. */
    private fun led(): Int = if (!S.on.value) 0xFF7A7E8E.toInt() else if (S.mode.intValue != 0) PINK
    else (0xFF000000.toInt() or (S.col[if (S.czone.intValue == 0) 0 else 1] and 0xFFFFFF))

    private fun bubbleBg(c: Context, st: Int): Drawable = when (st) {
        1 -> oval(0x40FFFFFF, 0xB3FFFFFF.toInt(), dp(c, 1))
        2 -> oval(DARK, led(), dp(c, 2))
        3 -> LayerDrawable(arrayOf(oval(led(), Color.WHITE, dp(c, 3)), oval(Color.WHITE))).apply { val i = dp(c, 8); setLayerInset(1, i, i, i, i) }
        4 -> LayerDrawable(arrayOf(
            GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFFFF9AD6.toInt(), PINK, 0xFF9C1568.toInt())).apply { shape = GradientDrawable.OVAL },
            oval(Color.WHITE))).apply { val i = dp(c, 7); setLayerInset(1, i, i, i, i) }
        5 -> LayerDrawable(arrayOf(oval(0xFF0B0B0E.toInt(), led(), dp(c, 2)), oval(Color.WHITE))).apply { val i = dp(c, 8); setLayerInset(1, i, i, i, i) }
        else -> oval(Color.WHITE, PINK, dp(c, 2))
    }

    /** Biểu tượng vẽ tay (không có chữ): 0 nguồn, 1 trừ, 2 cộng, 3 micro, 4 đổi bối cảnh. */
    private class Ico(val kind: Int, val color: Int) : Drawable() {
        private val pt = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        override fun draw(cv: Canvas) {
            val b = bounds
            val s = minOf(b.width(), b.height()).toFloat()
            val cx = b.exactCenterX(); val cy = b.exactCenterY()
            val r = s * 0.34f
            pt.color = color; pt.style = Paint.Style.STROKE; pt.strokeWidth = s * 0.10f
            when (kind) {
                0 -> { cv.drawArc(RectF(cx - r, cy - r + s * 0.04f, cx + r, cy + r + s * 0.04f), -60f, 300f, false, pt); cv.drawLine(cx, cy - r - s * 0.04f, cx, cy - s * 0.02f, pt) }
                1 -> cv.drawLine(cx - r, cy, cx + r, cy, pt)
                2 -> { cv.drawLine(cx - r, cy, cx + r, cy, pt); cv.drawLine(cx, cy - r, cx, cy + r, pt) }
                3 -> {
                    val w = s * 0.13f
                    cv.drawRoundRect(RectF(cx - w, cy - r - s * 0.04f, cx + w, cy + s * 0.04f), w, w, pt)
                    cv.drawArc(RectF(cx - s * 0.24f, cy - s * 0.22f, cx + s * 0.24f, cy + s * 0.22f), 0f, 180f, false, pt)
                    cv.drawLine(cx, cy + s * 0.22f, cx, cy + r + s * 0.06f, pt)
                }
                else -> {
                    cv.drawCircle(cx, cy, r, pt)
                    pt.style = Paint.Style.FILL
                    for (a in intArrayOf(-150, -90, -30)) { val g = Math.toRadians(a.toDouble()); cv.drawCircle(cx + (cos(g) * r * 0.52f).toFloat(), cy + (sin(g) * r * 0.52f).toFloat(), s * 0.07f, pt) }
                }
            }
        }
        override fun setAlpha(a: Int) {}
        override fun setColorFilter(cf: ColorFilter?) {}
        @Deprecated("bắt buộc phải có") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    private fun key(c: Context, kind: Int, tint: Int, bg: Drawable, onClick: () -> Unit) = ImageView(c).apply {
        setImageDrawable(Ico(kind, tint)); background = bg
        val pad = dp(c, 10); setPadding(pad, pad, pad, pad)
        setOnClickListener { onClick() }
    }
    private fun appKey(c: Context, bg: Drawable, p: View) = ImageView(c).apply {
        setImageResource(R.drawable.sk_mark); background = bg
        val pad = dp(c, 8); setPadding(pad, pad, pad, pad)
        contentDescription = "Mở SK Light"
        setOnClickListener { openApp(c); p.visibility = View.GONE }
    }
    private fun dot(c: Context, sc: Scene, sel: Boolean, onClick: () -> Unit) = View(c).apply {
        val d = if (sc.rainbow) GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, RAINBOW).apply { shape = GradientDrawable.OVAL }
        else oval(0xFF000000.toInt() or (sc.c1 and 0xFFFFFF))
        if (sel) d.setStroke(dp(c, 3), Color.WHITE) else d.setStroke(dp(c, 1), 0x66FFFFFF)
        background = d
        contentDescription = sc.name
        setOnClickListener { onClick() }
    }
    private fun openApp(c: Context) { try { c.startActivity(Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {} }
    private fun brightBy(d: Int) = S.setBright((S.bri[S.zone.intValue] + d).coerceIn(0, 100))
    private fun nextScene() { val n = Scenes.all().size; if (n > 0) Scenes.apply((S.sceneSel.intValue + 1).mod(n)) }
    private fun askMic(c: Context, p: View) { S.pendingMic = true; openApp(c); p.visibility = View.GONE }
    private fun lpw(w: Int, h: Int, l: Int = 0, t: Int = 0) = LinearLayout.LayoutParams(w, h).apply { leftMargin = l; topMargin = t }
    private fun hrow(c: Context) = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }

    @SuppressLint("ClickableViewAccessibility")
    private fun show(c: Context) {
        if (root != null) return
        val st = S.floatStyle.intValue.coerceIn(0, STYLES.size - 1)
        val wm = c.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val vertical = st == 5
        val all = LinearLayout(c).apply {
            orientation = if (vertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity = if (vertical) Gravity.END else if (st == 3) Gravity.CENTER_VERTICAL else Gravity.TOP
        }
        val p: ViewGroup = if (st == 3) FrameLayout(c) else LinearLayout(c).apply {
            orientation = if (st == 2) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            gravity = if (st == 2) Gravity.CENTER_VERTICAL else if (st == 5) Gravity.CENTER_HORIZONTAL else Gravity.NO_GRAVITY
        }
        val pad = dp(c, 12)
        when (st) {
            1 -> { p.background = box(0xDB141620.toInt(), 0x40FFFFFF, dp(c, 18).toFloat()); p.setPadding(pad, pad, pad, pad) }
            2 -> { p.background = box(DARK, 0xFF2C2E3A.toInt(), dp(c, 32).toFloat()); p.setPadding(dp(c, 10), dp(c, 8), dp(c, 10), dp(c, 8)) }
            3 -> {}
            4 -> { p.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFF272833.toInt(), 0xFF121318.toInt())).apply { cornerRadius = dp(c, 16).toFloat(); setStroke(dp(c, 1), 0xFF3A3D4A.toInt()) }; p.setPadding(pad, pad, pad, pad) }
            5 -> { p.setPadding(0, dp(c, 8), 0, dp(c, 8)) }
            else -> { p.background = box(Color.WHITE, BD, dp(c, 12).toFloat()); p.setPadding(pad, pad, pad, pad) }
        }
        p.visibility = View.GONE
        val b = ImageView(c).apply {
            setImageResource(R.drawable.sk_mark)
            background = bubbleBg(c, st)
            val bp = dp(c, if (st >= 3) 15 else 9); setPadding(bp, bp, bp, bp)
            contentDescription = "Nút nổi SK Light"
        }
        val pw = when (st) { 0 -> dp(c, 280); 1, 4 -> dp(c, 244); 3 -> dp(c, 96); 5 -> dp(c, 56); else -> ViewGroup.LayoutParams.WRAP_CONTENT }
        val ph = if (st == 3) dp(c, 212) else ViewGroup.LayoutParams.WRAP_CONTENT
        all.addView(p, LinearLayout.LayoutParams(pw, ph).apply { if (vertical) bottomMargin = dp(c, 8) else rightMargin = dp(c, if (st == 3) 0 else 10) })
        all.addView(b, LinearLayout.LayoutParams(dp(c, 56), dp(c, 56)))
        val l = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.END; x = dp(c, 12); y = dp(c, 160) }

        fun toggle() {
            if (p.visibility == View.VISIBLE) {
                p.visibility = View.GONE
                l.y += shifted; shifted = 0
            } else {
                fill(c, p, st)
                p.visibility = View.VISIBLE
                // quạt tròn và cột neon làm cửa sổ cao thêm về phía trên nút nổi: kéo cửa sổ lên đúng bằng phần đó để nút nổi đứng yên
                val want = when (st) {
                    3 -> (dp(c, 212) - dp(c, 56)) / 2
                    5 -> { p.measure(View.MeasureSpec.makeMeasureSpec(dp(c, 56), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)); p.measuredHeight + dp(c, 8) }
                    else -> 0
                }
                val ny = (l.y - want).coerceAtLeast(0)
                shifted = l.y - ny; l.y = ny
            }
            try { wm.updateViewLayout(all, l) } catch (_: Exception) {}
        }
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        val slop = dp(c, 8)
        b.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; startX = l.x; startY = l.y; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (moved || abs(dx) > slop || abs(dy) > slop) {
                        moved = true
                        l.x = (startX - dx.toInt()).coerceAtLeast(0); l.y = (startY + dy.toInt()).coerceAtLeast(0)
                        try { wm.updateViewLayout(all, l) } catch (_: Exception) {}
                    }
                }
                MotionEvent.ACTION_UP -> if (!moved) toggle()
            }
            true
        }
        try { wm.addView(all, l) } catch (e: Exception) { return }
        root = all; bubble = b; shownStyle = st; shifted = 0
    }

    /** Dựng lại nội dung bảng theo trạng thái hiện tại và kiểu đang chọn. */
    private fun fill(c: Context, p: ViewGroup, st: Int) {
        p.removeAllViews()
        bubble?.background = bubbleBg(c, st)
        fun again() = fill(c, p, st)
        val vi = L.cur.intValue == 0          // nút mic chỉ có khi dùng tiếng Việt
        val scenes = Scenes.all()
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        val gap = dp(c, 8)
        when (st) {
            1, 4 -> {
                val k = dp(c, 44)
                fun kb(): Drawable = if (st == 1) oval(0x29FFFFFF) else GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFF34353F.toInt(), 0xFF1B1C22.toInt())).apply { cornerRadius = dp(c, 11).toFloat(); setStroke(dp(c, 1), 0xFF454857.toInt()) }
                val r1 = hrow(c)
                r1.addView(key(c, 0, if (S.on.value) led() else 0xFF9AA0B0.toInt(), kb()) { S.setPower(!S.on.value); again() }, lpw(k, k))
                r1.addView(key(c, 1, Color.WHITE, kb()) { brightBy(-10); again() }, lpw(k, k, gap))
                r1.addView(TextView(c).apply { text = "${S.bri[S.zone.intValue]}%"; gravity = Gravity.CENTER; textSize = 17f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD }, LinearLayout.LayoutParams(0, wrap, 1f))
                r1.addView(key(c, 2, Color.WHITE, kb()) { brightBy(10); again() }, lpw(k, k))
                p.addView(r1)
                if (scenes.isNotEmpty()) {
                    val r2 = hrow(c)
                    scenes.take(6).forEachIndexed { i, sc -> r2.addView(dot(c, sc, S.sceneSel.intValue == i) { Scenes.apply(i); again() }, lpw(dp(c, 30), dp(c, 30), if (i == 0) 0 else dp(c, 8))) }
                    p.addView(r2, LinearLayout.LayoutParams(wrap, wrap).apply { topMargin = dp(c, 12) })
                    val name = scenes.getOrNull(S.sceneSel.intValue)?.name ?: ""
                    if (name.isNotEmpty()) p.addView(TextView(c).apply { text = L.tr(name); textSize = 12f; setTextColor(0xFFC3C9D6.toInt()) }, LinearLayout.LayoutParams(wrap, wrap).apply { topMargin = dp(c, 6) })
                }
                val r3 = hrow(c)
                if (vi) r3.addView(key(c, 3, PINK, kb()) { askMic(c, p) }, lpw(k, k))
                r3.addView(View(c), LinearLayout.LayoutParams(0, 1, 1f))
                r3.addView(appKey(c, kb(), p), lpw(k, k))
                p.addView(r3, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, wrap).apply { topMargin = dp(c, 10) })
            }
            2 -> {
                val k = dp(c, 46)
                p.addView(key(c, 0, if (S.on.value) led() else 0xFF9AA0B0.toInt(), oval(KEY)) { S.setPower(!S.on.value); again() }, lpw(k, k))
                p.addView(key(c, 1, Color.WHITE, oval(KEY)) { brightBy(-10); again() }, lpw(k, k, gap))
                p.addView(key(c, 2, Color.WHITE, oval(KEY)) { brightBy(10); again() }, lpw(k, k, gap))
                scenes.take(4).forEachIndexed { i, sc -> p.addView(dot(c, sc, S.sceneSel.intValue == i) { Scenes.apply(i); again() }, lpw(dp(c, 30), dp(c, 30), dp(c, 10))) }
                if (vi) p.addView(key(c, 3, PINK, oval(KEY)) { askMic(c, p) }, lpw(k, k, dp(c, 10)))
                p.addView(appKey(c, oval(KEY), p), lpw(k, k, gap))
            }
            3 -> {
                // 5 nút xòe thành cung bên trái nút nổi; tâm cung = tâm nút nổi (cách mép phải bảng 28dp, giữa chiều cao)
                val k = dp(c, 44); val rad = dp(c, 100)
                val items = ArrayList<View>()
                if (vi) items.add(key(c, 3, PINK, oval(DARK, 0xFF3A3D4A.toInt(), dp(c, 1))) { askMic(c, p) })
                items.add(key(c, 2, Color.WHITE, oval(DARK, 0xFF3A3D4A.toInt(), dp(c, 1))) { brightBy(10); again() })
                items.add(key(c, 0, if (S.on.value) led() else 0xFF9AA0B0.toInt(), oval(DARK, 0xFF3A3D4A.toInt(), dp(c, 1))) { S.setPower(!S.on.value); again() })
                items.add(key(c, 1, Color.WHITE, oval(DARK, 0xFF3A3D4A.toInt(), dp(c, 1))) { brightBy(-10); again() })
                items.add(key(c, 4, Color.WHITE, oval(DARK, 0xFF3A3D4A.toInt(), dp(c, 1))) { nextScene(); again() })
                val n = items.size
                val cxp = dp(c, 96) + dp(c, 28); val cyp = dp(c, 212) / 2
                items.forEachIndexed { i, v ->
                    val g = Math.toRadians(125.0 + (if (n > 1) 110.0 * i / (n - 1) else 55.0))
                    val x = cxp + (cos(g) * rad).toInt() - k / 2
                    val y = cyp - (sin(g) * rad).toInt() - k / 2
                    p.addView(v, FrameLayout.LayoutParams(k, k).apply { leftMargin = x.coerceAtLeast(0); topMargin = y.coerceAtLeast(0) })
                }
            }
            5 -> {
                val k = dp(c, 42)
                p.background = box(0xFF0B0B0E.toInt(), led(), dp(c, 28).toFloat(), dp(c, 2))
                fun kb() = oval(0xFF1E1F26.toInt())
                p.addView(appKey(c, kb(), p), lpw(k, k))
                if (vi) p.addView(key(c, 3, PINK, kb()) { askMic(c, p) }, lpw(k, k, 0, gap))
                p.addView(key(c, 4, Color.WHITE, kb()) { nextScene(); again() }, lpw(k, k, 0, gap))
                p.addView(key(c, 2, Color.WHITE, kb()) { brightBy(10); again() }, lpw(k, k, 0, gap))
                p.addView(key(c, 1, Color.WHITE, kb()) { brightBy(-10); again() }, lpw(k, k, 0, gap))
                p.addView(key(c, 0, if (S.on.value) led() else 0xFF9AA0B0.toInt(), kb()) { S.setPower(!S.on.value); again() }, lpw(k, k, 0, gap))
            }
            else -> fillClassic(c, p as LinearLayout)
        }
    }

    private fun btn(c: Context, text: String, sel: Boolean = false, onClick: () -> Unit) = TextView(c).apply {
        this.text = text
        gravity = Gravity.CENTER
        textSize = 13f
        setTextColor(TXT)
        typeface = if (sel) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        minHeight = dp(c, 46)
        val pad = dp(c, 6); setPadding(pad, 0, pad, 0)
        background = box(if (sel) 0xFFFFE9F6.toInt() else CHIP, if (sel) PINK else BD, dp(c, 8).toFloat())
        setOnClickListener { onClick() }
    }

    /** Kiểu gốc: thẻ trắng có chữ (giữ nguyên như các bản trước). */
    private fun fillClassic(c: Context, p: LinearLayout) {
        val gap = dp(c, 8)
        fun again() = fill(c, p, 0)
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        val row = hrow(c)
        row.addView(btn(c, L.tr(if (S.on.value) "Tắt đèn" else "Bật đèn")) { S.setPower(!S.on.value); again() }, LinearLayout.LayoutParams(dp(c, 76), wrap))
        row.addView(btn(c, "−") { brightBy(-10); again() }, LinearLayout.LayoutParams(dp(c, 46), wrap).apply { leftMargin = gap })
        row.addView(TextView(c).apply { text = "${S.bri[S.zone.intValue]}%"; gravity = Gravity.CENTER; textSize = 16f; setTextColor(0xFFC4127F.toInt()); typeface = Typeface.DEFAULT_BOLD }, LinearLayout.LayoutParams(0, wrap, 1f))
        row.addView(btn(c, "+") { brightBy(10); again() }, LinearLayout.LayoutParams(dp(c, 46), wrap))
        p.addView(row)
        Scenes.all().withIndex().chunked(2).forEach { pair ->
            val r = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEachIndexed { k, iv ->
                r.addView(btn(c, L.tr(iv.value.name), S.sceneSel.intValue == iv.index) { Scenes.apply(iv.index); again() },
                    LinearLayout.LayoutParams(0, wrap, 1f).apply { if (k == 1) leftMargin = gap })
            }
            if (pair.size == 1) r.addView(View(c), LinearLayout.LayoutParams(0, 1, 1f).apply { leftMargin = gap })
            p.addView(r, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, wrap).apply { topMargin = gap })
        }
        p.addView(TextView(c).apply {
            text = L.tr("Mở SK Light"); gravity = Gravity.CENTER; textSize = 13f; setTextColor(0xFFC4127F.toInt()); typeface = Typeface.DEFAULT_BOLD; minHeight = dp(c, 44)
            setOnClickListener { openApp(c); p.visibility = View.GONE }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, wrap).apply { topMargin = dp(c, 4) })
    }
}
