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
        S.blk = null; S.blkVer.intValue++; S.fac = null; S.p07 = null; S.m1 = 0          // cài đặt đọc từ hộp trước không dùng cho lần kết nối mới (có thể là hộp khác)
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

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) { ready(g) }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            val v = c.value
            if (v != null) rx(v)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) { rx(value) }
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
            android.widget.Toast.makeText(ctx, L.tr("Đã kết nối với " + S.devName.value), android.widget.Toast.LENGTH_SHORT).show()
            S.isCzh.value = (g.device.name ?: "").contains("CZH2")
            // Gói mở app + độ sáng trước; các lệnh hỏi gửi sau 700 ms để câu trả lời không rơi vào khoảng "vừa gửi lệnh" bị bỏ qua.
            main.postDelayed({
                val init = Protocol.init()
                send(init[0])
                S.sendBright()
                main.postDelayed({ for (f in init.drop(1)) send(f) }, 700)
            }, 800)
        }
    }

    // ---------------- gửi ----------------
    /** key >= 0: lệnh cùng key chưa gửi sẽ bị thay bằng lệnh mới (kéo thanh trượt không dồn hàng đợi). */
    fun send(data: ByteArray, key: Int = -1, isSet: Boolean = false) {
        if (isSet) lastSetMs = System.currentTimeMillis()
        if (key >= 0) q.removeIf { it.key == key }
        q.add(Item(key, data))
        if (!pumping) { pumping = true; main.post(pump) }
    }

    private val pump = object : Runnable {
        override fun run() {
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
        if (d.size >= 5 && (d[0].toInt() and 0xFF) == 0x2E) {
            val t = d[1].toInt() and 0xFF
            if (t == 0x8C) { main.post { X.onCarFrame(d) }; return }
            if (t == 0xAC) { main.post { X.onDiagFrame(d) }; return }
            if (t == 0x9C && d.size >= 7) { val b1 = d[4].toInt() and 0xFF; val b2 = d[5].toInt() and 0xFF; main.post { X.onSup(b1, b2) }; return }
            if (t == 0x7C && (d[3].toInt() and 0xFF) == 0x04) { main.post { X.onDoorRaw(d) }; return }
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
                sub == 0x02 && len >= 5 && v.size >= 8 -> S.m1 = v[4] and 0x0F
                sub == 0x00 && len >= 4 && v.size >= 7 -> S.onBright(v[4], v[5], v[6])
            }
        }
    }
}
