package vn.sklight.app

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Process
import android.provider.Settings
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// ===================== CHIA ĐÔI MÀN HÌNH VỚI APP ĐANG MỞ =====================
// Android không cho nhúng giao diện app khác vào SK Light, nên dùng chế độ chia đôi màn hình của máy.
// Cách chính: nhờ dịch vụ Hỗ trợ tiếp cận của SK Light (ScreenReader) bật chia đôi; máy tự đưa SK Light sang một nửa
// và hiện danh sách các app ĐANG MỞ ở nửa kia để người dùng chọn (chủ dự án yêu cầu chọn app đang mở, không cố định một app).
// Dự phòng khi chưa bật Hỗ trợ tiếp cận / máy không cho: hộp chọn trong các app dùng gần đây (cần quyền xem mức sử dụng),
// mở app đó với cờ LAUNCH_ADJACENT.
object Split {
    class App(val pkg: String, val name: String)

    val inSplit = mutableStateOf(false)         // SK Light đang ở chế độ chia đôi (MainActivity cập nhật)
    val picking = mutableStateOf(false)

    private fun act(c: Context): Activity? { var x: Context? = c; while (x is ContextWrapper) { if (x is Activity) return x; x = x.baseContext }; return null }

    fun go(c: Context) {
        val sr = ScreenReader.inst
        if (sr != null && sr.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)) {
            if (!inSplit.value) Notice.show(c, L.tr("Chọn app đang mở ở nửa màn hình còn lại."))
            return
        }
        picking.value = true
    }

    fun usageAllowed(c: Context): Boolean = try {
        val ops = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName) == AppOpsManager.MODE_ALLOWED
    } catch (_: Exception) { false }

    /** App có biểu tượng trên màn hình chính. recent = true: chỉ app dùng trong 12 giờ qua, mới nhất trước. */
    fun apps(c: Context, recent: Boolean): List<App> {
        val pm = c.packageManager
        val home = try { pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName } catch (_: Exception) { null }
        val launch = try { pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0) } catch (_: Exception) { emptyList() }
        val names = HashMap<String, String>()
        for (r in launch) {
            val p = r.activityInfo?.packageName ?: continue
            if (p != c.packageName && p != home) names[p] = try { r.loadLabel(pm).toString() } catch (_: Exception) { p }
        }
        if (!recent) return names.map { App(it.key, it.value) }.sortedBy { it.name.lowercase() }
        val um = c.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return emptyList()
        val now = System.currentTimeMillis()
        val st = try { um.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 12 * 3600_000L, now) } catch (_: Exception) { null } ?: return emptyList()
        return st.filter { it.lastTimeUsed > now - 12 * 3600_000L && names.containsKey(it.packageName) }
            .groupBy { it.packageName }.map { (p, l) -> p to l.maxOf { it.lastTimeUsed } }
            .sortedByDescending { it.second }.map { App(it.first, names[it.first] ?: it.first) }
    }

    fun open(c: Context, p: String) {
        picking.value = false
        val i = c.packageManager.getLaunchIntentForPackage(p) ?: run { Notice.show(c, L.tr("Không mở được app này."), true); return }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
        try { (act(c) ?: c).startActivity(i) } catch (_: Exception) { Notice.show(c, L.tr("Không mở được app này."), true); return }
        if (!inSplit.value) Notice.show(c, L.tr("Nếu app mở toàn màn hình: bật Hỗ trợ tiếp cận cho SK Light (trang Dẫn đường) để app tự chia màn hình."), true)
    }
}

/** Nút tròn nhỏ ở thanh menu màn ngang. */
@Composable
fun SplitBtn(size: Int = 46) {
    val ctx = LocalContext.current
    val src = remember { MutableInteractionSource() }
    val on = Split.inSplit.value
    Box(
        Modifier.size(size.dp).then(pressScale(src)).orb(on).clickable(src, LocalIndication.current) { Split.go(ctx) },
        contentAlignment = Alignment.Center
    ) { Icon(Icons.Filled.VerticalSplit, if (on) "Thoát chia đôi màn hình" else "Chia đôi màn hình", tint = if (on) Color.White else SubTxt, modifier = Modifier.size((size * 0.48f).dp)) }
}

/** Thẻ ở đầu trang Dẫn đường. */
@Composable
fun SplitCard() {
    val ctx = LocalContext.current
    Card {
        GroupTitle(Icons.Filled.VerticalSplit, "Chia đôi màn hình", 16)
        Spacer(Modifier.height(4.dp))
        Hint("SK Light một nửa, app đang mở (bản đồ, nhạc…) nửa kia. Bấm nút rồi chọn app ở nửa màn hình còn lại. Cần bật Hỗ trợ tiếp cận cho SK Light.", 12)
        Spacer(Modifier.height(10.dp))
        FillBtn(if (Split.inSplit.value) "Thoát chia đôi" else "Chia đôi với app đang mở", Icons.Filled.VerticalSplit, Pink, Modifier.fillMaxWidth()) { Split.go(ctx) }
    }
}

@Composable
fun SplitPicker() {
    if (!Split.picking.value) return
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val allowed = remember(tick) { Split.usageAllowed(ctx) }
    var all by remember { mutableStateOf(false) }
    val list = remember(all, allowed, tick) { Split.apps(ctx, allowed && !all) }
    AlertDialog(
        onDismissRequest = { Split.picking.value = false },
        confirmButton = { TextButton(onClick = { Split.picking.value = false }) { T("Đóng", 14, FontWeight.Bold, Pink) } },
        title = { T(if (allowed && !all) "Chọn app đang mở" else "Chọn app", 17, FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!allowed) {
                    T("Để hiện các app đang mở, hãy cho SK Light quyền xem mức sử dụng ứng dụng. Hoặc bật Hỗ trợ tiếp cận để máy tự hiện danh sách app đang mở.", 13, color = SubTxt)
                    OutlineBtn("Cho phép xem app đang mở", Modifier.fillMaxWidth()) {
                        try { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
                    }
                    OutlineBtn("Kiểm tra lại", Modifier.fillMaxWidth()) { tick++ }
                    T("Mọi ứng dụng", 13, FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                }
                if (list.isEmpty()) T("Không tìm thấy app nào.", 13, color = Grey)
                list.forEach { a ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { Split.open(ctx, a.pkg) }, verticalAlignment = Alignment.CenterVertically) {
                        T(a.name, 15, FontWeight.SemiBold)
                    }
                }
                if (allowed) OutlineBtn(if (all) "Chỉ hiện app đang mở" else "Hiện mọi ứng dụng", Modifier.fillMaxWidth()) { all = !all }
            }
        }
    )
}
