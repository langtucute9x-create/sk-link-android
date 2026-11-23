package vn.sklight.app

import android.content.Context
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Ngôn ngữ hiển thị. Chữ trong mã nguồn viết bằng tiếng Việt; bảng dịch (res/raw/lang.tsv) tra theo câu tiếng Việt.
 * Mỗi dòng: tiếng Việt | English | 日本語 | 한국어 | ไทย | ខ្មែរ | ລາວ. Ô trống thì dùng tiếng Anh.
 * Trong khóa: "#" = một con số, "%s" = một đoạn chữ thay đổi (được dịch tiếp nếu có trong bảng).
 */
object L {
    val cur = mutableIntStateOf(0)                       // 0 = tiếng Việt
    val NAMES = listOf("Tiếng Việt", "English", "日本語", "한국어", "ไทย", "ខ្មែរ", "ລາວ")
    private var maps: List<HashMap<String, String>> = emptyList()
    private var tpl: List<Pair<Regex, String>> = emptyList()
    private val cache = HashMap<String, String>()
    private val NUM = Regex("\\d+")

    fun init(ctx: Context) {
        if (maps.isNotEmpty()) return
        try {
            val m = List(NAMES.size - 1) { HashMap<String, String>() }
            val keys = ArrayList<String>()
            ctx.resources.openRawResource(R.raw.lang).bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    val c = line.split("\t")
                    if (c.size < 2 || c[0].isEmpty()) continue
                    for (i in m.indices) { val t = c.getOrNull(i + 1) ?: ""; if (t.isNotEmpty()) m[i][c[0]] = t }
                    if (c[0].contains("%s")) keys.add(c[0])
                }
            }
            // khóa dài xét trước để "Bối cảnh: %s" không bị "Bối cảnh %s" bắt nhầm
            tpl = keys.sortedByDescending { it.length }.map { k -> Regex(k.split("%s").joinToString("(.+?)") { Regex.escape(it) }) to k }
            maps = m
        } catch (e: Exception) { maps = emptyList() }
    }

    fun set(i: Int) { cache.clear(); cur.intValue = i.coerceIn(0, NAMES.size - 1) }

    /** Dịch [text] sang ngôn ngữ đang chọn; không có trong bảng thì trả lại nguyên văn. */
    fun tr(text: String): String {
        val lang = cur.intValue                           // đọc ở đây để giao diện vẽ lại khi đổi ngôn ngữ
        if (lang == 0 || text.isEmpty() || maps.isEmpty()) return text
        cache[text]?.let { return it }
        val r = look(text, lang, 0)
        if (cache.size > 3000) cache.clear()
        cache[text] = r
        return r
    }

    private fun get(key: String, lang: Int): String? = maps[lang - 1][key] ?: maps[0][key]

    private fun look(text: String, lang: Int, depth: Int): String {
        get(text, lang)?.let { return it }
        if (NUM.containsMatchIn(text)) {
            val t = get(NUM.replace(text, "#"), lang)
            if (t != null) {
                val nums = NUM.findAll(text).map { it.value }.toList()
                val parts = t.split("#")
                val sb = StringBuilder(parts[0])
                for (i in 1 until parts.size) sb.append(nums.getOrElse(i - 1) { "" }).append(parts[i])
                return sb.toString()
            }
        }
        if (depth < 2) for ((re, key) in tpl) {
            val mm = re.matchEntire(text) ?: continue
            val t = get(key, lang) ?: continue
            val parts = t.split("%s")
            val sb = StringBuilder(parts[0])
            for (i in 1 until parts.size) sb.append(look(mm.groupValues.getOrElse(i) { "" }, lang, depth + 1)).append(parts[i])
            return sb.toString()
        }
        return text
    }
}

/** Nút quay lại trang Cài đặt, dùng ở đầu các trang con. */
@Composable
fun BackToSettings() {
    Row(
        Modifier.height(44.dp).block(false, RoundedCornerShape(Theme.r(8)), CardBg).clickable { S.subPage.intValue = 0 }.padding(start = 10.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.ArrowBack, null, tint = Txt, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        T("Cài đặt", 13, FontWeight.Bold)
    }
}

// ===================== CÀI ĐẶT NGÔN NGỮ =====================
@Composable
fun LanguagePage() {
    BackToSettings()
    Card {
        GroupTitle(Icons.Filled.Language, "Ngôn ngữ", 16)
        Spacer(Modifier.height(6.dp))
        T("Chọn ngôn ngữ hiển thị của app.", 12, color = Grey)
        Spacer(Modifier.height(6.dp))
        L.NAMES.forEachIndexed { i, name ->
            val on = L.cur.intValue == i
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { L.set(i); S.saveSettings() }, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(18.dp).border(2.dp, if (on) Pink else Grey, CircleShape), contentAlignment = Alignment.Center) {
                    if (on) Box(Modifier.size(8.dp).clip(CircleShape).background(Pink))
                }
                Spacer(Modifier.width(12.dp))
                T(name, 15, if (on) FontWeight.Bold else FontWeight.Medium)
            }
        }
        if (L.cur.intValue != 0) {
            Spacer(Modifier.height(6.dp))
            T("Ra lệnh bằng giọng nói hiện chỉ có khi dùng tiếng Việt.", 11, color = Grey)
        }
    }
}
