package vn.sklight.app

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.File

/** Lời chào: 3 file mp3 người dùng tự chọn (mở máy / lên xe / xuống xe). Chưa chọn file thì đọc câu mặc định bằng giọng máy. Chưa thử trên xe thật. */
object Greet {
    val KINDS = listOf("Mở máy", "Lên xe", "Xuống xe")
    private val KEYS = listOf("greet0", "greet1", "greet2")
    private val DEF = listOf("Xin chào, chúc anh chị một ngày tốt lành", "Chào mừng anh chị lên xe", "Cảm ơn anh chị, hẹn gặp lại")
    val ver = mutableIntStateOf(0)
    private val h = Handler(Looper.getMainLooper())
    private var mp: MediaPlayer? = null
    private var doneThisSession = false

    private fun sp(c: Context) = c.getSharedPreferences("sklight", Context.MODE_PRIVATE)
    private fun file(c: Context, k: Int) = File(c.filesDir, "greet$k.mp3")
    fun name(c: Context, k: Int): String = if (file(c, k).exists()) sp(c).getString(KEYS[k], "") ?: "" else ""
    fun enabled(c: Context) = sp(c).getBoolean("greetOn", true)
    fun setEnabled(c: Context, v: Boolean) { sp(c).edit().putBoolean("greetOn", v).apply(); ver.intValue++ }

    fun save(c: Context, k: Int, uri: Uri, display: String): Boolean {
        return try {
        val data = c.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return false
        if (data.isEmpty() || data.size > 8 * 1024 * 1024) return false
        file(c, k).writeBytes(data)
        sp(c).edit().putString(KEYS[k], display).apply(); ver.intValue++; true
        } catch (e: Exception) { false }
    }

    fun clear(c: Context, k: Int) { file(c, k).delete(); sp(c).edit().remove(KEYS[k]).apply(); ver.intValue++ }

    fun play(c: Context, k: Int, light: Boolean) {
        if (light) flash()
        val f = file(c, k)
        if (f.exists()) {
            try {
                mp?.release()
                val p = MediaPlayer()
                p.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                p.setDataSource(f.absolutePath)
                p.setOnCompletionListener { it.release(); if (mp === it) mp = null }
                p.setOnErrorListener { m, _, _ -> m.release(); if (mp === m) mp = null; Speaker.say(c, DEF[k], force = true); true }
                p.prepare(); p.start(); mp = p
                return
            } catch (e: Exception) { mp = null }
        }
        Speaker.say(c, DEF[k], force = true)
    }

    /** Gọi khi vừa nối xong hộp: chỉ một lần mỗi lần mở app. */
    fun onConnected(c: Context) {
        if (doneThisSession || !enabled(c)) return
        doneThisSession = true
        h.postDelayed({ if (S.connected.value) play(c, 0, true) }, 1500)
    }

    /** Đèn chào: nháy độ sáng 2 lần rồi trả về mức đang để (dùng lại lệnh độ sáng đã có, không gửi lệnh mới). */
    private fun flash() {
        if (!S.connected.value || S.alertKind.intValue != 0 || Sim.running.value) return
        val b = if (S.zone.intValue == 0) S.bri[0] else maxOf(S.bri[1], S.bri[2])
        fun set(v: Int) { S.ble.send(Protocol.bright(true, true, v, v), 1, true) }
        val steps = listOf(0L to 100, 300L to 10, 600L to 100, 900L to 10, 1200L to b)
        for ((t, v) in steps) h.postDelayed({ if (S.alertKind.intValue == 0) { if (v == b && t == 1200L) S.sendBright() else set(v) } }, t)
    }
}

@Composable
fun GreetPage() {
    val ctx = LocalContext.current
    Greet.ver.intValue
    var target by remember { mutableIntStateOf(0) }
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val nm = try {
                ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (c.moveToFirst() && i >= 0) c.getString(i) else uri.lastPathSegment ?: ""
                } ?: (uri.lastPathSegment ?: "")
            } catch (e: Exception) { uri.lastPathSegment ?: "" }
            if (!Greet.save(ctx, target, uri, nm)) Notice.show(ctx, L.tr("Không đọc được file này (tối đa 8 MB)."))
        }
    }
    BackRow(0, "Cài đặt")
    Card {
        GroupTitle(Icons.Filled.RecordVoiceOver, "Lời chào", 16)
        Spacer(Modifier.height(6.dp))
        T("Chưa chọn file thì app đọc một câu chào mặc định bằng giọng của máy.", 13)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            val on = Greet.enabled(ctx)
            Chip("Bật khi nối hộp", on, Modifier.weight(1f), 44.dp, 13) { Greet.setEnabled(ctx, true) }
            Chip("Tắt", !on, Modifier.weight(1f), 44.dp, 13) { Greet.setEnabled(ctx, false) }
        }
    }
    for (k in 0..2) Card {
        T("Lời chào " + Greet.KINDS[k].lowercase(), 15, FontWeight.Bold)
        val n = Greet.name(ctx, k)
        T(if (n.isEmpty()) "Chưa chọn file (dùng giọng máy)" else n, 13, maxLines = 1)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlineBtn("Chọn mp3", Modifier.weight(1f)) { target = k; pick.launch(arrayOf("audio/*")) }
            OutlineBtn("Phát thử", Modifier.weight(1f)) { Greet.play(ctx, k, false) }
            if (n.isNotEmpty()) OutlineBtn("Xóa", Modifier.weight(1f)) { Greet.clear(ctx, k) }
        }
    }
    Card { T("Lời chào \"Mở máy\" tự phát một lần mỗi lần mở app, ngay sau khi nối được hộp đèn, kèm đèn nháy chào. \"Lên xe\" và \"Xuống xe\" hiện chỉ phát khi bấm Phát thử.", 13) }
}
