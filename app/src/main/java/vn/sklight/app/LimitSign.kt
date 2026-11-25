package vn.sklight.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.abs

/**
 * Biển giới hạn tốc độ nổi trên ứng dụng khác. Chỉ hiện khi giới hạn đang lấy từ bản đồ mở (OpenStreetMap), tức là không có app bản đồ nào
 * đang hiện biển (không mở bản đồ, hoặc Google Maps không có biển), để thay cho biển của app bản đồ. Trong SK Light thì hình Mô phỏng đã có biển.
 * Số đổi màu đỏ khi xe đang chạy quá giới hạn. Kéo được sang chỗ khác. Cần quyền hiển thị trên ứng dụng khác (cùng quyền với nút nổi).
 */
@SuppressLint("StaticFieldLeak", "ClickableViewAccessibility")
object LimitSign {
    private var view: SignView? = null
    private var lp: WindowManager.LayoutParams? = null
    private var px = -1; private var py = -1          // vị trí người dùng đã kéo tới (giữ trong lần chạy này)
    /** Vùng biển đang chiếm trên màn hình: bộ quét biển của ScreenReader bỏ qua vùng này, kẻo đọc lại chính biển của app. */
    @Volatile var rect: Rect? = null

    private class SignView(c: Context) : View(c) {
        var n = 0; var over = false
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(cv: Canvas) {
            val s = minOf(width, height).toFloat(); val cx = width / 2f; val cy = height / 2f
            p.style = Paint.Style.FILL; p.color = 0xFFFFFFFF.toInt(); cv.drawCircle(cx, cy, s * 0.48f, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = s * 0.11f; p.color = 0xFFE53935.toInt(); cv.drawCircle(cx, cy, s * 0.42f, p)
            p.style = Paint.Style.FILL; p.color = if (over) 0xFFD50000.toInt() else 0xFF111111.toInt()
            p.typeface = Typeface.DEFAULT_BOLD; p.textAlign = Paint.Align.CENTER; p.textSize = s * (if (n >= 100) 0.34f else 0.42f)
            cv.drawText(n.toString(), cx, cy - (p.descent() + p.ascent()) / 2f, p)
        }
    }

    /** Gọi trên luồng chính mỗi khi giới hạn, tốc độ hoặc trạng thái màn hình app đổi. */
    fun sync() {
        try {
            val c = S.app.applicationContext
            val lim = S.speedLimit.intValue
            val want = S.limitOsm.value && Limit.source == 2 && lim > 0 && !S.uiStarted && Settings.canDrawOverlays(c)
            if (!want) { hide(); return }
            val over = S.carSpeed() > lim
            val v = view
            if (v != null) { if (v.n != lim || v.over != over) { v.n = lim; v.over = over; v.invalidate() }; return }
            val wm = c.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val d = c.resources.displayMetrics.density
            val size = (64 * d).toInt()
            val l = WindowManager.LayoutParams(size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT)
            l.gravity = Gravity.TOP or Gravity.START
            l.x = if (px >= 0) px else (16 * d).toInt(); l.y = if (py >= 0) py else (110 * d).toInt()
            val nv = SignView(c).apply { n = lim; this.over = over }
            var dx = 0f; var dy = 0f; var sx = 0; var sy = 0
            nv.setOnTouchListener { _, e ->
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { dx = e.rawX; dy = e.rawY; sx = l.x; sy = l.y }
                    MotionEvent.ACTION_MOVE -> if (abs(e.rawX - dx) > 6 || abs(e.rawY - dy) > 6) {
                        l.x = sx + (e.rawX - dx).toInt(); l.y = sy + (e.rawY - dy).toInt(); px = l.x; py = l.y
                        try { wm.updateViewLayout(nv, l) } catch (_: Exception) {}
                        mark(l, size)
                    }
                }
                true
            }
            wm.addView(nv, l)
            view = nv; lp = l; mark(l, size)
        } catch (_: Exception) { hide() }
    }

    private fun mark(l: WindowManager.LayoutParams, size: Int) { val m = size / 2; rect = Rect(l.x - m, l.y - m, l.x + size + m, l.y + size + m) }

    fun hide() {
        val v = view ?: return
        try { (S.app.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v) } catch (_: Exception) {}
        view = null; lp = null; rect = null
    }
}
