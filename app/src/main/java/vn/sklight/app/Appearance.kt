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
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
        SwitchRow("Giảm hiệu ứng", "", !Theme.fx.value) { Theme.fx.value = !it; S.saveSettings() }
    }
    Card {
        GroupTitle(Icons.Filled.Style, "Phong cách", 16)
        Spacer(Modifier.height(12.dp))
        Grid(listOf(3, 0, 1, 2, 4), 3) { i, m ->
            val on = Theme.style.intValue == i
            val p = Theme.palOf(i, Theme.dark)
            Column(
                m.clip(RoundedCornerShape(Theme.r(8))).background(p.bgBot).border(if (on) 2.dp else 1.dp, if (on) Pink else Bd, RoundedCornerShape(Theme.r(8)))
                    .clickable { Theme.style.intValue = i; S.saveSettings() }.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // ô xem thử: một "thẻ" nhỏ có vạch chữ và chấm màu nhấn
                Row(Modifier.fillMaxWidth().height(26.dp).clip(RoundedCornerShape(Theme.r(6))).background(p.card).border(1.dp, p.bd, RoundedCornerShape(Theme.r(6))).padding(horizontal = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Pink))
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(p.txt))
                }
                T(if (Theme.dark) Theme.STYLES[i].second else Theme.STYLES[i].first, 12, if (on) FontWeight.ExtraBold else FontWeight.SemiBold, p.txt)
            }
        }
        Spacer(Modifier.height(14.dp))
        T("Bo góc", 13, FontWeight.Bold, SubTxt)
        Spacer(Modifier.height(8.dp))
        Grid(Theme.CORNERS.indices.toList(), 3) { i, m -> Chip(Theme.CORNERS[i], Theme.corner.intValue == i, m, 44.dp, 12) { Theme.corner.intValue = i; S.saveSettings() } }
    }
    Card {
        GroupTitle(Icons.Outlined.Palette, "Màu nhấn", 16)
        Spacer(Modifier.height(12.dp))
        Grid(Theme.ACCENTS.indices.toList(), 3) { i, m ->
            val on = Theme.accent.intValue == i
            val c = rgbColor(Theme.ACCENTS[i].second)
            Row(
                m.height(48.dp).clip(RoundedCornerShape(Theme.r(8))).background(if (on) mix(c, 0.14f) else ChipBg).border(if (on) 2.dp else 1.dp, if (on) c else Bd, RoundedCornerShape(Theme.r(8)))
                    .clickable { Theme.accent.intValue = i; S.saveSettings() }.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(20.dp).clip(CircleShape).background(c), contentAlignment = Alignment.Center) { if (on) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
                Spacer(Modifier.width(8.dp))
                FitT(Theme.ACCENTS[i].first, 12, if (on) FontWeight.ExtraBold else FontWeight.SemiBold, modifier = Modifier.weight(1f), align = androidx.compose.ui.text.style.TextAlign.Start)
            }
        }
    }
    Card {
        GroupTitle(Icons.Filled.FormatSize, "Kiểu chữ", 16)
        Spacer(Modifier.height(12.dp))
        // mỗi ô viết bằng chính phông đó để xem trước
        Grid(Theme.FONTS.indices.toList(), 2) { i, m ->
            val on = Theme.font.intValue == i
            Column(
                m.heightIn(min = 58.dp).clip(RoundedCornerShape(Theme.r(8))).background(if (on) tint(0.12f) else ChipBg).border(if (on) 2.dp else 1.dp, if (on) Pink else Bd, RoundedCornerShape(Theme.r(8)))
                    .clickable { Theme.font.intValue = i; S.saveSettings() }.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(L.tr(Theme.FONTS[i]), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (on) AccentText else Txt, fontFamily = fontFam(i), maxLines = 1)
                Text("Đèn viền nội thất 123", fontSize = 12.sp, color = SubTxt, fontFamily = fontFam(i), maxLines = 1)
            }
        }
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
                Notice.show(ctx, L.tr("Đã đặt ảnh làm màn hình chờ."))
            } catch (e: Exception) { Notice.show(ctx, L.tr("Không đọc được ảnh này.")) }
        }
    }
    Card {
        GroupTitle(Icons.Filled.Wallpaper, "Màn hình chờ", 16)
        Spacer(Modifier.height(12.dp))
        // hình demo: màn hình chờ thật thu nhỏ, đổi ngay khi chọn kiểu
        val cfg = LocalConfiguration.current
        val sw = cfg.screenWidthDp; val sh = cfg.screenHeightDp
        val wideScr = sw > sh && sw >= 600
        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(sw.toFloat() / sh).clip(RoundedCornerShape(Theme.r(10))).border(1.dp, Bd, RoundedCornerShape(Theme.r(10)))) {
            val k = maxWidth.value / sw
            // khung con to bằng cả màn hình nên được đặt CĂN GIỮA khung xem thử; phải thu nhỏ quanh tâm thì mới nằm trọn trong khung
            Box(Modifier.requiredSize(sw.dp, sh.dp).graphicsLayer(scaleX = k, scaleY = k, transformOrigin = TransformOrigin(0.5f, 0.5f))) { IdleScreen(wideScr, true) }
        }
        Spacer(Modifier.height(12.dp))
        Grid(IDLE_STYLES.indices.toList(), 2) { i, m -> Chip(IDLE_STYLES[i], S.idleStyle.intValue == i, m, 44.dp, 12) { S.idleStyle.intValue = i; S.saveSettings() } }
        Spacer(Modifier.height(12.dp))
        T("Vào màn hình chờ sau", 13, FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Grid(listOf(5, 10, 15, 20, 30, 60), 3) { v, m -> Chip("$v " + L.tr("giây"), S.idleDelay.intValue == v, m, 40.dp, 12) { S.idleDelay.intValue = v; S.saveSettings() } }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
            OutlineBtn("Chọn ảnh của tôi", Modifier.fillMaxWidth()) { try { pick.launch("image/*") } catch (e: Exception) { Notice.show(ctx, L.tr("Máy không có trình chọn ảnh.")) } }
        }
    }
    Card {
        GroupTitle(Icons.Filled.Wallpaper, "Nút nổi và màn hình nổi", 16)
        Spacer(Modifier.height(12.dp))
        // hình xem trước của kiểu đang chọn, đổi ngay khi chạm một kiểu
        FloatPreview(S.floatStyle.intValue)
        Spacer(Modifier.height(12.dp))
        T("Kích thước nút nổi", 13, FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Grid(listOf(80, 100, 125, 150, 180), 3) { v, m -> Chip("$v%", S.floatSize.intValue == v, m, 40.dp, 12) { S.floatSize.intValue = v; S.saveSettings(); FloatButton.sync(ctx) } }
        Spacer(Modifier.height(12.dp))
        Grid(listOf(5, 1, 2, 3, 4), 2) { i, m -> Chip(FloatButton.STYLES[i], S.floatStyle.intValue == i, m, 44.dp, 12) { S.floatStyle.intValue = i; S.saveSettings(); FloatButton.sync(ctx) } }
    }
    Card {
        GroupTitle(Icons.Filled.Style, "Vị trí menu khi màn hình ngang", 16)
        Spacer(Modifier.height(12.dp))
        Grid(listOf(true, false), 2) { top, m -> Chip(if (top) "Menu ở trên" else "Menu bên trái", S.menuTop.value == top, m, 44.dp, 12) { S.menuTop.value = top; S.saveSettings() } }
    }
    OutlineBtn("Về giao diện mặc định", Modifier.fillMaxWidth()) { Theme.reset(); S.saveSettings() }
}
