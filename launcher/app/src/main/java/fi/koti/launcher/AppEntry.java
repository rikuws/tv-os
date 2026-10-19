package fi.koti.launcher;

import android.content.ComponentName;
import android.content.Intent;
import android.graphics.drawable.Drawable;

/** One launchable app: label, banner (Android TV 16:9 art) or icon fallback. */
final class AppEntry {
    final String packageName;
    final ComponentName component;
    final String label;
    final Drawable banner;   // may be null; then icon is drawn on a plate
    final Drawable icon;

    AppEntry(String packageName, ComponentName component, String label, Drawable banner, Drawable icon) {
        this.packageName = packageName;
        this.component = component;
        this.label = label;
        this.banner = banner;
        this.icon = icon;
    }

    Intent launchIntent() {
        return new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .setComponent(component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
    }
}
