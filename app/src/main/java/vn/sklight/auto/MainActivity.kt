package vn.sklight.auto

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.car.app.connection.CarConnection

/** Man hinh tren dien thoai: gui dia chi len xe, quan ly trang va video, huong dan cai. */
class MainActivity : ComponentActivity() {

    private lateinit var status: TextView
    private lateinit var sitesBox: LinearLayout
    private lateinit var vidsBox: LinearLayout

    private val pick = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNullOrEmpty()) return@registerForActivityResult
        val list = ArrayList(Store.videos(this))
        for (u in uris) {
            try {
                contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
            }
            val s = u.toString()
            if (list.none { it.uri == s }) list.add(Vid(nameOf(u), s))
        }
        Store.saveVideos(this, list)
        fillVideos()
        Hub.renderer?.refreshHome()
    }

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun nameOf(u: Uri): String {
        try {
            contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val n = c.getString(0)
                    if (!n.isNullOrBlank()) return n
                }
            }
        } catch (e: Exception) {
        }
        return u.lastPathSegment ?: "Video"
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = text
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        t.setTextColor(color)
        if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
        return t
    }

    private fun section(parent: LinearLayout, text: String) {
        val t = label(text, 13f, PINK, true)
        t.setPadding(0, dp(22), 0, dp(6))
        parent.addView(t)
    }

    private fun button(text: String, f: () -> Unit): Button {
        val b = Button(this)
        b.text = text
        b.isAllCaps = false
        b.setOnClickListener { f() }
        return b
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(18), dp(18), dp(18), dp(28))
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(BG)
        scroll.fitsSystemWindows = true
        scroll.addView(col, ViewGroup.LayoutParams(MATCH, WRAP))
        setContentView(scroll)

        col.addView(label("SK Auto", 26f, PINK, true))
        status = label("Chưa kết nối xe", 15f, Color.LTGRAY)
        status.setPadding(0, dp(4), 0, 0)
        col.addView(status)

        // --- gui len xe
        section(col, "MỞ TRÊN MÀN HÌNH XE")
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val input = EditText(this)
        input.hint = "Từ khóa YouTube hoặc địa chỉ web"
        input.setSingleLine(true)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        input.imeOptions = EditorInfo.IME_ACTION_GO
        input.setTextColor(Color.WHITE)
        input.setHintTextColor(Color.GRAY)
        val go = { sendToCar(input.text.toString()) }
        input.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_GO) {
                go()
                true
            } else false
        }
        row.addView(input, LinearLayout.LayoutParams(0, WRAP, 1f))
        row.addView(button("Mở") { go() })
        col.addView(row)
        val quick = LinearLayout(this)
        quick.orientation = LinearLayout.HORIZONTAL
        quick.addView(button("Trang chủ") { Hub.renderer?.goHome() ?: toast("Chưa kết nối xe") }, LinearLayout.LayoutParams(0, WRAP, 1f))
        quick.addView(button("Phát / Dừng") { Hub.renderer?.togglePlay() ?: toast("Chưa kết nối xe") }, LinearLayout.LayoutParams(0, WRAP, 1f))
        quick.addView(button("Quay lại") { Hub.renderer?.goBack() ?: toast("Chưa kết nối xe") }, LinearLayout.LayoutParams(0, WRAP, 1f))
        col.addView(quick)

        // --- trang web
        section(col, "TRANG WEB")
        sitesBox = LinearLayout(this)
        sitesBox.orientation = LinearLayout.VERTICAL
        col.addView(sitesBox)
        col.addView(button("+ Thêm trang") { addSiteDialog() })
        col.addView(button("Đăng nhập YouTube / TikTok trên điện thoại") {
            startActivity(Intent(this, BrowserActivity::class.java).putExtra("url", "https://m.youtube.com/"))
        })
        val note = label(
            "Đăng nhập ở đây một lần, màn hình xe dùng chung tài khoản đó.",
            13f, Color.GRAY
        )
        col.addView(note)

        // --- video trong may
        section(col, "VIDEO TRONG MÁY")
        vidsBox = LinearLayout(this)
        vidsBox.orientation = LinearLayout.VERTICAL
        col.addView(vidsBox)
        col.addView(button("+ Chọn video") {
            try {
                pick.launch(arrayOf("video/*"))
            } catch (e: Exception) {
                toast("Không mở được trình chọn tệp")
            }
        })

        // --- huong dan
        section(col, "CÀI ĐẶT LẦN ĐẦU")
        col.addView(
            label(
                "1. Mở Cài đặt Android Auto trên điện thoại, chạm 10 lần vào dòng Phiên bản để bật chế độ nhà phát triển.\n" +
                    "2. Bấm dấu ba chấm, chọn Cài đặt cho nhà phát triển, bật Nguồn không xác định (Unknown sources).\n" +
                    "3. App này phải được máy ghi nhận là cài từ Google Play thì Android Auto mới hiện. Cài file APK bằng công cụ UnknownAuto (kèm Shizuku), không cài bằng trình quản lý tệp.\n" +
                    "4. Cắm cáp Type-C vào cổng USB của xe, mở Android Auto, tìm biểu tượng SK Auto trong danh sách ứng dụng.\n" +
                    "5. Google Maps và Waze vẫn chạy riêng trên Android Auto như bình thường.",
                14f, Color.LTGRAY
            )
        )
        col.addView(button("Mở cài đặt Android Auto") { openAaSettings() })
        val warn = label(
            "Video chỉ dành cho người ngồi cùng xe. Người lái không xem khi xe đang chạy.",
            13f, Color.GRAY
        )
        warn.setPadding(0, dp(14), 0, 0)
        col.addView(warn)

        fillSites()
        fillVideos()

        CarConnection(this).type.observe(this) { t ->
            status.text = when (t) {
                CarConnection.CONNECTION_TYPE_PROJECTION -> "Đang kết nối Android Auto"
                CarConnection.CONNECTION_TYPE_NATIVE -> "Đang chạy trên màn hình Android của xe"
                else -> "Chưa kết nối xe"
            }
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun sendToCar(text: String) {
        if (text.isBlank()) return
        val url = Hub.toUrl(text)
        val r = Hub.renderer
        if (r != null) {
            r.load(url)
            toast("Đã mở trên màn hình xe")
        } else {
            Hub.pending = url
            toast("Sẽ mở khi kết nối xe")
        }
    }

    private fun openAaSettings() {
        try {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:com.google.android.projection.gearhead"))
            )
        } catch (e: Exception) {
            toast("Không tìm thấy Android Auto trên máy này")
        }
    }

    private fun itemRow(title: String, sub: String, onOpen: () -> Unit, onDelete: () -> Unit): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val texts = LinearLayout(this)
        texts.orientation = LinearLayout.VERTICAL
        texts.addView(label(title, 16f, Color.WHITE))
        if (sub.isNotEmpty()) {
            val s = label(sub, 12f, Color.GRAY)
            s.maxLines = 1
            texts.addView(s)
        }
        texts.setOnClickListener { onOpen() }
        texts.setPadding(0, dp(8), 0, dp(8))
        row.addView(texts, LinearLayout.LayoutParams(0, WRAP, 1f))
        row.addView(button("Xóa") { onDelete() })
        return row
    }

    private fun fillSites() {
        sitesBox.removeAllViews()
        val list = Store.sites(this)
        for ((i, s) in list.withIndex()) {
            sitesBox.addView(itemRow(s.name, s.url, { sendToCar(s.url) }, {
                val next = ArrayList(Store.sites(this))
                if (i < next.size) next.removeAt(i)
                Store.saveSites(this, next)
                fillSites()
                Hub.renderer?.refreshHome()
            }))
        }
    }

    private fun fillVideos() {
        vidsBox.removeAllViews()
        val list = Store.videos(this)
        if (list.isEmpty()) {
            vidsBox.addView(label("Chưa có video nào", 14f, Color.GRAY))
            return
        }
        for ((i, v) in list.withIndex()) {
            vidsBox.addView(itemRow(v.name, "", {
                val r = Hub.renderer
                if (r != null) r.openVideo(i) else toast("Chưa kết nối xe")
            }, {
                val next = ArrayList(Store.videos(this))
                if (i < next.size) next.removeAt(i)
                Store.saveVideos(this, next)
                fillVideos()
                Hub.renderer?.refreshHome()
            }))
        }
    }

    private fun addSiteDialog() {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(8), dp(20), 0)
        val name = EditText(this)
        name.hint = "Tên hiển thị"
        name.setSingleLine(true)
        val url = EditText(this)
        url.hint = "Địa chỉ, ví dụ vtvgo.vn"
        url.setSingleLine(true)
        url.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        box.addView(name)
        box.addView(url)
        AlertDialog.Builder(this)
            .setTitle("Thêm trang")
            .setView(box)
            .setPositiveButton("Lưu") { _, _ ->
                val u = url.text.toString().trim()
                if (u.isNotEmpty()) {
                    val full = if (u.startsWith("http://") || u.startsWith("https://")) u else "https://$u"
                    val n = name.text.toString().trim().ifEmpty { Uri.parse(full).host ?: full }
                    val next = ArrayList(Store.sites(this))
                    next.add(Site(n, full))
                    Store.saveSites(this, next)
                    fillSites()
                    Hub.renderer?.refreshHome()
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    companion object {
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        private val PINK = Color.parseColor("#FE2CAA")
        private val BG = Color.parseColor("#101014")
    }
}
