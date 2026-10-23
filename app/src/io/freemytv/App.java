package io.freemytv;

import android.app.Application;

/** Gives Prefs a context in every process. */
public class App extends Application {
    @Override public void onCreate() {
        super.onCreate();
        Prefs.init(this);
        // The adhan pop-up lives in the home-screen process (kept alive as the home screen).
        if (getProcessName().endsWith(":home")) {
            PrayerAlarm.schedule(this);
            // TCL drops the boot and app-updated broadcasts to apps it didn't start itself; the home screen starts at
            // boot and after an update, so the check (Majlis mode complete, remote-buttons service on) runs here.
            if (RemoteKeys.supported()) HomeMode.bootCheck(this);
        }
    }
}
