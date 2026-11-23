package vn.sklight.app

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

class Dev(val name: String, val mac: String, val rssi: Int)

@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
class BleLed(private val ctx: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val adapter: BluetoothAdapter? =
        (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    private var gatt: BluetoothGatt? = null
    private var wch: BluetoothGattCharacteristic? = null
    private class Item(val key: Int, val data: ByteArray)
    private val q = ConcurrentLinkedQueue<Item>()
    private var pumping = false
    private var wantMac: String? = null
    private var scanning = false
    private var disc = false                      // đã gọi dò dịch vụ cho kết nối hiện tại chưa
    @Volatile var lastSetMs = 0L
    /** Gói quảng bá Bluetooth gần nhất của từng hộp (để tìm số seri). */
    val adv = HashMap<String, ByteArray>()
    @Volatile private var paused = false            // đang đọc thông tin hộp: tạm dừng hàng đợi ghi
    private var readDone: ((ByteArray?) -> Unit)? = null

    private fun u(s: String) = UUID.fromString("0000$s-0000-1000-8000-00805f9b34fb")
    private val svcId = u("ae30"); private val wId = u("ae01"); private val nId = u("ae02")
    private val cccd = u("2902")

    val ready get() = adapter?.isEnabled == true
    /** Đang có kết nối hoặc đang kết nối dở: không mở thêm kết nối thứ hai. */
    val busy get() = gatt != null

    // ---------------- quét ----------------
    private val scanCb = object : ScanCallback() {
        override fun onScanResult(type: Int, r: ScanResult) {
            val name = r.scanRecord?.deviceName ?: r.device.name ?: return
            val ok = name.contains("CTH2") || name.contains("CZH2") || name.contains("Pocket") || name.contains("BT2")
            if (!ok) return
            val mac = r.device.address
            r.scanRecord?.bytes?.let { b -> adv[mac] = b; try { ctx.getSharedPreferences("advlog", Context.MODE_PRIVATE).edit().putString(mac, b.joinToString("") { "%02X".format(it) }).apply() } catch (_: Exception) {} }
            main.post { S.addDev(Dev(name, mac, r.rssi)) }
        }
    }

    fun scan() {
        val sc = adapter?.bluetoothLeScanner ?: run { S.conn.value = "Bluetooth đang tắt"; return }
        if (scanning) return
        scanning = true; S.devices.clear(); S.scanning.value = true
        try { sc.startScan(scanCb) } catch (e: Exception) { scanning = false; S.scanning.value = false; return }
        main.postDelayed({ stopScan() }, 7000)
    }

    fun stopScan() {
        if (!scanning) return
        scanning = false; S.scanning.value = false
        try { adapter?.bluetoothLeScanner?.stopScan(scanCb) } catch (_: Exception) {}
    }

    // ---------------- kết nối ----------------
    fun connect(mac: String) {
        stopScan()
        S.userOff = false
        wantMac = mac
        S.savedMac = mac
        S.blk = null; S.blkVer.intValue++; S.fac = null; S.p07 = null; S.m1 = 0; main.post { X.clear() }          // cài đặt đọc từ hộp trước không dùng cho lần kết nối mới (có thể là hộp khác)
        val dev = try { adapter?.getRemoteDevice(mac) } catch (e: Exception) { null } ?: return
        closeGatt()
        S.conn.value = "Đang kết nối…"
        gatt = if (Build.VERSION.SDK_INT >= 23) dev.connectGatt(ctx, false, cb, BluetoothDevice.TRANSPORT_LE) else dev.connectGatt(ctx, false, cb)
    }

    fun disconnect() { wantMac = null; closeGatt(); S.connected.value = false; S.conn.value = "Chưa kết nối" }

    private fun closeGatt() {
        q.clear(); wch = null
        try { gatt?.disconnect() } catch (_: Exception) {}
        try { gatt?.close() } catch (_: Exception) {}
        gatt = null
    }

    private val cb = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // Xin tăng cỡ gói như app gốc của hộp (mặc định chỉ 20 byte mỗi lần ghi, lệnh số LED dài 21 byte sẽ bị cắt).
                main.post {
                    S.conn.value = "Đang dò dịch vụ…"
                    disc = false
                    val asked = try { g.requestMtu(247) } catch (_: Exception) { false }
                    if (asked) main.postDelayed({ discover(g) }, 1500) else discover(g)      // hộp không trả lời thì vẫn đi tiếp
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                main.post {
                    S.connected.value = false
                    S.conn.value = "Mất kết nối"
                    wch = null
                    try { g.close() } catch (_: Exception) {}
                    if (gatt === g) gatt = null
                    val m = wantMac
                    if (m != null) main.postDelayed({ if (wantMac == m && gatt == null) connect(m) }, 3000)
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) { main.post { discover(g) } }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val svc = g.getService(svcId)
            val w = svc?.getCharacteristic(wId)
            if (w == null) { main.post { S.conn.value = "Không thấy dịch vụ AE30" }; return }
            wch = w
            val n = svc?.getCharacteristic(nId)
            if (n != null) {
                g.setCharacteristicNotification(n, true)
                val d = n.getDescriptor(cccd)
                if (d != null) {
                    if (Build.VERSION.SDK_INT >= 33) g.writeDescriptor(d, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    else { d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE; g.writeDescriptor(d) }
                    return
                }
            }
            ready(g)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            val h = descDone
            if (h != null) h() else if (tap == null) ready(g)          // đang thử kênh JieLi: bật báo thêm không được chạy lại phần "đã kết nối"
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            val v = c.value
            if (v != null) onNotify(c, v)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) { onNotify(c, value) }

        @Deprecated("API < 33")
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            val v = if (status == BluetoothGatt.GATT_SUCCESS) c.value else null
            main.post { readDone?.invoke(v) }
        }
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            main.post { readDone?.invoke(if (status == BluetoothGatt.GATT_SUCCESS) value else null) }
        }
    }

    // ---------------- tìm số seri: đọc mọi đặc tính đọc được của hộp ----------------
    class Probe(val svc: String, val ch: String, val props: String, val value: ByteArray?)

    /** Đọc lần lượt mọi đặc tính có quyền đọc (kể cả dịch vụ Device Information 180A nếu hộp có). Trả về danh sách qua [done]. */
    fun probe(done: (List<Probe>) -> Unit) {
        val g = gatt ?: run { done(emptyList()); return }
        val all = ArrayList<Pair<BluetoothGattService, BluetoothGattCharacteristic>>()
        for (s in g.services ?: emptyList()) for (c in s.characteristics ?: emptyList()) all.add(s to c)
        val out = ArrayList<Probe>()
        fun pr(c: BluetoothGattCharacteristic): String {
            val p = c.properties; val l = ArrayList<String>()
            if (p and BluetoothGattCharacteristic.PROPERTY_READ != 0) l.add("đọc")
            if (p and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) l.add("ghi")
            if (p and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) l.add("ghi nhanh")
            if (p and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) l.add("báo")
            if (p and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) l.add("báo xác nhận")
            return l.joinToString(", ")
        }
        paused = true
        var i = 0
        lateinit var next: () -> Unit
        val timeout = Runnable { readDone?.invoke(null) }
        next = next@{
            while (i < all.size && all[i].second.properties and BluetoothGattCharacteristic.PROPERTY_READ == 0) {
                out.add(Probe(all[i].first.uuid.toString(), all[i].second.uuid.toString(), pr(all[i].second), null)); i++
            }
            if (i >= all.size || gatt !== g) { readDone = null; paused = false; done(out); return@next }
            val (s, c) = all[i]
            readDone = { v ->
                main.removeCallbacks(timeout)
                readDone = null
                out.add(Probe(s.uuid.toString(), c.uuid.toString(), pr(c), v)); i++
                main.postDelayed({ next() }, 60)
            }
            val ok = try { g.readCharacteristic(c) } catch (_: Exception) { false }
            if (!ok) readDone?.invoke(null) else main.postDelayed(timeout, 1500)
        }
        main.postDelayed({ next() }, 300)          // chờ hàng đợi ghi đang chạy dừng hẳn
    }

    // ---------------- thử kênh riêng của chip JieLi (RCSP): hỏi địa chỉ Bluetooth của chính hộp ----------------
    // Mục đích: iPhone không đọc được địa chỉ Bluetooth, nên cần một cách khác để nhận ra từng hộp (kích hoạt theo hộp).
    // Khung RCSP (theo thư viện jl_bt_ota của JieLi): FE DC BA | cờ | mã lệnh | dài (2 byte) | số thứ tự | dữ liệu | EF.
    // Lệnh 03 (GetTargetInfo) chỉ HỎI thông tin; thuộc tính 17 trong câu trả lời là địa chỉ BLE, thuộc tính 2 là địa chỉ EDR.
    @Volatile private var tap: ((String, ByteArray) -> Unit)? = null
    @Volatile private var descDone: (() -> Unit)? = null

    private fun sh(u: UUID) = u.toString().let { if (it.endsWith("-0000-1000-8000-00805f9b34fb")) it.substring(4, 8) else it }

    private fun onNotify(c: BluetoothGattCharacteristic, v: ByteArray) {
        val h = tap
        val own = c.uuid == nId && c.service?.uuid == svcId
        if (h != null) h(sh(c.service?.uuid ?: svcId) + "/" + sh(c.uuid), v.copyOf())
        if (h == null || own) rx(v)               // báo từ kênh khác (chỉ bật lúc thử) không đưa vào bộ đọc lệnh đèn
    }

    class JlResult(val log: List<String>, val bleAddr: String?, val edrAddr: String?, val channel: String?)

    fun jlProbe(done: (JlResult) -> Unit) {
        val g = gatt ?: run { done(JlResult(listOf("Chưa kết nối hộp đèn."), null, null, null)); return }
        paused = true
        Thread {
            val log = ArrayList<String>()
            var ble: String? = null; var edr: String? = null; var chan: String? = null
            try {
                val inbox = java.util.concurrent.LinkedBlockingQueue<Pair<String, ByteArray>>()
                tap = { ch, v -> inbox.offer(ch to v) }
                fun hx(b: ByteArray) = b.joinToString(" ") { "%02X".format(it) }
                fun mac(b: ByteArray) = b.joinToString(":") { "%02X".format(it) }
                fun wr(c: BluetoothGattCharacteristic, data: ByteArray): Boolean {
                    val type = if (c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    repeat(6) {
                        val ok = try {
                            if (Build.VERSION.SDK_INT >= 33) g.writeCharacteristic(c, data, type) == 0
                            else { c.writeType = type; c.value = data; g.writeCharacteristic(c) }
                        } catch (_: Exception) { false }
                        if (ok) { log.add("   → " + hx(data)); return true }
                        Thread.sleep(70)
                    }
                    log.add("   → (máy không gửi được) " + hx(data)); return false
                }
                /** Chờ báo về tối đa [ms]; ghi lại mọi gói; [stop] trả true thì dừng sớm. */
                fun collect(ms: Long, stop: (ByteArray) -> Boolean): Boolean {
                    val end = System.currentTimeMillis() + ms
                    while (true) {
                        val left = end - System.currentTimeMillis()
                        if (left <= 0) return false
                        val it = inbox.poll(left, java.util.concurrent.TimeUnit.MILLISECONDS) ?: return false
                        log.add("   ← " + it.first + ": " + hx(it.second))
                        if (stop(it.second)) return true
                    }
                }
                /** Tìm khung trả lời của lệnh 03 trong dữ liệu đã nhận; trả về (trạng thái, phần thuộc tính). */
                fun reply03(b: ByteArray): Pair<Int, ByteArray>? {
                    var i = 0
                    while (i + 8 <= b.size) {
                        if ((b[i].toInt() and 0xFF) == 0xFE && (b[i + 1].toInt() and 0xFF) == 0xDC && (b[i + 2].toInt() and 0xFF) == 0xBA) {
                            val flag = b[i + 3].toInt() and 0xFF; val op = b[i + 4].toInt() and 0xFF
                            val len = ((b[i + 5].toInt() and 0xFF) shl 8) or (b[i + 6].toInt() and 0xFF)
                            val end = i + 7 + len
                            if (end >= b.size) return null                 // chưa đủ khung, chờ gói tiếp
                            if ((b[end].toInt() and 0xFF) == 0xEF) {
                                if (op == 0x03 && flag and 0x80 == 0 && len >= 2) return (b[i + 7].toInt() and 0xFF) to b.copyOfRange(i + 9, end)
                                i = end + 1; continue
                            }
                        }
                        i++
                    }
                    return null
                }
                fun attrs(d: ByteArray) {
                    var i = 0
                    while (i + 2 <= d.size) {
                        val n = d[i].toInt() and 0xFF
                        if (n == 0 || i + 1 + n > d.size) break
                        val t = d[i + 1].toInt() and 0xFF
                        val v = d.copyOfRange(i + 2, i + 1 + n)
                        log.add("   • thuộc tính $t: " + hx(v))
                        if (t == 17 && v.size >= 7) ble = mac(v.copyOfRange(1, 7))
                        if (t == 2 && v.size >= 6) edr = mac(v.copyOfRange(0, 6))
                        i += n + 1
                    }
                }
                Thread.sleep(350)                                          // chờ hàng đợi lệnh đèn dừng hẳn
                val std = setOf("1800", "1801", "180a")
                val svcs = (g.services ?: emptyList()).filter { sh(it.uuid) !in std }
                log.add("--- Bật báo ở các kênh ---")
                for (s in svcs) for (c in s.characteristics ?: emptyList()) {
                    val p = c.properties
                    val nt = p and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
                    val ind = p and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
                    if (!nt && !ind) continue
                    if (s.uuid == svcId && c.uuid == nId) continue          // kênh lệnh đèn đã bật sẵn
                    val d = c.getDescriptor(cccd) ?: continue
                    try { g.setCharacteristicNotification(c, true) } catch (_: Exception) {}
                    val sem = java.util.concurrent.Semaphore(0)
                    descDone = { sem.release() }
                    val v = if (nt) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                    val ok = try {
                        if (Build.VERSION.SDK_INT >= 33) g.writeDescriptor(d, v) == 0 else { d.value = v; g.writeDescriptor(d) }
                    } catch (_: Exception) { false }
                    val got = ok && sem.tryAcquire(1500, java.util.concurrent.TimeUnit.MILLISECONDS)
                    descDone = null
                    log.add(sh(s.uuid) + "/" + sh(c.uuid) + ": " + if (got) "đã bật" else "không bật được")
                    Thread.sleep(80)
                }
                collect(400) { false }                                      // gói hộp tự gửi ngay sau khi bật báo (nếu có)
                // Kênh để ghi: ưu tiên dịch vụ AE00 (kênh RCSP chuẩn của JieLi), kênh lệnh đèn AE30/AE01 thử sau cùng.
                val ws = ArrayList<Pair<BluetoothGattService, BluetoothGattCharacteristic>>()
                for (s in svcs) for (c in s.characteristics ?: emptyList()) {
                    val p = c.properties
                    if (p and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) == 0) continue
                    if (p and BluetoothGattCharacteristic.PROPERTY_READ != 0) { log.add("Bỏ qua " + sh(s.uuid) + "/" + sh(c.uuid) + " (ô cấu hình, không phải kênh lệnh)"); continue }
                    ws.add(s to c)
                }
                fun rank(x: Pair<BluetoothGattService, BluetoothGattCharacteristic>) = when {
                    sh(x.first.uuid) == "ae00" -> 0
                    x.first.uuid != svcId -> 1
                    x.second.uuid != wId -> 2
                    else -> 3
                }
                ws.sortBy { rank(it) }
                val auth = try { com.jieli.jl_bt_ota.impl.RcspAuth.open() } catch (t: Throwable) { null }
                if (auth == null) log.add("Máy này không nạp được thư viện xác thực JieLi (" + com.jieli.jl_bt_ota.impl.RcspAuth.error + "): chỉ thử hỏi thẳng.")
                var sn = 0
                for ((s, c) in ws) {
                    if (chan != null || gatt !== g) break
                    val name = sh(s.uuid) + "/" + sh(c.uuid)
                    log.add("--- Thử kênh $name ---")
                    inbox.clear()
                    fun ask(): Boolean {
                        val acc = java.io.ByteArrayOutputStream()
                        val f = byteArrayOf(0xFE.toByte(), 0xDC.toByte(), 0xBA.toByte(), 0xC0.toByte(), 0x03, 0x00, 0x06, (sn++ and 0xFF).toByte(), -1, -1, -1, -1, 0x00, 0xEF.toByte())
                        if (!wr(c, f)) return false
                        return collect(1300) { v ->
                            acc.write(v)
                            val r = reply03(acc.toByteArray())
                            if (r != null) { chan = name; log.add("   Hộp TRẢ LỜI lệnh hỏi thông tin, trạng thái " + r.first + ":"); attrs(r.second); true } else false
                        }
                    }
                    if (ask()) break
                    if (auth == null) { log.add("   Không trả lời."); continue }
                    // Bước xác thực của JieLi: app gửi số ngẫu nhiên → hộp trả bản mã hóa → app báo "pass" → hộp gửi số của nó → app trả bản mã hóa → hộp báo "pass".
                    log.add("   Không trả lời khi hỏi thẳng, thử xác thực JieLi:")
                    var rnd = auth.getRandomAuthData()
                    var k = 0
                    while (rnd != null && rnd.any { (it.toInt() and 0xFF) == 0x2E } && k++ < 30) rnd = auth.getRandomAuthData()      // tránh byte mở đầu lệnh đèn
                    if (rnd == null || rnd.size != 17) { log.add("   Thư viện không tạo được số ngẫu nhiên."); continue }
                    if (!wr(c, rnd)) continue
                    var devEnc: ByteArray? = null
                    collect(1500) { v -> if (v.size == 17 && v[0].toInt() == 1) { devEnc = v; true } else false }
                    val de = devEnc
                    if (de == null) { log.add("   Hộp không trả lời xác thực ở kênh này."); continue }
                    val exp = auth.getEncryptedAuthData(rnd)
                    log.add("   Hộp trả lời xác thực: " + if (exp != null && exp.contentEquals(de)) "KHỚP khóa chuẩn của JieLi" else "KHÔNG khớp khóa chuẩn (hộp dùng khóa riêng)")
                    wr(c, byteArrayOf(2, 0x70, 0x61, 0x73, 0x73))
                    var devRnd: ByteArray? = null
                    collect(2500) { v -> if (v.size == 17 && v[0].toInt() == 0) { devRnd = v; true } else false }
                    val dr = devRnd
                    if (dr == null) log.add("   Hộp không gửi số của nó.")
                    else {
                        val e = auth.getEncryptedAuthData(dr)
                        if (e != null) wr(c, e)
                        val pass = collect(2500) { v -> v.size == 5 && v[0].toInt() == 2 }
                        log.add(if (pass) "   Xác thực XONG." else "   Hộp không báo xác thực xong.")
                    }
                    if (ask()) break
                    log.add("   Sau xác thực vẫn không trả lời lệnh hỏi thông tin.")
                }
                if (ws.isEmpty()) log.add("Hộp không có kênh ghi nào để thử.")
            } catch (t: Throwable) { log.add("Lỗi khi thử: $t") }
            tap = null; descDone = null; paused = false
            val r = JlResult(log, ble, edr, chan)
            main.post { done(r) }
        }.start()
    }

    private fun discover(g: BluetoothGatt) {
        if (disc || gatt !== g) return
        disc = true
        try { g.discoverServices() } catch (_: Exception) {}
    }

    private fun ready(g: BluetoothGatt) {
        main.post {
            S.connected.value = true
            S.devName.value = g.device.name ?: g.device.address
            S.conn.value = "Đã kết nối"
            Act.onConnected(g.device.address)
            Notice.show(ctx, L.tr("Đã kết nối với " + S.devName.value))
            if (L.cur.intValue == 0) Speaker.say(ctx, "Đã kết nối với hộp đèn", force = true)      // chủ dự án muốn có giọng nói báo đã kết nối
            S.isCzh.value = (g.device.name ?: "").contains("CZH2")
            // Gói mở app + độ sáng trước; các lệnh hỏi gửi sau 700 ms để câu trả lời không rơi vào khoảng "vừa gửi lệnh" bị bỏ qua.
            main.postDelayed({
                val init = Protocol.init()
                put(init[0])
                if (S.on.value) S.sendBright()          // kết nối lại không bao giờ tự tắt đèn của hộp
                main.postDelayed({ S.restoreP07(); S.syncUntil = System.currentTimeMillis() + 4000; send(Protocol.query(0x01)); for (f in init.drop(1)) put(f) }, 700)
            }, 800)
        }
    }

    // ---------------- gửi ----------------
    /** key >= 0: lệnh cùng key chưa gửi sẽ bị thay bằng lệnh mới (kéo thanh trượt không dồn hàng đợi). */
    fun send(data: ByteArray, key: Int = -1, isSet: Boolean = false) {
        // Máy chưa kích hoạt: không gửi lệnh ghi xuống hộp (khóa điều khiển); lệnh hỏi trạng thái vẫn đi.
        if (!Act.ok.value && data.size > 1 && (data[1].toInt() and 0xFF) == Protocol.T_WRITE) return
        put(data, key, isSet)
    }

    private fun put(data: ByteArray, key: Int = -1, isSet: Boolean = false) {
        if (isSet) lastSetMs = System.currentTimeMillis()
        if (key >= 0) q.removeIf { it.key == key }
        q.add(Item(key, data))
        if (!pumping) { pumping = true; main.post(pump) }
    }

    private val pump = object : Runnable {
        override fun run() {
            if (paused) { main.postDelayed(this, 120); return }
            val it = q.poll(); val g = gatt; val c = wch
            if (it == null || g == null || c == null) { pumping = false; return }
            try {
                if (Build.VERSION.SDK_INT >= 33) g.writeCharacteristic(c, it.data, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
                else { c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE; c.value = it.data; g.writeCharacteristic(c) }
            } catch (_: Exception) {}
            main.postDelayed(this, 40)
        }
    }

    // ---------------- nhận ----------------
    /** Một lần báo có thể chứa nhiều khung dính nhau (danh sách loại xe, chẩn đoán): tách theo byte độ dài. */
    private fun rx(raw: ByteArray) {
        // 1 byte: FF = hộp đã nhận, F0 sai tổng kiểm, F3 không hỗ trợ, FC đang bận. FF có thể dính ngay trước khung dữ liệu.
        if (raw.size == 1) { val b = raw[0].toInt() and 0xFF; main.post { X.onNack(b) }; return }
        val all = if (raw.size > 4 && (raw[0].toInt() and 0xFF) == 0xFF && (raw[1].toInt() and 0xFF) == 0x2E) raw.copyOfRange(1, raw.size) else raw
        var o = 0
        while (o + 4 <= all.size && (all[o].toInt() and 0xFF) == 0x2E) {
            val n = (all[o + 2].toInt() and 0xFF) + 4
            if (o == 0 && n >= all.size) break
            if (o + n > all.size) { rxOne(all.copyOfRange(o, all.size)); return }
            rxOne(all.copyOfRange(o, o + n)); o += n
        }
        if (o == 0) rxOne(all)
    }

    private fun rxOne(d: ByteArray) {
        if (X.snapOn) { val c = d.copyOf(); main.post { X.onSnapFrame(c) } }
        if (d.size >= 5 && (d[0].toInt() and 0xFF) == 0x2E) {
            val t = d[1].toInt() and 0xFF
            if (t == 0x8C) { main.post { X.onCarFrame(d) }; return }
            if (t == 0xAC) { main.post { X.onDiagFrame(d) }; return }
            if (t == 0x9C && d.size >= 7) { val b1 = d[4].toInt() and 0xFF; val b2 = d[5].toInt() and 0xFF; main.post { X.onSup(b1, b2) }; return }
            if (t == 0x7C && (d[3].toInt() and 0xFF) == 0x04) { main.post { X.onDoorRaw(d) }; return }
            val sb = d[3].toInt() and 0xFF
            if (t == 0x7C && (sb == 0x8F || sb == 0x90) && d.size >= 6) {
                val n = minOf((d[2].toInt() and 0xFF) - 1, d.size - 5)
                if (n > 0) { val txt = try { String(d, 4, n, Charsets.UTF_8) } catch (e: Exception) { "" }; main.post { X.onModel(sb, txt) } }
                return
            }
            if (t != 0x8D && (d[3].toInt() and 0xFF) == 0x0E && d.size >= 6 && (d[4].toInt() and 0xFF) == 0xF1) { main.post { X.onReset() }; return }
        }
        if (d.size < 6 || (d[0].toInt() and 0xFF) != 0x2E || (d[1].toInt() and 0xFF) != 0x7C) return
        if (System.currentTimeMillis() - lastSetMs < 500) return
        val len = d[2].toInt() and 0xFF
        val sub = d[3].toInt() and 0xFF
        val v = IntArray(d.size) { d[it].toInt() and 0xFF }
        main.post {
            when {
                sub == 0x05 && len >= 7 && v.size >= 10 -> { S.blk = IntArray(6) { v[4 + it] }; S.blkVer.intValue++ }
                sub == 0x08 && len >= 4 && v.size >= 7 -> S.on08(v[4], v[5], v[6])
                sub == 0x06 && len >= 7 && v.size >= 10 -> S.onLeds(IntArray(minOf(16, len - 1, v.size - 4)) { v[4 + it] })
                sub == 0x0D && len >= 3 && v.size >= 6 -> S.onFactory(v[4], v[5])
                sub == 0x07 && len >= 4 && v.size >= 7 -> { S.p07 = intArrayOf(v[4], v[5], v[6]); X.on07() }
                sub == 0x03 && len >= 2 && v.size >= 5 -> X.onPair(v[4])
                sub == 0x09 && len >= 9 && v.size >= 13 -> X.on09(IntArray(8) { v[4 + it] })
                sub == 0x0F && len >= 3 && v.size >= 6 -> X.onCarCur(v[4], v[5])
                sub == 0x02 && len >= 5 && v.size >= 8 -> S.onEffect(v[4], v[5], v[6], v[7])
                sub == 0x01 && len >= 8 && v.size >= 11 -> S.onColor(v[4], (v[5] shl 16) or (v[6] shl 8) or v[7], (v[8] shl 16) or (v[9] shl 8) or v[10])
                sub == 0x00 && len >= 4 && v.size >= 7 -> S.onBright(v[4], v[5], v[6])
            }
        }
    }
}
