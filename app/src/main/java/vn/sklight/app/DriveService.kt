package vn.sklight.app

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder

/** Giữ app sống khi chạy nền: BLE + đo tốc độ GPS cho cảnh báo. */
class DriveService : Service() {
    private var lm: LocationManager? = null
    private val ll = object : LocationListener {
        override fun onLocationChanged(l: Location) {
            AlertBridge.onSpeed(if (l.hasSpeed()) l.speed * 3.6f else 0f)
        }
        @Deprecated("old") override fun onStatusChanged(p: String?, s: Int, e: android.os.Bundle?) {}
        override fun onProviderEnabled(p: String) {}
        override fun onProviderDisabled(p: String) {}
    }

    override fun onBind(i: Intent?): IBinder? = null

    @SuppressLint("MissingPermission")
    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        S.init(this)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("drive", "SK Light", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "drive")
            .setContentTitle("SK Light đang chạy")
            .setContentText("Điều khiển đèn và cảnh báo dẫn đường")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true).build()
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            else startForeground(1, n)
        } catch (e: Exception) { stopSelf(); return START_NOT_STICKY }
        try {
            lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            lm?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, ll)
        } catch (e: Exception) { /* chưa cấp quyền vị trí: bỏ qua cảnh báo tốc độ */ }
        return START_STICKY
    }

    override fun onDestroy() {
        try { lm?.removeUpdates(ll) } catch (_: Exception) {}
        super.onDestroy()
    }
}
