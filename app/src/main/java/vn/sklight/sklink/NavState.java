package vn.sklight.sklink;

import android.graphics.Bitmap;

/** Trang thai chi duong doc duoc tu mot thong bao. */
final class NavState {
    boolean on;              // dang chi duong
    char dir = '?';          // L trai, R phai, S thang, U quay dau, O vong xuyen, A den noi, ? chua ro
    int distM = -1;          // khoang cach toi cho re (met), -1 = khong ro
    String street = "";
    String eta = "";
    String pkg = "";
    String iconHex = "";     // bieu tuong 32x32 diem, 1 bit/diem, dang hex (256 ky tu)
    String dirFrom = "";     // huong lay tu "chu" hay "hinh"
    long time;
    // du lieu tho de hien tren man hinh kiem tra
    String title = "", text = "", sub = "", big = "";
    Bitmap icon;
}
