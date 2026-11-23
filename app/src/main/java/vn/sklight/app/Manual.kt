package vn.sklight.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// ===================== CẨM NANG SỬ DỤNG =====================
// Nội dung viết theo đúng những gì app đang có ở bản 1.0.0. Phần "Chức năng theo xe" chưa có vì chưa kiểm chứng xong trên xe thật.
// Thêm tính năng mới thì bổ sung mục tương ứng ở đây.

private val MANUAL: List<Pair<String, List<String>>> = listOf(
    "1. Kết nối với hộp đèn" to listOf(
        "Bật Bluetooth của máy, mở SK Light và đồng ý các quyền app hỏi (Bluetooth, vị trí, thông báo).",
        "Chọn hộp đèn trong danh sách hiện ra. Từ lần sau app tự kết nối với hộp đã chọn.",
        "Ở màn hình dọc, chấm nhỏ ở góc trên bên phải màu xanh là đã kết nối, màu xám là chưa.",
        "Khi mất kết nối, đầu trang Đèn hiện thẻ có hình phích cắm. Chạm thẻ đó để thử nối lại ngay.",
        "Hộp đèn chỉ nhận một kết nối một lúc. Nếu không nối được, hãy tắt app đèn trên điện thoại hoặc màn hình khác.",
        "Muốn đổi sang hộp khác: Cài đặt, thẻ Thông tin thiết bị, bấm Chọn lại."
    ),
    "2. Trang Đèn" to listOf(
        "Nút nguồn bật hoặc tắt toàn bộ đèn.",
        "Đồng nhất, Khu vực 1, Khu vực 2: chọn chỉnh cả xe hay từng khu. Độ sáng và màu chỉnh theo khu đang chọn.",
        "Chọn màu bằng bánh xe màu hoặc các bóng màu mẫu. Bấm dấu cộng để lưu màu đang chọn thành màu riêng; giữ lâu một màu riêng để xóa.",
        "Chọn chế độ màu vùng 2: Màu tĩnh, Màu tự động, Màu nhấp nháy là của hộp; các kiểu còn lại do app điều khiển.",
        "Chạm một ô chế độ để chạy. Chế độ có nhiều kiểu thì hàng kiểu con hiện ngay bên dưới.",
        "Thẻ Hiệu ứng chỉnh Tốc độ nháy và chạy, Độ nhạy nhịp điệu (dùng cho các chế độ nhịp nhạc).",
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
        "Lần đầu, trang Dẫn đường hiện thẻ Cần cấp quyền: cấp quyền đọc thông báo và bật Hỗ trợ tiếp cận cho SK Light. Cấp đủ thì thẻ tự ẩn.",
        "Báo đèn khi sắp rẽ: bật công tắc, chọn khoảng cách Báo trước (50 đến 500 m), chọn một trong ba hiệu ứng và tốc độ của hiệu ứng đó.",
        "Thời gian đèn chạy: chọn số giây đèn báo chạy, hoặc để tới khi rẽ xong.",
        "Nếu rẽ trái mà đèn lại chạy sang phải, bật Đảo chiều chạy.",
        "Thử nháy 4 giây: bấm Rẽ trái, Rẽ phải hoặc Quá tốc độ để xem trước trên đèn.",
        "Nháy đỏ khi quá tốc độ: bật công tắc và chọn khu nháy đỏ. Chỉ hoạt động khi app bản đồ hiện biển giới hạn tốc độ.",
        "Để báo được khi SK Light không mở trên màn hình, hãy bật Tự kết nối khi khởi động máy ở thẻ Khi lái xe."
    ),
    "6. Khi lái xe" to listOf(
        "Tự kết nối khi khởi động máy: màn hình xe bật lên là app tự nối hộp đèn, không cần mở app.",
        "Tự dịu đèn ban đêm: đèn tự giảm sáng khi trời tối, theo giờ mặt trời lặn và mọc hoặc theo giờ tự đặt. Kéo thanh Độ sáng ban đêm để chọn mức.",
        "Màn hình chờ khi xe chạy: tự mở khi công tắc bật, xe chạy từ 10 km/h và 10 giây không chạm màn hình. Xe đứng yên thì không tự mở.",
        "Mở màn hình chờ bằng tay: bấm nút có hình khung ảnh trên thanh menu ở màn hình ngang. Chạm màn hình chờ để quay lại.",
        "Trên màn hình chờ, bấm nút mũi tên vòng để đổi kiểu. Có 11 kiểu, gồm đồng hồ, tốc độ, dẫn đường, nhạc đang phát và ảnh của bạn.",
        "Nút nổi trên màn hình: nút SK hiện trên các ứng dụng khác để bật tắt, chỉnh độ sáng, đổi bối cảnh mà không cần mở app. Cần cấp quyền hiển thị trên ứng dụng khác. Kéo nút để dời chỗ."
    ),
    "7. Trợ lý giọng nói" to listOf(
        "Bấm nút micro rồi nói câu lệnh. Trợ lý chỉ dùng được khi app đang để tiếng Việt, máy có mạng và có app Google.",
        "Ví dụ: “bật đèn”, “tắt đèn”, “màu đỏ”, “sáng hơn”, “tối hơn”, “độ sáng 50”, “chế độ thở”, “chế độ 15”, “tốc độ 8”, “khu vực 2 màu xanh”.",
        "Gọi bối cảnh và chế độ bằng tên của nó, ví dụ “thư giãn”, “thể thao”.",
        "Mỗi ô chế độ có một con số ở góc: nói “chế độ” kèm số đó để chạy.",
        "Cài đặt, mục Trợ lý SK Light (AI): bật tắt trả lời bằng tiếng nói, tắt tiếng báo khi bấm micro, chọn giọng và cách thông báo của hộp, xem đủ các câu lệnh.",
        "Muốn app trả lời bằng tiếng nói, máy cần có giọng đọc tiếng Việt; trang Trợ lý có nút cài."
    ),
    "8. Giao diện và ngôn ngữ" to listOf(
        "Cài đặt, mục Cài đặt giao diện: chọn Sáng, Tối hoặc Tự động; phong cách nền; bo góc; màu nhấn; kiểu chữ và cỡ chữ.",
        "Giảm hiệu ứng: bật nếu máy chạy chậm, giao diện sẽ bỏ khối nổi, bóng đổ và chuyển động.",
        "Màn hình chờ: chọn kiểu và xem trước ngay trong trang này; bấm Chọn ảnh của tôi để dùng ảnh riêng.",
        "Nút nổi và màn hình nổi: chọn một trong sáu kiểu, có hình xem trước.",
        "Vị trí menu khi màn hình ngang: để menu ở trên hoặc thành cột bên trái.",
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
        "Các chức năng theo tín hiệu của xe (xi nhan, cửa, dây an toàn…) đang được hoàn thiện và sẽ có trong bản sau."
    )
)

@Composable
fun ManualPage() {
    var open by remember { mutableIntStateOf(0) }
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
            }
        }
    }
}
