package fi.koti.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Enumerates apps that can be launched from a TV home screen. */
final class AppRepository {

    /** Launcher entries that only duplicate something the home screen already offers. */
    private static final Set<String> HIDDEN = new HashSet<>();
    static {
        HIDDEN.add("fi.koti.launcher");
        HIDDEN.add("com.google.android.tvlauncher");
        HIDDEN.add("com.google.android.leanbacklauncher");
        HIDDEN.add("com.android.tv.settings");          // reached from the top bar
    }

    private final Context context;

    AppRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    private static Drawable hiRes(PackageManager pm, ActivityInfo ai, int resId) {
        if (resId == 0) return null;
        try {
            return pm.getResourcesForApplication(ai.applicationInfo)
                    .getDrawableForDensity(resId, android.util.DisplayMetrics.DENSITY_XXXHIGH, null);
        } catch (Exception e) {
            return null;
        }
    }

    /** All launchable apps keyed by package, in a stable alphabetical order. */
    Map<String, AppEntry> loadAll() {
        PackageManager pm = context.getPackageManager();
        Map<String, AppEntry> out = new LinkedHashMap<>();

        List<ResolveInfo> found = new ArrayList<>();
        found.addAll(pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0));
        found.addAll(pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0));

        List<AppEntry> entries = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : found) {
            ActivityInfo ai = ri.activityInfo;
            if (ai == null || HIDDEN.contains(ai.packageName) || !seen.add(ai.packageName)) continue;
            // Highest-density art the app ships, so focused (scaled-up) tiles stay sharp.
            int bannerRes = ai.banner != 0 ? ai.banner : ai.applicationInfo.banner;
            Drawable banner = hiRes(pm, ai, bannerRes);
            if (banner == null) banner = ai.loadBanner(pm);
            if (banner == null) banner = ai.applicationInfo.loadBanner(pm);
            Drawable icon = hiRes(pm, ai, ai.getIconResource());
            if (icon == null) icon = ai.loadIcon(pm);
            String label = String.valueOf(ri.loadLabel(pm)).trim();
            entries.add(new AppEntry(ai.packageName, new ComponentName(ai.packageName, ai.name),
                    label, banner, icon));
        }

        Collator collator = Collator.getInstance(new Locale("fi", "FI"));
        Collections.sort(entries, (a, b) -> collator.compare(a.label, b.label));
        for (AppEntry e : entries) out.put(e.packageName, e);
        return out;
    }
}
