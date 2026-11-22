package vn.sklight.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Bản kiểm tra: đọc chữ hiện trên màn hình của app dẫn đường không có thông báo (ví dụ Gofa).
 * Chỉ chạy khi người dùng bật trong Hỗ trợ tiếp cận VÀ bấm "Bắt đầu đọc" (tự tắt sau 5 phút).
 * Chữ chỉ nằm trong bộ nhớ máy để hiển thị ở trang Dẫn đường, không gửi đi đâu.
 */
class ScreenReader : AccessibilityService() {
    companion object { @Volatile var connected = false }

    private var last = 0L

    override fun onServiceConnected() { connected = true }
    override fun onUnbind(intent: Intent?): Boolean { connected = false; return super.onUnbind(intent) }
    override fun onInterrupt() {}

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        try {
            if (e == null || !S.screenOn.value) return
            val now = System.currentTimeMillis()
            if (now > S.screenUntil) { S.screenOn.value = false; return }
            if (now - last < 1000) return
            val pkg = e.packageName?.toString() ?: return
            if (pkg == packageName || pkg == "com.android.systemui" || pkg.contains("inputmethod") || pkg.contains("launcher")) return
            last = now
            val root = rootInActiveWindow ?: return
            val out = LinkedHashSet<String>()
            walk(root, out, 0)
            S.screenPkg.value = pkg
            S.screenLines.clear()
            S.screenLines.addAll(out.take(60))
        } catch (t: Throwable) { /* bỏ qua một lần đọc lỗi */ }
    }

    private fun walk(n: AccessibilityNodeInfo?, out: MutableSet<String>, depth: Int) {
        if (n == null || depth > 40 || out.size >= 80 || !n.isVisibleToUser) return
        val id = n.viewIdResourceName?.substringAfter(":id/") ?: ""
        val t = n.text?.toString()?.trim().orEmpty()
        val d = n.contentDescription?.toString()?.trim().orEmpty()
        if (t.isNotEmpty()) out.add(if (id.isNotEmpty()) "$t  [$id]" else t)
        else if (d.isNotEmpty()) out.add("mô tả: $d" + if (id.isNotEmpty()) "  [$id]" else "")
        else if (id.isNotEmpty() && (n.className?.toString() ?: "").contains("Image")) out.add("hình [$id]")
        for (i in 0 until n.childCount) walk(n.getChild(i), out, depth + 1)
    }
}
