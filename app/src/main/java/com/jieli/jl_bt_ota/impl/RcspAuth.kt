package com.jieli.jl_bt_ota.impl

/**
 * Cầu nối tới thư viện xác thực của chip Bluetooth JieLi (libjl_ota_auth.so).
 * Thư viện lấy từ SDK công khai của JieLi: github.com/Jieli-Tech/Android-JL_OTA (jl_bt_ota V1.11.0, giấy phép Apache-2.0,
 * bản giấy phép ở assets/jieli-ota-LICENSE.txt).
 * PHẢI giữ đúng tên gói, tên lớp và tên 4 hàm dưới đây: thư viện tự đăng ký hàm theo các tên này lúc nạp.
 */
class RcspAuth {
    external fun nativeInit(): Boolean
    external fun getRandomAuthData(): ByteArray?
    external fun setLinkKey(key: ByteArray?): Int
    external fun getEncryptedAuthData(data: ByteArray?): ByteArray?

    companion object {
        @Volatile private var state = 0          // 0 chưa nạp, 1 nạp được, 2 lỗi
        @Volatile var error = ""
        /** Nạp thư viện một lần; máy không có bản thư viện hợp kiến trúc thì trả null. */
        fun open(): RcspAuth? {
            if (state == 0) synchronized(this) {
                if (state == 0) state = try { System.loadLibrary("jl_ota_auth"); 1 } catch (t: Throwable) { error = t.toString(); 2 }
            }
            if (state != 1) return null
            return try { RcspAuth().also { it.nativeInit() } } catch (t: Throwable) { error = t.toString(); null }
        }
    }
}
