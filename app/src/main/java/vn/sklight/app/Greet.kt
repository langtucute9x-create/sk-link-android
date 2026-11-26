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
    private var openDone = false
    private var pendingConnect = false
    private var lastDown = 0L

    private fun sp(c: Context) = c.getSharedPreferences("sklight", Context.MODE_PRIVATE)
    private fun file(c: Context, k: Int) = File(c.filesDir, "greet$k.mp3")
    fun name(c: Context, k: Int): String = if (file(c, k).exists()) sp(c).getString(KEYS[k], "") ?: "" else ""
    /** Nhạc có sẵn trong app (mặc định) cho Mở máy và Lên xe; Xuống xe chưa có. */
    private fun builtin(c: Context, k: Int): File? {
        if (k > 1) return null
        return try {
            val out = File(c.cacheDir, "greet_def$k.mp3")
            if (!out.exists() || out.length() == 0L) c.assets.open("greet_def$k.mp3").use { i -> out.outputStream().use { o -> i.copyTo(o) } }
            out
        } catch (e: Exception) { null }
    }
    fun enabled(c: Context) = sp(c).getBoolean("greetOn", true)
    fun openOn(c: Context) = sp(c).getBoolean("greetOpenOn", true)
    fun setOpenOn(c: Context, v: Boolean) { sp(c).edit().putBoolean("greetOpenOn", v).apply(); ver.intValue++ }
    fun downOn(c: Context) = sp(c).getBoolean("greetDownOn", true)
    fun setDownOn(c: Context, v: Boolean) { sp(c).edit().putBoolean("greetDownOn", v).apply(); ver.intValue++ }
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

    /** Màn hình ô tô: có nhạc mặc định và câu chào bằng giọng máy. Điện thoại: chỉ phát file mp3 người dùng đã chọn. */
    fun play(c: Context, k: Int, light: Boolean) {
        val car = S.isCar.value
        val f = if (file(c, k).exists()) file(c, k) else if (car) builtin(c, k) else null
        if (f == null && !car) return
        if (light) flash()
        if (f != null && f.exists()) {
            try {
                mp?.release()
                val p = MediaPlayer()
                p.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                p.setDataSource(f.absolutePath)
                p.setOnCompletionListener { it.release(); if (mp === it) mp = null; if (pendingConnect) { pendingConnect = false; if (S.connected.value) play(c, 0, true) } }
                p.setOnErrorListener { m, _, _ -> m.release(); if (mp === m) mp = null; if (car) Speaker.say(c, DEF[k], force = true); true }
                p.prepare(); p.start(); mp = p
                return
            } catch (e: Exception) { mp = null }
        }
        if (car) Speaker.say(c, DEF[k], force = true)
    }

    /** Gọi khi mở app: phát "Lên xe" một lần mỗi lần mở app. */
    fun onAppOpen(c: Context) {
        if (openDone || !enabled(c) || !openOn(c)) return
        openDone = true
        h.postDelayed({ play(c, 1, false) }, 800)
    }

    /** Ngắt kết nối hộp (bấm ngắt hoặc hộp mất điện) sau khi đã nối: phát "Xuống xe" nếu bật; lần nối lại sau đó chào "Mở máy" lại. Trả về true nếu đã phát. */
    fun onLost(c: Context): Boolean {
        if (!doneThisSession) return false
        doneThisSession = false; pendingConnect = false
        val now = android.os.SystemClock.elapsedRealtime()
        if (!enabled(c) || !downOn(c) || now - lastDown < 10000) return false
        if (!S.isCar.value && !file(c, 2).exists()) return false
        lastDown = now
        play(c, 2, false)
        return true
    }

    /** Gọi khi vừa nối xong hộp: một lần cho mỗi lần nối (ngắt rồi nối lại thì chào lại). */
    fun onConnected(c: Context) {
        if (doneThisSession || !enabled(c)) return
        doneThisSession = true
        h.postDelayed({ if (!S.connected.value) return@postDelayed; if (mp?.isPlaying == true) pendingConnect = true else play(c, 0, true) }, 1500)
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
    var dm by remember { mutableIntStateOf(S.devMode(ctx)) }
    fun got(uri: Uri?) {
        if (uri == null) return
        val nm = try {
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst() && i >= 0) c.getString(i) else uri.lastPathSegment ?: ""
            } ?: (uri.lastPathSegment ?: "")
        } catch (e: Exception) { uri.lastPathSegment ?: "" }
        if (!Greet.save(ctx, target, uri, nm)) Notice.show(ctx, L.tr("Không đọc được file này (tối đa 8 MB)."))
    }
    val pick2 = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri: Uri? -> got(uri) }
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri: Uri? -> got(uri) }
    // Màn hình xe Android 10 thường không có trình chọn file kiểu mới: thử lần lượt, không được thì báo chứ không để app đóng.
    fun choose(k: Int) {
        target = k
        try { pick.launch(arrayOf("audio/*")); return } catch (e: Exception) {}
        try { pick2.launch("audio/*"); return } catch (e: Exception) {}
        Notice.show(ctx, L.tr("Máy này không có trình chọn file. Cài thêm một app quản lý file rồi thử lại."))
    }
    BackRow(0, "Cài đặt")
    Card {
        GroupTitle(Icons.Filled.RecordVoiceOver, "Lời chào", 16)
        Spacer(Modifier.height(6.dp))
        T("Loại máy đang dùng: " + (if (S.isCar.value) "màn hình xe / Android box" else "điện thoại") + (if (dm == 0) " (tự nhận)" else ""), 13, FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Chip("Tự nhận", dm == 0, Modifier.weight(1f), 44.dp, 13) { dm = 0; S.setDevMode(ctx, 0) }
            Chip("Màn hình xe", dm == 1, Modifier.weight(1f), 44.dp, 13) { dm = 1; S.setDevMode(ctx, 1) }
            Chip("Điện thoại", dm == 2, Modifier.weight(1f), 44.dp, 13) { dm = 2; S.setDevMode(ctx, 2) }
        }
        Spacer(Modifier.height(6.dp))
        T(if (S.isCar.value) "Màn hình xe: chưa chọn file thì dùng nhạc có sẵn hoặc giọng máy; không đọc \"đã kết nối\"." else "Điện thoại: chỉ phát file mp3 anh chọn; có giọng báo \"đã kết nối\" khi nối hộp.", 12)
    }
    Card {
        T("Lời chào khi nối hộp đèn", 15, FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            val on = Greet.enabled(ctx)
            Chip("Bật", on, Modifier.weight(1f), 44.dp, 13) { Greet.setEnabled(ctx, true) }
            Chip("Tắt", !on, Modifier.weight(1f), 44.dp, 13) { Greet.setEnabled(ctx, false) }
        }
    }
    @Composable fun fileCard(k: Int) {
        val n = Greet.name(ctx, k)
        T(if (n.isNotEmpty()) n else if (!S.isCar.value) "Chưa chọn file (không phát)" else if (k <= 1) "Mặc định: nhạc có sẵn trong app" else "Chưa chọn file (dùng giọng máy)", 13, maxLines = 1)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlineBtn("Chọn mp3", Modifier.weight(1f)) { choose(k) }
            OutlineBtn("Phát thử", Modifier.weight(1f)) { Greet.play(ctx, k, false) }
            if (n.isNotEmpty()) OutlineBtn("Xóa", Modifier.weight(1f)) { Greet.clear(ctx, k) }
        }
    }
    Card {
        T("Lời chào lên xe (khi mở app)", 15, FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            val o = Greet.openOn(ctx)
            Chip("Bật", o, Modifier.weight(1f), 44.dp, 13) { Greet.setOpenOn(ctx, true) }
            Chip("Tắt", !o, Modifier.weight(1f), 44.dp, 13) { Greet.setOpenOn(ctx, false) }
        }
        Spacer(Modifier.height(10.dp))
        fileCard(1)
    }
    Card {
        T("Lời chào mở máy (khi nối được hộp)", 15, FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        fileCard(0)
    }
    Card {
        T("Lời chào xuống xe (khi ngắt kết nối hộp)", 15, FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            val d = Greet.downOn(ctx)
            Chip("Bật", d, Modifier.weight(1f), 44.dp, 13) { Greet.setDownOn(ctx, true) }
            Chip("Tắt", !d, Modifier.weight(1f), 44.dp, 13) { Greet.setDownOn(ctx, false) }
        }
        Spacer(Modifier.height(10.dp))
        fileCard(2)
    }
    Card { T("Mỗi lần mở app: \"Lên xe\" phát ngay; \"Mở máy\" phát khi nối được hộp đèn (kèm đèn nháy chào), nếu \"Lên xe\" còn đang phát thì chờ phát xong. \"Xuống xe\" phát khi ngắt kết nối hộp (bấm ngắt hoặc hộp mất điện); nối lại thì chào \"Mở máy\" lại.", 13) }
}
