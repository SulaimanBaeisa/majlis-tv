package io.freemytv;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Public RSS only. No account, key, webview, article scraping, or foreground network work. */
final class NewsFeed {
    static final String[] CATEGORIES={"عام","صحة وطب","اقتصاد","كرة القدم","سياسة","عام"};
    private static final String[] TERMS={"","(صحة OR طب OR دواء)","(اقتصاد OR أسواق)","(كرة القدم OR الدوري السعودي)","(سياسة OR دبلوماسية)",""};
    private static final long EVERY=15*60_000L, RETRY=3*60_000L;
    static final class Snapshot {
        final List<List<News.Item>> groups;
        final List<News.Item> top,ticker;
        final long updated;
        final int version;
        Snapshot(List<List<News.Item>> groups,long updated,int version) {
            this.groups=Collections.unmodifiableList(new ArrayList<>(groups));
            this.top=News.top(groups,30);this.ticker=News.balanced(groups,30);
            this.updated=updated;this.version=version;
        }
    }
    private static NewsFeed instance;
    static synchronized NewsFeed get(Context c) {if(instance==null)instance=new NewsFeed(c.getApplicationContext());return instance;}
    private final File dir;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final List<Runnable> listeners=new ArrayList<>();
    private final List<List<News.Item>> groups=new ArrayList<>();
    private boolean busy,loaded;
    private long lastAttempt=-EVERY,interval=EVERY,updated;
    private int version;
    volatile Snapshot snapshot;
    private NewsFeed(Context c) {
        dir=new File(c.getCacheDir(),"news-v1");
        for(String ignored:CATEGORIES)groups.add(Collections.emptyList());
        snapshot=new Snapshot(groups,0,0);
    }
    void addListener(Runnable r){if(!listeners.contains(r))listeners.add(r);}
    void removeListener(Runnable r){listeners.remove(r);}
    void refreshIfStale() {
        synchronized(this) {
            long now=SystemClock.elapsedRealtime();
            if(busy || loaded && now-lastAttempt<interval)return;
            busy=true;lastAttempt=now;
        }
        io.execute(()->{
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            int ok=0;
            try {
                if(!loaded) {
                    for(int i=0;i<CATEGORIES.length;i++) {
                        File f=new File(dir,i+".xml");
                        try(FileInputStream in=new FileInputStream(f)) {
                            List<News.Item> parsed=fresh(News.parse(read(in),CATEGORIES[i]));
                            groups.set(i,parsed);updated=Math.max(updated,f.lastModified());
                        } catch(IOException ignored){}
                    }
                    loaded=true;publish();
                    boolean complete=true;for(List<News.Item> g:groups)complete &= !g.isEmpty();
                    if(complete && updated>0 && System.currentTimeMillis()-updated<EVERY)return;
                }
                dir.mkdirs();
                for(int i=0;i<CATEGORIES.length;i++) {
                    try {
                        String xml=fetch(i);
                        List<News.Item> parsed=fresh(News.parse(xml,CATEGORIES[i]));
                        if(parsed.isEmpty())throw new IOException("empty validated feed");
                        groups.set(i,parsed);updated=System.currentTimeMillis();ok++;
                        File part=new File(dir,i+".xml.part"),dest=new File(dir,i+".xml");
                        try(FileOutputStream out=new FileOutputStream(part)){out.write(xml.getBytes(StandardCharsets.UTF_8));}
                        if(!part.renameTo(dest))part.delete();
                    } catch(IOException | RuntimeException ex) {
                        groups.set(i,fresh(groups.get(i)));
                        AppLog.w("news category "+i+" unavailable: "+ex.getClass().getSimpleName());
                    }
                }
                interval=ok==0?RETRY:EVERY;
                publish();
                AppLog.w("news: "+snapshot.top.size()+" verified headlines; "+ok+"/"+CATEGORIES.length+" feeds refreshed");
            } finally {synchronized(this){busy=false;}}
        });
    }
    private static List<News.Item> fresh(List<News.Item> items) {
        long now=System.currentTimeMillis();List<News.Item> out=new ArrayList<>();
        for(News.Item n:items)if(n.publishedMs<=now+10*60_000L && now-n.publishedMs<=48*3600_000L)out.add(n);
        return Collections.unmodifiableList(out);
    }
    private void publish() {
        snapshot=new Snapshot(groups,updated,++version);
        main.post(()->{for(Runnable r:new ArrayList<>(listeners))r.run();});
    }
    private static String fetch(int category) throws IOException {
        String q=(category==5?"site:alhadath.net":"(site:alarabiya.net OR site:alhadath.net)")+" "+TERMS[category]+" when:1d";
        URL url=new URL("https://news.google.com/rss/search?q="+URLEncoder.encode(q,"UTF-8")+"&hl=ar&gl=SA&ceid=SA:ar");
        HttpURLConnection c=(HttpURLConnection)url.openConnection();
        c.setConnectTimeout(6000);c.setReadTimeout(8000);c.setInstanceFollowRedirects(false);
        c.setRequestProperty("Accept","application/rss+xml, application/xml");
        try {
            if(c.getResponseCode()!=200)throw new IOException("RSS HTTP "+c.getResponseCode());
            try(InputStream in=c.getInputStream()){return read(in);}
        }finally{c.disconnect();}
    }
    private static String read(InputStream in) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;
        while((n=in.read(b))!=-1){if(out.size()+n>1_000_000)throw new IOException("RSS too large");out.write(b,0,n);}
        return new String(out.toByteArray(),StandardCharsets.UTF_8);
    }
}
