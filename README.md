# SK Light (Android)
App tự làm để điều khiển đèn nội thất xe qua BLE (hộp CTH2 / CZH2), giao diện theo app SK Ambient Light.

- **Đèn**: khu vực, độ sáng, bánh xe màu + màu mẫu + màu tùy chỉnh, tốc độ, độ nhạy nhịp điệu, chế độ màu vùng 2, 26 chế độ với kiểu con.
- **Chào mừng**: bật/tắt, chế độ 1-6 + tùy chỉnh, tốc độ, sơ đồ xe (ghép hộp phụ bằng cách cho nháy, số LED, hướng chạy từng đoạn), vị trí bắt đầu.
- **Cài đặt**: thông tin thiết bị, ngắt kết nối, chọn lại.

Build: GitHub Actions -> Releases -> sk-light.apk. Khóa ký tùy chọn: secrets KEYSTORE_B64 + KEYSTORE_PASS (alias `sklink`).

Lệnh BLE chưa kiểm chứng trên hộp thật: chế độ màu vùng 2 (mode1), mã vị trí khi gán hộp phụ (1-4, Khác = 15), mã vị trí bắt đầu 1 và 2. Công tắc "Chào xung quanh xe" chưa gửi lệnh.
