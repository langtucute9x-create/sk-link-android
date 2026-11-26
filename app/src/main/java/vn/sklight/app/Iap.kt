package vn.sklight.app

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Nâng cấp phần mềm CAN của hộp qua vi điều khiển (tệp .upg). Làm đúng theo app gốc LAMPFRGN 1.2.7
 * (lớp IapUpgradeCmd và IapUpgradeViewModel.ota, đọc từ mã app gốc ngày 26/11/2026), không có lệnh nào tự đoán:
 *
 * Khung: 2E | D9 | độ dài | loại | dữ liệu | ck.
 *   App gửi:  06 hỏi phiên bản bộ nạp, 07 hỏi phiên bản phần mềm, 01 5E 6A EA 87 xin nâng cấp,
 *             81 sẵn sàng (đã có tệp), 82 chưa sẵn sàng, 83 | vị trí (3 byte, byte cao trước) | dữ liệu tệp.
 *   Hộp gửi:  01 yêu cầu nâng cấp, 02 | số byte | vị trí (3 byte) = xin một đoạn tệp, 83 | phần trăm,
 *             84 | lỗi | tham số 1 | tham số 2, 85 xong, 86 chuỗi phiên bản bộ nạp, 87 chuỗi phiên bản phần mềm.
 * Hộp tự hỏi từng đoạn tệp và tự kiểm tra tệp (lỗi 5 = sai phiên bản); app chỉ trả đúng đoạn được hỏi, gửi nguyên văn tệp (không giải mã).
 * CHƯA thử trên hộp thật. Nạp giữa chừng mà mất kết nối thì theo app gốc hộp sẽ tự gửi lại loại 01 khi nối lại, nạp tiếp được.
 */
object Iap {
    private val h = Handler(Looper.getMainLooper())
    @Volatile var busy = false                     // đang nạp: BleLed chặn mọi lệnh khác
    var status by mutableStateOf("")
    var iapVer by mutableStateOf("")
    var appVer by mutableStateOf("")
    val progress = mutableIntStateOf(-1)
    val log = mutableStateListOf<String>()
    var fileName by mutableStateOf("")
    private var file: ByteArray? = null
    private var lastRx = 0L
    private var chunks = 0

    private fun add(s: String) { log.add(0, s); while (log.size > 60) log.removeAt(log.size - 1) }
    private fun hex(b: ByteArray, n: Int = 12) = b.take(n).joinToString(" ") { "%02X".format(it) } + if (b.size > n) " …" else ""

    fun setFile(name: String, data: ByteArray?) { fileName = name; file = data; if (data != null) add("Tệp: $name, ${data.size} byte") }
    fun hasFile() = (file?.size ?: 0) > 1024

    /** Hỏi phiên bản bộ nạp và phiên bản phần mềm CAN (chỉ đọc). */
    fun askVersions() {
        if (!S.connected.value) return
        S.ble.send(Protocol.frame(0xD9, 0x06)); S.ble.send(Protocol.frame(0xD9, 0x07))
        add("Đã hỏi phiên bản")
    }

    fun start() {
        val f = file
        if (!S.connected.value) { status = "Chưa kết nối hộp đèn"; return }
        if (f == null || !hasFile()) { status = "Chưa chọn tệp .upg"; return }
        busy = true; progress.intValue = 0; chunks = 0; lastRx = System.currentTimeMillis()
        status = "Đã gửi yêu cầu nâng cấp, chờ hộp trả lời…"
        add("Bắt đầu: $fileName (${f.size} byte), MTU ${S.ble.mtu}")
        S.ble.send(Protocol.frame(0xD9, 0x01, 0x5E, 0x6A, 0xEA, 0x87))
        h.removeCallbacks(watch); h.postDelayed(watch, 3000)
    }

    fun cancel() {
        if (!busy) return
        busy = false; h.removeCallbacks(watch)
        status = "Đã dừng gửi. Nếu hộp đang chờ dữ liệu, hộp sẽ báo lỗi hết giờ; nối lại rồi nạp lại từ đầu."
        add("Người dùng dừng")
    }

    private val watch = object : Runnable {
        override fun run() {
            if (!busy) return
            val idle = System.currentTimeMillis() - lastRx
            if (!S.connected.value) { status = "Mất kết nối khi đang nạp. Nối lại hộp rồi bấm Bắt đầu để nạp tiếp."; add("Mất kết nối"); busy = false; return }
            if (idle > 20000) { status = "Hộp không trả lời 20 giây. Đã dừng. Kiểm tra hộp có hỗ trợ nâng cấp CAN không."; add("Hết giờ chờ hộp"); busy = false; return }
            h.postDelayed(this, 2000)
        }
    }

    private fun err(c: Int) = when (c) {
        1 -> "hộp chờ dữ liệu quá 5 giây"; 2 -> "khung sai (thiếu byte)"; 5 -> "tệp không đúng phiên bản / loại hộp"
        3, 4, 6, 7, 8, 9, 10, 13 -> "dữ liệu tệp lỗi (mã $c)"; 11, 12 -> "hộp ghi bộ nhớ thất bại (mã $c)"; 14 -> "địa chỉ ngoài phạm vi"; else -> "mã $c"
    }

    /** Khung D9 hộp gửi về (gọi trên luồng chính). */
    fun onFrame(d: ByteArray) {
        if (d.size < 5) return
        val n = minOf((d[2].toInt() and 0xFF), d.size - 4)
        if (n < 1) return
        val p = IntArray(n) { d[3 + it].toInt() and 0xFF }
        lastRx = System.currentTimeMillis()
        fun text() = try { val raw = d.copyOfRange(4, 3 + n); String(raw, 0, raw.indexOf(0).let { if (it < 0) raw.size else it }, Charsets.UTF_8) } catch (_: Exception) { "" }
        when (p[0]) {
            0x86 -> { iapVer = text(); add("Phiên bản bộ nạp: $iapVer") }
            0x87 -> { appVer = text(); add("Phiên bản phần mềm CAN: $appVer") }
            0x01 -> {
                if (busy && hasFile()) { S.ble.send(Protocol.frame(0xD9, 0x81)); status = "Hộp nhận yêu cầu, đang chờ hộp xin dữ liệu…"; add("Hộp: yêu cầu nâng cấp → trả lời sẵn sàng") }
                else add("Hộp đang chờ nâng cấp (chưa bấm Bắt đầu nên app không trả lời)")
            }
            0x81 -> add("Hộp: có thể nâng cấp")
            0x82 -> { add("Hộp: không thể nâng cấp"); if (busy) { status = "Hộp báo không thể nâng cấp."; busy = false } }
            0x02 -> {
                val f = file
                if (!busy || f == null || n < 5) return
                val len = p[1]; val off = (p[2] shl 16) or (p[3] shl 8) or p[4]
                val max = S.ble.mtu - 3 - 8          // khung = 2E D9 dài 83 + 3 byte vị trí + dữ liệu + ck
                if (len > max) { status = "Hộp xin $len byte mỗi lần nhưng kết nối chỉ gửi được $max byte. Đã dừng, chưa ghi gì thêm."; add("Đoạn quá dài so với MTU ${S.ble.mtu}"); busy = false; return }
                val out = IntArray(4 + len)
                out[0] = 0x83; out[1] = (off shr 16) and 0xFF; out[2] = (off shr 8) and 0xFF; out[3] = off and 0xFF
                for (i in 0 until len) out[4 + i] = if (off + i < f.size) f[off + i].toInt() and 0xFF else 0      // app gốc: đọc quá cuối tệp thì là 0
                S.ble.send(Protocol.frame(0xD9, *out))
                chunks++
                if (progress.intValue < 0 || chunks % 8 == 1) status = "Đang gửi dữ liệu: ${minOf(off + len, f.size)} / ${f.size} byte"
                if (chunks % 40 == 1) add("Hộp xin $len byte tại $off")
            }
            0x83 -> { if (n >= 2) { progress.intValue = p[1].coerceIn(0, 100); status = "Đang nâng cấp ${progress.intValue}%" } }
            0x84 -> {
                val e0 = p.getOrElse(1) { 0 }
                if (e0 == 0 || e0 == 15) { add("Hộp: 84 (không lỗi)"); return }
                status = "Nâng cấp lỗi: ${err(e0)} (${e0}_${p.getOrElse(2) { 0 }}_${p.getOrElse(3) { 0 }})"
                add(status); busy = false; h.removeCallbacks(watch)
            }
            0x85 -> { progress.intValue = 100; status = "Nâng cấp xong. Chờ vài giây rồi tắt bật lại hộp và nối lại Bluetooth."; add("Hộp: nâng cấp xong"); busy = false; h.removeCallbacks(watch) }
            else -> add("Hộp: " + hex(d))
        }
    }
}
