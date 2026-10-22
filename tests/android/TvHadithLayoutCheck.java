import android.graphics.*;
import android.text.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

/** Runs with app_process on the actual TV to exercise its Android text shaping/layout. */
public class TvHadithLayoutCheck {
    public static void main(String[] args) throws Exception {
        Typeface font = new Typeface.Builder(args[0]).setFontVariationSettings("'wght' 600").build();
        TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        text.setTypeface(font); text.setColor(Color.WHITE); text.setTextAlign(Paint.Align.LEFT);
        int checked = 0, failed = 0;
        Bitmap sheet = Bitmap.createBitmap(1920, 4420, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(sheet); canvas.drawColor(0xff171b20);
        for (String line : new String(Files.readAllBytes(Paths.get(args[1])), StandardCharsets.UTF_8).split("\n")) {
            if (!line.startsWith("H\t")) continue;
            String[] row = line.split("\t", 3);
            StaticLayout best = null;
            int size;
            for (size = 34; size >= 20; size -= 2) {
                text.setTextSize(size);
                best = StaticLayout.Builder.obtain(row[2], 0, row[2].length(), text, 260)
                        .setTextDirection(TextDirectionHeuristics.RTL).setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(0, 1.18f).build();
                if (best.getHeight() <= 252) break;
            }
            boolean ok = best.getHeight() <= 252;
            if (!ok) failed++;
            System.out.println((ok ? "PASS" : "FAIL") + " size=" + size + " h=" + best.getHeight() + " " + row[1]);
            int left = checked % 6 * 320, top = checked / 6 * 442;
            Paint border = new Paint();border.setColor(0xff56616c);border.setStyle(Paint.Style.STROKE);
            canvas.drawRect(left, top, left+320, top+442, border);
            canvas.save();canvas.translate(left+30, top+98);best.draw(canvas);canvas.restore();
            TextPaint label = new TextPaint(text); label.setTextSize(23);label.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText("حديث اليوم · " + (checked+1),left+290,top+70,label);
            label.setTypeface(new Typeface.Builder(args[0]).setFontVariationSettings("'wght' 500").build());
            String[] source = row[1].split(" · ", 2);
            label.setTextSize(18);
            if (label.measureText(source[1]) > 260) label.setTextSize(16);
            if (label.measureText(source[0]) > 260 || label.measureText(source[1]) > 260
                    || 379 + label.ascent() < 98 + best.getHeight() || 419 + label.descent() > 442) {
                failed++;System.out.println("FAIL source bounds " + row[1]);
            }
            canvas.drawText(source[0],left+290,top+379,label);
            canvas.drawText(source[1],left+290,top+419,label);
            checked++;
        }
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(args[2])) { sheet.compress(Bitmap.CompressFormat.PNG,100,out); }
        System.out.println("ANDROID_LAYOUT checked=" + checked + " failed=" + failed);
        if (checked != 60 || failed != 0) System.exit(1);
    }
}
