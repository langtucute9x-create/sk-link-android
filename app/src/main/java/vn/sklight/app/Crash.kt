package vn.sklight.app

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

/**
 * Ghi lại lỗi làm app tự thoát. Lần mở sau, app hiện nội dung lỗi (màn hình chữ thường, không dùng giao diện chính)
 * để người dùng sao chép gửi cho người làm app.
 */
object Crash {
    private const val FILE = "crash.txt"
    private var installed = false

    fun install(ctx: Context) {
        if (installed) return
        installed = true
        val app = ctx.applicationContext
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val ver = try { app.packageManager.getPackageInfo(app.packageName, 0).versionName } catch (x: Throwable) { "?" }
                val head = "SK Light " + ver + " | Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ") | " + Build.MANUFACTURER + " " + Build.MODEL + "\n"
                File(app.filesDir, FILE).writeText(head + Log.getStackTraceString(e).take(6000))
            } catch (x: Throwable) {}
            if (old != null) old.uncaughtException(t, e) else { android.os.Process.killProcess(android.os.Process.myPid()) }
        }
    }

    /** Nội dung lỗi của lần mở trước (đọc xong thì xóa), null nếu không có. */
    fun take(ctx: Context): String? {
        return try {
            val f = File(ctx.applicationContext.filesDir, FILE)
            if (!f.exists()) return null
            val s = f.readText()
            f.delete()
            if (s.isBlank()) null else s
        } catch (x: Throwable) { null }
    }

    fun show(a: Activity, report: String) {
        val d = a.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(18)); setBackgroundColor(Color.WHITE) }
        col.addView(TextView(a).apply { text = "SK Light bị lỗi ở lần mở trước"; textSize = 18f; setTextColor(0xFF172032.toInt()); setTypeface(typeface, Typeface.BOLD) })
        col.addView(TextView(a).apply { text = "Bấm Sao chép rồi dán gửi cho người làm app để sửa. Sau đó bấm Mở lại app."; textSize = 14f; setTextColor(0xFF4B5568.toInt()); setPadding(0, dp(8), 0, dp(12)) })
        val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(Button(a).apply {
            text = "Sao chép"
            setOnClickListener {
                try {
                    (a.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("SK Light", report))
                    Toast.makeText(a, "Đã sao chép", Toast.LENGTH_SHORT).show()
                } catch (x: Throwable) {}
            }
        })
        row.addView(Button(a).apply { text = "Mở lại app"; setOnClickListener { a.recreate() } })
        col.addView(row)
        col.addView(TextView(a).apply { text = report; textSize = 11f; setTextColor(0xFF172032.toInt()); typeface = Typeface.MONOSPACE; setTextIsSelectable(true); setPadding(0, dp(12), 0, 0) })
        val sv = ScrollView(a).apply { setBackgroundColor(Color.WHITE); fitsSystemWindows = true }
        sv.addView(col, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        a.setContentView(sv)
    }
}
