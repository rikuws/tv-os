package fi.koti.launcher;

import android.content.Context;
import android.provider.Settings;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

/*
 * MOTION STORYBOARD
 *
 *   focus lands on a tile   tile rises LIFT dp, grows to FOCUS_SCALE and gains elevation
 *                           (deeper shadow) on one spring family; the un-dim fades up
 *                           over LIGHT_MS; a specular sheen fades in
 *   dock focus rests 380 ms  backdrop crossfades to that app's art (after the focus spring
 *                           has mostly settled: the two overlapping overloads this GPU)
 *   sidebar opens           panel grows from its corner (0.9 -> 1) as content dims
 *   focus leaves            same springs retarget back to rest from wherever they are
 *   row scroll              DecelerateInterpolator over SCROLL_MS
 *   caption swap            old caption out / new caption in, CAPTION_MS, overlapping
 *   sheet open              slides up SHEET_RISE dp + fades in over SHEET_MS
 *
 * Springs are interruptible: D-pad mashing never queues animations, it retargets them.
 * With "Animation scale: off" in developer options every value snaps.
 */
final class Motion {
    static final float FOCUS_SCALE = 1.10f;       // dock tile
    static final float POSTER_SCALE = 1.10f;      // continue-watching tile
    static final float LIFT_DP = 7f;

    static final float SPRING_STIFFNESS = 520f;   // between StiffnessLow and Medium
    static final float SPRING_DAMPING = 0.72f;    // one soft overshoot, settles ~250 ms

    static final int LIGHT_MS = 180;
    static final int SCROLL_MS = 260;
    static final int CAPTION_MS = 140;
    static final int SHEET_MS = 220;
    static final float SHEET_RISE_DP = 24f;

    static final Interpolator SCROLL_INTERPOLATOR = new DecelerateInterpolator(2.2f);
    static final Interpolator EASE_OUT = new DecelerateInterpolator(2f);

    private Motion() {}

    private static Boolean reducedCache;

    /** Animator scale 0 (developer options). Cached; {@link #refresh} re-reads it. */
    static boolean reduced(Context c) {
        if (reducedCache == null) refresh(c);
        return reducedCache;
    }

    static void refresh(Context c) {
        reducedCache = Settings.Global.getFloat(c.getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
    }

    /** Spring a view property to {@code target}, reusing one animation per property. */
    static void spring(View v, DynamicAnimation.ViewProperty prop, float target) {
        int key = keyFor(prop);
        Object tag = v.getTag(key);
        SpringAnimation anim;
        if (tag instanceof SpringAnimation) {
            anim = (SpringAnimation) tag;
        } else {
            anim = new SpringAnimation(v, prop);
            anim.setSpring(new SpringForce()
                    .setStiffness(SPRING_STIFFNESS)
                    .setDampingRatio(SPRING_DAMPING));
            v.setTag(key, anim);
        }
        if (reduced(v.getContext())) {
            anim.cancel();
            prop.setValue(v, target);
            return;
        }
        anim.animateToFinalPosition(target);
    }

    private static int keyFor(DynamicAnimation.ViewProperty prop) {
        if (prop == DynamicAnimation.SCALE_X) return R.id.spring_scale_x;
        if (prop == DynamicAnimation.SCALE_Y) return R.id.spring_scale_y;
        if (prop == DynamicAnimation.TRANSLATION_Y) return R.id.spring_translation_y;
        if (prop == DynamicAnimation.TRANSLATION_X) return R.id.spring_translation_x;
        if (prop == DynamicAnimation.Z) return R.id.spring_z;
        return R.id.spring_other;
    }

    static float dp(Context c, float dp) {
        return dp * c.getResources().getDisplayMetrics().density;
    }
}
