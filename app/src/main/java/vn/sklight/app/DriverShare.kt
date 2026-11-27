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
import androidx.compose.foundation.layout.aspectRatio
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

    private val reply = Messenger(Handler(Looper.getMainLooper()) { m ->
        if (m.what == 4 && m.arg1 >= 0) try {
            app?.startActivity(Intent().setComponent(ComponentName(PKG, "$PKG.MainActivity")).putExtra("screen", m.arg1).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Throwable) {}
        true
    })

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(n: ComponentName?, b: IBinder?) { out = Messenger(b); push() }
        override fun onServiceDisconnected(n: ComponentName?) { out = null }
    }

    fun bind(c: Context) {
        app = c.applicationContext
        if (bound) return
        bound = try { c.applicationContext.bindService(Intent().setComponent(ComponentName(PKG, "$PKG.ShareService")), conn, Context.BIND_AUTO_CREATE) } catch (_: Throwable) { false }
    }

    fun unbind() {
        send(Message.obtain(null, 2))
        if (bound) try { app?.unbindService(conn) } catch (_: Throwable) {}
        bound = false; out = null
    }

    private fun send(m: Message) { try { out?.send(m) } catch (_: Throwable) {} }

    private fun push() {
        val s = surface ?: return
        if (w <= 0 || h <= 0) return
        send(Message.obtain(null, 1, w, h).apply { data = Bundle().apply { putParcelable("s", s) } })
    }

    fun setSurface(s: Surface?, ww: Int, hh: Int) {
        if (s == null) { send(Message.obtain(null, 2)); surface?.release(); surface = null; return }
        surface = s; w = ww; h = hh; push()
    }

    fun resize(ww: Int, hh: Int) { w = ww; h = hh; push() }

    fun tap(x: Float, y: Float) { send(Message.obtain(null, 3, x.toInt(), y.toInt()).apply { replyTo = reply }) }
}

/** Khung hình 16:9 chiếu màn Lái xe của SK Driver. */
@Composable
fun DriverMirror() {
    val ctx = LocalContext.current
    DisposableEffect(Unit) { DriverShare.bind(ctx); onDispose { DriverShare.unbind() } }
    AndroidView(
        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(Theme.r(10))),
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
}
