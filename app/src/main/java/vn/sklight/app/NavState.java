package vn.sklight.app;

import android.graphics.Bitmap;

/** Trang thai chi duong doc duoc tu mot thong bao. */
public final class NavState {
    public boolean on;              // dang chi duong
    public char dir = '?';          // L trai, R phai, S thang, U quay dau, O vong xuyen, A den noi, ? chua ro
    public int distM = -1;          // khoang cach toi cho re (met), -1 = khong ro
    public String street = "";
    public String eta = "";
    String pkg = "";
    String iconHex = "";     // bieu tuong 32x32 diem, 1 bit/diem, dang hex (256 ky tu)
    String dirFrom = "";     // huong lay tu "chu" hay "hinh"
    long time;
    // du lieu tho de hien tren man hinh kiem tra
    String title = "", text = "", sub = "", big = "";
    Bitmap icon;
}
