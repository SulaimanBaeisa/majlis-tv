package io.freemytv;

import java.io.StringReader;
import java.net.URI;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

/** Bounded, source-validated RSS headlines. Pure Java; no network access during parsing. */
final class News {
    private News() {}
    static final class Item {
        final String title, source, category;
        final long publishedMs;
        Item(String title,String source,String category,long publishedMs) {
            this.title=title;this.source=source;this.category=category;this.publishedMs=publishedMs;
        }
    }
    static List<Item> parse(String xml,String category) {
        List<Item> out=new ArrayList<>();
        if (xml==null || xml.length()>1_000_000 || xml.toUpperCase(Locale.ROOT).contains("<!DOCTYPE")) return out;
        try {
            DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();
            f.setExpandEntityReferences(false);f.setValidating(false);
            DocumentBuilder b=f.newDocumentBuilder();
            b.setEntityResolver((publicId,systemId)->new InputSource(new StringReader("")));
            b.setErrorHandler(new DefaultHandler());
            NodeList items=b.parse(new InputSource(new StringReader(xml))).getElementsByTagName("item");
            for (int n=0;n<items.getLength() && out.size()<60;n++) {
                Element e=(Element)items.item(n);
                NodeList sources=e.getElementsByTagName("source");
                if (sources.getLength()!=1) continue;
                Element source=(Element)sources.item(0);
                String host;
                try {host=URI.create(source.getAttribute("url")).getHost();}catch(RuntimeException ex){continue;}
                String name=sourceName(host);
                if (name==null) continue;
                String title=clean(text(e,"title"));
                long date;
                try {date=ZonedDateTime.parse(text(e,"pubDate").trim(),DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli();}
                catch(RuntimeException ex){continue;}
                if (!title.isEmpty() && title.length()<=500 && date>0) out.add(new Item(title,name,category,date));
            }
        } catch(Exception ignored) { /* malformed/unavailable feeds never break the home screen */ }
        return Collections.unmodifiableList(out);
    }
    private static String sourceName(String host) {
        if (host==null) return null;
        String h=host.toLowerCase(Locale.ROOT);
        if (h.equals("alarabiya.net") || h.equals("www.alarabiya.net")) return "العربية";
        if (h.equals("alhadath.net") || h.equals("www.alhadath.net")) return "الحدث";
        return null;
    }
    private static String text(Element e,String tag) {
        NodeList n=e.getElementsByTagName(tag);return n.getLength()==0?"":n.item(0).getTextContent();
    }
    static String clean(String s) {
        String t=s.replaceAll("<[^>]*>"," ").replace("&quot;","\"").replace("&#39;","'").replace("&amp;","&")
                .replaceAll("[\\p{Cntrl}\\u202A-\\u202E\\u2066-\\u2069]"," ").replaceAll("\\s+"," ").trim();
        for(String suffix:new String[]{" - العربية"," - الحدث"," | العربية"," - Al Arabiya"," - Al Hadath"})
            if(t.endsWith(suffix))t=t.substring(0,t.length()-suffix.length()).trim();
        return t;
    }
    static List<Item> top(List<List<Item>> groups,int max) {
        List<Item> all=new ArrayList<>();for(List<Item> g:groups)all.addAll(g);
        Collections.sort(all,(a,b)->Long.compare(b.publishedMs,a.publishedMs));
        List<Item> result=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(Item i:all){if(result.size()>=max)break;if(seen.add(i.title))result.add(i);}
        return Collections.unmodifiableList(result);
    }
    /** Round-robin categories so general/politics cannot crowd health and football out of the ticker. */
    static List<Item> balanced(List<List<Item>> groups,int max) {
        List<Item> out=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(int row=0;row<60 && out.size()<max;row++)for(List<Item> g:groups)
            if(row<g.size() && out.size()<max && seen.add(g.get(row).title))out.add(g.get(row));
        return Collections.unmodifiableList(out);
    }
    static String ageAr(long published,long now) {
        long minutes=Math.max(1,(now-published)/60_000L);
        if(minutes<60)return "قبل "+Arabic.unit(minutes,"دقيقة","دقيقتين","دقائق","دقيقة");
        return "قبل "+Arabic.unit(minutes/60,"ساعة","ساعتين","ساعات","ساعة");
    }
}
