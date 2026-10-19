package fi.koti.launcher;

import android.content.Context;
import android.graphics.Typeface;
import android.util.Log;
import android.widget.TextView;

/**
 * Inter (OFL, variable weight + optical size), the closest open relative of Apple's SF
 * Pro. Text sizes use the 14 pt optical size; the clock uses the 32 pt display cut.
 */
final class Type {
    static final int REGULAR = 400;
    static final int MEDIUM = 500;
    static final int SEMIBOLD = 600;
    static final int BOLD = 700;

    private static final Typeface[] TEXT = new Typeface[10];
    private static final Typeface[] DISPLAY = new Typeface[10];

    private Type() {}

    static Typeface face(Context c, int weight) {
        return get(c, weight, 14, TEXT);
    }

    static Typeface display(Context c, int weight) {
        return get(c, weight, 32, DISPLAY);
    }

    private static Typeface get(Context c, int weight, int opsz, Typeface[] cache) {
        int i = weight / 100;
        if (cache[i] == null) {
            try {
                cache[i] = new Typeface.Builder(c.getAssets(), "fonts/Inter.ttf")
                        .setFontVariationSettings("'wght' " + weight + ", 'opsz' " + opsz)
                        .setWeight(weight)
                        .build();
            } catch (Exception e) {
                Log.w("Koti.Type", "font load failed", e);
                cache[i] = Typeface.create("sans-serif", Typeface.NORMAL);
            }
        }
        return cache[i];
    }

    static void apply(TextView v, int weight) {
        v.setTypeface(face(v.getContext(), weight));
    }
}
