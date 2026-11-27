package vn.sklight.driver

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.RectF
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.tan

/**
 * Bộ xử lý dùng chung: nhận từng khung hình từ BẤT KỲ nguồn hình nào (camera điện thoại, Wi-Fi MJPEG / RTSP, USB), nhận diện, ước lượng
 * khoảng cách, tính mức cảnh báo và gửi sang SK Light. Chạy trong [DriverService] nên vẫn chạy khi app khác (SK Light, bản đồ) ở phía trước.
 * Màn hình của SK Driver chỉ là nơi xem: đặt [ui] khi màn đang mở.
 */
object Engine {
    lateinit var cfg: Cfg
    private lateinit var app: Context
    private var inited = false

    /** Màn hình đang mở nhận khung hình (gọi trên luồng xử lý). Null khi app không ở phía trước. */
    @Volatile var ui: ((Frame) -> Unit)? = null
    /** Màn Lái xe đang được chiếu sang SK Light (xem ShareService). */
    @Volatile var mirror: ((Frame) -> Unit)? = null
    @Volatile var noteCb: ((String) -> Unit)? = null
    @Volatile var roll = 0f // độ nghiêng trái/phải của máy (độ), do màn hình cân chỉnh đo bằng cảm biến

    /** Trạng thái nguồn hình, để hiện ở Cài đặt (nút Thử kết nối) và ở màn Lái xe. */
    @Volatile var status = ""
    @Volatile var frames = 0L
    @Volatile var srcW = 0
    @Volatile var srcH = 0
    @Volatile var lastFrameAt = 0L
    @Volatile var srcName = ""

    private var detector: ObjectDetector? = null
    private var detGpu = false
    private var detFailed = false
    @Volatile private var rebuild = false
    private var lm: LocationManager? = null
    private var pm: PowerManager? = null
    private var tone: ToneGenerator? = null
    @Volatile private var speedKmh = -1f
    @Volatile private var speedAt = 0L
    private var smooth = -1f
    private var lastT = 0L
    private var fps = 0f
    private var inferMs = 0f
    private var lastLevel = 0
    private var lastBeep = 0L
    private var frameNo = 0
    private var calibLog = 0f
    private var calibN = 0
    private var calibHy = -1f
    private var calibCh = -1f
    private val lock = Any()

    private val gpsL = object : LocationListener {
        override fun onLocationChanged(l: Location) { speedKmh = if (l.hasSpeed()) l.speed * 3.6f else -1f; speedAt = SystemClock.elapsedRealtime() }
        @Deprecated("Deprecated in Java") override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
        override fun onProviderEnabled(p: String) {}
        override fun onProviderDisabled(p: String) {}
    }

    fun init(c: Context) {
        if (inited) return
        app = c.applicationContext
        cfg = Cfg(app)
        pm = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
        try { tone = ToneGenerator(AudioManager.STREAM_MUSIC, 90) } catch (_: Throwable) {}
        inited = true
    }

    fun ready() = inited

    fun note(s: String) { status = s; noteCb?.invoke(s) }

    /** Đổi CPU/GPU: dựng lại bộ nhận diện ở khung hình kế tiếp. */
    fun requestRebuild() { detFailed = false; rebuild = true }

    /** Bắt đầu một lần chạy nguồn mới: xóa số liệu làm mượt của nguồn cũ. */
    fun reset(name: String) {
        synchronized(lock) {
            smooth = -1f; lastT = 0L; fps = 0f; lastLevel = 0; calibN = 0; calibLog = 0f; calibHy = -1f; calibCh = -1f
            frames = 0L; srcW = 0; srcH = 0; lastFrameAt = 0L; srcName = name; status = "Đang kết nối…"
        }
    }

    @SuppressLint("MissingPermission")
    fun startGps() {
        if (ContextCompat.checkSelfPermission(app, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        try {
            lm?.removeUpdates(gpsL)
            lm = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            lm?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500L, 0f, gpsL, android.os.Looper.getMainLooper())
        } catch (_: Exception) {}
    }

    fun stopGps() { try { lm?.removeUpdates(gpsL) } catch (_: Exception) {}; speedKmh = -1f }

    fun shutdown() {
        stopGps()
        try { detector?.close() } catch (_: Throwable) {}
        detector = null
    }

    /** Dựng bộ nhận diện ngay trên luồng xử lý (GPU cần tạo và chạy cùng một luồng). GPU lỗi thì lùi về CPU. */
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
            return ObjectDetector.createFromOptions(app, opt)
        }
        if (cfg.gpu) {
            try { detector = make(true); detGpu = true; return detector } catch (_: Throwable) { cfg.gpu = false }
        }
        try { detector = make(false); detGpu = false } catch (e: Throwable) { detFailed = true; note("Không nạp được mô hình nhận diện trên máy này: ${e.javaClass.simpleName}") }
        return detector
    }

    /**
     * Một khung hình ĐÃ ĐỨNG THẲNG (nguồn tự xoay). [camAt] = lúc khung hình về tới app (SystemClock.elapsedRealtime), dùng đo độ trễ.
     * Nguồn phải bỏ bớt khung khi còn đang xử lý khung trước (chỉ giữ khung mới nhất), để độ trễ không dồn lại.
     */
    fun feed(bmp: Bitmap, camAt: Long) {
        synchronized(lock) {
            try { analyze(bmp, camAt) } catch (_: Throwable) {}
        }
    }

    private fun analyze(bmp: Bitmap, camAt: Long) {
        frameNo++
        frames++
        srcW = bmp.width; srcH = bmp.height; lastFrameAt = SystemClock.elapsedRealtime()
        if (!status.startsWith("Không nạp")) status = "Đã nhận hình ${bmp.width}x${bmp.height}"
        val hot = cfg.thermal && (pm?.currentThermalStatus ?: 0) >= PowerManager.THERMAL_STATUS_SEVERE
        if (hot && frameNo % 2 == 0) return // máy nóng: chỉ xử lý một nửa số khung hình
        val det = ensureDetector() ?: return
        // Chỉ đưa cho mô hình dải ngang quanh đường chân trời (cao bằng nửa bề ngang hình: 40% trên, 60% dưới chân trời). Mô hình thu mọi hình về
        // 320 x 320 nên cắt bớt trời và mặt đường sát đầu xe thì vật ở xa to hơn khoảng 1,5 lần theo chiều dọc. Vật gần hơn chừng 3 m bị cắt đáy.
        var top = 0
        var src = bmp
        if (cfg.roi) {
            val bandH = (bmp.width / 2).coerceAtMost(bmp.height)
            top = (cfg.horizon * bmp.height - bandH * 0.4f).toInt().coerceIn(0, bmp.height - bandH)
            if (bandH < bmp.height) src = Bitmap.createBitmap(bmp, 0, top, bmp.width, bandH)
        }
        val t0 = SystemClock.elapsedRealtime()
        val res = try { det.detect(BitmapImageBuilder(src).build()) } catch (e: Throwable) {
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
        // 1) gom mọi vật mô hình thấy (toạ độ đưa về hình đầy đủ)
        class Raw(val r: RectF, var name: String, var kind: Int, val score: Float)
        val raws = ArrayList<Raw>()
        for (d in res.detections()) {
            val c = d.categories().firstOrNull() ?: continue
            val kind = Kind.of(c.categoryName())
            if (kind < 0) continue
            raws.add(Raw(RectF(d.boundingBox()).apply { offset(0f, top.toFloat()) }, c.categoryName(), kind, c.score()))
        }
        // 2) người ngồi trên xe máy / xe đạp: gộp vào xe thành một vật, đáy khung lấy ở bánh xe
        val gone = HashSet<Raw>()
        for (b in raws) if (b.kind == 1 || b.kind == 2) for (q in raws) if (q.kind == 3 && q !in gone) {
            val pad = b.r.width() * 0.3f
            if (q.r.centerX() > b.r.left - pad && q.r.centerX() < b.r.right + pad && q.r.bottom > b.r.top && q.r.top < b.r.bottom) { b.r.union(q.r); gone.add(q) }
        }
        // 3) lọc theo nhóm đang bật, tính khoảng cách, chọn vật dẫn đầu
        val boxes = ArrayList<Box>()
        var lead: Box? = null
        var cw = 0f // tự kiểm tra cân chỉnh: tỉ số (mét theo chân trời) / (mét theo bề ngang ô tô 1,8 m) của ô tô nhìn từ sau rõ nhất
        for (q in raws) {
            if (q in gone || (kinds shr q.kind) and 1 == 0) continue
            val bb = q.r
            val below = bb.bottom - hy
            val dist = if (below > 2f) hf / below else -1f
            val bx = Box(bb, q.name, q.kind, q.score, dist)
            boxes.add(bx)
            if (dist > 0f && dist < 150f) {
                // chỉ lấy vật nằm trong làn của mình (theo hai mép đã căn). Người và động vật hay băng ngang nên nới rộng thêm một nửa.
                val half = laneK * below * (if (q.kind >= 3) 1.5f else 1f)
                if (abs(bb.centerX() - vpx) < half && (lead == null || dist < lead!!.dist)) lead = bx
                // ô tô con nhìn từ sau / trước (khung gần vuông), không chạm mép hình, đủ to: bề ngang thật khoảng 1,8 m
                val asp = bb.width() / bb.height().coerceAtLeast(1f)
                if (q.name == "car" && q.score > 0.5f && bb.width() > 28f && asp > 0.9f && asp < 1.5f && bb.left > 3f && bb.right < w - 3f && bb.bottom < top + src.height - 3f)
                    cw = cfg.camH * bb.width() / (1.8f * below)
            }
        }
        if (cw > 0f) { val lg = ln(cw); calibLog = if (calibN == 0) lg else calibLog * 0.9f + lg * 0.1f; if (calibN < 1000) calibN++ }
        if (cfg.horizon != calibHy || cfg.camH != calibCh) { calibHy = cfg.horizon; calibCh = cfg.camH; calibN = 0; calibLog = 0f } // vừa cân chỉnh lại: đo lại từ đầu
        // lệch quá 40% sau ít nhất 20 lần thấy ô tô thì nhắc cân chỉnh lại
        val calibFlag = if (calibN < 20) 0 else if (calibLog > 0.34f) 1 else if (calibLog < -0.34f) 2 else 0
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
        Link.send(app, cfg.link, level, smooth, camAt)
        val fr = Frame(bmp, boxes, l, smooth, err, spd, minD, level, fps, inferMs, hy, vpx, laneK, hf, detGpu, hot, start, calibFlag)
        ui?.invoke(fr)
        mirror?.invoke(fr)
    }
}
