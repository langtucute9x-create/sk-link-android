package vn.sklight.driver

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.app.AlertDialog
import android.widget.Button
import android.widget.EditText
import android.text.InputType
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread
import kotlin.math.roundToInt

/**
 * Cập nhật ứng dụng: so mã phiên bản với bản mới nhất trên GitHub Releases (thẻ sk-driver-latest), tải APK rồi mở trình cài đặt của máy.
 * Kiểu nút đổi dần theo tiến độ tham khảo kho Lucian UX/UI Prompt #004 (MIT License (c) 2026 Seokwoooo, https://lucianlabs.dev/uxui-prompt/).
 */
object Updater {
    private const val BASE = "https://github.com/langtucute9x-create/sk-link-android/releases/download/sk-driver-latest/"

    private fun open(url: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 30000; c.instanceFollowRedirects = true
        return c
    }

    /** Trả về (mã phiên bản, tên phiên bản) của bản mới nhất; lỗi mạng thì ném ngoại lệ. */
    fun latest(): Pair<Long, String> {
        val c = open(BASE + "sk-driver-version.txt")
        try {
            if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
            val s = c.inputStream.bufferedReader().readText().trim().split("|")
            return (s[0].trim().toLong()) to (s.getOrNull(1)?.trim() ?: "")
        } finally { c.disconnect() }
    }

    fun download(dir: File, onPct: (Int) -> Unit): File {
        dir.mkdirs()
        val out = File(dir, "sk-driver.apk")
        val c = open(BASE + "sk-driver.apk")
        try {
            if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
            val total = c.contentLengthLong
            var done = 0L; var last = -1
            c.inputStream.use { ins -> out.outputStream().use { os ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = ins.read(buf); if (n < 0) break
                    os.write(buf, 0, n); done += n
                    if (total > 0) { val pct = (done * 100 / total).toInt(); if (pct != last) { last = pct; onPct(pct) } }
                }
            } }
            if (out.length() < 1_000_000L) throw IllegalStateException("file quá nhỏ")
            return out
        } finally { c.disconnect() }
    }
}

/** Màn Cài đặt. */
@SuppressLint("ViewConstructor")
class SettingsScreen(private val act: MainActivity, onDrive: () -> Unit, onCalib: () -> Unit) : LinearLayout(act) {
    private val cfg = act.cfg
    private var upState = 0 // 0 chưa kiểm tra, 1 đang kiểm tra, 2 có bản mới, 3 đang tải, 4 đã tải xong
    private var apk: File? = null
    private lateinit var upSub: TextView
    private lateinit var upBtn: Button
    private lateinit var urlBox: EditText
    private lateinit var srcStatus: TextView
    private lateinit var srcHint: TextView
    private lateinit var probeMsg: TextView
    private lateinit var srcSeg: LinearLayout
    private val tick = object : Runnable {
        override fun run() {
            if (visibility != VISIBLE) return
            srcStatus.text = if (!DriverService.running) "Chưa chạy" else Engine.status.ifEmpty { "Đang kết nối…" } + (if (Engine.frames > 0) " · ${Engine.frames} khung hình" else "")
            postDelayed(this, 700)
        }
    }

    /** Gọi mỗi lần mở màn Cài đặt: đồng bộ ô địa chỉ theo nguồn đang chọn và bắt đầu cập nhật trạng thái. */
    fun sync() {
        urlBox.setText(cfg.url)
        urlBox.visibility = if (cfg.src == 0) GONE else VISIBLE
        srcHint.visibility = if (cfg.src == 0) GONE else VISIBLE
        srcHint.text = if (cfg.src == 1) "Địa chỉ luồng MJPEG, ví dụ http://192.168.1.1:8080/?action=stream. Máy phải nối Wi-Fi của camera." else "Địa chỉ RTSP, ví dụ rtsp://192.168.1.1:554/live. Máy phải nối Wi-Fi của camera."
        urlBox.hint = if (cfg.src == 1) "http://…" else "rtsp://…"
        removeCallbacks(tick); post(tick)
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(Ui.BLACK)
        val d = { v: Float -> Ui.dp(act, v) }
        setPadding(d(22f), d(16f), d(22f), 0)
        val head = LinearLayout(act).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(Ui.pill(act, "Lái xe") { cfg.save(); onDrive() }, LayoutParams(-2, d(52f)))
        head.addView(Ui.text(act, "Cài đặt", 26f, Ui.WHITE, true), LayoutParams(0, -2, 1f).apply { leftMargin = d(16f) })
        head.addView(Ui.pill(act, "Cân chỉnh lại") { cfg.save(); onCalib() }, LayoutParams(-2, d(52f)))
        addView(head)

        val cols = LinearLayout(act).apply { orientation = HORIZONTAL }
        val left = LinearLayout(act).apply { orientation = VERTICAL }
        val right = LinearLayout(act).apply { orientation = VERTICAL }
        cols.addView(left, LayoutParams(0, -2, 1f).apply { rightMargin = d(8f) })
        cols.addView(right, LayoutParams(0, -2, 1f).apply { leftMargin = d(8f) })
        fun card(parent: LinearLayout, title: String, vararg rows: View) {
            val c = Ui.card(act)
            c.addView(Ui.text(act, title, 14f, Ui.GREEN, true))
            for (r in rows) c.addView(r)
            parent.addView(c, LayoutParams(-1, -2).apply { topMargin = d(14f) })
        }

        // Nguồn hình: chọn loại camera, nhập địa chỉ luồng (Wi-Fi), nút Thử kết nối. Cân chỉnh lưu riêng cho từng nguồn.
        urlBox = EditText(act).apply {
            setTextColor(Ui.WHITE); setHintTextColor(Ui.MUTED); textSize = 14f; isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            background = Ui.bg(0xFF0A0A0B.toInt(), d(12f).toFloat(), 0x55FFFFFF, d(1f))
            setPadding(d(12f), d(8f), d(12f), d(8f))
        }
        srcStatus = Ui.text(act, "", 13f, Ui.MUTED)
        val testBtn = Ui.pill(act, "Thử kết nối") {
            val t = urlBox.text.toString().trim()
            if (cfg.src == 1) cfg.url1 = t else if (cfg.src == 2) cfg.url2 = t
            act.restartSource()
        }
        srcSeg = Ui.seg(act, listOf("Điện thoại", "Wi-Fi MJPEG", "Wi-Fi RTSP"), cfg.src.coerceIn(0, 2)) {
            val t = urlBox.text.toString().trim()
            if (cfg.src == 1) cfg.url1 = t else if (cfg.src == 2) cfg.url2 = t
            cfg.switchSrc(it); act.restartSource(); sync()
        }
        srcHint = Ui.text(act, "", 12f, Ui.MUTED)
        fun use(p: Probe.Preset) {
            cfg.switchSrc(p.src)
            if (p.src == 1) cfg.url1 = p.url else cfg.url2 = p.url
            act.restartSource(); act.recreateSettings()
        }
        probeMsg = Ui.text(act, "Nối máy vào Wi-Fi của camera trước khi dò.", 12f, Ui.MUTED)
        val probeBtn = Ui.pill(act, "Tự dò camera") {
            if (!Probe.busy) thread {
                val r = Probe.run(act.applicationContext) { m -> post { probeMsg.text = m } }
                post { probeMsg.text = r.msg; r.hit?.let { use(it) } }
            }
        }
        val listBtn = Ui.pill(act, "Chọn mẫu") {
            AlertDialog.Builder(act).setTitle("Dòng camera").setItems(Probe.LIST.map { it.name }.toTypedArray()) { _, i -> use(Probe.LIST[i]) }.show()
        }
        val probeRow = LinearLayout(act).apply { orientation = HORIZONTAL; setPadding(0, d(8f), 0, d(4f)) }
        probeRow.addView(probeBtn, LayoutParams(-2, d(46f)))
        probeRow.addView(listBtn, LayoutParams(-2, d(46f)).apply { leftMargin = d(10f) })
        card(left, "Nguồn hình",
            Ui.row(act, "Loại camera", "Cân chỉnh được lưu riêng cho từng nguồn hình", null),
            srcSeg,
            probeRow, probeMsg,
            urlBox, srcHint,
            Ui.row(act, "Trạng thái", "", testBtn),
            srcStatus,
            Ui.row(act, "Camera USB (UVC)", "Cắm thẳng vào màn hình xe hoặc box", Ui.soon(act)))

        val gaps = listOf(1.5f, 2f, 3f); val starts = listOf(30, 40, 60)
        card(left, "Cảnh báo khoảng cách",
            Ui.row(act, "Khoảng cách tối thiểu", "Tính theo giây ở tốc độ đang chạy",
                Ui.seg(act, listOf("1,5 s", "2 s", "3 s"), gaps.indexOfFirst { it >= cfg.gapSec - 0.01f }.coerceAtLeast(0)) { cfg.gapSec = gaps[it]; cfg.save() }),
            Ui.row(act, "Bắt đầu cảnh báo từ", "Dưới tốc độ này chỉ hiện số, không báo",
                Ui.seg(act, listOf("30", "40", "60 km/h"), starts.indexOf(cfg.startKmh).coerceAtLeast(0)) { cfg.startKmh = starts[it]; cfg.save() }))

        // chọn loại vật thể: bấm để bật tắt, Ô tô luôn bật
        val chips = LinearLayout(act).apply { orientation = HORIZONTAL }
        Kind.GROUPS.forEachIndexed { i, name ->
            val b = Button(act).apply { isAllCaps = false; textSize = 13f; stateListAnimator = null; minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0; setPadding(d(6f), 0, d(6f), 0) }
            fun paint() {
                val on = (cfg.kinds shr i) and 1 == 1 || i == 0
                b.text = (if (on) "✓ " else "") + name
                b.background = Ui.bg(if (on) 0x26A6F08F else 0xFF0A0A0B.toInt(), d(22f).toFloat(), if (on) Ui.GREEN else 0x55FFFFFF, d(1f))
                b.setTextColor(if (on) Ui.GREEN else Ui.WHITE)
            }
            b.setOnClickListener { if (i != 0) { cfg.kinds = cfg.kinds xor (1 shl i); cfg.save(); paint() } }
            paint()
            chips.addView(b, LayoutParams(0, d(44f), 1f).apply { if (i > 0) leftMargin = d(6f) })
        }
        card(left, "Vật thể nhận diện", chips.apply { setPadding(0, d(8f), 0, d(4f)) },
            Ui.text(act, "Cây cối và vật cản lạ: mô hình hiện tại chưa nhận diện được. Người, xe đạp, chó mèo ở xa có thể bị sót.", 12f, Ui.MUTED))

        card(right, "Cách báo",
            Ui.row(act, "Tiếng báo khi quá gần", "Một tiếng bíp ngắn, nhắc lại mỗi 2 giây", Ui.switch(act, cfg.beep) { cfg.beep = it; cfg.save() }),
            Ui.row(act, "Gửi tín hiệu sang SK Light", "Chọn màu, khu vực và bật nháy đèn trong SK Light, trang Khoảng cách", Ui.switch(act, cfg.link) { cfg.link = it; cfg.save() }),
            Ui.row(act, "Cửa sổ nổi trên app bản đồ", "Hiện số mét khi đang mở app khác", Ui.soon(act)))

        upBtn = Ui.pill(act, "Kiểm tra") { onUpdateClick() }
        val upRow = Ui.row(act, "Cập nhật ứng dụng", "", upBtn)
        upSub = Ui.text(act, "Đang dùng bản ${versionName()}", 12f, Ui.MUTED)
        ((upRow.getChildAt(0)) as LinearLayout).addView(upSub)
        upBtn.layoutParams = LayoutParams(d(150f), d(46f)).apply { leftMargin = d(12f) }
        card(right, "Khác",
            Ui.row(act, "Nhận diện dải giữa hình", "Vật ở xa rõ hơn; vật sát đầu xe (dưới 3 m) bị cắt đáy", Ui.switch(act, cfg.roi) { cfg.roi = it; cfg.save() }),
            Ui.row(act, "Giảm xử lý khi máy nóng", "Tự bỏ bớt một nửa số khung hình", Ui.switch(act, cfg.thermal) { cfg.thermal = it; cfg.save() }),
            Ui.row(act, "Cảnh báo lệch làn", "Đang phát triển, chưa dùng được", Ui.soon(act)),
            Ui.row(act, "Tự mở khi khởi động xe", "Cần quyền Hiện trên ứng dụng khác", Ui.soon(act)),
            upRow)

        val foot = Ui.text(act, "SK Driver ${versionName()} · bản thử. Công cụ hỗ trợ tham khảo, không phải hệ thống an toàn. Người lái luôn tự quan sát và chịu trách nhiệm.", 12f, Ui.MUTED)
        val inner = LinearLayout(act).apply { orientation = VERTICAL }
        inner.addView(cols)
        inner.addView(foot, LayoutParams(-1, -2).apply { topMargin = d(14f); bottomMargin = d(20f) })
        val sv = ScrollView(act).apply { isVerticalScrollBarEnabled = false; addView(inner) }
        addView(sv, LayoutParams(-1, 0, 1f))
    }

    private fun versionName(): String = try { act.packageManager.getPackageInfo(act.packageName, 0).versionName ?: "?" } catch (_: Throwable) { "?" }
    private fun versionCode(): Long = try { act.packageManager.getPackageInfo(act.packageName, 0).longVersionCode } catch (_: Throwable) { 0L }

    private fun setUp(state: Int, sub: String, btn: String) {
        upState = state; upSub.text = sub; upBtn.text = btn
        Ui.stylePill(upBtn, state == 2 || state == 4, if (state == 4) Ui.GREEN else Ui.WHITE)
        upBtn.isEnabled = state != 1 && state != 3
    }

    private fun onUpdateClick() {
        when (upState) {
            0 -> {
                setUp(1, "Đang kiểm tra bản mới…", "Đang kiểm tra")
                thread {
                    try {
                        val (code, name) = Updater.latest()
                        post { if (code > versionCode()) setUp(2, "Có bản $name", "Tải bản mới") else setUp(0, "Đang dùng bản mới nhất (${versionName()})", "Kiểm tra") }
                    } catch (e: Throwable) { post { setUp(0, "Không kiểm tra được, xem lại mạng (${e.message ?: e.javaClass.simpleName})", "Thử lại") } }
                }
            }
            2 -> {
                setUp(3, "Đang tải 0%", "0%")
                thread {
                    try {
                        val f = Updater.download(File(act.cacheDir, "upd")) { pct -> post { upSub.text = "Đang tải $pct%"; upBtn.text = "$pct%" } }
                        apk = f
                        post { setUp(4, "Đã tải xong (${(f.length() / 1048576f).roundToInt()} MB), bấm để cài", "Cài đặt") }
                    } catch (e: Throwable) { post { setUp(2, "Tải lỗi, thử lại (${e.message ?: e.javaClass.simpleName})", "Tải lại") } }
                }
            }
            4 -> install()
        }
    }

    private fun install() {
        val f = apk ?: return
        try {
            if (!act.packageManager.canRequestPackageInstalls()) {
                // máy chưa cho SK Driver cài ứng dụng: mở đúng trang cấp quyền, xong quay lại bấm Cài đặt lần nữa
                upSub.text = "Bật \"Cho phép từ nguồn này\" rồi quay lại bấm Cài đặt"
                act.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + act.packageName)))
                return
            }
            val uri = FileProvider.getUriForFile(act, act.packageName + ".files", f)
            act.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Throwable) { upSub.text = "Không mở được trình cài đặt: ${e.javaClass.simpleName}" }
    }
}
