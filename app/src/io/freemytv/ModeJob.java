package io.freemytv;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.PersistableBundle;

/**
 * Runs the mode switches in the background (HomeMode). As a job, the system keeps the app's main process running until
 * the switch finishes, even with the screen showing something else.
 */
public class ModeJob extends JobService {
    static final String APPLY = "apply", VERIFY = "verify", REVERT = "revert", MEMORY = "memory", REMOTE = "remote";

    @Override public boolean onStartJob(JobParameters params) {
        PersistableBundle extras = params.getExtras();
        final String action = extras.getString("action", "");
        final String mode = extras.getString("mode", "");
        final Context app = getApplicationContext();
        new Thread(() -> {
            run(app, action, mode);
            jobFinished(params, false);
        }, "free-my-tv-" + action).start();
        return true;
    }

    static void run(Context app, String action, String mode) {
        AppLog.w("mode task: " + action + (mode == null || mode.isEmpty() ? "" : " " + mode));
        try {
            switch (action) {
                case APPLY: HomeMode.applyNow(app, mode); break;
                case VERIFY: HomeMode.verifyNow(app); break;
                case REVERT: HomeMode.revertHomeNow(app); break;
                case MEMORY: HomeMode.measureMemoryNow(app, mode); break;
                case REMOTE: HomeMode.ensureRemoteServiceNow(app); break;
                default: break;
            }
        } catch (Throwable t) {
            AppLog.w("job " + action + " failed: " + t);
        }
    }

    /**
     * TCL (TclAppBoot) refuses to start this app's main process for a job ("forbid bind service"), so there the work
     * runs on a background thread of the process that asked (the settings / home screen, which is alive).
     */
    private static android.os.Handler direct;

    private static synchronized android.os.Handler direct() {
        if (direct == null) {
            android.os.HandlerThread t = new android.os.HandlerThread("free-my-tv-modes");
            t.start();
            direct = new android.os.Handler(t.getLooper());
        }
        return direct;
    }

    @Override public boolean onStopJob(JobParameters params) {
        return false;
    }

    /** Schedules a job (one pending per action: a new request replaces the old one). */
    static void schedule(Context c, String action, String mode, long delayMs) {
        if (RemoteKeys.supported()) {
            final Context app = c.getApplicationContext();
            android.os.Handler h = direct();
            h.removeCallbacksAndMessages(action);   // one pending per action, as with the jobs
            h.postAtTime(() -> run(app, action, mode), action, android.os.SystemClock.uptimeMillis() + delayMs);
            JobScheduler js = c.getSystemService(JobScheduler.class);   // a job left from before can never run here
            if (js != null) js.cancel(1000 + Math.abs(action.hashCode() % 1000));
            return;
        }
        JobScheduler scheduler = c.getSystemService(JobScheduler.class);
        if (scheduler == null) return;
        PersistableBundle extras = new PersistableBundle();
        extras.putString("action", action);
        extras.putString("mode", mode == null ? "" : mode);
        JobInfo.Builder job = new JobInfo.Builder(1000 + Math.abs(action.hashCode() % 1000),
                new ComponentName(c, ModeJob.class)).setExtras(extras);
        if (delayMs > 0) job.setMinimumLatency(delayMs);
        job.setOverrideDeadline(delayMs + 2_000);
        scheduler.schedule(job.build());
    }
}
