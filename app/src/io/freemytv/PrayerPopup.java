package io.freemytv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

/**
 * The adhan banner: a dark glass card at the top of the screen, over any app, for one minute. It never takes the
 * remote's focus and can't be touched, so whatever is playing keeps working underneath.
 */
final class PrayerPopup {
    private PrayerPopup() {}

    private static final long SHOW_MS = 60_000L;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static View current;

    static void show(Context app, String title, String sub) {
        MAIN.post(() -> {
            hideNow();
            Ui.init(app);
            WindowManager wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
            if (wm == null) return;
            float k = app.getResources().getDisplayMetrics().widthPixels / 1920f;
            Card v = new Card(app, title, sub, k);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    Math.round(980 * k), Math.round(200 * k),
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            lp.y = Math.round(56 * k);
            lp.setTitle("Free My TV prayer");
            try {
                wm.addView(v, lp);
            } catch (RuntimeException e) {
                AppLog.w("prayer popup: " + e.getMessage());
                return;
            }
            current = v;
            v.setAlpha(0f);
            v.animate().alpha(1f).setDuration(400).start();
            MAIN.postDelayed(PrayerPopup::fadeOut, SHOW_MS);
        });
    }

    static void hide() {
        MAIN.post(PrayerPopup::hideNow);
    }

    private static void fadeOut() {
        View v = current;
        if (v == null) return;
        v.animate().alpha(0f).setDuration(500).withEndAction(PrayerPopup::hideNow).start();
    }

    private static void hideNow() {
        MAIN.removeCallbacksAndMessages(null);
        View v = current;
        current = null;
        if (v == null) return;
        try {
            ((WindowManager) v.getContext().getSystemService(Context.WINDOW_SERVICE)).removeView(v);
        } catch (RuntimeException ignored) {
        }
    }

    private static final class Card extends View {
        private final String title, sub, time;
        private final float k;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint titlePaint, subPaint, timePaint;
        private final RectF r = new RectF();

        Card(Context c, String title, String sub, float k) {
            super(c);
            this.title = title;
            this.sub = sub;
            this.k = k;
            java.time.LocalTime now = java.time.LocalTime.now();
            this.time = Info.time12(now.getHour() * 60 + now.getMinute());
            titlePaint = Ui.text(Ui.semibold, 44 * k, 0xFFFFFFFF);
            subPaint = Ui.text(Ui.medium, 30 * k, 0xFF30D158);
            timePaint = Ui.numbers(Ui.semibold, 40 * k, 0xD9FFFFFF);
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), rad = 34 * k;
            r.set(0, 0, w, h);
            fill.setColor(0xFA1C1C1E);
            c.drawRoundRect(r, rad, rad, fill);
            fill.setColor(0x33FFFFFF);
            fill.setStyle(Paint.Style.STROKE);
            fill.setStrokeWidth(2 * k);
            r.inset(k, k);
            c.drawRoundRect(r, rad, rad, fill);
            fill.setStyle(Paint.Style.FILL);
            // green accent on the reading side (right)
            fill.setColor(0xFF30D158);
            c.drawRoundRect(w - 18 * k, 40 * k, w - 10 * k, h - 40 * k, 4 * k, 4 * k, fill);
            float right = w - 50 * k;
            titlePaint.setTextAlign(Paint.Align.RIGHT);
            subPaint.setTextAlign(Paint.Align.RIGHT);
            c.drawText(title, right, 92 * k, titlePaint);
            c.drawText(sub, right, 148 * k, subPaint);
            timePaint.setTextAlign(Paint.Align.LEFT);
            c.drawText("\u200F" + time, 48 * k, h / 2 + Ui.halfCap(timePaint), timePaint);
        }
    }
}
