package vn.sklight.app

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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

    // tên dài đứng trước để "xanh lá" không bị hiểu thành "xanh"
    private val COLORS: List<Triple<List<String>, Int, String>> = listOf(
        Triple(listOf("xanh lá", "xanh lục", "lá cây"), 0x41E01F, "xanh lá"),
        Triple(listOf("xanh lam", "xanh ngọc", "xanh da trời"), 0x17F4EA, "xanh lam"),
        Triple(listOf("xanh dương", "xanh biển", "xanh nước biển", "xanh"), 0x4E78FF, "xanh dương"),
        Triple(listOf("đỏ"), 0xFF2A48, "đỏ"), Triple(listOf("cam"), 0xFF7A1A, "cam"), Triple(listOf("vàng"), 0xFFE114, "vàng"),
        Triple(listOf("tím"), 0x8A00FF, "tím"), Triple(listOf("trắng"), 0xFFFFFF, "trắng"), Triple(listOf("hồng"), 0xFE2CAA, "hồng")
    )

    /** [word] xuất hiện trong [t] thành cụm từ riêng (không dính vào chữ khác). */
    private fun has(t: String, word: String) = Regex("(^|\\s)" + Regex.escape(word) + "(\\s|$)").containsMatchIn(t)

    private fun pick(e: Effect): String { S.pickEffect(e); return "Chế độ ${e.name}" }

    /** Thực hiện câu lệnh. Trả về câu báo kết quả, hoặc null nếu không có lệnh này. So trên chữ CÓ DẤU. */
    fun run(raw: String): String? {
        val t = raw.lowercase().replace(Regex("[.,!?]"), " ").replace(Regex("\\s+"), " ").trim()
        if (t.isEmpty()) return null
        val cur = S.bri[S.zone.intValue]
        Regex("độ sáng (\\d{1,3})").find(t)?.let { val v = it.groupValues[1].toInt().coerceIn(0, 100); S.setBright(v); return "Độ sáng $v%" }
        Regex("tốc độ (\\d{1,2})").find(t)?.let { val v = it.groupValues[1].toInt().coerceIn(1, 10); S.setSpeed(v); return "Tốc độ $v" }
        if (has(t, "tắt đèn") || t == "tắt" || has(t, "tắt hết") || has(t, "màu đen")) { S.setPower(false); return "Đã tắt đèn" }
        if (has(t, "bật đèn") || has(t, "mở đèn") || t == "bật") { S.setPower(true); return "Đã bật đèn" }
        if (has(t, "sáng nhất") || has(t, "sáng tối đa")) { S.setBright(100); return "Độ sáng 100%" }
        if (has(t, "sáng hơn") || has(t, "tăng sáng") || has(t, "sáng lên")) { val v = (cur + 20).coerceAtMost(100); S.setBright(v); return "Độ sáng $v%" }
        if (has(t, "tối hơn") || has(t, "giảm sáng") || has(t, "tối lại") || has(t, "dịu")) { val v = (cur - 20).coerceAtLeast(5); S.setBright(v); return "Độ sáng $v%" }
        if (has(t, "nhanh hơn") || has(t, "tăng tốc")) { val v = (S.speed.intValue + 2).coerceAtMost(10); S.setSpeed(v); return "Tốc độ $v" }
        if (has(t, "chậm lại") || has(t, "chậm hơn") || has(t, "giảm tốc")) { val v = (S.speed.intValue - 2).coerceAtLeast(1); S.setSpeed(v); return "Tốc độ $v" }
        // chế độ gọi kèm "chế độ", "hiệu ứng", "mùa": nhận cả tên một chữ (thở, chớp, xuân…)
        for (e in Effects.all) { val n = e.name.lowercase(); if (has(t, "chế độ $n") || has(t, "hiệu ứng $n") || has(t, "mùa $n")) return pick(e) }
        // bối cảnh gọi theo tên
        Scenes.all().forEachIndexed { i, sc -> if (has(t, sc.name.lowercase())) { Scenes.apply(i); return "Bối cảnh ${sc.name}" } }
        // chế độ tên từ hai chữ trở lên: gọi thẳng tên
        for (e in Effects.all) { val n = e.name.lowercase(); if (n.contains(' ') && has(t, n)) return pick(e) }
        if (has(t, "mùa hè")) Effects.byId(16)?.let { return pick(it) }
        if (has(t, "bảy màu") || has(t, "7 màu") || has(t, "đủ màu")) Effects.byId(1)?.let { return pick(it) }
        // màu: cần chữ "màu", hoặc câu rất ngắn
        if (t.contains("màu") || t.split(" ").size <= 3) for (c in COLORS) if (c.first.any { has(t, it) }) {
            if (S.mode.intValue != 0) { S.mode.intValue = 0; S.sendEffect() }      // đang chạy hiệu ứng thì về Đơn sắc để thấy màu
            S.setColor(c.second)
            return "Đã đổi sang màu ${c.third}"
        }
        return null
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
                    for (cand in list2) { val m = run(cand); if (m != null) { show(2, cand, m); return } }
                    if (list2.isEmpty()) show(6, msg = "Không nghe thấy câu lệnh.") else show(3, list2[0])
                }
                override fun onError(code: Int) {
                    if (my != gen) return
                    h.removeCallbacksAndMessages(null)
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
                if (heard.isNotBlank()) { val m = run(heard); if (m != null) show(2, heard, m) else show(3, heard) }
                else show(6, msg = "Không nghe thấy câu lệnh.")
            }, 16000)
        } catch (e: Exception) { show(6, msg = "Không mở được bộ nhận giọng nói của máy.") }
    }
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
                CmdLine("Độ sáng", "“sáng hơn”, “tối hơn”, “độ sáng 50”")
                CmdLine("Màu", "“màu đỏ”, cam, vàng, xanh lá, xanh lam, xanh dương, tím, trắng, hồng")
                CmdLine("Chế độ", "gọi theo tên: “chế độ thở”, “chế độ chớp”, “nhịp nhạc”, “mây trôi”, “mùa xuân”")
                CmdLine("Bối cảnh", "gọi theo tên: “thư giãn”, “thể thao”, “ban đêm”, “đón khách” và tên bạn tự đặt")
                CmdLine("Tốc độ", "“nhanh hơn”, “chậm lại”, “tốc độ 8”")
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
