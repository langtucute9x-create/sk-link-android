package vn.sklight.app

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*

/** Trạng thái chung của app + các hành động gửi lệnh tới hộp đèn. */
object S {
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
    val mode = mutableIntStateOf(0)                       // id hiệu ứng
    val sub = mutableStateListOf<Int>().also { l -> repeat(64) { l.add(0) } }   // kiểu con đang chọn theo id (đếm từ 0)
    val speed = mutableIntStateOf(5)
    val sens = mutableIntStateOf(6)
    val z2 = mutableIntStateOf(0)                         // chế độ màu vùng 2: 0 tĩnh, 1 tự động, 2 nhấp nháy

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
    val aroundOn = mutableStateOf(false)
    val boxPos = mutableStateListOf(-1, -1, -1, -1)       // -1 chưa gán, 0 khác, 2..5 = đoạn trên sơ đồ
    val pickBox = mutableIntStateOf(0)                    // hộp phụ đang nháy (1..4), 0 = không

    // cài đặt
    val invert = mutableStateOf(true)

    // dẫn đường + cảnh báo (bản thử)
    val warnOn = mutableStateOf(false)                    // nháy đèn khi sắp rẽ
    val alertDist = mutableIntStateOf(150)                // báo trước bao nhiêu mét
    val navOn = mutableStateOf(false)
    val navDir = mutableStateOf("?")
    val navDist = mutableIntStateOf(-1)
    val navStreet = mutableStateOf("")
    val navApp = mutableStateOf("")
    val navLog = mutableStateListOf<String>()             // thông báo thô của app dẫn đường, mới nhất ở đầu
    val signSeen = mutableStateOf("")                     // con số km/h thấy trong thông báo (nếu có)
    val alertKind = mutableIntStateOf(0)                  // xem AlertBridge
    val alertPhase = mutableStateOf(false)                // pha sáng / tối của nhịp nháy

    fun init(c: Context) {
        if (inited) return
        inited = true
        app = c.applicationContext
        sp = app.getSharedPreferences("sklight", Context.MODE_PRIVATE)
        invert.value = sp.getBoolean("invert", true)
        warnOn.value = sp.getBoolean("warnOn", false)
        alertDist.intValue = sp.getInt("alertDist", 150)
        (sp.getString("customs", "") ?: "").split(",").mapNotNull { it.toIntOrNull() }.forEach { customs.add(it) }
        (sp.getString("boxPos", "") ?: "").split(",").mapNotNull { it.toIntOrNull() }.forEachIndexed { i, v -> if (i < 4) boxPos[i] = v }
        ble = BleLed(app)
    }

    fun saveSettings() {
        sp.edit().putBoolean("invert", invert.value)
            .putBoolean("warnOn", warnOn.value).putInt("alertDist", alertDist.intValue)
            .putString("customs", customs.joinToString(","))
            .putString("boxPos", boxPos.joinToString(",")).apply()
    }

    fun addDev(d: Dev) {
        val i = devices.indexOfFirst { it.mac == d.mac }
        if (i >= 0) devices[i] = d else devices.add(d)
    }

    fun autoConnect() {
        val m = savedMac
        try { if (m.isNotEmpty() && !connected.value && !ble.busy && ble.ready) ble.connect(m) } catch (_: Exception) {}
    }

    fun onConnected() {}

    // ---------- nhận từ hộp ----------
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

    // ---------- đèn ----------
    fun setPower(v: Boolean) {
        on.value = v
        if (v) sendBright() else ble.send(Protocol.bright(false, true, 100, 100), 1, true)
    }

    fun sendBright() {
        val f = if (zone.intValue == 0) Protocol.bright(on.value, true, bri[0], bri[0])
        else Protocol.bright(on.value, false, bri[1], bri[2])
        ble.send(f, 1, true)
    }

    fun setBright(v: Int) { bri[zone.intValue] = v; on.value = true; sendBright() }
    fun setZoneSel(z: Int) { zone.intValue = z; sendBright() }

    fun colorFrame(): ByteArray =
        if (czone.intValue == 0) Protocol.color(true, col[0], col[0], invert.value)
        else Protocol.color(false, col[1], col[2], invert.value)

    fun setColor(rgb: Int) {
        col[czone.intValue] = rgb
        on.value = true
        ble.send(colorFrame(), 2, true)
    }

    fun setColorZone(z: Int) { czone.intValue = z; ble.send(colorFrame(), 2, true) }

    fun addCustom() {
        val c = col[czone.intValue]
        if (customs.size >= 8 || customs.contains(c)) return
        customs.add(c); saveSettings()
    }

    fun removeCustom(c: Int) { customs.remove(c); saveSettings() }

    fun sendEffect() {
        val e = Effects.byId(mode.intValue)
        val param = if (e != null && e.subs.isNotEmpty()) e.base + sub[e.id] else 0
        ble.send(Protocol.effect(sens.intValue, z2.intValue, mode.intValue, param, speed.intValue), 3, true)
    }

    fun pickEffect(e: Effect) { mode.intValue = e.id; on.value = true; sendEffect() }
    fun pickSub(e: Effect, i: Int) { sub[e.id] = i; sendEffect() }
    fun setSpeed(v: Int) { if (v != speed.intValue) { speed.intValue = v; sendEffect() } }
    fun setSens(v: Int) { if (v != sens.intValue) { sens.intValue = v; sendEffect() } }
    fun setZ2(v: Int) { z2.intValue = v; sendEffect() }

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
        ble.send(Protocol.door(n, if (seg >= 2) seg - 1 else 0x0F))
        saveSettings()
    }

    /** Giữ lâu một hộp phụ: đưa hộp đó về "Khác" (không gắn ở cửa nào). */
    fun unassignBox(n: Int) {
        boxPos[n - 1] = 0
        pickBox.intValue = 0
        ble.send(Protocol.door(n, 0x0F))
        saveSettings()
    }

    fun resetDoors() {
        for (i in 0 until 4) boxPos[i] = -1
        pickBox.intValue = 0
        ble.send(Protocol.doorReset())
        saveSettings()
    }

    fun refreshBlocks() { for (q in intArrayOf(0x00, 0x05, 0x06, 0x08)) ble.send(Protocol.query(q)) }
}
