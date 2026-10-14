package io.github.fragtion.statusback;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Per-slot toggles for the status icons Samsung hides on the home status bar. */
public final class MainActivity extends Activity {
    // keep in sync with StatusBackModule.KEY / DEFAULT
    static final String KEY = "statusback_restore_slots";
    static final String DEFAULT = "ims_volte,ims_volte2,bluetooth,bluetooth_connected";
    private static final String PERM = "android.permission.WRITE_SECURE_SETTINGS";
    // keep in sync with versionName in app/build.gradle
    private static final String VERSION = "1.0";

    private static final String[][] SLOTS = {
        {"ims_volte", "VoLTE / Wi-Fi calling", "SIM 1 HD-voice / calling-over-Wi-Fi badge."},
        {"ims_volte2", "VoLTE / Wi-Fi calling (SIM 2)", "Same badge for the second SIM."},
        {"bluetooth", "Bluetooth", "Bluetooth is turned on."},
        {"bluetooth_connected", "Bluetooth connected", "A Bluetooth device is connected."},
        {"alarm_clock", "Alarm", "An alarm is set."},
        {"mute", "Silent / vibrate", "Ringer is muted or on vibrate."},
        {"volume", "Sound mode", "Sound-mode indicator (not confirmed on every build)."},
        {"zen", "Do Not Disturb / Modes", "A DND mode is active."},
        {"rotate", "Rotation lock", "Auto-rotate is locked."},
        {"hotspot", "Hotspot", "Mobile hotspot is on."},
        {"bt_tethering", "Bluetooth tethering", "Sharing the connection over Bluetooth."},
        {"data_saver", "Data Saver", "Data Saver is on."},
        {"location", "Location", "An app is using your location."},
        {"cast", "Cast / Smart View", "Screen is being cast."},
        {"screen_record", "Screen recording", "A screen recording is running."},
        {"connected_display", "External display / DeX", "An external display is connected."},
        {"satellite", "Satellite", "Satellite connectivity is active."},
        {"camera", "Camera in use", "An app is using the camera."},
        {"microphone", "Microphone in use", "An app is using the microphone."},
        {"sensors_off", "Sensors off", "The sensors-off toggle is active."},
    };

    private final List<Switch> switches = new ArrayList<>();
    private TextView banner;
    private boolean binding;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (getActionBar() != null) getActionBar().hide();
        setTitle("StatusBack");
        float d = getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * d);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("StatusBack");
        title.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Large);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Show status-bar icons that One UI hides on the home screen. Changes apply instantly."
            + "\nStatusBack v" + VERSION + " - io.github.fragtion.statusback");
        sub.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Small);
        sub.setPadding(0, (int) (4 * d), 0, (int) (12 * d));
        root.addView(sub);

        banner = new TextView(this);
        banner.setText("Needs a one-time permission to save settings.");
        banner.setTextColor(Color.rgb(0xC6, 0x28, 0x28));
        banner.setVisibility(View.GONE);
        root.addView(banner);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        for (String[] s : SLOTS) list.addView(row(s[0], s[1], s[2], d));
        root.addView(list);

        ScrollView sv = new ScrollView(this);
        sv.addView(root, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        sv.setOnApplyWindowInsetsListener((v, ins) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets i = ins.getInsets(WindowInsets.Type.systemBars());
                v.setPadding(i.left, i.top, i.right, i.bottom);
            } else {
                v.setPadding(ins.getSystemWindowInsetLeft(), ins.getSystemWindowInsetTop(),
                    ins.getSystemWindowInsetRight(), ins.getSystemWindowInsetBottom());
            }
            return ins;
        });
        setContentView(sv);
    }

    private View row(String slot, String name, String desc, float d) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int v = (int) (10 * d);
        row.setPadding(0, v, 0, v);

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this);
        t.setText(name);
        t.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Medium);
        TextView s = new TextView(this);
        s.setText(desc);
        s.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Small);
        text.addView(t);
        text.addView(s);
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch sw = new Switch(this);
        sw.setTag(slot);
        sw.setOnCheckedChangeListener((btn, on) -> { if (!binding) save(); });
        switches.add(sw);
        row.addView(sw);
        row.setOnClickListener(x -> sw.toggle());
        return row;
    }

    @Override
    protected void onResume() {
        super.onResume();
        String cur = Settings.Secure.getString(getContentResolver(), KEY);
        Set<String> on = new HashSet<>();
        for (String p : (cur == null ? DEFAULT : cur).split(",")) {
            if (!p.trim().isEmpty()) on.add(p.trim());
        }
        binding = true;
        for (Switch sw : switches) sw.setChecked(on.contains((String) sw.getTag()));
        binding = false;
        boolean ok = checkSelfPermission(PERM) == PackageManager.PERMISSION_GRANTED;
        banner.setVisibility(ok ? View.GONE : View.VISIBLE);
        banner.setText("Needs a one-time permission:\nadb shell pm grant " + getPackageName() + " " + PERM);
    }

    private void save() {
        String joined = "";
        for (Switch sw : switches) {
            if (sw.isChecked()) {
                if (joined.length() > 0) joined += ",";
                joined += (String) sw.getTag();
            }
        }
        try {
            Settings.Secure.putString(getContentResolver(), KEY, joined);
        } catch (SecurityException e) {
            banner.setVisibility(View.VISIBLE);
            Toast.makeText(this, "Permission missing - not saved", Toast.LENGTH_SHORT).show();
        }
    }
}
