package vn.sklight.driver

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Số cuộn từng chữ số: khi giá trị đổi, chỉ chữ số nào khác mới cuộn.
 * Ý tưởng chuyển động tham khảo kho Lucian UX/UI Prompt #098 (MIT License (c) 2026 Seokwoooo, https://lucianlabs.dev/uxui-prompt/). Viết lại cho Canvas.
 */
class Roll(private val n: Int) {
    private val pos = FloatArray(n)
    private val alpha = FloatArray(n)

    /** Vẽ [value] (null = trống) bắt đầu tại x, đường chân chữ [base]. Trả về bề rộng đã dùng. */
    fun draw(cv: Canvas, p: Paint, value: Int?, x: Float, base: Float): Float {
        val s = (value?.toString() ?: "").padStart(n, ' ').takeLast(n)
        val colW = p.measureText("0")
        val lineH = p.textSize * 0.92f
        val a0 = p.alpha
        var cx = x
        for (i in 0 until n) {
            val ch = s[i]
            val target = if (ch == ' ') 0f else (ch - '0').toFloat()
            pos[i] += (target - pos[i]) * 0.2f
            alpha[i] += ((if (ch == ' ') 0f else 1f) - alpha[i]) * 0.25f
            if (alpha[i] > 0.03f) {
                val f = pos[i] - floor(pos[i])
                val d0 = floor(pos[i]).toInt().coerceIn(0, 9)
                cv.save()
                cv.clipRect(cx, base - p.textSize * 0.84f, cx + colW, base + p.textSize * 0.08f)
                p.alpha = (a0 * alpha[i]).toInt()
                cv.drawText(d0.toString(), cx, base - f * lineH, p)
                if (f > 0.01f) cv.drawText(((d0 + 1) % 10).toString(), cx, base + (1f - f) * lineH, p)
                cv.restore()
            }
            cx += colW * alpha[i]
        }
        p.alpha = a0
        return cx - x
    }
}

/**
 * Màn Lái xe: hình camera toàn màn, hành lang làn đổi màu theo mức, khung ngắm quanh vật phía trước, thẻ số mét lớn,
 * thước có vạch tối thiểu, thông báo trượt xuống và viền đỏ nháy khi quá gần.
 */
@SuppressLint("ViewConstructor")
class DriveView(c: Context, private val onCalib: () -> Unit, private val onSettings: () -> Unit) : View(c) {
    @Volatile private var f: Frame? = null
    @Volatile var note = "Đang mở camera…"
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val dst = RectF()
    private val aLead = RectF()
    private var hasLead = false
    private val bigRoll = Roll(3)
    private val spdRoll = Roll(3)
    private var barA = 0f
    private var toastA = 0f
    private var dangerA = 0f
    private val btnCalib = RectF()
    private val btnSet = RectF()
    private var pressed = 0

    fun show(fr: Frame) { f = fr; postInvalidateOnAnimation() }

    override fun onDraw(cv: Canvas) {
        val u = height / 720f
        val W = width.toFloat(); val H = height.toFloat()
        val t = (SystemClock.uptimeMillis() % 120000L) / 1000f
        val fr = f
        p.style = Paint.Style.FILL; p.typeface = Typeface.DEFAULT_BOLD; p.textAlign = Paint.Align.LEFT; p.alpha = 255
        if (fr == null) {
            p.color = Ui.WHITE; p.textSize = 22f * u; p.textAlign = Paint.Align.CENTER
            cv.drawText(note, W / 2f, H / 2f, p)
            drawButtons(cv, u, W, H)
            return
        }
        val bw = fr.bmp.width.toFloat(); val bh = fr.bmp.height.toFloat()
        val k = max(W / bw, H / bh) // phủ kín màn hình, cắt bớt phần thừa
        val ox = (W - bw * k) / 2f; val oy = (H - bh * k) / 2f
        dst.set(ox, oy, ox + bw * k, oy + bh * k)
        p.isFilterBitmap = true
        cv.drawBitmap(fr.bmp, null, dst, p)
        fun X(x: Float) = ox + x * k
        fun Y(y: Float) = oy + y * k
        val col = Ui.levelColor(fr.level)
        val lead = fr.lead

        // ---- hành lang làn: từ mép dưới hình tới vật phía trước (không có vật thì tới gần chân trời)
        val nearB = bh - fr.hy
        val farB = if (lead != null) (lead.r.bottom - fr.hy).coerceIn(4f, nearB) else min(8f, nearB)
        if (nearB > farB) {
            fun quad(b0: Float, b1: Float) {
                path.rewind()
                path.moveTo(X(fr.vpx - fr.laneK * b0), Y(fr.hy + b0)); path.lineTo(X(fr.vpx + fr.laneK * b0), Y(fr.hy + b0))
                path.lineTo(X(fr.vpx + fr.laneK * b1), Y(fr.hy + b1)); path.lineTo(X(fr.vpx - fr.laneK * b1), Y(fr.hy + b1)); path.close()
                cv.drawPath(path, p)
            }
            p.color = col; p.alpha = 0x47
            quad(farB, nearB)
            // dải sáng chạy về phía trước, đúng phối cảnh (mỗi 6 m một dải dài 2,5 m)
            p.alpha = 0x55
            val dNear = fr.hf / nearB; val dFar = fr.hf / farB
            var d = dNear + (t * 9f) % 6f
            while (d < dFar) { quad(fr.hf / min(d + 2.5f, dFar), fr.hf / d); d += 6f }
            p.alpha = 255; p.style = Paint.Style.STROKE; p.strokeWidth = 5f * u; p.strokeCap = Paint.Cap.ROUND
            cv.drawLine(X(fr.vpx - fr.laneK * farB), Y(fr.hy + farB), X(fr.vpx - fr.laneK * nearB), Y(fr.hy + nearB), p)
            cv.drawLine(X(fr.vpx + fr.laneK * farB), Y(fr.hy + farB), X(fr.vpx + fr.laneK * nearB), Y(fr.hy + nearB), p)
            p.style = Paint.Style.FILL
        }

        // ---- các vật khác: khung mảnh
        p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f * u; p.color = 0x99FFFFFF.toInt()
        for (b in fr.boxes) if (b !== lead) cv.drawRoundRect(X(b.r.left), Y(b.r.top), X(b.r.right), Y(b.r.bottom), 6f * u, 6f * u, p)

        // ---- vật dẫn đầu: khung ngắm 4 góc, nhãn, tam giác
        if (lead != null) {
            if (!hasLead) aLead.set(lead.r) else {
                aLead.left += (lead.r.left - aLead.left) * 0.3f; aLead.top += (lead.r.top - aLead.top) * 0.3f
                aLead.right += (lead.r.right - aLead.right) * 0.3f; aLead.bottom += (lead.r.bottom - aLead.bottom) * 0.3f
            }
            hasLead = true
            val pulse = 1f + 0.04f * sin(t * 5.2f)
            val cxm = X(aLead.centerX()); val cym = Y(aLead.centerY())
            val hw = (aLead.width() * k / 2f + 8f * u) * pulse; val hh = (aLead.height() * k / 2f + 8f * u) * pulse
            val l = cxm - hw; val r = cxm + hw; val tp = cym - hh; val bt = cym + hh
            val cl = min(hw, hh) * 0.5f
            p.color = col; p.strokeWidth = 4f * u; p.strokeCap = Paint.Cap.ROUND
            path.rewind()
            path.moveTo(l, tp + cl); path.lineTo(l, tp); path.lineTo(l + cl, tp)
            path.moveTo(r - cl, tp); path.lineTo(r, tp); path.lineTo(r, tp + cl)
            path.moveTo(r, bt - cl); path.lineTo(r, bt); path.lineTo(r - cl, bt)
            path.moveTo(l + cl, bt); path.lineTo(l, bt); path.lineTo(l, bt - cl)
            cv.drawPath(path, p)
            p.style = Paint.Style.FILL
            // nhãn: tên vật + số mét
            p.textSize = 15f * u; p.textAlign = Paint.Align.CENTER
            val tag = Kind.label(lead.name) + " · " + fr.dist.roundToInt() + " m"
            val tw = p.measureText(tag) + 22f * u
            cv.drawRoundRect(cxm - tw / 2f, tp - 36f * u, cxm + tw / 2f, tp - 8f * u, 14f * u, 14f * u, p)
            p.color = 0xFF111111.toInt(); cv.drawText(tag, cxm, tp - 17f * u, p)
            // tam giác vàng nhún dưới chân vật
            val ts = max(18f * u, aLead.width() * k * 0.3f); val ty = bt + 4f * u + 3f * u * sin(t * 6f)
            p.color = Ui.AMBER
            path.rewind(); path.moveTo(cxm, ty); path.lineTo(cxm + ts / 2f, ty + ts * 0.75f); path.lineTo(cxm - ts / 2f, ty + ts * 0.75f); path.close()
            cv.drawPath(path, p)
            p.textAlign = Paint.Align.LEFT
        } else hasLead = false
        p.style = Paint.Style.FILL

        // ---- viền đỏ nháy khi quá gần
        dangerA += ((if (fr.level == 3) 1f else 0f) - dangerA) * 0.2f
        if (dangerA > 0.02f) {
            p.style = Paint.Style.STROKE; p.strokeWidth = 24f * u; p.color = 0xFFF0564F.toInt()
            p.alpha = (255 * dangerA * (0.6f + 0.4f * sin(t * 9f))).toInt().coerceIn(0, 255)
            cv.drawRect(0f, 0f, W, H, p)
            p.style = Paint.Style.FILL; p.alpha = 255
        }

        drawCard(cv, fr, u, col)
        drawToast(cv, fr, u, W)
        drawChips(cv, fr, u, W)

        // ---- chữ SK Driver
        p.textAlign = Paint.Align.RIGHT; p.setShadowLayer(6f * u, 0f, 1f * u, 0x99000000.toInt())
        p.textSize = 30f * u; p.color = Ui.PINK
        cv.drawText("Driver", W - 34f * u, 330f * u, p)
        val dw = p.measureText("Driver "); p.color = Ui.WHITE
        cv.drawText("SK", W - 34f * u - dw, 330f * u, p)
        p.textSize = 11f * u; p.letterSpacing = 0.12f
        cv.drawText("HỖ TRỢ THAM KHẢO", W - 34f * u, 346f * u, p)
        p.letterSpacing = 0f; p.clearShadowLayer(); p.textAlign = Paint.Align.LEFT

        drawButtons(cv, u, W, H)
        postInvalidateOnAnimation() // giữ hiệu ứng chạy đều giữa hai khung hình camera
    }

    /** Thẻ nổi góc trái: nhãn trạng thái, số mét lớn cuộn, thước có vạch tối thiểu (ý tưởng: Lucian #163), tốc độ, mức tối thiểu. */
    private fun drawCard(cv: Canvas, fr: Frame, u: Float, col: Int) {
        val x = 30f * u; val y = 26f * u; val w = 400f * u; val h = 292f * u
        p.color = Ui.PANEL; cv.drawRoundRect(x, y, x + w, y + h, 24f * u, 24f * u, p)
        if (fr.level == 2 || fr.level == 3) { p.style = Paint.Style.STROKE; p.strokeWidth = 2f * u; p.color = col; cv.drawRoundRect(x, y, x + w, y + h, 24f * u, 24f * u, p); p.style = Paint.Style.FILL }
        val px = x + 24f * u
        val hasMin = fr.minD > 0f
        val label = when (fr.level) {
            0 -> "KHÔNG CÓ VẬT PHÍA TRƯỚC"; 1 -> "AN TOÀN"; 2 -> "GẦN MỨC TỐI THIỂU"; 3 -> "DƯỚI KHOẢNG CÁCH TỐI THIỂU"
            else -> if (fr.speed < 0f) "CHƯA CÓ TỐC ĐỘ GPS" else "CHƯA ĐỦ TỐC ĐỘ CẢNH BÁO"
        }
        p.color = col; p.textSize = 13f * u; p.letterSpacing = 0.1f
        cv.drawText(label, px, y + 36f * u, p); p.letterSpacing = 0f
        // số mét lớn
        val base = y + 140f * u
        p.textSize = 112f * u
        val d = if (fr.lead != null) fr.dist.roundToInt().coerceIn(0, 999) else null
        var nx = px + bigRoll.draw(cv, p, d, px, base)
        if (d == null) { cv.drawText("—", px, base, p); nx = px + p.measureText("—") }
        p.typeface = Typeface.DEFAULT; p.textSize = 44f * u
        cv.drawText("m", nx + 10f * u, base, p)
        if (d != null) { val mw = p.measureText("m"); p.textSize = 22f * u; cv.drawText("±" + fr.err.roundToInt(), nx + 20f * u + mw, base, p) }
        p.typeface = Typeface.DEFAULT_BOLD
        // thước: 0 .. 2 lần mức tối thiểu, vạch trắng ở giữa là mức tối thiểu
        val scale = if (hasMin) fr.minD * 2f else 60f
        val target = if (d != null) (fr.dist / scale).coerceIn(0f, 1f) else 0f
        barA += (target - barA) * 0.15f
        val by = y + 166f * u; val bw = w - 48f * u
        p.color = 0x2EFFFFFF; cv.drawRoundRect(px, by, px + bw, by + 8f * u, 4f * u, 4f * u, p)
        p.color = col; cv.drawRoundRect(px, by, px + bw * barA, by + 8f * u, 4f * u, 4f * u, p)
        if (hasMin) { p.color = Ui.WHITE; cv.drawRoundRect(px + bw / 2f - 1.5f * u, by - 7f * u, px + bw / 2f + 1.5f * u, by + 15f * u, 2f * u, 2f * u, p) }
        p.textSize = 13f * u; p.color = Ui.MUTED
        cv.drawText("0", px, by + 32f * u, p)
        p.textAlign = Paint.Align.RIGHT; cv.drawText(scale.roundToInt().toString() + " m", px + bw, by + 32f * u, p)
        p.textAlign = Paint.Align.CENTER; p.color = col
        val gap = if (d == null) "Đường trống" else if (!hasMin) "Chưa cảnh báo" else if (fr.dist >= fr.minD) "Dư " + (fr.dist - fr.minD).roundToInt() + " m" else "Thiếu " + (fr.minD - fr.dist).roundToInt() + " m"
        cv.drawText(gap, px + bw / 2f, by + 32f * u, p)
        p.textAlign = Paint.Align.LEFT
        // tốc độ và mức tối thiểu
        val ry = y + 228f * u
        p.textSize = 12f * u; p.color = Ui.MUTED; p.letterSpacing = 0.1f
        cv.drawText("TỐC ĐỘ", px, ry, p); cv.drawText("TỐI THIỂU", px + 150f * u, ry, p); p.letterSpacing = 0f
        p.color = Ui.WHITE; p.textSize = 26f * u
        if (fr.speed < 0f) cv.drawText("—", px, ry + 32f * u, p) else {
            val sw = spdRoll.draw(cv, p, fr.speed.roundToInt().coerceIn(0, 999), px, ry + 32f * u)
            p.typeface = Typeface.DEFAULT; p.textSize = 16f * u; cv.drawText("km/h", px + sw + 6f * u, ry + 32f * u, p); p.typeface = Typeface.DEFAULT_BOLD
        }
        p.textSize = if (hasMin) 26f * u else 18f * u
        cv.drawText(if (hasMin) fr.minD.roundToInt().toString() + " m" else if (fr.speed < 0f) "— chờ GPS" else "— dưới ${fr.startKmh} km/h", px + 150f * u, ry + 32f * u, p)
    }

    /** Thông báo trượt từ trên xuống khi gần hoặc quá gần (ý tưởng: Lucian #070). */
    private fun drawToast(cv: Canvas, fr: Frame, u: Float, W: Float) {
        val show = fr.level == 2 || fr.level == 3
        toastA += ((if (show) 1f else 0f) - toastA) * 0.18f
        if (toastA < 0.02f) return
        val s = if (fr.level == 3) Kind.warn(fr.lead?.kind ?: 0) else if (fr.level == 2) "Sắp chạm mức tối thiểu" else ""
        if (s.isEmpty() && toastA < 0.5f) return
        p.textSize = 17f * u; p.textAlign = Paint.Align.CENTER
        val tw = p.measureText(s) + 48f * u; val h = 52f * u
        val y = -h + (26f * u + h) * toastA
        p.color = if (fr.level == 3) Ui.RED else Ui.AMBER
        cv.drawRoundRect(W / 2f - tw / 2f, y, W / 2f + tw / 2f, y + h, h / 2f, h / 2f, p)
        p.color = 0xFF170806.toInt(); cv.drawText(s, W / 2f, y + h / 2f + 6f * u, p)
        p.textAlign = Paint.Align.LEFT
    }

    /** Góc phải trên: nguồn hình, tốc độ xử lý, trạng thái SK Light (chấm toả sóng, ý tưởng: Lucian #186). */
    private fun drawChips(cv: Canvas, fr: Frame, u: Float, W: Float) {
        p.textSize = 14f * u
        val h = 34f * u; var rx = W - 30f * u; val y = 26f * u
        fun chip(s: String, fg: Int, stroke: Int, yy: Float, right: Float): Float {
            val tw = p.measureText(s) + 28f * u
            p.style = Paint.Style.FILL; p.color = 0xE0141416.toInt(); cv.drawRoundRect(right - tw, yy, right, yy + h, h / 2f, h / 2f, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = 1f * u; p.color = stroke; cv.drawRoundRect(right - tw, yy, right, yy + h, h / 2f, h / 2f, p)
            p.style = Paint.Style.FILL; p.color = fg; cv.drawText(s, right - tw + 14f * u, yy + h / 2f + 5f * u, p)
            return right - tw
        }
        val ai = (if (fr.gpu) "GPU" else "AI") + " · " + fr.fps.roundToInt() + " khung/giây" + (if (fr.hot) " · máy nóng" else "")
        val c = if (fr.hot) Ui.AMBER else Ui.GREEN
        rx = chip(ai, c, c, y, rx) - 8f * u
        chip("Camera điện thoại", Ui.WHITE, 0x44FFFFFF, y, rx)
        val lk = if (!Link.enabled) 0 else if (!Link.installed) 1 else if (Link.sending) 2 else 3
        val l = chip("    SK Light: " + arrayOf("đang tắt", "chưa cài SK Light", "đang gửi tín hiệu", "đang chờ")[lk], Ui.WHITE, if (lk == 2) Ui.GREEN else 0x44FFFFFF, y + h + 10f * u, W - 30f * u)
        val cy = y + h + 10f * u + h / 2f; val cx = l + 20f * u
        val ph = (SystemClock.uptimeMillis() % 1600L) / 1600f
        p.color = if (lk == 2) Ui.GREEN else Ui.MUTED
        if (lk == 2) { p.alpha = (200 * (1f - ph)).toInt(); cv.drawCircle(cx, cy, 5f * u * (1f + 2f * ph), p) }
        p.alpha = 255; cv.drawCircle(cx, cy, 5f * u, p)
    }

    private fun drawButtons(cv: Canvas, u: Float, W: Float, H: Float) {
        p.textSize = 17f * u; p.textAlign = Paint.Align.CENTER
        val h = 56f * u; val y = H - 24f * u - h
        val w1 = p.measureText("Cân chỉnh") + 48f * u; val w2 = p.measureText("Cài đặt") + 48f * u
        btnCalib.set(30f * u, y, 30f * u + w1, y + h); btnSet.set(W - 30f * u - w2, y, W - 30f * u, y + h)
        for ((i, b) in listOf(btnCalib, btnSet).withIndex()) {
            p.style = Paint.Style.FILL; p.color = if (pressed == i + 1) 0xFF3A3A3D.toInt() else 0xE0141416.toInt(); cv.drawRoundRect(b, h / 2f, h / 2f, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = 1f * u; p.color = 0x55FFFFFF; cv.drawRoundRect(b, h / 2f, h / 2f, p)
            p.style = Paint.Style.FILL; p.color = Ui.WHITE; cv.drawText(if (i == 0) "Cân chỉnh" else "Cài đặt", b.centerX(), b.centerY() + 6f * u, p)
        }
        p.textAlign = Paint.Align.LEFT
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val hit = if (btnCalib.contains(e.x, e.y)) 1 else if (btnSet.contains(e.x, e.y)) 2 else 0
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { pressed = hit; invalidate(); return hit != 0 }
            MotionEvent.ACTION_UP -> { val was = pressed; pressed = 0; invalidate(); if (was != 0 && was == hit) { if (was == 1) onCalib() else onSettings() } }
            MotionEvent.ACTION_CANCEL -> { pressed = 0; invalidate() }
        }
        return true
    }
}
