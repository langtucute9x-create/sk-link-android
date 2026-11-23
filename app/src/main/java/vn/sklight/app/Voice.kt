package vn.sklight.app

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Ra lệnh bằng giọng nói, không dùng AI: bộ nhận giọng nói có sẵn của máy đổi lời nói thành chữ,
 * rồi so với bảng lệnh cố định. Trạng thái (S.voiceState): 0 ẩn, 1 đang nghe, 2 đã làm, 3 chưa có lệnh,
 * 4 bảng câu lệnh, 5 máy chưa có bộ nhận giọng nói, 6 lỗi.
 */
object Voice {
    private var sr: SpeechRecognizer? = null
    private val h = Handler(Looper.getMainLooper())
    private var gen = 0                              // lượt nghe hiện tại; trả lời của lượt cũ bị bỏ qua
    val phase = mutableIntStateOf(0)                 // 0 đang mở micro, 1 sẵn sàng nghe, 2 đang nghe thấy tiếng, 3 đang xử lý
    val level = mutableFloatStateOf(0f)              // độ lớn tiếng thu được, 0..1
    var engine = ""                                  // gói của bộ nhận giọng nói đang dùng (để báo khi lỗi)
    private val mutedStreams = ArrayList<Int>()      // các luồng âm thanh app đang tạm tắt tiếng trong lúc nghe

    /**
     * Tắt tiếng "tinh tinh" của bộ nhận giọng nói: bộ nhận không có cách tắt riêng, nên app tạm tắt tiếng luồng nhạc
     * và luồng hệ thống từ lúc bắt đầu nghe tới khi có kết quả. Chỉ đụng tới luồng đang có tiếng, xong thì bật lại đúng các luồng đó.
     */
    private fun quiet(on: Boolean) {
        try {
            val am = S.app.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (on) {
                if (!S.voiceQuiet.value || mutedStreams.isNotEmpty()) return
                for (st in intArrayOf(AudioManager.STREAM_MUSIC, AudioManager.STREAM_SYSTEM)) {
                    try { if (!am.isStreamMute(st)) { am.adjustStreamVolume(st, AudioManager.ADJUST_MUTE, 0); mutedStreams.add(st) } } catch (e: Exception) {}
                }
            } else {
                for (st in mutedStreams) try { am.adjustStreamVolume(st, AudioManager.ADJUST_UNMUTE, 0) } catch (e: Exception) {}
                mutedStreams.clear()
            }
        } catch (e: Exception) {}
    }

    /** Có kết quả: chờ tiếng báo kết thúc của bộ nhận trôi qua rồi mới bật tiếng lại và xử lý (để câu trả lời nghe được). */
    private fun settle(my: Int, block: () -> Unit) {
        if (mutedStreams.isEmpty()) { block(); return }
        h.postDelayed({ quiet(false); if (my == gen) block() }, 350)
    }

    private fun results(c: Context, list: List<String>) {
        for (cand in list) { val m = run(cand); if (m != null) { answer(c, cand, m); return } }
        if (list.isEmpty()) { show(6, msg = "Không nghe thấy câu lệnh."); Speaker.say(c, "Em chưa nghe rõ") }
        else { show(3, list[0]); Speaker.say(c, "Em chưa có lệnh này") }
    }

    // tên dài đứng trước để "xanh lá" không bị hiểu thành "xanh". Tên màu theo tờ "Hướng dẫn điều khiển giọng nói" của SK:
    // "xanh lam" = xanh dương đậm, "xanh da trời" = xanh nhạt.
    private val COLORS: List<Triple<List<String>, Int, String>> = listOf(
        Triple(listOf("xanh lá", "xanh lục", "lá cây"), 0x41E01F, "xanh lá"),
        Triple(listOf("xanh da trời", "xanh ngọc", "xanh nhạt"), 0x17F4EA, "xanh da trời"),
        Triple(listOf("xanh lam", "xanh dương", "xanh biển", "xanh nước biển", "xanh"), 0x4E78FF, "xanh lam"),
        Triple(listOf("đỏ"), 0xFF2A48, "đỏ"), Triple(listOf("cam"), 0xFF7A1A, "cam"), Triple(listOf("vàng"), 0xFFE114, "vàng"),
        Triple(listOf("tím"), 0x8A00FF, "tím"), Triple(listOf("trắng"), 0xFFFFFF, "trắng"), Triple(listOf("hồng"), 0xFE2CAA, "hồng")
    )

    /** Mục 3 của tờ hướng dẫn: số gọi ("chế độ một"), id hiệu ứng của hộp, các tên gọi. Tờ hướng dẫn không có số mười. */
    private val GUIDE: List<Triple<List<String>, Int, List<String>>> = listOf(
        Triple(listOf("một", "1"), 6, listOf("thể thao")),
        Triple(listOf("hai", "2"), 7, listOf("ma thuật")),
        Triple(listOf("ba", "3"), 13, listOf("đuôi sao chổi", "sao chổi")),
        Triple(listOf("bốn", "4", "tư"), 15, listOf("xuân về hoa nở", "xuân về")),
        Triple(listOf("năm", "5"), 18, listOf("khúc tình ca mùa đông", "tình ca mùa đông")),
        Triple(listOf("sáu", "6"), 19, listOf("nhảy theo nhạc")),
        Triple(listOf("bảy", "bẩy", "7"), 52, listOf("mây trôi nước chảy", "mây trôi")),
        Triple(listOf("tám", "8"), 53, listOf("xếp chồng")),
        Triple(listOf("chín", "9"), 50, listOf("nhịp điệu năng động")),
        Triple(listOf("mười một", "11"), 12, listOf("cuộn đủ màu")),
        Triple(listOf("mười hai", "12"), 51, listOf("chiếu sáng năng động"))
    )

    /** Việc cần làm sau khi đọc xong câu trả lời: 0 không, 1 nghe tiếp (vừa được gọi tên), 2 đóng thẻ, 3 đổi giọng đọc. */
    var after = 0

    /** [word] xuất hiện trong [t] thành cụm từ riêng (không dính vào chữ khác). */
    private fun has(t: String, word: String) = Regex("(^|\\s)" + Regex.escape(word) + "(\\s|$)").containsMatchIn(t)

    private fun pick(e: Effect): String { S.pickEffect(e); return "Em đã chuyển sang chế độ ${e.name} rồi" }

    /** Bỏ dấu tiếng Việt, để so tên khi máy nghe sai dấu hoặc sai chữ gần âm ("cuộn" thành "quận", "cuốn"). */
    private fun fold(x: String): String =
        java.text.Normalizer.normalize(x.lowercase().replace('đ', 'd'), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").replace(Regex("\\s+"), " ").trim()

    private fun lev(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1); cur[0] = i
            for (j in 1..b.length) cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            prev = cur
        }
        return prev[b.length]
    }

    /**
     * Tìm chế độ có tên gần giống nhất với một cụm trong câu [t] (so không dấu, cho sai tối đa khoảng 1/4 số chữ).
     * Chỉ xét tên dài từ 6 chữ cái trở lên để không bắt nhầm các từ ngắn.
     */
    private fun fuzzyMode(t: String): Effect? {
        val words = fold(t).split(" ").filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        // có chữ "chế độ" / "hiệu ứng" thì cho sai nhiều hơn một chút
        val joined = words.joinToString(" ")
        val limit = if (joined.contains("che do") || joined.contains("hieu ung")) 0.26f else 0.2f
        val cands = ArrayList<Pair<String, Effect>>()
        for (g in GUIDE) Effects.byId(g.second)?.let { e -> for (n in g.third) cands.add(fold(n) to e) }
        for (e in Effects.all) cands.add(fold(e.name) to e)
        var best: Effect? = null; var bestScore = 1f; var bestLen = 0
        for ((name, e) in cands) {
            if (name.replace(" ", "").length < 6) continue
            val k = name.split(" ").size
            for (n in listOf(k, k - 1, k + 1)) {
                if (n < 1 || n > words.size) continue
                for (i in 0..words.size - n) {
                    val w = words.subList(i, i + n).joinToString(" ")
                    val d = lev(w, name)
                    val score = d.toFloat() / name.length
                    if (d <= 3 && score <= limit && (score < bestScore || (score == bestScore && name.length > bestLen))) { best = e; bestScore = score; bestLen = name.length }
                }
            }
        }
        return best
    }
    private fun zn(z: Int) = when (z) { 1 -> " khu vực 1"; 2 -> " khu vực 2"; 0 -> " cả xe"; else -> "" }
    /** Độ sáng hiện tại của khu vực [z] (-1 = khu vực đang chọn trong trang Đèn). */
    private fun curBri(z: Int) = if (z > 0 && S.zone.intValue == 0) S.bri[0] else S.bri[if (z >= 0) z else S.zone.intValue]
    private fun bright(v: Int, z: Int): String {
        if (z >= 0 && z != S.zone.intValue) {
            if (z != 0 && S.zone.intValue == 0) { S.bri[1] = S.bri[0]; S.bri[2] = S.bri[0] }      // đang đồng nhất: khu vực còn lại giữ mức đang sáng
            S.zone.intValue = z
        }
        S.setBright(v)
        return "Em đã chỉnh độ sáng${zn(z)} $v% rồi"
    }
    private fun speed(v: Int): String { S.setSpeed(v); return "Em đã chỉnh tốc độ $v rồi" }

    /** Thực hiện câu lệnh. Trả về câu trả lời (hiện trên thẻ và đọc thành tiếng), hoặc null nếu không có lệnh này. So trên chữ CÓ DẤU. */
    fun run(raw: String): String? {
        after = 0
        var t = raw.lowercase().replace(Regex("[.,!?]"), " ").replace(Regex("\\s+"), " ").trim()
        if (t.isEmpty()) return null
        // các câu không cần hộp đèn
        if (has(t, "hẹn gặp lại") || has(t, "tạm biệt")) { after = 2; return "Hẹn gặp lại ạ" }
        if (has(t, "đổi giọng nói") || has(t, "đổi giọng") || has(t, "thay giọng nói")) { after = 3; return "Em đổi giọng rồi ạ" }
        val called = has(t, "xin chào") || has(t, "này đèn viền") || has(t, "đèn viền ơi")
        if (called && t.split(" ").size <= 5) { after = 1; return "Em nghe đây ạ" }
        if (!S.connected.value) return "Em chưa kết nối với hộp đèn"

        // khu vực nêu trong câu: "khu vực 2 màu đỏ", "vùng 1 sáng hơn", "cả xe màu trắng". z: -1 không nêu, 0 cả xe, 1, 2
        var z = -1
        Regex("(khu vực|khu|vùng) (số )?(1|2|một|hai)(\\s|$)").find(t)?.let { m ->
            z = if (m.groupValues[3] == "1" || m.groupValues[3] == "một") 1 else 2
            t = t.removeRange(m.range).replace(Regex("\\s+"), " ").trim()
        }
        if (z < 0 && (has(t, "cả xe") || has(t, "đồng nhất") || has(t, "toàn bộ") || has(t, "tất cả"))) z = 0
        if (z == 2) {      // chế độ màu của khu vực 2 (tĩnh / tự động / nhấp nháy)
            val v = if (has(t, "tự động")) 1 else if (has(t, "nhấp nháy") || has(t, "chớp")) 2 else if (has(t, "tĩnh") || has(t, "đứng yên")) 0 else -1
            if (v >= 0) {
                if (S.setZ2(v)) return "Em đã chuyển khu vực 2 sang ${listOf("màu tĩnh", "màu tự động", "màu nhấp nháy")[v]} rồi"
                S.refreshBlocks(); return "Em chưa đọc được cài đặt từ hộp đèn"
            }
        }
        val cur = curBri(z)
        Regex("độ sáng (\\d{1,3})").find(t)?.let { return bright(it.groupValues[1].toInt().coerceIn(0, 100), z) }
        Regex("tốc độ (\\d{1,2})").find(t)?.let { return speed(it.groupValues[1].toInt().coerceIn(1, 10)) }
        if (has(t, "tắt đèn") || t == "tắt" || has(t, "tắt hết") || has(t, "màu đen")) { S.setPower(false); return "Em đã tắt đèn rồi" }
        if (has(t, "bật đèn") || has(t, "mở đèn") || t == "bật") { S.setPower(true); return "Em đã bật đèn rồi" }
        if (has(t, "sáng tối đa") || has(t, "sáng nhất")) return bright(100, z)
        if (has(t, "sáng tối thiểu") || has(t, "tối nhất")) return bright(5, z)
        if (has(t, "sáng hơn") || has(t, "tăng sáng") || has(t, "sáng lên")) return bright((cur + 20).coerceAtMost(100), z)
        if (has(t, "tối hơn") || has(t, "giảm sáng") || has(t, "tối lại") || has(t, "dịu")) return bright((cur - 20).coerceAtLeast(5), z)
        if (has(t, "tăng tốc") || has(t, "nhanh hơn")) return speed((S.speed.intValue + 2).coerceAtMost(10))
        if (has(t, "chậm lại") || has(t, "chậm hơn") || has(t, "giảm tốc")) return speed((S.speed.intValue - 2).coerceAtLeast(1))
        if (has(t, "đổi hướng") || has(t, "đảo hướng")) {
            return if (S.flipDir()) "Em đã đổi hướng ánh sáng chạy rồi" else "Em chưa đọc được cài đặt hướng từ hộp đèn"
        }
        if (listOf("đổi hiệu ứng", "đổi chế độ", "chuyển hiệu ứng", "chuyển chế độ").any { t.endsWith(it) }) {
            val l = Effects.all.filter { it.id != 0 }
            return pick(l[(l.indexOfFirst { it.id == S.mode.intValue } + 1) % l.size])
        }
        // tờ hướng dẫn của SK: "chế độ một" … "chế độ mười hai" và tên riêng của từng chế độ
        for (g in GUIDE) if (g.first.any { has(t, "chế độ $it") || has(t, "chế độ số $it") }) Effects.byId(g.second)?.let { return pick(it) }
        for (g in GUIDE) if (g.third.any { has(t, it) } && (t.contains("chế độ") || t.contains("hiệu ứng"))) Effects.byId(g.second)?.let { return pick(it) }
        if (has(t, "nhịp điệu sôi động") || has(t, "sôi động")) Effects.byId(4)?.let { return pick(it) }
        if (has(t, "nhịp điệu năng động")) Effects.byId(50)?.let { return pick(it) }
        // chế độ gọi kèm "chế độ", "hiệu ứng", "mùa": nhận cả tên một chữ (thở, chớp, xuân…)
        for (e in Effects.all) { val n = e.name.lowercase(); if (has(t, "chế độ $n") || has(t, "hiệu ứng $n") || has(t, "mùa $n")) return pick(e) }
        // chế độ tự đặt: gọi theo tên
        Seqs.list.forEachIndexed { i, q -> if (q.name.isNotBlank() && has(t, q.name.lowercase())) { S.pickAuto(Auto.SEQ_BASE + i); return "Em đã chuyển sang chế độ ${q.name} rồi" } }
        // bối cảnh gọi theo tên
        Scenes.all().forEachIndexed { i, sc -> if (has(t, sc.name.lowercase())) { Scenes.apply(i); return "Em đã chuyển sang bối cảnh ${sc.name} rồi" } }
        // chế độ tên từ hai chữ trở lên: gọi thẳng tên
        for (e in Effects.all) { val n = e.name.lowercase(); if (n.contains(' ') && has(t, n)) return pick(e) }
        for (g in GUIDE) if (g.third.any { has(t, it) }) Effects.byId(g.second)?.let { return pick(it) }
        if (has(t, "mùa hè")) Effects.byId(16)?.let { return pick(it) }
        // máy nghe sai chữ ("quận đủ màu", "cuốn đủ màu", "mai trôi"…): lấy chế độ có tên gần giống nhất
        if (COLORS.none { c -> c.first.any { has(t, it) } }) fuzzyMode(t)?.let { return pick(it) }      // câu có tên màu thì để phần chọn màu bên dưới xử lý
        if (has(t, "bảy màu") || has(t, "7 màu") || has(t, "đủ màu")) Effects.byId(1)?.let { return pick(it) }
        // màu: cần chữ "màu", có nêu khu vực, hoặc câu rất ngắn
        if (z >= 0 || t.contains("màu") || t.split(" ").size <= 3) for (c in COLORS) if (c.first.any { has(t, it) }) {
            if (S.mode.intValue != 0) { S.mode.intValue = 0; S.sendEffect() }      // đang chạy hiệu ứng thì về Đơn sắc để thấy màu
            if (z >= 0) {
                if (z != 0 && S.czone.intValue == 0) { S.col[1] = S.col[0]; S.col[2] = S.col[0] }      // đang đồng nhất: khu vực còn lại giữ màu đang có
                S.czone.intValue = z
            }
            S.setColor(c.second)
            return "Em đã đổi${zn(z)} sang màu ${c.third} rồi"
        }
        return null
    }

    /** Hiện câu trả lời và đọc thành tiếng; đọc xong thì làm tiếp việc trong [after]. */
    private fun answer(c: Context, heard: String, m: String) {
        val a = after
        show(2, heard, m)
        val done: () -> Unit = { if (a == 1) start(c) else if (a == 2) close() }
        if (a == 3) Speaker.changeVoice(c, m) else Speaker.say(c, m, then = done)
    }

    fun close() {
        gen++
        h.removeCallbacksAndMessages(null)
        try { sr?.cancel(); sr?.destroy() } catch (_: Exception) {}
        sr = null
        quiet(false)
        S.voiceState.intValue = 0
    }

    /** Các bộ nhận giọng nói có trong máy, của Google đứng trước (bộ mặc định của một số máy không phản hồi). */
    private fun engines(c: Context): List<ComponentName> {
        val l = try { c.packageManager.queryIntentServices(Intent("android.speech.RecognitionService"), 0) } catch (e: Exception) { emptyList() }
        return l.mapNotNull { it.serviceInfo?.let { s -> ComponentName(s.packageName, s.name) } }
            .sortedByDescending { it.packageName.startsWith("com.google") }
    }

    private fun show(state: Int, heard: String = "", msg: String = "") {
        S.voiceHeard.value = heard; S.voiceMsg.value = msg
        S.voiceState.intValue = state; S.voiceTick.intValue++
    }

    /** Bắt đầu nghe (gọi trên luồng chính, đã có quyền micro). [attempt] = thứ tự bộ nhận giọng nói đang thử. */
    fun start(ctx: Context, attempt: Int = 0, retry: Int = 0) {
        val c = ctx.applicationContext
        val my = ++gen
        h.removeCallbacksAndMessages(null)
        Speaker.stop()
        try { sr?.cancel(); sr?.destroy() } catch (_: Exception) {}
        sr = null
        if (!try { SpeechRecognizer.isRecognitionAvailable(c) } catch (e: Exception) { false }) { show(5); return }
        val list = engines(c)
        val comp = list.getOrNull(attempt)
        engine = comp?.packageName ?: "mặc định của máy"
        phase.intValue = 0; level.floatValue = 0f
        quiet(true)
        try {
            val r = if (comp != null) SpeechRecognizer.createSpeechRecognizer(c, comp) else SpeechRecognizer.createSpeechRecognizer(c)
            r.setRecognitionListener(object : RecognitionListener {
                override fun onResults(b: Bundle?) {
                    if (my != gen) return
                    h.removeCallbacksAndMessages(null)
                    val list2: List<String> = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: emptyList()
                    settle(my) { results(c, list2) }
                }
                override fun onError(code: Int) {
                    if (my != gen) return
                    h.removeCallbacksAndMessages(null)
                    // 11 = dịch vụ nhận giọng nói bị ngắt kết nối giữa chừng, 5 = lỗi phía máy khách, 8 = đang bận: tạo lại bộ nhận và thử một lần nữa.
                    if ((code == 11 || code == 5 || code == 8) && retry < 1) {
                        try { sr?.cancel(); sr?.destroy() } catch (_: Exception) {}
                        sr = null
                        h.postDelayed({ if (my == gen) start(c, attempt, retry + 1) }, 500)
                        return
                    }
                    settle(my) {
                        if (code == SpeechRecognizer.ERROR_NO_MATCH || code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) Speaker.say(c, "Em chưa nghe rõ")
                        show(6, msg = when (code) {
                            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Không nghe thấy câu lệnh."
                            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Cần có mạng để nhận giọng nói."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Bộ nhận giọng nói chưa có quyền micro. Vào Cài đặt máy, mục Ứng dụng, chọn Google, bật quyền Micro."
                            12, 13 -> "Bộ nhận giọng nói của máy chưa có tiếng Việt."
                            11 -> "Bộ nhận giọng nói của Google bị ngắt kết nối (mã 11). Bấm mic để thử lại."
                            else -> "Bộ nhận giọng nói báo lỗi (mã $code)."
                        })
                    }
                }
                override fun onReadyForSpeech(p: Bundle?) { if (my == gen) phase.intValue = 1 }
                override fun onBeginningOfSpeech() { if (my == gen) phase.intValue = 2 }
                override fun onRmsChanged(v: Float) { if (my == gen) level.floatValue = ((v + 2f) / 12f).coerceIn(0f, 1f) }
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() { if (my == gen) { phase.intValue = 3; level.floatValue = 0f } }
                override fun onPartialResults(b: Bundle?) {
                    if (my != gen) return
                    val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
                    if (t.isNotBlank()) { S.voiceHeard.value = t; if (phase.intValue < 2) phase.intValue = 2 }
                }
                override fun onEvent(t: Int, b: Bundle?) {}
            })
            r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, c.packageName))
            sr = r
            show(1)
            // Bộ nhận không báo "sẵn sàng" sau 3 giây: thử bộ kế tiếp, hết thì báo lỗi (trước đây thẻ đứng mãi ở "Đang nghe").
            h.postDelayed({
                if (my != gen || phase.intValue != 0) return@postDelayed
                if (attempt + 1 < list.size) start(c, attempt + 1)
                else { close(); show(6, msg = "Bộ nhận giọng nói của máy không phản hồi. Mở app Google một lần, cho phép dùng micro, rồi thử lại.") }
            }, 3000)
            // Quá 12 giây chưa có kết quả: bảo bộ nhận chốt lại; thêm 4 giây nữa vẫn không có thì dừng.
            h.postDelayed({ if (my == gen) try { r.stopListening() } catch (_: Exception) {} }, 12000)
            h.postDelayed({
                if (my != gen) return@postDelayed
                val heard = S.voiceHeard.value
                close()
                if (heard.isNotBlank()) { val m = run(heard); if (m != null) answer(c, heard, m) else { show(3, heard); Speaker.say(c, "Em chưa có lệnh này") } }
                else { show(6, msg = "Không nghe thấy câu lệnh."); Speaker.say(c, "Em chưa nghe rõ") }
            }, 16000)
        } catch (e: Exception) { quiet(false); show(6, msg = "Không mở được bộ nhận giọng nói của máy.") }
    }
}

/**
 * Trả lời bằng tiếng nói: bộ đọc chữ thành tiếng có sẵn của máy (không dùng AI).
 * Bộ đọc mặc định của máy không có tiếng Việt thì thử bộ đọc của Google. Không có giọng tiếng Việt thì app im lặng
 * (chỉ hiện chữ trên thẻ) và báo một lần; cách cài nằm ở Cài đặt > Giọng nói AI.
 */
object Speaker {
    private const val GOOGLE = "com.google.android.tts"
    private val h = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    val state = mutableIntStateOf(0)                 // 0 chưa mở, 1 đang mở, 2 dùng được, 3 máy không có giọng tiếng Việt
    var engine = ""                                  // gói của bộ đọc đang dùng
    private var triedGoogle = false
    private var waiting: ((TextToSpeech?) -> Unit)? = null
    private var onDone: (() -> Unit)? = null
    private var seq = 0
    private var told = false

    private fun open(c: Context, pkg: String?) {
        try {
            tts = if (pkg == null) TextToSpeech(c) { st -> h.post { opened(c, st) } } else TextToSpeech(c, { st -> h.post { opened(c, st) } }, pkg)
        } catch (e: Exception) { fail() }
    }

    private fun fail() {
        try { tts?.shutdown() } catch (e: Exception) {}
        tts = null
        state.intValue = 3
        val w = waiting; waiting = null
        w?.invoke(null)
    }

    private fun use(ctx: Context, block: (TextToSpeech?) -> Unit) {
        when (state.intValue) {
            2 -> block(tts)
            3 -> block(null)
            1 -> waiting = block
            else -> { state.intValue = 1; waiting = block; triedGoogle = false; open(ctx.applicationContext, null) }
        }
    }

    private fun opened(c: Context, st: Int) {
        val e = tts
        var ok = false
        if (st == TextToSpeech.SUCCESS && e != null) {
            val r = try { e.setLanguage(Locale("vi", "VN")) } catch (x: Exception) { TextToSpeech.LANG_NOT_SUPPORTED }
            ok = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
        }
        if (!ok || e == null) {
            val def = try { e?.defaultEngine } catch (x: Exception) { null }
            try { e?.shutdown() } catch (x: Exception) {}
            tts = null
            if (!triedGoogle && def != GOOGLE) { triedGoogle = true; open(c, GOOGLE) } else fail()
            return
        }
        engine = if (triedGoogle) GOOGLE else (try { e.defaultEngine } catch (x: Exception) { null } ?: "")
        state.intValue = 2
        try {
            val want = S.ttsVoice
            if (want.isNotEmpty()) e.voices?.firstOrNull { it.name == want }?.let { e.voice = it }
        } catch (x: Exception) {}
        e.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { finished(id) }
            @Deprecated("") override fun onError(id: String?) { finished(id) }
        })
        val w = waiting; waiting = null
        w?.invoke(e)
    }

    private fun finished(id: String?) {
        h.post { if (id == "sk$seq") { val d = onDone; onDone = null; d?.invoke() } }
    }

    private fun noVoice(ctx: Context) {
        if (told) return
        told = true
        Toast.makeText(ctx.applicationContext, L.tr("Máy chưa có giọng đọc tiếng Việt nên app chưa trả lời bằng tiếng được. Vào Cài đặt, mục Giọng nói AI để cài."), Toast.LENGTH_LONG).show()
    }

    private fun speak(e: TextToSpeech, text: String, then: (() -> Unit)?) {
        onDone = then
        val id = "sk${++seq}"
        val r = try { e.speak(text.replace("%", " phần trăm"), TextToSpeech.QUEUE_FLUSH, null, id) } catch (x: Exception) { TextToSpeech.ERROR }
        if (r != TextToSpeech.SUCCESS) { onDone = null; then?.invoke() }
    }

    /** Đọc [text]; đọc xong (hoặc không đọc được) thì gọi [then]. [force]: đọc cả khi đã tắt công tắc trả lời (nút Nghe thử). */
    fun say(ctx: Context, text: String, force: Boolean = false, then: (() -> Unit)? = null) {
        if (!force && !S.voiceReply.value) { if (then != null) h.postDelayed({ then() }, 1200); return }      // đã tắt trả lời bằng tiếng: chỉ hiện chữ trên thẻ
        use(ctx) { e ->
            if (e != null) speak(e, text, then)
            else { noVoice(ctx); if (then != null) h.postDelayed({ then() }, 1200) }
            Unit
        }
    }

    /** Chuyển sang giọng tiếng Việt kế tiếp của máy rồi đọc [text] bằng giọng mới. */
    fun changeVoice(ctx: Context, text: String) {
        use(ctx) { e ->
            if (e == null) { noVoice(ctx); return@use }
            val l = try { e.voices?.filter { it.locale.language == "vi" && !it.features.contains("notInstalled") }?.sortedBy { it.name } } catch (x: Exception) { null } ?: emptyList()
            if (l.size < 2) { speak(e, "Máy này chỉ có một giọng tiếng Việt", null); return@use }
            val cur = try { e.voice?.name } catch (x: Exception) { null }
            val v = l[(l.indexOfFirst { it.name == cur } + 1) % l.size]
            try { e.voice = v; S.ttsVoice = v.name } catch (x: Exception) {}
            speak(e, text, null)
        }
    }

    /** Mở bộ đọc để biết máy có giọng tiếng Việt không (cho trang Giọng nói AI). */
    fun probe(ctx: Context) { if (state.intValue == 0) use(ctx) { } }

    /** Đóng bộ đọc để lần sau dò lại từ đầu (sau khi người dùng cài thêm giọng). */
    fun reset() {
        onDone = null; waiting = null; told = false
        try { tts?.shutdown() } catch (e: Exception) {}
        tts = null
        state.intValue = 0
    }

    /** Mở chỗ cài giọng đọc tiếng Việt: tải dữ liệu giọng của Google, không được thì mở cài đặt đọc chữ của máy, rồi CH Play. */
    fun install(ctx: Context) {
        val tries = listOf(
            Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).setPackage(GOOGLE),
            Intent("com.android.settings.TTS_SETTINGS"),
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$GOOGLE")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$GOOGLE"))
        )
        for (i in tries) { try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return } catch (e: Exception) {} }
        Toast.makeText(ctx.applicationContext, L.tr("Máy này không mở được CH Play."), Toast.LENGTH_LONG).show()
    }

    fun stop() { onDone = null; try { tts?.stop() } catch (e: Exception) {} }
}

private fun micAllowed(ctx: Context) = ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

/** Nút mic: chạm để nói, chạm lần nữa để hủy. */
@Composable
fun MicBtn() {
    val ctx = LocalContext.current
    val on = S.voiceState.intValue == 1
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) Voice.start(ctx) else Toast.makeText(ctx, L.tr("Cần quyền micro để ra lệnh bằng giọng nói."), Toast.LENGTH_LONG).show()
    }
    Box(
        Modifier.size(46.dp).clip(CircleShape).background(if (on) Pink else CardBg).border(1.dp, if (on) Pink else Bd, CircleShape)
            .clickable { if (on) Voice.close() else if (micAllowed(ctx)) Voice.start(ctx) else ask.launch(Manifest.permission.RECORD_AUDIO) },
        contentAlignment = Alignment.Center
    ) { Icon(Icons.Filled.Mic, "Ra lệnh bằng giọng nói", tint = if (on) Color.White else Pink, modifier = Modifier.size(24.dp)) }
}

@Composable
private fun CmdLine(group: String, items: String) {
    Row { T(group, 13, FontWeight.Bold, modifier = Modifier.width(76.dp)); T(items, 13, color = SubTxt) }
}

/** Câu lệnh của mô-đun giọng nói trong hộp (theo tờ "Hướng dẫn điều khiển giọng nói" của SK). Nút mic trên app cũng hiểu các câu này. */
@Composable
fun CmdList() {
    T("Các câu lệnh", 16, FontWeight.ExtraBold)
    CmdLine("Gọi", "“Xin chào SK Light” hoặc “Này đèn viền”; xong thì nói “Hẹn gặp lại”")
    CmdLine("Bật tắt", "“bật đèn”, “tắt đèn”")
    CmdLine("Độ sáng", "“sáng hơn”, “tối hơn”, “độ sáng tối đa”, “độ sáng tối thiểu”")
    CmdLine("Màu", "“màu đỏ”, “màu vàng”, “màu xanh lá”, “màu xanh lam”, “màu xanh da trời”, “màu tím”, “màu trắng”")
    CmdLine("Chế độ", "“chế độ tự động”, “chế độ thở”, “chế độ chớp”, “nhịp điệu sôi động”, “đổi hiệu ứng”")
    CmdLine("Tốc độ", "“tăng tốc”, “chậm lại”")
    CmdLine("Khác", "“đổi hướng”, “đổi giọng nói”")
    T("Chế độ theo số", 13, FontWeight.Bold)
    T("“chế độ một” Thể thao · “hai” Ma thuật · “ba” Đuôi sao chổi · “bốn” Xuân về hoa nở · “năm” Khúc tình ca mùa đông · “sáu” Nhảy theo nhạc · “bảy” Mây trôi nước chảy · “tám” Xếp chồng · “chín” Nhịp điệu năng động · “mười một” Cuộn đủ màu · “mười hai” Chiếu sáng năng động", 13, color = SubTxt)
}

/** Câu lệnh chỉ nút mic trên app mới hiểu (hộp không có). */
@Composable
fun CmdListApp() {
    T("Thêm khi dùng nút mic trên app", 14, FontWeight.Bold)
    CmdLine("Độ sáng", "“độ sáng 50”")
    CmdLine("Màu", "“màu cam”, “màu hồng”")
    CmdLine("Khu vực", "“khu vực 2 màu đỏ”, “khu vực 1 màu xanh”, “khu vực 2 sáng hơn”, “cả xe màu trắng”")
    CmdLine("Bối cảnh", "gọi theo tên: “thư giãn”, “thể thao”, “ban đêm”, “đón khách” và tên bạn tự đặt")
    CmdLine("Tốc độ", "“tốc độ 8”")
    CmdLine("Chế độ", "gọi theo tên chế độ trên màn hình")
}

/** Trang "Giọng nói AI": cài đặt mô-đun giọng nói của hộp, câu lệnh, rồi tới phần nút mic của app. */
@Composable
fun VoicePage() {
    BackToSettings()
    BoxVoiceCard()
    Card { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { CmdList() } }
    Card {
        GroupTitle(Icons.Filled.Mic, "Nút mic trên app", 16)
        Spacer(Modifier.height(4.dp))
        T("Ra lệnh qua điện thoại hoặc màn hình xe, dùng khi không nói trực tiếp với hộp.", 12, color = Grey)
        Spacer(Modifier.height(6.dp))
        SwitchRow("Trả lời bằng tiếng nói", "Đọc lại việc vừa làm sau mỗi câu lệnh", S.voiceReply.value) { S.voiceReply.value = it; if (!it) Speaker.stop(); S.saveSettings() }
        val ctx = LocalContext.current
        val st = Speaker.state.intValue
        LaunchedEffect(Unit) { Speaker.probe(ctx) }
        T(when (st) { 2 -> "Máy có giọng đọc tiếng Việt."; 3 -> "Máy chưa có giọng đọc tiếng Việt, nên app chưa trả lời bằng tiếng được."; else -> "Đang kiểm tra giọng đọc của máy…" }, 12, color = if (st == 3) Pink else Grey)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (st == 3) OutlineBtn("Cài giọng đọc tiếng Việt", Modifier.weight(1.6f)) { Speaker.install(ctx) }
            OutlineBtn(if (st == 3) "Kiểm tra lại" else "Nghe thử", Modifier.weight(1f)) { Speaker.reset(); Speaker.say(ctx, "Xin chào, em là SK Light", force = true) }
        }
        Spacer(Modifier.height(6.dp))
        Divider()
        SwitchRow("Tắt tiếng báo khi bấm mic", "Tắt tiếng “tinh tinh” của bộ nhận giọng nói. Nhạc đang phát cũng im trong lúc nghe.", S.voiceQuiet.value) { S.voiceQuiet.value = it; S.saveSettings() }
        Divider(6.dp, 10.dp)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { CmdListApp() }
    }
}

/** Thẻ nhỏ dưới menu: đang nghe, kết quả, hoặc hướng dẫn. Ẩn khi không dùng. */
@Composable
fun VoicePanel(modifier: Modifier = Modifier) {
    val st = S.voiceState.intValue
    val tick = S.voiceTick.intValue
    if (st == 0) return
    val ctx = LocalContext.current
    LaunchedEffect(st, tick) { if (st == 2) { delay(2000); if (S.voiceState.intValue == 2) S.voiceState.intValue = 0 } }
    val again = { if (micAllowed(ctx)) Voice.start(ctx) else Voice.close() }
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(Theme.r(10))).background(if (st == 1) tint(0.06f) else CardBg)
            .border(1.dp, if (st == 1) tint(0.5f) else Bd, RoundedCornerShape(Theme.r(10))).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when (st) {
            1 -> {
                val ph = Voice.phase.intValue
                val heard = S.voiceHeard.value
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(if (ph == 0) Grey else Pink), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Mic, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        T(when (ph) { 0 -> "Đang mở micro…"; 2 -> "Đang nghe thấy tiếng…"; 3 -> "Đang xử lý…"; else -> "Mời nói" }, 16, FontWeight.ExtraBold)
                        if (heard.isNotBlank()) T("Nghe được: “$heard”", 12, color = SubTxt)
                        else T("Nói: “màu đỏ”, “sáng hơn”, “chế độ thở”", 12, color = SubTxt)
                    }
                    OutlineBtn("Hủy", Modifier.width(64.dp)) { Voice.close() }
                }
                // thanh báo độ lớn tiếng micro đang thu: nói mà thanh không nhúc nhích là máy chưa nghe thấy
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Bd)) {
                    Box(Modifier.fillMaxWidth(Voice.level.floatValue.coerceIn(0.02f, 1f)).fillMaxHeight().background(Pink))
                }
            }
            2 -> Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0xFFE3F5E6)), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Check, null, tint = Color(0xFF2E8B3D), modifier = Modifier.size(24.dp)) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { T("Nghe được: “${S.voiceHeard.value}”", 12, color = SubTxt); T(S.voiceMsg.value, 16, FontWeight.ExtraBold) }
            }
            3 -> {
                T("Nghe được: “${S.voiceHeard.value}”", 12, color = SubTxt)
                T("Chưa có lệnh này", 16, FontWeight.ExtraBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlineBtn("Nói lại", Modifier.weight(1f), again)
                    OutlineBtn("Xem các câu lệnh", Modifier.weight(1.6f)) { S.voiceState.intValue = 4 }
                    OutlineBtn("Đóng", Modifier.weight(1f)) { Voice.close() }
                }
            }
            4 -> {
                CmdList()
                CmdListApp()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlineBtn("Nói thử", Modifier.weight(1f), again)
                    OutlineBtn("Đóng", Modifier.weight(1f)) { Voice.close() }
                }
            }
            5 -> {
                T("Máy này chưa có bộ nhận giọng nói", 16, FontWeight.ExtraBold)
                T("Cài hoặc bật app Google từ CH Play, rồi quay lại chạm nút mic.", 13, color = SubTxt)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlineBtn("Mở CH Play", Modifier.weight(1f)) {
                        val id = "com.google.android.googlequicksearchbox"
                        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$id")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        catch (e: Exception) {
                            try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$id")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            catch (e2: Exception) { Toast.makeText(ctx, L.tr("Máy này không mở được CH Play."), Toast.LENGTH_LONG).show() }
                        }
                    }
                    OutlineBtn("Đóng", Modifier.weight(1f)) { Voice.close() }
                }
            }
            else -> {
                T(S.voiceMsg.value, 15, FontWeight.Bold)
                T("Bộ nhận giọng nói: ${Voice.engine}", 11, color = Grey)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlineBtn("Nói lại", Modifier.weight(1f), again)
                    OutlineBtn("Xem các câu lệnh", Modifier.weight(1.6f)) { S.voiceState.intValue = 4 }
                    OutlineBtn("Đóng", Modifier.weight(1f)) { Voice.close() }
                }
            }
        }
    }
}
