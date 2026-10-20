package io.freemytv;

import java.util.ArrayList;
import java.util.List;

/**
 * The screensaver's own photos of Saudi Arabia (assets/saudi/, from Wikimedia Commons, each under a free license;
 * credits in assets/saudi/credits.tsv and PHOTO-CREDITS.md). Used when there is no TMDB key. Pure Java.
 */
final class SaudiPhotos {
    private SaudiPhotos() {}

    static final class Photo {
        String file, title, place, artist, license, source;

        /** "جدة · Sammy Six · CC BY 2.0": the line under the title on the screensaver (also the credit). */
        String meta() { return place + " · " + artist + " · " + license; }
    }

    /** Lines of "file TAB title TAB place TAB photographer TAB license TAB source"; '#' lines and short lines are skipped. */
    static List<Photo> parse(String tsv) {
        List<Photo> out = new ArrayList<>();
        for (String line : tsv.split("\n")) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split("\t");
            if (f.length < 6) continue;
            Photo p = new Photo();
            p.file = f[0]; p.title = f[1]; p.place = f[2]; p.artist = f[3]; p.license = f[4]; p.source = f[5];
            out.add(p);
        }
        return out;
    }

    /** The photo of the day (the home wallpaper): the first of the daily rotation; null with no photos. */
    static Photo today(List<Photo> list, long day) {
        return list.isEmpty() ? null : rotate(list, day).get(0);
    }

    /** The list starting at a different photo each day. */
    static List<Photo> rotate(List<Photo> list, long day) {
        List<Photo> out = new ArrayList<>(list.size());
        if (list.isEmpty()) return out;
        int start = (int) Math.floorMod(day, (long) list.size());
        for (int i = 0; i < list.size(); i++) out.add(list.get((start + i) % list.size()));
        return out;
    }
}
