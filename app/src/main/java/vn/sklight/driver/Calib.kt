package vn.sklight.driver

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** Hình camera thu vừa khung, vẽ vạch chân trời, hai mép làn, điểm kéo và số đo của vật phía trước. */
@SuppressLint("ViewConstructor")
class CalibView(c: Context, private val cfg: Cfg, private val onDrag: () -> Unit) : View(c) {
    @Volatile var f: Frame? = null
    var dragOn = false
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dst = RectF()
    private var k = 1f
    private var lx = 0f
    private var ly = 0f

    override fun onDraw(cv: Canvas) {
        val fr = f
        val dm = resources.displayMetrics.density
        p.style = Paint.Style.FILL; p.typeface = Typeface.DEFAULT_BOLD
        if (fr == null) { p.color = Ui.WHITE; p.textSize = 16f * dm; p.textAlign = Paint.Align.CENTER; cv.drawText("Đang mở camera…", width / 2f, height / 2f, p); return }
        val bw = fr.bmp.width.toFloat(); val bh = fr.bmp.height.toFloat()
        k = min(width / bw, height / bh)
        val ox = (width - bw * k) / 2f; val oy = (height - bh * k) / 2f
        dst.set(ox, oy, ox + bw * k, oy + bh * k)
        p.isFilterBitmap = true; cv.drawBitmap(fr.bmp, null, dst, p)
        // dùng thông số đang chỉnh (không chờ khung hình sau) để kéo thấy ngay
        val hy = cfg.horizon * bh; val vpx = bw / 2f + cfg.vx * bw; val lk = cfg.laneK
        fun X(x: Float) = ox + x * k
        fun Y(y: Float) = oy + y * k
        cv.save(); cv.clipRect(dst)
        // hai mép làn
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND; p.strokeWidth = 4f * dm; p.color = Ui.GREEN
        val nb = bh - hy
        if (nb > 6f) for (s in floatArrayOf(-1f, 1f)) cv.drawLine(X(vpx + s * lk * 6f), Y(hy + 6f), X(vpx + s * lk * nb), Y(hy + nb), p)
        // vạch chân trời
        p.strokeWidth = 2f * dm; p.color = 0xFFFFD60A.toInt()
        cv.drawLine(dst.left, Y(hy), dst.right, Y(hy), p)
        // vật phía trước và số đo
        val l = fr.lead
        if (l != null) {
            p.strokeWidth = 3f * dm; p.color = Ui.levelColor(fr.level)
            cv.drawRoundRect(X(l.r.left), Y(l.r.top), X(l.r.right), Y(l.r.bottom), 6f * dm, 6f * dm, p)
            p.style = Paint.Style.FILL; p.textSize = 18f * dm; p.textAlign = Paint.Align.CENTER
            val s = "Đo được " + "%.1f".format(fr.dist).replace('.', ',') + " m"
            val tw = p.measureText(s) + 20f * dm; val cx = X(l.r.centerX()); val ty = (Y(l.r.top) - 10f * dm).coerceAtLeast(dst.top + 34f * dm)
            p.color = 0xE618181A.toInt(); cv.drawRoundRect(cx - tw / 2f, ty - 26f * dm, cx + tw / 2f, ty + 6f * dm, 16f * dm, 16f * dm, p)
            p.color = Ui.GREEN; cv.drawText(s, cx, ty - 4f * dm, p)
        }
        // điểm kéo ở chỗ hai mép gặp nhau
        if (dragOn) {
            p.style = Paint.Style.FILL; p.color = 0x77000000; cv.drawCircle(X(vpx), Y(hy), 20f * dm, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = 3f * dm; p.color = 0xFFFFD60A.toInt(); cv.drawCircle(X(vpx), Y(hy), 20f * dm, p)
            p.style = Paint.Style.FILL; cv.drawCircle(X(vpx), Y(hy), 5f * dm, p)
        }
        cv.restore()
        p.style = Paint.Style.FILL
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val fr = f
        if (!dragOn || fr == null) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { lx = e.x; ly = e.y }
            MotionEvent.ACTION_MOVE -> {
                // kéo ở đâu cũng được: điểm gặp nhau dịch theo ngón tay
                cfg.vx = (cfg.vx + (e.x - lx) / (fr.bmp.width * k)).coerceIn(-0.2f, 0.2f)
                cfg.horizon = (cfg.horizon + (e.y - ly) / (fr.bmp.height * k)).coerceIn(0.2f, 0.8f)
                lx = e.x; ly = e.y; onDrag(); invalidate()
            }
        }
        return true
    }
}

/**
 * Màn Cân chỉnh 5 bước: nguồn hình, tốc độ xử lý, căn làn và chân trời, độ cao camera, thử với xe đỗ.
 * Bố cục các bước tham khảo kho Lucian UX/UI Prompt #042 (MIT License (c) 2026 Seokwoooo, https://lucianlabs.dev/uxui-prompt/). Viết lại bằng View.
 */
@SuppressLint("ViewConstructor")
class CalibScreen(private val act: MainActivity, private val onExit: () -> Unit) : LinearLayout(act) {
    private val cfg = act.cfg
    private var n = 3
    private val view = CalibView(act, cfg) { syncBars() }
    private val dots = ArrayList<TextView>()
    private val stepTxt = Ui.text(act, "", 14f, Ui.MUTED)
    private val title = Ui.text(act, "", 26f, Ui.WHITE, true)
    private val desc = Ui.text(act, "", 15f, 0xFFC9CCD1.toInt())
    private val body = FrameLayout(act)
    private val pages = ArrayList<View>()
    private val live2 = Ui.text(act, "Đang đo…", 20f, Ui.GREEN, true)
    private val live2b = Ui.text(act, "", 14f, Ui.MUTED)
    private val lvl = Ui.text(act, "", 14f, Ui.MUTED)
    private val live5 = Ui.text(act, "Chưa thấy xe phía trước", 20f, Ui.GREEN, true)
    private val camTxt5 = Ui.text(act, "", 18f, Ui.WHITE, true)
    private val back = Ui.pill(act, "Quay lại") { if (n > 1) { n--; render() } }
    private val next = Ui.pill(act, "Tiếp tục", true) { if (n < 5) { n++; render() } else onExit() }
    private var barH: SeekBar? = null
    private var barV: SeekBar? = null
    private var barL: SeekBar? = null
    private var barC: SeekBar? = null
    private var lblH: TextView? = null
    private var lblV: TextView? = null
    private var lblL: TextView? = null
    private var lblC: TextView? = null
    private var syncing = false

    private val T = listOf(
        "Chọn nguồn hình" to "Bản này dùng camera sau của điện thoại, gắn ngang trên kính lái. Camera USB và camera hành trình Wi-Fi sẽ có ở bản sau.",
        "Kiểm tra tốc độ xử lý" to "Xử lý càng nhanh, số mét cập nhật càng kịp. Có thể thử GPU; máy không hợp thì app tự quay về CPU.",
        "Căn làn và chân trời" to "Kéo trên hình (hoặc ba thanh dưới) đến khi hai mép xanh trùng hai vạch làn. Chỗ hai mép gặp nhau là chân trời.",
        "Nhập độ cao camera" to "Đo từ mặt đường lên ống kính. Xe con thường 110 đến 140 cm, xe gầm cao hơn.",
        "Thử với xe đỗ phía trước" to "Đỗ sau một xe cách đúng 10 m. Bấm cộng trừ đến khi số đo hiện 10,0 m. Thử thêm ở 30 m: nếu lệch nhiều, quay lại bước 3 chỉnh chân trời."
    )

    init {
        orientation = HORIZONTAL
        setBackgroundColor(Ui.BLACK)
        val d = { v: Float -> Ui.dp(act, v) }
        val panel = LinearLayout(act).apply {
            orientation = VERTICAL
            background = Ui.bg(0xFF0A0A0B.toInt(), d(24f).toFloat(), Ui.LINE, d(1f))
            setPadding(d(22f), d(16f), d(22f), d(14f))
        }
        // đầu bảng: tên app + bước
        val head = LinearLayout(act).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(Ui.text(act, "SK ", 15f, Ui.WHITE, true)); head.addView(Ui.text(act, "Driver", 15f, Ui.PINK, true))
        head.addView(stepTxt, LayoutParams(-2, -2).apply { leftMargin = d(12f) })
        panel.addView(head)
        // 5 vòng tròn bước
        val row = LinearLayout(act).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        for (i in 1..5) {
            val t = Ui.text(act, i.toString(), 15f, Ui.WHITE, true).apply { gravity = Gravity.CENTER; setOnClickListener { n = i; render() } }
            dots.add(t); row.addView(t, LayoutParams(d(40f), d(40f)).apply { rightMargin = d(10f) })
        }
        panel.addView(row, LayoutParams(-1, -2).apply { topMargin = d(10f) })
        panel.addView(title, LayoutParams(-1, -2).apply { topMargin = d(10f) })
        panel.addView(desc, LayoutParams(-1, -2).apply { topMargin = d(4f) })
        panel.addView(body, LayoutParams(-1, 0, 1f).apply { topMargin = d(8f) })
        // 5 trang nội dung
        pages.add(col(Ui.row(act, "Đang dùng", "Camera sau của điện thoại, 640 × 480", null), Ui.row(act, "Camera USB, camera hành trình Wi-Fi", "Cần biết loại camera và màn hình trên xe", Ui.soon(act))))
        pages.add(col(live2, live2b, Ui.row(act, "Thử dùng GPU", "Thử nghiệm. Lỗi thì tự quay về CPU", Ui.switch(act, cfg.gpu) { cfg.gpu = it; act.requestRebuild() })))
        pages.add(col(
            bar("Chân trời", 20, 80, { (cfg.horizon * 100).roundToInt() }, { cfg.horizon = it / 100f }, { "$it%" }, 0),
            bar("Hướng thẳng", -200, 200, { (cfg.vx * 1000).roundToInt() }, { cfg.vx = it / 1000f }, { (if (it > 0) "+" else "") + it / 10 + "%" }, 1),
            bar("Bề rộng làn", 50, 300, { (cfg.laneK * 100).roundToInt() }, { cfg.laneK = it / 100f }, { "$it" }, 2),
            lvl
        ))
        pages.add(col(bar("Độ cao camera", 80, 200, { (cfg.camH * 100).roundToInt() }, { cfg.camH = it / 100f }, { "$it cm" }, 3)))
        val pm = LinearLayout(act).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        pm.addView(Ui.text(act, "Độ cao camera", 15f, Ui.WHITE, true), LayoutParams(0, -2, 1f))
        pm.addView(Ui.pill(act, "−") { cfg.camH = (cfg.camH - 0.01f).coerceAtLeast(0.8f); syncBars() }, LayoutParams(d(52f), d(48f)))
        pm.addView(camTxt5, LayoutParams(-2, -2).apply { leftMargin = d(12f); rightMargin = d(12f) })
        pm.addView(Ui.pill(act, "+") { cfg.camH = (cfg.camH + 0.01f).coerceAtMost(2f); syncBars() }, LayoutParams(d(52f), d(48f)))
        pages.add(col(live5, pm))
        for (pg in pages) body.addView(pg, FrameLayout.LayoutParams(-1, -2))
        // hàng nút dưới
        val foot = LinearLayout(act).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        foot.addView(Ui.pill(act, "Lái xe") { onExit() }, LayoutParams(-2, d(52f)))
        foot.addView(back, LayoutParams(-2, d(52f)).apply { leftMargin = d(10f) })
        foot.addView(View(act), LayoutParams(0, 1, 1f))
        foot.addView(next, LayoutParams(-2, d(52f)))
        panel.addView(foot, LayoutParams(-1, -2).apply { topMargin = d(8f) })
        addView(panel, LayoutParams(0, -1, 44f).apply { setMargins(d(14f), d(14f), d(8f), d(14f)) })
        addView(view, LayoutParams(0, -1, 56f).apply { setMargins(0, d(14f), d(14f), d(14f)) })
        render()
    }

    private fun col(vararg vs: View) = LinearLayout(act).apply {
        orientation = VERTICAL
        for (v in vs) addView(v, LayoutParams(-1, -2).apply { topMargin = Ui.dp(act, 4f) })
    }

    /** Một thanh kéo có nhãn và số bên phải. which: 0 chân trời, 1 hướng, 2 bề rộng làn, 3 độ cao camera. */
    private fun bar(name: String, mn: Int, mx: Int, get: () -> Int, set: (Int) -> Unit, fmt: (Int) -> String, which: Int): View {
        val row = LinearLayout(act).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val lbl = Ui.text(act, fmt(get()), 15f, 0xFFFFD60A.toInt(), true).apply { gravity = Gravity.END }
        val sb = SeekBar(act).apply {
            min = mn; max = mx; progress = get().coerceIn(mn, mx)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, v: Int, user: Boolean) { lbl.text = fmt(v); if (user && !syncing) { set(v); view.invalidate() } }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) { cfg.save() }
            })
        }
        when (which) { 0 -> { barH = sb; lblH = lbl }; 1 -> { barV = sb; lblV = lbl }; 2 -> { barL = sb; lblL = lbl }; else -> { barC = sb; lblC = lbl } }
        row.addView(Ui.text(act, name, 14f, Ui.WHITE, true), LayoutParams(Ui.dp(act, 104f), -2))
        row.addView(sb, LayoutParams(0, Ui.dp(act, 44f), 1f))
        row.addView(lbl, LayoutParams(Ui.dp(act, 64f), -2))
        return row
    }

    private fun syncBars() {
        syncing = true
        barH?.progress = (cfg.horizon * 100).roundToInt(); barV?.progress = (cfg.vx * 1000).roundToInt()
        barL?.progress = (cfg.laneK * 100).roundToInt(); barC?.progress = (cfg.camH * 100).roundToInt()
        camTxt5.text = "${(cfg.camH * 100).roundToInt()} cm"
        syncing = false
    }

    /** Gọi khi mở màn: cập nhật thanh kéo theo thông số đã lưu. */
    fun sync() { syncBars(); render() }

    private fun render() {
        stepTxt.text = "Cân chỉnh · bước $n / 5"
        title.text = T[n - 1].first; desc.text = T[n - 1].second
        val r = Ui.dp(act, 20f).toFloat()
        dots.forEachIndexed { i, t ->
            val s = i + 1
            t.text = if (s < n) "✓" else s.toString()
            t.background = if (s < n) Ui.bg(Ui.GREEN, r) else if (s == n) Ui.bg(Ui.WHITE, r) else Ui.bg(0xFF0A0A0B.toInt(), r, 0x66FFFFFF, Ui.dp(act, 2f))
            t.setTextColor(if (s <= n) Ui.BLACK else Ui.MUTED)
        }
        pages.forEachIndexed { i, v -> v.visibility = if (i == n - 1) View.VISIBLE else View.GONE }
        view.dragOn = n == 3
        back.alpha = if (n > 1) 1f else 0.4f
        next.text = if (n == 5) "Xong" else "Tiếp tục"
        Ui.stylePill(next, true, if (n == 5) Ui.GREEN else Ui.WHITE)
        syncBars(); view.invalidate()
    }

    /** Nhận khung hình mới từ luồng phân tích. */
    fun show(fr: Frame) {
        view.f = fr; view.postInvalidate()
        post {
            when (n) {
                2 -> {
                    live2.text = "${fr.inferMs.roundToInt()} ms mỗi lần · ${fr.fps.roundToInt()} khung/giây"
                    live2b.text = "Đang chạy trên " + (if (fr.gpu) "GPU" else "CPU") + (if (fr.hot) " · máy đang nóng, đã giảm xử lý" else "")
                }
                3 -> {
                    val a = act.roll
                    lvl.text = "Thăng bằng: " + (if (abs(a) < 1f) "máy đang cân" else "nghiêng " + "%.1f".format(abs(a)).replace('.', ',') + "°, chỉnh lại giá đỡ")
                    lvl.setTextColor(if (abs(a) < 1f) Ui.GREEN else Ui.AMBER)
                }
                5 -> live5.text = if (fr.lead != null) "Đo được " + "%.1f".format(fr.dist).replace('.', ',') + " m" else "Chưa thấy xe phía trước"
            }
        }
    }
}
