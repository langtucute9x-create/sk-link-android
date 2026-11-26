package vn.sklight.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

// ===================== XIN QUYỀN DẪN ĐƯỜNG NGAY LẦN ĐẦU MỞ APP (chủ dự án yêu cầu 24/11) =====================
// Quyền đọc thông báo và Hỗ trợ tiếp cận KHÔNG xin được bằng hộp hỏi của máy: phải mở trang Cài đặt cho người dùng tự bật.
// Lần đầu mở app (sau các quyền Bluetooth, vị trí): hiện bảng hướng dẫn 2 bước, mỗi bước tự đánh dấu khi đã bật.
// App cài ngoài CH Play trên Android 13+: nút bật có thể bị làm mờ ("cài đặt bị hạn chế") → có nút mở Thông tin ứng dụng để cho phép.

object Setup {
    val show = mutableStateOf(false)            // đang hiện bảng xin quyền
    var pickAfter = false                        // đóng bảng xong thì mở hộp chọn hộp đèn (lần đầu mở app)
    fun notifOn(c: Context) = (Settings.Secure.getString(c.contentResolver, "enabled_notification_listeners") ?: "").contains(c.packageName)
    fun accOn(c: Context) = BuildConfig.PLAY || (Settings.Secure.getString(c.contentResolver, "enabled_accessibility_services") ?: "").contains(c.packageName)
    private fun sp(c: Context) = c.getSharedPreferences("sklight", Context.MODE_PRIVATE)
    fun asked(c: Context) = sp(c).getBoolean("setupAsked", false)
    fun markAsked(c: Context) = sp(c).edit().putBoolean("setupAsked", true).apply()
    fun open(c: Context, action: String) {
        try { c.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (_: Exception) { Notice.show(c, L.tr("Không mở được trang cấp quyền. Vào Cài đặt máy, tìm \"Truy cập thông báo\"."), true) }
    }
    fun openAppInfo(c: Context) {
        try { c.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + c.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
    }
}

@Composable
private fun StepRow(n: Int, icon: ImageVector, title: String, sub: String, done: Boolean, btn: String, onClick: () -> Unit) {
    val sh = RoundedCornerShape(Theme.r(12))
    Column(Modifier.fillMaxWidth().clip(sh).background(if (done) mix(OkGreen, 0.08f) else ChipBg).border(1.dp, if (done) mix(OkGreen, 0.5f) else Bd, sh).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).clip(CircleShape).background(if (done) OkGreen else Pink), contentAlignment = Alignment.Center) {
                if (done) Icon(Icons.Filled.Check, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(18.dp))
                else T("$n", 13, FontWeight.Bold, androidx.compose.ui.graphics.Color.White)
            }
            Spacer(Modifier.width(10.dp))
            Icon(icon, null, tint = if (done) OkGreen else Pink, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            T(title, 15, FontWeight.Bold, modifier = Modifier.weight(1f))
            if (done) T("Đã bật", 12, FontWeight.Bold, OkGreen)
        }
        Spacer(Modifier.height(4.dp))
        T(sub, 12, color = SubTxt)
        if (!done) { Spacer(Modifier.height(10.dp)); FillBtn(btn, icon, Pink, Modifier.fillMaxWidth(), onClick) }
    }
}

/** Bảng xin quyền dẫn đường. Hiện tự động một lần ở lần đầu mở app; [force] = mở lại từ trang Dẫn đường. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupSheet(show: Boolean, onClose: () -> Unit) {
    if (!show) return
    val ctx = LocalContext.current
    var n by remember { mutableStateOf(Setup.notifOn(ctx)) }
    var a by remember { mutableStateOf(Setup.accOn(ctx)) }
    LaunchedEffect(Unit) { while (true) { n = Setup.notifOn(ctx); a = Setup.accOn(ctx); delay(1000) } }
    val close = { Setup.markAsked(ctx); onClose() }
    ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = CardBg) {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 22.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            T("Cho phép báo đèn khi dẫn đường", 19, FontWeight.ExtraBold)
            T("Để đèn báo sắp rẽ và quá tốc độ theo Google Maps, Vietmap, Gofa, SK Light cần 2 quyền dưới đây. Android không cho app tự bật, nên bấm từng nút rồi bật SK Light trong trang Cài đặt mở ra.", 13, color = SubTxt)
            StepRow(1, Icons.Filled.Notifications, "Đọc thông báo", "Đọc hướng rẽ, khoảng cách từ thông báo của Google Maps, Vietmap.", n, "Mở trang Truy cập thông báo") {
                Setup.open(ctx, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            }
            if (!BuildConfig.PLAY) StepRow(2, Icons.Filled.Accessibility, "Hỗ trợ tiếp cận", "Đọc app dẫn đường không có thông báo (Gofa) và tự chia đôi màn hình.", a, "Mở trang Hỗ trợ tiếp cận") {
                Setup.open(ctx, Settings.ACTION_ACCESSIBILITY_SETTINGS)
            }
            if (!n || !a) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Theme.r(10))).background(ChipBg).padding(10.dp)) {
                    T("Nút bật SK Light bị mờ, không bấm được?", 13, FontWeight.Bold)
                    T("App cài ngoài CH Play nên máy chặn. Mở Thông tin ứng dụng, bấm dấu ⋮ ở góc trên, chọn \"Cho phép cài đặt bị hạn chế\", rồi bật lại.", 12, color = SubTxt)
                    Spacer(Modifier.height(8.dp))
                    OutlineBtn("Mở Thông tin ứng dụng", Modifier.fillMaxWidth()) { Setup.openAppInfo(ctx) }
                }
            }
            FillBtn(if (n && a) "Xong" else "Để sau", Icons.Filled.Check, if (n && a) OkGreen else Grey, Modifier.fillMaxWidth()) { close() }
            if (!(n && a)) T("Có thể bật sau ở trang Dẫn đường.", 11, color = Grey, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}
