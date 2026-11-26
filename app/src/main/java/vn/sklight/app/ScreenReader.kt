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
        val descs = ArrayList<String>()
        val tops = ArrayList<Int>()        // mép trên (px) của nút chứa dòng mô tả tương ứng
        val rcs = ArrayList<Rect>()        // khung của nút đó
        var arrow2: Rect? = null           // vùng tìm thứ hai (khi chưa biết khung chữ là riêng dòng chữ hay cả dải)
        var seek = false                   // [arrow] mới là vùng tìm, phải dò hình mũi tên trắng trong đó
        var win: Rect? = null             // cửa sổ app bản đồ   // chữ mô tả theo thứ tự (Vietmap Live ghi tốc độ, km/h, giới hạn thành ba dòng liền nhau)
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

    override fun onServiceConnected() { connected = true; inst = this; h.removeCallbacks(poll); h.post(poll) }
    override fun onUnbind(intent: Intent?): Boolean { connected = false; inst = null; return super.onUnbind(intent) }
    override fun onDestroy() { h.removeCallbacks(poll); if (inst === this) inst = null; super.onDestroy() }
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
            evPkgAt = now
            readNow(now)
        } catch (t: Throwable) { /* bỏ qua một lần đọc lỗi */ }
    }

    // App bản đồ không gửi sự kiện khi đồng hồ tốc độ đổi số, nên đọc thêm theo nhịp 0,5 giây khi app bản đồ đang ở trước
    private var evPkgAt = 0L
    private var spdAt = 0L
    private var idleAt = 0L
    private var txtLim = 0          // giới hạn đọc được từ CHỮ của app bản đồ (chắc hơn số đọc từ ảnh chụp)
    private var txtLimAt = 0L
    private val poll = object : Runnable {
        override fun run() {
            try {
                val now = System.currentTimeMillis()
                if (connected && (S.screenOn.value || S.screenAuto.value)) {
                    // bản đồ đứng yên không gửi sự kiện nào: vẫn tự đọc lại mỗi 2 giây (đang thử thì đọc liên tục) để thấy biển và số mới
                    if (now - evPkgAt < 15000 || S.screenOn.value || now - idleAt > 2000) { idleAt = now; readNow(now) }
                    if (spdAt > 0 && now - spdAt > 3000 && S.appSpeed.intValue >= 0) S.appSpeed.intValue = -1   // số cũ quá 3 giây: dùng tốc độ GPS
                }
            } catch (_: Throwable) {}
            h.postDelayed(this, 500)
        }
    }

    private fun readNow(now: Long) {
        try {
            if (now - last < 400) return
            last = now
            val test = S.screenOn.value
            val out = LinkedHashSet<String>()
            val f = Found()
            var shown = ""
            var mapPkg = ""
            val rects = ArrayList<Rect>()
            budget = 1500
            for (w in windows) {
                val r = w.root ?: continue
                val p = r.packageName?.toString() ?: continue
                if (skip(p)) continue
                val isMap = NavListener.isMapApp(p)
                if (!test && !isMap) continue
                if (shown.isEmpty()) shown = p
                if (isMap && mapPkg.isEmpty()) mapPkg = p
                if (isMap) { val wb = Rect(); w.getBoundsInScreen(wb); if (!wb.isEmpty) { rects.add(wb); if (f.win == null) f.win = wb } }
                if (test) out.add("=== $p ===")
                walk(r, out, if (isMap) f else null, 0)
            }
            if (test && out.isNotEmpty()) {
                S.screenPkg.value = shown
                S.screenLines.clear()
                S.screenLines.addAll(out.take(80))
            }
            if (mapPkg.isNotEmpty()) { mapRects = rects; process(mapPkg, f, now) }
        } catch (t: Throwable) { /* bỏ qua một lần đọc lỗi */ }
    }

    private fun process(pkg: String, f: Found, now: Long) {
        var dist = NavParser.findDist(f.instr)
        // Vietmap Live không có mã ô như Gofa: dải chỉ đường phía trên ("50m Lý Sơn") lộ ra thành chữ mô tả. Lấy dòng khoảng cách nằm ở nửa trên
        // cửa sổ (dòng "3.7km" ở thanh dưới là quãng đường còn lại, không lấy). Hướng rẽ là hình mũi tên, chưa đọc được ở bước này.
        if (dist < 0 && pkg.contains("vietmap")) {
            val wn = f.win
            val re = Regex("^(\\d+(?:[.,]\\d+)?)\\s?(m|km)\\b\\s*(.*)$", RegexOption.IGNORE_CASE)
            for (i in f.descs.indices) {
                if (wn != null && f.tops[i] > wn.top + wn.height() * 45 / 100) continue
                val m = re.find(f.descs[i]) ?: continue
                dist = NavParser.findDist(m.groupValues[1] + " " + m.groupValues[2])
                if (dist >= 0) {
                    f.road = m.groupValues[3].trim().ifEmpty { f.descs.getOrNull(i + 1)?.takeIf { f.tops[i + 1] == f.tops[i] } ?: "" }
                    // Mũi tên rẽ là hình trắng ở đầu trái dải chỉ đường, ngay trước chữ khoảng cách. Chưa biết khung của nút chữ là riêng dòng chữ
                    // hay cả dải, nên xét cả hai: bên trái nút còn chỗ thì tìm ở đó, không thì tìm ở đầu trái của chính nút.
                    val nr = f.rcs[i]
                    if (wn != null) {
                        val rowH = minOf(nr.height(), wn.height() * 13 / 100).coerceAtLeast(24)
                        val pad = if (nr.height() <= rowH) rowH * 3 / 10 else 0
                        val y0 = nr.top - pad; val y1 = nr.top + rowH + pad
                        val inside = Rect(nr.left, y0, nr.left + rowH * 12 / 10, y1)
                        if (nr.left - wn.left >= rowH * 6 / 10) { f.arrow = Rect(maxOf(wn.left, nr.left - rowH * 2), y0, nr.left, y1); f.arrow2 = inside }
                        else f.arrow = inside
                        f.seek = true
                    }
                    break
                }
            }
        }
        var sp = f.spd.filter { it.isDigit() }.toIntOrNull()
        // Vietmap Live: ba dòng mô tả liền nhau "tốc độ" / "km/h" / "giới hạn" (giới hạn chỉ có khi đường có biển)
        val ki = f.descs.indexOfFirst { it.equals("km/h", true) }
        if (ki > 0) {
            val v = f.descs[ki - 1].toIntOrNull()
            if (sp == null && v != null && v in 0..250) sp = v
            val lim = f.descs.getOrNull(ki + 1)?.toIntOrNull()
            if (lim != null && lim in 10..140 && lim % 5 == 0) {
                Limit.setScreen(lim)
                txtLim = lim; txtLimAt = now
            }
        }
        if (sp != null) spdAt = now
        S.appSpeed.intValue = sp ?: -1
        if (f.sign == null && Build.VERSION.SDK_INT >= 30 && now - lastScan > 2500L) { lastScan = now; scanScreen() }
        Limit.update()
        if (dist < 0) { AlertBridge.refresh(); info(); return }
        val changed = f.road != lastRoad
        lastRoad = f.road
        if (f.road != dirRoad) dirNow = '?'          // sang đường mới: chờ đọc lại mũi tên
        pushNav(pkg, dist, f.road)
        h.removeCallbacks(offRun)
        h.postDelayed(offRun, 12000)
        if (Build.VERSION.SDK_INT >= 30 && (f.arrow != null || f.sign != null) &&
            now - lastShot > (if (changed || dirNow == '?') 600L else 2500L)) {
            lastShot = now
            shoot(pkg, f.arrow, f.sign, f.road, dist, f.seek, f.arrow2)
        }
        lastDist = dist
        info()
    }

    private var lastDist = -1
    private var budget = 0          // số nút giao diện còn được duyệt trong một lần đọc
    private fun info() {
        S.screenInfo.value = "khoảng cách ${if (lastDist < 0) "chưa có" else "$lastDist m"}, đường \"${lastRoad}\", hướng ${dirNow}, giới hạn ${S.speedLimit.intValue}, tốc độ trên app bản đồ ${S.appSpeed.intValue} (không dùng), GPS ${S.carSpeed()}"
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

    private fun shoot(pkg: String, arrow: Rect?, sign: Rect?, road: String, dist: Int, seek: Boolean = false, arrow2: Rect? = null) {
        if (Build.VERSION.SDK_INT < 30) return
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(r: ScreenshotResult) {
                try {
                    val hb = r.hardwareBuffer
                    val bmp = Bitmap.wrapHardwareBuffer(hb, r.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
                    hb.close()
                    if (bmp == null) return
                    if (arrow != null) (if (seek) (whiteArrow(bmp, arrow) ?: arrow2?.let { whiteArrow(bmp, it) }) else crop(bmp, arrow))?.let { c ->
                        val d = NavParser.analyseIcon(Bitmap.createScaledBitmap(c, 64, 64, true), NavState())
                        if (seek && S.dev.value) S.arrowThumb.value = c        // chế độ nghiên cứu: xem app đang cắt hình nào làm mũi tên
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

    /**
     * Tìm hình mũi tên rẽ trong vùng [r] của dải chỉ đường Vietmap. Dải này nền tối chữ trắng ban đêm, nền sáng chữ xám đậm ban ngày,
     * nên lấy "nét" = điểm lệch sáng rõ so với màu nền (màu chiếm nhiều nhất trong vùng). Gom các cột có nét liền nhau thành từng cụm;
     * chọn cụm to nhất có dáng gần vuông (mũi tên), bỏ cụm hẹp (mép bản đồ, số 1) và cụm dài (chữ).
     */
    private fun whiteArrow(bmp: Bitmap, r: Rect): Bitmap? {
        val c = crop(bmp, r) ?: return null
        val w = c.width; val hh = c.height
        val px = IntArray(w * hh); c.getPixels(px, 0, w, 0, 0, w, hh)
        fun lum(p: Int) = (((p shr 16) and 255) * 3 + ((p shr 8) and 255) * 6 + (p and 255)) / 10
        val hist = IntArray(16); for (p in px) hist[lum(p) shr 4]++
        val bg = hist.indices.maxByOrNull { hist[it] }!! * 16 + 8
        fun ink(p: Int) = kotlin.math.abs(lum(p) - bg) > 70
        val col = IntArray(w)
        for (y in 0 until hh) for (x in 0 until w) if (ink(px[y * w + x])) col[x]++
        val gap = maxOf(4, hh * 6 / 100)
        var best: IntArray? = null; var bestArea = 0
        var x0 = -1; var x1 = -1; var empty = 0
        fun close() {
            if (x0 < 0) return
            var y0 = hh; var y1 = -1
            for (y in 0 until hh) for (x in x0..x1) if (ink(px[y * w + x])) { if (y < y0) y0 = y; if (y > y1) y1 = y }
            val bw = x1 - x0 + 1; val bh = y1 - y0 + 1
            if (bh >= hh * 28 / 100 && bw >= hh * 28 / 100 && bw <= bh * 2 && bh <= bw * 2 && bw * bh > bestArea) { bestArea = bw * bh; best = intArrayOf(x0, y0, x1, y1) }
            x0 = -1
        }
        for (x in 0 until w) {
            if (col[x] >= 2) { if (x0 < 0) x0 = x; x1 = x; empty = 0 }
            else if (x0 >= 0 && ++empty >= gap) close()
        }
        close()
        val k = best ?: return null
        val p = 3          // chừa viền nền quanh hình, để bộ nhận hướng lấy đúng màu nền ở góc
        val ax = maxOf(0, k[0] - p); val ay = maxOf(0, k[1] - p)
        return Bitmap.createBitmap(c, ax, ay, minOf(w, k[2] + p + 1) - ax, minOf(hh, k[3] + p + 1) - ay)
    }

    /** Đọc số trên biển: thử vùng bên trong vành đỏ trước, không ra thì thử cả biển. */
    private fun readSign(bmp: Bitmap, sign: Rect, apply: Boolean = true, onHit: (() -> Unit)? = null, onMiss: (() -> Unit)? = null) {
        val inner = Rect(sign.left + sign.width() * 18 / 100, sign.top + sign.height() * 18 / 100,
            sign.right - sign.width() * 18 / 100, sign.bottom - sign.height() * 18 / 100)
        val tries = listOf(inner, sign)
        fun attempt(i: Int) {
            if (i >= tries.size) { S.ocrNote.value += " · không đọc được số"; onMiss?.invoke(); return }
            val c = crop(bmp, tries[i]) ?: return attempt(i + 1)
            val k = (240f / c.height).coerceIn(1f, 4f)
            val big = Bitmap.createScaledBitmap(c, (c.width * k).toInt(), (c.height * k).toInt(), true)
            if (i == 0) S.signThumb.value = big
            recognizer.process(InputImage.fromBitmap(big, 0)).addOnSuccessListener { t ->
                val v = Regex("\\d{2,3}").findAll(t.text).mapNotNull { it.value.toIntOrNull() }.firstOrNull { it in 5..130 }
                if (v != null) {
                    if (apply) { Limit.setScreen(v); info(); S.ocrNote.value = "đọc được $v (lần ${i + 1})" }
                    onHit?.invoke()
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


    @Volatile private var mapRects: List<Rect> = emptyList()      // vùng các cửa sổ của app bản đồ trên màn hình; chỉ đọc biển nằm trong đó
    private var banAt = 0L
    private var banN = 0
    private var banDone = false

    /**
     * Biển tròn viền đỏ không có số trên màn hình Vietmap (xem ảnh chủ dự án gửi 25/11): phân loại theo hình bên trong.
     * - có chấm xanh lá hoặc một chấm đỏ nhỏ (cột đèn giao thông) = camera vượt đèn đỏ;
     * - có mảng đỏ lớn (chiếc xe đỏ) = vào đường cấm vượt;
     * - còn lại (máy quay đen, chữ AI) = camera phạt nguội / AI / đo tốc độ.
     * Khoảng cách đọc từ nhãn "261m" ngay dưới biển. Ngưỡng màu đặt theo ảnh chụp, chưa thử bằng ảnh màn hình thật trên xe.
     */
    private fun classify(bmp: Bitmap, ring: Rect) {
        if (!(S.camWarn.value || S.redCamWarn.value || S.banWarn.value)) return
        val w = ring.width(); val hh = ring.height()
        val cx = ring.exactCenterX(); val cy = ring.exactCenterY(); val rad = minOf(w, hh) / 2f
        fun isRed(c: Int): Boolean { val r = (c shr 16) and 255; val g = (c shr 8) and 255; val bl = c and 255; return r >= 110 && r - g >= 60 && r - bl >= 45 && g < 120 }
        // phải là hình tròn: 16 hướng quanh tâm đều gặp viền đỏ (biển tam giác thì không)
        var hits = 0
        for (a in 0 until 16) {
            val an = a * Math.PI / 8; var q = 0.70; var ok = false
            while (q <= 1.06 && !ok) {
                val x = (cx + Math.cos(an) * rad * q).toInt(); val y = (cy + Math.sin(an) * rad * q).toInt()
                if (x in 0 until bmp.width && y in 0 until bmp.height && isRed(bmp.getPixel(x, y))) ok = true
                q += 0.04
            }
            if (ok) hits++
        }
        if (hits < 13) { S.ocrNote.value += " · viền đỏ không tròn"; return }
        val ri = (rad * 0.62f).toInt(); var tot = 0; var green = 0; var redIn = 0; var blue = 0
        for (y in (cy - ri).toInt()..(cy + ri).toInt()) for (x in (cx - ri).toInt()..(cx + ri).toInt()) {
            if (x !in 0 until bmp.width || y !in 0 until bmp.height) continue
            if ((x - cx) * (x - cx) + (y - cy) * (y - cy) > ri * ri) continue
            val c = bmp.getPixel(x, y); val r = (c shr 16) and 255; val g = (c shr 8) and 255; val bl = c and 255
            tot++
            if (g >= 110 && g - r >= 40 && g - bl >= 30) green++          // xanh lá thật; nền xanh dương của biển cấm đỗ không tính
            if (bl > 150 && r < 90 && bl - r > 90) blue++
            if (isRed(c)) redIn++
        }
        if (tot == 0) return
        val gp = green * 1000 / tot; val rp = redIn * 1000 / tot          // phần nghìn diện tích ruột biển
        // ruột xanh dương: biển cấm dừng / cấm đỗ. Chủ dự án quyết định 26/11: loại này KHÔNG báo đèn.
        if (blue * 1000 / tot >= 300) { S.ocrNote.value += " · cấm dừng đỗ (không báo đèn)"; return }
        val kind = if (rp >= 35) 6 else if (gp >= 2 || rp >= 2) 5 else 4
        val name = when (kind) { 6 -> "cấm vượt"; 5 -> "camera đèn đỏ"; else -> "camera" }
        fun fire(d: Int) {
            S.ocrNote.value += " · $name" + (if (d >= 0) " $d m" else " (không đọc được khoảng cách)")
            if (kind == 6) {
                if (d > 200) return
                val now = System.currentTimeMillis()
                // phải thấy 2 lần liên tiếp mới báo, đỡ nhiễu. Biển còn nằm trên màn hình (Vietmap giữ biển cấm đỗ suốt cả con phố) thì chỉ báo MỘT lần;
                // biển biến mất quá 10 giây rồi hiện lại mới tính là biển mới.
                if (now - banAt > 10000) { banN = 0; banDone = false }
                banN++; banAt = now
                if (banN >= 2 && !banDone) { banDone = true; AlertBridge.event(6) }
            } else if (d in 0..S.camDist.intValue) AlertBridge.event(kind)
        }
        val c = crop(bmp, Rect(ring.left - w / 2, ring.bottom, ring.right + w / 2, ring.bottom + hh * 3 / 4)) ?: run { fire(-1); return }
        val k = (120f / c.height).coerceIn(1f, 4f)
        val big = Bitmap.createScaledBitmap(c, (c.width * k).toInt(), (c.height * k).toInt(), true)
        recognizer.process(InputImage.fromBitmap(big, 0)).addOnSuccessListener { t ->
            val txt = t.text.lowercase().replace('\n', ' ')
            if (txt.contains("km")) { S.ocrNote.value += " · $name còn xa"; return@addOnSuccessListener }
            val m = Regex("(\\d{2,4})\\s*m\\b").find(txt)
            fire(m?.groupValues?.get(1)?.toIntOrNull() ?: -1)
        }.addOnFailureListener { }
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
                    // chỉ lấy biển nằm trong cửa sổ app bản đồ: màn hình còn có biển do chính SK Light vẽ (thẻ dẫn đường, mô phỏng)
                    val zone = mapRects
                    val own = LimitSign.rect          // biển nổi của chính SK Light: không đọc lại
                    val rcs = findRedRings(bmp).filter { r -> (zone.isEmpty() || zone.any { it.contains(r.centerX(), r.centerY()) }) && own?.contains(r.centerX(), r.centerY()) != true }.take(4)
                    val byText = System.currentTimeMillis() - txtLimAt < 5000
                    val head = if (byText) "giới hạn $txtLim đọc từ chữ của app bản đồ · " else ""
                    if (rcs.isEmpty()) { S.ocrNote.value = head + "quét màn hình ${bmp.width}x${bmp.height}: không thấy biển"; return }
                    S.ocrNote.value = head + "quét màn hình: thấy ${rcs.size} biển tròn viền đỏ"
                    // Xét lần lượt TỪNG biển: có số = biển giới hạn (chỉ lấy biển đầu tiên), không có số = camera / cấm vượt.
                    // Trước 1.4.64 đọc được biển giới hạn là dừng luôn, nên biển camera đứng cạnh không bao giờ được xét.
                    val more = S.camWarn.value || S.redCamWarn.value || S.banWarn.value
                    var gotLimit = byText          // số đọc từ ảnh có thể sai (đọc nhầm 50 thành 30 là sinh cảnh báo "giới hạn giảm" giả)
                    fun tryAt(i: Int) {
                        if (i >= rcs.size) return
                        readSign(bmp, rcs[i], apply = !gotLimit, onHit = { gotLimit = true; if (more) tryAt(i + 1) }, onMiss = { classify(bmp, rcs[i]); tryAt(i + 1) })
                    }
                    tryAt(0)
                } catch (t: Throwable) { /* bỏ qua */ }
            }
            override fun onFailure(code: Int) {}
        })
    }

    /** Tìm vùng viền đỏ hình vuông (biển tròn) có ruột sáng. Chạy trên ảnh thu nhỏ, trả về hình chữ nhật theo toạ độ ảnh gốc. */
    private fun findRedRings(b: Bitmap): List<Rect> {
        val sc = (b.width / 720f).coerceAtLeast(1f)
        val w = (b.width / sc).toInt(); val hh = (b.height / sc).toInt()
        val s = Bitmap.createScaledBitmap(b, w, hh, true)
        val px = IntArray(w * hh); s.getPixels(px, 0, w, 0, 0, w, hh)
        if (s !== b) s.recycle()          // ảnh thu nhỏ chỉ cần tới đây; quét 2,5 giây một lần nên trả bộ nhớ ngay
        fun red(c: Int): Boolean { val r = (c shr 16) and 255; val g = (c shr 8) and 255; val bl = c and 255; return r >= 110 && r - g >= 60 && r - bl >= 45 && g < 120 }
        val cell = 4; val cw = w / cell; val ch = hh / cell
        val on = BooleanArray(cw * ch)
        for (cy in 0 until ch) for (cx in 0 until cw) {
            var n = 0
            for (y in cy * cell until cy * cell + cell) for (x in cx * cell until cx * cell + cell) if (red(px[y * w + x])) n++
            on[cy * cw + cx] = n >= 3
        }
        val seen = BooleanArray(on.size)
        val found = ArrayList<Pair<Int, Rect>>()
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
            if (cnt < bw + bh || cnt > bw * bh * 3 / 4) continue   // vành rỗng, không phải khối đặc
            // ruột phải sáng (nền trắng hoặc xanh của biển)
            var lit = 0; var tot = 0
            val ix0 = (x0 + bw / 4) * cell; val ix1 = (x1 + 1 - bw / 4) * cell; val iy0 = (y0 + bh / 4) * cell; val iy1 = (y1 + 1 - bh / 4) * cell
            for (y in iy0 until minOf(iy1, hh)) for (x in ix0 until minOf(ix1, w)) { val c = px[y * w + x]; tot++; val cr = (c shr 16) and 255; val cg = (c shr 8) and 255; val cb = c and 255; if ((cr > 120 && cg > 120 && cb > 120) || (cb > 150 && cr < 90 && cb - cr > 90)) lit++ }   // ruột trắng (biển tốc độ, camera) hoặc xanh dương (biển cấm dừng đỗ)
            if (tot == 0 || lit * 100 / tot < 30) continue
            val score = lit
            run { found.add(score to Rect((x0 * cell * sc).toInt(), (y0 * cell * sc).toInt(), ((x1 + 1) * cell * sc).toInt(), ((y1 + 1) * cell * sc).toInt())) }
        }
        return found.sortedByDescending { it.first }.take(8).map { it.second }
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
        if (n == null || depth > 40 || !n.isVisibleToUser || --budget < 0) return
        if (f == null && out.size >= 80) return          // cửa sổ app khác chỉ để xem thử; cửa sổ app bản đồ thì luôn đọc hết
        val id = n.viewIdResourceName?.substringAfter(":id/") ?: ""
        val low = id.lowercase()
        val t = n.text?.toString()?.trim().orEmpty()
        val d = n.contentDescription?.toString()?.trim().orEmpty()
        if (f != null && d.isNotEmpty() && f.descs.size < 60) { val rr = Rect(); n.getBoundsInScreen(rr); for (part in d.split('\n')) { val q = part.trim(); if (q.isNotEmpty()) { f.descs.add(q); f.tops.add(rr.top); f.rcs.add(rr) } } }   // Vietmap gộp "0 / km/h / 50" thành MỘT mô tả nhiều dòng
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
        else if (d.isNotEmpty()) out.add("mô tả: $d" + (if (id.isNotEmpty()) "  [$id]" else "") + (if (f != null) Rect().also { n.getBoundsInScreen(it) }.let { "  @${it.left},${it.top} ${it.width()}x${it.height()}" } else ""))
        else if (id.isNotEmpty() && (n.className?.toString() ?: "").contains("Image")) out.add("hình [$id]")
        for (i in 0 until n.childCount) walk(n.getChild(i), out, f, depth + 1)
    }
}
