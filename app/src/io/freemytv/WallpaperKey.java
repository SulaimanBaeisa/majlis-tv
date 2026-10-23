package io.freemytv;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Content-addressed blur cache identity; changed pixels or renderer version cannot reuse an old blur. */
final class WallpaperKey {
    private WallpaperKey() {}
    static String of(byte[] bytes) {
        try {return of(new ByteArrayInputStream(bytes));}catch(IOException impossible){throw new IllegalStateException(impossible);}
    }
    static String of(InputStream in) throws IOException {
        try {
            MessageDigest d=MessageDigest.getInstance("SHA-256");
            d.update("wallpaper-v2|1920x1080|480x270|box6x3|scrim3|contrast130".getBytes(StandardCharsets.UTF_8));
            byte[] buf=new byte[32768];int n;while((n=in.read(buf))!=-1)d.update(buf,0,n);
            char[] hex="0123456789abcdef".toCharArray();StringBuilder s=new StringBuilder(64);
            for(byte b:d.digest()){s.append(hex[(b&255)>>>4]);s.append(hex[b&15]);}return s.toString();
        }catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
}
