package io.freemytv;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.Typeface;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.util.Arrays;

/** Runs on the Sony Android runtime before installation. No UI, preferences, alarms, or package-state changes. */
public final class TvV2Check {
    private static int passed;
    private static void require(boolean ok,String name){if(!ok)throw new AssertionError(name);passed++;}
    public static void main(String[] args) throws Exception {
        // app_process is not forked from the preloaded zygote; initialise Android's default font map explicitly.
        for(java.lang.reflect.Method m:Typeface.class.getDeclaredMethods())if(m.getName().equals("loadPreinstalledSystemFontMap") && m.getParameterCount()==0){m.setAccessible(true);m.invoke(null);}
        System.out.println("SONY_V2_CHECK started");
        Typeface font=Typeface.createFromFile(args[0]);
        Ui.ar=true;Ui.medium=font;Ui.regular=font;Ui.semibold=font;Ui.displaySemibold=font;
        int q=0,h=0;
        for(String line:new String(Files.readAllBytes(Paths.get(args[1])),StandardCharsets.UTF_8).split("\n")) {
            if(!line.startsWith("H\t") && !line.startsWith("Q\t"))continue;
            String[] row=line.split("\t",3);
            DailyLayout layout=DailyLayout.make(row[2],row[1],264,500,1);
            require(layout.fits,"daily text fit: "+row[1]);
            require(layout.text.getLineEnd(layout.text.getLineCount()-1)==row[2].length(),"full religious text");
            require(layout.sourceTop+layout.source.getHeight()<=472.01f,"source bottom padding");
            if(line.startsWith("H\t"))h++;else q++;
        }
        require(q==29 && h==60,"complete daily catalogue");
        System.out.println("DAILY_ANDROID q="+q+" h="+h+" all fit, no text/source truncation");
        int[] bright=Backdrop.readableGlass(new int[]{Color.WHITE,Color.RED,0xFF010203});
        for(int v:bright)require(Math.max(Color.red(v),Math.max(Color.green(v),Color.blue(v)))<=130,"bright wallpaper contrast cap");
        require(bright[2]==0xFF010203,"dark colours preserved");
        Scene scene=new Scene();scene.setSize(1920,1080);
        int[] pixels=new int[Backdrop.GW*Backdrop.GH];
        for(int y=0;y<Backdrop.GH;y++)for(int x=0;x<Backdrop.GW;x++)pixels[y*Backdrop.GW+x]=x<Backdrop.GW/2?Color.RED:Color.BLUE;
        scene.setGlassImage(pixels);scene.setOffset(0,0);
        Bitmap output=Bitmap.createBitmap(1920,1080,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(output);
        scene.glass(c,new RectF(96,168,1824,668),32,Color.BLACK,0,0,0,0,1);
        require(output.getPixel(400,400)==Color.RED && output.getPixel(1400,400)==Color.BLUE,"screen-aligned glass samples");
        require(output.getPixel(96,168)==Color.TRANSPARENT,"rounded corner clipping");
        output.eraseColor(Color.TRANSPARENT);
        scene.setOffset(600,200);c.save();c.translate(600,200);
        scene.glass(c,new RectF(0,0,1000,500),32,Color.BLACK,0,0,0,0,1);c.restore();
        require(output.getPixel(700,400)==Color.RED && output.getPixel(1100,400)==Color.BLUE,"translated glass samples global background");
        int[] green=pixels.clone();Arrays.fill(green,Color.GREEN);scene.setGlassImages(pixels,green);scene.setGlassMix(1);scene.setOffset(0,0);output.eraseColor(Color.TRANSPARENT);
        scene.glass(c,new RectF(96,168,1824,668),32,Color.BLACK,0,0,0,0,1);
        require(output.getPixel(400,400)==Color.GREEN,"wallpaper crossfade completes");
        System.out.println("GLASS_ANDROID cached bitmap, coordinate mapping, rounded corners and wallpaper swap PASS");
        String xml=new String(Files.readAllBytes(Paths.get(args[2])),StandardCharsets.UTF_8);
        require(News.parse(xml,"عام").size()>0,"actual RSS parses on Sony Android");
        require(Info.clock12(LocalTime.of(23,36)).equals("11:36"),"Western 12-hour clock");
        System.out.println("SONY_V2_CHECK PASS assertions="+passed+" actualRSS="+News.parse(xml,"عام").size());
    }
}
