package vn.sklight.sklink;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Man hinh cai dat va kiem tra. Giao dien dung bang ma, chu to de bam tren man hinh o to. */
public class MainActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextView tvPerm, tvHud, tvNav, tvRaw;
    private ImageView ivIcon;
    private EditText etIp;
    private final SimpleDateFormat hm = new SimpleDateFormat("HH:mm:ss", Locale.US);

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private TextView label(LinearLayout root, String t, float sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(sp);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setPadding(0, dp(6), 0, dp(6));
        root.addView(v);
        return v;
    }

    private Button button(LinearLayout root, String t, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(t);
        b.setTextSize(16);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        lp.setMargins(dp(3), dp(3), dp(3), dp(3));
        root.addView(b, lp);
        return b;
    }

    private LinearLayout row(LinearLayout root) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(r, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return r;
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        final int FG = Color.rgb(233, 237, 244), MUTED = Color.rgb(150, 160, 180), ACC = Color.rgb(56, 198, 255);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.rgb(11, 14, 20));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(14), dp(18), dp(24));
        sv.addView(root);
        setContentView(sv);

        label(root, "SK Link · gửi chỉ đường sang HUD", 22, FG, true);

        label(root, "1. Quyền đọc thông báo", 17, ACC, true);
        tvPerm = label(root, "", 16, FG, false);
        LinearLayout r1 = row(root);
        button(r1, "Mở cài đặt quyền đọc thông báo", new View.OnClickListener() {
            @Override public void onClick(View v) { openListenerSettings(); }
        });

        label(root, "2. Kết nối HUD (cùng mạng WiFi)", 17, ACC, true);
        tvHud = label(root, "", 16, FG, false);
        LinearLayout r2 = row(root);
        etIp = new EditText(this);
        etIp.setHint("Địa chỉ IP của HUD (để trống = tự tìm)");
        etIp.setInputType(InputType.TYPE_CLASS_PHONE);
        etIp.setTextColor(FG);
        etIp.setHintTextColor(MUTED);
        etIp.setText(Prefs.manualIp(this));
        r2.addView(etIp, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2));
        button(r2, "Lưu", new View.OnClickListener() {
            @Override public void onClick(View v) {
                Prefs.setManualIp(MainActivity.this, etIp.getText().toString());
                HudLink.get(MainActivity.this).ping();
                Toast.makeText(MainActivity.this, "Đã lưu", Toast.LENGTH_SHORT).show();
            }
        });

        label(root, "3. Gửi thử (không cần mở bản đồ)", 17, ACC, true);
        LinearLayout r3 = row(root);
        button(r3, "↰ Rẽ trái 100 m", new View.OnClickListener() {
            @Override public void onClick(View v) { HudLink.get(MainActivity.this).test('L', 100); }
        });
        button(r3, "↱ Rẽ phải 100 m", new View.OnClickListener() {
            @Override public void onClick(View v) { HudLink.get(MainActivity.this).test('R', 100); }
        });
        button(r3, "Kết thúc", new View.OnClickListener() {
            @Override public void onClick(View v) { HudLink.get(MainActivity.this).off(); }
        });

        label(root, "4. Đang đọc được gì từ ứng dụng bản đồ", 17, ACC, true);
        LinearLayout r4 = row(root);
        ivIcon = new ImageView(this);
        ivIcon.setBackgroundColor(Color.rgb(30, 38, 54));
        r4.addView(ivIcon, new LinearLayout.LayoutParams(dp(72), dp(72)));
        tvNav = new TextView(this);
        tvNav.setTextSize(18);
        tvNav.setTextColor(FG);
        tvNav.setPadding(dp(12), 0, 0, 0);
        r4.addView(tvNav, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        tvRaw = label(root, "", 13, MUTED, false);

        CheckBox cb = new CheckBox(this);
        cb.setText("Đọc thông báo của mọi ứng dụng (chỉ bật khi ứng dụng bản đồ của bạn không được nhận ra)");
        cb.setTextColor(FG);
        cb.setTextSize(15);
        cb.setChecked(Prefs.allApps(this));
        cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton c, boolean on) { Prefs.setAllApps(MainActivity.this, on); }
        });
        root.addView(cb);

        label(root, "Cách dùng: cấp quyền ở mục 1, cho HUD và thiết bị này vào cùng một mạng WiFi, rồi mở ứng dụng bản đồ và bấm Bắt đầu. "
                + "Ứng dụng này tự chạy nền, không cần mở lại. Ứng dụng chỉ đọc thông báo của ứng dụng bản đồ và chỉ gửi trong mạng nội bộ, không gửi lên internet.", 14, MUTED, false);
    }

    private void openListenerSettings() {
        try {
            startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
        } catch (Exception e) {
            Toast.makeText(this, "Thiết bị này không có màn hình cấp quyền đọc thông báo", Toast.LENGTH_LONG).show();
        }
    }

    private boolean hasAccess() {
        String s = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return s != null && s.contains(getPackageName());
    }

    private static String dirName(char d) {
        switch (d) {
            case 'L': return "↰ Rẽ trái";
            case 'R': return "↱ Rẽ phải";
            case 'S': return "↑ Đi thẳng";
            case 'U': return "↶ Quay đầu";
            case 'O': return "⟳ Vòng xuyến";
            case 'A': return "⚑ Đến nơi";
            default: return "? Chưa rõ hướng";
        }
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            refresh();
            ui.postDelayed(this, 700);
        }
    };

    private void refresh() {
        boolean acc = hasAccess();
        tvPerm.setText(acc ? (NavListener.connected ? "ĐÃ BẬT · đang chạy" : "ĐÃ BẬT · đang chờ hệ thống khởi động dịch vụ") : "CHƯA BẬT · bấm nút bên dưới rồi bật SK Link");
        tvPerm.setTextColor(acc ? Color.rgb(53, 208, 127) : Color.rgb(255, 176, 46));

        HudLink h = HudLink.get(this);
        long now = System.currentTimeMillis();
        String hud;
        if (h.hudAddr != null && now - h.hudSeen < 8000) hud = "ĐÃ THẤY HUD tại " + h.hudAddr.getHostAddress() + (h.hudName.isEmpty() ? "" : " (" + h.hudName + ")");
        else if (h.hudAddr != null) hud = "Mất liên lạc với HUD (lần cuối " + hm.format(new Date(h.hudSeen)) + ")";
        else hud = "Chưa thấy HUD";
        if (!h.lastError.isEmpty()) hud += "\nLỗi gửi: " + h.lastError;
        hud += "\nĐã gửi " + h.sentCount + " gói" + (h.lastSent > 0 ? ", gần nhất " + hm.format(new Date(h.lastSent)) : "");
        tvHud.setText(hud);
        tvHud.setTextColor(h.hudAddr != null && now - h.hudSeen < 8000 ? Color.rgb(53, 208, 127) : Color.rgb(233, 237, 244));

        NavState n = NavListener.lastNav, r = NavListener.lastRaw;
        if (n != null && n.on && now - n.time < 120000) {
            tvNav.setText(dirName(n.dir) + (n.distM >= 0 ? " sau " + n.distM + " m" : "") + "\n" + n.street + (n.dirFrom.isEmpty() ? "" : "\n(hướng lấy từ " + n.dirFrom + ")"));
        } else {
            tvNav.setText("Chưa có chỉ đường");
        }
        if (r != null) {
            ivIcon.setImageBitmap(r.icon);
            tvRaw.setText("Thông báo gần nhất lúc " + hm.format(new Date(r.time)) + " từ " + r.pkg
                    + "\nTiêu đề: " + r.title + "\nNội dung: " + r.text + "\nDòng phụ: " + r.sub + (r.big.isEmpty() ? "" : "\nMở rộng: " + r.big));
        } else {
            tvRaw.setText("Chưa nhận được thông báo nào từ ứng dụng bản đồ."
                    + (NavListener.lastPkgs.isEmpty() ? "" : "\nỨng dụng vừa có thông báo: " + NavListener.lastPkgs));
        }
    }

    @Override protected void onResume() {
        super.onResume();
        HudLink.get(this).ping();
        ui.post(tick);
    }

    @Override protected void onPause() {
        super.onPause();
        ui.removeCallbacks(tick);
    }
}
