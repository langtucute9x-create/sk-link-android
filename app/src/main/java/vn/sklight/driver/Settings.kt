package vn.sklight.driver

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
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

        card(left, "Nguồn hình",
            Ui.row(act, "Camera điện thoại", "Camera sau, gắn ngang trên kính lái", null),
            Ui.row(act, "Camera USB, camera hành trình Wi-Fi", "Dùng khi lắp trên màn hình xe", Ui.soon(act)))

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
