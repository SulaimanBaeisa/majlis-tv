package io.freemytv;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Text/content for the approved three-tab TV layout. Chrome is drawn separately with screen-aligned glass. */
final class HomePanels {
    private float k;
    private Paint title,body,meta,num,score,fill;
    private final RectF box=new RectF();
    private final Map<String,StaticLayout> layouts=new LinkedHashMap<>();
    void size(float scale) {
        k=scale;layouts.clear();title=Ui.text(Ui.semibold,32*k,0xFFFFFFFF);
        body=Ui.text(Ui.medium,30*k,0xFFF5F5F7);meta=Ui.text(Ui.medium,25*k,0xFFBFC3CA);
        num=Ui.numbers(Ui.medium,28*k,0xFFFFFFFF);score=Ui.numbers(Ui.semibold,36*k,0xFFFFFFFF);
        fill=new Paint(Paint.ANTI_ALIAS_FLAG);
    }
    private void right(Canvas c,String s,float right,float base,Paint p,float width) {
        p.setTextAlign(Paint.Align.RIGHT);
        c.drawText(TextUtils.ellipsize(s,new TextPaint(p),Math.max(1,width),TextUtils.TruncateAt.END).toString(),right,base,p);
    }
    private int multiline(Canvas c,String s,float left,float top,float width,Paint p,int lines) {
        String key=s+"|"+Math.round(width)+"|"+p.getTextSize()+"|"+lines;
        StaticLayout l=layouts.get(key);
        if(l==null) {
            TextPaint tp=new TextPaint(p);tp.setTextAlign(Paint.Align.LEFT);
            l=StaticLayout.Builder.obtain(s,0,s.length(),tp,Math.max(1,(int)width))
                    .setTextDirection(android.text.TextDirectionHeuristics.RTL).setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setIncludePad(false).setLineSpacing(2*k,1.10f).setMaxLines(lines)
                    .setEllipsize(TextUtils.TruncateAt.END).build();
            if(layouts.size()>=80)layouts.remove(layouts.keySet().iterator().next());layouts.put(key,l);
        }
        l.getPaint().setColor(p.getColor());c.save();c.translate(left,top);l.draw(c);c.restore();return l.getHeight();
    }
    void news(Canvas c,RectF r,NewsFeed.Snapshot snapshot,int panel) {
        float pad=36*k,left=r.left+pad,right=r.right-pad,width=right-left;
        List<News.Item> items=panel==0?snapshot.top:panel==1?snapshot.groups.get(1):snapshot.groups.get(2);
        right(c,panel==0?"آخر الأخبار":panel==1?"صحة وطب":"اقتصاد",right,r.top+60*k,title,width);
        if(items.isEmpty()) {
            multiline(c,"الأخبار غير متاحة الآن",left,r.top+110*k,width,body,2);
            multiline(c,"تُعرض عناوين العربية والحدث عند توفر الاتصال.",left,r.top+220*k,width,meta,3);return;
        }
        boolean stale=snapshot.updated>0 && System.currentTimeMillis()-snapshot.updated>2*3600_000L;
        int count=panel==0 && !stale?3:2;float step=panel==0?130*k:180*k;
        for(int i=0;i<Math.min(count,items.size());i++) {
            News.Item n=items.get(i);float y=r.top+104*k+i*step;
            float textH=multiline(c,n.title,left,y,width,body,2);
            right(c,n.source+" · "+News.ageAr(n.publishedMs,System.currentTimeMillis()),right,y+textH+30*k,meta,width);
        }
        if(snapshot.updated>0 && System.currentTimeMillis()-snapshot.updated>2*3600_000L)
            right(c,"نسخة محفوظة · الاتصال غير متاح",right,r.bottom-22*k,meta,width);
    }
    void matches(Canvas c,RectF r,LeagueFeed feed,int panel) {
        if(panel==0) {
            long now=System.currentTimeMillis();List<League.Match> all=feed.matches(),rows=new ArrayList<>();
            rows.addAll(League.live(all));
            if(rows.isEmpty())rows.addAll(League.recent(all,now,3));
            for(League.Match m:League.upcoming(all,now,3))if(rows.size()<3)rows.add(m);
            right(c,League.live(all).isEmpty()?"مباريات الدوري السعودي":"مباشر الآن",r.right-40*k,r.top+60*k,title,r.width()-80*k);
            if(rows.isEmpty()){right(c,"لا توجد مباريات متاحة الآن",r.right-40*k,r.top+126*k,body,r.width()-80*k);return;}
            for(int i=0;i<Math.min(3,rows.size());i++)match(c,r,rows.get(i),r.top+(88+i*125)*k);
        } else if(panel==1) {
            table(c,r,feed.table(),6,false);
        } else {
            float left=r.left+30*k,right=r.right-30*k,width=right-left;
            right(c,"الدوري السعودي",right,r.top+60*k,title,width);
            multiline(c,"المصدر: ESPN",left,r.top+110*k,width,body,2);
            long at=feed.lastUpdateMs();
            multiline(c,at>0?"آخر تحديث\n"+News.ageAr(at,System.currentTimeMillis()):"بانتظار بيانات المباريات",left,r.top+210*k,width,meta,3);
            multiline(c,"لكل فريق سطره ونتيجته، حتى لا ينعكس الترتيب مع العربية.",left,r.top+320*k,width,meta,4);
        }
    }
    private void match(Canvas c,RectF r,League.Match m,float y) {
        float left=r.left+40*k,right=r.right-40*k,width=right-left;
        box.set(left,y,right,y+108*k);fill.setColor(0x16FFFFFF);c.drawRoundRect(box,20*k,20*k,fill);
        float nameRight=right-22*k,scoreX=left+36*k;
        right(c,m.home,nameRight,y+38*k,body,width-110*k);
        right(c,m.away,nameRight,y+82*k,body,width-110*k);
        score.setTextAlign(Paint.Align.CENTER);score.setColor("in".equals(m.state)?0xFF5BE37F:0xFFFFFFFF);
        c.drawText(League.scoreFor(m,true),scoreX,y+38*k,score);
        c.drawText(League.scoreFor(m,false),scoreX,y+82*k,score);
        // A status in its own column; it never separates a team from its score.
        String status="pre".equals(m.state)?League.whenAr(m.kickoffMs,System.currentTimeMillis(),java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis())):League.clockAr(m);
        float middle=left+width*0.47f;
        meta.setTextAlign(Paint.Align.CENTER);c.drawText(status,middle,y+61*k,meta);
    }
    void drawer(Canvas c,RectF r,LeagueFeed feed) {
        float mid=r.centerX();RectF matches=new RectF(mid+8*k,r.top,r.right,r.bottom);
        right(c,"المباريات",matches.right-28*k,r.top+48*k,title,matches.width()-56*k);
        List<League.Match> rows=new ArrayList<>(League.live(feed.matches()));
        for(League.Match m:League.upcoming(feed.matches(),System.currentTimeMillis(),2))if(rows.size()<2)rows.add(m);
        if(rows.isEmpty())right(c,"لا مباريات متاحة الآن",matches.right-28*k,r.top+110*k,meta,matches.width()-50*k);
        for(int i=0;i<Math.min(2,rows.size());i++)compactMatch(c,matches,rows.get(i),r.top+(65+i*112)*k);
        table(c,new RectF(r.left,r.top,mid-8*k,r.bottom),feed.table(),4,true);
    }
    private void compactMatch(Canvas c,RectF r,League.Match m,float y) {
        float left=r.left+22*k,right=r.right-22*k,width=right-left;
        box.set(left,y,right,y+104*k);fill.setColor(0x16FFFFFF);c.drawRoundRect(box,16*k,16*k,fill);
        body.setTextSize(25*k);score.setTextSize(28*k);score.setTextAlign(Paint.Align.LEFT);
        right(c,m.home,right-14*k,y+28*k,body,width-70*k);
        right(c,m.away,right-14*k,y+61*k,body,width-70*k);
        score.setColor("in".equals(m.state)?0xFF5BE37F:0xFFFFFFFF);
        c.drawText(League.scoreFor(m,true),left+12*k,y+28*k,score);
        c.drawText(League.scoreFor(m,false),left+12*k,y+61*k,score);
        String status="pre".equals(m.state)?League.whenAr(m.kickoffMs,System.currentTimeMillis(),java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis())):League.clockAr(m);
        meta.setTextSize(22*k);right(c,status,right-14*k,y+92*k,meta,width-28*k);
        body.setTextSize(30*k);score.setTextSize(36*k);meta.setTextSize(25*k);
    }
    private void table(Canvas c,RectF r,List<League.Row> rows,int max,boolean compact) {
        float left=r.left+26*k,right=r.right-26*k,width=right-left;
        right(c,"ترتيب الدوري",right,r.top+(compact?48:60)*k,title,width);
        if(rows.isEmpty()){right(c,"الترتيب غير متاح الآن",right,r.top+118*k,meta,width);return;}
        meta.setTextAlign(Paint.Align.LEFT);c.drawText("نقاط",left,r.top+(compact?83:111)*k,meta);
        float step=(compact?42:53)*k,base=r.top+(compact?125:163)*k;
        for(int i=0;i<Math.min(max,rows.size());i++) {
            League.Row row=rows.get(i);float y=base+i*step;
            num.setTextAlign(Paint.Align.RIGHT);c.drawText(String.valueOf(row.rank),right,y,num);
            right(c,row.team,right-36*k,y,meta,width-104*k);
            num.setTextAlign(Paint.Align.LEFT);c.drawText(String.valueOf(row.points),left,y,num);
        }
    }
    void ticker(Canvas c,RectF r,News.Item item,float alpha,int index,int total) {
        int save=c.saveLayerAlpha(r,Math.round(255*Ui.clamp(alpha)));
        float right=r.right-24*k,left=r.left+30*k;
        if(item==null){right(c,"الأخبار غير متاحة الآن · العربية والحدث",right,r.centerY()+10*k,meta,r.width()-60*k);c.restoreToCount(save);return;}
        String badge=item.source+" · "+item.category;
        meta.setTextAlign(Paint.Align.RIGHT);meta.setColor(0xFF5BE37F);
        float badgeW=meta.measureText(badge)+36*k;
        box.set(right-badgeW,r.top+14*k,right,r.bottom-14*k);fill.setColor(0x2630D158);c.drawRoundRect(box,24*k,24*k,fill);
        right(c,badge,right-18*k,r.centerY()+9*k,meta,badgeW-36*k);meta.setColor(0xFFBFC3CA);
        String age=News.ageAr(item.publishedMs,System.currentTimeMillis());meta.setTextAlign(Paint.Align.LEFT);
        c.drawText(age,left,r.centerY()+9*k,meta);float ageW=meta.measureText(age)+26*k;
        right(c,item.title,right-badgeW-24*k,r.centerY()+11*k,body,r.width()-badgeW-ageW-84*k);
        c.restoreToCount(save);
    }
}
