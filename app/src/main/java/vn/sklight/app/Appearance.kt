package vn.sklight.app

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// ===================== CÀI ĐẶT GIAO DIỆN =====================
@Composable
fun AppearancePage() { AppearanceTheme(); AppearanceIdle() }

/** Chế độ màu, màu nhấn, kiểu chữ, cỡ chữ. */
@Composable
fun AppearanceTheme() {
    BackToSettings()
    Card {
        GroupTitle(Icons.Filled.DarkMode, "Chế độ màu", 16)
        Spacer(Modifier.height(12.dp))
        Grid(Theme.MODES.indices.toList(), 3) { i, m -> Chip(Theme.MODES[i], Theme.mode.intValue == i, m, 44.dp, 13) { Theme.mode.intValue = i; S.saveSettings() } }
        Spacer(Modifier.height(8.dp))
        val w = Night.window()
        T("Tự động: giao diện tối từ ${Night.hm(w.first)} đến ${Night.hm(w.second)}, ngoài giờ đó là giao diện sáng. Giờ lấy theo mục Tự dịu đèn ban đêm.", 11, color = Grey)
    }
    Card {
        GroupTitle(Icons.Outlined.Palette, "Màu nhấn", 16)
        Spacer(Modifier.height(12.dp))
        Grid(Theme.ACCENTS.indices.toList(), 3) { i, m ->
            val on = Theme.accent.intValue == i
            val c = rgbColor(Theme.ACCENTS[i].second)
            Row(
                m.height(48.dp).clip(RoundedCornerShape(8.dp)).background(if (on) mix(c, 0.14f) else ChipBg).border(if (on) 2.dp else 1.dp, if (on) c else Bd, RoundedCornerShape(8.dp))
                    .clickable { Theme.accent.intValue = i; S.saveSettings() }.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(20.dp).clip(CircleShape).background(c), contentAlignment = Alignment.Center) { if (on) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
                Spacer(Modifier.width(8.dp))
                T(Theme.ACCENTS[i].first, 12, if (on) FontWeight.ExtraBold else FontWeight.SemiBold)
            }
        }
    }
    Card {
        GroupTitle(Icons.Filled.FormatSize, "Kiểu chữ", 16)
        Spacer(Modifier.height(12.dp))
        Grid(Theme.FONTS.indices.toList(), 3) { i, m -> Chip(Theme.FONTS[i], Theme.font.intValue == i, m, 44.dp, 12) { Theme.font.intValue = i; S.saveSettings() } }
        Spacer(Modifier.height(14.dp))
        T("Cỡ chữ", 13, FontWeight.Bold, SubTxt)
        Spacer(Modifier.height(8.dp))
        Grid(Theme.SCALES.indices.toList(), 4) { i, m -> Chip(Theme.SCALES[i].first, Theme.scale.intValue == i, m, 44.dp, 12) { Theme.scale.intValue = i; S.saveSettings() } }
    }
}

/** Kiểu màn hình chờ, ảnh của người dùng, xem thử; và nút về mặc định. */
@Composable
fun AppearanceIdle() {
    val ctx = LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                ctx.contentResolver.openInputStream(uri)?.use { inp -> photoFile(ctx).outputStream().use { inp.copyTo(it) } }
                S.idlePhotoVer.intValue++
                S.idleStyle.intValue = 3; S.saveSettings()
                Toast.makeText(ctx, L.tr("Đã đặt ảnh làm màn hình chờ."), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { Toast.makeText(ctx, L.tr("Không đọc được ảnh này."), Toast.LENGTH_SHORT).show() }
        }
    }
    Card {
        GroupTitle(Icons.Filled.Wallpaper, "Màn hình chờ", 16)
        Spacer(Modifier.height(12.dp))
        Grid(IDLE_STYLES.indices.toList(), 2) { i, m -> Chip(IDLE_STYLES[i], S.idleStyle.intValue == i, m, 44.dp, 12) { S.idleStyle.intValue = i; S.saveSettings() } }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
            OutlineBtn("Chọn ảnh của tôi", Modifier.weight(1f)) { try { pick.launch("image/*") } catch (e: Exception) { Toast.makeText(ctx, L.tr("Máy không có trình chọn ảnh."), Toast.LENGTH_SHORT).show() } }
            OutlineBtn("Xem thử", Modifier.weight(1f)) { S.idleShow.value = true }
        }
        Spacer(Modifier.height(8.dp))
        T("Bật hoặc tắt việc tự mở màn hình chờ khi xe chạy ở thẻ Khi lái xe.", 11, color = Grey)
    }
    OutlineBtn("Về giao diện mặc định", Modifier.fillMaxWidth()) { Theme.reset(); S.saveSettings() }
}
