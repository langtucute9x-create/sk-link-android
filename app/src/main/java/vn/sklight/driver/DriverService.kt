package vn.sklight.driver

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService

/**
 * Dịch vụ nền: giữ nguồn hình và bộ xử lý ([Engine]) chạy khi SK Driver không ở phía trước, để SK Light dẫn đường mà vẫn nhận tín hiệu cảnh báo.
 * Có thông báo cố định với nút Dừng. Mở lại SK Driver thì màn hình chỉ gắn vào để xem.
 */
class DriverService : LifecycleService() {
    companion object {
        const val STOP = "vn.sklight.driver.STOP"
        const val CH = "driver_run"
        @Volatile var running = false

        /** Bắt đầu hoặc chạy lại nguồn hình theo cài đặt hiện tại (đổi nguồn, đổi địa chỉ). */
        fun start(c: Context) { try { ContextCompat.startForegroundService(c, Intent(c, DriverService::class.java)) } catch (_: Throwable) {} }
        fun stop(c: Context) { try { c.startService(Intent(c, DriverService::class.java).setAction(STOP)) } catch (_: Throwable) {} }
    }

    private var src: Src? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Engine.init(this)
        if (intent?.action == STOP) {
            // thông báo vẫn phải gọi startForeground nếu dịch vụ vừa được khởi động bằng startForegroundService; ở đây chỉ dừng
            closeSrc(); Engine.stopGps(); Link.stop(applicationContext, Engine.cfg.link)
            running = false
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
            return START_NOT_STICKY
        }
        goForeground()
        running = true
        closeSrc()
        Engine.reset("")
        val cfg = Engine.cfg
        val s: Src? = when (cfg.src) {
            0 -> PhoneSrc(this)
            1 -> MjpegSrc(cfg.url1)
            2 -> RtspSrc(applicationContext, cfg.url2)
            else -> null
        }
        if (s == null) { Engine.note("Nguồn hình này chưa dùng được."); return START_NOT_STICKY }
        Engine.reset(s.name)
        src = s
        s.start()
        Engine.startGps()
        return START_NOT_STICKY
    }

    private fun closeSrc() { try { src?.stop() } catch (_: Throwable) {}; src = null }

    private fun goForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CH, "SK Driver đang chạy", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, DriverService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        val n = (if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CH) else @Suppress("DEPRECATION") Notification.Builder(this))
            .setSmallIcon(R.drawable.ic_notif).setContentTitle("SK Driver đang chạy").setContentText("Đang theo dõi khoảng cách phía trước và gửi tín hiệu sang SK Light")
            .setContentIntent(open).setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Dừng", stop).build()).build()
        if (Build.VERSION.SDK_INT >= 29) {
            var t = 0
            if (Engine.cfg.src == 0 && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) t = t or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) t = t or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            if (t == 0) t = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            try { startForeground(1, n, t) } catch (_: Throwable) { try { startForeground(1, n) } catch (_: Throwable) {} }
        } else startForeground(1, n)
    }

    override fun onBind(intent: Intent): IBinder? { super.onBind(intent); return null }

    override fun onDestroy() {
        closeSrc()
        Engine.stopGps()
        Link.stop(applicationContext, Engine.cfg.link)
        running = false
        super.onDestroy()
    }
}
