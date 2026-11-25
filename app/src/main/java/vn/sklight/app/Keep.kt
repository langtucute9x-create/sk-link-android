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
    private var lostSince = 0L
    private val loop = object : Runnable {
        override fun run() {
            try { S.autoConnect() } catch (_: Exception) {}
            val now = System.currentTimeMillis()
            if (S.connected.value || S.appVisible || S.bgAlways()) lostSince = 0L
            else {
                if (lostSince == 0L) lostSince = now
                // Không luôn chạy nền (điện thoại): mất kết nối hộp quá 1 phút thì bỏ cuộc, ngừng nối lại và ngừng chạy nền. Mở app thì nối lại.
                if (now - lostSince >= 60000L) { S.ble.disconnect(); stopSelf(); return }
            }
            h.postDelayed(this, if (S.connected.value) 10000L else 5000L)
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
        lostSince = 0L
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
        try { S.init(ctx); if (S.autoBoot.value) S.syncService(ctx, true) } catch (_: Exception) {}
    }
}
