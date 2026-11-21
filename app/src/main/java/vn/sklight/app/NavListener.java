package vn.sklight.app;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

/** Dich vu doc thong bao: loc thong bao cua ung dung ban do roi chuyen sang HUD. */
public class NavListener extends NotificationListenerService {
    private static final String TAG = "SKLight";
    // ten goi ung dung co chua mot trong cac tu nay duoc coi la ung dung ban do
    private static final String[] MAP_HINTS = {"maps", "vietmap", "waze", "navitel", "osmand", "sygic", "navi", "here.app",
            "organicmaps", "magicearth", "tomtom", "goong", "map4d", "mapbox", "amap", "autonavi", "baidu.baidumap", "petal"};

    static volatile boolean connected;
    static volatile NavState lastNav;     // lan chi duong gan nhat (da gui sang HUD)
    static volatile NavState lastRaw;     // thong bao gan nhat cua ung dung duoc theo doi (ke ca khi khong doc duoc khoang cach)
    static volatile String lastPkgs = "";

    private String navKey;

    static boolean isMapApp(String pkg) {
        String p = pkg.toLowerCase(java.util.Locale.US);
        for (String k : MAP_HINTS) if (p.contains(k)) return true;
        return false;
    }

    @Override public void onListenerConnected() {
        connected = true;
        try {
            StatusBarNotification[] a = getActiveNotifications();
            if (a != null) for (StatusBarNotification s : a) handle(s);
        } catch (Throwable t) {
            Log.w(TAG, "active", t);
        }
    }

    @Override public void onListenerDisconnected() { connected = false; }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        try { handle(sbn); } catch (Throwable t) { Log.w(TAG, "posted", t); }
    }

    @Override public void onNotificationRemoved(StatusBarNotification sbn) {
        try {
            if (sbn != null && navKey != null && navKey.equals(sbn.getKey())) {
                navKey = null;
                NavState s = new NavState();
                s.pkg = sbn.getPackageName();
                s.time = System.currentTimeMillis();
                lastNav = s;
                AlertBridge.onNavOff();
            }
        } catch (Throwable t) {
            Log.w(TAG, "removed", t);
        }
    }

    private void handle(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) return;
        connected = true;
        String pkg = sbn.getPackageName();
        if (pkg == null || pkg.equals(getPackageName())) return;
        remember(pkg);
        if (!Prefs.allApps(this) && !isMapApp(pkg)) return;
        NavState s = NavParser.parse(this, pkg, sbn.getNotification());
        lastRaw = s;
        if (!s.on) return;
        navKey = sbn.getKey();
        lastNav = s;
        AlertBridge.onNav(s);
    }

    private static void remember(String pkg) {
        String cur = lastPkgs;
        if (cur.contains(pkg)) return;
        String n = pkg + (cur.isEmpty() ? "" : ", " + cur);
        lastPkgs = n.length() > 400 ? n.substring(0, 400) : n;
    }
}
