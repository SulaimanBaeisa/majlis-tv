package io.freemytv;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.provider.Settings;

import java.time.LocalDateTime;
import java.util.TimeZone;

/**
 * Prayer pop-up: an alarm at every adhan (Umm al-Qura times for the configured place) that shows {@link PrayerPopup}
 * over whatever is on screen, then schedules the next one. Needs "display over other apps" (SYSTEM_ALERT_WINDOW) and,
 * for an on-the-minute alarm, SCHEDULE_EXACT_ALARM; both are granted by Mashrabiya mode and handed back by Original
 * (HomeMode). Without the overlay permission nothing is shown and nothing is scheduled.
 *
 * The receiver is exported only to holders of android.permission.DUMP (the adb shell, the system): the test action
 * lets the pop-up be checked from a computer without waiting for the next prayer.
 */
public class PrayerAlarm extends BroadcastReceiver {
    static final String ACTION_TEST = "io.freemytv.PRAYER_TEST";
    /** adb only: arm a real alarm 60 s from now (for checking that the TV delivers it). */
    static final String ACTION_TEST_ALARM = "io.freemytv.PRAYER_TEST_ALARM";
    private static final long LATE_LIMIT_MS = 10 * 60_000L;   // the TV was off: an adhan older than this is skipped

    @Override public void onReceive(Context c, Intent intent) {
        String action = intent.getAction();
        if (ACTION_TEST_ALARM.equals(action)) {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am != null && allowed(c)) arm(c.getApplicationContext(), am, next(c, LocalDateTime.now()), System.currentTimeMillis() + 60_000L);
        } else if (ACTION_TEST.equals(action)) {
            Schedule.Adhan a = next(c, LocalDateTime.now());
            show(c, a.prayer, a.jumuah);
        } else {
            schedule(c);
        }
    }

    private static void show(Context c, int prayer, boolean jumuah) {
        if (!allowed(c)) return;
        Schedule.Adhan a = new Schedule.Adhan();
        a.prayer = prayer;
        a.jumuah = jumuah;
        PrayerPopup.show(c.getApplicationContext(), Schedule.popupTitle(a), Schedule.popupSub(a, Prefs.iqama()));
        AppLog.w("prayer popup: " + Schedule.NAMES_AR[prayer]);
    }

    static boolean allowed(Context c) {
        return Settings.canDrawOverlays(c);
    }

    private static double tzHours() {
        return TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 3_600_000.0;
    }

    private static Schedule.Adhan next(Context c, LocalDateTime now) {
        double lat = 21.49012, lng = 39.18624;
        try {
            lat = Double.parseDouble(Prefs.latitude());
            lng = Double.parseDouble(Prefs.longitude());
        } catch (NumberFormatException ignored) {
        }
        return Schedule.nextAdhan(now, lat, lng, tzHours());
    }

    /** Alarm broadcast to a receiver registered at run time in the home-screen process (no manifest receiver). */
    static final String ACTION_DUE = "io.freemytv.PRAYER_DUE";
    private static BroadcastReceiver live;
    private static PendingIntent armed;

    /**
     * Arms the alarm for the next adhan (replacing any earlier one). It is delivered to a receiver registered at run
     * time in the home-screen process, not to the manifest: TCL's TclAppBoot drops alarm broadcasts to manifest
     * receivers of apps it considers not running, and Android 14 drops in-process alarm listeners of cached apps.
     * Safe to call often.
     */
    static synchronized void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        if (armed != null) am.cancel(armed);
        armed = null;
        if (!allowed(c)) return;
        Schedule.Adhan a = next(c, LocalDateTime.now());
        arm(c.getApplicationContext(), am, a, a.epochMs(tzHours()));
    }

    private static synchronized void arm(Context app, AlarmManager am, Schedule.Adhan a, long due) {
        if (live == null) {
            live = new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent i) {
                    synchronized (PrayerAlarm.class) {
                        armed = null;
                    }
                    long d = i.getLongExtra("due", 0);
                    int prayer = i.getIntExtra("prayer", -1);
                    if (prayer >= 0 && System.currentTimeMillis() - d < LATE_LIMIT_MS) show(c, prayer, i.getBooleanExtra("jumuah", false));
                    schedule(c);
                }
            };
            app.registerReceiver(live, new IntentFilter(ACTION_DUE), Context.RECEIVER_NOT_EXPORTED);
        }
        if (armed != null) am.cancel(armed);
        Intent i = new Intent(ACTION_DUE).setPackage(app.getPackageName())
                .putExtra("due", due).putExtra("prayer", a.prayer).putExtra("jumuah", a.jumuah);
        armed = PendingIntent.getBroadcast(app, 7, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, due, armed);
        else am.setWindow(AlarmManager.RTC_WAKEUP, due, 60_000L, armed);
        AppLog.w("prayer alarm: " + Schedule.NAMES_AR[a.prayer] + " " + a.date + " " + Schedule.hhmm(a.minutes)
                + (exact ? " exact" : " inexact") + " in " + (due - System.currentTimeMillis()) / 1000 + " s");
    }

    /** From the home screen's 15 s tick: arms the alarm once the permission arrives, drops it once it is taken away. */
    static synchronized void ensure(Context c) {
        boolean ok = allowed(c);
        if (ok && armed == null) schedule(c);
        else if (!ok && armed != null) cancel(c);
    }

    static synchronized void cancel(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null && armed != null) am.cancel(armed);
        armed = null;
        PrayerPopup.hide();
    }
}
