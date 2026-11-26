package vn.sklight.auto

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback

/**
 * Trinh duyet tren dien thoai, dung chung cookie voi man hinh xe.
 * Dang nhap YouTube / TikTok o day (co ban phim), tren xe khong can dang nhap lai.
 */
class BrowserActivity : ComponentActivity() {

    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setBackgroundColor(Color.parseColor("#101014"))
        col.fitsSystemWindows = true

        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        val input = EditText(this)
        input.setSingleLine(true)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        input.imeOptions = EditorInfo.IME_ACTION_GO
        input.setTextColor(Color.WHITE)
        input.setHintTextColor(Color.GRAY)
        input.hint = "Địa chỉ web"
        bar.addView(input, LinearLayout.LayoutParams(0, WRAP, 1f))
        val go = Button(this)
        go.text = "Đi"
        go.isAllCaps = false
        bar.addView(go)
        val yt = Button(this)
        yt.text = "YouTube"
        yt.isAllCaps = false
        bar.addView(yt)
        val tt = Button(this)
        tt.text = "TikTok"
        tt.isAllCaps = false
        bar.addView(tt)
        col.addView(bar, LinearLayout.LayoutParams(MATCH, WRAP))

        web = WebView(this)
        val st = web.settings
        st.javaScriptEnabled = true
        st.domStorageEnabled = true
        st.userAgentString = Renderer.UA_MOBILE
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(web, true)
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val scheme = request?.url?.scheme ?: return true
                return scheme != "http" && scheme != "https"
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (url != null) input.setText(url)
                CookieManager.getInstance().flush()
            }
        }
        col.addView(web, LinearLayout.LayoutParams(MATCH, 0, 1f))
        setContentView(col)

        val open = { text: String ->
            if (text.isNotBlank()) web.loadUrl(Hub.toUrl(text))
        }
        go.setOnClickListener { open(input.text.toString()) }
        yt.setOnClickListener { open("https://m.youtube.com/") }
        tt.setOnClickListener { open("https://www.tiktok.com/login") }
        input.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_GO) {
                open(input.text.toString())
                true
            } else false
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else finish()
            }
        })

        open(intent.getStringExtra("url") ?: "https://m.youtube.com/")
    }

    override fun onDestroy() {
        CookieManager.getInstance().flush()
        try {
            (web.parent as? ViewGroup)?.removeView(web)
            web.destroy()
        } catch (e: Exception) {
        }
        super.onDestroy()
    }

    companion object {
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
