package vn.sklight.app

/** Khung lệnh BLE của hộp đèn: 2E | loại | độ dài | dữ liệu | ck, ck = ~(loại + dài + dữ liệu). */
object Protocol {
    const val T_WRITE = 0x8D
    const val T_READ = 0x90

    fun frame(type: Int, vararg payload: Int): ByteArray {
        val out = ByteArray(4 + payload.size)
        out[0] = 0x2E
        out[1] = type.toByte()
        out[2] = payload.size.toByte()
        var sum = type + payload.size
        for (i in payload.indices) { out[3 + i] = payload[i].toByte(); sum += payload[i] and 0xFF }
        out[3 + payload.size] = (sum.inv() and 0xFF).toByte()
        return out
    }

    fun query(sub: Int) = frame(T_READ, 0x7C, sub)

    fun bright(on: Boolean, unified: Boolean, b1: Int, b2: Int): ByteArray {
        val flag = (if (on) 0x40 else 0) or (if (unified) 0x08 else 0)
        return frame(T_WRITE, 0x00, flag, b1, b2)
    }

    /** Màu hai khu. Hộp hiện màu bù của màu gửi đi nên đảo 255-x khi [invert]. */
    fun color(unified: Boolean, c1: Int, c2: Int, invert: Boolean): ByteArray {
        val a = led(c1, invert); val b = led(c2, invert)
        val flag = if (unified) 0x48 else 0x40
        return frame(T_WRITE, 0x01, flag, a[0], a[1], a[2], b[0], b[1], b[2])
    }

    private fun led(rgb: Int, invert: Boolean): IntArray {
        var r = (rgb shr 16) and 0xFF; var g = (rgb shr 8) and 0xFF; var b = rgb and 0xFF
        if (invert) { r = 255 - r; g = 255 - g; b = 255 - b }
        if (r == 0 && g == 0 && b == 0) { r = 1; g = 1; b = 1 }   // 00 00 00 = tắt
        return intArrayOf(r, g, b)
    }

    /** Hiệu ứng: 02 | (độ nhạy << 4) | mode1 | id hiệu ứng | kiểu con | tốc độ. mode1 = chế độ màu vùng 2 (chưa kiểm chứng). */
    fun effect(sens: Int, mode1: Int, id: Int, param: Int, speed: Int) =
        frame(T_WRITE, 0x02, ((sens and 0x0F) shl 4) or (mode1 and 0x0F), id, param, speed)

    /** Khối 05: hàm gốc + chào mừng, luôn gửi đủ 6 byte. */
    fun block05(b: IntArray) = frame(T_WRITE, 0x05, b[0], b[1], b[2], b[3], b[4], b[5])

    /** Khối 08: hướng chào mừng (bit theo đoạn) | (tốc độ << 3) | vị trí bắt đầu | hướng hộp 7..14. */
    fun block08(data1: Int, speed: Int, start: Int, data3: Int) =
        frame(T_WRITE, 0x08, data1, ((speed and 3) shl 3) or (start and 7), data3)

    /** Số LED: 06 | táp-lô giữa | trước trái | trước phải | sau trái | sau phải | đồng hồ | hộp 5..14. */
    fun leds(raw: IntArray) = frame(T_WRITE, 0x06, *raw)

    /** Cấu hình cửa: 04 | (hộp phụ << 4) | mã vị trí. Mã 0 = chỉ cho hộp đó nháy. */
    fun door(box: Int, code: Int) = frame(T_WRITE, 0x04, ((box and 0x0F) shl 4) or (code and 0x0F))
    fun doorReset() = frame(T_WRITE, 0x04, 0xFF)

    fun init() = listOf(
        frame(0x81, 0x01),
        query(0x90), query(0x8F), query(0x00), query(0x02), query(0x03),
        query(0x05), query(0x06), query(0x08), query(0x09), query(0x0A),
        frame(T_READ, 0x9C, 0x01)
    )
}
