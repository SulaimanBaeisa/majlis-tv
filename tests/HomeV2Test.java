package io.freemytv;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

/** Regression cases for the approved Sony v2 behaviour (synthetic XML, never displayed as live data). */
public final class HomeV2Test {
    static void eq(Object got,Object want) { if (!String.valueOf(got).equals(String.valueOf(want))) throw new AssertionError("got ["+got+"] want ["+want+"]"); }
    public static void main(String[] args) {
        eq(Info.clock12(LocalTime.of(0,7)),"12:07");
        eq(Info.clock12(LocalTime.of(12,0)),"12:00");
        eq(Info.clock12(LocalTime.of(23,36)),"11:36");
        eq(Info.ampm(LocalTime.of(0,0)),"ص"); eq(Info.ampm(LocalTime.of(12,0)),"م");
        for(int sdk:new int[]{30,31,32})eq(TvCompat.separateDream(sdk),true);
        for(int sdk:new int[]{28,29,33,34,35})eq(TvCompat.separateDream(sdk),false);
        League.Match m=new League.Match();m.state="in";m.home="الهلال";m.away="النصر";m.homeScore=2;m.awayScore=1;
        eq(League.scoreFor(m,true),"2");eq(League.scoreFor(m,false),"1");
        m.state="post";eq(League.scoreFor(m,true),"2");
        m.state="pre";eq(League.scoreFor(m,true),"–");
        String xml="<rss><channel><item><title><![CDATA[عنوان <b>مهم</b> - العربية]]></title><pubDate>Thu, 22 Oct 2026 05:21:00 GMT</pubDate><source url='https://www.alarabiya.net'>العربية</source></item>"
            +"<item><title>خبر &quot;جديد&quot; - الحدث</title><pubDate>Thu, 22 Oct 2026 06:00:00 GMT</pubDate><source url='https://alhadath.net'>الحدث</source></item>"
            +"<item><title>مرفوض</title><pubDate>Thu, 22 Oct 2026 06:00:00 GMT</pubDate><source url='https://alarabiya.net.evil.invalid'>العربية</source></item></channel></rss>";
        List<News.Item> list=News.parse(xml,"اقتصاد");
        eq(list.size(),2);eq(list.get(0).title,"عنوان مهم");eq(list.get(1).title,"خبر \"جديد\"");
        eq(list.get(1).source,"الحدث");eq(list.get(0).category,"اقتصاد");
        eq(News.top(Arrays.asList(list,list),30).size(),2);
        eq(News.top(Arrays.asList(list),1).get(0).source,"الحدث");
        eq(News.parse("<!DOCTYPE rss [<!ENTITY a SYSTEM 'file:///etc/passwd'>]><rss/>","عام").size(),0);
        eq(News.parse("not xml","عام").size(),0);
        eq(News.parse("<rss><channel><item><title>x</title><pubDate>bad</pubDate><source url='https://alarabiya.net'>العربية</source></item></channel></rss>","عام").size(),0);
        eq(News.ageAr(list.get(1).publishedMs,list.get(1).publishedMs+20*60_000),"قبل 20 دقيقة");
        eq(News.ageAr(list.get(1).publishedMs,list.get(1).publishedMs+60*60_000),"قبل ساعة");
        eq(WallpaperKey.of(new byte[]{1,2,3}),WallpaperKey.of(new byte[]{1,2,3}));
        eq(WallpaperKey.of(new byte[]{1,2,3}).equals(WallpaperKey.of(new byte[]{1,2,4})),false);
        eq(WallpaperKey.of(new byte[]{1}).matches("[0-9a-f]{64}"),true);
        System.out.println("PASS HomeV2Test (clock, paired scores, safe RSS, dedupe, cache invalidation)");
    }
}
