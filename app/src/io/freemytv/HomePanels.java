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
    private Paint title,body,meta,num,score,fill,chipText,chipBorder;
    private final TextPaint chipTp=new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box=new RectF(),chipBox=new RectF();
    private final Map<String,StaticLayout> layouts=new LinkedHashMap<>();
    void size(float scale) {
        k=scale;layouts.clear();title=Ui.text(Ui.semibold,32*k,0xFFFFFFFF);
        body=Ui.text(Ui.medium,30*k,0xFFF5F5F7);meta=Ui.text(Ui.medium,25*k,0xFFBFC3CA);
        num=Ui.numbers(Ui.medium,28*k,0xFFFFFFFF);score=Ui.numbers(Ui.semibold,36*k,0xFFFFFFFF);
        chipText=Ui.text(Ui.medium,26*k,0xFFFFFFFF);
        chipBorder=new Paint(Paint.ANTI_ALIAS_FLAG);chipBorder.setStyle(Paint.Style.STROKE);chipBorder.setStrokeWidth(2.5f*k);
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
            multiline(c,"تُعرض عناوين "+News.namesAr()+" عند توفر الاتصال.",left,r.top+220*k,width,meta,3);return;
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
    /** Why there are no rows to show, for the competition on screen (null = there is data). */
    static String emptyReason(LeagueFeed feed) {
        if(feed.current()==null)return "لم تختر أي بطولة. اخترها من الإعدادات ← كرة القدم.";
        int s=feed.status();
        if(s==Competitions.UNSUPPORTED)return "هذه البطولة غير متاحة من المصدر حاليًا.";
        if(s==LeagueFeed.UNKNOWN)return "جارٍ جلب المباريات…";
        if(s==Competitions.FAILED && !feed.hasData())return "تعذّر الاتصال بالمصدر. سنحاول مجددًا.";
        return null;
    }
    static String heading(LeagueFeed feed) {
        Competitions.Competition c=feed.current();return c==null?"كرة القدم":c.ar;
    }
    /** Panel 0 leaves {@code reserve} px at its left edge for the refresh button. */
    void matches(Canvas c,RectF r,LeagueFeed feed,int panel,float reserve) {
        long now=System.currentTimeMillis();
        String reason=emptyReason(feed);
        if(panel==0) {
            List<League.Match> all=feed.matches(),rows=new ArrayList<>();
            rows.addAll(League.live(all));
            if(rows.isEmpty())rows.addAll(League.recent(all,now,3));
            for(League.Match m:League.upcoming(all,now,3))if(rows.size()<3)rows.add(m);
            chips(c,r,feed,reserve);
            if(reason!=null){multiline(c,reason,r.left+40*k,r.top+104*k,r.width()-80*k,body,3);return;}
            if(rows.isEmpty()){multiline(c,"لا توجد مباريات قريبة لهذه البطولة (قد تكون في فترة توقف).",r.left+40*k,r.top+104*k,r.width()-80*k,body,3);return;}
            for(int i=0;i<Math.min(3,rows.size());i++)match(c,r,rows.get(i),r.top+(CompetitionTabs.MATCH_TOP+i*CompetitionTabs.MATCH_STEP)*k);
        } else if(panel==1) {
            Competitions.Competition comp=feed.current();
            if(comp!=null && !comp.hasTable()) {
                float right=r.right-26*k,width=r.width()-52*k;
                right(c,"الترتيب",right,r.top+60*k,title,width);
                multiline(c,"لا يُعرض جدول ترتيب لبطولات الكؤوس هنا.",r.left+26*k,r.top+104*k,width,meta,3);
                return;
            }
            table(c,r,reason==null?feed.table():new ArrayList<League.Row>(),6,false);
        } else {
            float left=r.left+30*k,right=r.right-30*k,width=right-left;
            right(c,heading(feed),right,r.top+60*k,title,width);
            multiline(c,"المصدر: ESPN",left,r.top+100*k,width,body,1);
            long at=feed.lastUpdateMs();
            int st=feed.gate.state(now);
            String status=st==RefreshGate.LOADING?"جارٍ التحديث…":st==RefreshGate.DONE?"تم التحديث الآن"
                    :st==RefreshGate.FAILED || feed.status()==Competitions.FAILED?"تعذّر التحديث، المعروض آخر نسخة محفوظة":at>0?"آخر تحديث "+News.ageAr(at,now)
                    :"بانتظار بيانات المباريات";
            int color=meta.getColor();
            if(st==RefreshGate.FAILED || (st==RefreshGate.IDLE && at>0 && feed.stale(now)))meta.setColor(0xFFFFC266);
            float y=r.top+150*k;
            y+=multiline(c,status+(st==RefreshGate.IDLE && at>0 && feed.stale(now)?" (بيانات قديمة)":""),left,y,width,meta,2)+18*k;
            meta.setColor(color);
            multiline(c,"زر التحديث ↻ يجلب أحدث النتائج.",left,y,width,meta,2);
        }
    }
    /**
     * The followed competitions as small glass chips inside the wide matches card; the one on screen is highlighted.
     * Remote left/right switches (LeagueFeed.step); with more than {@link CompetitionTabs#MAX_VISIBLE} followed the
     * window pages to keep the active chip visible. A green dot marks live matches for the chip on screen.
     */
    private void chips(Canvas c,RectF r,LeagueFeed feed,float reserve) {
        List<String> ids=feed.followed();
        int total=ids.size(),shown=CompetitionTabs.visible(total);
        if(shown==0)return;
        float pad=18*k,h=CompetitionTabs.HEIGHT*k,gap=CompetitionTabs.GAP*k;
        if(shown>=4)pad=12*k;
        float stripLeft=r.left+40*k+reserve;
        float stripRight=r.right-40*k;
        if(stripRight-stripLeft<shown*(h+gap))stripLeft=stripRight-shown*(h+gap);
        int active=Math.max(0,Math.min(total-1,feed.position()-1));
        int start=CompetitionTabs.start(total,active);
        boolean live=!League.live(feed.matches()).isEmpty();
        float w=CompetitionTabs.width(stripLeft,stripRight,shown);
        float top=r.top+CompetitionTabs.TOP*k;
        for(int slot=0;slot<shown;slot++) {
            int i=start+slot;
            if(i>=total)break;
            Competitions.Competition comp=Competitions.byId(ids.get(i));
            String label=comp==null?ids.get(i):CompetitionTabs.chipLabel(comp.ar,shown>=3);
            boolean on=i==active;
            float cellLeft=CompetitionTabs.left(stripLeft,stripRight,shown,slot);
            chipBox.set(cellLeft,top,cellLeft+w,top+h);
            fill.setShader(null);
            fill.setColor(on?0x3A30D158:0x1CFFFFFF);
            c.drawRoundRect(chipBox,h/2,h/2,fill);
            if(on){chipBorder.setColor(0x995BE37F);c.drawRoundRect(chipBox,h/2,h/2,chipBorder);}
            float dot=on&&live?18*k:0,textLeft=chipBox.left+pad,textRight=chipBox.right-pad-dot;
            chipTp.set(chipText);
            if(shown>=4)chipTp.setTextSize(chipTp.getTextSize()*0.84f);   // five names in one row: shrink, never clip a word
            String fitted=TextUtils.ellipsize(label,chipTp,Math.max(1,textRight-textLeft),TextUtils.TruncateAt.END).toString();
            chipTp.setTextAlign(Paint.Align.CENTER);
            chipTp.setColor(on?0xFFFFFFFF:0xFFD7DAE0);
            c.drawText(fitted,(textLeft+textRight)/2,chipBox.centerY()+chipTp.getTextSize()*0.35f,chipTp);
            if(dot>0){fill.setColor(0xFF5BE37F);c.drawCircle(chipBox.right-pad-3.5f*k,chipBox.centerY(),3.5f*k,fill);}
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
        right(c,heading(feed),matches.right-28*k,r.top+48*k,title,matches.width()-56*k);
        String reason=emptyReason(feed);
        List<League.Match> rows=new ArrayList<>(League.live(feed.matches()));
        for(League.Match m:League.upcoming(feed.matches(),System.currentTimeMillis(),2))if(rows.size()<2)rows.add(m);
        if(reason!=null)multiline(c,reason,matches.left+28*k,r.top+80*k,matches.width()-56*k,meta,4);
        else if(rows.isEmpty())right(c,"لا مباريات قريبة",matches.right-28*k,r.top+110*k,meta,matches.width()-50*k);
        else for(int i=0;i<Math.min(2,rows.size());i++)compactMatch(c,matches,rows.get(i),r.top+(65+i*112)*k);
        Competitions.Competition comp=feed.current();
        if(comp!=null && comp.hasTable() && reason==null)table(c,new RectF(r.left,r.top,mid-8*k,r.bottom),feed.table(),4,true);
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
        if(item==null){right(c,"الأخبار غير متاحة الآن · "+News.namesAr(),right,r.centerY()+10*k,meta,r.width()-60*k);c.restoreToCount(save);return;}
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
