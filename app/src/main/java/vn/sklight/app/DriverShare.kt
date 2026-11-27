package vn.sklight.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.SurfaceTexture
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Xem trực tiếp màn Lái xe của SK Driver ngay trong SK Light. SK Light KHÔNG nối camera: nó đưa một mặt vẽ cho SK Driver (dịch vụ ShareService,
 * quyền chữ ký DRIVER_LINK) và SK Driver vẽ nguyên màn hình của nó lên đó. Chạm vào nút Cân chỉnh / Cài đặt trên hình thì mở SK Driver đúng màn đó.
 */
object DriverShare {
    private const val PKG = "vn.sklight.driver"
    private var out: Messenger? = null
    private var surface: Surface? = null
    private var w = 0
    private var h = 0
    private var bound = false
    private var app: Context? = null

    /** Trạng thái hiện đè lên khung hình; rỗng = đang có hình. */
    val state = mutableStateOf("Đang nối SK Driver…")
    private var lastOk = 0L

    private val reply = Messenger(Handler(Looper.getMainLooper()) { m ->
        if (m.what == 5) {
            val e = m.data?.getString("e") ?: ""
            state.value = if (e.isNotEmpty()) e else if (m.arg1 > 0) "" else "Đã nối SK Driver, đang chờ hình…"
            lastOk = android.os.SystemClock.elapsedRealtime()
        }
        if (m.what == 4 && m.arg1 >= 0) try {
            app?.startActivity(Intent().setComponent(ComponentName(PKG, "$PKG.MainActivity")).putExtra("screen", m.arg1).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Throwable) {}
        true
    })

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(n: ComponentName?, b: IBinder?) { out = Messenger(b); state.value = "Đã nối SK Driver, đang chờ hình…"; push() }
        override fun onServiceDisconnected(n: ComponentName?) { out = null; state.value = "SK Driver đã dừng. Chạm để mở lại." }
    }

    fun bind(c: Context) {
        app = c.applicationContext
        if (bound) return
        state.value = "Đang nối SK Driver…"; bindAt = android.os.SystemClock.elapsedRealtime()
        bound = try { c.applicationContext.bindService(Intent().setComponent(ComponentName(PKG, "$PKG.ShareService")), conn, Context.BIND_AUTO_CREATE) } catch (_: Throwable) { false }
        if (!bound) state.value = "Chưa nối được SK Driver. Cần SK Driver bản 0.4.2 trở lên: mở SK Driver, vào Cài đặt, bấm Kiểm tra cập nhật."
    }

    /** Gọi đều từ khung hình: SK Driver không báo gì quá 3 giây thì nói rõ. */
    fun watch() {
        val now = android.os.SystemClock.elapsedRealtime()
        if (bound && out != null && state.value.isEmpty() && now - lastOk > 3000) state.value = "SK Driver không còn gửi hình."
        if (bound && out == null && state.value.startsWith("Đang nối") && now - bindAt > 3000) state.value = "Chưa nối được SK Driver. Cần SK Driver bản 0.4.2 trở lên: mở SK Driver, vào Cài đặt, bấm Kiểm tra cập nhật."
    }
    private var bindAt = 0L

    fun unbind() {
        send(Message.obtain(null, 2))
        if (bound) try { app?.unbindService(conn) } catch (_: Throwable) {}
        bound = false; out = null
    }

    private fun send(m: Message) { try { out?.send(m) } catch (_: Throwable) {} }

    private fun push() {
        val s = surface ?: return
        if (w <= 0 || h <= 0) return
        send(Message.obtain(null, 1, w, h).apply { data = Bundle().apply { putParcelable("s", s) }; replyTo = reply })
    }

    fun setSurface(s: Surface?, ww: Int, hh: Int) {
        if (s == null) { send(Message.obtain(null, 2)); surface?.release(); surface = null; return }
        surface = s; w = ww; h = hh; push()
    }

    fun resize(ww: Int, hh: Int) { w = ww; h = hh; push() }

    fun tap(x: Float, y: Float) {
        // chưa nối được hoặc SK Driver đã dừng: chạm là mở SK Driver
        if (out == null) { try { app?.packageManager?.getLaunchIntentForPackage(PKG)?.let { app?.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } } catch (_: Throwable) {}; return }
        send(Message.obtain(null, 3, x.toInt(), y.toInt()).apply { replyTo = reply })
    }
}

/** Khung hình 16:9 chiếu màn Lái xe của SK Driver. */
@Composable
fun DriverMirror() {
    val ctx = LocalContext.current
    DisposableEffect(Unit) { DriverShare.bind(ctx); onDispose { DriverShare.unbind() } }
    LaunchedEffect(Unit) { while (true) { delay(1000); DriverShare.watch() } }
    val st = DriverShare.state.value
    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(Theme.r(10))).background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                TextureView(c).apply {
                    isOpaque = true
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) { DriverShare.setSurface(Surface(st), w, h) }
                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) { DriverShare.resize(w, h) }
                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean { DriverShare.setSurface(null, 0, 0); return true }
                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                    }
                    var lx = 0f; var ly = 0f
                    setOnTouchListener { _, e -> if (e.actionMasked == MotionEvent.ACTION_DOWN || e.actionMasked == MotionEvent.ACTION_UP) { lx = e.x; ly = e.y }; false }
                    setOnClickListener { DriverShare.tap(lx, ly) }
                }
            }
        )
        if (st.isNotEmpty()) Box(Modifier.fillMaxSize().background(Color(0xCC000000)).padding(16.dp), contentAlignment = Alignment.Center) {
            T(st, 13, FontWeight.Bold, Color.White)
        }
    }
}
