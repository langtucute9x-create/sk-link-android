package vn.sklight.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Kiểm tra bản mới trên GitHub (file version.json cạnh file APK), tải về và mở trình cài của máy. */
object Updater {
    private const val BASE = "https://github.com/langtucute9x-create/sk-link-android/releases/download/sk-light-latest/"
    private const val INFO_URL = BASE + "version.json"
    private val main = Handler(Looper.getMainLooper())

    class Info(val code: Int, val name: String, val notes: String, val url: String, val sha: String, val size: Long)

    val info = mutableStateOf<Info?>(null)
    val checking = mutableStateOf(false)
    val progress = mutableIntStateOf(-1)      // -1 chưa tải, 0..100 đang tải, 101 đã tải xong
    val msg = mutableStateOf("")
    val showDialog = mutableStateOf(false)
    val noInstaller = mutableStateOf(false)

    private fun sp(c: Context) = c.applicationContext.getSharedPreferences("sklight_upd", Context.MODE_PRIVATE)

    @Suppress("DEPRECATION")
    private fun curCode(c: Context): Int = try {
        val p = c.packageManager.getPackageInfo(c.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) p.longVersionCode.toInt() else p.versionCode
    } catch (e: Exception) { 0 }

    private fun file(c: Context) = File(File(c.cacheDir, "update"), "sk-light.apk")

    /** Tự kiểm tra khi mở app, tối đa một lần mỗi ngày. */
    fun auto(c: Context) {
        val last = sp(c).getLong("last", 0L)
        if (System.currentTimeMillis() - last < 24L * 3600 * 1000) return
        check(c, false)
    }

    fun check(c: Context, manual: Boolean) {
        if (checking.value) return
        val app = c.applicationContext
        checking.value = true
        if (manual) msg.value = ""
        Thread {
            var found: Info? = null
            var err = ""
            try {
                val u = URL(INFO_URL).openConnection() as HttpURLConnection
                u.connectTimeout = 8000; u.readTimeout = 8000
                if (u.responseCode != 200) throw Exception("HTTP ${u.responseCode}")
                val j = JSONObject(u.inputStream.bufferedReader().use { it.readText() })
                val i = Info(j.getInt("versionCode"), j.optString("versionName"), j.optString("notes"), j.optString("url", BASE + "sk-light.apk"), j.optString("sha256"), j.optLong("size"))
                if (i.code > curCode(app) && (manual || sp(app).getInt("skip", 0) != i.code)) found = i
                sp(app).edit().putLong("last", System.currentTimeMillis()).apply()
            } catch (e: Exception) { err = e.message ?: "lỗi" }
            main.post {
                checking.value = false
                if (found != null) {
                    if (info.value?.code != found.code) { progress.intValue = -1; noInstaller.value = false }
                    info.value = found
                    if (manual) showDialog.value = true
                } else if (manual) {
                    msg.value = if (err.isEmpty()) "Bạn đang dùng bản mới nhất." else "Không kiểm tra được (cần có mạng)."
                    Notice.show(app, L.tr(msg.value))
                }
            }
        }.start()
    }

    fun skip(c: Context) {
        info.value?.let { sp(c).edit().putInt("skip", it.code).apply() }
        info.value = null; showDialog.value = false
    }

    fun download(c: Context) {
        val i = info.value ?: return
        if (progress.intValue in 0..100) return
        val app = c.applicationContext
        progress.intValue = 0; msg.value = ""
        Thread {
            var ok = false
            var err = ""
            try {
                val f = file(app); f.parentFile?.mkdirs(); if (f.exists()) f.delete()
                val u = URL(i.url).openConnection() as HttpURLConnection
                u.connectTimeout = 10000; u.readTimeout = 20000
                if (u.responseCode != 200) throw Exception("HTTP ${u.responseCode}")
                val total = if (u.contentLengthLong > 0) u.contentLengthLong else i.size
                val md = MessageDigest.getInstance("SHA-256")
                var done = 0L; var lastP = -1
                u.inputStream.use { ins -> f.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = ins.read(buf); if (n < 0) break
                        out.write(buf, 0, n); md.update(buf, 0, n); done += n
                        if (total > 0) { val p = (done * 100 / total).toInt().coerceIn(0, 100); if (p != lastP) { lastP = p; main.post { progress.intValue = p } } }
                    }
                } }
                val hex = md.digest().joinToString("") { "%02x".format(it) }
                if (i.sha.isNotEmpty() && !hex.equals(i.sha, true)) { f.delete(); throw Exception("file tải về bị lỗi") }
                ok = true
            } catch (e: Exception) { err = e.message ?: "lỗi" }
            main.post {
                if (ok) { progress.intValue = 101; install(app) }
                else { progress.intValue = -1; msg.value = "Tải không được ($err). Kiểm tra mạng rồi thử lại."; noInstaller.value = true }
            }
        }.start()
    }

    fun install(c: Context) {
        val app = c.applicationContext
        val f = file(app)
        if (!f.exists()) { progress.intValue = -1; return }
        if (Build.VERSION.SDK_INT >= 26 && !app.packageManager.canRequestPackageInstalls()) {
            msg.value = "Hãy bật \"Cho phép từ nguồn này\" cho SK Light, rồi quay lại bấm Cài đặt."
            try { app.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + app.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            catch (e: Exception) { noInstaller.value = true }
            return
        }
        try {
            val uri = FileProvider.getUriForFile(app, app.packageName + ".update", f)
            app.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
            msg.value = ""
        } catch (e: Exception) { msg.value = "Máy này không mở được trình cài đặt."; noInstaller.value = true }
    }

    fun copyLink(c: Context) {
        val i = info.value ?: return
        try {
            (c.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("SK Light", i.url))
            Notice.show(c, L.tr("Đã chép link tải."))
        } catch (e: Exception) {}
    }

    fun openBrowser(c: Context) {
        val i = info.value ?: return
        try { c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(i.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {}
    }
}

/** Thanh mỏng báo có bản mới. Ẩn khi xe đang chạy hoặc đang có cảnh báo rẽ. */
@Composable
fun UpdateBanner(modifier: Modifier = Modifier) {
    val i = Updater.info.value ?: return
    if (S.speedKmh.floatValue >= 10f || S.alertKind.intValue != 0) return
    Row(
        modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(Theme.r(8))).background(Pink).clickable { Updater.showDialog.value = true }.padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.SystemUpdate, null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        val p = Updater.progress.intValue
        T(if (p in 0..100) "Đang tải bản ${i.name}… $p%" else if (p == 101) "Đã tải xong bản ${i.name}. Chạm để cài đặt" else "Có bản mới ${i.name}. Chạm để cập nhật", 12, FontWeight.Bold, Color.White, Modifier.weight(1f))
    }
}

@Composable
fun UpdateDialog() {
    val i = Updater.info.value ?: return
    if (!Updater.showDialog.value) return
    val ctx = LocalContext.current
    val p = Updater.progress.intValue
    val mb = if (i.size > 0) " (${(i.size / 1048576)} MB)" else ""
    AlertDialog(
        onDismissRequest = { Updater.showDialog.value = false },
        title = { T("Có bản mới ${i.name}", 16, FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                T("Đang dùng ${S.VER}. Bản mới nặng khoảng${mb.ifEmpty { " vài chục MB" }}, nên dùng wifi.", 12, color = Grey)
                if (i.notes.isNotBlank()) T(i.notes, 13)
                if (p in 0..100) { T("Đang tải… $p%", 13, FontWeight.Bold, Pink); SkProgress(p / 100f) }
                if (p == 101) T("Đã tải xong. Bấm Cài đặt để tiếp tục.", 13, FontWeight.Bold, Pink)
                if (Updater.msg.value.isNotEmpty()) T(Updater.msg.value, 12, color = Pink)
                if (Updater.noInstaller.value) {
                    T("Hoặc tải bằng trình duyệt rồi tự cài:", 12, color = Grey)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlineBtn("Chép link", Modifier.weight(1f)) { Updater.copyLink(ctx) }
                        OutlineBtn("Mở trình duyệt", Modifier.weight(1f)) { Updater.openBrowser(ctx) }
                    }
                }
                TextButton(onClick = { Updater.skip(ctx) }) { T("Bỏ qua bản này", 12, color = Grey) }
            }
        },
        confirmButton = {
            if (p == 101) TextButton(onClick = { Updater.install(ctx) }) { T("Cài đặt", 14, FontWeight.Bold, Pink) }
            else if (p in 0..100) TextButton(onClick = {}) { T("Đang tải…", 14, FontWeight.Bold, Grey) }
            else TextButton(onClick = { Updater.download(ctx) }) { T("Cập nhật", 14, FontWeight.Bold, Pink) }
        },
        dismissButton = { TextButton(onClick = { Updater.showDialog.value = false }) { T("Để sau", 14, FontWeight.Bold) } }
    )
}
