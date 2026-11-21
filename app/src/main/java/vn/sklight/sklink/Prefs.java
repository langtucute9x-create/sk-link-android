package vn.sklight.sklink;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    private static SharedPreferences sp(Context c) { return c.getSharedPreferences("sklink", Context.MODE_PRIVATE); }
    static String manualIp(Context c) { return sp(c).getString("ip", ""); }
    static void setManualIp(Context c, String v) { sp(c).edit().putString("ip", v == null ? "" : v.trim()).apply(); }
    static boolean allApps(Context c) { return sp(c).getBoolean("all", false); }
    static void setAllApps(Context c, boolean v) { sp(c).edit().putBoolean("all", v).apply(); }
    private Prefs() {}
}
