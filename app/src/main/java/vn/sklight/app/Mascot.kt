package vn.sklight.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.View
import android.webkit.WebView

/**
 * Năm nhân vật 3D động của nút nổi, dùng lại đúng hình SVG của nút nổi Trợ lý AI ở SK Core
 * (assets/mascot_*.svg). Chuyển động: lơ lửng, chớp mắt, lấp lánh, miệng cười nhấp nhô; chạm vào thì nhảy lên.
 * Vẽ bằng WebView trong suốt, không tải gì từ mạng.
 */
object Mascot {
    val IDS = listOf("", "robo-bi", "tia-sang", "mimi", "robo-sk-tron", "robo-sk")
    val NAMES = listOf("Logo SK", "Robo Bi", "Tia Sáng", "Mimi", "Robo SK tròn", "Robo SK")

    private val CSS = """
html,body{margin:0;height:100%;background:transparent;overflow:hidden}
#w{width:100%;height:100%;transform-origin:50% 100%}
svg{width:100%;height:100%;display:block}
.mc-fl{animation:fl 3.2s ease-in-out infinite}
.mc-bl{transform-box:fill-box;transform-origin:center;animation:bl 4.2s infinite}
.mc-tw{transform-box:fill-box;transform-origin:center;animation:tw 2.4s ease-in-out infinite}
.mc-sm{transform-box:fill-box;transform-origin:center;animation:sm 5.5s ease-in-out infinite}
@keyframes fl{0%,100%{transform:translateY(0)}50%{transform:translateY(-7px)}}
@keyframes bl{0%,92%,100%{transform:scaleY(1)}95%{transform:scaleY(.08)}}
@keyframes tw{0%,100%{opacity:.25;transform:scale(.6)}50%{opacity:1;transform:scale(1.15)}}
@keyframes sm{0%,72%,100%{transform:scale(1,1)}80%,90%{transform:scale(1.3,1.5)}}
.jump{animation:jp .65s ease-out}
@keyframes jp{0%{transform:translateY(0) scale(1,1)}25%{transform:translateY(-12%) scale(.94,1.1)}55%{transform:translateY(0) scale(1.1,.9)}80%{transform:scale(.97,1.04)}100%{transform:scale(1,1)}}
"""

    private fun html(c: Context, id: String): String? = try {
        val svg = c.assets.open("mascot_$id.svg").bufferedReader().use { it.readText() }
        "<html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><style>$CSS</style></head><body><div id=\"w\">$svg</div>" +
            "<script>function jump(){var e=document.getElementById('w');e.classList.remove('jump');void e.offsetWidth;e.classList.add('jump')}</script></body></html>"
    } catch (_: Exception) { null }

    /** Trả về WebView đã nạp nhân vật [idx] (1..5), hoặc null nếu máy không có WebView. Không nhận chạm: lớp phủ bên trên lo việc kéo / bấm. */
    @SuppressLint("SetJavaScriptEnabled")
    fun view(c: Context, idx: Int): WebView? {
        val id = IDS.getOrNull(idx)?.takeIf { it.isNotEmpty() } ?: return null
        val h = html(c, id) ?: return null
        return try {
            WebView(c).apply {
                setBackgroundColor(Color.TRANSPARENT)
                isVerticalScrollBarEnabled = false; isHorizontalScrollBarEnabled = false
                isClickable = false; isFocusable = false; isLongClickable = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false; settings.allowContentAccess = false
                loadDataWithBaseURL(null, h, "text/html", "utf-8", null)
            }
        } catch (_: Throwable) { null }
    }

    fun jump(v: WebView?) { try { v?.evaluateJavascript("jump()", null) } catch (_: Throwable) {} }
    fun free(v: WebView?) { try { v?.destroy() } catch (_: Throwable) {} }
}
