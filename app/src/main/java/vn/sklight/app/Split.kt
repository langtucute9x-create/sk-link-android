package vn.sklight.app

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Handler
import android.os.Looper
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// ===================== MỞ BẢN ĐỒ BÊN CẠNH (chia đôi màn hình) =====================
// Android không cho nhúng giao diện app khác vào SK Light, nên dùng chế độ chia đôi màn hình của máy:
// SK Light một nửa, app bản đồ nửa kia. Chưa ở chế độ chia đôi thì nhờ dịch vụ Hỗ trợ tiếp cận của SK Light (ScreenReader) bật chia đôi,
// rồi mở app bản đồ với cờ LAUNCH_ADJACENT để nó vào nửa còn lại.
object Split {
    class App(val pkg: String, val name: String)

    private val HINTS = listOf("maps", "vietmap", "waze", "navitel", "osmand", "sygic", "navi", "here.app", "organicmaps", "magicearth",
        "tomtom", "goong", "map4d", "amap", "autonavi", "baidumap", "petal", "gofa")
    private val main = Handler(Looper.getMainLooper())

    val pkg = mutableStateOf("")                // app bản đồ đã chọn (lưu khóa mapPkg)
    val picking = mutableStateOf(false)         // đang hiện hộp chọn app

    fun load(c: Context) { pkg.value = c.getSharedPreferences("sklight", Context.MODE_PRIVATE).getString("mapPkg", "") ?: "" }
    private fun save(c: Context) { c.getSharedPreferences("sklight", Context.MODE_PRIVATE).edit().putString("mapPkg", pkg.value).apply() }

    /** Mọi app có biểu tượng trên màn hình chính, trừ SK Light. */
    fun apps(c: Context, onlyMaps: Boolean): List<App> {
        val pm = c.packageManager
        val list = try { pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0) } catch (_: Exception) { emptyList() }
        return list.mapNotNull { r ->
            val p = r.activityInfo?.packageName ?: return@mapNotNull null
            if (p == c.packageName) return@mapNotNull null
            val n = try { r.loadLabel(pm).toString() } catch (_: Exception) { p }
            if (onlyMaps && HINTS.none { p.lowercase().contains(it) || n.lowercase().contains(it) }) null else App(p, n)
        }.distinctBy { it.pkg }.sortedBy { it.name.lowercase() }
    }

    fun name(c: Context, p: String): String = try { c.packageManager.getApplicationLabel(c.packageManager.getApplicationInfo(p, 0)).toString() } catch (_: Exception) { "" }

    private fun act(c: Context): Activity? { var x: Context? = c; while (x is ContextWrapper) { if (x is Activity) return x; x = x.baseContext }; return null }

    /** Bấm nút: chưa chọn app (hoặc app đã gỡ) thì hỏi chọn, có rồi thì mở bên cạnh. */
    fun go(c: Context) {
        if (pkg.value.isEmpty() || name(c, pkg.value).isEmpty()) { picking.value = true; return }
        open(c, pkg.value)
    }

    fun choose(c: Context, p: String) { pkg.value = p; save(c); picking.value = false; open(c, p) }

    private fun open(c: Context, p: String) {
        val i = c.packageManager.getLaunchIntentForPackage(p)
        if (i == null) { picking.value = true; return }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
        val a = act(c)
        val start = { try { c.startActivity(i) } catch (_: Exception) { Notice.show(c, L.tr("Không mở được app này."), true) } }
        if (a?.isInMultiWindowMode == true) { start(); return }
        val sr = ScreenReader.inst
        if (sr != null && sr.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)) {
            // máy cần một lúc để chuyển SK Light sang nửa màn hình rồi mới mở app bản đồ vào nửa kia
            main.postDelayed({ start() }, 900)
        } else {
            start()
            Notice.show(c, L.tr(if (sr == null) "Chưa chia đôi được: bật Hỗ trợ tiếp cận cho SK Light (trang Dẫn đường) để app tự chia màn hình." else "Màn hình này không cho chia đôi."), true)
        }
    }
}

/** Nút tròn nhỏ ở thanh menu màn ngang. */
@Composable
fun SplitBtn(size: Int = 46) {
    val ctx = LocalContext.current
    val src = remember { MutableInteractionSource() }
    Box(
        Modifier.size(size.dp).then(pressScale(src)).orb(false).clickable(src, LocalIndication.current) { Split.go(ctx) },
        contentAlignment = Alignment.Center
    ) { Icon(Icons.Filled.VerticalSplit, "Mở bản đồ bên cạnh", tint = SubTxt, modifier = Modifier.size((size * 0.48f).dp)) }
}

/** Thẻ ở đầu trang Dẫn đường. */
@Composable
fun SplitCard() {
    val ctx = LocalContext.current
    Card {
        GroupTitle(Icons.Filled.VerticalSplit, "Bản đồ bên cạnh", 16)
        Spacer(Modifier.height(4.dp))
        Hint("Chia đôi màn hình: SK Light một nửa, app bản đồ nửa kia. Cần bật Hỗ trợ tiếp cận cho SK Light để app tự chia.", 12)
        Spacer(Modifier.height(10.dp))
        val n = if (Split.pkg.value.isEmpty()) "" else Split.name(ctx, Split.pkg.value)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            FillBtn(if (n.isEmpty()) "Mở bản đồ bên cạnh" else L.tr("Mở") + " " + n, Icons.Filled.VerticalSplit, Pink, Modifier.weight(1.7f)) { Split.go(ctx) }
            if (n.isNotEmpty()) OutlineBtn("Đổi app", Modifier.weight(1f)) { Split.picking.value = true }
        }
    }
}

@Composable
fun SplitPicker() {
    if (!Split.picking.value) return
    val ctx = LocalContext.current
    var all by remember { mutableStateOf(false) }
    val list = remember(all) { Split.apps(ctx, !all) }
    AlertDialog(
        onDismissRequest = { Split.picking.value = false },
        confirmButton = { TextButton(onClick = { Split.picking.value = false }) { T("Đóng", 14, FontWeight.Bold, Pink) } },
        title = { T("Chọn app bản đồ", 17, FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (list.isEmpty()) T(if (all) "Không tìm thấy app nào." else "Không thấy app bản đồ nào trong máy.", 13, color = Grey)
                list.forEach { a ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { Split.choose(ctx, a.pkg) }, verticalAlignment = Alignment.CenterVertically) {
                        T(a.name, 15, if (a.pkg == Split.pkg.value) FontWeight.ExtraBold else FontWeight.SemiBold, if (a.pkg == Split.pkg.value) Pink else Txt)
                    }
                }
                OutlineBtn(if (all) "Chỉ hiện app bản đồ" else "Hiện mọi ứng dụng", Modifier.fillMaxWidth()) { all = !all }
            }
        }
    )
}
