# SK Link (Android)

Ứng dụng nhỏ cài trên Android box / màn hình Android trên ô tô / điện thoại Android.
Nó đọc thông báo chỉ đường của ứng dụng bản đồ (Google Maps, Waze, Vietmap...) rồi gửi hướng rẽ,
khoảng cách và tên đường sang HUD SK qua mạng WiFi nội bộ (UDP cổng 4210).

- Không cần Bluetooth. HUD và thiết bị Android phải ở cùng một mạng WiFi.
- Chỉ đọc thông báo của ứng dụng bản đồ; chỉ gửi trong mạng nội bộ, không gửi lên internet.
- Yêu cầu Android 5.0 trở lên và quyền "Truy cập thông báo".

## Tải APK
Mỗi lần đẩy mã lên nhánh `main`, GitHub Actions tự build. File nằm ở mục **Releases → latest → sk-link.apk**.

## Gói tin gửi sang HUD
Một dòng chữ UTF-8, các trường cách nhau bằng dấu TAB:

    SKNAV1	on=1	dir=L	dist=120	st=Nguyễn Huệ	eta=...	seq=12	ic=<256 ký tự hex>

`dir`: L trái, R phải, S thẳng, U quay đầu, O vòng xuyến, A đến nơi, ? chưa rõ.
`spd` / `lim` (tuỳ chọn): tốc độ hiện tại và giới hạn tốc độ (km/h); HUD nháy đỏ viền khi spd > lim. Bản app hiện tại chưa gửi hai trường này (thông báo bản đồ không có), script `tools/nav_sim.py` của HUD có gửi để thử.
`ic`: biểu tượng mũi tên 32x32 điểm, 1 bit mỗi điểm. Lặp lại mỗi 1,5 giây khi đang chỉ đường; `on=0` khi kết thúc.
HUD trả lời `SKHUD1<TAB>tên` về địa chỉ gửi để ứng dụng biết HUD đang ở đâu.
