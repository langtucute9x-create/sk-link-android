package vn.sklight.app

import androidx.compose.runtime.mutableStateListOf

/** Một bối cảnh: màu từng khu vực, chế độ, kiểu con, tốc độ, độ sáng. */
class Scene(
    val name: String, val uni: Boolean, val c1: Int, val c2: Int,
    val mode: Int, val sub: Int, val speed: Int, val bri: Int, val z2: Int, val custom: Boolean
) {
    /** Chế độ "Tự động" tự đổi nhiều màu: chấm màu vẽ dải cầu vồng. */
    val rainbow get() = mode == 1
    fun line() = listOf(name.replace("|", " ").replace("\n", " "), if (uni) 1 else 0, c1, c2, mode, sub, speed, bri, z2).joinToString("|")
}

object Scenes {
    // id chế độ: 0 Đơn sắc, 1 Tự động, 2 Thở (kiểu con 4 = xanh lam), 6 Thể thao
    val presets = listOf(
        Scene("Thư giãn", true, 0x17F4EA, 0x17F4EA, 2, 4, 4, 50, 0, false),
        Scene("Thể thao", true, 0xFF2A48, 0xFF2A48, 6, 0, 8, 100, 0, false),
        Scene("Ban đêm", true, 0xFF7A1A, 0xFF7A1A, 0, 0, 5, 20, 0, false),
        Scene("Đón khách", true, 0xFFFFFF, 0xFFFFFF, 1, 0, 5, 100, 0, false)
    )
    val customs = mutableStateListOf<Scene>()
    const val MAX_CUSTOM = 8

    fun all(): List<Scene> = presets + customs

    fun load(text: String) {
        customs.clear()
        for (l in text.split("\n")) {
            val p = l.split("|")
            if (p.size < 9) continue
            val n = p.drop(1).map { it.toIntOrNull() ?: 0 }
            customs.add(Scene(p[0], n[0] == 1, n[1], n[2], n[3], n[4], n[5], n[6], n[7], true))
        }
    }

    fun dump() = customs.joinToString("\n") { it.line() }

    fun apply(i: Int) {
        val sc = all().getOrNull(i) ?: return
        S.applyScene(sc)
        S.sceneSel.intValue = i
    }

    /** Lưu cài đặt đang dùng thành bối cảnh mới. Trả về false nếu đã đủ số lượng. */
    fun saveCurrent(name: String): Boolean {
        if (customs.size >= MAX_CUSTOM) return false
        customs.add(S.currentScene(name.trim().ifEmpty { "Của tôi ${customs.size + 1}" }))
        S.sceneSel.intValue = presets.size + customs.size - 1
        S.saveSettings()
        return true
    }

    fun rename(ci: Int, name: String) {
        val o = customs.getOrNull(ci) ?: return
        val n = name.trim().ifEmpty { o.name }
        customs[ci] = Scene(n, o.uni, o.c1, o.c2, o.mode, o.sub, o.speed, o.bri, o.z2, true)
        S.saveSettings()
    }

    /** Ghi đè bối cảnh tự tạo bằng cài đặt đang dùng, giữ tên cũ. */
    fun update(ci: Int) {
        val o = customs.getOrNull(ci) ?: return
        customs[ci] = S.currentScene(o.name)
        S.sceneSel.intValue = presets.size + ci
        S.saveSettings()
    }

    fun delete(ci: Int) {
        if (ci !in customs.indices) return
        customs.removeAt(ci)
        S.sceneSel.intValue = -1
        S.saveSettings()
    }
}
