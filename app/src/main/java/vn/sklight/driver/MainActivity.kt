package vn.sklight.driver

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.util.Size
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.tan

/**
 * SK Driver 0.2.0 – BẢN THỬ. Camera sau của điện thoại (máy gắn ngang trên kính lái), nhận diện xe, người, động vật phía trước ngay trên máy
 * và ước lượng khoảng cách. Ba màn: Lái xe, Cân chỉnh, Cài đặt. Chưa liên kết SK Light, chưa có cảnh báo lệch làn.
 * Đây là công cụ hỗ trợ tham khảo, KHÔNG phải hệ thống an toàn.
 */
class MainActivity : ComponentActivity() {
    lateinit var cfg: Cfg
    private lateinit var drive: DriveView
    private lateinit var calib: CalibScreen
    private lateinit var settings: SettingsScreen
    private var screen = 0 // 0 lái xe, 1 cân chỉnh, 2 cài đặt

    private val exec = Executors.newSingleThreadExecutor()
    private var detector: ObjectDetector? = null
    private var detGpu = false
    private var detFailed = false
    @Volatile private var rebuild = false
    private var lm: LocationManager? = null
    private var pm: PowerManager? = null
    private var tone: ToneGenerator? = null
    @Volatile private var speedKmh = -1f
    @Volatile private var speedAt = 0L
    @Volatile var roll = 0f // độ nghiêng trái/phải của máy (độ), đo bằng cảm biến gia tốc
    private var smooth = -1f
    private var lastT = 0L
    private var fps = 0f
    private var inferMs = 0f
    private var lastLevel = 0
    private var lastBeep = 0L
    private var frameNo = 0

    private val gpsL = object : LocationListener {
        override fun onLocationChanged(l: Location) { speedKmh = if (l.hasSpeed()) l.speed * 3.6f else -1f; speedAt = SystemClock.elapsedRealtime() }
        @Deprecated("Deprecated in Java") override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
        override fun onProviderEnabled(p: String) {}
        override fun onProviderDisabled(p: String) {}
    }

    private val accL = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            // máy nằm ngang: trọng lực chủ yếu theo trục x của máy, phần lệch sang trục y là độ nghiêng
            val r = Math.toDegrees(atan2(e.values[1].toDouble(), abs(e.values[0]).toDouble())).toFloat()
            roll = roll * 0.85f + r * 0.15f
        }
        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    }

    private val ask = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { begin() }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        cfg = Cfg(this)
        pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        try { tone = ToneGenerator(AudioManager.STREAM_MUSIC, 90) } catch (_: Throwable) {}
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
        ask.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION))
    }

    private fun go(s: Int) {
        screen = s
        drive.visibility = if (s == 0) View.VISIBLE else View.GONE
        calib.visibility = if (s == 1) View.VISIBLE else View.GONE
        settings.visibility = if (s == 2) View.VISIBLE else View.GONE
        if (s == 1) calib.sync()
        cfg.save()
    }

    /** Đổi CPU/GPU: dựng lại bộ nhận diện ở khung hình kế tiếp. */
    fun requestRebuild() { detFailed = false; rebuild = true }

    @Suppress("DEPRECATION")
    override fun onWindowFocusChanged(has: Boolean) {
        super.onWindowFocusChanged(has)
        if (has) window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
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

    @SuppressLint("MissingPermission")
    private fun begin() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try { lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager; lm?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500L, 0f, gpsL) } catch (_: Exception) {}
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) { note("Chưa có quyền camera. Vào Cài đặt máy, cấp quyền Camera cho SK Driver."); return }
        val fut = ProcessCameraProvider.getInstance(this)
        fut.addListener({
            try {
                val provider = fut.get()
                @Suppress("DEPRECATION")
                val an = ImageAnalysis.Builder().setTargetResolution(Size(640, 480)).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888).build()
                an.setAnalyzer(exec) { img -> try { analyze(img) } catch (_: Throwable) {} finally { img.close() } }
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, an)
            } catch (e: Throwable) { note("Không mở được camera sau: ${e.javaClass.simpleName}") }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun note(s: String) { drive.note = s; drive.postInvalidate() }

    /** Dựng bộ nhận diện ngay trên luồng phân tích (GPU cần tạo và chạy cùng một luồng). GPU lỗi thì lùi về CPU. */
    private fun ensureDetector(): ObjectDetector? {
        if (rebuild) { rebuild = false; try { detector?.close() } catch (_: Throwable) {}; detector = null }
        detector?.let { return it }
        if (detFailed) return null
        fun make(gpu: Boolean): ObjectDetector {
            val base = BaseOptions.builder().setModelAssetPath("det.tflite")
            if (gpu) base.setDelegate(Delegate.GPU)
            val opt = ObjectDetector.ObjectDetectorOptions.builder().setBaseOptions(base.build())
                .setRunningMode(RunningMode.IMAGE).setMaxResults(12).setScoreThreshold(0.35f)
                .setCategoryAllowlist(Kind.ALLOW).build()
            return ObjectDetector.createFromOptions(this, opt)
        }
        if (cfg.gpu) {
            try { detector = make(true); detGpu = true; return detector } catch (_: Throwable) { cfg.gpu = false }
        }
        try { detector = make(false); detGpu = false } catch (e: Throwable) { detFailed = true; note("Không nạp được mô hình nhận diện trên máy này: ${e.javaClass.simpleName}") }
        return detector
    }

    private var raw: Bitmap? = null

    private fun analyze(img: ImageProxy) {
        frameNo++
        val hot = cfg.thermal && (pm?.currentThermalStatus ?: 0) >= PowerManager.THERMAL_STATUS_SEVERE
        if (hot && frameNo % 2 == 0) return // máy nóng: chỉ xử lý một nửa số khung hình
        val det = ensureDetector() ?: return
        val plane = img.planes[0]
        val rw = plane.rowStride / plane.pixelStride
        var r = raw
        if (r == null || r.width != rw || r.height != img.height) { r = Bitmap.createBitmap(rw, img.height, Bitmap.Config.ARGB_8888); raw = r }
        plane.buffer.rewind(); r!!.copyPixelsFromBuffer(plane.buffer)
        val m = Matrix().apply { postRotate(img.imageInfo.rotationDegrees.toFloat()) }
        val bmp = Bitmap.createBitmap(r, 0, 0, img.width, img.height, m, false)
        val t0 = SystemClock.elapsedRealtime()
        val res = try { det.detect(BitmapImageBuilder(bmp).build()) } catch (e: Throwable) {
            // GPU chạy lỗi giữa chừng: chuyển về CPU
            if (detGpu) { cfg.gpu = false; rebuild = true }
            return
        }
        val ms = (SystemClock.elapsedRealtime() - t0).toFloat()
        inferMs = if (inferMs == 0f) ms else inferMs * 0.8f + ms * 0.2f
        val w = bmp.width.toFloat(); val h = bmp.height.toFloat()
        val hy = cfg.horizon * h
        val vpx = w / 2f + cfg.vx * w
        val laneK = cfg.laneK
        val fpx = (h / 2f) / tan(Math.toRadians(cfg.vfov / 2.0)).toFloat()
        val hf = cfg.camH * fpx
        val kinds = cfg.kinds or 1
        val boxes = ArrayList<Box>()
        var lead: Box? = null
        for (d in res.detections()) {
            val bb = d.boundingBox()
            val c = d.categories().firstOrNull() ?: continue
            val kind = Kind.of(c.categoryName())
            if (kind < 0 || (kinds shr kind) and 1 == 0) continue
            val below = bb.bottom - hy
            val dist = if (below > 2f) hf / below else -1f
            val bx = Box(RectF(bb), c.categoryName(), kind, c.score(), dist)
            boxes.add(bx)
            if (dist > 0f && dist < 150f) {
                // chỉ lấy vật nằm trong làn của mình (theo hai mép đã căn). Người và động vật hay băng ngang nên nới rộng thêm một nửa.
                val half = laneK * below * (if (kind >= 3) 1.5f else 1f)
                if (abs(bb.centerX() - vpx) < half && (lead == null || dist < lead!!.dist)) lead = bx
            }
        }
        val now = SystemClock.elapsedRealtime()
        if (lastT != 0L) { val f = 1000f / (now - lastT).coerceAtLeast(1); fps = if (fps == 0f) f else fps * 0.8f + f * 0.2f }
        lastT = now
        val l = lead
        smooth = if (l == null) -1f else if (smooth < 0f) l.dist else smooth * 0.6f + l.dist * 0.4f
        // sai số ước lượng: đáy khung lệch khoảng 2 điểm ảnh thì khoảng cách lệch bấy nhiêu
        val err = if (l == null) 0f else (smooth * 2f / (l.r.bottom - hy).coerceAtLeast(2f)).coerceAtLeast(1f)
        val spd = if (now - speedAt < 4000) speedKmh else -1f
        val start = cfg.startKmh
        val minD = if (spd >= start) spd / 3.6f * cfg.gapSec else -1f
        val level = if (l == null) 0 else if (minD < 0f) 4 else if (smooth < minD) 3 else if (smooth < minD + 5f) 2 else 1
        if (level == 3 && cfg.beep && (lastLevel != 3 || now - lastBeep > 2000)) { lastBeep = now; try { tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 220) } catch (_: Throwable) {} }
        lastLevel = level
        val fr = Frame(bmp, boxes, l, smooth, err, spd, minD, level, fps, inferMs, hy, vpx, laneK, hf, detGpu, hot, start)
        when (screen) { 0 -> drive.show(fr); 1 -> calib.show(fr) }
    }

    override fun onDestroy() {
        try { lm?.removeUpdates(gpsL) } catch (_: Exception) {}
        exec.shutdown()
        try { detector?.close() } catch (_: Exception) {}
        try { tone?.release() } catch (_: Exception) {}
        super.onDestroy()
    }
}
