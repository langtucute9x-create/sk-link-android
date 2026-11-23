package vn.sklight.app

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Một chế độ do người dùng tự đặt: 2 đến 5 màu, chuyển mượt hoặc nhảy, hai khu cùng màu hoặc so le. App tự chạy bằng lệnh màu (xem Auto). */
class Seq(val name: String, val colors: List<Int>, val smooth: Boolean, val split: Boolean) {
    fun line() = listOf(name.replace("|", " ").replace("\n", " "), if (smooth) 1 else 0, if (split) 1 else 0, colors.joinToString(",")).joinToString("|")
}

object Seqs {
    val list = mutableStateListOf<Seq>()
    const val MAX = 12

    fun load(text: String) {
        list.clear()
        for (l in text.split("\n")) {
            val p = l.split("|")
            if (p.size < 4) continue
            val cs = p[3].split(",").mapNotNull { it.toIntOrNull() }.map { it and 0xFFFFFF }
            if (cs.size >= 2) list.add(Seq(p[0], cs.take(5), p[1] == "1", p[2] == "1"))
        }
    }
    fun dump() = list.joinToString("\n") { it.line() }

    /** Lưu (i < 0 = thêm mới) rồi chạy luôn chế độ đó. */
    fun save(i: Int, q: Seq) {
        val at = if (i in list.indices) { list[i] = q; i } else { list.add(q); list.size - 1 }
        S.saveSettings()
        S.pickAuto(Auto.SEQ_BASE + at)
    }

    fun delete(i: Int) {
        if (i !in list.indices) return
        // số thứ tự các chế độ sau bị dồn lại, nên đang chạy chế độ tự đặt nào thì dừng cho chắc
        if (S.mode.intValue >= Auto.SEQ_BASE) { S.mode.intValue = 0; Auto.leave(); try { if (S.connected.value) S.ble.send(S.colorFrame(), 2, true) } catch (e: Exception) {} }
        list.removeAt(i)
        S.saveSettings()
    }
}

// màu để ghép chuỗi; ô cuối (đen) = tắt, dùng làm nhịp nghỉ
private val PICK = listOf(0xFF0000, 0xFF6A00, 0xFFD400, 0xA8FF00, 0x00E040, 0x00FFC8, 0x00C8FF, 0x0050FF, 0x8A2BFF, 0xFF2CAA, 0xFF8AC8, 0xFFFFFF, 0x000000)

/** Thẻ "Chế độ của tôi" ở trang Đèn: chạy, tạo, sửa, xóa chuỗi màu tự đặt. */
@Composable
fun MyModesCard(cols: Int) {
    val ctx = LocalContext.current
    var edit by remember { mutableIntStateOf(-2) }                 // -2 không sửa, -1 tạo mới, >= 0 sửa chế độ thứ i
    var name by remember { mutableStateOf("") }
    val cs = remember { mutableStateListOf<Int>() }
    var sel by remember { mutableIntStateOf(0) }
    var smooth by remember { mutableStateOf(true) }
    var split by remember { mutableStateOf(false) }
    fun open(i: Int) {
        val q = Seqs.list.getOrNull(i)
        name = q?.name ?: ""
        cs.clear(); cs.addAll(q?.colors ?: listOf(0xFF0000, 0x0050FF))
        smooth = q?.smooth ?: true; split = q?.split ?: false
        sel = 0; edit = i
    }
    Card {
        GroupTitle(Icons.Filled.ColorLens, "CHẾ ĐỘ CỦA TÔI")
        Spacer(Modifier.height(6.dp))
        T("Tự ghép 2 đến 5 màu thành một chế độ riêng. Chạm để chạy, chạm bút để sửa.", 12, color = Grey)
        Spacer(Modifier.height(12.dp))
        if (Seqs.list.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                EmptyOrbs()
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    T("Chưa có chế độ riêng nào", 14, FontWeight.Bold)
                    T("Bấm ô có dấu cộng, chọn vài màu ưa thích là có ngay một hiệu ứng mang tên riêng.", 12, color = Grey)
                }
            }
        }
        Grid((Seqs.list.indices.toList() + listOf(-1)), cols, 9.dp, 11.dp) { i, m ->
            if (i < 0) {
                Row(
                    m.height(56.dp).clip(RoundedCornerShape(Theme.r(8))).background(CardBg).border(1.dp, Pink, RoundedCornerShape(Theme.r(8)))
                        .clickable { if (Seqs.list.size >= Seqs.MAX) Notice.show(ctx, L.tr("Đã đủ ${Seqs.MAX} chế độ, xóa bớt để thêm.")) else open(-1) },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Filled.Add, null, tint = Pink, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    T("Tạo mới", 12, FontWeight.Bold, AccentText)
                }
            } else {
                val q = Seqs.list[i]
                val on = S.mode.intValue == Auto.SEQ_BASE + i
                Row(
                    m.height(56.dp).clip(RoundedCornerShape(Theme.r(8))).background(if (on) tint(0.14f) else ChipBg)
                        .border(if (on) 2.dp else 1.dp, if (on) Pink else Bd, RoundedCornerShape(Theme.r(8))).clickable { S.pickAuto(Auto.SEQ_BASE + i) }.padding(start = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(18.dp).clip(CircleShape).background(Brush.horizontalGradient(q.colors.map { rgbColor(it) })).border(1.dp, Bd, CircleShape))
                    Spacer(Modifier.width(7.dp))
                    T(q.name, 12, if (on) FontWeight.ExtraBold else FontWeight.SemiBold, if (on) Txt else SubTxt, Modifier.weight(1f))
                    Box(Modifier.size(40.dp).clickable { open(i) }, contentAlignment = Alignment.Center) { Icon(Icons.Filled.Edit, "Sửa ${q.name}", tint = Grey, modifier = Modifier.size(16.dp)) }
                }
            }
        }
        if (edit >= -1) {
            Divider(14.dp, 12.dp)
            T(if (edit < 0) "Tạo chế độ mới" else "Sửa chế độ", 15, FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(name, { name = it.take(16) }, singleLine = true, modifier = Modifier.fillMaxWidth(), placeholder = { T("Tên chế độ, ví dụ: Hoàng hôn tím", 14, color = Grey) })
            Spacer(Modifier.height(12.dp))
            T("Các màu theo thứ tự (chạm một ô để đổi màu ô đó)", 13, FontWeight.Bold, SubTxt)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                cs.forEachIndexed { k, c ->
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(Theme.r(8))).background(rgbColor(c)).border(if (sel == k) 3.dp else 1.dp, if (sel == k) Txt else Bd, RoundedCornerShape(Theme.r(8))).clickable { sel = k },
                        contentAlignment = Alignment.Center
                    ) { T("${k + 1}", 12, FontWeight.Bold, if (c == 0xFFFFFF || c == 0xFFD400 || c == 0xA8FF00) Color(0xFF14161C) else Color.White) }
                }
                if (cs.size < 5) Box(Modifier.size(44.dp).clip(RoundedCornerShape(Theme.r(8))).border(1.dp, Bd, RoundedCornerShape(Theme.r(8))).clickable { cs.add(0xFFFFFF); sel = cs.size - 1 }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Add, "Thêm màu", tint = Txt, modifier = Modifier.size(20.dp))
                }
                if (cs.size > 2) Box(Modifier.size(44.dp).clickable { cs.removeAt(sel.coerceIn(0, cs.size - 1)); sel = sel.coerceIn(0, cs.size - 1) }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Close, "Bỏ màu đang chọn", tint = Grey, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            T("Màu cho ô ${sel + 1}", 13, FontWeight.Bold, SubTxt)
            Spacer(Modifier.height(8.dp))
            Grid(PICK, 7, 7.dp) { c, m ->
                val on = cs.getOrNull(sel) == c
                Box(
                    m.height(36.dp).clip(RoundedCornerShape(Theme.r(6))).background(rgbColor(c)).border(if (on) 3.dp else 1.dp, if (on) Txt else Bd, RoundedCornerShape(Theme.r(6)))
                        .clickable { if (sel in cs.indices) cs[sel] = c },
                    contentAlignment = Alignment.Center
                ) { if (c == 0) T("Tắt", 10, FontWeight.Bold, Color.White) }
            }
            Spacer(Modifier.height(8.dp))
            OutlineBtn("Lấy màu đang chọn ở bánh xe màu", Modifier.fillMaxWidth()) { if (sel in cs.indices) cs[sel] = S.col[S.czone.intValue] and 0xFFFFFF }
            Spacer(Modifier.height(12.dp))
            T("Kiểu chuyển màu", 13, FontWeight.Bold, SubTxt)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Chip("Mượt", smooth, Modifier.weight(1f), 44.dp, 13) { smooth = true }
                Chip("Nhảy", !smooth, Modifier.weight(1f), 44.dp, 13) { smooth = false }
            }
            Spacer(Modifier.height(10.dp))
            T("Hai khu vực", 13, FontWeight.Bold, SubTxt)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Chip("Cùng màu", !split, Modifier.weight(1f), 44.dp, 13) { split = false }
                Chip("So le", split, Modifier.weight(1f), 44.dp, 13) { split = true }
            }
            Spacer(Modifier.height(6.dp))
            T("Nhanh hay chậm chỉnh bằng thanh Tốc độ nháy & chạy ở thẻ Hiệu ứng.", 11, color = Grey)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                FillBtn("Lưu và chạy", Icons.Filled.Check, Pink, Modifier.weight(1.4f)) {
                    val i = edit
                    Seqs.save(i, Seq(name.trim().ifEmpty { "Chế độ ${if (i < 0) Seqs.list.size + 1 else i + 1}" }, cs.toList(), smooth, split))
                    edit = -2
                }
                if (edit >= 0) OutlineBtn("Xóa", Modifier.weight(1f)) { Seqs.delete(edit); edit = -2 }
                OutlineBtn("Hủy", Modifier.weight(1f)) { edit = -2 }
            }
        }
    }
}
