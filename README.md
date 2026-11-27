# SK Driver – Cảnh báo khoảng cách (bản thử 0.1.0)

App riêng, sau này liên kết với SK Light để báo bằng đèn. Bản 0.1.0 chỉ là bản thử: dùng camera sau của điện thoại,
nhận diện xe phía trước ngay trên máy (MediaPipe Tasks + EfficientDet-Lite0, đều giấy phép Apache-2.0), ước lượng khoảng cách
theo vị trí đáy xe trong hình, độ cao camera và đường chân trời. CHƯA thử trên máy thật, CHƯA điều khiển đèn.

- Nhánh: `sk-driver`. Gói: `vn.sklight.driver`.
- Build: GitHub Actions -> Releases -> thẻ `sk-driver-latest` -> `sk-driver.apk`. Mô hình được tải lúc build (không lưu trong repo).
- Đây là công cụ hỗ trợ tham khảo, không phải hệ thống an toàn.
