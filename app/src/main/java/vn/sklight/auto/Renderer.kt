package vn.sklight.auto

import android.annotation.SuppressLint
import android.app.Presentation
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.VideoView
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import java.io.ByteArrayInputStream

/**
 * Ve noi dung len be mat Android Auto cap cho app.
 *
 * Cach lam: tao mot man hinh ao (VirtualDisplay) xuat thang ra be mat cua xe, dat mot
 * Presentation len man hinh ao do, ben trong la WebView (YouTube / TikTok ban web) va
 * VideoView (video trong may). Android Auto chi bao "cham o (x, y)", "vuot (dx, dy)"...
 * nen app tu dung lai thanh su kien cham gui vao cac View.
 *
 * Moi ham deu chay tren luong chinh.
 */
class Renderer(car: CarContext) : SurfaceCallback {

    private val app: Context = car.applicationContext
    private val main = Handler(Looper.getMainLooper())

    private var vd: VirtualDisplay? = null
    private var pres: Presentation? = null
    private var root: FrameLayout? = null
    private var web: WebView? = null
    private var session: MediaSession? = null

    private var videoBox: FrameLayout? = null
    private var video: VideoView? = null
    private var videoTitle: TextView? = null
    private var vidIndex = 0

    private var customView: View? = null
    private var customCb: WebChromeClient.CustomViewCallback? = null

    private var w = 0
    private var h = 0
    private var dpi = 160
    private var lastUrl: String? = null

    // Trang thai cu chi vuot dang dung lai tu onScroll.
    private var dragging = false
    private var dragDown = 0L
    private var px = 0f
    private var py = 0f
    private val endDrag = Runnable {
        if (dragging) {
            send(MotionEvent.ACTION_UP, dragDown, SystemClock.uptimeMillis(), px, py)
            dragging = false
        }
    }

    // ---------------------------------------------------------------- be mat

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        val s = surfaceContainer.surface ?: return
        val nw = surfaceContainer.width
        val nh = surfaceContainer.height
        val nd = if (surfaceContainer.dpi > 0) surfaceContainer.dpi else 160
        if (nw <= 0 || nh <= 0) return
        try {
            val cur = vd
            if (cur != null && nw == w && nh == h && nd == dpi && pres?.isShowing == true) {
                // Cung kich thuoc: chi gan lai be mat, giu nguyen trang dang xem.
                cur.surface = s
                return
            }
            // Kich thuoc doi (hoac lan dau): dung lai tu dau, mo lai trang cu.
            teardownDisplay()
            w = nw
            h = nh
            dpi = nd
            val dm = app.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            vd = dm.createVirtualDisplay(
                "sk-auto", w, h, dpi, s,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION or DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
            )
            build()
        } catch (e: Throwable) {
            Log.e(TAG, "onSurfaceAvailable", e)
        }
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        // Giu WebView song de tieng van phat khi nguoi dung chuyen sang app khac tren xe.
        try {
            vd?.surface = null
        } catch (e: Throwable) {
            Log.w(TAG, "onSurfaceDestroyed", e)
        }
    }

    private fun build() {
        val d = vd?.display ?: return
        val p = Presentation(app, d, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val fl = FrameLayout(p.context)
        fl.setBackgroundColor(Color.BLACK)
        p.setContentView(fl)
        p.setCancelable(false)
        p.show()
        pres = p
        root = fl
        val wv = makeWeb(p.context)
        fl.addView(wv, FrameLayout.LayoutParams(MATCH, MATCH))
        web = wv
        ensureSession()
        val u = Hub.takePending() ?: lastUrl
        if (u != null) load(u) else goHome()
    }

    private fun teardownDisplay() {
        main.removeCallbacks(endDrag)
        dragging = false
        try {
            video?.stopPlayback()
        } catch (e: Throwable) {
        }
        video = null
        videoBox = null
        videoTitle = null
        customView = null
        customCb = null
        try {
            web?.let {
                (it.parent as? ViewGroup)?.removeView(it)
                it.destroy()
            }
        } catch (e: Throwable) {
        }
        web = null
        root = null
        try {
            pres?.dismiss()
        } catch (e: Throwable) {
        }
        pres = null
        try {
            vd?.release()
        } catch (e: Throwable) {
        }
        vd = null
    }

    fun release() {
        main.removeCallbacksAndMessages(null)
        teardownDisplay()
        try {
            session?.release()
        } catch (e: Throwable) {
        }
        session = null
    }

    // ---------------------------------------------------------------- trinh duyet

    @SuppressLint("SetJavaScriptEnabled")
    private fun makeWeb(ctx: Context): WebView {
        val wv = WebView(ctx)
        wv.setBackgroundColor(Color.BLACK)
        val st = wv.settings
        st.javaScriptEnabled = true
        st.domStorageEnabled = true
        st.mediaPlaybackRequiresUserGesture = false
        st.useWideViewPort = true
        st.loadWithOverviewMode = true
        st.setSupportZoom(true)
        st.builtInZoomControls = true
        st.displayZoomControls = false
        st.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        st.userAgentString = UA_MOBILE
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(wv, true)

        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val u = request?.url ?: return true
                val scheme = u.scheme ?: return true
                // Chan cac lien ket "mo bang app" (intent://, market://, vnd.youtube...).
                if (scheme != "http" && scheme != "https") return true
                if (u.host == HomePage.HOST) {
                    val seg = u.pathSegments
                    if (seg.size == 2 && seg[0] == "v") {
                        openVideo(seg[1].toIntOrNull() ?: 0)
                        return true
                    }
                }
                return false
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val u = request?.url ?: return null
                if (u.host != HomePage.HOST) return null
                val path = u.path
                val body = if (path.isNullOrEmpty() || path == "/") HomePage.html(app) else ""
                return WebResourceResponse("text/html", "utf-8", ByteArrayInputStream(body.toByteArray(Charsets.UTF_8)))
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (url != null && url.startsWith("http") && !url.startsWith(HomePage.URL)) lastUrl = url
            }
        }

        wv.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View?, callback: WebChromeClient.CustomViewCallback?) {
                if (view == null) return
                hideCustom()
                customView = view
                customCb = callback
                root?.addView(view, FrameLayout.LayoutParams(MATCH, MATCH))
            }

            override fun onHideCustomView() {
                hideCustom()
            }

            override fun getDefaultVideoPoster(): Bitmap? = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }
        return wv
    }

    private fun hideCustom() {
        val v = customView ?: return
        customView = null
        try {
            root?.removeView(v)
        } catch (e: Throwable) {
        }
        try {
            customCb?.onCustomViewHidden()
        } catch (e: Throwable) {
        }
        customCb = null
    }

    private fun isHome(): Boolean = web?.url?.startsWith(HomePage.URL) == true

    /** Mo mot dia chi tren man hinh xe. Chua co be mat thi de danh, co la mo. */
    fun load(url: String) {
        val wv = web
        if (wv == null) {
            Hub.pending = url
            return
        }
        closeVideo()
        hideCustom()
        val host = Uri.parse(url).host ?: ""
        // TikTok ban may tinh dung duoc phim mui ten de chuyen video; ban di dong toan moi mo app.
        wv.settings.userAgentString = if (host.contains("tiktok")) UA_DESKTOP else UA_MOBILE
        wv.loadUrl(url)
    }

    fun goHome() {
        val wv = web ?: return
        closeVideo()
        hideCustom()
        wv.loadUrl(HomePage.URL)
    }

    /** Goi tu dien thoai khi danh sach trang / video thay doi. */
    fun refreshHome() {
        if (isHome() && !videoShowing()) web?.reload()
    }

    fun goBack() {
        if (customView != null) {
            hideCustom()
            return
        }
        if (videoShowing()) {
            closeVideo()
            return
        }
        val wv = web ?: return
        if (wv.canGoBack()) wv.goBack() else goHome()
    }

    /** Nut len / xuong tren man hinh xe: video truoc / sau, hoac cuon trang. */
    fun step(dir: Int) {
        if (videoShowing()) {
            openVideo(vidIndex + dir)
            return
        }
        val wv = web ?: return
        val host = Uri.parse(wv.url ?: "").host ?: ""
        if (host.contains("tiktok")) {
            val code = if (dir > 0) KeyEvent.KEYCODE_DPAD_DOWN else KeyEvent.KEYCODE_DPAD_UP
            wv.requestFocus()
            wv.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            wv.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        } else {
            swipe(0f, -dir * h * 0.6f)
        }
    }

    fun togglePlay() {
        val v = video
        if (videoShowing() && v != null) {
            if (v.isPlaying) v.pause() else v.start()
            return
        }
        web?.evaluateJavascript(
            "(function(){var v=document.querySelector('video');if(!v)return;if(v.paused)v.play();else v.pause();})()",
            null
        )
    }

    // ---------------------------------------------------------------- video trong may

    private fun videoShowing(): Boolean = videoBox?.visibility == View.VISIBLE

    private fun ensureVideoBox(): FrameLayout? {
        videoBox?.let { return it }
        val ctx = pres?.context ?: return null
        val host = root ?: return null
        val box = FrameLayout(ctx)
        box.setBackgroundColor(Color.BLACK)
        box.isClickable = true

        val vv = VideoView(ctx)
        box.addView(vv, FrameLayout.LayoutParams(MATCH, MATCH, Gravity.CENTER))

        val title = TextView(ctx)
        title.setTextColor(Color.WHITE)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        title.setPadding(24, 12, 24, 12)
        title.setBackgroundColor(0x66000000)
        title.maxLines = 1
        box.addView(title, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP))

        val bar = LinearLayout(ctx)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER
        bar.setBackgroundColor(0x99000000.toInt())
        fun btn(text: String, f: () -> Unit) {
            val b = Button(ctx)
            b.text = text
            b.isAllCaps = false
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            b.setOnClickListener { f() }
            bar.addView(b, LinearLayout.LayoutParams(0, WRAP, 1f))
        }
        btn("Trước") { openVideo(vidIndex - 1) }
        btn("-10s") { seekBy(-10000) }
        btn("Phát / Dừng") { togglePlay() }
        btn("+10s") { seekBy(10000) }
        btn("Sau") { openVideo(vidIndex + 1) }
        btn("Đóng") { closeVideo() }
        box.addView(bar, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM))

        vv.setOnCompletionListener { openVideo(vidIndex + 1) }
        vv.setOnErrorListener { _, _, _ ->
            title.text = "Không phát được video này"
            true
        }

        box.visibility = View.GONE
        host.addView(box, FrameLayout.LayoutParams(MATCH, MATCH))
        videoBox = box
        video = vv
        videoTitle = title
        return box
    }

    /** Phat video thu i trong danh sach (quay vong). */
    fun openVideo(i: Int) {
        val list = Store.videos(app)
        if (list.isEmpty()) return
        val box = ensureVideoBox() ?: return
        val vv = video ?: return
        val n = list.size
        vidIndex = ((i % n) + n) % n
        val item = list[vidIndex]
        hideCustom()
        // Dung tieng trang web dang phat.
        web?.evaluateJavascript("(function(){var v=document.querySelector('video');if(v)v.pause();})()", null)
        videoTitle?.text = item.name
        box.visibility = View.VISIBLE
        box.bringToFront()
        try {
            vv.setVideoURI(Uri.parse(item.uri))
            vv.start()
        } catch (e: Throwable) {
            Log.e(TAG, "openVideo", e)
            videoTitle?.text = "Không mở được video này"
        }
    }

    private fun closeVideo() {
        val box = videoBox ?: return
        if (box.visibility != View.VISIBLE) return
        try {
            video?.stopPlayback()
        } catch (e: Throwable) {
        }
        box.visibility = View.GONE
    }

    private fun seekBy(ms: Int) {
        val v = video ?: return
        try {
            val dur = v.duration
            var t = v.currentPosition + ms
            if (t < 0) t = 0
            if (dur > 0 && t > dur) t = dur
            v.seekTo(t)
        } catch (e: Throwable) {
        }
    }

    // ---------------------------------------------------------------- phim media (vo lang)

    private fun ensureSession() {
        if (session != null) return
        try {
            val s = MediaSession(app, "sk-auto")
            s.setCallback(object : MediaSession.Callback() {
                override fun onPlay() = togglePlay()
                override fun onPause() = togglePlay()
                override fun onSkipToNext() = step(1)
                override fun onSkipToPrevious() = step(-1)
            })
            s.setPlaybackState(
                PlaybackState.Builder()
                    .setActions(
                        PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                            PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS
                    )
                    .setState(PlaybackState.STATE_PLAYING, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
                    .build()
            )
            s.isActive = true
            session = s
        } catch (e: Throwable) {
            Log.w(TAG, "session", e)
        }
    }

    // ---------------------------------------------------------------- cham / vuot tu man hinh xe

    private fun send(action: Int, down: Long, time: Long, x: Float, y: Float) {
        val target: View = pres?.window?.decorView ?: root ?: return
        val e = MotionEvent.obtain(down, time, action, x, y, 0)
        e.source = InputDevice.SOURCE_TOUCHSCREEN
        try {
            target.dispatchTouchEvent(e)
        } catch (t: Throwable) {
            Log.w(TAG, "send", t)
        } finally {
            e.recycle()
        }
    }

    override fun onClick(x: Float, y: Float) {
        if (dragging) {
            main.removeCallbacks(endDrag)
            endDrag.run()
        }
        val t = SystemClock.uptimeMillis()
        send(MotionEvent.ACTION_DOWN, t, t, x, y)
        main.postDelayed({ send(MotionEvent.ACTION_UP, t, SystemClock.uptimeMillis(), x, y) }, 60)
    }

    override fun onScroll(distanceX: Float, distanceY: Float) {
        val now = SystemClock.uptimeMillis()
        if (!dragging) {
            px = w / 2f
            py = h / 2f
            dragDown = now
            send(MotionEvent.ACTION_DOWN, now, now, px, py)
            dragging = true
        }
        // distance = vi tri cu - vi tri moi, nen ngon tay di nguoc dau.
        px -= distanceX
        py -= distanceY
        send(MotionEvent.ACTION_MOVE, dragDown, now, px, py)
        main.removeCallbacks(endDrag)
        main.postDelayed(endDrag, 150)
    }

    override fun onFling(velocityX: Float, velocityY: Float) {
        if (dragging) {
            // Dang keo do: nha tay ngay, View tu tinh quan tinh tu cac lan MOVE.
            main.removeCallbacks(endDrag)
            endDrag.run()
            return
        }
        swipe(velocityX * 0.15f, velocityY * 0.15f)
    }

    override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {
        if (videoShowing()) return
        val wv = web ?: return
        try {
            val f = scaleFactor.coerceIn(0.5f, 2f)
            if (f != 1f) wv.zoomBy(f)
        } catch (e: Throwable) {
        }
    }

    /** Dung mot cu vuot nhanh tu giua man hinh, ngon tay di (dx, dy) diem anh. */
    private fun swipe(dx0: Float, dy0: Float) {
        if (dragging || w <= 0 || h <= 0) return
        val dx = dx0.coerceIn(-w * 0.45f, w * 0.45f)
        val dy = dy0.coerceIn(-h * 0.45f, h * 0.45f)
        val x0 = w / 2f
        val y0 = h / 2f
        val t0 = SystemClock.uptimeMillis()
        send(MotionEvent.ACTION_DOWN, t0, t0, x0, y0)
        val n = 8
        for (i in 1..n) {
            main.postDelayed({
                send(MotionEvent.ACTION_MOVE, t0, SystemClock.uptimeMillis(), x0 + dx * i / n, y0 + dy * i / n)
            }, 12L * i)
        }
        main.postDelayed({
            send(MotionEvent.ACTION_UP, t0, SystemClock.uptimeMillis(), x0 + dx, y0 + dy)
        }, 12L * n + 12L)
    }

    companion object {
        private const val TAG = "SKAuto"
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

        // Khong co "; wv" de Google cho dang nhap trong WebView.
        const val UA_MOBILE =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
        const val UA_DESKTOP =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    }
}
