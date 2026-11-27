package vn.sklight.driver

import android.Manifest
import android.content.Context
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import kotlin.math.abs
import kotlin.math.atan2

/**
 * SK Driver 0.4.0 – BẢN THỬ. Màn hình xem và cài đặt. Việc nhận hình, nhận diện, tính khoảng cách và gửi sang SK Light nằm trong [DriverService] /
 * [Engine], chạy được cả khi app này không ở phía trước. Nguồn hình: camera điện thoại, Wi-Fi MJPEG, Wi-Fi RTSP (USB sắp có).
 * Ba màn: Lái xe, Cân chỉnh, Cài đặt. Đây là công cụ hỗ trợ tham khảo, KHÔNG phải hệ thống an toàn.
 */
class MainActivity : ComponentActivity() {
    val cfg: Cfg get() = Engine.cfg
    val roll: Float get() = Engine.roll
    private lateinit var drive: DriveView
    private lateinit var calib: CalibScreen
    private lateinit var settings: SettingsScreen
    private var screen = 0 // 0 lái xe, 1 cân chỉnh, 2 cài đặt

    private val accL = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            // máy nằm ngang: trọng lực chủ yếu theo trục x của máy, phần lệch sang trục y là độ nghiêng
            val r = Math.toDegrees(atan2(e.values[1].toDouble(), abs(e.values[0]).toDouble())).toFloat()
            Engine.roll = Engine.roll * 0.85f + r * 0.15f
        }
        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    }

    private val ask = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { begin() }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Engine.init(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        drive = DriveView(this, { go(1) }, { go(2) })
        calib = CalibScreen(this) { go(0) }
        settings = SettingsScreen(this, { go(0) }, { go(1) })
        root.addView(drive, FrameLayout.LayoutParams(-1, -1))
        root.addView(calib, FrameLayout.LayoutParams(-1, -1))
        root.addView(settings, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        go(0)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (screen != 0) go(0) else finish() }
        })
        val need = arrayListOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) need.add(Manifest.permission.POST_NOTIFICATIONS)
        ask.launch(need.toTypedArray())
    }

    private fun go(s: Int) {
        screen = s
        drive.visibility = if (s == 0) View.VISIBLE else View.GONE
        calib.visibility = if (s == 1) View.VISIBLE else View.GONE
        settings.visibility = if (s == 2) View.VISIBLE else View.GONE
        if (s == 1) calib.sync()
        if (s == 2) settings.sync()
        cfg.save()
    }

    /** Đổi CPU/GPU: dựng lại bộ nhận diện ở khung hình kế tiếp. */
    fun requestRebuild() = Engine.requestRebuild()

    /** Dựng lại màn Cài đặt sau khi nguồn hình bị đổi từ bên ngoài ô chọn (tự dò, chọn mẫu). */
    fun recreateSettings() {
        val root = settings.parent as FrameLayout
        val vis = settings.visibility
        root.removeView(settings)
        settings = SettingsScreen(this, { go(0) }, { go(1) })
        settings.visibility = vis
        root.addView(settings, FrameLayout.LayoutParams(-1, -1))
        settings.sync()
    }

    /** Đổi nguồn hình hoặc địa chỉ: chạy lại dịch vụ với nguồn mới. */
    fun restartSource() { cfg.save(); DriverService.start(this) }

    @Suppress("DEPRECATION")
    override fun onWindowFocusChanged(has: Boolean) {
        super.onWindowFocusChanged(has)
        if (has) window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    override fun onStart() {
        super.onStart()
        if (Engine.status.isNotEmpty()) drive.note = Engine.status
        Engine.noteCb = { s -> drive.note = s; drive.postInvalidate() }
        Engine.ui = { fr -> when (screen) { 0 -> drive.show(fr); 1 -> calib.show(fr) } }
    }

    override fun onStop() {
        Engine.ui = null; Engine.noteCb = null
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        try {
            val sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(accL, it, SensorManager.SENSOR_DELAY_UI) }
        } catch (_: Throwable) {}
    }

    override fun onPause() {
        try { (getSystemService(Context.SENSOR_SERVICE) as SensorManager).unregisterListener(accL) } catch (_: Throwable) {}
        cfg.save()
        super.onPause()
    }

    /** Sau khi xin quyền: bật dịch vụ nền (nếu đang chạy rồi thì chỉ chạy lại nguồn hình, không sao). */
    private fun begin() { if (!DriverService.running) DriverService.start(this) }
}
