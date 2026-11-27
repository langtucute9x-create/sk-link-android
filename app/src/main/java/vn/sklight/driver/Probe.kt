package vn.sklight.driver

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

/** Mạng Wi-Fi đang nối. Wi-Fi của camera không có internet nên Android hay đẩy kết nối sang 4G: mọi kết nối tới camera phải ép đi qua Wi-Fi. */
object Net {
    fun wifi(c: Context): Network? = try {
        val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        @Suppress("DEPRECATION")
        cm.allNetworks.firstOrNull { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }
    } catch (_: Throwable) { null }

    /** Địa chỉ của camera (cổng mạng của Wi-Fi đang nối), ví dụ 192.168.1.254. */
    fun gateway(c: Context): String? = try {
        val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val lp = wifi(c)?.let { cm.getLinkProperties(it) }
        lp?.routes?.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }?.gateway?.hostAddress
            ?: lp?.linkAddresses?.firstOrNull { it.address is Inet4Address }?.address?.hostAddress?.substringBeforeLast('.')?.plus(".1")
    } catch (_: Throwable) { null }
}

/**
 * Địa chỉ luồng hình của các dòng camera hành trình thông dụng (theo cách app xem camera của hãng kết nối) và việc tự dò.
 * src: 1 MJPEG, 2 RTSP.
 */
object Probe {
    class Preset(val name: String, val src: Int, val url: String)

    val LIST = listOf(
        Preset("Novatek (192.168.1.254), RTSP", 2, "rtsp://192.168.1.254/xxx.mov"),
        Preset("Novatek (192.168.1.254), MJPEG", 1, "http://192.168.1.254:8192"),
        Preset("MStar / SigmaStar (192.168.1.1), camera trước", 2, "rtsp://192.168.1.1:554/264_rt/XXX.sd"),
        Preset("MStar / SigmaStar (192.168.1.1), camera sau", 2, "rtsp://192.168.1.1:554/264_rt/rear.sd"),
        Preset("AIT / iCatch (192.72.1.1)", 2, "rtsp://192.72.1.1/liveRTSP/av4"),
        Preset("HiSilicon (192.168.0.1), kênh 12 (đoán)", 2, "rtsp://192.168.0.1:554/livestream/12"),
        Preset("HiSilicon (192.168.0.1), kênh 11 (đoán)", 2, "rtsp://192.168.0.1:554/livestream/11")
    )

    /** Kết quả: preset tìm được (null nếu không), kèm câu mô tả để hiện cho người dùng. */
    class Result(val hit: Preset?, val msg: String)

    @Volatile var busy = false

    fun run(c: Context, progress: (String) -> Unit): Result {
        busy = true
        try {
            val net = Net.wifi(c) ?: return Result(null, "Máy chưa nối Wi-Fi. Nối vào Wi-Fi của camera rồi dò lại.")
            val gw = Net.gateway(c)
            val cands = ArrayList<Preset>()
            // địa chỉ camera thật (cổng mạng Wi-Fi) thử trước với mọi kiểu đường dẫn, rồi tới các địa chỉ cố định của từng dòng
            if (gw != null) for (p in LIST) if (!p.name.contains("camera sau")) {
                val u = Uri.parse(p.url)
                cands.add(Preset("Camera ở $gw", p.src, p.url.replace(u.host ?: "", gw)))
            }
            for (p in LIST) if (!p.name.contains("camera sau") && cands.none { it.url == p.url }) cands.add(p)
            val dead = HashSet<String>() // máy chủ:cổng không mở, khỏi thử lại
            var refused = ""
            for ((i, p) in cands.withIndex()) {
                val u = Uri.parse(p.url)
                val host = u.host ?: continue
                val port = if (u.port > 0) u.port else if (p.src == 2) 554 else 80
                progress("Đang dò ${i + 1}/${cands.size}: $host")
                if ("$host:$port" in dead) continue
                val r = if (p.src == 2) rtsp(net, host, port, p.url) else mjpeg(net, p.url)
                if (r == 1) return Result(p, "Đã tìm thấy camera: ${p.url}")
                if (r < 0) dead.add("$host:$port") else if (refused.isEmpty()) refused = "$host:$port"
            }
            return Result(null, if (refused.isNotEmpty()) "Thấy camera ở $refused nhưng chưa địa chỉ hình nào đúng. Gửi tên camera cho SK để bổ sung."
                else "Không thấy camera nào" + (if (gw != null) " (địa chỉ Wi-Fi: $gw)" else "") + ". Kiểm tra máy đã nối đúng Wi-Fi của camera chưa.")
        } catch (e: Throwable) { return Result(null, "Lỗi khi dò: ${e.javaClass.simpleName}") } finally { busy = false }
    }

    /** Hỏi camera bằng lệnh RTSP DESCRIBE. 1 = có luồng hình, 0 = camera trả lời nhưng không có đường dẫn này, -1 = không nối được. */
    private fun rtsp(net: Network, host: String, port: Int, url: String): Int {
        var s: Socket? = null
        try {
            s = net.socketFactory.createSocket()
            s.connect(InetSocketAddress(host, port), 900)
            s.soTimeout = 2000
            s.getOutputStream().write("DESCRIBE $url RTSP/1.0\r\nCSeq: 1\r\nUser-Agent: SKDriver\r\nAccept: application/sdp\r\n\r\n".toByteArray())
            val buf = ByteArray(64)
            val n = s.getInputStream().read(buf)
            if (n <= 0) return 0
            val line = String(buf, 0, n).substringBefore('\r')
            return if (line.startsWith("RTSP/") && line.contains(" 200")) 1 else 0
        } catch (_: java.net.SocketTimeoutException) { return if (s?.isConnected == true) 0 else -1 } catch (_: Throwable) { return -1 } finally { try { s?.close() } catch (_: Throwable) {} }
    }

    private fun mjpeg(net: Network, url: String): Int {
        var c: HttpURLConnection? = null
        try {
            c = (net.openConnection(URL(url)) as HttpURLConnection).apply { connectTimeout = 900; readTimeout = 2000 }
            val ct = c.contentType ?: ""
            return if (c.responseCode == 200 && (ct.startsWith("multipart") || ct.startsWith("image/"))) 1 else 0
        } catch (_: Throwable) { return -1 } finally { try { c?.disconnect() } catch (_: Throwable) {} }
    }
}
