package io.github.fragtion.statusback;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import java.lang.ClassLoader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * Lets chosen status-bar slots through Samsung's home-bar "simplification" gate
 * (StatusBarIconControllerImpl.hideBySimplification). The slot list lives in
 * Settings.Secure[KEY] (comma-separated) and is written by MainActivity; changes
 * apply live via a ContentObserver + refreshIconGroups().
 */
public final class StatusBackModule extends XposedModule {
    private static final String TAG = "StatusBack";
    // keep in sync with MainActivity.KEY / DEFAULT
    private static final String KEY = "statusback_restore_slots";
    private static final String DEFAULT = "ims_volte,ims_volte2,bluetooth,bluetooth_connected";

    private static final String PKG = "com.android.systemui.statusbar.phone.ui.";

    private static volatile Set<String> restore = parse(DEFAULT);
    private static final Set<String> SEEN = Collections.synchronizedSet(new HashSet<>());
    private static Field locField, ctxField, ctlField;
    private static Method refresh;
    private static Object controller;
    private static boolean observing;

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        if (!"com.android.systemui".equals(param.getPackageName())) {
            return;
        }

        ClassLoader cl = param.getDefaultClassLoader();
        try {
            Class<?> impl = cl.loadClass(PKG + "StatusBarIconControllerImpl");
            Class<?> mgr = cl.loadClass(PKG + "IconManager");
            locField = field(mgr, "mLocation");
            ctxField = field(mgr, "mContext");
            ctlField = field(mgr, "mController");
            refresh = impl.getDeclaredMethod("refreshIconGroups");
            refresh.setAccessible(true);
            Method target = impl.getDeclaredMethod("hideBySimplification", mgr, String.class);
            target.setAccessible(true);

            hook(target)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    List<?> args = chain.getArgs();
                    Object manager = args.get(0);
                    String slot = args.get(1) instanceof String ? (String) args.get(1) : "";
                    if (isHome(manager)) {
                        attach(manager);
                        boolean show = restore.contains(slot);
                        if (SEEN.add(slot)) {
                            log(Log.INFO, TAG, "HOME slot: " + slot + (show ? " -> shown" : " -> hidden"));
                        }
                        if (show) return false;
                    }
                    return chain.proceed();
                });

            log(Log.INFO, TAG, "Installed home status-bar slot hook in SystemUI.");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Could not install hook; firmware class/method may differ.", t);
        }
    }

    /** First HOME call: grab controller + Context, load config, watch for changes. */
    private static void attach(Object manager) {
        try {
            if (controller == null) controller = ctlField.get(manager);
            if (observing) return;
            Context ctx = (Context) ctxField.get(manager);
            if (ctx == null) return;
            observing = true;
            Context app = ctx.getApplicationContext() != null ? ctx.getApplicationContext() : ctx;
            final ContentResolver cr = app.getContentResolver();
            reload(cr);
            cr.registerContentObserver(Settings.Secure.getUriFor(KEY), false,
                new ContentObserver(new Handler(Looper.getMainLooper())) {
                    @Override
                    public void onChange(boolean selfChange) {
                        reload(cr);
                        try {
                            if (controller != null) refresh.invoke(controller);
                        } catch (Throwable t) {
                            Log.w(TAG, "refreshIconGroups failed", t);
                        }
                    }
                });
        } catch (Throwable t) {
            Log.w(TAG, "attach failed", t);
        }
    }

    private static void reload(ContentResolver cr) {
        String v = Settings.Secure.getString(cr, KEY);
        restore = parse(v == null ? DEFAULT : v);
    }

    private static Set<String> parse(String s) {
        Set<String> out = new HashSet<>();
        if (s != null) {
            for (String p : s.split(",")) {
                p = p.trim();
                if (!p.isEmpty()) out.add(p);
            }
        }
        return out;
    }

    private static Field field(Class<?> c, String name) throws NoSuchFieldException {
        Field f = c.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private static boolean isHome(Object manager) {
        try {
            Object l = locField.get(manager);
            return l != null && "HOME".equals(l.toString());
        } catch (Throwable t) {
            return false;
        }
    }
}
