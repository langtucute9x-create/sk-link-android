package vn.sklight.sklink;

import android.app.Notification;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RemoteViews;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Doc thong bao cua ung dung ban do -> huong re, khoang cach, ten duong. */
final class NavParser {
    private static final Pattern DIST = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(km|mi|ft|yd|m)(?![\\p{L}])", Pattern.CASE_INSENSITIVE);
    private static final Pattern EN_LEFT = Pattern.compile("\\bleft\\b");
    private static final Pattern EN_RIGHT = Pattern.compile("\\bright\\b");
    private static final Locale VI = new Locale("vi");

    static NavState parse(Context ctx, String pkg, Notification n) {
        NavState s = new NavState();
        s.pkg = pkg;
        s.time = System.currentTimeMillis();
        Bundle ex = n.extras;
        if (ex != null) {
            s.title = str(ex.getCharSequence(Notification.EXTRA_TITLE));
            s.text = str(ex.getCharSequence(Notification.EXTRA_TEXT));
            s.sub = str(ex.getCharSequence(Notification.EXTRA_SUB_TEXT));
            s.big = str(ex.getCharSequence(Notification.EXTRA_BIG_TEXT));
            if (s.sub.isEmpty()) s.sub = str(ex.getCharSequence(Notification.EXTRA_INFO_TEXT));
        }
        s.icon = largeIcon(ctx, n, ex);

        // Mot so ung dung ve thong bao bang giao dien rieng: tieu de/noi dung chuan bi rong -> doc chu ngay trong giao dien do
        if (findDist(s.title) < 0 && findDist(s.text) < 0 && findDist(s.big) < 0) readCustomViews(ctx, n, s);

        int d = findDist(s.title);
        String rest = s.text;
        if (d < 0) { d = findDist(s.text); rest = s.title; }
        if (d < 0) d = findDist(s.big);
        s.distM = d;
        s.on = d >= 0;
        s.street = clean(rest.isEmpty() ? s.big : rest);
        s.eta = clean(s.sub);

        String all = (s.title + " \n " + s.text + " \n " + s.big).toLowerCase(VI);
        // "Đến nơi lúc 22:16" / "Arrive at ..." la gio den (ETA), khong phai da toi noi
        all = all.replaceAll("đến nơi (lúc|vào)[^\\n·]*", " ").replaceAll("arriv(e|al|ing) (at|by)[^\\n·]*", " ");
        char c = dirFromText(all);
        if (c != '?') { s.dir = c; s.dirFrom = "chữ"; }
        if (s.icon != null) {
            char ci = analyseIcon(s.icon, s);
            if (s.dir == '?' && ci != '?') { s.dir = ci; s.dirFrom = "hình"; }
        }
        // Thong bao luc xuat phat ("Huong ve <duong>") chua co khoang cach nhung van la dang chi duong
        if (!s.on && s.dir != '?' && NavListener.isMapApp(pkg)) {
            s.on = true;
            String st = rest.isEmpty() ? s.big : rest;
            String low = st.toLowerCase(VI);
            for (String pre : new String[]{"hướng về ", "đi về ", "head toward ", "head to ", "head "}) {
                if (low.startsWith(pre)) { st = st.substring(pre.length()); break; }
            }
            s.street = clean(st);
        }
        return s;
    }

    private static String str(CharSequence c) { return c == null ? "" : c.toString().trim(); }

    private static String clean(String t) {
        t = t.replace('\n', ' ').replace('\t', ' ').trim();
        return t.length() > 70 ? t.substring(0, 70) : t;
    }

    /** Khoang cach dau tien trong chuoi, doi ra met; -1 neu khong co. */
    static int findDist(String t) {
        if (t == null || t.isEmpty()) return -1;
        Matcher m = DIST.matcher(t);
        if (!m.find()) return -1;
        double v;
        try { v = Double.parseDouble(m.group(1).replace(',', '.')); } catch (NumberFormatException e) { return -1; }
        String u = m.group(2).toLowerCase(Locale.US);
        if (u.equals("km")) v *= 1000; else if (u.equals("mi")) v *= 1609.34; else if (u.equals("ft")) v *= 0.3048; else if (u.equals("yd")) v *= 0.9144;
        return (int) Math.round(v);
    }

    static char dirFromText(String low) {
        if (low.contains("quay đầu") || low.contains("quay xe") || low.contains("u-turn") || low.contains("uturn")) return 'U';
        if (low.contains("vòng xuyến") || low.contains("bùng binh") || low.contains("vòng xoay") || low.contains("roundabout")) return 'O';
        int l = first(low.indexOf("trái"), find(EN_LEFT, low));
        int r = first(low.indexOf("phải"), find(EN_RIGHT, low));
        if (l >= 0 && (r < 0 || l < r)) return 'L';
        if (r >= 0) return 'R';
        if (low.contains("hướng về") || low.contains("head toward") || low.contains("head to ")) return 'S';
        if (low.contains("đến nơi") || low.contains("điểm đến") || low.contains("arrive") || low.contains("destination")) return 'A';
        if (low.contains("đi thẳng") || low.contains("tiếp tục") || low.contains("straight") || low.contains("continue")) return 'S';
        return '?';
    }

    private static int find(Pattern p, String s) { Matcher m = p.matcher(s); return m.find() ? m.start() : -1; }
    private static int first(int a, int b) { if (a < 0) return b; if (b < 0) return a; return Math.min(a, b); }

    private static Bitmap largeIcon(Context ctx, Notification n, Bundle ex) {
        try {
            Drawable d = null;
            if (Build.VERSION.SDK_INT >= 23) {
                Icon ic = n.getLargeIcon();
                if (ic != null) d = ic.loadDrawable(ctx);
            }
            if (d == null && ex != null) {
                Object o = ex.get(Notification.EXTRA_LARGE_ICON);
                if (o instanceof Bitmap) return scale((Bitmap) o);
            }
            return d == null ? null : toBitmap(d);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Bitmap scale(Bitmap b) { return Bitmap.createScaledBitmap(b, 64, 64, true); }

    private static Bitmap toBitmap(Drawable d) {
        if (d instanceof BitmapDrawable && ((BitmapDrawable) d).getBitmap() != null) return scale(((BitmapDrawable) d).getBitmap());
        Bitmap b = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        d.setBounds(0, 0, 64, 64);
        d.draw(c);
        return b;
    }

    /** Dung lai giao dien rieng cua thong bao de lay chu va hinh mui ten. */
    private static void readCustomViews(Context ctx, Notification n, NavState s) {
        try {
            RemoteViews rv = n.bigContentView != null ? n.bigContentView : n.contentView;
            if (rv == null && Build.VERSION.SDK_INT >= 24) {
                Notification.Builder b = Notification.Builder.recoverBuilder(ctx, n);
                rv = b.createBigContentView();
                if (rv == null) rv = b.createContentView();
            }
            if (rv == null) return;
            View v = rv.apply(ctx.getApplicationContext(), new FrameLayout(ctx));
            List<String> texts = new ArrayList<>();
            Bitmap[] best = new Bitmap[1];
            walk(v, texts, best);
            String distLine = null;
            StringBuilder others = new StringBuilder();
            for (String t : texts) {
                if (distLine == null && findDist(t) >= 0) distLine = t;
                else { if (others.length() > 0) others.append(" · "); others.append(t); }
            }
            if (distLine != null) {
                if (s.title.isEmpty()) s.title = distLine; else s.big = distLine + " " + s.big;
                if (s.text.isEmpty()) s.text = others.toString();
            } else if (s.big.isEmpty()) {
                s.big = others.toString();
            }
            if (s.icon == null && best[0] != null) s.icon = scale(best[0]);
        } catch (Throwable ignored) {
        }
    }

    private static void walk(View v, List<String> texts, Bitmap[] best) {
        if (v instanceof TextView) {
            String t = str(((TextView) v).getText());
            if (!t.isEmpty() && v.getVisibility() == View.VISIBLE) texts.add(t);
        } else if (v instanceof ImageView) {
            Drawable d = ((ImageView) v).getDrawable();
            if (d instanceof BitmapDrawable) {
                Bitmap b = ((BitmapDrawable) d).getBitmap();
                if (b != null && (best[0] == null || b.getWidth() * b.getHeight() > best[0].getWidth() * best[0].getHeight())) best[0] = b;
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) walk(g.getChildAt(i), texts, best);
        }
    }

    /**
     * Doan huong tu hinh mui ten: so sanh vi tri phan tren (dau mui ten) voi phan duoi (than mui ten).
     * Dong thoi tao ban 32x32 diem gui sang HUD de ve lai dung bieu tuong.
     */
    static char analyseIcon(Bitmap b, NavState s) {
        final int W = 64, H = 64;
        int[] px = new int[W * H];
        b.getPixels(px, 0, W, 0, 0, W, H);
        boolean[] fg = new boolean[W * H];
        int opaque = 0;
        for (int p : px) if ((p >>> 24) > 96) opaque++;
        boolean byAlpha = opaque < W * H * 9 / 10;
        int bgL = lum(px[0]);
        int count = 0, x0 = W, x1 = -1, y0 = H, y1 = -1;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            int p = px[y * W + x];
            boolean f = byAlpha ? (p >>> 24) > 96 : Math.abs(lum(p) - bgL) > 60;
            fg[y * W + x] = f;
            if (f) { count++; if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y; }
        }
        // ban 32x32
        StringBuilder hex = new StringBuilder(256);
        for (int y = 0; y < 32; y++) {
            for (int nib = 0; nib < 8; nib++) {
                int v = 0;
                for (int k = 0; k < 4; k++) {
                    int x = nib * 4 + k;
                    boolean on = fg[(2 * y) * W + 2 * x] || fg[(2 * y) * W + 2 * x + 1] || fg[(2 * y + 1) * W + 2 * x] || fg[(2 * y + 1) * W + 2 * x + 1];
                    v = (v << 1) | (on ? 1 : 0);
                }
                hex.append(Character.forDigit(v, 16));
            }
        }
        s.iconHex = hex.toString();
        int bw = x1 - x0 + 1, bh = y1 - y0 + 1;
        if (count < 40 || bw < 8 || bh < 8) return '?';
        int topEnd = y0 + bh * 4 / 10, botStart = y0 + bh * 6 / 10;
        long sxT = 0, nT = 0, sxB = 0, nB = 0;
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) {
            if (!fg[y * W + x]) continue;
            if (y <= topEnd) { sxT += x; nT++; } else if (y >= botStart) { sxB += x; nB++; }
        }
        if (nT == 0 || nB == 0) return '?';
        double diff = ((double) sxT / nT - (double) sxB / nB) / bw;
        if (diff < -0.12) return 'L';
        if (diff > 0.12) return 'R';
        return 'S';
    }

    private static int lum(int p) { return (((p >> 16) & 255) * 3 + ((p >> 8) & 255) * 6 + (p & 255)) / 10; }

    private NavParser() {}
}
