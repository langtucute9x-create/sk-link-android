package vn.sklight.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

// ===================== GIAO DIỆN KÍCH HOẠT + BẢO HÀNH (bản làm mới 24/11) =====================

/** Vòng tròn số ngày còn bảo hành trên tổng 24 tháng. */
@Composable
private fun DaysRing(left: Int, size: Int = 92) {
    val a = Pink
    val frac = (left.coerceAtLeast(0) / 730f).coerceIn(0f, 1f)
    Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 9.dp.toPx()
            val r = Size(this.size.width - w, this.size.height - w)
            drawArc(Bd, 0f, 360f, false, Offset(w / 2, w / 2), r, style = Stroke(w))
            drawArc(Brush.sweepGradient(listOf(a, lite(a, 0.35f), a)), -90f, 360f * frac, false, Offset(w / 2, w / 2), r, style = Stroke(w, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            T("${left.coerceAtLeast(0)}", 22, FontWeight.ExtraBold)
            T("ngày", 11, color = Grey)
        }
    }
}

/** Thẻ Bảo hành ở trang Cài đặt: ngày kích hoạt và số ngày còn bảo hành. Chưa kích hoạt thì có nút nhập mã. */
@Composable
fun WarrantyCard() {
    Card {
        GroupTitle(Icons.Filled.VerifiedUser, "Bảo hành", 16)
        Spacer(Modifier.height(10.dp))
        if (!S.connected.value) {
            T("Nối hộp đèn để xem bảo hành của hộp.", 13, color = SubTxt)
        } else if (Act.ok.value) {
            val left = Act.daysLeft()
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (left != null) { DaysRing(left); Spacer(Modifier.width(16.dp)) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, null, tint = OkGreen, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        T(if (left != null && left < 0) "Đã hết hạn" else "Đang bảo hành", 15, FontWeight.Bold, if (left != null && left < 0) Color(0xFFF44336) else Txt)
                    }
                    T(L.tr("Kích hoạt") + ": " + (if (Act.at.value.isEmpty()) "—" else Act.vn(Act.at.value)), 13, color = SubTxt)
                    T(L.tr("Hết hạn") + ": " + (if (Act.until.value.isEmpty()) "—" else Act.vn(Act.until.value)), 13, color = SubTxt)
                }
            }
            if (Act.needCust.value) {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(Theme.r(10))).background(Color(0x1AFFA000)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.ErrorOutline, null, tint = Color(0xFFFFA000), modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    T("Hộp này chưa có thông tin bảo hành của khách. Cần điền để được bảo hành.", 13, FontWeight.SemiBold, Txt, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                FillBtn("Điền thông tin bảo hành", Icons.Filled.VerifiedUser, Pink, Modifier.fillMaxWidth()) { S.subPage.intValue = 13 }
            }
        } else {
            T("App chưa kích hoạt trên máy này.", 13, color = SubTxt)
            Spacer(Modifier.height(10.dp))
            FillBtn("Nhập mã kích hoạt", Icons.Filled.Key, Pink, Modifier.fillMaxWidth()) { S.subPage.intValue = 13 }
        }
    }
}

/** Biểu tượng tròn lớn đầu trang kích hoạt. */
@Composable
private fun Hero(icon: ImageVector) {
    val a = Pink
    Box(
        Modifier.size(64.dp).clip(CircleShape).background(Brush.linearGradient(listOf(lite(a, 0.25f), dim(a, 0.12f)))),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(32.dp)) }
}

/** Hai bước: Nhập mã → Thông tin bảo hành. */
@Composable
private fun Steps(step: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
        listOf("Nhập mã", "Thông tin bảo hành").forEachIndexed { i, s ->
            val on = i <= step
            if (i > 0) Box(Modifier.padding(horizontal = 8.dp).width(28.dp).height(2.dp).clip(CircleShape).background(if (on) Pink else Bd))
            Box(Modifier.size(22.dp).clip(CircleShape).background(if (on) Pink else ChipBg).border(1.dp, if (on) Pink else Bd, CircleShape), contentAlignment = Alignment.Center) {
                T("${i + 1}", 11, FontWeight.Bold, if (on) Color.White else Grey)
            }
            Spacer(Modifier.width(6.dp))
            T(s, 12, if (i == step) FontWeight.Bold else FontWeight.Normal, if (on) Txt else Grey)
        }
    }
}

/** Công tắc Luôn chạy nền. Tắt (mặc định, điện thoại): chạy nền nhưng mất kết nối hộp quá 1 phút thì dừng. Bật (màn hình xe / Android box): chạy nền liên tục. */
@Composable
fun RunModeRow() {
    val ctx = LocalContext.current
    SwitchRow("Luôn chạy nền", "", S.alwaysBg.value) {
        S.alwaysBg.value = it; S.saveSettings(); S.syncService(ctx); if (it) S.autoConnect()
    }
}

/** Lệnh 435: máy đang dùng mã đại lý / VIP. Không có hạn bảo hành, không hồ sơ khách. */
@Composable
private fun MasterCard() {
    Card {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Hero(Icons.Filled.VerifiedUser)
            Spacer(Modifier.height(12.dp))
            T("Đang dùng mã đại lý", 20, FontWeight.ExtraBold, align = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            T(Act.master.value, 15, FontWeight.Bold, AccentText, align = TextAlign.Center)
            if (Act.masterUntil.value.isNotEmpty()) { Spacer(Modifier.height(4.dp)); T(L.tr("Dùng đến") + ": " + Act.vn(Act.masterUntil.value), 13, color = SubTxt, align = TextAlign.Center) }
            Spacer(Modifier.height(8.dp))
            T("Dùng được app với mọi hộp đèn, không cần điền thông tin khách.", 13, color = SubTxt, align = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            FillBtn("Gỡ mã đại lý khỏi máy này", Icons.Filled.ArrowBack, Pink, Modifier.fillMaxWidth()) { Act.clearMaster() }
        }
    }
}

/** Ô nhập mã 4 ký tự (chữ và số) kiểu từng ô riêng. */
@Composable
private fun CodeBoxes(code: String, enabled: Boolean, onChange: (String) -> Unit) {
    val fr = remember { FocusRequester() }
    val a = Pink
    BasicTextField(
        value = code, onValueChange = { v -> onChange(v.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }.take(4)) },
        enabled = enabled, singleLine = true, cursorBrush = SolidColor(Color.Transparent), textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii),
        modifier = Modifier.fillMaxWidth().focusRequester(fr),
        decorationBox = { inner ->
            Box {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
                    for (i in 0 until 4) {
                        val ch = code.getOrNull(i)?.toString() ?: ""
                        val cur = enabled && i == code.length.coerceAtMost(3)
                        val sh = RoundedCornerShape(Theme.r(12))
                        Box(
                            Modifier.size(58.dp, 66.dp).clip(sh).background(if (ch.isNotEmpty()) mix(a, 0.08f) else ChipBg)
                                .border(if (cur) 2.dp else 1.dp, if (cur) a else if (ch.isNotEmpty()) mix(a, 0.45f) else Bd, sh).clickable(enabled) { fr.requestFocus() },
                            contentAlignment = Alignment.Center
                        ) { T(ch, 28, FontWeight.ExtraBold, if (enabled) Txt else Grey) }
                    }
                }
                Box(Modifier.size(1.dp)) { inner() }
            }
        }
    )
    LaunchedEffect(enabled) { if (enabled) try { fr.requestFocus() } catch (_: Exception) {} }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, icon: ImageVector, modifier: Modifier = Modifier.fillMaxWidth(),
                  hint: String = "", kb: KeyboardOptions = KeyboardOptions.Default) {
    OutlinedTextField(
        value, onChange, singleLine = true, modifier = modifier,
        label = { T(label, 12, color = Grey) },
        placeholder = if (hint.isEmpty()) null else ({ T(hint, 13, color = Grey) }),
        leadingIcon = { Icon(icon, null, tint = Pink, modifier = Modifier.size(20.dp)) },
        shape = RoundedCornerShape(Theme.r(12)), keyboardOptions = kb,
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Pink, unfocusedBorderColor = Bd, focusedContainerColor = CardBg, unfocusedContainerColor = ChipBg, cursorColor = Pink)
    )
}

/** Ô bấm để mở danh sách chọn (không gõ trực tiếp). */
@Composable
private fun PickField(value: String, label: String, icon: ImageVector, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val sh = RoundedCornerShape(Theme.r(12))
    Box(modifier.alpha(if (enabled) 1f else 0.5f)) {
        OutlinedTextField(
            value, {}, readOnly = true, singleLine = true, modifier = Modifier.fillMaxWidth(),
            label = { T(label, 12, color = Grey) },
            leadingIcon = { Icon(icon, null, tint = Pink, modifier = Modifier.size(20.dp)) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null, tint = Grey) },
            shape = sh,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Pink, unfocusedBorderColor = Bd, focusedContainerColor = CardBg, unfocusedContainerColor = ChipBg, cursorColor = Pink)
        )
        Box(Modifier.matchParentSize().clip(sh).clickable(enabled, onClick = onClick))
    }
}

/** Hộp chọn có ô tìm. [allowCustom]: cho dùng đúng chữ đã gõ khi danh sách không có (xe lạ, đời xe cũ). */
@Composable
private fun PickDialog(title: String, options: List<String>, allowCustom: Boolean, validCustom: (String) -> Boolean, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val s = q.trim()
    val shown = remember(q, options) { if (s.isEmpty()) options else options.filter { it.contains(s, ignoreCase = true) } }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).clip(RoundedCornerShape(Theme.r(16))).background(CardBg).padding(14.dp)) {
            T(title, 16, FontWeight.ExtraBold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                q, { q = it.take(40) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                placeholder = { T(if (allowCustom) "Gõ để tìm, hoặc gõ tên xe khác" else "Gõ để tìm", 13, color = Grey) },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = Pink, modifier = Modifier.size(20.dp)) },
                shape = RoundedCornerShape(Theme.r(12)),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Pink, unfocusedBorderColor = Bd, focusedContainerColor = CardBg, unfocusedContainerColor = ChipBg, cursorColor = Pink)
            )
            Spacer(Modifier.height(6.dp))
            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(shown) { o ->
                    Box(Modifier.fillMaxWidth().clickable { onPick(o) }.padding(horizontal = 6.dp, vertical = 12.dp)) { T(o, 15, FontWeight.SemiBold) }
                }
                if (allowCustom && s.isNotEmpty() && validCustom(s) && shown.none { it.equals(s, ignoreCase = true) }) {
                    item {
                        Box(Modifier.fillMaxWidth().clickable { onPick(s) }.padding(horizontal = 6.dp, vertical = 12.dp)) { T(L.tr("Dùng") + " \"$s\"", 15, FontWeight.ExtraBold, AccentText) }
                    }
                }
            }
        }
    }
}

/** Chọn lần lượt Hãng xe → Dòng xe → Đời xe. [onModel] nhận "Hãng Dòng" (rỗng khi chưa chọn đủ), [onYear] nhận năm 4 số (rỗng khi chưa chọn). */
@Composable
private fun CarPicker(onModel: (String) -> Unit, onYear: (String) -> Unit) {
    var brand by remember { mutableStateOf("") }
    var series by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var open by remember { mutableIntStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PickField(brand, "Hãng xe", Icons.Filled.DirectionsCar, true, Modifier.fillMaxWidth()) { open = 1 }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PickField(series, "Dòng xe", Icons.Filled.DirectionsCar, brand.isNotEmpty(), Modifier.weight(1.5f)) { open = 2 }
            PickField(year, "Đời xe", Icons.Filled.CalendarMonth, series.isNotEmpty(), Modifier.weight(1f)) { open = 3 }
        }
    }
    when (open) {
        1 -> PickDialog("Chọn hãng xe", CarData.brands(), true, { it.length >= 2 }, { b ->
            brand = b; series = ""; year = ""; open = 0; onModel(""); onYear("")
        }, { open = 0 })
        2 -> PickDialog("Chọn dòng xe", CarData.series(brand), true, { it.isNotEmpty() }, { sr ->
            series = sr; year = ""; open = 0
            onModel(if (sr.startsWith(brand, ignoreCase = true)) sr.take(60) else "$brand $sr".take(60)); onYear("")
        }, { open = 0 })
        3 -> PickDialog("Chọn đời xe", CarData.years(brand, series), true, { v -> v.length == 4 && v.all { it.isDigit() } && v.toInt() in 1950..2100 }, { y ->
            year = y; open = 0; onYear(y)
        }, { open = 0 })
    }
}

/** Khai thông tin khách cho hộp đã gắn mã (Lệnh 434). Bắt buộc để có hồ sơ bảo hành, nhưng không khóa điều khiển: thợ có thể để khách điền sau. */
@Composable
private fun CustomerForm() {
    val ctx = LocalContext.current
    var phone by remember { mutableStateOf("") }
    var plate by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var inst by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    var hot by remember { mutableStateOf(false) }
    LaunchedEffect(err) { if (err.isNotEmpty()) Speaker.err(ctx, err) }
    val save: () -> Unit = save@{
        if (phone.filter { it.isDigit() }.length < 10) { err = L.tr("Số điện thoại chưa đúng."); return@save }
        if (plate.filter { it.isLetterOrDigit() }.length < 4) { err = L.tr("Biển số xe chưa đúng."); return@save }
        if (model.isBlank()) { err = L.tr("Chưa chọn hãng xe và dòng xe."); return@save }
        if (year.length != 4) { err = L.tr("Chưa chọn đời xe."); return@save }
        if (inst.isBlank()) { err = L.tr("Chưa nhập đơn vị lắp đặt."); return@save }
        err = ""; hot = false
        val cust = JSONObject().put("phone", phone).put("plate", plate.trim()).put("model", model.trim()).put("year", year).put("installer", inst.trim())
        Act.setCustomer(cust) { r ->
            when {
                r == "ok" -> Notice.show(ctx, L.tr("Đã lưu thông tin bảo hành."), true)
                r == "phone_used" -> { err = L.tr("Số điện thoại này đã kích hoạt bảo hành cho một bộ sản phẩm khác."); hot = true }
                r == "not_activated" -> { err = L.tr("Hộp này chưa gắn mã kích hoạt."); hot = true }
                r == "invalid_input:phone" -> err = L.tr("Số điện thoại chưa đúng.")
                r == "invalid_input:plate" -> err = L.tr("Biển số xe chưa đúng.")
                r == "invalid_input:model" -> err = L.tr("Chưa chọn hãng xe và dòng xe.")
                r == "invalid_input:year" -> err = L.tr("Chưa chọn đời xe.")
                r == "invalid_input:installer" -> err = L.tr("Chưa nhập đơn vị lắp đặt.")
                r == "network" -> err = L.tr("Không kết nối được máy chủ. Kiểm tra mạng rồi thử lại.")
                else -> { err = L.tr("Máy chủ đang bận. Hãy thử lại sau ít phút."); hot = true }
            }
        }
    }
    Card {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Hero(Icons.Filled.VerifiedUser)
            Spacer(Modifier.height(12.dp))
            T("Thông tin bảo hành", 20, FontWeight.ExtraBold, align = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            T("Hộp đã gắn mã, bảo hành đã bắt đầu tính. Điền thông tin xe và khách để có hồ sơ bảo hành. Thợ lắp có thể điền hộ, hoặc để khách tự điền sau.", 13, color = SubTxt, align = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Field(phone, { v -> phone = v.filter { it.isDigit() || it == '+' }.take(14); err = "" }, "Số điện thoại khách", Icons.Filled.Phone, kb = KeyboardOptions(keyboardType = KeyboardType.Phone))
            Field(plate, { v -> plate = v.uppercase().take(20); err = "" }, "Biển số xe", Icons.Filled.Badge, hint = "30A-123.45", kb = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
            CarPicker({ model = it; err = "" }, { year = it; err = "" })
            Field(inst, { inst = it.take(100); err = "" }, "Đơn vị lắp đặt", Icons.Filled.Storefront, hint = "ABC Auto", kb = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
        }
        AnimatedVisibility(err.isNotEmpty()) {
            Row(Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(Theme.r(10))).background(Color(0x1AF44336)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ErrorOutline, null, tint = Color(0xFFF44336), modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                T(err, 13, FontWeight.SemiBold, Color(0xFFD32F2F), modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(16.dp))
        FillBtn(if (Act.busy.value) "Đang lưu…" else "Lưu thông tin bảo hành", Icons.Filled.VerifiedUser, Pink, Modifier.fillMaxWidth().height(50.dp), save)
        if (hot) {
            Spacer(Modifier.height(10.dp))
            FillBtn("Gọi hotline 0868 055 555", Icons.Filled.Call, OkGreen, Modifier.fillMaxWidth()) { Act.call(ctx) }
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.clip(RoundedCornerShape(Theme.r(8))).clickable { S.subPage.intValue = 0 }.padding(8.dp)) {
                T("Để khách điền sau", 12, FontWeight.SemiBold, AccentText)
            }
        }
    }
}

/** Trang nhập mã. [locked] = đang hiện thay cho trang điều khiển vì đã nối hộp đèn mà máy chưa kích hoạt. */
@Composable
fun ActivationPage(locked: Boolean) {
    val ctx = LocalContext.current
    var code by remember { mutableStateOf("") }
    var long by remember { mutableStateOf(false) }      // nhập mã 12 ký tự cũ (Lệnh 421)
    // 2 = mã giới thiệu 4 chữ: số điện thoại, biển số, dòng xe, đời xe, đơn vị lắp đặt. 1 = mã 12 ký tự cũ: tên, số điện thoại, nơi lắp đặt.
    var need by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var place by remember { mutableStateOf("") }
    var plate by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var inst by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    var hot by remember { mutableStateOf(false) }
    LaunchedEffect(err) { if (err.isNotEmpty()) Speaker.err(ctx, err) }
    if (!locked) BackRow(0, "Cài đặt")
    if (Act.master.value.isNotEmpty()) { MasterCard(); return }
    if (S.connected.value && Act.ok.value) { if (Act.needCust.value) CustomerForm() else WarrantyCard(); return }
    // chưa nối hộp, hoặc đang hỏi SK Core, hoặc lần đầu nối hộp này mà không có mạng
    val ck = Act.checking.intValue
    if (!S.connected.value || ck == 1 || ck == 2) {
        Card {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Hero(Icons.Filled.Key)
                Spacer(Modifier.height(12.dp))
                T(when { !S.connected.value -> "Nối hộp đèn để kích hoạt"; ck == 1 -> "Đang kiểm tra hộp đèn…"; else -> "Cần có mạng" }, 19, FontWeight.ExtraBold, align = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                T(when {
                    !S.connected.value -> "Kích hoạt gắn với hộp đèn. Nối hộp đèn trước, app sẽ tự kiểm tra hộp đã kích hoạt chưa."
                    ck == 1 -> "App đang hỏi máy chủ SK xem hộp này đã kích hoạt chưa."
                    else -> "Lần đầu nối hộp này trên máy này cần có mạng để kiểm tra kích hoạt. Bật Wi-Fi hoặc dữ liệu di động rồi thử lại."
                }, 13, color = SubTxt, align = TextAlign.Center)
                if (S.connected.value && ck == 2) {
                    Spacer(Modifier.height(14.dp))
                    FillBtn("Thử lại", Icons.Filled.Refresh, Pink, Modifier.fillMaxWidth()) { Act.recheck() }
                }
            }
        }
        return
    }

    val go: () -> Unit = go@{
        val raw = code.filter { it.isLetterOrDigit() }
        val short = raw.length == 4 && raw.all { it in 'A'..'Z' || it in '0'..'9' }
        if (!short && raw.length < 8) { err = L.tr(if (long) "Mã kích hoạt chưa đủ ký tự." else "Mã kích hoạt là 4 ký tự (chữ và số) in trên tem."); return@go }
        if (need == 1) {
            if (name.isBlank()) { err = L.tr("Chưa nhập tên khách hàng."); return@go }
            if (phone.filter { it.isDigit() }.length < 10) { err = L.tr("Số điện thoại chưa đúng."); return@go }
            if (place.isBlank()) { err = L.tr("Chưa nhập nơi lắp đặt."); return@go }
        }
        if (need == 2) {
            if (phone.filter { it.isDigit() }.length < 10) { err = L.tr("Số điện thoại chưa đúng."); return@go }
            if (plate.filter { it.isLetterOrDigit() }.length < 4) { err = L.tr("Biển số xe chưa đúng."); return@go }
            if (model.isBlank()) { err = L.tr("Chưa chọn hãng xe và dòng xe."); return@go }
            if (year.length != 4) { err = L.tr("Chưa chọn đời xe."); return@go }
            if (inst.isBlank()) { err = L.tr("Chưa nhập đơn vị lắp đặt."); return@go }
        }
        err = ""; hot = false
        val cust = when (need) {
            1 -> JSONObject().put("name", name.trim()).put("phone", phone).put("place", place.trim())
            2 -> JSONObject().put("phone", phone).put("plate", plate.trim()).put("model", model.trim()).put("year", year).put("installer", inst.trim())
            else -> null
        }
        Act.activate(ctx, raw, cust, defer = short) { r ->
            when {
                r == "ok" -> {
                    Notice.show(ctx, L.tr(if (Act.needCust.value) "Đã gắn mã vào hộp. Bảo hành đến " else "Đã kích hoạt. Bảo hành đến ") + Act.vn(Act.until.value), true)
                    if (Act.needCust.value) S.subPage.intValue = 13       // sang ngay trang điền thông tin bảo hành
                }
                r == "need_customer" -> need = if (short) 2 else 1
                r == "invalid_code" -> err = L.tr("Mã kích hoạt không đúng. Kiểm tra lại mã trên tem.")
                r == "code_used" -> { err = L.tr("Mã này đã kích hoạt cho một hộp đèn khác."); hot = true }
                r == "phone_used" -> { err = L.tr("Số điện thoại này đã kích hoạt bảo hành cho một bộ sản phẩm khác."); hot = true }
                r == "limit_reached" -> { err = L.tr("Mã đã hết lượt kích hoạt."); hot = true }
                r == "locked" -> { err = L.tr("Mã này đang bị khóa."); hot = true }
                r == "expired" -> { err = L.tr("Mã đại lý đã hết hạn."); hot = true }
                r == "too_many" -> err = L.tr("Nhập sai quá nhiều lần. Hãy thử lại sau 15 phút.")
                r == "invalid_input:phone" -> err = L.tr("Số điện thoại chưa đúng.")
                r == "invalid_input:name" -> err = L.tr("Chưa nhập tên khách hàng.")
                r == "invalid_input:place" -> err = L.tr("Chưa nhập nơi lắp đặt.")
                r == "invalid_input:plate" -> err = L.tr("Biển số xe chưa đúng.")
                r == "invalid_input:model" -> err = L.tr("Chưa chọn hãng xe và dòng xe.")
                r == "invalid_input:year" -> err = L.tr("Chưa chọn đời xe.")
                r == "invalid_input:installer" -> err = L.tr("Chưa nhập đơn vị lắp đặt.")
                r == "network" -> err = L.tr("Không kết nối được máy chủ. Kiểm tra mạng rồi thử lại.")
                else -> { err = L.tr("Máy chủ đang bận. Hãy thử lại sau ít phút."); hot = true }
            }
        }
    }

    Card {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Hero(if (need == 0) Icons.Filled.Key else Icons.Filled.VerifiedUser)
            Spacer(Modifier.height(12.dp))
            T(if (need == 0) "Kích hoạt SK Light" else "Đăng ký bảo hành 24 tháng", 20, FontWeight.ExtraBold, align = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            T(when {
                need > 0 -> "Mã này kích hoạt lần đầu. Điền thông tin xe để đăng ký bảo hành."
                locked -> "Hộp đèn này chưa kích hoạt. Nhập mã trên tem một lần; sau đó mọi điện thoại, màn hình xe nối vào hộp đều tự dùng được."
                else -> "Nhập mã trên tem để dùng app điều khiển đèn trên máy này."
            }, 13, color = SubTxt, align = TextAlign.Center)
            Spacer(Modifier.height(14.dp))
            Steps(if (need == 0) 0 else 1)
            Spacer(Modifier.height(16.dp))
        }

        if (need == 0) {
            if (!long) {
                CodeBoxes(code, !Act.busy.value) { code = it; err = ""; hot = false; if (it.length == 4 && !Act.busy.value) go() }
                Spacer(Modifier.height(8.dp))
                T("Mã giới thiệu 4 ký tự (chữ và số) in trên tem dán ở vỏ hộp sản phẩm.", 12, color = Grey, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            } else {
                Field(code, { v -> code = v.uppercase().filter { it.isLetterOrDigit() || it == '-' }.take(20); err = ""; hot = false }, "Mã kích hoạt", Icons.Filled.Key,
                    kb = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii))
            }
        } else {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(Theme.r(10))).background(ChipBg).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Key, null, tint = Pink, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                T(code, 15, FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                Row(Modifier.clip(RoundedCornerShape(Theme.r(8))).clickable(!Act.busy.value) { need = 0; err = ""; code = ""; model = ""; year = "" }.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.ArrowBack, null, tint = SubTxt, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); T("Đổi mã", 12, FontWeight.SemiBold, SubTxt)
                }
            }
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (need == 1) Field(name, { name = it.take(80); err = "" }, "Tên khách hàng", Icons.Filled.Badge, kb = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                Field(phone, { v -> phone = v.filter { it.isDigit() || it == '+' }.take(14); err = "" }, "Số điện thoại", Icons.Filled.Phone, kb = KeyboardOptions(keyboardType = KeyboardType.Phone))
                if (need == 2) {
                    Field(plate, { v -> plate = v.uppercase().take(20); err = "" }, "Biển số xe", Icons.Filled.Badge, hint = "30A-123.45", kb = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
                    CarPicker({ model = it; err = "" }, { year = it; err = "" })
                    Field(inst, { inst = it.take(100); err = "" }, "Đơn vị lắp đặt", Icons.Filled.Storefront, hint = "ABC Auto", kb = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                }
                if (need == 1) Field(place, { place = it.take(200); err = "" }, "Nơi lắp đặt", Icons.Filled.Storefront, hint = "Tên cửa hàng hoặc địa chỉ lắp đèn")
            }
        }

        AnimatedVisibility(err.isNotEmpty()) {
            Row(Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(Theme.r(10))).background(Color(0x1AF44336)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ErrorOutline, null, tint = Color(0xFFF44336), modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                T(err, 13, FontWeight.SemiBold, Color(0xFFD32F2F), modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(16.dp))
        FillBtn(if (Act.busy.value) "Đang kiểm tra…" else if (need > 0) "Kích hoạt bảo hành" else "Kích hoạt", Icons.Filled.VerifiedUser, Pink, Modifier.fillMaxWidth().height(50.dp), go)
        if (hot) {
            Spacer(Modifier.height(10.dp))
            FillBtn("Gọi hotline 0868 055 555", Icons.Filled.Call, OkGreen, Modifier.fillMaxWidth()) { Act.call(ctx) }
        }
        if (need == 0) {
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.clip(RoundedCornerShape(Theme.r(8))).clickable { long = !long; code = ""; err = "" }.padding(8.dp)) {
                    T(if (long) "Nhập mã 4 chữ trên tem" else "Mã của tôi dài hơn 4 ký tự (mã đại lý)", 12, FontWeight.SemiBold, AccentText)
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        T("Cần hỗ trợ?", 12, color = Grey); Spacer(Modifier.width(6.dp))
        Box(Modifier.clip(RoundedCornerShape(Theme.r(8))).clickable { Act.call(ctx) }.padding(4.dp)) { T("Gọi 0868 055 555", 12, FontWeight.Bold, AccentText) }
    }
    if (locked) T("Trang Cài đặt vẫn dùng được khi chưa kích hoạt.", 11, color = Grey, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
}
