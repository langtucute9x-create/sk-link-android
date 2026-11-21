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
    val isCzh = mutableStateOf(false)
    val scanning = mutableStateOf(false)
    val devices = mutableStateListOf<Dev>()
    var savedMac: String
        get() = sp.getString("mac", "") ?: ""
        set(v) { sp.edit().putString("mac", v).apply() }

    // đèn
    val on = mutableStateOf(true)
    val zone = mutableIntStateOf(0)                       // 0 đồng nhất, 1 khu 1, 2 khu 2
    val bri = mutableStateListOf(100, 100, 100)
    val col = mutableStateListOf(0xFFFFFF, 0xFFFFFF, 0xFFFFFF)
    val hue = mutableStateListOf(0f, 0f, 0f)
    val sat = mutableStateListOf(0f, 0f, 0f)
    val mode = mutableIntStateOf(0)                       // id hiệu ứng
    val sub = mutableStateListOf<Int>().also { l -> repeat(64) { l.add(0) } }
    val speed = mutableIntStateOf(5)

    // hàm gốc
    var blk: IntArray? = null
    var w08: IntArray? = null
    val blkVer = mutableIntStateOf(0)

    // cài đặt
    val invert = mutableStateOf(true)
    val leftZone = mutableIntStateOf(1)                   // khu nào là bên trái xe
    val limit = mutableIntStateOf(0)                      // giới hạn tốc độ nhập tay, 0 = tắt
    val alertDist = mutableIntStateOf(150)                // nháy khi còn ≤ m
    val blinkColor = mutableIntStateOf(0xFF9600)

    // dẫn đường
    val navOn = mutableStateOf(false)
    val navDir = mutableStateOf("?")
    val navDist = mutableIntStateOf(-1)
    val navStreet = mutableStateOf("")
    val speedKmh = mutableFloatStateOf(0f)
    val alertText = mutableStateOf("")

    fun init(c: Context) {
        if (inited) return
        inited = true
        app = c.applicationContext
        sp = app.getSharedPreferences("sklight", Context.MODE_PRIVATE)
        invert.value = sp.getBoolean("invert", true)
        leftZone.intValue = sp.getInt("leftZone", 1)
        limit.intValue = sp.getInt("limit", 0)
        alertDist.intValue = sp.getInt("alertDist", 150)
        ble = BleLed(app)
    }

    fun saveSettings() {
        sp.edit().putBoolean("invert", invert.value).putInt("leftZone", leftZone.intValue)
            .putInt("limit", limit.intValue).putInt("alertDist", alertDist.intValue).apply()
    }

    fun addDev(d: Dev) {
        val i = devices.indexOfFirst { it.mac == d.mac }
        if (i >= 0) devices[i] = d else devices.add(d)
    }

    fun autoConnect() {
        val m = savedMac
        if (m.isNotEmpty() && !connected.value && ble.ready) ble.connect(m)
    }

    fun onConnected() { sendBright() }

    fun onBright(flag: Int, b1: Int, b2: Int) {
        on.value = (flag and 0x40) != 0
        if ((flag and 0x08) != 0) { bri[0] = b1.coerceIn(0, 100) }
        else { bri[1] = b1.coerceIn(0, 100); bri[2] = b2.coerceIn(0, 100) }
    }

    // ---------- gửi lệnh ----------
    fun setPower(v: Boolean) {
        on.value = v
        if (v) sendBright() else ble.send(Protocol.bright(false, true, 100, 100), 1, true)
    }

    fun sendBright() {
        val z = zone.intValue
        val f = if (z == 0) Protocol.bright(on.value, true, bri[0], bri[0])
        else Protocol.bright(on.value, false, bri[1], bri[2])
        ble.send(f, 1, true)
    }

    fun setBright(v: Int) { bri[zone.intValue] = v; on.value = true; sendBright() }

    fun colorFrame(): ByteArray =
        if (zone.intValue == 0) Protocol.color(true, col[0], col[0], invert.value)
        else Protocol.color(false, col[1], col[2], invert.value)

    fun setColor(rgb: Int, h: Float = -1f, s: Float = -1f) {
        val z = zone.intValue
        col[z] = rgb
        if (h >= 0f) { hue[z] = h; sat[z] = s }
        if (mode.intValue != 0) { mode.intValue = 0; sendEffect() }
        on.value = true
        ble.send(colorFrame(), 2, true)
    }

    fun sendEffect() { ble.send(Protocol.effect(mode.intValue, sub[mode.intValue], speed.intValue), 3, true) }

    fun pickEffect(e: Effect) {
        if (mode.intValue == e.id && e.subs > 0) sub[e.id] = (sub[e.id] + 1) % e.subs
        mode.intValue = e.id
        on.value = true
        sendEffect()
    }

    fun setSpeed(v: Int) { speed.intValue = v; if (mode.intValue != 0) sendEffect() }

    fun setZoneSel(z: Int) { zone.intValue = z; sendBright() }

    // ---------- hàm gốc (khối 05) ----------
    fun blkBit(i: Int, mask: Int): Boolean = (blk?.get(i) ?: 0) and mask != 0

    fun setBlkBit(i: Int, mask: Int, v: Boolean) {
        val b = blk?.copyOf() ?: return
        b[i] = if (v) b[i] or mask else b[i] and mask.inv()
        blk = b; blkVer.intValue++
        ble.send(Protocol.block05(b), 10, true)
    }

    fun setBlkField(i: Int, mask: Int, value: Int) {
        val b = blk?.copyOf() ?: return
        b[i] = (b[i] and mask.inv()) or (value and mask)
        blk = b; blkVer.intValue++
        ble.send(Protocol.block05(b), 10, true)
    }

    fun setBlkByte(i: Int, value: Int) {
        val b = blk?.copyOf() ?: return
        b[i] = value and 0xFF
        blk = b; blkVer.intValue++
        ble.send(Protocol.block05(b), 10, true)
    }

    fun refreshBlocks() { ble.send(Protocol.query(0x05)); ble.send(Protocol.query(0x08)); ble.send(Protocol.query(0x00)) }
}
