package vn.sklight.driver

import android.content.Context
import android.content.Intent
import android.os.SystemClock

/**
 * Liên kết SK Light: gửi mức cảnh báo và khoảng cách sang app SK Light (vn.sklight.app) trên cùng máy để SK Light nháy đèn.
 * Gửi bằng broadcast đích danh, có quyền chữ ký (hai app ký cùng khóa mới nhận được của nhau). SK Light không trả lời lại:
 * SK Driver chỉ biết SK Light có cài hay không, không biết SK Light đã bật nháy đèn hay đã nối hộp đèn chưa.
 */
object Link {
    const val PKG = "vn.sklight.app"
    const val ACTION = "vn.sklight.DRIVER_SIGNAL"
    const val PERM = "vn.sklight.permission.DRIVER_LINK"
    @Volatile var installed = false; private set
    @Volatile var sending = false; private set
    @Volatile var enabled = true; private set
    private var checkedAt = 0L
    private var lastAt = 0L
    private var lastLevel = -1

    private fun check(c: Context, now: Long) {
        if (checkedAt != 0L && now - checkedAt < 5000) return
        checkedAt = now
        installed = try { c.packageManager.getPackageInfo(PKG, 0); true } catch (_: Throwable) { false }
    }

    /**
     * [level] 0 không có vật, 1 an toàn, 2 gần, 3 quá gần, 4 chưa đủ tốc độ. [dist] mét (-1 = không có). [camAt] = lúc bắt đầu xử lý khung hình
     * (đồng hồ elapsedRealtime, hai app trên cùng máy dùng chung) để SK Light đo độ trễ.
     * Đổi mức thì gửi ngay; không đổi thì gửi cách nhau ít nhất 180 ms (khoảng 5 lần mỗi giây) để SK Light biết SK Driver còn chạy.
     */
    fun send(c: Context, on: Boolean, level: Int, dist: Float, camAt: Long) {
        val now = SystemClock.elapsedRealtime()
        check(c, now)
        enabled = on
        if (!on || !installed) { sending = false; return }
        if (level == lastLevel && now - lastAt < 180) return
        lastLevel = level; lastAt = now
        try {
            c.sendBroadcast(Intent(ACTION).setPackage(PKG).addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                .putExtra("level", level).putExtra("dist", dist).putExtra("cam", camAt).putExtra("sent", SystemClock.elapsedRealtime()), PERM)
            sending = true
        } catch (_: Throwable) { sending = false }
    }

    /** SK Driver ngừng xử lý hình (rời màn hình, tắt app): báo mức 0 để SK Light trả đèn về ngay, không phải chờ hết 3 giây. */
    fun stop(c: Context, on: Boolean) { lastLevel = -1; if (on) send(c, true, 0, -1f, SystemClock.elapsedRealtime()); sending = false }
}
