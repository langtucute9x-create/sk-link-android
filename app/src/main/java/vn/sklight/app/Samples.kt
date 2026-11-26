package vn.sklight.app

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Chụp mẫu màn hình app bản đồ (chủ dự án đề nghị 26/11): mỗi khi bộ đọc thấy biển hoặc dải chỉ đường, lưu lại đúng ảnh cửa sổ app bản đồ
 * kèm ghi chú app đọc ra gì, để gửi cho người sửa đối chiếu. Chỉ chạy khi người dùng bật công tắc; ảnh nằm trong máy, không tự gửi đi đâu.
 * Tối đa 80 ảnh (đủ thì xóa ảnh cũ nhất). Cùng một tình huống chỉ chụp lại sau 60 giây.
 */
object Samples {
    val on = mutableStateOf(false)
    val count = mutableIntStateOf(0)
    private const val MAX = 80
    private var lastKey = ""; private var lastAt = 0L
    private fun dir(c: Context) = File(c.filesDir, "mau").apply { mkdirs() }
    private fun files(c: Context) = (dir(c).listFiles { f -> f.name.endsWith(".jpg") } ?: emptyArray()).sortedBy { it.name }
    fun refresh(c: Context) { count.intValue = files(c).size }

    /** [key] = tình huống (số biển, tên đường, hướng…): đổi thì chụp ngay, không đổi thì 60 giây một lần. [note] gọi sau 1,5 giây để lấy cả kết quả đọc. */
    fun offer(c: Context, bmp: Bitmap, win: Rect?, key: String, note: () -> String) {
        if (!on.value) return
        val now = System.currentTimeMillis()
        if (now - lastAt < 3000 || (key == lastKey && now - lastAt < 60000)) return
        lastKey = key; lastAt = now
        try {
            val w0 = win?.let { Rect(it.left.coerceIn(0, bmp.width - 1), it.top.coerceIn(0, bmp.height - 1), it.right.coerceIn(1, bmp.width), it.bottom.coerceIn(1, bmp.height)) }
            val part = if (w0 != null && w0.width() > 50 && w0.height() > 50) Bitmap.createBitmap(bmp, w0.left, w0.top, w0.width(), w0.height()) else bmp
            val name = SimpleDateFormat("MMdd_HHmmss", Locale.US).format(Date(now))
            val app = c.applicationContext
            Thread {
                try {
                    val k = minOf(1f, 1280f / part.width)
                    val small = if (k < 1f) Bitmap.createScaledBitmap(part, (part.width * k).toInt(), (part.height * k).toInt(), true) else part
                    File(dir(app), "$name.jpg").outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 82, it) }
                    val all = files(app); for (f in all.take(maxOf(0, all.size - MAX))) f.delete()
                    Thread.sleep(1500)
                    val off = if (w0 != null) "cửa sổ bản đồ @${w0.left},${w0.top} ${w0.width()}x${w0.height()}, thu nhỏ x${"%.2f".format(k)}" else "cả màn hình"
                    File(dir(app), "ghi-chu.txt").appendText("$name.jpg | SK Light ${S.VER} | $off | $key\n${note()}\n\n")
                    count.intValue = files(app).size
                } catch (_: Exception) {}
            }.start()
        } catch (_: Exception) {}
    }

    fun clear(c: Context) { dir(c).listFiles()?.forEach { it.delete() }; count.intValue = 0; lastKey = "" }

    /** Gom ảnh + ghi chú thành một tệp zip rồi mở bảng chia sẻ của máy (Zalo, Gmail, Drive…). */
    fun share(c: Context): Boolean {
        return try {
            val out = File(File(c.cacheDir, "update").apply { mkdirs() }, "sk-light-mau.zip")
            ZipOutputStream(out.outputStream()).use { z ->
                for (f in dir(c).listFiles() ?: emptyArray()) { z.putNextEntry(ZipEntry(f.name)); f.inputStream().use { it.copyTo(z) }; z.closeEntry() }
            }
            val uri = FileProvider.getUriForFile(c, c.packageName + ".update", out)
            val i = Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            c.startActivity(Intent.createChooser(i, "Gửi mẫu").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: Exception) { false }
    }

    /** Chép ảnh mẫu vào thư viện ảnh của máy (thư mục Pictures/SKLight) để lấy ra bằng USB hoặc app khác. Trả về số ảnh đã chép. */
    fun toGallery(c: Context): Int {
        if (Build.VERSION.SDK_INT < 29) return 0
        var n = 0
        for (f in files(c)) try {
            val v = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "sklight_" + f.name); put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/SKLight")
            }
            val u = c.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v) ?: continue
            c.contentResolver.openOutputStream(u)?.use { o -> f.inputStream().use { it.copyTo(o) } }
            n++
        } catch (_: Exception) {}
        return n
    }

    fun notes(c: Context): String = try { File(dir(c), "ghi-chu.txt").readText().takeLast(6000) } catch (_: Exception) { "" }
}
