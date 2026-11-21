package vn.sklight.app

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/** Dịch vụ chạy nền: giữ kết nối với hộp đèn (tự nối lại), và giữ nút nổi trên màn hình. */
class KeepService : Service() {
    private val h = Handler(Looper.getMainLooper())
    private val loop = object : Runnable {
        override fun run() {
            try { S.autoConnect() } catch (_: Exception) {}
            h.postDelayed(this, 10000)
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        S.init(this)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("keep", L.tr("SK Light chạy nền"), NotificationManager.IMPORTANCE_LOW))
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, "keep")
            .setContentTitle(L.tr("SK Light đang giữ kết nối với hộp đèn"))
            .setContentText(L.tr("Chạm để mở app"))
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pi).setOngoing(true).build()
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            else startForeground(1, n)
        } catch (e: Exception) { stopSelf(); return START_NOT_STICKY }
        h.removeCallbacks(loop); h.post(loop)
        FloatButton.sync(this)
        return START_STICKY
    }

    override fun onDestroy() {
        h.removeCallbacks(loop)
        FloatButton.hide(this)
        super.onDestroy()
    }
}

/** Máy khởi động xong: tự chạy dịch vụ nền để nối lại hộp đèn đã chọn. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent?) {
        try { S.init(ctx); if (S.autoBoot.value) S.syncService(ctx) } catch (_: Exception) {}
    }
}

/** Nút nổi SK trên mọi ứng dụng: chạm để mở bảng nhỏ (bật tắt, độ sáng, bối cảnh), kéo để dời chỗ. */
@SuppressLint("StaticFieldLeak", "ClickableViewAccessibility", "SetTextI18n")
object FloatButton {
    private var root: LinearLayout? = null
    private var panel: LinearLayout? = null
    private var lp: WindowManager.LayoutParams? = null
    private const val PINK = 0xFFFE2CAA.toInt()
    private const val TXT = 0xFF172032.toInt()
    private const val BD = 0xFFDDE3EF.toInt()
    private const val CHIP = 0xFFF4F6FC.toInt()

    /** Hiện khi công tắc bật, đã có quyền hiển thị trên ứng dụng khác, và app không đang mở trên màn hình. */
    fun sync(ctx: Context) {
        val c = ctx.applicationContext
        val want = S.floatOn.value && !S.appVisible && try { Settings.canDrawOverlays(c) } catch (e: Exception) { false }
        if (want) show(c) else hide(c)
    }

    private fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()

    private fun box(color: Int, stroke: Int, radius: Float) = GradientDrawable().apply { setColor(color); setStroke(2, stroke); cornerRadius = radius }

    private fun show(c: Context) {
        if (root != null) return
        val wm = c.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val all = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP }
        val p = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            background = box(Color.WHITE, BD, dp(c, 12).toFloat())
            val pad = dp(c, 12); setPadding(pad, pad, pad, pad)
            visibility = View.GONE
        }
        val bubble = ImageView(c).apply {
            setImageResource(R.drawable.sk_mark)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE); setStroke(dp(c, 2), PINK) }
            val pad = dp(c, 9); setPadding(pad, pad, pad, pad)
            contentDescription = "Nút nổi SK Light"
        }
        all.addView(p, LinearLayout.LayoutParams(dp(c, 280), ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(c, 10) })
        all.addView(bubble, LinearLayout.LayoutParams(dp(c, 56), dp(c, 56)))
        val l = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.END; x = dp(c, 12); y = dp(c, 160) }

        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        val slop = dp(c, 8)
        bubble.setOnTouchListener { _, e ->
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
                MotionEvent.ACTION_UP -> if (!moved) {
                    if (p.visibility == View.VISIBLE) p.visibility = View.GONE else { fill(c, p); p.visibility = View.VISIBLE }
                }
            }
            true
        }
        try { wm.addView(all, l) } catch (e: Exception) { return }
        root = all; panel = p; lp = l
    }

    fun hide(ctx: Context) {
        val r = root ?: return
        try { (ctx.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(r) } catch (_: Exception) {}
        root = null; panel = null; lp = null
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

    /** Dựng lại nội dung bảng theo trạng thái hiện tại. */
    private fun fill(c: Context, p: LinearLayout) {
        p.removeAllViews()
        val gap = dp(c, 8)
        fun again() = fill(c, p)
        val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(btn(c, L.tr(if (S.on.value) "Tắt đèn" else "Bật đèn")) { S.setPower(!S.on.value); again() }, LinearLayout.LayoutParams(dp(c, 76), ViewGroup.LayoutParams.WRAP_CONTENT))
        row.addView(btn(c, "−") { S.setBright((S.bri[S.zone.intValue] - 10).coerceIn(0, 100)); again() }, LinearLayout.LayoutParams(dp(c, 46), ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = gap })
        row.addView(TextView(c).apply { text = "${S.bri[S.zone.intValue]}%"; gravity = Gravity.CENTER; textSize = 16f; setTextColor(0xFFC4127F.toInt()); typeface = Typeface.DEFAULT_BOLD }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(btn(c, "+") { S.setBright((S.bri[S.zone.intValue] + 10).coerceIn(0, 100)); again() }, LinearLayout.LayoutParams(dp(c, 46), ViewGroup.LayoutParams.WRAP_CONTENT))
        p.addView(row)
        Scenes.all().withIndex().chunked(2).forEach { pair ->
            val r = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEachIndexed { k, iv ->
                r.addView(btn(c, L.tr(iv.value.name), S.sceneSel.intValue == iv.index) { Scenes.apply(iv.index); again() },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { if (k == 1) leftMargin = gap })
            }
            if (pair.size == 1) r.addView(View(c), LinearLayout.LayoutParams(0, 1, 1f).apply { leftMargin = gap })
            p.addView(r, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = gap })
        }
        p.addView(TextView(c).apply {
            text = L.tr("Mở SK Light"); gravity = Gravity.CENTER; textSize = 13f; setTextColor(0xFFC4127F.toInt()); typeface = Typeface.DEFAULT_BOLD; minHeight = dp(c, 44)
            setOnClickListener {
                try { c.startActivity(Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
                p.visibility = View.GONE
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(c, 4) })
    }
}
