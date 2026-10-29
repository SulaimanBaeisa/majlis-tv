package io.freemytv;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Choreographer;
import android.view.KeyEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Majlis's settings (the app's icon, "All Apps", or the gear on the home screen), in the style of Apple TV's
 * settings: the big symbol and title on the left, the list on the right with the focused row in white. "Home screen"
 * opens the Majlis / Original choice (with a check on the current one); the switch runs in the main process
 * (HomeMode) and this screen only asks for it and follows the state file.
 */
public class SettingsActivity extends Activity {
    private SettingsView view;
    private DreamView preview;   // "Screensaver": the screensaver right here, without changing the system setting

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Watchdog.install(this);
        Ui.init(this);
        view = new SettingsView(this);
        setContentView(view);
    }

    @Override protected void onResume() {
        super.onResume();
        view.resume();
        // From ADB: am start -n io.freemytv/.SettingsActivity --ez screensaver true (straight to the preview).
        if (preview == null && getIntent().getBooleanExtra("screensaver", false)) {
            getIntent().removeExtra("screensaver");
            view.pause();
            showPreview();
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override protected void onPause() {
        closePreview();     // before pausing the view: closing the preview resumes it (view.resume)
        view.pause();
        super.onPause();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (preview != null) {
            // Like the real screensaver: arrows, OK or Back close it (other keys go on to the system).
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_BACK:
                    if (event.getAction() == KeyEvent.ACTION_UP) closePreview();
                    return true;
                default:
                    return super.dispatchKeyEvent(event);
            }
        }
        if (view.onKey(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    void showPreview() {
        preview = new DreamView(this);
        setContentView(preview);
        preview.start();
    }

    private void closePreview() {
        if (preview == null) return;
        preview.stop();
        preview = null;
        setContentView(view);
        view.resume();
    }

    static final class SettingsView extends View implements Choreographer.FrameCallback {
        private static final int MAIN = 0, HOME_CHOICE = 1, HOME_SETTINGS = 2, REMOTE_KEYS = 3, PICKER = 4, SHELF_EDIT = 5, FOOTBALL = 6, ABOUT = 7;
        /** Main rows, in order. */
        private static final int ROW_HOME = 0, ROW_SAVER = 1, ROW_HOME_SETTINGS = 2, ROW_FOOTBALL = 3, ROW_SHELF = 4,
                ROW_REMOTE = 5, ROW_TV = 6, ROW_ABOUT = 7, MAIN_ROWS = 8;
        /** Text never goes below this (tvOS safe area: 60 px from the bottom of 1080). */
        private static final float SAFE_BOTTOM = 1020;
        private static final float LIST_ROW_H = 68, LIST_ROW_GAP = 6;
        private static final int PICK_VISIBLE = 9;
        private static final float LIST_X = 1000, LIST_R = 1824, LIST_TOP = 186, ROW_H = 74, ROW_GAP = 8, CHOICE_H = 104;
        private static final int AMBER = 0xFFFFC266, INK = 0xFF1C1C1E;

        private final SettingsActivity activity;
        private final Scene scene = new Scene();
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Spring[] pop = new Spring[12];
        private final List<String> appPkgs = new ArrayList<>(), appLabels = new ArrayList<>();
        // The list picker (the app of a remote button): what it shows, which one is in use, where we are.
        private final List<String> pickLabels = new ArrayList<>();
        // The shelf editor: the apps it can hold (selected ones first, in order) and the selection.
        private final List<String[]> shelfRows = new ArrayList<>();
        private final List<String> shelfOrder = new ArrayList<>();
        private int pickFor, pickCurrent, pickScroll;   // pickFor: the remote button (index + 1)
        private final RectF tmp = new RectF(), rowRect = new RectF(), focusBox = new RectF();
        private JSONObject state = new JSONObject(), recs = new JSONObject();
        private boolean hasKey;
        private int screen = MAIN, focus;
        private boolean running, okDown, backDown, wasBusy;
        private StaticLayout errorLayout;   // the last error, wrapped (rebuilt only when it changes)
        private String errorShown;
        // Football: the 20 competitions, the selection being edited (saved on every change).
        private List<String> football = new ArrayList<>();
        // About: wrapped paragraphs (heading flag, layout), their total height and the scroll.
        private final List<StaticLayout> aboutLayouts = new ArrayList<>();
        private final List<Boolean> aboutHeads = new ArrayList<>();
        private float aboutHeight, aboutScroll;
        private String aboutKey = "", paragraphKey = "";
        private StaticLayout paragraphLayout;
        private long aboutCheckTick = -1;
        private final Spoken spoken;
        private long startedAt, lastNanos, pendingAt, lastSky;
        private int w, h;
        private float k = 1;
        private Paint title, subtitle, version, rowLabel, rowValue, rowSub, header, cellLabel, cellValue, warn, spinner, white, shadow;
        private TextPaint notice, warnText;
        private Paint aboutTitle;

        private final Runnable poll = new Runnable() {
            @Override public void run() {
                if (!running) return;
                state = HomeMode.clearStaleBusy(getContext());
                hasKey = Recs.hasKey(getContext());
                recs = Recs.status(getContext());
                boolean nowBusy = !state.optString("busy").isEmpty();
                if (nowBusy) pendingAt = 0;
                // A switch just ended: TalkBack reads how it went (the newest notice).
                JSONArray notices = state.optJSONArray("notices");
                if (wasBusy && !nowBusy && notices != null && notices.length() > 0) {
                    JSONObject newest = notices.optJSONObject(0);
                    if (newest != null) announceForAccessibility(newest.optString("text"));
                }
                wasBusy = nowBusy;
                invalidate();
                handler.postDelayed(this, 500);
            }
        };

        SettingsView(SettingsActivity a) {
            super(a);
            activity = a;
            for (int i = 0; i < pop.length; i++) pop[i] = new Spring(0.28f, 0.85f, 0);
            // Always the dark Majlis look (not the time-of-day sky: by day it was a bright blue behind white text).
            Shaders.majlis(scene.palette);
            scene.setMotion(0.5f, 0.2f);
            scene.applyPalette();
            setFocusable(true);
            setContentDescription("Majlis");
            // TalkBack: one item per row, id = row + 1.
            spoken = new Spoken(this, new Spoken.Source() {
                @Override public void items(List<Integer> out) { for (int i = 1; i <= rowCount(); i++) out.add(i); }
                @Override public String text(int id) { return id >= 1 && id <= rowCount() ? rowText(id - 1) : ""; }
                @Override public boolean bounds(int id, Rect out) {
                    if (id < 1 || id > rowCount()) return false;
                    rowBox(id - 1).round(out);
                    return true;
                }
                @Override public int focused() { return focus + 1; }
                @Override public void focus(int id) {
                    focus = id - 1;
                    invalidate();
                }
                @Override public void click(int id) { activate(); }
            });
        }

        @Override public AccessibilityNodeProvider getAccessibilityNodeProvider() {
            return spoken;
        }

        /** A row as TalkBack reads it: its name and its value, as on screen. */
        private String rowText(int row) {
            switch (screen) {
                case MAIN: {
                    if (row == 0) {
                        String busyText = state.optString("busy");
                        String value = busy() ? (busyText.isEmpty() ? Ui.tr("Começando…", "Starting…") : busyText)
                                : freeActive() ? "Majlis" : Ui.tr("Original", "Original");
                        return Ui.tr("Início, ", "Home Screen, ") + value;
                    }
                    String[] labels = mainLabels();
                    if (row == ROW_SAVER) return Ui.tr("Tela de Descanso, ver agora", "Screensaver, preview");
                    if (row == ROW_FOOTBALL) return labels[row] + ", " + footballValue();
                    return row < labels.length ? labels[row] : "";
                }
                case FOOTBALL: {
                    int at = pickScroll + row;
                    if (at >= Competitions.ALL.size()) return "";
                    Competitions.Competition comp = Competitions.ALL.get(at);
                    return name(comp) + ", " + (football.contains(comp.id) ? say("مختارة", "selecionada", "selected") : say("غير مختارة", "não selecionada", "not selected"))
                            + ", " + (at + 1) + Ui.tr(" de ", " of ") + Competitions.ALL.size();
                }
                case ABOUT:
                    return aboutText();
                case HOME_CHOICE: {
                    String name = row == 0 ? "Majlis, " + Ui.tr("sem anúncios, com a tela de descanso nova", "no ads, with the new screensaver")
                            : "Original, " + Ui.tr("o Início de fábrica da TV", "the TV's factory home screen");
                    boolean inUse = (row == 0) == freeActive();
                    return name + (inUse ? Ui.tr(". Em uso", ". In use") : "") + ", " + (row + 1) + Ui.tr(" de 2", " of 2");
                }
                case HOME_SETTINGS:
                case REMOTE_KEYS: {
                    boolean keys = screen == REMOTE_KEYS;
                    String[] labels = keys ? keyLabels() : settingLabels(), values = keys ? keyValues() : settingValues();
                    return row < labels.length ? labels[row] + ", " + values[row] : "";
                }
                case PICKER: {
                    int at = pickScroll + row;
                    if (at >= pickLabels.size()) return "";
                    return pickLabels.get(at) + (at == pickCurrent ? Ui.tr(", escolhido", ", selected") : "")
                            + ", " + (at + 1) + Ui.tr(" de ", " of ") + pickLabels.size();
                }
                case SHELF_EDIT: {
                    int at = pickScroll + row;
                    if (at >= shelfRows.size()) return "";
                    String[] app = shelfRows.get(at);
                    int place = shelfOrder.indexOf(app[0]);
                    return app[1] + ", " + (place >= 0 ? Ui.tr("na prateleira, posição ", "on the shelf, place ") + (place + 1)
                            : Ui.tr("fora da prateleira", "not on the shelf"));
                }
                default:
                    return "";
            }
        }

        private RectF rowBox(int row) {
            float top, height;
            if (screen == MAIN) {
                top = LIST_TOP + row * (ROW_H + ROW_GAP);
                height = ROW_H;
            } else if (screen == HOME_CHOICE) {
                top = LIST_TOP + 20 + row * (CHOICE_H + ROW_GAP);
                height = CHOICE_H;
            } else if (screen == ABOUT) {
                top = LIST_TOP;
                height = SAFE_BOTTOM - LIST_TOP;
            } else {
                top = LIST_TOP + 20 + row * (LIST_ROW_H + LIST_ROW_GAP);
                height = LIST_ROW_H;
            }
            focusBox.set(LIST_X * k, top * k, LIST_R * k, (top + height) * k);
            Rtl.flip(focusBox, 0, w);
            if (screen == ABOUT) focusBox.offset(aboutColumnShift(), 0);
            return focusBox;
        }

        void resume() {
            if (running) return;
            running = true;
            startedAt = SystemClock.uptimeMillis();
            lastNanos = 0;
            handler.post(poll);
            Choreographer.getInstance().postFrameCallback(this);
            spoken.readAgain();
        }

        void pause() {
            running = false;
            handler.removeCallbacks(poll);
            Choreographer.getInstance().removeFrameCallback(this);
        }

        @Override public void doFrame(long frameTimeNanos) {
            if (!running) return;
            boolean moving = busy();
            for (Spring s : pop) moving |= !s.settled();
            if (moving || SystemClock.uptimeMillis() - lastSky >= 100) invalidate();
            Choreographer.getInstance().postFrameCallback(this);
        }

        @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            w = width;
            h = height;
            k = w / 1920f;
            scene.setSize(w, h);
            title = Ui.text(Ui.displaySemibold, 50 * k, 0xFFFFFFFF);
            title.setTextAlign(Paint.Align.CENTER);
            subtitle = Ui.text(Ui.regular, 26 * k, 0x99FFFFFF);
            subtitle.setTextAlign(Paint.Align.CENTER);
            version = Ui.text(Ui.regular, 22 * k, 0x66FFFFFF);
            version.setTextAlign(Paint.Align.CENTER);
            rowLabel = Ui.text(Ui.medium, 30 * k, 0xF2FFFFFF);
            rowValue = Ui.text(Ui.regular, 28 * k, 0x8CFFFFFF);
            rowValue.setTextAlign(Paint.Align.RIGHT);
            rowSub = Ui.text(Ui.regular, 23 * k, 0x8CFFFFFF);
            header = Ui.text(Ui.semibold, 21 * k, 0x73FFFFFF);
            header.setLetterSpacing(0.06f);
            cellLabel = Ui.text(Ui.regular, 25 * k, 0xD9FFFFFF);
            cellValue = Ui.numbers(Ui.regular, 25 * k, 0xD9FFFFFF);
            cellValue.setTextAlign(Paint.Align.RIGHT);
            warn = Ui.text(Ui.regular, 23 * k, AMBER);
            warnText = new TextPaint(warn);
            aboutTitle = Ui.text(Ui.displaySemibold, 34 * k, 0xFFFFFFFF);
            paragraphKey = aboutKey = "";
            paragraphLayout = null;
            aboutCheckTick = -1;
            notice = new TextPaint(Ui.text(Ui.regular, 23 * k, 0xB3FFFFFF));
            spinner = new Paint(Paint.ANTI_ALIAS_FLAG);
            spinner.setStyle(Paint.Style.STROKE);
            spinner.setStrokeCap(Paint.Cap.ROUND);
            spinner.setStrokeWidth(3.5f * k);
            white = new Paint(Paint.ANTI_ALIAS_FLAG);
            white.setColor(0xFFF5F5F7);
            shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
            shadow.setMaskFilter(new BlurMaskFilter(22 * k, BlurMaskFilter.Blur.NORMAL));
        }

        private boolean busy() {
            return !state.optString("busy").isEmpty() || (pendingAt != 0 && SystemClock.uptimeMillis() - pendingAt < 10_000);
        }

        private boolean freeActive() {
            return HomeMode.FREE.equals(state.optString("mode")) && !state.optBoolean("homeReverted");
        }

        private int rowCount() {
            switch (screen) {
                case MAIN: return MAIN_ROWS;
                case HOME_CHOICE: return 2;
                case HOME_SETTINGS: return settingLabels().length;
                case PICKER: return Math.min(PICK_VISIBLE, pickLabels.size());
                case SHELF_EDIT: return Math.min(PICK_VISIBLE, shelfRows.size());
                case FOOTBALL: return Math.min(PICK_VISIBLE, Competitions.ALL.size());
                case ABOUT: return 1;   // one block of text, scrolled with ↑ ↓
                default: return remoteKeys().length;
            }
        }

        /** drawText for a layout drawn left-to-right: in right-to-left the anchor is mirrored across the screen. */
        private void put(Canvas c, String s, float x, float y, Paint p) {
            c.drawText(s, Rtl.tx(p, s, x, 0, w), y, p);
        }

        @Override protected void onDraw(Canvas c) {
            if (w == 0) return;
            long now = System.nanoTime();
            float dt = lastNanos == 0 ? 1 / 60f : Math.min(0.05f, (now - lastNanos) / 1e9f);
            lastNanos = now;
            for (int i = 0; i < pop.length; i++) {
                pop[i].target = i == focus && i < rowCount() ? 1 : 0;
                pop[i].step(dt);
            }
            long up = SystemClock.uptimeMillis();
            float seconds = (up - startedAt) / 1000f;
            if (up - lastSky >= 100) {
                lastSky = up;
                scene.advance(seconds);
            }
            scene.drawBackground(c);

            // On the left: big symbol, title, description.
            c.save();
            if (screen == ABOUT) c.translate(-aboutColumnShift(), 0);
            float cx = 500 * k;
            Symbols.draw(getContext(), c, "sparkles", Rtl.x(cx, 0, w), 400 * k, 150 * k, 0xEBFFFFFF);
            put(c, "Majlis", cx, 612 * k, title);
            put(c, Ui.tr("Início e tela de descanso da TV", "Your TV's home screen and screensaver"), cx, 660 * k, subtitle);
            put(c, versionName(getContext()), cx, 704 * k, version);
            c.restore();

            if (screen == MAIN) drawMain(c, seconds); else if (screen == HOME_CHOICE) drawChoice(c); else if (screen == PICKER) drawPicker(c);
            else if (screen == SHELF_EDIT) drawShelfEditor(c); else if (screen == FOOTBALL) drawFootball(c); else if (screen == ABOUT) drawAbout(c); else drawList(c);
        }

        /** Text for a run-time string with no fixed English key: Arabic, Portuguese, or English (also for Spanish). */
        private static String say(String ar, String pt, String en) {
            return Ui.ar ? ar : Ui.en || Ui.es ? en : pt;
        }

        private String[] mainLabels() {
            return new String[] {Ui.tr("Início", "Home Screen"), Ui.tr("Tela de Descanso", "Screensaver"),
                    Ui.tr("Ajustes do Início", "Home Settings"), say("كرة القدم", "Futebol", "Football"), Ui.tr("Prateleira", "Shelf"),
                    Ui.tr("Botões do Controle", "Remote Buttons"), Ui.tr("Ajustes da TV", "TV Settings"),
                    say("حول مجلس ومصادر البيانات", "Sobre e fontes de dados", "About and data sources")};
        }

        private static String name(Competitions.Competition comp) { return Ui.ar ? comp.ar : comp.en; }

        /** "3 بطولات" / "None": the Football row's value. */
        private String footballValue() {
            List<String> ids = Prefs.competitions();
            if (ids.isEmpty()) return say("لا شيء", "Nenhum", "None");
            if (ids.size() == 1) return name(Competitions.byId(ids.get(0)));
            return Ui.ar ? Arabic.unit(ids.size(), "بطولة", "بطولتان", "بطولات", "بطولة") : ids.size() + say("", " competições", " competitions");
        }

        /**
         * A wrapped paragraph in the list column: right-aligned in Arabic (RTL paragraph direction, so mixed Arabic and
         * Latin keeps its order), at most {@code maxLines}, never past SAFE_BOTTOM. Returns the height used.
         */
        private float paragraph(Canvas c, String text, float top, TextPaint paint, int maxLines) {
            int width = Math.round((LIST_R - LIST_X - 60) * k);
            int lines = Math.max(1, Math.min(maxLines, (int) ((SAFE_BOTTOM * k - top) / (paint.getFontSpacing()))));
            String key = text + "|" + width + "|" + lines + "|" + paint.getTextSize() + "|" + paint.getColor() + "|" + Ui.language;
            if (paragraphLayout == null || !key.equals(paragraphKey)) {
                paragraphLayout = Rtl.dir(StaticLayout.Builder.obtain(text, 0, text.length(), new TextPaint(paint), Math.max(1, width))
                        .setMaxLines(lines).setEllipsize(TextUtils.TruncateAt.END)).build();
                paragraphKey = key;
            }
            StaticLayout l = paragraphLayout;
            tmp.set((LIST_X + 30) * k, top, (LIST_X + 30) * k + width, top + l.getHeight());
            Rtl.flip(tmp, 0, w);
            c.save();
            c.translate(tmp.left, top);
            l.draw(c);
            c.restore();
            return l.getHeight();
        }

        private void drawMain(Canvas c, float seconds) {
            boolean working = busy();
            String busyText = state.optString("busy");
            String mode = freeActive() ? "Majlis" : Ui.tr("Original", "Original");
            String[] labels = mainLabels();
            String[] values = {working ? "" : mode, Ui.tr("Ver agora", "Preview"), "", footballValue(), "", "", "", ""};
            for (int i = 0; i < MAIN_ROWS; i++) {
                float y = (LIST_TOP + i * (ROW_H + ROW_GAP)) * k;
                rowRect.set(LIST_X * k, y, LIST_R * k, y + ROW_H * k);
                float f = drawRowBackground(c, i);
                int ink = blend(0xF2FFFFFF, INK, f);
                rowLabel.setColor(ink);
                float base = rowRect.centerY() + Ui.halfCap(rowLabel);
                put(c, labels[i], rowRect.left + 30 * k, base, rowLabel);
                float right = rowRect.right - 30 * k;
                if (i == ROW_FOOTBALL) {
                    rowValue.setColor(blend(0x8CFFFFFF, 0x8C1C1C1E, f));
                    float cw = Symbols.width(getContext(), "chevron_right", 22 * k);
                    String shown = TextUtils.ellipsize(values[i], new TextPaint(rowValue), 360 * k, TextUtils.TruncateAt.END).toString();
                    put(c, shown, right - cw - 18 * k, base, rowValue);
                }
                if (i >= 2) {
                    float cw = Symbols.width(getContext(), "chevron_right", 22 * k);
                    float chx = Rtl.x(right - cw / 2, 0, w);
                    c.save();
                    if (Rtl.on()) c.scale(-1, 1, chx, rowRect.centerY());
                    Symbols.draw(getContext(), c, "chevron_right", chx, rowRect.centerY(), 22 * k, blend(0x66FFFFFF, 0x991C1C1E, f));
                    c.restore();
                } else if (i == 0 && working) {
                    // Switching: the current step in place of the value, with the spinning arc.
                    String text = busyText.isEmpty() ? Ui.tr("Começando…", "Starting…") : busyText;
                    rowValue.setColor(blend(0xB3FFFFFF, 0xB31C1C1E, f));
                    String shown = TextUtils.ellipsize(text, new TextPaint(rowValue), 470 * k, TextUtils.TruncateAt.END).toString();
                    put(c, shown, right, base, rowValue);
                    float sx = right - rowValue.measureText(shown) - 26 * k;
                    spinner.setColor(blend(0xFFFFFFFF, INK, f));
                    Ui.spinner(c, Rtl.x(sx, 0, w), rowRect.centerY(), 12 * k, seconds * 1.3f, spinner);
                } else {
                    rowValue.setColor(blend(0x8CFFFFFF, 0x8C1C1C1E, f));
                    put(c, values[i], right, base, rowValue);
                }
            }

            // Under the list, inside the safe area: what to do on the TV, the last error (wrapped, whole when it fits),
            // or the newest notice. The comparison, recommendations, credits and all notices are in About.
            float y = (LIST_TOP + MAIN_ROWS * (ROW_H + ROW_GAP) + 18) * k;
            String error = state.optString("error");
            if (working && (busyText.contains("Permitir") || busyText.contains("Allow"))) {
                paragraph(c, Ui.tr("Na TV: marque “Sempre permitir” e aperte OK.", "On the TV: tick “Always allow” and press OK."), y, warnText, 2);
            } else if (!working && !error.isEmpty()) {
                paragraph(c, error, y, warnText, 4);
            } else {
                JSONArray notices = state.optJSONArray("notices");
                JSONObject newest = notices == null ? null : notices.optJSONObject(0);
                if (newest != null) paragraph(c, ago(newest.optLong("at")) + " · " + newest.optString("text"), y, notice, 3);
            }
        }

        /** Home screen picks: status (About; the credit TMDB asks for is in About.sections). */
        private String recsLine() {
            String line;
            String error = recs.optString("error");
            int count = recs.optInt("count");
            if (!hasKey) line = Ui.tr("Sem chave do TMDB: veja o README (config.properties).", "No TMDB key: see the README (config.properties).");
            else if (!error.isEmpty()) line = Ui.tr("Erro: ", "Error: ") + error + ".";
            else if (count > 0) line = Ui.orAr("تعمل · " + count + " عنوانًا من تطبيقات التلفاز، و3 جديدة كل ساعة.",
                    Ui.tr("Ligadas · " + count + " títulos nos apps da TV, 3 novos a cada hora.",
                    "On · " + count + " titles from the TV's apps, 3 new ones every hour."));
            else line = Ui.tr("Buscando os títulos…", "Fetching titles…");
            return line;
        }

        /** List row: no background; focused, white with a shadow (the Apple TV focus). Returns how focused it is. */
        private float drawRowBackground(Canvas c, int i) {
            float f = Ui.clamp(pop[i].value);
            if (f > 0.01f) {
                float s = 1 + 0.02f * f;
                float cx = rowRect.centerX(), cy = rowRect.centerY();
                tmp.set(cx - rowRect.width() * s / 2, cy - rowRect.height() * s / 2, cx + rowRect.width() * s / 2, cy + rowRect.height() * s / 2);
                float r = 18 * k * s;
                tmp.offset(0, 10 * k * f);
                shadow.setColor(Ui.alpha(0xFF000000, 0.4f * f));
                Rtl.flip(tmp, 0, w);
                c.drawRoundRect(tmp, r, r, shadow);
                Rtl.flip(tmp, 0, w);
                tmp.offset(0, -10 * k * f);
                white.setAlpha(Math.round(255 * f));
                Rtl.flip(tmp, 0, w);
                c.drawRoundRect(tmp, r, r, white);
                Rtl.flip(tmp, 0, w);
                rowRect.set(tmp);
            }
            return f;
        }

        /** The Original / Majlis measurements, one line each (About). */
        private String comparisonText() {
            JSONObject metrics = state.optJSONObject("metrics");
            JSONObject a = metrics == null ? null : metrics.optJSONObject(HomeMode.ORIGINAL);
            JSONObject b = metrics == null ? null : metrics.optJSONObject(HomeMode.FREE);
            String[][] rows = {{Ui.tr("Botão Início", "Home button"), "homeMs"}, {Ui.tr("Memória livre", "Free memory"), "memAvailableMB"},
                    {Ui.tr("Memória do Início", "Home screen memory"), "homePssMB"}};
            StringBuilder sb = new StringBuilder();
            for (String[] r : rows) {
                sb.append(sb.length() == 0 ? "" : "\n").append(r[0]).append(": ").append(Ui.tr("Original", "Original")).append(" ")
                        .append(Rtl.ltr(metric(a, r[1]))).append(" · Majlis ").append(Rtl.ltr(metric(b, r[1])));
            }
            return sb.toString();
        }

        /** Up to five notices, newest first, whole (About). */
        private String noticesText() {
            JSONArray notices = state.optJSONArray("notices");
            StringBuilder sb = new StringBuilder();
            for (int i = 0, shown = 0; notices != null && i < notices.length() && shown < 5; i++) {
                JSONObject o = notices.optJSONObject(i);
                if (o == null) continue;
                sb.append(sb.length() == 0 ? "" : "\n").append(ago(o.optLong("at"))).append(" · ").append(o.optString("text"));
                shown++;
            }
            return sb.length() == 0 ? Ui.tr("Nenhum ainda.", "None yet.") : sb.toString();
        }

        /** Everything About shows, as {heading, text} (headings may be ""), in order. */
        private List<String[]> aboutSections() {
            List<String[]> out = new ArrayList<>();
            out.add(new String[] {versionName(getContext()), say("الشاشة الرئيسية وشاشة التوقف لتلفازك.",
                    "Início e tela de descanso da TV.", "Your TV's home screen and screensaver.")});
            out.addAll(About.sections(Ui.ar));   // Arabic, else English
            out.add(new String[] {Ui.tr("RECOMENDAÇÕES", "RECOMMENDATIONS"), recsLine()});
            out.add(new String[] {Ui.tr("COMPARAÇÃO", "COMPARISON"), comparisonText()});
            out.add(new String[] {Ui.tr("AVISOS", "NOTICES"), noticesText()});
            return out;
        }

        private String aboutText() {
            StringBuilder sb = new StringBuilder();
            for (String[] s : aboutSections()) sb.append(s[0]).append(s[0].isEmpty() ? "" : ". ").append(s[1]).append(" ");
            return sb.toString().trim();
        }

        /** Rebuilds the wrapped paragraphs when the text or the size changed (the notices change while it is open). */
        private void layoutAbout() {
            long tick = SystemClock.uptimeMillis() / 500;
            if (tick == aboutCheckTick) return;
            aboutCheckTick = tick;
            List<String[]> sections = aboutSections();
            StringBuilder key = new StringBuilder().append(w);
            for (String[] s : sections) key.append('|').append(s[0]).append('|').append(s[1]);
            if (key.toString().equals(aboutKey)) return;
            aboutKey = key.toString();
            aboutLayouts.clear();
            aboutHeads.clear();
            int width = Math.round((LIST_R - LIST_X - 60) * k);
            TextPaint head = new TextPaint(header), body = new TextPaint(notice);
            head.setColor(HomeLayout.ACCENT);   // headings in the home's green: readable on the dark background
            head.setLetterSpacing(0);           // Arabic must not be letter-spaced
            aboutHeight = 0;
            for (String[] s : sections) {
                if (!s[0].isEmpty()) add(s[0], head, width, true);
                add(s[1], body, width, false);
            }
            aboutScroll = Math.max(0, Math.min(aboutScroll, maxAboutScroll()));
        }

        private void add(String text, TextPaint paint, int width, boolean heading) {
            StaticLayout l = Rtl.dir(StaticLayout.Builder.obtain(text, 0, text.length(), paint, Math.max(1, width))
                    .setLineSpacing(4 * k, 1f)).build();
            aboutLayouts.add(l);
            aboutHeads.add(heading);
            aboutHeight += l.getHeight() + (heading ? 8 : 22) * k;
        }

        private float maxAboutScroll() {
            return Math.max(0, aboutHeight - (SAFE_BOTTOM - LIST_TOP - 60) * k);
        }

        /**
         * About: the data sources (generated from what the app really fetches), the licences, the TMDB notice, then the
         * recommendations status, the measurements and the notices. Wrapped and right-aligned in Arabic, clipped to the
         * safe area and scrolled with ↑ ↓; nothing is cut to fit.
         */
        private float aboutColumnShift() {
            return HomeLayout.rightColumnShift(w, LIST_X * k, LIST_R * k, Rtl.on());
        }

        private void drawAbout(Canvas c) {
            c.save();
            c.translate(aboutColumnShift(), 0);
            layoutAbout();
            put(c, say("حول مجلس", "Sobre", "About"), LIST_X * k + 30 * k, (LIST_TOP - 24) * k, aboutTitle);
            float top = (LIST_TOP + 10) * k, bottom = (SAFE_BOTTOM - 50) * k;
            tmp.set((LIST_X + 30) * k, top, (LIST_R - 30) * k, bottom);
            Rtl.flip(tmp, 0, w);
            c.save();
            c.clipRect(tmp);
            float y = top - aboutScroll;
            for (int i = 0; i < aboutLayouts.size(); i++) {
                StaticLayout l = aboutLayouts.get(i);
                if (y + l.getHeight() > top && y < bottom) {
                    c.save();
                    c.translate(tmp.left, y);
                    l.draw(c);
                    c.restore();
                }
                y += l.getHeight() + (aboutHeads.get(i) ? 8 : 22) * k;
            }
            c.restore();
            Paint hint = Ui.text(Ui.regular, 23 * k, 0x8CFFFFFF);
            if (maxAboutScroll() > 0) {
                int pct = Math.round(100 * aboutScroll / maxAboutScroll());
                put(c, say("↑ ↓ للتمرير · ", "↑ ↓ rolam · ", "↑ ↓ scroll · ") + Rtl.ltr(pct + "%"), LIST_X * k + 30 * k, (SAFE_BOTTOM - 8) * k, hint);
            }
            c.restore();
        }

        // MARK: - Football competitions

        private void openFootball() {
            football = new ArrayList<>(Prefs.competitions());
            pickScroll = 0;
            focus = 0;
            screen = FOOTBALL;
        }

        /** OK: follows or stops following the focused competition (saved right away; Competitions.MAX_SELECTED at most). */
        private void toggleCompetition() {
            String id = Competitions.ALL.get(pickScroll + focus).id;
            List<String> next = Competitions.toggle(football, id);
            if (next.equals(football)) return;   // the limit: nothing changes (the hint says why)
            football = new ArrayList<>(next);
            Prefs.setCompetitions(football);
        }

        private void drawFootball(Canvas c) {
            Paint heading = Ui.text(Ui.displaySemibold, 34 * k, 0xFFFFFFFF);
            put(c, say("بطولات كرة القدم", "Campeonatos de futebol", "Football competitions"), LIST_X * k + 30 * k, (LIST_TOP - 24) * k, heading);
            int rows = rowCount();
            for (int i = 0; i < rows; i++) {
                Competitions.Competition comp = Competitions.ALL.get(pickScroll + i);
                boolean on = football.contains(comp.id);
                float y = (LIST_TOP + 20 + i * (LIST_ROW_H + LIST_ROW_GAP)) * k;
                rowRect.set(LIST_X * k, y, LIST_R * k, y + LIST_ROW_H * k);
                float f = drawRowBackground(c, i);
                rowLabel.setColor(blend(on ? 0xF2FFFFFF : 0x99FFFFFF, on ? INK : 0x991C1C1E, f));
                float base = rowRect.centerY() + Ui.halfCap(rowLabel);
                put(c, TextUtils.ellipsize(name(comp), new TextPaint(rowLabel), 600 * k, TextUtils.TruncateAt.END).toString(), rowRect.left + 30 * k, base, rowLabel);
                if (on) {
                    float cw = Symbols.width(getContext(), "check", 26 * k);
                    Symbols.draw(getContext(), c, "check", Rtl.x(rowRect.right - 30 * k - cw / 2, 0, w), rowRect.centerY(), 26 * k,
                            blend(HomeLayout.ACCENT, 0xFF1E8E3E, f));
                }
            }
            float hy = (LIST_TOP + 20 + rows * (LIST_ROW_H + LIST_ROW_GAP) + 24) * k;
            String count = Rtl.ltr((pickScroll + focus + 1) + " / " + Competitions.ALL.size());
            String hint = football.isEmpty()
                    ? say("لم تختر أي بطولة: تبويب المباريات سيطلب منك الاختيار ولن يجلب أي بيانات.",
                        "Nenhum escolhido: a aba de jogos pede para escolher e não busca nada.",
                        "None selected: the matches tab asks you to pick one and fetches nothing.")
                    : football.size() >= Competitions.MAX_SELECTED
                    ? say("الحد " + Competitions.MAX_SELECTED + " بطولات لتخفيف الطلبات. ألغِ واحدة لإضافة أخرى.",
                        "No máximo " + Competitions.MAX_SELECTED + ". Tire um para pôr outro.",
                        "Up to " + Competitions.MAX_SELECTED + " at once. Remove one to add another.")
                    : say("موافق للاختيار أو الإلغاء (حتى " + Competitions.MAX_SELECTED + "). ← → في تبويب المباريات للتبديل بينها.",
                        "OK escolhe ou tira (até " + Competitions.MAX_SELECTED + "). ← → na aba de jogos alternam.",
                        "OK picks or removes (up to " + Competitions.MAX_SELECTED + "). ← → in the matches tab switch between them.");
            paragraph(c, count + "  ·  " + hint, hy - Ui.cap(notice), notice, 3);
        }

        /** Home screen choice: Majlis or the original, with a check on the one in use. */
        private void drawChoice(Canvas c) {
            Paint heading = Ui.text(Ui.displaySemibold, 34 * k, 0xFFFFFFFF);
            put(c, Ui.tr("Início", "Home Screen"), LIST_X * k + 30 * k, (LIST_TOP - 24) * k, heading);
            String[][] options = {{"Majlis", Ui.tr("Sem anúncios, com a tela de descanso nova", "No ads, with the new screensaver")},
                    {"Original", Ui.tr("O Início de fábrica da TV", "The TV's factory home screen")}};
            int current = freeActive() ? 0 : 1;
            for (int i = 0; i < 2; i++) {
                float y = (LIST_TOP + 20 + i * (CHOICE_H + ROW_GAP)) * k;
                rowRect.set(LIST_X * k, y, LIST_R * k, y + CHOICE_H * k);
                float f = drawRowBackground(c, i);
                rowLabel.setColor(blend(0xF2FFFFFF, INK, f));
                rowSub.setColor(blend(0x8CFFFFFF, 0x991C1C1E, f));
                put(c, options[i][0], rowRect.left + 30 * k, rowRect.top + 44 * k, rowLabel);
                put(c, options[i][1], rowRect.left + 30 * k, rowRect.top + 80 * k, rowSub);
                if (i == current) {
                    float cw = Symbols.width(getContext(), "check", 26 * k);
                    Symbols.draw(getContext(), c, "check", Rtl.x(rowRect.right - 30 * k - cw / 2, 0, w), rowRect.centerY(), 26 * k, blend(0xFFFFFFFF, INK, f));
                }
            }
            Paint hint = Ui.text(Ui.regular, 23 * k, 0x8CFFFFFF);
            put(c, Ui.tr("A troca leva uns segundos e a tela pisca nas medições.",
                    "Switching takes a few seconds; the screen blinks while it measures."), LIST_X * k + 30 * k,
                    (LIST_TOP + 20 + 2 * (CHOICE_H + ROW_GAP) + 40) * k, hint);
        }

        private static int blend(int a, int b, float f) {
            int aa = a >>> 24, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
            int ba = b >>> 24, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
            return (Math.round(aa + (ba - aa) * f) << 24) | (Math.round(ar + (br - ar) * f) << 16)
                    | (Math.round(ag + (bg - ag) * f) << 8) | Math.round(ab + (bb - ab) * f);
        }

        private static String metric(JSONObject m, String key) {
            if (m == null || !m.has(key)) return "—";
            long v = m.optLong(key);
            if (key.equals("homeMs")) return v + " ms";
            if (key.equals("memAvailableMB") && v >= 1024) return String.format(Ui.locale(), "%.2f GB", v / 1024.0);
            return v + " MB";
        }

        private static String ago(long at) {
            long minutes = Math.max(0, (System.currentTimeMillis() - at) / 60_000);
            if (Ui.ar) return Arabic.ago(minutes);
            if (minutes < 1) return Ui.tr("agora", "now");
            if (minutes < 60) return Ui.tr("há " + minutes + " min", minutes + " min ago");
            if (minutes < 48 * 60) return Ui.tr("há " + (minutes / 60) + " h", (minutes / 60) + " h ago");
            return Ui.tr("há " + (minutes / 1440) + " dias", (minutes / 1440) + " days ago");
        }

        private static String versionName(Context c) {
            try {
                return "Majlis " + c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
            } catch (android.content.pm.PackageManager.NameNotFoundException e) {
                return "Majlis";
            }
        }

        boolean onKey(KeyEvent e) {
            boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
            switch (e.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (screen == ABOUT) {
                        if (down) aboutScroll = Math.max(0, aboutScroll - 80 * k);
                        invalidate();
                        return true;
                    }
                    if (down && focus > 0) focus--;
                    else if (down && scrolling() && pickScroll > 0) pickScroll--;
                    invalidate();
                    spoken.focusMoved();
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (screen == ABOUT) {
                        if (down) aboutScroll = Math.min(maxAboutScroll(), aboutScroll + 80 * k);
                        invalidate();
                        return true;
                    }
                    if (down && focus < rowCount() - 1) focus++;
                    else if (down && scrolling() && pickScroll + rowCount() < listSize()) pickScroll++;
                    invalidate();
                    spoken.focusMoved();
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (down && screen == SHELF_EDIT) {
                        moveShelfApp(Rtl.key(e.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1));
                        invalidate();
                    } else if (down && screen == HOME_SETTINGS) {
                        adjust(Rtl.key(e.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1));
                        invalidate();
                    }
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    if (down) {
                        if (e.getRepeatCount() == 0) okDown = true;
                    } else {
                        if (okDown && !e.isCanceled() && spoken.keyClick()) activate();
                        okDown = false;
                    }
                    return true;
                case KeyEvent.KEYCODE_BACK:
                    if (screen == MAIN) return false;   // leaves the settings
                    if (down) {
                        if (e.getRepeatCount() == 0) backDown = true;
                    } else {
                        if (backDown && !e.isCanceled()) {
                            if (screen == PICKER) {
                                focus = pickFor - 1;
                                screen = REMOTE_KEYS;
                            } else {
                                focus = screen == HOME_SETTINGS ? ROW_HOME_SETTINGS : screen == SHELF_EDIT ? ROW_SHELF
                                        : screen == REMOTE_KEYS ? ROW_REMOTE : screen == FOOTBALL ? ROW_FOOTBALL : screen == ABOUT ? ROW_ABOUT : 0;                                screen = MAIN;
                            }
                        }
                        backDown = false;
                    }
                    invalidate();
                    spoken.focusMoved();
                    return true;
                default:
                    return false;
            }
        }

        // MARK: - Home Settings and Remote Buttons

        private static final String[] LANGUAGES = {"auto", "pt", "en", "es", "ar"}, UNITS = {"auto", "C", "F"}, THEMES = {"auto", "light", "dark"};
        /** Home Settings: language, temperature, theme, weather, quotes, then one row per coin. */
        private static final int COIN_ROWS = 5;

        private boolean dollar() {
            return "BRL".equalsIgnoreCase(Config.CURRENCY.trim());
        }

        private String[] settingLabels() {
            List<String> l = new ArrayList<>();
            l.add(Ui.tr("Idioma", "Language"));
            l.add(Ui.tr("Temperatura", "Temperature"));
            l.add(Ui.tr("Tema", "Theme"));
            l.add(Ui.tr("Widget do clima", "Weather widget"));
            l.add(Ui.tr("Widget de cotações", "Quotes widget"));
            l.add("Bitcoin");
            l.add("Ethereum");
            l.add("Solana");
            if (dollar()) l.add(Ui.tr("Dólar", "Dollar"));
            return l.toArray(new String[0]);
        }

        private String[] settingValues() {
            String[] labels = settingLabels();
            String[] v = new String[labels.length];
            String lang = Prefs.language();
            v[0] = lang.equals("pt") ? "Português" : lang.equals("en") ? "English" : lang.equals("es") ? "Español" : lang.equals("ar") ? "العربية" : Ui.tr("Automático", "Automatic");
            String unit = Prefs.temperature();
            v[1] = unit.equals("C") ? "°C" : unit.equals("F") ? "°F" : Ui.tr("Automática", "Automatic");
            String theme = Prefs.theme();
            v[2] = theme.equals("light") ? Ui.tr("Claro", "Light") : theme.equals("dark") ? Ui.tr("Escuro", "Dark") : Ui.tr("Automático", "Automatic");
            v[3] = onOff(Prefs.weather());
            v[4] = onOff(Prefs.quotes());
            // No quotes widget, nowhere to show prices: the coins read as off (and stay locked) until it is back on.
            for (int i = COIN_ROWS; i < v.length; i++) v[i] = onOff(Prefs.quotes() && Prefs.coin(i - COIN_ROWS));
            return v;
        }

        private static String onOff(boolean on) {
            return on ? Ui.tr("Ligado", "On") : Ui.tr("Desligado", "Off");
        }

        /**
         * The remote buttons this TV's maker sends with the codes Majlis knows (TvBrand): TCL's four (with its own
         * "TCL Channel" button), none on other makers, where the codes may mean other buttons.
         */
        private static int[] remoteKeys() {
            return RemoteKeys.supported() ? Prefs.KEYS : new int[0];
        }

        private String[] keyLabels() {
            int[] keys = remoteKeys();
            String[] out = new String[keys.length];
            for (int i = 0; i < keys.length; i++) {
                switch (keys[i]) {
                    case Prefs.KEY_NETFLIX: out[i] = Ui.tr("Botão Netflix", "Netflix button"); break;
                    case Prefs.KEY_YOUTUBE: out[i] = Ui.tr("Botão YouTube", "YouTube button"); break;
                    case Prefs.KEY_PRIME: out[i] = Ui.tr("Botão Prime Video", "Prime Video button"); break;
                    default: out[i] = Ui.tr("Botão TCL Channel", "TCL Channel button"); break;   // KEY_TCL_CHANNEL: TCL only
                }
            }
            return out;
        }

        /** Remote Buttons on a TV that isn't a TCL: says so with the TV's own maker name. */
        private String otherBrandNote() {
            String brand = RemoteKeys.brandName();
            if (brand.isEmpty()) return say("أزرار هذا الريموت تعمل كما هي دائمًا، ولا يغيّرها مجلس على هذا التلفاز.",
                    "Os botões deste controle funcionam como sempre: o Majlis não os muda nesta TV.",
                    "This remote's buttons work as they always did: Majlis doesn't change them on this TV.");
            return say("على تلفاز " + brand + " تعمل أزرار الريموت كما هي دائمًا، ولا يغيّرها مجلس.",
                    "Nesta TV " + brand + ", os botões do controle funcionam como sempre: o Majlis não os muda.",
                    "On this " + brand + " TV, the remote's buttons work as they always did: Majlis doesn't change them.");
        }

        private String[] keyValues() {
            int[] keys = remoteKeys();
            String[] v = new String[keys.length];
            for (int i = 0; i < v.length; i++) {
                int at = appPkgs.indexOf(Prefs.keyApp(keys[i]));
                v[i] = at >= 0 ? appLabels.get(at) : Ui.tr("Não instalado", "Not installed");
            }
            return v;
        }

        /** Installed TV apps, by name, after "Nothing": what a remote button can open. */
        private void loadApps() {
            PackageManager pm = getContext().getPackageManager();
            List<String[]> found = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
            for (ResolveInfo ri : pm.queryIntentActivities(query, 0)) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getContext().getPackageName()) || !seen.add(pkg)) continue;
                found.add(new String[] {pkg, String.valueOf(ri.loadLabel(pm))});
            }
            Collections.sort(found, (a, b) -> a[1].toLowerCase(Locale.ROOT).compareTo(b[1].toLowerCase(Locale.ROOT)));
            appPkgs.clear();
            appLabels.clear();
            appPkgs.add("");
            appLabels.add(Ui.tr("Nada", "Nothing"));
            for (String[] f : found) {
                appPkgs.add(f[0]);
                appLabels.add(f[1]);
            }
        }

        /** Changes the focused row's value one step (OK and → go forward, ← goes back). */
        private void adjust(int dir) {
            if (screen == REMOTE_KEYS) {
                int n = appPkgs.size();
                int at = appPkgs.indexOf(Prefs.keyApp(Prefs.KEYS[focus]));
                int next = at < 0 ? (dir > 0 ? 0 : n - 1) : (at + dir + n) % n;
                Prefs.setKeyApp(Prefs.KEYS[focus], appPkgs.get(next));
                return;
            }
            switch (focus) {
                case 0: {
                    int at = java.util.Arrays.asList(LANGUAGES).indexOf(Prefs.language());
                    Prefs.setLanguage(LANGUAGES[(at + dir + LANGUAGES.length) % LANGUAGES.length]);
                    break;
                }
                case 1: {
                    int at = java.util.Arrays.asList(UNITS).indexOf(Prefs.temperature());
                    Prefs.setTemperature(UNITS[(at + dir + UNITS.length) % UNITS.length]);
                    break;
                }
                case 2: {
                    int at = java.util.Arrays.asList(THEMES).indexOf(Prefs.theme());
                    Prefs.setTheme(THEMES[(at + dir + THEMES.length) % THEMES.length]);
                    break;
                }
                case 3: Prefs.setWeather(!Prefs.weather()); break;
                case 4: Prefs.setQuotes(!Prefs.quotes()); break;
                default:
                    if (!Prefs.quotes()) return;
                    Prefs.setCoin(focus - COIN_ROWS, !Prefs.coin(focus - COIN_ROWS));
                    break;
            }
            HomeData.get(getContext()).settingsChanged();
        }

        private boolean scrolling() {
            return screen == PICKER || screen == SHELF_EDIT || screen == FOOTBALL;
        }

        private int listSize() {
            return screen == SHELF_EDIT ? shelfRows.size() : screen == FOOTBALL ? Competitions.ALL.size() : pickLabels.size();
        }

        // MARK: - Shelf editor

        private void openShelfEditor() {
            shelfOrder.clear();
            shelfOrder.addAll(HomeApps.shelfPackages(getContext()));
            rebuildShelfRows(HomeApps.shelfCandidates(getContext()));
            pickScroll = 0;
            focus = 0;
            screen = SHELF_EDIT;
        }

        /** Selected apps first, in shelf order, then the rest in the order they came. */
        private void rebuildShelfRows(List<String[]> candidates) {
            shelfRows.clear();
            for (String pkg : shelfOrder) {
                for (String[] cand : candidates) if (cand[0].equals(pkg)) { shelfRows.add(cand); break; }
            }
            for (String[] cand : candidates) if (!shelfOrder.contains(cand[0])) shelfRows.add(cand);
        }

        /** Same rows after a change: keeps the focus on the app that was focused. */
        private void refreshShelfRows(String focusedPkg) {
            List<String[]> all = new ArrayList<>(shelfRows);
            rebuildShelfRows(all);
            for (int i = 0; i < shelfRows.size(); i++) {
                if (!shelfRows.get(i)[0].equals(focusedPkg)) continue;
                int visible = rowCount();
                if (i < pickScroll) pickScroll = i;
                else if (i >= pickScroll + visible) pickScroll = i - visible + 1;
                focus = i - pickScroll;
                break;
            }
            Prefs.setShelf(shelfOrder);
        }

        /** OK: puts the app on the shelf (at the end) or takes it off. */
        private void toggleShelfApp() {
            String pkg = shelfRows.get(pickScroll + focus)[0];
            if (shelfOrder.contains(pkg)) shelfOrder.remove(pkg);
            else if (shelfOrder.size() < HomeApps.MAX_SHELF) shelfOrder.add(pkg);
            else return;
            refreshShelfRows(pkg);
        }

        /** ← → : moves a shelf app one place earlier or later. */
        private void moveShelfApp(int dir) {
            String pkg = shelfRows.get(pickScroll + focus)[0];
            int at = shelfOrder.indexOf(pkg), to = at + dir;
            if (at < 0 || to < 0 || to >= shelfOrder.size()) return;
            shelfOrder.remove(at);
            shelfOrder.add(to, pkg);
            refreshShelfRows(pkg);
        }

        private void drawShelfEditor(Canvas c) {
            Paint heading = Ui.text(Ui.displaySemibold, 34 * k, 0xFFFFFFFF);
            put(c, Ui.tr("Prateleira", "Shelf"), LIST_X * k + 30 * k, (LIST_TOP - 24) * k, heading);
            int rows = rowCount();
            for (int i = 0; i < rows; i++) {
                String[] app = shelfRows.get(pickScroll + i);
                int place = shelfOrder.indexOf(app[0]);
                float y = (LIST_TOP + 20 + i * (LIST_ROW_H + LIST_ROW_GAP)) * k;
                rowRect.set(LIST_X * k, y, LIST_R * k, y + LIST_ROW_H * k);
                float f = drawRowBackground(c, i);
                rowLabel.setColor(blend(place >= 0 ? 0xF2FFFFFF : 0x80FFFFFF, place >= 0 ? INK : 0x801C1C1E, f));
                rowValue.setColor(blend(0xB3FFFFFF, 0xB31C1C1E, f));
                float base = rowRect.centerY() + Ui.halfCap(rowLabel);
                put(c, TextUtils.ellipsize(app[1], new TextPaint(rowLabel), 520 * k, TextUtils.TruncateAt.END).toString(), rowRect.left + 30 * k, base, rowLabel);
                put(c, place >= 0 ? "#" + (place + 1) : Ui.tr("Fora", "Off"), rowRect.right - 30 * k, base, rowValue);
            }
            Paint hint = Ui.text(Ui.regular, 23 * k, 0x8CFFFFFF);
            float hy = (LIST_TOP + 20 + rows * (LIST_ROW_H + LIST_ROW_GAP) + 36) * k;
            put(c, Ui.orAr("موافق للإضافة أو الإزالة (حتى " + HomeApps.MAX_SHELF + "). ← → لتغيير الترتيب.",
                    Ui.tr("OK põe ou tira da prateleira (até " + HomeApps.MAX_SHELF + "). ← → mudam a ordem.",
                    "OK adds or removes (up to " + HomeApps.MAX_SHELF + "). ← → change the order.",
                    "OK pone o quita del estante (hasta " + HomeApps.MAX_SHELF + "). ← → cambian el orden.")), LIST_X * k + 30 * k, hy, hint);
        }

        private void openPicker(int which) {
            pickFor = which;
            pickLabels.clear();
            pickLabels.addAll(appLabels);
            pickCurrent = appPkgs.indexOf(Prefs.keyApp(Prefs.KEYS[which - 1]));
            int visible = Math.min(PICK_VISIBLE, pickLabels.size());
            int at = Math.max(0, pickCurrent);
            pickScroll = Math.max(0, Math.min(at - 3, pickLabels.size() - visible));
            focus = at - pickScroll;
            screen = PICKER;
        }

        /** A long list (the apps): nine rows at a time, the one in use checked. */
        private void drawPicker(Canvas c) {
            Paint heading = Ui.text(Ui.displaySemibold, 34 * k, 0xFFFFFFFF);
            String title = keyLabels()[pickFor - 1];
            put(c, title, LIST_X * k + 30 * k, (LIST_TOP - 24) * k, heading);
            int rows = rowCount();
            for (int i = 0; i < rows; i++) {
                int at = pickScroll + i;
                float y = (LIST_TOP + 20 + i * (LIST_ROW_H + LIST_ROW_GAP)) * k;
                rowRect.set(LIST_X * k, y, LIST_R * k, y + LIST_ROW_H * k);
                float f = drawRowBackground(c, i);
                rowLabel.setColor(blend(0xF2FFFFFF, INK, f));
                put(c, pickLabels.get(at), rowRect.left + 30 * k, rowRect.centerY() + Ui.halfCap(rowLabel), rowLabel);
                if (at == pickCurrent) {
                    float cw = Symbols.width(getContext(), "check", 26 * k);
                    Symbols.draw(getContext(), c, "check", Rtl.x(rowRect.right - 30 * k - cw / 2, 0, w), rowRect.centerY(), 26 * k, blend(0xFFFFFFFF, INK, f));
                }
            }
            Paint hint = Ui.text(Ui.regular, 23 * k, 0x8CFFFFFF);
            float hy = (LIST_TOP + 20 + rows * (LIST_ROW_H + LIST_ROW_GAP) + 36) * k;
            put(c, (pickScroll + focus + 1) + " / " + pickLabels.size() + "  ·  " + Ui.tr("OK escolhe, Voltar cancela.", "OK picks, Back cancels."),
                    LIST_X * k + 30 * k, hy, hint);
        }

        private void drawList(Canvas c) {
            boolean keys = screen == REMOTE_KEYS;
            String[] labels = keys ? keyLabels() : settingLabels();
            String[] values = keys ? keyValues() : settingValues();
            Paint heading = Ui.text(Ui.displaySemibold, 34 * k, 0xFFFFFFFF);
            put(c, keys ? Ui.tr("Botões do Controle", "Remote Buttons") : Ui.tr("Ajustes do Início", "Home Settings"),
                    LIST_X * k + 30 * k, (LIST_TOP - 24) * k, heading);
            for (int i = 0; i < labels.length; i++) {
                float y = (LIST_TOP + 20 + i * (LIST_ROW_H + LIST_ROW_GAP)) * k;
                rowRect.set(LIST_X * k, y, LIST_R * k, y + LIST_ROW_H * k);
                float f = drawRowBackground(c, i);
                rowLabel.setColor(blend(0xF2FFFFFF, INK, f));
                boolean locked = !keys && i >= COIN_ROWS && !Prefs.quotes();
                rowValue.setColor(blend(locked ? 0x59FFFFFF : 0xB3FFFFFF, locked ? 0x591C1C1E : 0xB31C1C1E, f));
                if (locked) rowLabel.setColor(blend(0x73FFFFFF, 0x731C1C1E, f));
                float base = rowRect.centerY() + Ui.halfCap(rowLabel);
                put(c, labels[i], rowRect.left + 30 * k, base, rowLabel);
                put(c, values[i], rowRect.right - 30 * k, base, rowValue);
            }
            Paint hint = Ui.text(Ui.regular, 23 * k, 0x8CFFFFFF);
            float hy = (LIST_TOP + 20 + labels.length * (LIST_ROW_H + LIST_ROW_GAP) + 36) * k;
            if (keys && labels.length == 0) {
                paragraph(c, otherBrandNote(), hy - Ui.cap(notice), notice, 4);
                return;
            }
            put(c, Ui.tr("OK ou as setas mudam o valor.", "OK or the arrows change the value."), LIST_X * k + 30 * k, hy, hint);
            if (keys) {
                paragraph(c, Ui.tr("Disney e Globoplay: a própria TV controla esses botões.",
                        "Disney and Globoplay: the TV itself controls those buttons."), hy + 38 * k - Ui.cap(notice), notice, 2);
            }
        }

        private void activate() {
            Context c = getContext();
            if (screen == HOME_CHOICE) {
                boolean wantFree = focus == 0;
                if (wantFree != freeActive() && !busy()) {
                    HomeMode.request(c, wantFree ? HomeMode.FREE : HomeMode.ORIGINAL);
                    pendingAt = SystemClock.uptimeMillis();
                }
                screen = MAIN;
                focus = 0;
            } else if (screen == PICKER) {
                int at = pickScroll + focus;
                Prefs.setKeyApp(Prefs.KEYS[pickFor - 1], appPkgs.get(at));
                focus = pickFor - 1;
                screen = REMOTE_KEYS;
            } else if (screen == SHELF_EDIT) {
                toggleShelfApp();
                invalidate();
                return;
            } else if (screen == FOOTBALL) {
                toggleCompetition();
                invalidate();
                spoken.focusMoved();
                return;
            } else if (screen == ABOUT) {
                return;
            } else if (screen == REMOTE_KEYS) {
                if (remoteKeys().length == 0) return;   // not a TCL: nothing to pick (the note says why)
                openPicker(focus + 1);
            } else if (screen == HOME_SETTINGS) {
                adjust(1);
                invalidate();
                return;
            } else if (focus == ROW_HOME) {
                if (busy()) return;
                screen = HOME_CHOICE;
                focus = freeActive() ? 0 : 1;
            } else if (focus == ROW_SAVER) {
                pause();
                activity.showPreview();
            } else if (focus == ROW_HOME_SETTINGS) {
                screen = HOME_SETTINGS;
                focus = 0;
            } else if (focus == ROW_FOOTBALL) {
                openFootball();
            } else if (focus == ROW_SHELF) {
                openShelfEditor();
            } else if (focus == ROW_REMOTE) {
                HomeMode.ensureRemoteService(c, 0);   // the buttons work from inside other apps through this service
                loadApps();
                screen = REMOTE_KEYS;
                focus = 0;
            } else if (focus == ROW_TV) {
                try {
                    activity.startActivity(HomeApps.tvSettings(c).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (android.content.ActivityNotFoundException | SecurityException e) {
                    AppLog.w("TV settings: " + e.getMessage());
                }
            } else {
                aboutScroll = 0;
                aboutKey = "";
                screen = ABOUT;
                focus = 0;
            }
            for (Spring s : pop) s.snap(0);
            invalidate();
            spoken.focusMoved();
        }
    }
}
