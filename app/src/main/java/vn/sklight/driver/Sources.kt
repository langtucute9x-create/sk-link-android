package vn.sklight.driver

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.media.Image
import android.media.ImageReader
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Base64
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Nguồn hình: mỗi loại camera là một lớp, cùng đưa khung hình ĐÃ ĐỨNG THẲNG vào [Engine.feed]. Luôn chỉ giữ khung mới nhất (khung cũ bị bỏ)
 * để độ trễ không dồn lại khi bộ nhận diện chậm hơn luồng hình.
 */
interface Src {
    val name: String
    fun start()
    fun stop()
}

/** Camera sau của điện thoại, qua CameraX. Chạy được khi app ở nền vì được gắn vào dịch vụ nền. */
class PhoneSrc(private val svc: LifecycleService) : Src {
    override val name = "Camera điện thoại"
    private val exec = Executors.newSingleThreadExecutor()
    private var provider: ProcessCameraProvider? = null
    private var raw: Bitmap? = null

    override fun start() {
        if (ContextCompat.checkSelfPermission(svc, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Engine.note("Chưa có quyền camera. Vào Cài đặt máy, cấp quyền Camera cho SK Driver."); return
        }
        val fut = ProcessCameraProvider.getInstance(svc)
        fut.addListener({
            try {
                val p = fut.get(); provider = p
                @Suppress("DEPRECATION")
                val an = ImageAnalysis.Builder().setTargetResolution(Size(640, 480)).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888).build()
                an.setAnalyzer(exec) { img ->
                    try {
                        val camAt = SystemClock.elapsedRealtime()
                        val plane = img.planes[0]
                        val rw = plane.rowStride / plane.pixelStride
                        var r = raw
                        if (r == null || r.width != rw || r.height != img.height) { r = Bitmap.createBitmap(rw, img.height, Bitmap.Config.ARGB_8888); raw = r }
                        plane.buffer.rewind(); r!!.copyPixelsFromBuffer(plane.buffer)
                        val m = Matrix().apply { postRotate(img.imageInfo.rotationDegrees.toFloat()) }
                        Engine.feed(Bitmap.createBitmap(r, 0, 0, img.width, img.height, m, false), camAt)
                    } catch (_: Throwable) {} finally { img.close() }
                }
                p.unbindAll()
                p.bindToLifecycle(svc, CameraSelector.DEFAULT_BACK_CAMERA, an)
            } catch (e: Throwable) { Engine.note("Không mở được camera sau: ${e.javaClass.simpleName}") }
        }, ContextCompat.getMainExecutor(svc))
    }

    override fun stop() {
        try { ContextCompat.getMainExecutor(svc).execute { try { provider?.unbindAll() } catch (_: Throwable) {} } } catch (_: Throwable) {}
        exec.shutdown()
    }
}

/**
 * Wi-Fi, luồng MJPEG qua HTTP (nhiều camera hành trình và camera giá rẻ dùng): đọc liên tục, cắt từng ảnh JPEG theo dấu đầu FFD8 và dấu cuối FFD9.
 * Cũng nhận địa chỉ trả về MỘT ảnh JPEG (snapshot): khi đó tải lại liên tục. Địa chỉ có dạng http://tên:mật-khẩu@địa-chỉ/... thì gửi kèm đăng nhập.
 */
class MjpegSrc(private val url: String) : Src {
    override val name = "Wi-Fi MJPEG"
    @Volatile private var run = false
    private var reader: Thread? = null
    private var worker: Thread? = null
    private val mail = Object()
    private var latest: ByteArray? = null
    private var latestAt = 0L

    override fun start() {
        if (url.isBlank()) { Engine.note("Chưa nhập địa chỉ luồng hình. Vào Cài đặt, mục Nguồn hình."); return }
        run = true
        reader = Thread({ readLoop() }, "mjpeg-read").also { it.start() }
        worker = Thread({ workLoop() }, "mjpeg-work").also { it.start() }
    }

    override fun stop() {
        run = false
        synchronized(mail) { mail.notifyAll() }
        reader?.interrupt(); worker?.interrupt()
    }

    private fun publish(b: ByteArray) { synchronized(mail) { latest = b; latestAt = SystemClock.elapsedRealtime(); mail.notifyAll() } }

    private fun readLoop() {
        while (run) {
            var conn: HttpURLConnection? = null
            try {
                val u = URL(url)
                conn = (u.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000; readTimeout = 6000; setRequestProperty("User-Agent", "SKDriver")
                    u.userInfo?.let { setRequestProperty("Authorization", "Basic " + Base64.encodeToString(it.toByteArray(), Base64.NO_WRAP)) }
                }
                if (conn.responseCode != 200) throw RuntimeException("máy chủ trả mã ${conn.responseCode}")
                val ct = conn.contentType ?: ""
                if (ct.startsWith("image/")) { // một ảnh mỗi lần
                    publish(conn.inputStream.use { it.readBytes() })
                    Thread.sleep(30); continue
                }
                val ins = java.io.BufferedInputStream(conn.inputStream, 65536)
                var buf = ByteArray(1 shl 20); var n = 0
                val chunk = ByteArray(16384)
                while (run) {
                    val r = ins.read(chunk)
                    if (r < 0) break
                    if (n + r > buf.size) { if (buf.size >= (16 shl 20)) n = 0 else buf = buf.copyOf(buf.size * 2) }
                    System.arraycopy(chunk, 0, buf, n, r); n += r
                    while (true) {
                        val s = find(buf, n, 0xD8, 0)
                        if (s < 0) { if (n > 0 && buf[n - 1] == 0xFF.toByte()) { buf[0] = 0xFF.toByte(); n = 1 } else n = 0; break }
                        val e = find(buf, n, 0xD9, s + 2)
                        if (e < 0) { if (s > 0) { System.arraycopy(buf, s, buf, 0, n - s); n -= s }; break }
                        publish(buf.copyOfRange(s, e + 2))
                        val rest = n - (e + 2); System.arraycopy(buf, e + 2, buf, 0, rest); n = rest
                    }
                }
                if (run) throw RuntimeException("luồng hình bị ngắt")
            } catch (_: InterruptedException) { break } catch (e: Exception) {
                if (run) Engine.note("Không nhận được hình Wi-Fi: ${e.message ?: e.javaClass.simpleName}. Máy đã nối Wi-Fi của camera chưa?")
                try { Thread.sleep(2000) } catch (_: InterruptedException) { break }
            } finally { try { conn?.disconnect() } catch (_: Exception) {} }
        }
    }

    /** Tìm cặp byte FF [b2] từ vị trí [from]; trả vị trí byte FF, hoặc -1. */
    private fun find(buf: ByteArray, n: Int, b2: Int, from: Int): Int {
        var i = from
        while (i < n - 1) { if (buf[i] == 0xFF.toByte() && (buf[i + 1].toInt() and 0xFF) == b2) return i; i++ }
        return -1
    }

    private fun workLoop() {
        while (run) {
            var b: ByteArray? = null; var at = 0L
            synchronized(mail) {
                try { while (latest == null && run) mail.wait(500) } catch (_: InterruptedException) { return }
                b = latest; at = latestAt; latest = null
            }
            val jpg = b ?: continue
            var bmp = try { BitmapFactory.decodeByteArray(jpg, 0, jpg.size) } catch (_: Throwable) { null } ?: continue
            if (bmp.width > 1280) bmp = Bitmap.createScaledBitmap(bmp, bmp.width / 2, bmp.height / 2, true)
            Engine.feed(bmp, at)
        }
    }
}

/**
 * Wi-Fi, luồng RTSP (H.264 / H.265): dùng bộ phát của Media3, giải mã bằng chip của máy rồi lấy khung hình qua ImageReader (không cần cửa sổ nên
 * chạy được ở nền). Nhận qua TCP cho ổn định, bộ đệm nhỏ để độ trễ thấp. Địa chỉ dạng rtsp://tên:mật-khẩu@địa-chỉ:cổng/đường-dẫn.
 */
class RtspSrc(private val ctx: Context, private val url: String) : Src {
    override val name = "Wi-Fi RTSP"
    private val main = Handler(Looper.getMainLooper())
    private val ht = HandlerThread("rtsp-img").apply { start() }
    private val h = Handler(ht.looper)
    private var player: ExoPlayer? = null
    private var reader: ImageReader? = null
    @Volatile private var run = false

    override fun start() {
        if (url.isBlank()) { Engine.note("Chưa nhập địa chỉ luồng hình. Vào Cài đặt, mục Nguồn hình."); return }
        run = true
        main.post {
            try {
                val lc = DefaultLoadControl.Builder().setBufferDurationsMs(200, 400, 100, 200).build()
                val p = ExoPlayer.Builder(ctx).setLoadControl(lc).build()
                p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build()
                p.volume = 0f
                p.addListener(object : Player.Listener {
                    override fun onVideoSizeChanged(vs: VideoSize) {
                        val rd = reader
                        if (vs.width > 0 && vs.height > 0 && (rd == null || rd.width != vs.width || rd.height != vs.height)) attach(p, vs.width, vs.height)
                    }
                    override fun onPlayerError(e: PlaybackException) {
                        Engine.note("Không nhận được luồng RTSP (${e.errorCodeName}). Kiểm tra địa chỉ và Wi-Fi của camera.")
                        main.postDelayed({ if (run) try { p.prepare() } catch (_: Throwable) {} }, 3000)
                    }
                })
                val ms = RtspMediaSource.Factory().setForceUseRtpTcp(true).setTimeoutMs(8000).createMediaSource(MediaItem.fromUri(Uri.parse(url)))
                p.setMediaSource(ms)
                attach(p, 1280, 720)
                p.playWhenReady = true
                p.prepare()
                player = p
            } catch (e: Throwable) { Engine.note("Không mở được luồng RTSP: ${e.javaClass.simpleName}") }
        }
    }

    /** Tạo ImageReader đúng cỡ hình rồi đưa mặt vẽ cho bộ giải mã. */
    private fun attach(p: ExoPlayer, w: Int, hh: Int) {
        val rd = ImageReader.newInstance(w, hh, ImageFormat.YUV_420_888, 3)
        rd.setOnImageAvailableListener({ r ->
            val img = try { r.acquireLatestImage() } catch (_: Exception) { null } ?: return@setOnImageAvailableListener
            val at = SystemClock.elapsedRealtime()
            val bmp = try { yuvToBitmap(img) } catch (_: Throwable) { null } finally { img.close() }
            if (bmp != null) Engine.feed(bmp, at)
        }, h)
        p.setVideoSurface(rd.surface)
        val old = reader; reader = rd
        try { old?.close() } catch (_: Throwable) {}
    }

    override fun stop() {
        run = false
        main.post {
            try { player?.release() } catch (_: Throwable) {}
            player = null
            try { reader?.close() } catch (_: Throwable) {}
            reader = null
            ht.quitSafely()
        }
    }

    /** YUV_420_888 sang Bitmap, thu nhỏ khi hình rộng hơn 960 điểm ảnh để xử lý nhanh. */
    private fun yuvToBitmap(img: Image): Bitmap {
        val w = img.width; val hgt = img.height
        val step = (w + 959) / 960
        val ow = w / step; val oh = hgt / step
        val yp = img.planes[0]; val up = img.planes[1]; val vp = img.planes[2]
        val yb = yp.buffer; val ub = up.buffer; val vb = vp.buffer
        val px = IntArray(ow * oh)
        for (y in 0 until oh) {
            val sy = y * step
            for (x in 0 until ow) {
                val sx = x * step
                val yy = yb.get(sy * yp.rowStride + sx * yp.pixelStride).toInt() and 0xFF
                val u = (ub.get((sy / 2) * up.rowStride + (sx / 2) * up.pixelStride).toInt() and 0xFF) - 128
                val v = (vb.get((sy / 2) * vp.rowStride + (sx / 2) * vp.pixelStride).toInt() and 0xFF) - 128
                val r = (yy + ((359 * v) shr 8)).coerceIn(0, 255)
                val g = (yy - ((88 * u + 183 * v) shr 8)).coerceIn(0, 255)
                val b = (yy + ((454 * u) shr 8)).coerceIn(0, 255)
                px[y * ow + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return Bitmap.createBitmap(px, ow, oh, Bitmap.Config.ARGB_8888)
    }
}
