package vn.sklight.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// ===================== CẨM NANG SỬ DỤNG =====================
// Nội dung viết theo đúng những gì app đang có ở bản 1.4.14. Phần "Chức năng theo xe" chưa có vì chưa kiểm chứng xong trên xe thật.
// Thêm tính năng mới thì bổ sung mục tương ứng ở đây.

private val MANUAL: List<Pair<String, List<String>>> = listOf(
    "1. Kết nối với hộp đèn" to listOf(
        "Bật Bluetooth của máy, mở SK Light và đồng ý các quyền app hỏi (Bluetooth, vị trí, thông báo).",
        "Chọn hộp đèn trong danh sách hiện ra. Từ lần sau app tự kết nối với hộp đã chọn.",
        "Ở màn hình dọc, chấm nhỏ ở góc trên bên phải màu xanh là đã kết nối, màu xám là chưa.",
        "Khi mất kết nối, đầu trang Đèn hiện thẻ có hình phích cắm với nút Kết nối (nối lại hộp đã chọn) và Chọn hộp đèn (tìm hộp khác). Kết nối được thì app nói \"Đã kết nối với hộp đèn\".",
        "Máy không có Bluetooth hoặc Bluetooth đang tắt (một số màn hình xe và Android box): app báo rõ nguyên nhân. Bật Bluetooth rồi nối lại. Máy không có Bluetooth thì không điều khiển được hộp đèn trên máy đó.",
        "Lỗi nối hộp hoặc kích hoạt được đọc to bằng tiếng Việt khi app đang mở. Mất kết nối chỉ báo một lần cho đến khi nối lại được, và không báo khi bạn tự bấm Ngắt kết nối.",
        "Hộp đèn chỉ nhận một kết nối một lúc. Nếu không nối được, hãy tắt app đèn trên điện thoại hoặc màn hình khác.",
        "Muốn đổi sang hộp khác: Cài đặt, thẻ Thông tin thiết bị, bấm Chọn lại."
    ),
    "2. Trang Đèn" to listOf(
        "Nút nguồn bật hoặc tắt toàn bộ đèn.",
        "Đồng nhất, Khu vực 1, Khu vực 2: chọn chỉnh cả xe hay từng khu. Độ sáng và màu chỉnh theo khu đang chọn.",
        "Chọn màu bằng bánh xe màu hoặc các bóng màu mẫu. Bấm dấu cộng để lưu màu đang chọn thành màu riêng; giữ lâu một màu riêng để xóa.",
        "Chọn chế độ màu vùng 2: Màu tĩnh, Màu tự động, Màu nhấp nháy là của hộp; các kiểu còn lại do app điều khiển.",
        "Chạm một ô chế độ để chạy. Chế độ có nhiều kiểu thì hàng kiểu con hiện ngay bên dưới.",
        "Thẻ Hiệu ứng (dưới cùng trang Đèn; màn ngang thì ở cột trái) chỉnh Tốc độ nháy và chạy, Độ nhạy nhịp điệu (dùng cho các chế độ nhịp nhạc).",
        "Chạm giữ một ô chế độ để ghim lên đầu trang; chạm giữ lần nữa để bỏ ghim. Ghim được tối đa 9 chế độ.",
        "Các chế độ do app tự chạy (từ Cầu vồng mượt đến Thư giãn, Chế độ của tôi và các kiểu thêm của vùng 2) chỉ chạy khi app đang mở và đang kết nối."
    ),
    "3. Bối cảnh và Chế độ của tôi" to listOf(
        "Bối cảnh là một bộ cài đặt lưu sẵn (màu, độ sáng, chế độ, tốc độ). Hàng bối cảnh nằm ở đầu trang Đèn, chạm là dùng.",
        "Bấm Thêm để lưu cài đặt đang dùng thành bối cảnh mới. Giữ lâu một bối cảnh để đổi tên, cập nhật hoặc xóa. Lưu được tối đa 12 bối cảnh.",
        "Chế độ của tôi: bấm ô có dấu cộng, chọn 2 đến 5 màu, chọn kiểu chuyển Mượt hoặc Nhảy, hai khu Cùng màu hoặc So le, đặt tên rồi bấm Lưu và chạy.",
        "Chạm biểu tượng bút trên một chế độ của tôi để sửa hoặc xóa. Lưu được tối đa 12 chế độ."
    ),
    "4. Chào mừng và sơ đồ xe" to listOf(
        "Hiệu ứng chào mừng: bật thì đèn chạy hiệu ứng chào khi hộp được cấp điện. Chào xung quanh xe: đèn chào lần lượt từng cửa.",
        "Chọn Chế độ 1 đến 6, hoặc Tùy chỉnh để tự chọn màu chào. Tốc độ chào mừng có 4 mức.",
        "Gán hộp phụ vào cửa: chạm một Hộp phụ, đèn ở cửa gắn hộp đó sẽ nhấp nháy; thấy cửa nào nháy thì chạm hình xe đang mở cửa đó. Giữ lâu một hộp phụ để bỏ gán.",
        "Chạm một hình xe (hoặc hai mũi tên trái phải) để chọn đoạn đèn, rồi chỉnh số LED của đoạn đó bằng nút trừ và cộng; chạm vào con số để gõ thẳng.",
        "Hai nút mũi tên phía dưới chọn hướng ánh sáng chạy của đoạn đang chọn.",
        "Vị trí bắt đầu chào mừng: chọn đoạn mà hiệu ứng chào bắt đầu chạy. Khôi phục mặc định đưa sơ đồ về ban đầu."
    ),
    "5. Dẫn đường: báo rẽ và quá tốc độ" to listOf(
        "App đọc chỉ dẫn từ app bản đồ đang dẫn đường (Google Maps, Vietmap, Gofa…) để báo bằng đèn.",
        "Lần đầu mở app, SK Light hiện bảng 2 bước: bật quyền đọc thông báo và Hỗ trợ tiếp cận cho SK Light. Nút bật bị mờ thì mở Thông tin ứng dụng, bấm dấu ⋮, chọn Cho phép cài đặt bị hạn chế. Bỏ qua thì bật sau ở trang Dẫn đường.",
        "Báo đèn khi sắp rẽ: bật công tắc, chọn khoảng cách Báo trước (50 đến 500 m), chọn Kiểu 1, Kiểu 2 hoặc Kiểu 3 và tốc độ của kiểu đó. Bấm Thử nháy để xem từng kiểu trên đèn.",
        "Thời gian đèn chạy: chọn số giây đèn báo chạy, hoặc để tới khi rẽ xong.",
        "Nếu rẽ trái mà đèn lại chạy sang phải, bật Đảo chiều chạy.",
        "Thử nháy 4 giây: bấm Rẽ trái, Rẽ phải hoặc Quá tốc độ để xem trước trên đèn.",
        "Nháy đỏ khi quá tốc độ: bật công tắc và chọn khu nháy đỏ. Chỉ hoạt động khi app bản đồ hiện biển giới hạn tốc độ.",
        "Chia đôi màn hình: bấm nút chia đôi (thẻ Chia đôi màn hình ở trang Dẫn đường, hoặc nút trên thanh menu màn ngang). SK Light sang một nửa, nửa kia hiện các app đang mở để chọn (bản đồ, nhạc…). Bấm lần nữa để thoát. Cần bật Hỗ trợ tiếp cận cho SK Light; màn hình xe phải hỗ trợ chia đôi.",
        "Để báo được khi SK Light không mở trên màn hình, hãy bật Tự kết nối khi khởi động máy ở thẻ Khi lái xe."
    ),
    "6. Khi lái xe" to listOf(
        "Tự kết nối khi khởi động máy: màn hình xe bật lên là app tự nối hộp đèn, không cần mở app.",
        "Tự dịu đèn ban đêm: đèn tự giảm sáng khi trời tối, theo giờ mặt trời lặn và mọc hoặc theo giờ tự đặt. Kéo thanh Độ sáng ban đêm để chọn mức.",
        "Màn hình chờ khi xe chạy: tự mở khi công tắc bật, xe chạy từ 10 km/h và 10 giây không chạm màn hình. Xe đứng yên thì không tự mở.",
        "Mở màn hình chờ bằng tay: bấm nút có hình khung ảnh trên thanh menu ở màn hình ngang. Chạm màn hình chờ để quay lại.",
        "Trên màn hình chờ, bấm nút mũi tên vòng để đổi kiểu. Có 11 kiểu, gồm đồng hồ, tốc độ, dẫn đường, nhạc đang phát và ảnh của bạn.",
        "Nút nổi trên màn hình: nút SK hiện trên các ứng dụng khác để bật tắt, chỉnh độ sáng, đổi bối cảnh mà không cần mở app. Cần cấp quyền hiển thị trên ứng dụng khác. Kéo nút để dời chỗ; kéo xuống ô dấu X ở giữa cạnh dưới màn hình rồi thả để tắt nút nổi."
    ),
    "7. Trợ lý giọng nói" to listOf(
        "Bấm nút micro rồi nói câu lệnh. Trợ lý chỉ dùng được khi app đang để tiếng Việt, máy có mạng và có app Google.",
        "Ví dụ: “bật đèn”, “tắt đèn”, “màu đỏ”, “sáng hơn”, “tối hơn”, “độ sáng 50”, “chế độ thở”, “chế độ 15”, “tốc độ 8”, “khu vực 2 màu xanh”.",
        "Gọi bối cảnh và chế độ bằng tên của nó, ví dụ “thư giãn”, “thể thao”.",
        "Mỗi ô chế độ có một con số ở góc: nói “chế độ” kèm số đó để chạy.",
        "Cài đặt, mục Trợ lý SK Light (AI): bật tắt trả lời bằng tiếng nói, tắt tiếng báo khi bấm micro, chọn giọng và cách thông báo của hộp.",
        "Danh sách đầy đủ các câu lệnh ở ngay bên dưới.",
        "Muốn app trả lời bằng tiếng nói, máy cần có giọng đọc tiếng Việt; trang Trợ lý có nút cài."
    ),
    "8. Giao diện và ngôn ngữ" to listOf(
        "Cài đặt, mục Cài đặt giao diện: chọn Sáng, Tối hoặc Tự động (mặc định là Tự động); phong cách nền (Xanh băng, Xanh đêm, Cổ điển…); bo góc; màu nhấn; kiểu chữ và cỡ chữ.",
        "Giảm hiệu ứng: bật nếu máy chạy chậm, giao diện sẽ bỏ khối nổi, bóng đổ và chuyển động.",
        "Màn hình chờ: chọn kiểu và xem trước ngay trong trang này; bấm Chọn ảnh của tôi để dùng ảnh riêng.",
        "Nút nổi và màn hình nổi: chọn một trong năm kiểu (mặc định Cột neon), có hình xem trước.",
        "Vị trí menu khi màn hình ngang: mặc định ở trên; muốn thành cột bên trái thì chọn ở đây.",
        "Ở màn hình ngang, các dòng giải thích được ẩn cho gọn. Chạm biểu tượng chữ i ở góc thẻ để hiện.",
        "Cài đặt, mục Cài đặt ngôn ngữ: Việt, Anh, Nhật, Hàn, Thái, Khmer, Lào."
    ),
    "9. Lắp đặt (dành cho thợ)" to listOf(
        "Ghép nối hộp phụ: cấp nguồn cho hộp phụ cần ghép, bấm Bắt đầu ghép nối và đợi tối đa 60 giây. Đặt Số lượng hộp phụ đúng với xe.",
        "Xóa hộp phụ: xóa mọi hộp phụ đã ghép khỏi hộp chính, sau đó phải ghép lại từ đầu.",
        "Sau khi ghép, sang trang Chào mừng, phần Sơ đồ xe để gán từng hộp phụ vào đúng cửa.",
        "Hướng ánh sáng chạy: chọn Thuận hoặc Ngược cho từng khu vực nếu hiệu ứng chạy ngược chiều mong muốn.",
        "Cài đặt nhà máy: Chọn loại xe, phiên bản hộp ảo ảnh (Thể thao hoặc Ánh sáng).",
        "Màu gửi cho hộp: để Bình thường. Chỉ đổi sang Đảo màu nếu chọn màu đỏ mà đèn ra xanh.",
        "Khôi phục cài đặt gốc: hộp xóa hết cài đặt (hộp phụ, vị trí cửa, số LED, loại xe, chào mừng) và phải cài lại từ đầu. Không hoàn tác được."
    ),
    "10. Cập nhật ứng dụng" to listOf(
        "Khi có bản mới, một thanh màu hiện ở đầu màn hình. Chạm thanh đó, bấm Cập nhật, chờ tải xong rồi bấm Cài đặt.",
        "Lần đầu, máy hỏi cho phép cài từ nguồn này: bật cho SK Light rồi quay lại bấm Cài đặt.",
        "Nếu máy cảnh báo Play Protect, chọn Vẫn cài đặt.",
        "Màn hình xe không cài trực tiếp được: bấm Hiện mã QR, quét bằng điện thoại để tải file cài đặt.",
        "Kiểm tra bằng tay: Cài đặt, mục Kiểm tra cập nhật."
    ),
    "11. Xử lý sự cố thường gặp" to listOf(
        "Không thấy hộp đèn trong danh sách: kiểm tra hộp đã có điện, bật Bluetooth và vị trí của máy, đứng gần xe, tắt app đèn trên máy khác.",
        "Màu đèn ra sai (chọn đỏ ra xanh): Cài đặt, Cài đặt nhà máy, Màu gửi cho hộp, chọn Đảo màu.",
        "Đèn không báo rẽ: kiểm tra đã cấp đủ quyền ở trang Dẫn đường, app bản đồ đang dẫn đường, và công tắc Báo đèn khi sắp rẽ đang bật.",
        "Màn hình chờ không tự mở: cần bật công tắc ở thẻ Khi lái xe và xe phải chạy từ 10 km/h. Dòng Tốc độ đang đọc được dưới công tắc cho biết máy có đọc được tốc độ không.",
        "Giọng nói không nhận lệnh: kiểm tra mạng, quyền micro của SK Light và của app Google.",
        "Chữ quá to hoặc quá nhỏ: Cài đặt giao diện, mục Cỡ chữ.",
        "Máy chạy giật: bật Giảm hiệu ứng trong Cài đặt giao diện.",
        "Mục Chức năng gốc xe (đèn báo theo xi nhan, cửa, dây an toàn…) đã có trong Cài đặt nhưng đang được hoàn thiện; hướng dẫn chi tiết sẽ bổ sung sau."
    ),
    "12. Kích hoạt app và bảo hành" to listOf(
        "Kích hoạt gắn với hộp đèn, không gắn với máy. Hộp đã kích hoạt thì mọi điện thoại, màn hình xe nối vào đều tự dùng được, không cần nhập mã.",
        "Nối một hộp chưa kích hoạt: các trang điều khiển bị khóa và hiện ô nhập mã. Lần đầu nối một hộp trên một máy cần có mạng để kiểm tra.",
        "Mã kích hoạt là mã giới thiệu 4 ký tự (chữ và số) in trên tem dán ở vỏ hộp sản phẩm, cùng mã dùng trên web sklight.vn. Cần có mạng khi kích hoạt; sau đó dùng không cần mạng.",
        "Thợ nhập mã là gắn mã vào hộp xong ngay, ngày bảo hành 24 tháng tính từ lúc gắn. Thông tin khách có thể khai sau; hộp còn thiếu hồ sơ thì thẻ Bảo hành nhắc ở mọi máy nối vào hộp đó.",
        "Khai thông tin bảo hành: số điện thoại, biển số xe, hãng xe, dòng xe, đời xe và đơn vị lắp đặt. Hãng xe, dòng xe và đời xe chọn trong danh sách các xe đang bán ở Việt Nam. Không có trong danh sách thì chọn tự nhập.",
        "Mỗi mã chỉ kích hoạt được một hộp. Nếu thợ nối nhầm hộp, nhập lại mã trên hộp đúng được thêm một lần. Thay hộp khi bảo hành: cửa hàng gỡ hộp cũ trên hệ thống rồi khách nhập lại mã trên hộp mới.",
        "Xóa app rồi cài lại, hoặc đổi máy: chỉ cần nối lại hộp khi có mạng, không cần nhập mã.",
        "Mã đại lý (8 ký tự) dành cho chủ và đại lý cấp cao: nhập ở mục Mã của tôi dài hơn 4 ký tự. Dùng được với mọi hộp đèn, không hỏi thông tin khách, không tính bảo hành. Mã có thể giới hạn số lần dùng và ngày hết hạn; hết hạn hoặc bị khóa thì app tự về chưa kích hoạt khi có mạng.",
        "Xem bảo hành: Cài đặt, thẻ Bảo hành hiện ngày kích hoạt và số ngày còn bảo hành. Máy dùng mã đại lý thì thẻ hiện tên người dùng mã và ngày dùng đến (nếu có).",
        "Nếu app báo mã đã hết lượt kích hoạt, hết hạn hoặc mã bị khóa, gọi hotline 0868 055 555."
    ),
    "13. Chạy nền và tiết kiệm pin" to listOf(
        "Cài đặt, thẻ Bạn đang dùng app trên: chọn Điện thoại hoặc Màn hình xe / Android box. Chưa chọn thì app chạy nền như màn hình xe.",
        "Điện thoại: chỉ nối hộp khi mở app. Rời khỏi app khoảng 1 phút thì app ngắt kết nối để đỡ tốn pin. Các hiệu ứng do app vẽ (từ Cầu vồng mượt trở đi) dừng khi đóng app; hiệu ứng có sẵn trong hộp vẫn chạy.",
        "Màn hình xe hoặc Android box: app chạy nền, hiện một thông báo cố định, tự nối hộp khi bật máy và nối lại khi mất kết nối. Hộp tắt hoặc ra xa thì app thử nối lại thưa dần để đỡ tốn pin.",
        "Nút nổi, báo đèn khi sắp rẽ và cảnh báo quá tốc độ cần app chạy nền; bật các tính năng này thì app giữ chạy nền cả trên điện thoại.",
        "Buộc dừng ứng dụng trong Thông tin ứng dụng sẽ tắt hẳn app và hiệu ứng. Android không cho app tự chạy lại; hãy mở lại app. Vuốt app khỏi danh sách gần đây thì thường không tắt dịch vụ nền, nhưng một số màn hình xe tự tắt."
    )
)

// Ảnh minh họa theo mục (chỉ số mục bắt đầu từ 0). Ảnh chụp ở màn hình ngang; số đỏ trong ảnh khớp với chú thích.
private class Pic(val res: Int, val cap: String)

private val PICS: Map<Int, List<Pic>> = mapOf(
    0 to listOf(Pic(R.drawable.man_chon_lai, "Cài đặt, thẻ Thông tin thiết bị: (1) Chọn lại để chọn hộp đèn khác.")),
    2 to listOf(Pic(R.drawable.man_che_do_cua_toi, "Trang Đèn, mục Chế độ của tôi: (1) Tạo mới.")),
    3 to listOf(Pic(R.drawable.man_so_do_xe, "Trang Chào mừng, Sơ đồ xe: (1) chạm Hộp phụ, (2) chạm hình xe có cửa đang nháy.")),
    5 to listOf(
        Pic(R.drawable.man_hud_1, "Màn hình chờ kiểu đồng hồ và tốc độ."),
        Pic(R.drawable.man_hud_2, "Màn hình chờ kiểu dẫn đường. Bấm nút đổi kiểu ở góc trên để chuyển.")
    ),
    6 to listOf(
        Pic(R.drawable.man_mic, "(1) Nút micro. Khi thanh Mời nói hiện ra, hãy nói câu lệnh."),
        Pic(R.drawable.man_tro_ly, "Cài đặt, Trợ lý SK Light: (1) chọn giọng, (2) Xem các câu lệnh.")
    ),
    7 to listOf(Pic(R.drawable.man_ung_dung, "Cài đặt, mục Ứng dụng: (1) Cài đặt giao diện, (2) Cài đặt ngôn ngữ, (3) Cẩm nang sử dụng.")),
    8 to listOf(
        Pic(R.drawable.man_lap_dat, "Cài đặt, mục Lắp đặt: (1) Trợ lý SK Light, (2) Hướng ánh sáng chạy, (3) Ghép nối hộp phụ, (4) Kiểm tra cập nhật."),
        Pic(R.drawable.man_ghep_hop_phu, "Ghép nối hộp phụ: (1) Bắt đầu ghép nối.")
    ),
    11 to listOf(Pic(R.drawable.man_bao_hanh, "Cài đặt, thẻ Bảo hành: (1) Điền thông tin bảo hành."))
)

@Composable
private fun PicBlock(p: Pic) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp)) {
        Image(
            painterResource(p.res), p.cap,
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(BorderStroke(1.dp, Grey.copy(alpha = 0.35f)), RoundedCornerShape(10.dp)),
            contentScale = ContentScale.FillWidth
        )
        Spacer(Modifier.height(4.dp))
        T(p.cap, 12, color = Grey)
    }
}

@Composable
fun ManualPage() {
    var open by S.manualOpen
    BackRow(0, "Cài đặt")
    Card {
        GroupTitle(Icons.Filled.MenuBook, "Cẩm nang sử dụng", 16)
        Spacer(Modifier.height(6.dp))
        T("Chạm một mục để mở. Cẩm nang viết theo bản SK Light ${S.VER}.", 12, color = Grey)
    }
    MANUAL.forEachIndexed { i, (title, lines) ->
        Card {
            Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable { open = if (open == i) -1 else i }, verticalAlignment = Alignment.CenterVertically) {
                T(title, 15, FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(if (open == i) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = SubTxt, modifier = Modifier.size(22.dp))
            }
            if (open == i) {
                Spacer(Modifier.height(6.dp))
                for (l in lines) Row(Modifier.padding(bottom = 7.dp)) {
                    T("•", 13, FontWeight.Bold, Pink, Modifier.width(14.dp))
                    T(l, 13, color = SubTxt, modifier = Modifier.weight(1f))
                }
                PICS[i]?.forEach { PicBlock(it) }
                if (i == 6) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Divider(4.dp, 6.dp); CmdList(); Divider(4.dp, 6.dp); CmdListApp() }
            }
        }
    }
}
