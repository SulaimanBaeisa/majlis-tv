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
    /**
     * The only publishers whose headlines are shown: {site, Arabic name, English name}. NewsFeed builds its Google News
     * RSS queries from this list and the parser drops any item from another site, so the credits in Settings > About
     * (credits()) are generated from what is really fetched.
     */
    static final String[][] SOURCES = {
        {"alarabiya.net", "العربية", "Al Arabiya"},
        {"alhadath.net", "الحدث", "Al Hadath"},
    };
    /** The feed the headlines come through (public RSS search, no account or key). */
    static final String AGGREGATOR_AR = "أخبار Google ‏(RSS)", AGGREGATOR_EN = "Google News (RSS)";

    static String sourceName(String host) {
        if (host==null) return null;
        String h=host.toLowerCase(Locale.ROOT);
        for (String[] s:SOURCES) if (h.equals(s[0]) || h.equals("www."+s[0])) return s[1];
        return null;
    }

    /** "site:alarabiya.net OR site:alhadath.net" (or one site), for the RSS search. */
    static String siteQuery(boolean onlyLast) {
        if (onlyLast) return "site:"+SOURCES[SOURCES.length-1][0];
        StringBuilder sb=new StringBuilder("(");
        for (int i=0;i<SOURCES.length;i++) sb.append(i==0?"":" OR ").append("site:").append(SOURCES[i][0]);
        return sb.append(")").toString();
    }

    /** "العربية والحدث": the publishers' names, for short notices. */
    static String namesAr() {
        StringBuilder sb=new StringBuilder();
        for (int i=0;i<SOURCES.length;i++) sb.append(i==0?"":i==SOURCES.length-1?" و":"، ").append(SOURCES[i][1]);
        return sb.toString();
    }

    /** The news credit line for Settings > About, from SOURCES. */
    static String credits(boolean arabic) {
        StringBuilder sb=new StringBuilder();
        for (int i=0;i<SOURCES.length;i++) {
            sb.append(i==0?"":arabic?"، ":", ").append(arabic?SOURCES[i][1]:SOURCES[i][2]).append(" (").append(SOURCES[i][0]).append(")");
        }
        return arabic ? "عناوين الأخبار: "+sb+" عبر "+AGGREGATOR_AR+". الحقوق لأصحابها، وتُعرض العناوين فقط."
                : "News headlines: "+sb+" via "+AGGREGATOR_EN+". Rights belong to their publishers; only headlines are shown.";
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
