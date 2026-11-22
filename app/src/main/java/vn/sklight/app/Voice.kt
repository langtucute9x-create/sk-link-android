package vn.sklight.app

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
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
    private fun bright(v: Int): String { S.setBright(v); return "Em đã chỉnh độ sáng $v% rồi" }
    private fun speed(v: Int): String { S.setSpeed(v); return "Em đã chỉnh tốc độ $v rồi" }

    /** Thực hiện câu lệnh. Trả về câu trả lời (hiện trên thẻ và đọc thành tiếng), hoặc null nếu không có lệnh này. So trên chữ CÓ DẤU. */
    fun run(raw: String): String? {
        after = 0
        val t = raw.lowercase().replace(Regex("[.,!?]"), " ").replace(Regex("\\s+"), " ").trim()
        if (t.isEmpty()) return null
        // các câu không cần hộp đèn
        if (has(t, "hẹn gặp lại") || has(t, "tạm biệt")) { after = 2; return "Hẹn gặp lại ạ" }
        if (has(t, "đổi giọng nói") || has(t, "đổi giọng") || has(t, "thay giọng nói")) { after = 3; return "Em đổi giọng rồi ạ" }
        val called = has(t, "xin chào") || has(t, "này đèn viền") || has(t, "đèn viền ơi")
        if (called && t.split(" ").size <= 5) { after = 1; return "Em nghe đây ạ" }
        if (!S.connected.value) return "Em chưa kết nối với hộp đèn"

        val cur = S.bri[S.zone.intValue]
        Regex("độ sáng (\\d{1,3})").find(t)?.let { return bright(it.groupValues[1].toInt().coerceIn(0, 100)) }
        Regex("tốc độ (\\d{1,2})").find(t)?.let { return speed(it.groupValues[1].toInt().coerceIn(1, 10)) }
        if (has(t, "tắt đèn") || t == "tắt" || has(t, "tắt hết") || has(t, "màu đen")) { S.setPower(false); return "Em đã tắt đèn rồi" }
        if (has(t, "bật đèn") || has(t, "mở đèn") || t == "bật") { S.setPower(true); return "Em đã bật đèn rồi" }
        if (has(t, "sáng tối đa") || has(t, "sáng nhất")) return bright(100)
        if (has(t, "sáng tối thiểu") || has(t, "tối nhất")) return bright(5)
        if (has(t, "sáng hơn") || has(t, "tăng sáng") || has(t, "sáng lên")) return bright((cur + 20).coerceAtMost(100))
        if (has(t, "tối hơn") || has(t, "giảm sáng") || has(t, "tối lại") || has(t, "dịu")) return bright((cur - 20).coerceAtLeast(5))
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
        // bối cảnh gọi theo tên
        Scenes.all().forEachIndexed { i, sc -> if (has(t, sc.name.lowercase())) { Scenes.apply(i); return "Em đã chuyển sang bối cảnh ${sc.name} rồi" } }
        // chế độ tên từ hai chữ trở lên: gọi thẳng tên
        for (e in Effects.all) { val n = e.name.lowercase(); if (n.contains(' ') && has(t, n)) return pick(e) }
        for (g in GUIDE) if (g.third.any { has(t, it) }) Effects.byId(g.second)?.let { return pick(it) }
        if (has(t, "mùa hè")) Effects.byId(16)?.let { return pick(it) }
        if (has(t, "bảy màu") || has(t, "7 màu") || has(t, "đủ màu")) Effects.byId(1)?.let { return pick(it) }
        // màu: cần chữ "màu", hoặc câu rất ngắn
        if (t.contains("màu") || t.split(" ").size <= 3) for (c in COLORS) if (c.first.any { has(t, it) }) {
            if (S.mode.intValue != 0) { S.mode.intValue = 0; S.sendEffect() }      // đang chạy hiệu ứng thì về Đơn sắc để thấy màu
            S.setColor(c.second)
            return "Em đã đổi sang màu ${c.third} rồi"
        }
        return null
    }

    /** Hiện câu trả lời và đọc thành tiếng; đọc xong thì làm tiếp việc trong [after]. */
    private fun answer(c: Context, heard: String, m: String) {
        val a = after
        show(2, heard, m)
        val done: () -> Unit = { if (a == 1) start(c) else if (a == 2) close() }
        if (a == 3) Speaker.changeVoice(c, m) else Speaker.say(c, m, done)
    }

    fun close() {
        gen++
        h.removeCallbacksAndMessages(null)
        try { sr?.cancel(); sr?.destroy() } catch (_: Exception) {}
        sr = null
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
    fun start(ctx: Context, attempt: Int = 0) {
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
        try {
            val r = if (comp != null) SpeechRecognizer.createSpeechRecognizer(c, comp) else SpeechRecognizer.createSpeechRecognizer(c)
            r.setRecognitionListener(object : RecognitionListener {
                override fun onResults(b: Bundle?) {
                    if (my != gen) return
                    h.removeCallbacksAndMessages(null)
                    val list2 = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: arrayListOf()
                    for (cand in list2) { val m = run(cand); if (m != null) { answer(c, cand, m); return } }
                    if (list2.isEmpty()) { show(6, msg = "Không nghe thấy câu lệnh."); Speaker.say(c, "Em chưa nghe rõ") }
                    else { show(3, list2[0]); Speaker.say(c, "Em chưa có lệnh này") }
                }
                override fun onError(code: Int) {
                    if (my != gen) return
                    h.removeCallbacksAndMessages(null)
                    if (code == SpeechRecognizer.ERROR_NO_MATCH || code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) Speaker.say(c, "Em chưa nghe rõ")
                    show(6, msg = when (code) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Không nghe thấy câu lệnh."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Cần có mạng để nhận giọng nói."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Bộ nhận giọng nói chưa có quyền micro. Vào Cài đặt máy, mục Ứng dụng, chọn Google, bật quyền Micro."
                        12, 13 -> "Bộ nhận giọng nói của máy chưa có tiếng Việt."
                        else -> "Bộ nhận giọng nói báo lỗi (mã $code)."
                    })
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
        } catch (e: Exception) { show(6, msg = "Không mở được bộ nhận giọng nói của máy.") }
    }
}

/**
 * Trả lời bằng tiếng nói: bộ đọc chữ thành tiếng có sẵn của máy (không dùng mạng riêng, không dùng AI).
 * Máy chưa có giọng tiếng Việt thì kêu một tiếng "bíp" và báo một lần cách cài.
 */
object Speaker {
    private val h = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var state = 0                            // 0 chưa mở, 1 đang mở, 2 dùng được, 3 máy không có giọng tiếng Việt
    private var waiting: ((TextToSpeech?) -> Unit)? = null
    private var onDone: (() -> Unit)? = null
    private var seq = 0
    private var told = false

    private fun use(ctx: Context, block: (TextToSpeech?) -> Unit) {
        when (state) {
            2 -> block(tts)
            3 -> block(null)
            1 -> waiting = block
            else -> {
                state = 1; waiting = block
                val c = ctx.applicationContext
                try {
                    tts = TextToSpeech(c) { st -> h.post { opened(st) } }
                } catch (e: Exception) { state = 3; waiting = null; block(null) }
            }
        }
    }

    private fun opened(st: Int) {
        val e = tts
        var ok = false
        if (st == TextToSpeech.SUCCESS && e != null) {
            val r = try { e.setLanguage(Locale("vi", "VN")) } catch (x: Exception) { TextToSpeech.LANG_NOT_SUPPORTED }
            ok = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
        }
        if (ok && e != null) {
            state = 2
            try {
                val want = S.ttsVoice
                if (want.isNotEmpty()) e.voices?.firstOrNull { it.name == want }?.let { e.voice = it }
            } catch (x: Exception) {}
            e.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) { finished(id) }
                @Deprecated("") override fun onError(id: String?) { finished(id) }
            })
        } else state = 3
        val w = waiting; waiting = null
        w?.invoke(if (state == 2) e else null)
    }

    private fun finished(id: String?) {
        h.post { if (id == "sk$seq") { val d = onDone; onDone = null; d?.invoke() } }
    }

    private fun beep(ctx: Context) {
        try {
            val g = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
            g.startTone(ToneGenerator.TONE_PROP_ACK, 180)
            h.postDelayed({ try { g.release() } catch (e: Exception) {} }, 600)
        } catch (e: Exception) {}
        if (!told) {
            told = true
            Toast.makeText(ctx.applicationContext, L.tr("Máy chưa có giọng đọc tiếng Việt nên app chỉ kêu bíp. Vào Cài đặt máy, tìm \"Chuyển văn bản thành giọng nói\" để cài tiếng Việt."), Toast.LENGTH_LONG).show()
        }
    }

    private fun speak(e: TextToSpeech, text: String, then: (() -> Unit)?) {
        onDone = then
        val id = "sk${++seq}"
        val r = try { e.speak(text.replace("%", " phần trăm"), TextToSpeech.QUEUE_FLUSH, null, id) } catch (x: Exception) { TextToSpeech.ERROR }
        if (r != TextToSpeech.SUCCESS) { onDone = null; then?.invoke() }
    }

    /** Đọc [text]; đọc xong (hoặc không đọc được) thì gọi [then]. */
    fun say(ctx: Context, text: String, then: (() -> Unit)? = null) {
        use(ctx) { e -> if (e == null) { beep(ctx); then?.invoke() } else speak(e, text, then) }
    }

    /** Chuyển sang giọng tiếng Việt kế tiếp của máy rồi đọc [text] bằng giọng mới. */
    fun changeVoice(ctx: Context, text: String) {
        use(ctx) { e ->
            if (e == null) { beep(ctx); return@use }
            val l = try { e.voices?.filter { it.locale.language == "vi" && !it.features.contains("notInstalled") }?.sortedBy { it.name } } catch (x: Exception) { null } ?: emptyList()
            if (l.size < 2) { speak(e, "Máy này chỉ có một giọng tiếng Việt", null); return@use }
            val cur = try { e.voice?.name } catch (x: Exception) { null }
            val v = l[(l.indexOfFirst { it.name == cur } + 1) % l.size]
            try { e.voice = v; S.ttsVoice = v.name } catch (x: Exception) {}
            speak(e, text, null)
        }
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
        modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (st == 1) tint(0.06f) else CardBg)
            .border(1.dp, if (st == 1) tint(0.5f) else Bd, RoundedCornerShape(10.dp)).padding(12.dp),
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
                T("Các câu lệnh", 16, FontWeight.ExtraBold)
                CmdLine("Bật tắt", "“bật đèn”, “tắt đèn”")
                CmdLine("Độ sáng", "“sáng hơn”, “tối hơn”, “độ sáng tối đa”, “độ sáng tối thiểu”, “độ sáng 50”")
                CmdLine("Màu", "“màu đỏ”, vàng, xanh lá, xanh lam, xanh da trời, tím, trắng, cam, hồng")
                CmdLine("Chế độ", "“chế độ tự động”, “chế độ thở”, “chế độ chớp”, “nhịp điệu sôi động”, “chế độ một” đến “chế độ mười hai”, hoặc gọi theo tên; “đổi hiệu ứng”")
                CmdLine("Bối cảnh", "gọi theo tên: “thư giãn”, “thể thao”, “ban đêm”, “đón khách” và tên bạn tự đặt")
                CmdLine("Tốc độ", "“tăng tốc”, “chậm lại”, “tốc độ 8”")
                CmdLine("Khác", "“đổi hướng”, “đổi giọng nói”, “xin chào SK Light”, “hẹn gặp lại”")
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
