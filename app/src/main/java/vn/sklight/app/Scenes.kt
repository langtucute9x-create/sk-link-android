package vn.sklight.app

import androidx.compose.runtime.mutableStateListOf

/** Một bối cảnh: màu từng khu vực, chế độ, kiểu con, tốc độ, độ sáng. */
class Scene(
    val name: String, val uni: Boolean, val c1: Int, val c2: Int,
    val mode: Int, val sub: Int, val speed: Int, val bri: Int, val z2: Int
) {
    /** Chế độ "Tự động" tự đổi nhiều màu: chấm màu vẽ dải cầu vồng. */
    val rainbow get() = mode == 1
    fun line() = listOf(name.replace("|", " ").replace("\n", " "), if (uni) 1 else 0, c1, c2, mode, sub, speed, bri, z2).joinToString("|")
    fun named(n: String) = Scene(n, uni, c1, c2, mode, sub, speed, bri, z2)
}

/** Danh sách bối cảnh yêu thích. Lần đầu có sẵn 4 bối cảnh; người dùng thêm, đổi tên, cập nhật, xóa tùy ý. */
object Scenes {
    // id chế độ: 0 Đơn sắc, 1 Tự động, 2 Thở (kiểu con 4 = xanh lam), 6 Thể thao
    val presets = listOf(
        Scene("Thể thao", true, 0xFF2A48, 0xFF2A48, 51, 6, 8, 100, 1),        // Chiếu sáng năng động: Cực quang đa sắc, vùng 2 màu tự động
        Scene("Ban đêm", false, 0xFF7A1A, 0xFFFFFF, 51, 3, 5, 20, 0),          // Mặt trời mọc, vùng 2 màu tĩnh trắng
        Scene("Thư giãn", true, 0x17F4EA, 0x17F4EA, 7, 0, 4, 50, 1),           // Ma thuật, vùng 2 màu tự động
        Scene("Đón khách", true, 0xFFFFFF, 0xFFFFFF, 12, 0, 5, 100, 2),        // Cuộn đủ màu chế độ 1, vùng 2 màu nhấp nháy
        Scene("Mơ màng", true, 0xFE2CAA, 0xFE2CAA, 51, 2, 5, 60, 1)            // Vườn sen, vùng 2 màu tự động
    )
    /** 4 bối cảnh có sẵn của các bản trước: nếu người dùng chưa sửa thì được đổi sang bản mới. */
    private val old = listOf(
        Scene("Thư giãn", true, 0x17F4EA, 0x17F4EA, 2, 4, 4, 50, 0),
        Scene("Thể thao", true, 0xFF2A48, 0xFF2A48, 6, 0, 8, 100, 0),
        Scene("Ban đêm", true, 0xFF7A1A, 0xFF7A1A, 0, 0, 5, 20, 0),
        Scene("Đón khách", true, 0xFFFFFF, 0xFFFFFF, 1, 0, 5, 100, 0)
    )
    val list = mutableStateListOf<Scene>()
    const val MAX = 12

    fun all(): List<Scene> = list

    /** [text] = null: chưa từng lưu, dùng 4 bối cảnh có sẵn. */
    fun load(text: String?) {
        list.clear()
        if (text == null) { list.addAll(presets); S.markScenesV2(); return }
        for (l in text.split("\n")) {
            val p = l.split("|")
            if (p.size < 9) continue
            val n = p.drop(1).map { it.toIntOrNull() ?: 0 }
            list.add(Scene(p[0], n[0] == 1, n[1], n[2], n[3], n[4], n[5], n[6], n[7]))
        }
        // chuyển 4 bối cảnh gốc cũ (chưa bị sửa) sang bản mới, thêm Mơ màng một lần
        for (i in list.indices) {
            val o = old.firstOrNull { it.line() == list[i].line() } ?: continue
            presets.firstOrNull { it.name == o.name }?.let { list[i] = it }
        }
        if (!S.scenesV2()) { if (list.none { it.name == "Mơ màng" } && list.size < MAX) list.add(presets.last()); S.markScenesV2() }
    }

    fun dump() = list.joinToString("\n") { it.line() }

    fun apply(i: Int) {
        val sc = list.getOrNull(i) ?: return
        S.applyScene(sc)
        S.sceneSel.intValue = i
    }

    /** Lưu cài đặt đang dùng thành bối cảnh mới. Trả về false nếu đã đủ số lượng. */
    fun saveCurrent(name: String): Boolean {
        if (list.size >= MAX) return false
        list.add(S.currentScene(name.trim().ifEmpty { "Yêu thích ${list.size + 1}" }))
        S.sceneSel.intValue = list.size - 1
        S.saveSettings()
        return true
    }

    fun rename(i: Int, name: String) {
        val o = list.getOrNull(i) ?: return
        list[i] = o.named(name.trim().ifEmpty { o.name })
        S.saveSettings()
    }

    /** Ghi đè bối cảnh bằng cài đặt đang dùng, giữ tên cũ. */
    fun update(i: Int) {
        val o = list.getOrNull(i) ?: return
        list[i] = S.currentScene(o.name)
        S.sceneSel.intValue = i
        S.saveSettings()
    }

    fun delete(i: Int) {
        if (i !in list.indices) return
        list.removeAt(i)
        S.sceneSel.intValue = -1
        S.saveSettings()
    }

    /** Thêm lại những bối cảnh có sẵn đã bị xóa. */
    fun restore() {
        for (p in presets) if (list.none { it.name == p.name } && list.size < MAX) list.add(p)
        S.sceneSel.intValue = -1
        S.saveSettings()
    }
}
