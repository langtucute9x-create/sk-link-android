package vn.sklight.app

/** Khung lệnh BLE của hộp đèn (giải mã từ app gốc): 2E | loại | độ dài | dữ liệu | ck, ck = ~(loại+dài+dữ liệu). */
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

    /** Bật + độ sáng. zone 0 = đồng nhất, 1/2 = hai khu riêng. */
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

    fun effect(id: Int, sub: Int, speed: Int) = frame(T_WRITE, 0x02, 0x60, id, sub, speed)

    /** Khối 05: Hàm gốc + chào mừng + đèn theo sau, luôn gửi đủ 6 byte. */
    fun block05(b: IntArray) = frame(T_WRITE, 0x05, b[0], b[1], b[2], b[3], b[4], b[5])

    /** Khối 08: vị trí + tốc độ chào mừng (xx = (tốc độ-1)<<3 | vị trí). */
    fun block08(keep1: Int, xx: Int, keep2: Int) = frame(T_WRITE, 0x08, keep1, xx, keep2)

    fun init() = listOf(
        frame(0x81, 0x01, 0x01), // mở app (2E 81 01 01 7C)
        query(0x90), query(0x8F), query(0x00), query(0x02), query(0x03),
        query(0x05), query(0x06), query(0x08), query(0x09), query(0x0A),
        frame(T_READ, 0x9C, 0x01)
    )
}

class Effect(val id: Int, val name: String, val subs: Int)
class EffectGroup(val name: String, val items: List<Effect>)

object Effects {
    val groups = listOf(
        EffectGroup("CƠ BẢN", listOf(Effect(0, "Đơn sắc", 0), Effect(1, "Tự động", 0), Effect(2, "Thở", 7), Effect(3, "Chớp", 8))),
        EffectGroup("MÙA VÀ MÀU", listOf(Effect(7, "Ma thuật", 0), Effect(8, "Huyền ảo", 0), Effect(9, "Ma sắc", 0), Effect(11, "Mở màn", 0),
            Effect(14, "Nhảy 7 màu", 0), Effect(15, "Xuân", 0), Effect(16, "Hạ", 0), Effect(17, "Thu", 0), Effect(18, "Đông", 0))),
        EffectGroup("NHẠC", listOf(Effect(19, "Nhạc", 0), Effect(20, "Phổ nhạc", 0), Effect(21, "Cầu vồng", 0), Effect(50, "Sôi động", 0),
            Effect(4, "Nhạc vui", 8), Effect(5, "Nhạc êm", 8))),
        EffectGroup("CHUYỂN ĐỘNG", listOf(Effect(6, "Thể thao", 3), Effect(10, "Trôi đa sắc", 6), Effect(12, "Cuộn màu", 6), Effect(13, "Sao chổi", 7),
            Effect(52, "Mây trôi", 0), Effect(51, "Năng động", 7), Effect(53, "Xếp chồng", 4)))
    )
    val all: List<Effect> = groups.flatMap { it.items }
    fun byId(id: Int) = all.firstOrNull { it.id == id }
}
