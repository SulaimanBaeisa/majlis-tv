package io.freemytv;

import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;

/** Actual Android shaping, shared by the UI and on-TV checks; never truncates a religious text or its source. */
final class DailyLayout {
    final StaticLayout text,source;
    final float sourceTop;
    final boolean fits;
    private DailyLayout(StaticLayout text,StaticLayout source,float sourceTop,boolean fits) {
        this.text=text;this.source=source;this.sourceTop=sourceTop;this.fits=fits;
    }
    static DailyLayout make(String body,String reference,int width,float height,float k) {
        TextPaint sourcePaint=new TextPaint(Ui.text(Ui.medium,24*k,0xFFBFC3CA));
        StaticLayout src=layout(reference.replace(" · ","\n"),sourcePaint,width,1.10f);
        float sourceTop=height-28*k-src.getHeight();
        float maxBody=sourceTop-98*k-24*k;
        StaticLayout best=null;
        for(float size=34;size>=20;size-=2) {
            TextPaint tp=new TextPaint(Ui.text(Ui.medium,size*k,0xFFF5F5F7));
            best=layout(body,tp,width,1.18f);
            if(best.getHeight()<=maxBody)break;
        }
        return new DailyLayout(best,src,sourceTop,best!=null && best.getHeight()<=maxBody);
    }
    private static StaticLayout layout(String s,TextPaint paint,int width,float spacing) {
        return StaticLayout.Builder.obtain(s,0,s.length(),paint,Math.max(1,width))
                .setTextDirection(TextDirectionHeuristics.RTL).setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false).setLineSpacing(0,spacing).build();
    }
}
