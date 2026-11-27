package vn.sklight.driver

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Size
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.tan

/**
 * SK Driver 0.1.0 – BẢN THỬ. Dùng camera sau của điện thoại (máy gắn ngang trên kính lái), nhận diện xe phía trước ngay trên máy
 * và ước lượng khoảng cách. Chưa thử trên máy thật, chưa điều khiển đèn, chưa có cảnh báo lệch làn.
 * Đây là công cụ hỗ trợ tham khảo, KHÔNG phải hệ thống an toàn.
 */
class MainActivity : ComponentActivity() {
    private lateinit var scene: SceneView
    private val exec = Executors.newSingleThreadExecutor()
    private var detector: ObjectDetector? = null
    private var lm: LocationManager? = null
    @Volatile private var speedKmh = -1f
    @Volatile private var speedAt = 0L
    private var smooth = -1f
    private var lastT = 0L
    private var fps = 0f

    private val sp by lazy { getSharedPreferences("skdriver", Context.MODE_PRIVATE) }
    @Volatile private var horizon = 0.5f       // vị trí đường chân trời, 0 = mép trên hình, 1 = mép dưới
    @Volatile private var camH = 1.25f         // độ cao camera so với mặt đường (m)
    @Volatile private var vfov = 50f           // góc nhìn dọc của camera (độ)
    @Volatile private var gapSec = 2.0f        // khoảng cách tối thiểu tính theo giây

    private val gpsL = object : LocationListener {
        override fun onLocationChanged(l: Location) { speedKmh = if (l.hasSpeed()) l.speed * 3.6f else -1f; speedAt = SystemClock.elapsedRealtime() }
        @Deprecated("Deprecated in Java") override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
        override fun onProviderEnabled(p: String) {}
        override fun onProviderDisabled(p: String) {}
    }

    private val ask = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { begin() }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        horizon = sp.getFloat("horizon", 0.5f); camH = sp.getFloat("camH", 1.25f); vfov = sp.getFloat("vfov", 50f); gapSec = sp.getFloat("gap", 2f)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        scene = SceneView(this)
        root.addView(scene, FrameLayout.LayoutParams(-1, -1))
        root.addView(panel(), FrameLayout.LayoutParams(dp(300), -2, Gravity.BOTTOM or Gravity.START).apply { setMargins(dp(10), 0, 0, dp(10)) })
        setContentView(root)
        ask.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()

    /** Bảng cân chỉnh: bấm "Cân chỉnh" để hiện / ẩn ba thanh trượt. */
    private fun panel(): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xCC0B1230.toInt()); setPadding(dp(10), dp(6), dp(10), dp(8)) }
        val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        val btn = Button(this).apply { text = "Cân chỉnh"; setOnClickListener { inner.visibility = if (inner.visibility == View.GONE) View.VISIBLE else View.GONE } }
        fun row(label: (Int) -> String, max: Int, start: Int, onSet: (Int) -> Unit) {
            val t = TextView(this).apply { setTextColor(Color.WHITE); textSize = 13f; text = label(start) }
            val s = SeekBar(this).apply {
                this.max = max; progress = start
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, p: Int, u: Boolean) { t.text = label(p); onSet(p) }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) { sp.edit().putFloat("horizon", horizon).putFloat("camH", camH).putFloat("vfov", vfov).putFloat("gap", gapSec).apply() }
                })
            }
            inner.addView(t); inner.addView(s, LinearLayout.LayoutParams(-1, dp(40)))
        }
        row({ "Đường chân trời: $it% (kéo cho vạch xanh trùng chân trời)" }, 100, (horizon * 100).roundToInt()) { horizon = it / 100f }
        row({ "Độ cao camera: ${80 + it} cm" }, 120, (camH * 100).roundToInt() - 80) { camH = (80 + it) / 100f }
        row({ "Góc nhìn dọc camera: ${30 + it}°" }, 50, vfov.roundToInt() - 30) { vfov = 30f + it }
        row({ "Khoảng cách tối thiểu: ${"%.1f".format(1f + it / 10f)} giây" }, 20, ((gapSec - 1f) * 10).roundToInt()) { gapSec = 1f + it / 10f }
        box.addView(btn); box.addView(inner)
        return box
    }

    @SuppressLint("MissingPermission")
    private fun begin() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try { lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager; lm?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500L, 0f, gpsL) } catch (_: Exception) {}
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) { scene.note = "Chưa có quyền camera. Vào Cài đặt máy, cấp quyền Camera cho SK Driver."; scene.postInvalidate(); return }
        try {
            val opt = ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath("det.tflite").build())
                .setRunningMode(RunningMode.IMAGE).setMaxResults(10).setScoreThreshold(0.35f)
                .setCategoryAllowlist(listOf("car", "truck", "bus", "motorcycle")).build()
            detector = ObjectDetector.createFromOptions(this, opt)
        } catch (e: Throwable) { scene.note = "Không nạp được mô hình nhận diện trên máy này: ${e.javaClass.simpleName}"; scene.postInvalidate(); return }
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
            } catch (e: Throwable) { scene.note = "Không mở được camera sau: ${e.javaClass.simpleName}"; scene.postInvalidate() }
        }, ContextCompat.getMainExecutor(this))
    }

    private var raw: Bitmap? = null

    private fun analyze(img: ImageProxy) {
        val det = detector ?: return
        val plane = img.planes[0]
        val rw = plane.rowStride / plane.pixelStride
        var r = raw
        if (r == null || r.width != rw || r.height != img.height) { r = Bitmap.createBitmap(rw, img.height, Bitmap.Config.ARGB_8888); raw = r }
        plane.buffer.rewind(); r!!.copyPixelsFromBuffer(plane.buffer)
        val m = Matrix().apply { postRotate(img.imageInfo.rotationDegrees.toFloat()) }
        val bmp = Bitmap.createBitmap(r, 0, 0, img.width, img.height, m, false)
        val res = det.detect(BitmapImageBuilder(bmp).build())
        val w = bmp.width.toFloat(); val h = bmp.height.toFloat()
        val hy = horizon * h
        val fpx = (h / 2f) / tan(Math.toRadians(vfov / 2.0)).toFloat()
        val boxes = ArrayList<Box>()
        var lead: Box? = null
        for (d in res.detections()) {
            val bb = d.boundingBox()
            val c = d.categories().firstOrNull() ?: continue
            val below = bb.bottom - hy
            val dist = if (below > 2f) camH * fpx / below else -1f
            val bx = Box(RectF(bb), c.categoryName(), c.score(), dist)
            boxes.add(bx)
            if (dist > 0f && dist < 150f) {
                // chỉ lấy xe nằm trong làn của mình: tâm xe lệch khỏi giữa hình không quá nửa bề rộng làn (1,75 m) ở khoảng cách đó
                val half = 1.75f * fpx / dist
                if (abs(bb.centerX() - w / 2f) < half && (lead == null || dist < lead!!.dist)) lead = bx
            }
        }
        val now = SystemClock.elapsedRealtime()
        if (lastT != 0L) { val f = 1000f / (now - lastT).coerceAtLeast(1); fps = if (fps == 0f) f else fps * 0.8f + f * 0.2f }
        lastT = now
        val l = lead
        smooth = if (l == null) -1f else if (smooth < 0f) l.dist else smooth * 0.6f + l.dist * 0.4f
        val spd = if (now - speedAt < 4000) speedKmh else -1f
        val minD = if (spd >= 40f) spd / 3.6f * gapSec else -1f
        val level = if (l == null) 0 else if (minD < 0f) 4 else if (smooth < minD) 3 else if (smooth < minD + 5f) 2 else 1
        scene.show(Frame(bmp, boxes, l, smooth, spd, minD, level, fps, hy, fpx))
    }

    override fun onDestroy() {
        try { lm?.removeUpdates(gpsL) } catch (_: Exception) {}
        exec.shutdown()
        try { detector?.close() } catch (_: Exception) {}
        super.onDestroy()
    }
}

class Box(val r: RectF, val name: String, val score: Float, val dist: Float)
class Frame(val bmp: Bitmap, val boxes: List<Box>, val lead: Box?, val dist: Float, val speed: Float, val minD: Float, val level: Int, val fps: Float, val hy: Float, val fpx: Float)

/** Vẽ hình camera, khung quanh xe, vạch chân trời, hành lang làn và số khoảng cách. */
class SceneView(c: Context) : View(c) {
    @Volatile private var f: Frame? = null
    @Volatile var note = "Đang mở camera…"
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dst = RectF()

    fun show(fr: Frame) { f = fr; postInvalidate() }

    private fun levelColor(l: Int) = when (l) { 1 -> 0xFF34D399.toInt(); 2 -> 0xFFFACC15.toInt(); 3 -> 0xFFFF3040.toInt(); 4 -> 0xFF4E9BFF.toInt(); else -> 0xFFB8BCC6.toInt() }

    override fun onDraw(cv: Canvas) {
        val dm = resources.displayMetrics.density
        val fr = f
        p.style = Paint.Style.FILL; p.typeface = Typeface.DEFAULT_BOLD; p.pathEffect = null
        if (fr == null) { p.color = Color.WHITE; p.textSize = 18f * dm; p.textAlign = Paint.Align.CENTER; cv.drawText(note, width / 2f, height / 2f, p); return }
        val k = minOf(width / fr.bmp.width.toFloat(), height / fr.bmp.height.toFloat())
        val ox = (width - fr.bmp.width * k) / 2f; val oy = (height - fr.bmp.height * k) / 2f
        dst.set(ox, oy, ox + fr.bmp.width * k, oy + fr.bmp.height * k)
        p.isFilterBitmap = true
        cv.drawBitmap(fr.bmp, null, dst, p)
        fun X(x: Float) = ox + x * k
        fun Y(y: Float) = oy + y * k
        // vạch chân trời
        p.style = Paint.Style.STROKE; p.strokeWidth = 2f * dm; p.color = 0xFF22D3EE.toInt(); p.pathEffect = DashPathEffect(floatArrayOf(14f * dm, 10f * dm), 0f)
        cv.drawLine(dst.left, Y(fr.hy), dst.right, Y(fr.hy), p)
        // hành lang làn của mình (rộng 3,5 m) từ 6 m tới 80 m
        p.pathEffect = null; p.color = 0x99FFFFFF.toInt()
        val cx = fr.bmp.width / 2f
        fun lanePt(side: Float, d: Float) = floatArrayOf(X(cx + side * 1.75f * fr.fpx / d), Y(fr.hy + laneDy(fr, d)))
        for (s in floatArrayOf(-1f, 1f)) { val a = lanePt(s, 6f); val b = lanePt(s, 80f); cv.drawLine(a[0], a[1], b[0], b[1], p) }
        // khung quanh các xe
        p.textAlign = Paint.Align.LEFT; p.textSize = 13f * dm
        for (b in fr.boxes) {
            val lead = b === fr.lead
            p.style = Paint.Style.STROKE; p.strokeWidth = (if (lead) 4f else 1.5f) * dm; p.color = if (lead) levelColor(fr.level) else 0xCCFFFFFF.toInt()
            cv.drawRect(X(b.r.left), Y(b.r.top), X(b.r.right), Y(b.r.bottom), p)
            p.style = Paint.Style.FILL
            if (b.dist > 0f) cv.drawText("${b.dist.roundToInt()} m", X(b.r.left) + 3f * dm, Y(b.r.top) - 4f * dm, p)
        }
        // số khoảng cách lớn ở giữa trên
        p.textAlign = Paint.Align.CENTER; p.color = 0xCC0B1230.toInt()
        val bw = 250f * dm; val bh = 96f * dm
        cv.drawRoundRect(width / 2f - bw / 2f, 10f * dm, width / 2f + bw / 2f, 10f * dm + bh, 18f * dm, 18f * dm, p)
        p.color = levelColor(fr.level); p.textSize = 54f * dm
        cv.drawText(if (fr.lead == null) "—" else "${fr.dist.roundToInt()} m", width / 2f, 10f * dm + 58f * dm, p)
        p.color = Color.WHITE; p.textSize = 14f * dm
        val sub = (if (fr.speed < 0f) "Chưa có tốc độ GPS" else "${fr.speed.roundToInt()} km/h") + (if (fr.minD > 0f) " · tối thiểu ${fr.minD.roundToInt()} m" else if (fr.speed >= 0f) " · dưới 40 km/h chưa cảnh báo" else "")
        cv.drawText(sub, width / 2f, 10f * dm + 84f * dm, p)
        // góc phải trên: tốc độ xử lý
        p.textAlign = Paint.Align.RIGHT; p.textSize = 13f * dm; p.color = 0xFFFFFFFF.toInt()
        cv.drawText("${"%.1f".format(fr.fps)} khung/giây · ${fr.bmp.width}x${fr.bmp.height}", width - 12f * dm, 24f * dm, p)
        cv.drawText("BẢN THỬ · chỉ để tham khảo", width - 12f * dm, 44f * dm, p)
    }

    /** Độ lệch xuống dưới chân trời (theo điểm ảnh của hình gốc) của một điểm trên mặt đường cách camera [d] mét; cần độ cao camera nên suy ngược từ xe dẫn đầu nếu có. */
    private fun laneDy(fr: Frame, d: Float): Float {
        val l = fr.lead
        // camH * fpx = dist * (đáy xe - chân trời): lấy từ xe dẫn đầu; chưa có xe thì giả định camera cao 1,25 m
        val hf = if (l != null && l.dist > 0f) l.dist * (l.r.bottom - fr.hy) else 1.25f * fr.fpx
        return hf / d
    }
}
