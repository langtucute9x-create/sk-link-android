# SK Auto (Android Auto)

App cài trên điện thoại. Cắm cáp Type-C vào cổng USB của xe, mở Android Auto, app hiện một trình duyệt
(YouTube / TikTok bản web) và trình phát video trong máy trên màn hình xe.

Cách làm: app khai báo loại "dẫn đường" (`androidx.car.app.category.NAVIGATION`) để được cấp một bề mặt tự vẽ,
tạo màn hình ảo (VirtualDisplay) xuất ra bề mặt đó, đặt WebView / VideoView lên. Chạm và vuốt từ màn hình xe
(`SurfaceCallback.onClick/onScroll/onFling/onScale`) được dựng lại thành sự kiện chạm.

- Nhánh: `sk-auto`. Gói: `vn.sklight.auto`.
- Build: GitHub Actions -> Releases -> thẻ `sk-auto-latest` -> `sk-auto.apk` (ký bằng khóa `sklink` trong Secrets).
- Cài: bật chế độ nhà phát triển + "Nguồn không xác định" trong Android Auto, và cài APK bằng UnknownAuto (Shizuku)
  để máy ghi nhận là cài từ Google Play; cài thường thì Android Auto không hiện app.

Tệp mã (`app/src/main/java/vn/sklight/auto/`): `AutoService.kt` (dịch vụ Android Auto, các màn hình mẫu),
`Renderer.kt` (màn hình ảo, WebView, video, dựng lại chạm), `Store.kt` (danh sách trang / video, trang chủ),
`MainActivity.kt` (màn hình điện thoại), `BrowserActivity.kt` (đăng nhập trên điện thoại, chung cookie).
