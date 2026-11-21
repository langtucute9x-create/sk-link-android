# SK Light (Android)
App tự làm để điều khiển đèn nội thất xe qua BLE (hộp CTH2/CZH2), thay app của nhà cung cấp.
- Đèn: bật/tắt, độ sáng 2 khu, bánh xe màu, tốc độ, 26 hiệu ứng.
- Hàm gốc + đèn chào mừng (khối 05, chỉ hộp CZH2).
- Dẫn đường: đọc thông báo Google Maps/Vietmap, nháy đèn bên rẽ khi còn ≤150 m; nháy đỏ khi quá tốc độ (GPS so với giới hạn nhập tay).
Build: GitHub Actions → Releases → latest → sk-light.apk. Khóa ký tùy chọn: secrets KEYSTORE_B64 + KEYSTORE_PASS (alias `sklink`).
