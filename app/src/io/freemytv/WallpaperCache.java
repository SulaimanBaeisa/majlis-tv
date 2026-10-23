package io.freemytv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.os.SystemClock;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

/** One worker-side preparation per wallpaper; one content-addressed disk record, four-record bounded cache. */
final class WallpaperCache {
    private static final int MAGIC=0x57503231;
    private WallpaperCache() {}
    static Backdrop load(Context context,File source) throws IOException {
        long start=SystemClock.elapsedRealtime();String key;
        try(FileInputStream in=new FileInputStream(source)){key=WallpaperKey.of(in);}
        File dir=new File(context.getCacheDir(),"wallpaper-v2");dir.mkdirs();
        File record=new File(dir,key+".wall");
        if(record.isFile())try {
            Backdrop cached=read(record);
            record.setLastModified(System.currentTimeMillis());
            AppLog.w("wallpaper disk HIT: "+(SystemClock.elapsedRealtime()-start)+" ms");
            return cached;
        }catch(IOException | RuntimeException ignored){record.delete();}
        Bitmap b=decode(source);
        Backdrop result=Backdrop.wallpaper(b);
        try {write(record,result);prune(dir);}catch(IOException ex){AppLog.w("wallpaper cache write unavailable");}
        AppLog.w("wallpaper prepared: "+(SystemClock.elapsedRealtime()-start)+" ms; blur 480x270, no draw-time blur");
        return result;
    }
    private static Bitmap decode(File file) throws IOException {
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
        BitmapFactory.decodeFile(file.getPath(),bounds);
        if(bounds.outWidth<=0 || bounds.outHeight<=0)throw new IOException("unreadable wallpaper");
        BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=1;opts.inPreferredConfig=Bitmap.Config.ARGB_8888;
        while((bounds.outWidth/(opts.inSampleSize*2)>=Backdrop.WALL_W && bounds.outHeight/(opts.inSampleSize*2)>=Backdrop.WALL_H)
                || ((long)bounds.outWidth*bounds.outHeight)/(opts.inSampleSize*(long)opts.inSampleSize)>8_000_000L)opts.inSampleSize*=2;
        Bitmap b=BitmapFactory.decodeFile(file.getPath(),opts);
        if(b==null)throw new IOException("unreadable wallpaper");
        return orient(file,b);
    }
    static Bitmap orient(File file,Bitmap b) {
        int orientation=1;
        try{orientation=new ExifInterface(file.getPath()).getAttributeInt(ExifInterface.TAG_ORIENTATION,1);}catch(IOException ignored){}
        Matrix m=new Matrix();
        switch(orientation) {
            case 2:m.setScale(-1,1);break;
            case 3:m.setRotate(180);break;
            case 4:m.setScale(1,-1);break;
            case 5:m.setRotate(90);m.postScale(-1,1);break;
            case 6:m.setRotate(90);break;
            case 7:m.setRotate(270);m.postScale(-1,1);break;
            case 8:m.setRotate(270);break;
            default:return b;
        }
        Bitmap rotated=Bitmap.createBitmap(b,0,0,b.getWidth(),b.getHeight(),m,true);
        if(rotated!=b)b.recycle();return rotated;
    }
    private static Backdrop read(File file) throws IOException {
        if(file.length()>20_000_000L)throw new IOException("oversized cache");
        try(DataInputStream in=new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            if(in.readInt()!=MAGIC || in.readInt()!=Backdrop.WALL_W || in.readInt()!=Backdrop.WALL_H)throw new IOException("old cache");
            int[] frost=readArray(in,Backdrop.FW*Backdrop.FH),glass=readArray(in,Backdrop.GW*Backdrop.GH);
            Bitmap bitmap=BitmapFactory.decodeStream(in);
            if(bitmap==null)throw new IOException("bad cache bitmap");
            if(bitmap.getWidth()!=Backdrop.WALL_W || bitmap.getHeight()!=Backdrop.WALL_H){bitmap.recycle();throw new IOException("bad cache size");}
            return new Backdrop(frost,glass,bitmap);
        }
    }
    private static int[] readArray(DataInputStream in,int count) throws IOException {
        if(in.readInt()!=count)throw new IOException("bad cache array");
        int[] out=new int[count];for(int i=0;i<count;i++)out[i]=in.readInt();return out;
    }
    private static void write(File target,Backdrop b) throws IOException {
        File part=new File(target.getPath()+".part");
        try {
            try(DataOutputStream out=new DataOutputStream(new BufferedOutputStream(new FileOutputStream(part)))) {
                out.writeInt(MAGIC);out.writeInt(Backdrop.WALL_W);out.writeInt(Backdrop.WALL_H);
                out.writeInt(b.frost.length);for(int v:b.frost)out.writeInt(v);
                out.writeInt(b.lightGlass.length);for(int v:b.lightGlass)out.writeInt(v);
                if(!b.lightImage.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("PNG encoding failed");
            }
            if(!part.renameTo(target))throw new IOException("cache rename failed");
        }finally{if(part.exists())part.delete();}
    }
    private static void prune(File dir) {
        File[] files=dir.listFiles((d,n)->n.matches("[0-9a-f]{64}\\.wall"));if(files==null)return;
        Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified()));long bytes=0;
        for(int i=0;i<files.length;i++){bytes+=files[i].length();if(i>=4 || bytes>36*1024*1024L)files[i].delete();}
    }
}
