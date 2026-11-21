package vn.sklight.sklink;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Gui trang thai chi duong sang HUD qua mang WiFi noi bo (UDP cong 4210).
 * Goi tin la mot dong chu UTF-8, cac truong cach nhau bang dau TAB:
 *   SKNAV1 \t on=1 \t dir=L \t dist=120 \t st=Nguyễn Huệ \t eta=... \t seq=12 \t ic=<256 ky tu hex>
 * HUD tra loi "SKHUD1\t<ten>" ve dung dia chi gui -> ung dung biet HUD dang o dau.
 */
final class HudLink {
    static final int PORT = 4210;
    private static HudLink inst;

    static synchronized HudLink get(Context c) {
        if (inst == null) inst = new HudLink(c.getApplicationContext());
        return inst;
    }

    private final Context ctx;
    private final Handler h;
    private DatagramSocket sock;
    private NavState cur;
    private int seq;
    private int offLeft;
    volatile InetAddress hudAddr;
    volatile long hudSeen;
    volatile String hudName = "";
    volatile long lastSent;
    volatile int sentCount;
    volatile String lastError = "";

    private HudLink(Context c) {
        ctx = c;
        HandlerThread t = new HandlerThread("hud");
        t.start();
        h = new Handler(t.getLooper());
        Thread rx = new Thread(new Runnable() { @Override public void run() { receiveLoop(); } }, "hud-rx");
        rx.setDaemon(true);
        rx.start();
    }

    void update(final NavState s) {
        h.post(new Runnable() { @Override public void run() { cur = s; offLeft = 0; h.removeCallbacks(tick); tick.run(); } });
    }

    void off() {
        h.post(new Runnable() { @Override public void run() { cur = null; offLeft = 3; h.removeCallbacks(tick); tick.run(); } });
    }

    /** Gui thu khong can ung dung ban do. */
    void test(char dir, int dist) {
        NavState s = new NavState();
        s.on = true; s.dir = dir; s.distM = dist; s.street = "Đường thử nghiệm"; s.pkg = "test";
        update(s);
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (cur != null && cur.on) {
                send(encode(cur));
                h.postDelayed(this, 1500);        // lap lai de HUD biet con dang chi duong (qua 6 giay khong nhan thi HUD tu tat)
            } else if (offLeft > 0) {
                offLeft--;
                send("SKNAV1\ton=0\tseq=" + (++seq));
                h.postDelayed(this, 300);
            }
        }
    };

    private String encode(NavState s) {
        StringBuilder b = new StringBuilder(420);
        b.append("SKNAV1\ton=1\tdir=").append(s.dir).append("\tdist=").append(s.distM)
         .append("\tst=").append(s.street.replace('\t', ' '))
         .append("\teta=").append(s.eta.replace('\t', ' '))
         .append("\tseq=").append(++seq);
        if (s.iconHex.length() == 256) b.append("\tic=").append(s.iconHex);
        return b.toString();
    }

    private synchronized DatagramSocket socket() throws Exception {
        if (sock == null || sock.isClosed()) {
            sock = new DatagramSocket();
            sock.setBroadcast(true);
            sock.setSoTimeout(1000);
        }
        return sock;
    }

    private void send(String line) {
        byte[] d = line.getBytes(StandardCharsets.UTF_8);
        Set<InetAddress> to = targets();
        int ok = 0;
        String err = "";
        for (InetAddress a : to) {
            try { socket().send(new DatagramPacket(d, d.length, a, PORT)); ok++; }
            catch (Exception e) { err = String.valueOf(e.getMessage()); }
        }
        lastSent = System.currentTimeMillis();
        sentCount++;
        lastError = ok > 0 ? "" : (to.isEmpty() ? "Không có mạng WiFi" : err);
    }

    /** Dia chi can gui: HUD da biet, dia chi nhap tay, va dia chi phat chung cua moi mang dang noi. */
    private Set<InetAddress> targets() {
        Set<InetAddress> s = new LinkedHashSet<>();
        try {
            InetAddress a = hudAddr;
            if (a != null && System.currentTimeMillis() - hudSeen < 60000) s.add(a);
            String ip = Prefs.manualIp(ctx);
            if (ip.matches("\\d{1,3}(\\.\\d{1,3}){3}")) s.add(InetAddress.getByName(ip));
            Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
            while (en != null && en.hasMoreElements()) {
                NetworkInterface ni = en.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    InetAddress bc = ia.getBroadcast();
                    if (bc != null) s.add(bc);
                }
            }
        } catch (Exception ignored) {
        }
        return s;
    }

    private void receiveLoop() {
        byte[] buf = new byte[256];
        while (true) {
            try {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                socket().receive(p);
                String t = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8);
                if (t.startsWith("SKHUD1")) {
                    hudAddr = p.getAddress();
                    hudSeen = System.currentTimeMillis();
                    String[] f = t.split("\t");
                    hudName = f.length > 1 ? f[1] : "";
                }
            } catch (java.net.SocketTimeoutException e) {
                // het gio cho, lap lai
            } catch (Exception e) {
                try { Thread.sleep(1000); } catch (InterruptedException ie) { return; }
            }
        }
    }

    /** Hoi HUD "co do khong" (dung khi mo man hinh ung dung). */
    void ping() {
        h.post(new Runnable() { @Override public void run() { send("SKNAV1\tping=1\tseq=" + (++seq)); } });
    }
}
