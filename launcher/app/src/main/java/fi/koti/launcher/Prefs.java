package fi.koti.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The dock: an ordered list of package names. Edited on the TV (hold OK on a tile)
 * or over ADB:
 *   am start -n fi.koti.launcher/.MainActivity --es dock "pkg1,pkg2,..."
 */
final class Prefs {
    static final List<String> DEFAULT_DOCK = Arrays.asList(
            "com.apple.atve.androidtv.appletv",   // Apple TV
            "com.yle.webtv",                      // Yle Areena
            "fi.mtvkatsomo",                      // MTV Katsomo
            "com.netflix.ninja",                  // Netflix
            "com.google.android.youtube.tv");     // YouTube

    static final int MAX_DOCK = 7;

    private static final String FILE = "koti";
    private static final String KEY_DOCK = "dock";

    private final SharedPreferences sp;

    Prefs(Context context) {
        sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    List<String> dock() {
        String raw = sp.getString(KEY_DOCK, null);
        if (raw == null) return new ArrayList<>(DEFAULT_DOCK);
        List<String> out = new ArrayList<>();
        for (String p : raw.split(",")) {
            p = p.trim();
            if (!p.isEmpty() && !out.contains(p)) out.add(p);
        }
        return out;
    }

    void setDock(List<String> packages) {
        List<String> clean = new ArrayList<>();
        for (String p : packages) if (!clean.contains(p) && clean.size() < MAX_DOCK) clean.add(p);
        sp.edit().putString(KEY_DOCK, TextUtils.join(",", clean)).apply();
    }

    void resetDock() {
        sp.edit().remove(KEY_DOCK).apply();
    }

    /** Continue-watching items the family chose to hide (WatchNextRepository.Item.key()). */
    java.util.Set<String> hiddenItems() {
        return new java.util.HashSet<>(sp.getStringSet("hidden_items", new java.util.HashSet<>()));
    }

    void hideItem(String key) {
        java.util.Set<String> s = hiddenItems();
        s.add(key);
        sp.edit().putStringSet("hidden_items", s).apply();
    }

    void clearHidden() {
        sp.edit().remove("hidden_items").apply();
    }
}
