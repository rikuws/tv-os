package fi.koti.launcher;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

/*
 * FOCUS STORYBOARD (tvOS-style, shared by app tiles and continue cards)
 *
 *     0 ms   focus lands: scale -> FOCUS_SCALE, rise LIFT, elevation +Z, all on one spring
 *     0 ms   specular sheen starts outside the top-left corner...
 *   450 ms   ...and has slid into place, like light catching the card as it lifts
 *   press    OK held down: card sinks to ~95% of its focused size; release springs back
 *   wall     D-pad pushes past a row end: card nudges 6 dp that way and springs home,
 *            so the edge is felt instead of the press being silently swallowed
 *
 * Springs retarget mid-flight, so D-pad mashing never queues motion. With animator
 * scale off, everything snaps and the glint and nudge are skipped.
 */
final class FocusFx {
    /** A focusable tile that can acknowledge a press against a row wall. */
    interface Host {
        void nudge(int dirX, int dirY);
    }

    static final float PRESS = 0.955f;
    static final float NUDGE_DP = 6f;
    static final int GLINT_MS = 450;

    private final View v;
    private final float scale, lift, z, d;
    private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix sheenMatrix = new Matrix();
    private LinearGradient sheen;
    private int sheenW, sheenH;
    private final int sheenAlpha;
    private final SpringAnimation focusSpring;
    private ValueAnimator glintAnim;
    private float focus;            // 0 resting .. 1 focused (spring)
    private float glint = 1f;       // 0 sheen outside the card .. 1 in place
    private boolean focused;

    FocusFx(View v, float focusScale, float liftDp, float zDp, int sheenAlpha) {
        this.v = v;
        this.d = v.getResources().getDisplayMetrics().density;
        this.scale = focusScale;
        this.lift = liftDp * d;
        this.z = zDp * d;
        this.sheenAlpha = sheenAlpha;
        FloatValueHolder holder = new FloatValueHolder(0f);
        focusSpring = new SpringAnimation(holder);
        focusSpring.setSpring(new SpringForce()
                .setStiffness(Motion.SPRING_STIFFNESS)
                .setDampingRatio(Motion.SPRING_DAMPING));
        focusSpring.addUpdateListener((a, value, velocity) -> { focus = value; v.invalidate(); });
    }

    float focus() { return focus; }

    void onFocusChanged(boolean gained) {
        focused = gained;
        boolean reduced = Motion.reduced(v.getContext());
        float s = gained ? scale : 1f;
        Motion.spring(v, DynamicAnimation.SCALE_X, s);
        Motion.spring(v, DynamicAnimation.SCALE_Y, s);
        Motion.spring(v, DynamicAnimation.TRANSLATION_Y, gained ? -lift : 0f);
        Motion.spring(v, DynamicAnimation.TRANSLATION_X, 0f);
        Motion.spring(v, DynamicAnimation.Z, gained ? z : 0f);
        if (reduced) {
            focusSpring.cancel();
            focus = gained ? 1f : 0f;
            glint = 1f;
            v.invalidate();
            return;
        }
        focusSpring.animateToFinalPosition(gained ? 1f : 0f);
        if (gained) {
            if (glintAnim != null) glintAnim.cancel();
            glintAnim = ValueAnimator.ofFloat(0f, 1f);
            glintAnim.setDuration(GLINT_MS);
            glintAnim.setInterpolator(Motion.EASE_OUT);
            glintAnim.addUpdateListener(a -> { glint = (float) a.getAnimatedValue(); v.invalidate(); });
            glintAnim.start();
        }
    }

    void onPressed(boolean pressed) {
        if (!focused) return;
        float s = pressed ? scale * PRESS : scale;
        Motion.spring(v, DynamicAnimation.SCALE_X, s);
        Motion.spring(v, DynamicAnimation.SCALE_Y, s);
    }

    /** Acknowledge a D-pad press that hit a wall: push toward it, spring back. */
    void nudge(int dirX, int dirY) {
        if (Motion.reduced(v.getContext())) return;
        float n = NUDGE_DP * d;
        v.setTranslationX(v.getTranslationX() + dirX * n);
        v.setTranslationY(v.getTranslationY() + dirY * n);
        Motion.spring(v, DynamicAnimation.TRANSLATION_X, 0f);
        Motion.spring(v, DynamicAnimation.TRANSLATION_Y, focused ? -lift : 0f);
    }

    /** The specular sheen: bright at the top-left, gone by 40%, sliding in on focus. */
    void drawSheen(Canvas c, RectF r, float radius) {
        if (focus <= 0.01f) return;
        int w = (int) r.width(), h = (int) r.height();
        if (sheen == null || w != sheenW || h != sheenH) {
            sheenW = w;
            sheenH = h;
            int a = sheenAlpha << 24 | 0xFFFFFF;
            sheen = new LinearGradient(0, 0, w * 0.75f, h,
                    new int[]{a, ((sheenAlpha / 4) << 24) | 0xFFFFFF, 0x00FFFFFF},
                    new float[]{0f, 0.4f, 1f}, Shader.TileMode.CLAMP);
            sheenPaint.setShader(sheen);
        }
        float t = 1f - glint;
        sheenMatrix.setTranslate(-t * w * 0.7f, -t * h * 0.7f);
        sheen.setLocalMatrix(sheenMatrix);
        sheenPaint.setAlpha((int) (255 * Math.min(1f, focus)));
        c.drawRoundRect(r, radius, radius, sheenPaint);
    }
}
