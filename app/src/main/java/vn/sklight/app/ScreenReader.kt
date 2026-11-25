package vn.sklight.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * Đọc màn hình của app dẫn đường không có thông báo (Gofa...).
 * - Chữ: khoảng cách tới chỗ rẽ, tên đường, tốc độ xe đọc thẳng từ giao diện app.
 * - Hình: mũi tên rẽ và biển giới hạn tốc độ là hình ảnh, nên chụp màn hình rồi cắt đúng vùng đó:
 *   mũi tên -> trái/phải bằng bộ nhận dạng sẵn có; biển -> đọc số bằng ML Kit (chạy ngay trên máy).
 * Chỉ chạy khi người dùng bật quyền Hỗ trợ tiếp cận VÀ bật "Tự đọc" (chỉ app bản đồ) hoặc bấm "Bắt đầu đọc" (thử 5 phút).
 * Mọi thứ nằm trong bộ nhớ máy, không lưu ảnh, không gửi đi đâu.
 */
class ScreenReader : AccessibilityService() {
    companion object { @Volatile var connected = false; @Volatile var inst: ScreenReader? = null }

    private class Found {
        var instr = ""; var road = ""; var spd = ""
        var arrow: Rect? = null; var sign: Rect? = null
    }

    private val h = Handler(Looper.getMainLooper())
    private var last = 0L
    private var lastShot = 0L
    private var lastScan = 0L
    private var dirNow = '?'
    private var dirRoad = ""
    private var lastRoad = ""
    private val offRun = Runnable { navLost() }
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    override fun onServiceConnected() { connected = true; inst = this }
    override fun onUnbind(intent: Intent?): Boolean { connected = false; inst = null; return super.onUnbind(intent) }
    override fun onDestroy() { if (inst === this) inst = null; super.onDestroy() }
    override fun onInterrupt() {}

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        try {
            if (e == null) return
            val now = System.currentTimeMillis()
            if (S.screenOn.value && now > S.screenUntil) S.screenOn.value = false
            val test = S.screenOn.value
            if (!test && !S.screenAuto.value) return
            val pkg = e.packageName?.toString() ?: return
            if (skip(pkg)) return
            if (!test && !NavListener.isMapApp(pkg)) return
            if (now - last < 700) return
            last = now
            val out = LinkedHashSet<String>()
            val f = Found()
            var shown = ""
            var mapPkg = ""
            for (w in windows) {
                val r = w.root ?: continue
                val p = r.packageName?.toString() ?: continue
                if (skip(p)) continue
                val isMap = NavListener.isMapApp(p)
                if (!test && !isMap) continue
                if (shown.isEmpty()) shown = p
                if (isMap && mapPkg.isEmpty()) mapPkg = p
                if (test) out.add("=== $p ===")
                walk(r, out, if (isMap) f else null, 0)
            }
            if (test && out.isNotEmpty()) {
                S.screenPkg.value = shown
                S.screenLines.clear()
                S.screenLines.addAll(out.take(80))
            }
            if (mapPkg.isNotEmpty()) process(mapPkg, f, now)
        } catch (t: Throwable) { /* bỏ qua một lần đọc lỗi */ }
    }

    private fun process(pkg: String, f: Found, now: Long) {
        val dist = NavParser.findDist(f.instr)
        S.appSpeed.intValue = f.spd.filter { it.isDigit() }.toIntOrNull() ?: -1
        if (f.sign == null && Build.VERSION.SDK_INT >= 30 && now - lastScan > 2500L) { lastScan = now; scanScreen() }
        Limit.update()
        if (dist < 0) { AlertBridge.refresh(); return }
        val changed = f.road != lastRoad
        lastRoad = f.road
        if (f.road != dirRoad) dirNow = '?'          // sang đường mới: chờ đọc lại mũi tên
        pushNav(pkg, dist, f.road)
        h.removeCallbacks(offRun)
        h.postDelayed(offRun, 12000)
        if (Build.VERSION.SDK_INT >= 30 && (f.arrow != null || f.sign != null) &&
            now - lastShot > (if (changed || dirNow == '?') 600L else 2500L)) {
            lastShot = now
            shoot(pkg, f.arrow, f.sign, f.road, dist)
        }
        lastDist = dist
        info()
    }

    private var lastDist = -1
    private fun info() {
        S.screenInfo.value = "khoảng cách ${lastDist} m, đường \"${lastRoad}\", hướng ${dirNow}, giới hạn ${S.speedLimit.intValue}, tốc độ ${S.appSpeed.intValue}"
    }

    private fun pushNav(pkg: String, dist: Int, road: String) {
        val s = NavState()
        s.on = true; s.dir = dirNow; s.distM = dist; s.street = road; s.pkg = pkg; s.time = System.currentTimeMillis()
        AlertBridge.onNav(s)
    }

    private fun navLost() {
        Limit.clearScreen(); S.appSpeed.intValue = -1; dirNow = '?'; dirRoad = ""; lastRoad = ""
        AlertBridge.onNavOff()
    }

    private fun shoot(pkg: String, arrow: Rect?, sign: Rect?, road: String, dist: Int) {
        if (Build.VERSION.SDK_INT < 30) return
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(r: ScreenshotResult) {
                try {
                    val hb = r.hardwareBuffer
                    val bmp = Bitmap.wrapHardwareBuffer(hb, r.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
                    hb.close()
                    if (bmp == null) return
                    if (arrow != null) crop(bmp, arrow)?.let { c ->
                        val d = NavParser.analyseIcon(Bitmap.createScaledBitmap(c, 64, 64, true), NavState())
                        if (d != '?') { dirNow = d; dirRoad = road; pushNav(pkg, dist, road); info() }
                    }
                    if (sign != null) {
                        S.ocrNote.value = "vùng biển ${sign.width()}x${sign.height()}, màn hình ${bmp.width}x${bmp.height}"
                        readSign(bmp, sign)
                    }
                } catch (t: Throwable) { /* bỏ qua */ }
            }
            override fun onFailure(code: Int) {}
        })
    }

    /** Đọc số trên biển: thử vùng bên trong vành đỏ trước, không ra thì thử cả biển. */
    private fun readSign(bmp: Bitmap, sign: Rect) {
        val inner = Rect(sign.left + sign.width() * 18 / 100, sign.top + sign.height() * 18 / 100,
            sign.right - sign.width() * 18 / 100, sign.bottom - sign.height() * 18 / 100)
        val tries = listOf(inner, sign)
        fun attempt(i: Int) {
            if (i >= tries.size) { S.ocrNote.value += " · không đọc được số"; return }
            val c = crop(bmp, tries[i]) ?: return attempt(i + 1)
            val k = (240f / c.height).coerceIn(1f, 4f)
            val big = Bitmap.createScaledBitmap(c, (c.width * k).toInt(), (c.height * k).toInt(), true)
            if (i == 0) S.signThumb.value = big
            recognizer.process(InputImage.fromBitmap(big, 0)).addOnSuccessListener { t ->
                val v = Regex("\\d{2,3}").findAll(t.text).mapNotNull { it.value.toIntOrNull() }.firstOrNull { it in 5..130 }
                if (v != null) {
                    Limit.setScreen(v); info()
                    S.ocrNote.value = "đọc được $v (lần ${i + 1})"
                } else {
                    S.ocrNote.value += " · lần ${i + 1} thấy \"${t.text.replace('\n', ' ').take(20)}\""
                    attempt(i + 1)
                }
            }.addOnFailureListener { ex ->
                S.ocrNote.value = "lỗi đọc chữ: ${ex.javaClass.simpleName} ${ex.message?.take(60) ?: ""}"
            }
        }
        attempt(0)
    }


    /** App bản đồ không lộ biển qua giao diện (Vietmap, Google Maps...): chụp màn hình, tìm hình tròn viền đỏ rồi đọc số bên trong. */
    private fun scanScreen() {
        if (Build.VERSION.SDK_INT < 30) return
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(r: ScreenshotResult) {
                try {
                    val hb = r.hardwareBuffer
                    val bmp = Bitmap.wrapHardwareBuffer(hb, r.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
                    hb.close()
                    if (bmp == null) return
                    val rc = findRedRing(bmp)
                    if (rc == null) { S.ocrNote.value = "quét màn hình ${bmp.width}x${bmp.height}: không thấy biển"; return }
                    S.ocrNote.value = "quét màn hình: thấy biển ${rc.width()}x${rc.height()}"
                    readSign(bmp, rc)
                } catch (t: Throwable) { /* bỏ qua */ }
            }
            override fun onFailure(code: Int) {}
        })
    }

    /** Tìm vùng viền đỏ hình vuông (biển tròn) có ruột sáng. Chạy trên ảnh thu nhỏ, trả về hình chữ nhật theo toạ độ ảnh gốc. */
    private fun findRedRing(b: Bitmap): Rect? {
        val sc = (b.width / 360f).coerceAtLeast(1f)
        val w = (b.width / sc).toInt(); val hh = (b.height / sc).toInt()
        val s = Bitmap.createScaledBitmap(b, w, hh, true)
        val px = IntArray(w * hh); s.getPixels(px, 0, w, 0, 0, w, hh)
        fun red(c: Int): Boolean { val r = (c shr 16) and 255; val g = (c shr 8) and 255; val bl = c and 255; return r > 150 && g < 80 && bl < 80 && r - g > 100 }
        val cell = 6; val cw = w / cell; val ch = hh / cell
        val on = BooleanArray(cw * ch)
        for (cy in 0 until ch) for (cx in 0 until cw) {
            var n = 0
            for (y in cy * cell until cy * cell + cell) for (x in cx * cell until cx * cell + cell) if (red(px[y * w + x])) n++
            on[cy * cw + cx] = n >= 4
        }
        val seen = BooleanArray(on.size)
        var best: Rect? = null; var bestScore = 0
        for (i in on.indices) {
            if (!on[i] || seen[i]) continue
            var x0 = cw; var y0 = ch; var x1 = 0; var y1 = 0; var cnt = 0
            val st = ArrayDeque<Int>(); st.add(i); seen[i] = true
            while (st.isNotEmpty()) {
                val k = st.removeLast(); val x = k % cw; val y = k / cw
                cnt++; x0 = minOf(x0, x); x1 = maxOf(x1, x); y0 = minOf(y0, y); y1 = maxOf(y1, y)
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = x + dx; val ny = y + dy
                    if (nx in 0 until cw && ny in 0 until ch && on[ny * cw + nx] && !seen[ny * cw + nx]) { seen[ny * cw + nx] = true; st.add(ny * cw + nx) }
                }
            }
            val bw = x1 - x0 + 1; val bh = y1 - y0 + 1
            if (bw < 4 || bh < 4 || bw > cw / 3) continue
            val asp = bw.toFloat() / bh
            if (asp < 0.8f || asp > 1.25f) continue
            if (cnt < (bw + bh) * 3 / 2 || cnt > bw * bh * 3 / 4) continue   // vành rỗng, không phải khối đặc
            // ruột phải sáng (nền trắng của biển)
            var lit = 0; var tot = 0
            val ix0 = (x0 + bw / 4) * cell; val ix1 = (x1 + 1 - bw / 4) * cell; val iy0 = (y0 + bh / 4) * cell; val iy1 = (y1 + 1 - bh / 4) * cell
            for (y in iy0 until minOf(iy1, hh)) for (x in ix0 until minOf(ix1, w)) { val c = px[y * w + x]; tot++; if (((c shr 16) and 255) > 190 && ((c shr 8) and 255) > 190 && (c and 255) > 190) lit++ }
            if (tot == 0 || lit * 100 / tot < 35) continue
            val score = lit
            if (score > bestScore) { bestScore = score; best = Rect((x0 * cell * sc).toInt(), (y0 * cell * sc).toInt(), ((x1 + 1) * cell * sc).toInt(), ((y1 + 1) * cell * sc).toInt()) }
        }
        return best
    }

    private fun crop(b: Bitmap, r: Rect): Bitmap? {
        val x = r.left.coerceIn(0, b.width - 1); val y = r.top.coerceIn(0, b.height - 1)
        val w = (r.right.coerceIn(0, b.width) - x); val hh = (r.bottom.coerceIn(0, b.height) - y)
        if (w < 8 || hh < 8) return null
        return Bitmap.createBitmap(b, x, y, w, hh)
    }

    private fun skip(p: String) = p == packageName || p == "com.android.systemui" || p == "com.android.settings" ||
        p.contains("inputmethod") || p.contains("launcher")

    private fun walk(n: AccessibilityNodeInfo?, out: MutableSet<String>, f: Found?, depth: Int) {
        if (n == null || depth > 40 || out.size >= 80 || !n.isVisibleToUser) return
        val id = n.viewIdResourceName?.substringAfter(":id/") ?: ""
        val low = id.lowercase()
        val t = n.text?.toString()?.trim().orEmpty()
        val d = n.contentDescription?.toString()?.trim().orEmpty()
        if (f != null) {
            if (t.isNotEmpty()) {
                if (low.contains("instruction")) f.instr = t
                else if (low.contains("roadname")) f.road = t
                else if (low.contains("currentspeed")) f.spd = t
            } else {
                val r = Rect(); n.getBoundsInScreen(r)
                if (low.contains("imgdirection")) f.arrow = r
                else if (low.contains("speedsignlimit")) f.sign = r
            }
        }
        if (t.isNotEmpty()) out.add(if (id.isNotEmpty()) "$t  [$id]" else t)
        else if (d.isNotEmpty()) out.add("mô tả: $d" + if (id.isNotEmpty()) "  [$id]" else "")
        else if (id.isNotEmpty() && (n.className?.toString() ?: "").contains("Image")) out.add("hình [$id]")
        for (i in 0 until n.childCount) walk(n.getChild(i), out, f, depth + 1)
    }
}
