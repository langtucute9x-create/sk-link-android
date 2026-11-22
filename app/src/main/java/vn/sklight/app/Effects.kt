package vn.sklight.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/** [base] = số gửi cho kiểu con đầu tiên (0 hoặc 1, theo app gốc của hộp). */
class Effect(val id: Int, val name: String, val icon: ImageVector, val color: Color, val subs: List<String> = emptyList(), val base: Int = 0)
class EffectGroup(val name: String, val icon: ImageVector, val items: List<Effect>)

object Effects {
    private val cPink = Color(0xFFFE2CAA); private val cRed = Color(0xFFFE2B48); private val cCyan = Color(0xFF1AF4EA); private val cYel = Color(0xFFFED119)
    private val cOrange = Color(0xFFFF791B); private val cBlue = Color(0xFF4E78FF); private val cPurple = Color(0xFF8E5CFF)
    private val cNhip = Color(0xFFFE8A01); private val cComet = Color(0xFFFF456B); private val cGreen = Color(0xFF40E01E)
    private val c7 = listOf("đa sắc", "đỏ", "vàng", "xanh lá", "xanh lam", "xanh dương", "tím")
    private val c8 = c7 + "trắng"
    private fun pre(p: String, l: List<String>) = l.map { "$p $it" }
    private fun num(p: String, n: Int) = (1..n).map { "$p $it" }
    private val nhip = Icons.Filled.GraphicEq
    private val wave = Icons.Filled.Waves

    val groups = listOf(
        EffectGroup("CHẾ ĐỘ CƠ BẢN", Icons.Outlined.LightMode, listOf(
            Effect(0, "Đơn sắc", Icons.Outlined.Lightbulb, cPink), Effect(1, "Tự động", Icons.Filled.Autorenew, cRed),
            Effect(2, "Thở", Icons.Filled.ShowChart, cCyan, pre("Chuyển màu", c7)), Effect(3, "Chớp", Icons.Filled.Bolt, cYel, pre("Nhấp nháy", c8)),
            Effect(100, "Cầu vồng mượt", Icons.Filled.Gradient, cPurple), Effect(101, "Thở theo màu chọn", Icons.Filled.ShowChart, cPink),
            Effect(102, "Hai khu so le", Icons.Filled.SwapHoriz, cCyan), Effect(103, "Lửa", Icons.Filled.LocalFireDepartment, cOrange),
            Effect(104, "Cảnh sát", Icons.Filled.FlashOn, cBlue), Effect(105, "Sét", Icons.Filled.Bolt, cYel),
            Effect(106, "Đại dương", Icons.Filled.Waves, cBlue), Effect(107, "Hoàng hôn", Icons.Filled.WbTwilight, cRed))),
        EffectGroup("CHẾ ĐỘ HIỆU ỨNG", Icons.Filled.AutoAwesome, listOf(
            Effect(7, "Ma thuật", Icons.Filled.AutoAwesome, cRed), Effect(8, "Huyền ảo", Icons.Filled.BlurCircular, cPink),
            Effect(9, "Ma thuật", Icons.Filled.AutoAwesome, cCyan), Effect(11, "Cuộn màn hình", Icons.Outlined.ViewAgenda, cOrange),
            Effect(14, "Nhảy đa sắc", Icons.Filled.BlurCircular, cBlue),
            Effect(51, "Chiếu sáng năng động", Icons.Filled.Flare, cOrange,
                listOf("Đêm trăng đỏ", "Hoàng hôn sa mạc", "Vườn sen", "Mặt trời mọc", "Đường tre", "Bình minh xuân", "Cực quang đa sắc"), 1),
            Effect(53, "Xếp chồng", Icons.Outlined.Layers, cGreen,
                listOf("Xếp chồng đỏ-trắng", "Xếp chồng xanh-đỏ", "Xếp chồng đỏ-tím", "Xếp chồng trắng-trắng"), 1))),
        EffectGroup("CHỦ ĐỀ MÙA", Icons.Filled.LocalFlorist, listOf(
            Effect(15, "Xuân", Icons.Filled.LocalFlorist, cPink), Effect(16, "Hạ", wave, cPurple),
            Effect(17, "Thu", Icons.Filled.FilterVintage, cNhip), Effect(18, "Đông", Icons.Filled.AcUnit, cRed))),
        EffectGroup("NHỊP NHẠC", nhip, listOf(
            Effect(19, "Nhịp nhạc", nhip, cNhip), Effect(20, "Nhịp phổ", nhip, cNhip), Effect(21, "Nhịp cầu vồng", nhip, cNhip),
            Effect(50, "Năng động", nhip, cNhip), Effect(4, "Nhịp vui", nhip, cNhip, pre("Nhịp", c8)), Effect(5, "Nhịp nhẹ", nhip, cNhip, pre("Nhịp", c8)))),
        EffectGroup("CHẾ ĐỘ CHẠY", wave, listOf(
            Effect(52, "Mây trôi", wave, cBlue), Effect(6, "Thể thao", Icons.Filled.DirectionsRun, cBlue, num("Thể thao", 3), 1),
            Effect(10, "Trôi đa sắc", wave, cBlue, num("Chế độ", 6), 1), Effect(12, "Cuộn đủ màu", wave, cBlue, num("Chế độ", 6), 1),
            Effect(13, "Đuôi sao chổi", wave, cComet, listOf("Đỏ", "Vàng", "Xanh lá", "Xanh lam", "Xanh dương", "Tím", "Trắng"), 1)))
    )
    val all: List<Effect> = groups.flatMap { it.items }
    fun byId(id: Int) = all.firstOrNull { it.id == id }
}
