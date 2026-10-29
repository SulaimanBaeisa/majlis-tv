package io.freemytv;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.view.InputDevice;
import android.view.KeyEvent;

/**
 * The remote's app buttons on TCL TVs (the maker comes from TvBrand, the installer's profile rules). The stock home screen opens the apps for Netflix, YouTube and Prime (and TCL
 * Channel); with it off, the keys still arrive, so we open the app the person picked (Settings > Remote Buttons).
 * Disney and Globoplay are opened by the system itself, so they are not listed. The YouTube button (BUTTON_3) never
 * reaches an app's window; only the accessibility service's key filter (RemoteKeyService) sees it.
 */
final class RemoteKeys {
    private RemoteKeys() {}

    /** This TV's maker profile (tcl, sony, changhong, generic), with the installer's rules (TvBrand). */
    static String brand() {
        return TvBrand.profile(Build.BRAND, Build.MANUFACTURER, Build.MODEL, Build.DEVICE);
    }

    /** The maker's name to show ("TCL", "Sony", "Classpro"…; "" when the TV doesn't say). */
    static String brandName() {
        return TvBrand.displayName(brand(), Build.BRAND, Build.MANUFACTURER);
    }

    /** The key codes are TCL's: on other brands they can be other buttons, so nothing here runs there. */
    static boolean supported() {
        return TvBrand.remoteKeys(brand());
    }

    /** Opens the app for this key on its first press. True if the key is ours and in use (it is then consumed). */
    static boolean onKey(Context c, KeyEvent e) {
        if (!supported()) return false;
        // BUTTON_3 is also a game controller's button: keys from a gamepad or a joystick are left alone.
        int source = e.getSource();
        if ((source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) return false;
        boolean ours = false;
        for (int key : Prefs.KEYS) ours |= key == e.getKeyCode();
        if (!ours) return false;
        String pkg = Prefs.keyApp(e.getKeyCode());
        if (pkg.isEmpty()) return false;   // "Nothing": the key goes on as if we weren't here
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
            Intent intent = HomeApps.launchIntent(c.getPackageManager(), pkg);
            AppLog.w("remote key " + e.getKeyCode() + " -> " + pkg + (intent == null ? " (not installed)" : ""));
            if (intent != null) c.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        return true;
    }
}
