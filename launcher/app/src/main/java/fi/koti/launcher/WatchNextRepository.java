package fi.koti.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.media.tv.TvContract.WatchNextPrograms;
import android.text.TextUtils;
import android.util.Log;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads the Android TV "Watch Next" table that streaming apps fill with what you were
 * watching. Needs READ_TV_LISTINGS; without it TvProvider only returns our own rows.
 */
final class WatchNextRepository {
    private static final String TAG = "Koti.WatchNext";

    static final class Item {
        final long id;
        final String packageName;
        final String title;
        final String subtitle;     // "K2 J5 · Episode title" or null
        final String imageUri;
        final Intent intent;
        final float progress;      // 0..1, or -1 when unknown
        final long lastEngagement;
        final int watchNextType;   // CONTINUE, NEXT, NEW
        final int minutesLeft;     // -1 when unknown

        Item(long id, String packageName, String title, String subtitle, String imageUri,
             Intent intent, float progress, long lastEngagement, int watchNextType, int minutesLeft) {
            this.id = id;
            this.packageName = packageName;
            this.title = title;
            this.subtitle = subtitle;
            this.imageUri = imageUri;
            this.intent = intent;
            this.progress = progress;
            this.lastEngagement = lastEngagement;
            this.watchNextType = watchNextType;
            this.minutesLeft = minutesLeft;
        }

        /** Key for hiding an item: stable across the app re-publishing it. */
        String key() { return packageName + "|" + title.toLowerCase(); }
    }

    /** Rules for what the row shows. */
    static final int PER_APP = 3;
    static final float FINISHED = 0.95f;
    static final long MAX_AGE_MS = 90L * 24 * 60 * 60 * 1000;

    private static final String[] PROJECTION = {
            WatchNextPrograms._ID,
            WatchNextPrograms.COLUMN_PACKAGE_NAME,
            WatchNextPrograms.COLUMN_TITLE,
            WatchNextPrograms.COLUMN_EPISODE_TITLE,
            WatchNextPrograms.COLUMN_SEASON_DISPLAY_NUMBER,
            WatchNextPrograms.COLUMN_EPISODE_DISPLAY_NUMBER,
            WatchNextPrograms.COLUMN_POSTER_ART_URI,
            WatchNextPrograms.COLUMN_POSTER_ART_ASPECT_RATIO,
            WatchNextPrograms.COLUMN_THUMBNAIL_URI,
            WatchNextPrograms.COLUMN_INTENT_URI,
            WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS,
            WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS,
            WatchNextPrograms.COLUMN_DURATION_MILLIS,
            WatchNextPrograms.COLUMN_BROWSABLE,
            WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE,
    };

    private final Context context;

    WatchNextRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    boolean hasPermission() {
        return context.checkSelfPermission("android.permission.READ_TV_LISTINGS")
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Newest first, one per show, at most {@link #PER_APP} per app. Leaves out finished
     * items, watchlist entries, anything untouched for 90 days, and hidden keys.
     */
    List<Item> load(Map<String, AppEntry> installed, java.util.Set<String> hidden, int limit) {
        List<Item> all = new ArrayList<>();
        // TvProvider rejects a selection clause from callers without ACCESS_ALL_EPG_DATA,
        // so filter in code.
        try (Cursor c = context.getContentResolver().query(
                WatchNextPrograms.CONTENT_URI, PROJECTION, null, null, null)) {
            if (c == null) return all;
            while (c.moveToNext()) {
                String pkg = c.getString(1);
                if (pkg == null || !installed.containsKey(pkg)) continue;
                if (!c.isNull(13) && c.getInt(13) == 0) continue;   // not browsable
                Intent intent = parseIntent(c.getString(9));
                if (intent == null) continue;
                String title = c.getString(2);
                if (TextUtils.isEmpty(title)) continue;

                int type = c.isNull(14) ? WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE : c.getInt(14);
                if (type == WatchNextPrograms.WATCH_NEXT_TYPE_WATCHLIST) continue;
                long engaged = c.isNull(10) ? 0 : c.getLong(10);
                if (engaged > 0 && System.currentTimeMillis() - engaged > MAX_AGE_MS) continue;
                String image = pickImage(c.getString(6), c.isNull(7) ? -1 : c.getInt(7), c.getString(8));
                long pos = c.isNull(11) ? 0 : c.getLong(11);
                long dur = c.isNull(12) ? 0 : c.getLong(12);
                float progress = (pos > 0 && dur > 0) ? Math.min(1f, pos / (float) dur) : -1f;
                if (progress >= FINISHED) continue;
                int left = (pos > 0 && dur > pos) ? (int) Math.max(1, Math.round((dur - pos) / 60000.0)) : -1;
                Item item = new Item(c.getLong(0), pkg, title.trim(),
                        subtitle(c.getString(4), c.getString(5), c.getString(3)),
                        image, intent, progress, engaged, type, left);
                if (hidden.contains(item.key())) continue;
                all.add(item);
            }
        } catch (SecurityException e) {
            Log.w(TAG, "no access to watch next: " + e.getMessage());
        } catch (Exception e) {
            Log.w(TAG, "watch next query failed", e);
        }

        Collections.sort(all, (a, b) -> Long.compare(b.lastEngagement, a.lastEngagement));
        List<Item> out = new ArrayList<>();
        Set<String> shows = new HashSet<>();
        Map<String, Integer> perApp = new java.util.HashMap<>();
        for (Item it : all) {
            if (!shows.add(it.key())) continue;
            int n = perApp.containsKey(it.packageName) ? perApp.get(it.packageName) : 0;
            if (n >= PER_APP) continue;
            perApp.put(it.packageName, n + 1);
            out.add(it);
            if (out.size() >= limit) break;
        }
        return out;
    }

    private static Intent parseIntent(String uri) {
        if (TextUtils.isEmpty(uri)) return null;
        try {
            Intent i = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return i;
        } catch (URISyntaxException e) {
            return null;
        }
    }

    /** Tiles are 16:9: use 16:9 poster art when the app provides it, else its thumbnail. */
    private static String pickImage(String poster, int posterAspect, String thumb) {
        boolean posterIsWide = posterAspect == TvContract.PreviewPrograms.ASPECT_RATIO_16_9;
        if (!TextUtils.isEmpty(poster) && posterIsWide) return poster;
        if (!TextUtils.isEmpty(thumb)) return thumb;
        return poster;
    }

    private static String subtitle(String season, String episode, String episodeTitle) {
        StringBuilder sb = new StringBuilder();
        if (!TextUtils.isEmpty(season)) sb.append("K").append(season.trim());
        if (!TextUtils.isEmpty(episode)) {
            if (sb.length() > 0) sb.append(' ');
            sb.append("J").append(episode.trim());
        }
        if (!TextUtils.isEmpty(episodeTitle)) {
            if (sb.length() > 0) sb.append("  ·  ");
            sb.append(episodeTitle.trim());
        }
        return sb.length() == 0 ? null : sb.toString();
    }
}
