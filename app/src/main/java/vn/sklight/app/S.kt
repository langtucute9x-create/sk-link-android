package vn.sklight.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.*

/** Trạng thái chung của app + các hành động gửi lệnh tới hộp đèn. */
object S {
    const val VER = "0.5.58"
    private var inited = false
    lateinit var app: Context
    lateinit var ble: BleLed
    private lateinit var sp: SharedPreferences

    // kết nối
    val conn = mutableStateOf("Chưa kết nối")
    val connected = mutableStateOf(false)
    val devName = mutableStateOf("")
    val isCzh = mutableStateOf(false)
    val scanning = mutableStateOf(false)
    val devices = mutableStateListOf<Dev>()
    var savedMac: String
        get() = sp.getString("mac", "") ?: ""
        set(v) { sp.edit().putString("mac", v).apply() }

    // đèn
    val on = mutableStateOf(true)
    val zone = mutableIntStateOf(0)                       // khu vực của độ sáng: 0 đồng nhất, 1, 2
    val czone = mutableIntStateOf(0)                      // khu vực của màu
    val bri = mutableStateListOf(100, 100, 100)
    val col = mutableStateListOf(0xFFFFFF, 0xFFFFFF, 0xFFFFFF)
    val customs = mutableStateListOf<Int>()
    const val MAX_PINS = 9
    val pins = mutableStateListOf<Int>()                  // id các chế độ đã ghim lên đầu trang Đèn
    /** Ghim / bỏ ghim một chế độ. Trả 1 = vừa ghim, 0 = vừa bỏ ghim, -1 = đã đủ số lượng. */
    fun togglePin(id: Int): Int {
        if (pins.remove(id)) { saveSettings(); return 0 }
        if (pins.size >= MAX_PINS) return -1
        pins.add(id); saveSettings(); return 1
    }
    val mode = mutableIntStateOf(0)                       // id hiệu ứng
    val sub = mutableStateListOf<Int>().also { l -> repeat(128) { l.add(0) } }   // kiểu con đang chọn theo id (đếm từ 0)
    val speed = mutableIntStateOf(5)
    val sens = mutableIntStateOf(6)
    val z2 = mutableIntStateOf(0)                         // chế độ màu vùng 2: 0 tĩnh, 1 tự động, 2 nhấp nháy (bit 3-4 của khối 0D)
    var fac: IntArray? = null                             // khối 0D (cài đặt nhà máy) đọc từ hộp: b1, b2; null = chưa đọc được
    var m1 = 0                                            // ô mode1 của lệnh hiệu ứng: giữ nguyên giá trị hộp trả về
    var p07: IntArray? = null                             // khối 07 đọc từ hộp: bit hướng ánh sáng chạy | giọng nói của hộp | hướng hộp 7..14
    var ttsVoice: String                                  // giọng đọc tiếng Việt đã chọn bằng lệnh "đổi giọng nói"
        get() = sp.getString("ttsVoice", "") ?: ""
        set(v) { sp.edit().putString("ttsVoice", v).apply() }

    // chào mừng. Thứ tự đoạn trên sơ đồ: 0 bảng đồng hồ trái, 1 bảng đồng hồ phải, 2 trước trái, 3 trước phải, 4 sau trái, 5 sau phải
    var blk: IntArray? = null
    val blkVer = mutableIntStateOf(0)
    val leds = mutableStateListOf(50, 50, 50, 50, 50, 50)
    private var ledRaw = IntArray(16) { 50 }
    private val ledIdx = intArrayOf(5, 0, 1, 2, 3, 4)     // đoạn -> vị trí trong lệnh 06
    val wDir = mutableStateListOf(false, false, false, false, false, false)
    private val dirBit = intArrayOf(5, 4, 0, 1, 2, 3)     // đoạn -> bit trong khối 08
    private var w08d3 = 0
    val wSpeed = mutableIntStateOf(1)                     // 0..3
    val wStart = mutableIntStateOf(0)                     // mã vị trí bắt đầu 0..5
    val boxPos = mutableStateListOf(-1, -1, -1, -1)       // -1 chưa gán, 0 khác, 2..5 = đoạn trên sơ đồ
    val pickBox = mutableIntStateOf(0)                    // hộp phụ đang nháy (1..4), 0 = không

    // cài đặt
    /** Đảo màu 255-x trước khi gửi. Mặc định KHÔNG đảo: hộp của chủ dự án hiện đúng màu gửi đi (các bản tới 0.5.27 đảo sẵn nên ra màu bù). */
    val invert = mutableStateOf(false)

    // dẫn đường + cảnh báo (bản thử)
    val warnOn = mutableStateOf(false)                    // nháy đèn khi sắp rẽ
    val alertMaxSec = mutableIntStateOf(0)                // đèn báo rẽ chạy tối đa bao nhiêu giây (0 = tới khi qua chỗ rẽ)
    val alertDist = mutableIntStateOf(150)                // báo trước bao nhiêu mét
    val navOn = mutableStateOf(false)
    val navDir = mutableStateOf("?")
    val navDist = mutableIntStateOf(-1)
    val navStreet = mutableStateOf("")
    val navApp = mutableStateOf("")
    val navLog = mutableStateListOf<String>()             // thông báo thô của app dẫn đường, mới nhất ở đầu
    val speedZone = mutableIntStateOf(0)                  // quá tốc độ: khu nào nháy đỏ (0 = cả xe, 1, 2). Mặc định cả xe.
    val speedWarn = mutableStateOf(false)                 // nháy đỏ khi quá tốc độ
    val speedLimit = mutableIntStateOf(0)                 // giới hạn tốc độ đọc được (km/h), 0 = chưa biết
    val appSpeed = mutableIntStateOf(-1)                  // tốc độ xe đọc từ app dẫn đường, -1 = chưa biết
    val screenAuto = mutableStateOf(false)                // tự đọc màn hình app bản đồ
    val signThumb = mutableStateOf<android.graphics.Bitmap?>(null)   // ảnh cắt biển tốc độ app đang đọc (để kiểm tra)
    val ocrNote = mutableStateOf("")                      // kết quả đọc biển tốc độ gần nhất (để kiểm tra)
    val screenInfo = mutableStateOf("")                   // kết quả đọc gần nhất, để kiểm tra
    val screenOn = mutableStateOf(false)                  // đang đọc chữ trên màn hình (tự tắt sau 5 phút)
    @Volatile var screenUntil = 0L
    val screenPkg = mutableStateOf("")                    // app đang hiện trên màn hình lần đọc gần nhất
    val screenLines = mutableStateListOf<String>()        // chữ đọc được, để kiểm tra
    val signSeen = mutableStateOf("")                     // con số km/h thấy trong thông báo (nếu có)
    val turnStyle = mutableIntStateOf(0)                  // cảnh báo rẽ: 0 nháy vàng cả xe, 1 đèn chạy theo hướng rẽ
    val turnFx = mutableIntStateOf(0)                     // hiệu ứng báo rẽ: chỉ số trong AlertBridge.TURN_FX
    val turnSpd = mutableStateListOf(10, 10, 10)          // tốc độ (1..10) của từng hiệu ứng báo rẽ
    val turnFlip = mutableStateOf(false)                  // đảo chiều chạy của kiểu 1
    val alertKind = mutableIntStateOf(0)                  // xem AlertBridge
    val alertPhase = mutableStateOf(false)                // pha sáng / tối của nhịp nháy

    // bối cảnh + các tính năng khi lái xe
    val sceneSel = mutableIntStateOf(-1)                  // bối cảnh đang dùng (vị trí trong Scenes.all()), -1 = không
    val autoBoot = mutableStateOf(true)                   // tự kết nối khi khởi động máy
    val nightOn = mutableStateOf(false)                   // tự dịu đèn ban đêm
    val nightMode = mutableIntStateOf(0)                  // 0 theo giờ mặt trời, 1 theo giờ tự đặt
    val nightFrom = mutableIntStateOf(18 * 60)            // phút tính từ 0 giờ
    val nightTo = mutableIntStateOf(6 * 60)
    val nightBri = mutableIntStateOf(30)
    var dayBri: IntArray? = null                          // độ sáng ban ngày đang được giữ lại trong lúc dịu đèn
    val idleOn = mutableStateOf(false)                    // tự mở màn hình chờ khi xe chạy
    val idleStyle = mutableIntStateOf(0)                  // 0 đồng hồ + tốc độ, 1 chỉ đồng hồ, 2 tốc độ lớn, 3 ảnh của tôi
    val idlePhotoVer = mutableIntStateOf(0)
    val idleShow = mutableStateOf(false)
    val floatStyle = mutableIntStateOf(0)                 // kiểu nút nổi + màn hình nổi (FloatButton.STYLES)
    val menuTop = mutableStateOf(true)                    // màn ngang: menu nằm trên (true) hay cột bên trái (false)
    var pendingMic = false                                // bấm mic ở màn hình nổi: mở app rồi nghe ngay
    val floatOn = mutableStateOf(false)                   // nút nổi trên ứng dụng khác
    val voiceState = mutableIntStateOf(0)                 // xem Voice.kt
    val voiceHeard = mutableStateOf("")
    val voiceMsg = mutableStateOf("")
    val voiceTick = mutableIntStateOf(0)
    val voiceReply = mutableStateOf(true)                 // trả lời bằng tiếng nói sau mỗi câu lệnh
    val voiceQuiet = mutableStateOf(true)                 // tắt tiếng báo của bộ nhận giọng nói khi bấm mic
    val speedKmh = mutableFloatStateOf(0f)
    val song = mutableStateOf("")
    val placeVer = mutableIntStateOf(0)
    var lat = Double.NaN
    var lon = Double.NaN
    @Volatile var lastTouch = 0L
    var appVisible = false
    var userOff = false                                   // người dùng vừa bấm Ngắt kết nối: không tự nối lại
    private val main = Handler(Looper.getMainLooper())
    val subPage = mutableIntStateOf(0)                    // trang con trong Cài đặt: 0 không, 1 Cài đặt giao diện, 2 Cài đặt ngôn ngữ, 3 Thử chế độ tự tạo, 4 Giọng nói AI
    private val nightLoop = object : Runnable {
        override fun run() {
            try { Theme.nightNow.value = Night.isNightNow(); Night.tick() } catch (_: Exception) {}
            main.postDelayed(this, 30000)
        }
    }

    fun init(c: Context) {
        if (inited) return
        inited = true
        app = c.applicationContext
        Crash.install(app)
        sp = app.getSharedPreferences("sklight", Context.MODE_PRIVATE)
        invert.value = sp.getBoolean("invert2", false)        // khóa mới từ 0.5.28, bỏ giá trị "invert" cũ (mặc định sai)
        warnOn.value = sp.getBoolean("warnOn", false)
        turnStyle.intValue = sp.getInt("turnStyle", 0).coerceIn(0, 1)
        turnFlip.value = sp.getBoolean("turnFlip", false)
        turnFx.intValue = sp.getInt("turnFx", 0).coerceIn(0, 2)
        (sp.getString("turnSpd", "") ?: "").split(",").mapNotNull { it.toIntOrNull() }.take(3).forEachIndexed { i, v -> turnSpd[i] = v.coerceIn(1, 10) }
        alertDist.intValue = sp.getInt("alertDist", 150)
        alertMaxSec.intValue = sp.getInt("alertMaxSec", 0)
        speedWarn.value = sp.getBoolean("speedWarn", false)
        speedZone.intValue = sp.getInt("speedZoneB", 0)       // khóa mới từ 0.5.23: mặc định đổi sang cả xe
        screenAuto.value = sp.getBoolean("screenAuto", false)
        (sp.getString("bri", "") ?: "").split(",").mapNotNull { it.toIntOrNull() }.forEachIndexed { i, v -> if (i < 3) bri[i] = v.coerceIn(0, 100) }
        on.value = sp.getBoolean("on", true)
        dayBri = (sp.getString("dayBri", "") ?: "").split(",").mapNotNull { it.toIntOrNull() }.let { if (it.size == 3) it.toIntArray() else null }
        autoBoot.value = sp.getBoolean("autoBoot", true)
        nightOn.value = sp.getBoolean("nightOn", false)
        nightMode.intValue = sp.getInt("nightMode", 0)
        nightFrom.intValue = sp.getInt("nightFrom", 18 * 60)
        nightTo.intValue = sp.getInt("nightTo", 6 * 60)
        nightBri.intValue = sp.getInt("nightBri", 30)
        idleOn.value = sp.getBoolean("idleOn", false)
        idleStyle.intValue = sp.getInt("idleStyle", 0)
        floatOn.value = sp.getBoolean("floatOn", false)
        floatStyle.intValue = sp.getInt("floatStyle", 0).coerceIn(0, 5)
        menuTop.value = sp.getBoolean("menuTop", true)
        voiceReply.value = sp.getBoolean("voiceReply", true)
        voiceQuiet.value = sp.getBoolean("voiceQuiet", true)
        lat = (sp.getString("lat", "") ?: "").toDoubleOrNull() ?: Double.NaN
        lon = (sp.getString("lon", "") ?: "").toDoubleOrNull() ?: Double.NaN
        Scenes.load(if (sp.contains("scenes2")) sp.getString("scenes2", "") ?: "" else null)
        L.init(app); L.set(sp.getInt("lang", 0))
        Theme.mode.intValue = sp.getInt("themeMode", 0).coerceIn(0, 2)
        Theme.accent.intValue = sp.getInt("themeAccent", 0).coerceIn(0, Theme.ACCENTS.size - 1)
        Theme.font.intValue = sp.getInt("themeFont", 0).coerceIn(0, Theme.FONTS.size - 1)
        Theme.style.intValue = sp.getInt("themeStyle", 0).coerceIn(0, Theme.STYLES.size - 1)
        Theme.corner.intValue = sp.getInt("themeCorner", 1).coerceIn(0, Theme.CORNERS.size - 1)
        Theme.fx.value = sp.getBoolean("themeFx2", true)        // mặc định TẮT "Giảm hiệu ứng" trên mọi máy (khóa mới từ 0.5.58, bỏ giá trị cũ do app tự đặt cho máy yếu)
        Theme.scale.intValue = sp.getInt("themeScale", 1).coerceIn(0, Theme.SCALES.size - 1)
        (sp.getString("customs", "") ?: "").split(",").mapNotNull { it.toIntOrNull() }.forEach { customs.add(it) }
        (sp.getString("pins", "") ?: "").split(",").mapNotNull { it.toIntOrNull() }.distinct().take(MAX_PINS).forEach { pins.add(it) }
        (sp.getString("boxPos", "") ?: "").split(",").mapNotNull { it.toIntOrNull() }.forEachIndexed { i, v -> if (i < 4) boxPos[i] = v }
        (sp.getString("col", "") ?: "").split(",").mapNotNull { it.toIntOrNull() }.forEachIndexed { i, v -> if (i < 3) col[i] = v and 0xFFFFFF }
        czone.intValue = sp.getInt("czone", 0).coerceIn(0, 2)
        Auto.z2.intValue = sp.getInt("autoZ2", 0).coerceIn(0, Auto.Z2_NAMES.size)
        Seqs.load(sp.getString("seqs", "") ?: "")
        X.loadSnaps(sp.getString("snaps", "") ?: "")
        ble = BleLed(app)
        main.post(nightLoop)
        Auto.resume()
    }

    fun saveSettings() {
        sp.edit().putBoolean("invert2", invert.value)
            .putInt("speedZoneB", speedZone.intValue).putBoolean("speedWarn", speedWarn.value).putBoolean("screenAuto", screenAuto.value).putBoolean("warnOn", warnOn.value).putInt("turnStyle", turnStyle.intValue).putBoolean("turnFlip", turnFlip.value).putInt("turnFx", turnFx.intValue).putString("turnSpd", turnSpd.joinToString(",")).putInt("alertDist", alertDist.intValue).putInt("alertMaxSec", alertMaxSec.intValue)
            .putBoolean("on", on.value).putString("bri", bri.joinToString(",")).putString("dayBri", dayBri?.joinToString(",") ?: "")
            .putBoolean("autoBoot", autoBoot.value).putBoolean("nightOn", nightOn.value).putInt("nightMode", nightMode.intValue)
            .putInt("nightFrom", nightFrom.intValue).putInt("nightTo", nightTo.intValue).putInt("nightBri", nightBri.intValue)
            .putBoolean("idleOn", idleOn.value).putInt("idleStyle", idleStyle.intValue).putBoolean("floatOn", floatOn.value).putInt("floatStyle", floatStyle.intValue).putBoolean("menuTop", menuTop.value)
            .putBoolean("voiceReply", voiceReply.value).putBoolean("voiceQuiet", voiceQuiet.value)
            .putString("scenes2", Scenes.dump())
            .putInt("lang", L.cur.intValue).putInt("themeMode", Theme.mode.intValue).putInt("themeAccent", Theme.accent.intValue)
            .putInt("themeFont", Theme.font.intValue).putInt("themeScale", Theme.scale.intValue).putInt("themeStyle", Theme.style.intValue).putInt("themeCorner", Theme.corner.intValue).putBoolean("themeFx2", Theme.fx.value)
            .putString("customs", customs.joinToString(",")).putString("pins", pins.joinToString(","))
            .putString("boxPos", boxPos.joinToString(",")).putInt("autoZ2", Auto.z2.intValue).putString("seqs", Seqs.dump()).apply()
    }

    fun addDev(d: Dev) {
        val i = devices.indexOfFirst { it.mac == d.mac }
        if (i >= 0) devices[i] = d else devices.add(d)
    }

    fun autoConnect() {
        val m = savedMac
        try { if (!userOff && m.isNotEmpty() && !connected.value && !ble.busy && ble.ready) ble.connect(m) } catch (_: Exception) {}
    }

    fun onConnected() {}

    // ---------- nhận từ hộp ----------
    /** Chỉ lấy màu và chế độ từ hộp trong vài giây sau khi kết nối (để app hiện đúng cái hộp đang chạy); ngoài lúc đó bỏ qua để không đè lên thao tác của người dùng. */
    @Volatile var syncUntil = 0L
    private fun syncing() = System.currentTimeMillis() < syncUntil && !Auto.running() && alertKind.intValue == 0

    /** Trả lời 7C 01: cờ (08 = đồng nhất) + màu khu 1 + màu khu 2. */
    fun onColor(flag: Int, c1: Int, c2: Int) {
        if (!syncing()) return
        fun fix(c: Int) = if (invert.value) c xor 0xFFFFFF else c
        fun dark(c: Int) = ((c shr 16) and 255) + ((c shr 8) and 255) + (c and 255) <= 3      // 00 00 00 / 01 01 01 = tắt, không lấy làm màu
        if (flag and 0x08 != 0) { if (!dark(c1)) { col[0] = fix(c1); czone.intValue = 0 } }
        else {
            if (!dark(c1)) col[1] = fix(c1)
            if (!dark(c2)) col[2] = fix(c2)
            if (czone.intValue == 0) czone.intValue = 1
        }
    }

    /** Trả lời 7C 02: (độ nhạy << 4 | mode1) | id hiệu ứng | kiểu con | tốc độ. */
    fun onEffect(b1: Int, id: Int, param: Int, spd: Int) {
        m1 = b1 and 0x0F
        // kiểu vùng 2 của app không đụng tới chế độ của hộp nên vẫn lấy chế độ từ hộp; chỉ bỏ qua khi app đang chạy chế độ tự tạo cả xe
        if (System.currentTimeMillis() >= syncUntil || Auto.main.intValue != 0 || alertKind.intValue != 0) return
        val e = Effects.byId(id) ?: return
        if (id >= 100) return
        mode.intValue = id
        if (e.subs.isNotEmpty()) sub[id] = (param - e.base).coerceIn(0, e.subs.size - 1)
        if (spd in 1..10) speed.intValue = spd
        sens.intValue = (b1 shr 4) and 7
    }

    fun onBright(flag: Int, b1: Int, b2: Int) {
        on.value = (flag and 0x40) != 0
        if ((flag and 0x08) != 0) { bri[0] = b1.coerceIn(0, 100) }
        else { bri[1] = b1.coerceIn(0, 100); bri[2] = b2.coerceIn(0, 100) }
    }

    fun onLeds(v: IntArray) {
        for (i in v.indices) if (i < 16) ledRaw[i] = v[i]
        for (s in 0 until 6) leds[s] = ledRaw[ledIdx[s]]
    }

    fun on08(data1: Int, mid: Int, data3: Int) {
        for (s in 0 until 6) wDir[s] = (data1 shr dirBit[s]) and 1 == 1
        wSpeed.intValue = (mid shr 3) and 3
        wStart.intValue = mid and 7
        w08d3 = data3
    }

    fun onFactory(b1: Int, b2: Int) {
        fac = intArrayOf(b1, b2)
        z2.intValue = ((b1 shr 3) and 3).coerceAtMost(2)
    }

    /** Đổi cách gửi màu rồi gửi lại màu đang đặt để thấy ngay. */
    fun setInvert(v: Boolean) {
        if (v == invert.value) return
        invert.value = v
        saveSettings()
        try { if (connected.value && mode.intValue == 0 && !Auto.running()) ble.send(colorFrame(), 2) } catch (e: Exception) {}
    }

    // ---------- đèn ----------
    fun setPower(v: Boolean) {
        on.value = v                                          // chế độ tự tạo tự nghỉ khi tắt và chạy lại khi bật (xem Auto.step)
        if (v) sendBright() else ble.send(Protocol.bright(false, true, 100, 100), 1, true)
        saveSettings()
    }

    fun sendBright() {
        val f = if (zone.intValue == 0) Protocol.bright(on.value, true, bri[0], bri[0])
        else Protocol.bright(on.value, false, bri[1], bri[2])
        ble.send(f, 1, true)
    }

    fun setBright(v: Int) {
        bri[zone.intValue] = v; on.value = true; sceneSel.intValue = -1
        if (Night.active) nightBri.intValue = v.coerceIn(5, 100)      // chỉnh tay ban đêm = đổi mức ban đêm
        sendBright(); saveSettings()
    }
    fun setZoneSel(z: Int) { zone.intValue = z; sendBright() }

    fun colorFrame(): ByteArray =
        if (czone.intValue == 0) Protocol.color(true, col[0], col[0], invert.value)
        else Protocol.color(false, col[1], col[2], invert.value)

    fun setColor(rgb: Int) {
        // chế độ tự tạo cả xe không dùng màu gốc (cầu vồng, lửa…): chọn màu là rời chế độ đó. Kiểu vùng 2 của app thì chạy tiếp.
        if (Auto.main.intValue != 0 && !Auto.usesBase()) { Auto.leave(); if (mode.intValue >= 100) mode.intValue = 0 }
        col[czone.intValue] = rgb
        on.value = true; sceneSel.intValue = -1
        saveColSoon()
        if (!Auto.running()) ble.send(colorFrame(), 2, true)      // đang có chế độ / kiểu vùng 2 của app thì nhịp kế tiếp tự gửi màu mới
    }

    private val saveCol = Runnable { try { sp.edit().putString("col", col.joinToString(",")).putInt("czone", czone.intValue).apply() } catch (e: Exception) {} }
    /** Nhớ màu đang chọn (ghi sau nửa giây để kéo bánh xe màu không ghi liên tục). */
    private fun saveColSoon() { main.removeCallbacks(saveCol); main.postDelayed(saveCol, 500) }

    fun setColorZone(z: Int) { czone.intValue = z; saveColSoon(); if (!Auto.running()) ble.send(colorFrame(), 2, true) }

    fun addCustom() {
        val c = col[czone.intValue]
        if (customs.size >= 8 || customs.contains(c)) return
        customs.add(c); saveSettings()
    }

    fun removeCustom(c: Int) { customs.remove(c); saveSettings() }

    fun sendEffect() {
        if (mode.intValue >= 100) { Auto.startMain(mode.intValue); return }
        Auto.leave()
        val e = Effects.byId(mode.intValue)
        val param = if (e != null && e.subs.isNotEmpty()) e.base + sub[e.id] else 0
        ble.send(Protocol.effect(sens.intValue, m1, mode.intValue, param, speed.intValue), 3, true)
    }

    /** Chạy một chế độ do app tự điều khiển theo id (dùng cho chuỗi màu tự đặt). */
    fun pickAuto(id: Int) { mode.intValue = id; on.value = true; sceneSel.intValue = -1; sendEffect() }
    fun pickEffect(e: Effect) { mode.intValue = e.id; on.value = true; sceneSel.intValue = -1; sendEffect() }
    fun pickSub(e: Effect, i: Int) { sub[e.id] = i; sceneSel.intValue = -1; sendEffect() }

    // ---------- bối cảnh ----------
    /** Cài đặt đang dùng, đóng gói thành một bối cảnh tự tạo. */
    fun currentScene(name: String): Scene {
        val uni = czone.intValue == 0
        val m = mode.intValue
        return Scene(name, uni, if (uni) col[0] else col[1], if (uni) col[0] else col[2], m, if (m in sub.indices) sub[m] else 0,
            speed.intValue, if (Night.active) (dayBri?.get(zone.intValue) ?: bri[zone.intValue]) else bri[zone.intValue], z2.intValue)
    }

    fun applyScene(sc: Scene) {
        on.value = true
        if (sc.uni) { col[0] = sc.c1; czone.intValue = 0 } else { col[1] = sc.c1; col[2] = sc.c2; if (czone.intValue == 0) czone.intValue = 1 }
        mode.intValue = sc.mode
        if (sc.mode in sub.indices) sub[sc.mode] = sc.sub
        speed.intValue = sc.speed
        if (sc.z2 != 0) Auto.clearZ2(false)                    // bối cảnh dùng kiểu vùng 2 của hộp: bỏ kiểu của app
        if (sc.z2 != z2.intValue && Auto.z2.intValue == 0) setZ2(sc.z2)      // chưa đọc được khối 0D thì bỏ qua, giữ nguyên chế độ vùng 2 của hộp
        val b = if (Night.active) minOf(sc.bri, nightBri.intValue) else sc.bri       // ban đêm không sáng quá mức ban đêm
        for (i in 0..2) bri[i] = b
        sendBright()
        ble.send(colorFrame(), 2, true)
        sendEffect()
        saveSettings()
    }

    // ---------- khi lái xe ----------
    fun setPlace(la: Double, lo: Double) {
        val first = lat.isNaN()
        if (!first && Math.abs(la - lat) < 0.2 && Math.abs(lo - lon) < 0.2) return     // chỉ lưu lại khi đi xa hẳn
        lat = la; lon = lo; placeVer.intValue++
        sp.edit().putString("lat", la.toString()).putString("lon", lo.toString()).apply()
    }

    /** Bật dịch vụ nền khi cần giữ kết nối (tự kết nối, nút nổi, cảnh báo rẽ), tắt khi không cần. */
    fun syncService(ctx: Context = app) {
        val c = ctx.applicationContext
        try {
            if (!(autoBoot.value || floatOn.value || warnOn.value || speedWarn.value)) { c.stopService(Intent(c, KeepService::class.java)); return }
            if (Build.VERSION.SDK_INT >= 31 && c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return
            c.startForegroundService(Intent(c, KeepService::class.java))
        } catch (_: Exception) {}
    }
    // chế độ tự tạo (id >= 100) đọc tốc độ trực tiếp, không cần gửi lại lệnh hiệu ứng
    fun setSpeed(v: Int) { if (v != speed.intValue) { speed.intValue = v; if (mode.intValue < 100) sendEffect() } }
    fun setSens(v: Int) { if (v != sens.intValue) { sens.intValue = v; if (mode.intValue < 100) sendEffect() } }

    /** Chế độ màu vùng 2: đọc-sửa-ghi khối 0D, chỉ đổi bit 3-4 của b1. Trả về false nếu chưa đọc được khối từ hộp (không gửi mò). */
    fun setZ2(v: Int): Boolean {
        val f = fac ?: return false
        val b1 = (f[0] and 0x18.inv()) or ((v and 3) shl 3)
        fac = intArrayOf(b1, f[1])
        z2.intValue = v
        ble.send(Protocol.factory(b1, f[1]), 22, true)
        return true
    }

    // ---------- chào mừng ----------
    fun blkBit(i: Int, mask: Int): Boolean = (blk?.get(i) ?: 0) and mask != 0

    /** Trả về false nếu chưa đọc được khối cài đặt từ hộp (không gửi mò). */
    fun setBlk(i: Int, mask: Int, value: Int): Boolean {
        val b = blk?.copyOf() ?: return false
        b[i] = (b[i] and mask.inv()) or (value and mask)
        blk = b; blkVer.intValue++
        ble.send(Protocol.block05(b), 10, true)
        return true
    }

    private fun send08() {
        var d1 = 0
        for (s in 0 until 6) if (wDir[s]) d1 = d1 or (1 shl dirBit[s])
        ble.send(Protocol.block08(d1, wSpeed.intValue, wStart.intValue, w08d3), 21, true)
    }

    fun setWSpeed(v: Int) { wSpeed.intValue = v; send08() }
    fun setWStart(code: Int) { wStart.intValue = code; send08() }
    fun setWDir(seg: Int, right: Boolean) { wDir[seg] = right; send08() }

    fun setLed(seg: Int, v: Int) {
        val n = v.coerceIn(1, 200)
        leds[seg] = n; ledRaw[ledIdx[seg]] = n
        ble.send(Protocol.leds(ledRaw), 20, true)
    }

    /** Chạm hộp phụ: hộp đó nháy để biết đang gắn ở cửa nào. */
    fun flashBox(n: Int) {
        if (pickBox.intValue == n) { pickBox.intValue = 0; return }
        pickBox.intValue = n
        ble.send(Protocol.door(n, 0))
    }

    /** Gán hộp đang nháy vào đoạn [seg] (2..5), hoặc 0 = Khác. Mã vị trí gửi đi: trước trái 1, trước phải 2, sau trái 3, sau phải 4 (chưa kiểm chứng). */
    fun assignBox(seg: Int) {
        val n = pickBox.intValue
        if (n == 0) return
        if (seg >= 2) for (i in 0 until 4) if (boxPos[i] == seg) boxPos[i] = -1
        boxPos[n - 1] = seg
        pickBox.intValue = 0
        ble.send(Protocol.door(n, if (seg >= 2) seg - 1 else 0))      // "Khác" = mã 0, đúng như app gốc
        saveSettings()
    }

    /** Giữ lâu một hộp phụ: đưa hộp đó về "Khác" (không gắn ở cửa nào). */
    fun unassignBox(n: Int) {
        boxPos[n - 1] = 0
        pickBox.intValue = 0
        ble.send(Protocol.door(n, 0))
        saveSettings()
    }

    fun resetDoors() {
        for (i in 0 until 4) boxPos[i] = -1
        pickBox.intValue = 0
        ble.send(Protocol.doorReset())
        saveSettings()
    }

    /**
     * Cảnh báo rẽ kiểu "đèn chạy" tạm đổi hướng chạy (khối 07) của hộp. Ghi lại giá trị gốc vào máy để nếu app bị tắt giữa chừng,
     * lần kết nối sau vẫn trả lại được. [p] = null: đã trả xong.
     */
    fun saveSnaps(text: String) { try { sp.edit().putString("snaps", text).apply() } catch (e: Exception) {} }
    fun holdP07(p: IntArray?) { try { sp.edit().putString("p07Hold", p?.joinToString(",") ?: "").apply() } catch (e: Exception) {} }
    /** Gọi sau khi kết nối: còn giá trị gốc chưa trả thì trả lại cho hộp. */
    fun restoreP07() {
        val v = (sp.getString("p07Hold", "") ?: "").split(",").mapNotNull { it.toIntOrNull() }
        if (v.size != 3) return
        ble.send(Protocol.block07(v[0], v[1], v[2]), 23)
        holdP07(null)
    }

    /** Lệnh giọng nói "đổi hướng": đảo hướng ánh sáng chạy của mọi đoạn (khối 07, đọc-sửa-ghi; byte giữa giữ nguyên). */
    fun flipDir(): Boolean {
        val p = p07 ?: run { ble.send(Protocol.query(0x07)); return false }
        val n = intArrayOf(p[0] xor 0xFF, p[1], p[2] xor 0xFF)
        p07 = n
        ble.send(Protocol.block07(n[0], n[1], n[2]), 23, true)
        return true
    }

    fun refreshBlocks() { for (q in intArrayOf(0x00, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0D)) ble.send(Protocol.query(q)) }
}
