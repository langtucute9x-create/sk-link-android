package vn.sklight.driver

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.view.Surface

/**
 * Chia màn Lái xe cho SK Light: SK Light nối vào dịch vụ này, đưa sang một mặt vẽ (Surface); SK Driver vẽ NGUYÊN màn Lái xe ([DriveView]) lên đó
 * mỗi khi có khung hình mới. Chỉ SK Driver nối camera (nhiều camera hành trình chỉ cho một app xem). Bảo vệ bằng quyền chữ ký DRIVER_LINK.
 * Tin nhắn: 1 = mặt vẽ mới (data "s", arg1 rộng, arg2 cao), 2 = thôi vẽ, 3 = người dùng chạm (arg1 x, arg2 y) → trả lời 4 (arg1 = màn cần mở
 * trong SK Driver: 0 lái xe, 1 cân chỉnh, 2 cài đặt; -1 = không làm gì). SK Light tự mở SK Driver vì app nền không được tự bật màn hình.
 */
class ShareService : Service() {
    private val ht = HandlerThread("mirror").apply { start() }
    private var surface: Surface? = null
    private var sw = 0
    private var sh = 0
    private var view: DriveView? = null
    private val DRAW = 10

    private var peer: Messenger? = null
    private var drawn = 0
    private var err = ""

    private val h = Handler(ht.looper) { m ->
        try {
            when (m.what) {
                1 -> {
                    peer = m.replyTo ?: peer
                    @Suppress("DEPRECATION")
                    surface = m.data.getParcelable("s"); sw = m.arg1; sh = m.arg2; drawn = 0; err = ""
                    if (view == null) view = DriveView(this, {}, {})
                    Engine.mirror = { fr -> view?.show(fr); kick() }
                    kick()
                }
                2 -> { Engine.mirror = null; surface = null }
                3 -> {
                    val v = view
                    val scr = if (!DriverService.running) 0 else v?.hitAt(m.arg1.toFloat(), m.arg2.toFloat()) ?: -1
                    try { m.replyTo?.send(Message.obtain(null, 4, scr, 0)) } catch (_: Throwable) {}
                }
                DRAW -> { draw(); report(); tick() }
            }
        } catch (e: Throwable) { err = "Lỗi ${e.javaClass.simpleName}: ${e.message ?: ""}"; report() }
        true
    }
    private val messenger = Messenger(h)

    private fun kick() { h.removeMessages(DRAW); h.sendEmptyMessage(DRAW) }
    /** Không có khung hình mới vẫn vẽ lại đều để hiện trạng thái (chưa chạy, mất hình). */
    private fun tick() { h.removeMessages(DRAW); if (surface != null) h.sendEmptyMessageDelayed(DRAW, 700) }

    /** Báo cho SK Light: tin 5, arg1 = số lần đã vẽ được lên mặt vẽ, data "e" = lỗi (rỗng là ổn). SK Light hiện ngay trên khung hình. */
    private fun report() {
        try { peer?.send(Message.obtain(null, 5, drawn, 0).apply { data = android.os.Bundle().apply { putString("e", err) } }) } catch (_: Throwable) {}
    }

    private fun draw() {
        val s = surface ?: return
        val v = view ?: return
        if (sw <= 0 || sh <= 0) { err = "Khung hình chưa có kích thước"; return }
        if (!s.isValid) { err = "Mặt vẽ nhận từ SK Light không dùng được"; return }
        v.note = if (!DriverService.running) "Chạm để mở SK Driver" else Engine.status.ifEmpty { "Đang kết nối…" }
        try {
            if (v.width != sw || v.height != sh) v.layout(0, 0, sw, sh)
            // Vẽ bằng canvas thường (không dùng canvas phần cứng): chạy được trên mọi máy và với View không gắn cửa sổ.
            val c = s.lockCanvas(null)
            try {
                c.drawColor(0xFF000000.toInt())
                try { v.draw(c) } catch (e: Throwable) { err = "Lỗi vẽ ${e.javaClass.simpleName}: ${e.message ?: ""}" }
            } finally { s.unlockCanvasAndPost(c) }
            drawn++
            if (!err.startsWith("Lỗi vẽ")) err = ""
        } catch (e: Throwable) { err = "Không vẽ được lên khung hình (${e.javaClass.simpleName}: ${e.message ?: ""})" }
    }

    override fun onBind(i: Intent): IBinder { Engine.init(this); return messenger.binder }
    override fun onUnbind(i: Intent): Boolean { Engine.mirror = null; h.post { surface = null }; return false }
    override fun onDestroy() { Engine.mirror = null; ht.quitSafely(); super.onDestroy() }
}
