package fi.koti.launcher;

import android.content.Context;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.media.tv.TvContract.PreviewPrograms;
import android.text.TextUtils;
import android.util.Log;
import android.util.LongSparseArray;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * "Top shelf": the artwork each app publishes in its Android TV preview channels (the
 * rows the stock launcher showed). Koti shows the focused dock app's art as the
 * backdrop. Only browsable channels count: Apple TV leaves hundreds of hidden, stale
 * channels behind.
 */
final class ShelfRepository {
    private static final String TAG = "Koti.Shelf";

    static final class Art {
        final String title;
        final String imageUri;     // backdrop-sized where the CDN allows
        final String original;     // as published, the fallback
        Art(String title, String imageUri, String original) {
            this.title = title; this.imageUri = imageUri; this.original = original;
        }
    }

    private final Context context;

    ShelfRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    /** Art per package, in the order the app ranks it. */
    Map<String, List<Art>> load() {
        Map<String, List<Art>> out = new HashMap<>();
        LongSparseArray<String> browsable = new LongSparseArray<>();
        try (Cursor c = context.getContentResolver().query(TvContract.Channels.CONTENT_URI,
                new String[]{TvContract.Channels._ID, TvContract.Channels.COLUMN_PACKAGE_NAME,
                        TvContract.Channels.COLUMN_TYPE, TvContract.Channels.COLUMN_BROWSABLE},
                null, null, null)) {
            while (c != null && c.moveToNext()) {
                if (TvContract.Channels.TYPE_PREVIEW.equals(c.getString(2)) && c.getInt(3) == 1) {
                    browsable.put(c.getLong(0), c.getString(1));
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "channels", e);
            return out;
        }
        try (Cursor c = context.getContentResolver().query(PreviewPrograms.CONTENT_URI,
                new String[]{PreviewPrograms.COLUMN_CHANNEL_ID, PreviewPrograms.COLUMN_TITLE,
                        PreviewPrograms.COLUMN_POSTER_ART_URI, PreviewPrograms.COLUMN_POSTER_ART_ASPECT_RATIO,
                        PreviewPrograms.COLUMN_THUMBNAIL_URI, PreviewPrograms.COLUMN_THUMBNAIL_ASPECT_RATIO,
                        PreviewPrograms.COLUMN_WEIGHT, PreviewPrograms.COLUMN_BROWSABLE},
                null, null, null)) {
            while (c != null && c.moveToNext()) {
                String pkg = browsable.get(c.getLong(0));
                if (pkg == null || (!c.isNull(7) && c.getInt(7) == 0)) continue;
                String uri = pickWide(c.getString(2), c.isNull(3) ? -1 : c.getInt(3),
                        c.getString(4), c.isNull(5) ? -1 : c.getInt(5));
                if (TextUtils.isEmpty(uri)) continue;
                List<Art> list = out.get(pkg);
                if (list == null) out.put(pkg, list = new ArrayList<>());
                list.add(new Art(c.getString(1), upsize(uri), uri));
            }
        } catch (Exception e) {
            Log.w(TAG, "programs", e);
        }
        for (Map.Entry<String, List<Art>> e : out.entrySet()) {
            Log.i(TAG, e.getKey() + ": " + e.getValue().size() + " art, first " + e.getValue().get(0).imageUri);
        }
        return out;
    }

    /** Prefer 16:9 art; portrait posters make poor backdrops. */
    private static String pickWide(String poster, int posterAspect, String thumb, int thumbAspect) {
        int wide = PreviewPrograms.ASPECT_RATIO_16_9;
        if (!TextUtils.isEmpty(poster) && (posterAspect == wide || posterAspect == -1)) return poster;
        if (!TextUtils.isEmpty(thumb) && (thumbAspect == wide || thumbAspect == -1)) return thumb;
        return null;
    }

    /** Ask image CDNs that size by URL for a backdrop-sized image instead of a thumbnail. */
    static String upsize(String uri) {
        // Yle: .../f_webp,crop_fill,w_512,focus_center/...
        String u = uri.replaceFirst(",w_\\d+,", ",w_1920,");
        // Apple (mzstatic): .../{w}x{h}{crop}.{ext}
        u = u.replaceFirst("/(\\d{2,4})x(\\d{2,4})([a-z]{0,4})\\.(jpg|jpeg|png|webp)$", "/1920x1080$3.$4");
        // YouTube: .../vi/{id}/hq720.jpg?... -> the full-HD frame (callers fall back if absent)
        u = u.replaceFirst("^(https://i\\.ytimg\\.com/vi/[^/]+)/(hq720|hqdefault|mqdefault|sddefault)\\.jpg.*$",
                "$1/maxresdefault.jpg");
        return u;
    }
}
