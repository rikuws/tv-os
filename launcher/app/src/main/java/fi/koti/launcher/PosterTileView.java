package fi.koti.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

/**
 * A show to continue: 16:9 artwork (or the app's icon on a plate until it loads), a white
 * progress bar as in the Apple TV app, and the same lift, shadow, sheen and lighting as
 * the dock so the two rows read as one system.
 */
final class PosterTileView extends View implements ImageLoader.Target {
    private final float d, radius;
    private final Paint artPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private android.graphics.LinearGradient sheen;
    private final RectF rect = new RectF();
    private final Matrix matrix = new Matrix();

    private Bitmap art;
    private Drawable icon;
    private float progress = -1;
    private android.graphics.LinearGradient barShade;
    private float focus, dim = 0.25f;
    private final SpringAnimation focusSpring;

    private static final FloatPropertyCompat<PosterTileView> FOCUS =
            new FloatPropertyCompat<PosterTileView>("focus") {
                @Override public float getValue(PosterTileView v) { return v.focus; }
                @Override public void setValue(PosterTileView v, float f) { v.focus = f; v.invalidate(); }
            };

    PosterTileView(Context c) {
        super(c);
        d = getResources().getDisplayMetrics().density;
        radius = 12 * d;
        setFocusable(true);
        setClickable(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
        setElevation(2 * d);
        focusSpring = new SpringAnimation(this, FOCUS);
        focusSpring.setSpring(new SpringForce()
                .setStiffness(Motion.SPRING_STIFFNESS)
                .setDampingRatio(Motion.SPRING_DAMPING));
    }

    void bind(Drawable appIcon, float progress, String description) {
        this.icon = appIcon;
        this.progress = progress;
        setContentDescription(description);
        invalidate();
    }

    @Override
    public void setImage(Bitmap bitmap) {
        art = bitmap;
        artPaint.setShader(bitmap == null ? null
                : new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        invalidate();
    }

    void setDim(float target) {
        if (Math.abs(target - dim) < 0.001f) return;
        if (Motion.reduced(getContext())) { dim = target; invalidate(); return; }
        android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(dim, target);
        a.setDuration(Motion.LIGHT_MS);
        a.setInterpolator(Motion.EASE_OUT);
        a.addUpdateListener(v -> { dim = (float) v.getAnimatedValue(); invalidate(); });
        a.start();
    }

    @Override
    protected void onFocusChanged(boolean gained, int direction, android.graphics.Rect previous) {
        super.onFocusChanged(gained, direction, previous);
        setPivotX(getWidth() / 2f);
        setPivotY(getHeight() / 2f);
        float s = gained ? Motion.POSTER_SCALE : 1f;
        Motion.spring(this, DynamicAnimation.SCALE_X, s);
        Motion.spring(this, DynamicAnimation.SCALE_Y, s);
        Motion.spring(this, DynamicAnimation.TRANSLATION_Y, gained ? -Motion.LIFT_DP * 0.6f * d : 0f);
        Motion.spring(this, DynamicAnimation.Z, gained ? 12 * d : 0f);
        if (Motion.reduced(getContext())) { focus = gained ? 1 : 0; invalidate(); }
        else focusSpring.animateToFinalPosition(gained ? 1f : 0f);
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        rect.set(0, 0, w, h);

        if (art != null) {
            // center-crop the artwork into the tile
            float s = Math.max(w / (float) art.getWidth(), h / (float) art.getHeight());
            matrix.setScale(s, s);
            matrix.postTranslate((w - art.getWidth() * s) / 2f, (h - art.getHeight() * s) / 2f);
            artPaint.getShader().setLocalMatrix(matrix);
            c.drawRoundRect(rect, radius, radius, artPaint);
        } else {
            fill.setColor(Palette.PLATE);
            c.drawRoundRect(rect, radius, radius, fill);
            if (icon != null) {
                int size = (int) (h * 0.42f);
                int l = (w - size) / 2, t = (h - size) / 2;
                icon.setBounds(l, t, l + size, t + size);
                icon.draw(c);
            }
        }

        if (progress >= 0) {
            // Bottom fade so the bar reads on bright artwork, then a white bar on a track.
            if (barShade == null) {
                barShade = new android.graphics.LinearGradient(0, h * 0.6f, 0, h,
                        0x00000000, 0x8C000000, Shader.TileMode.CLAMP);
            }
            fill.setShader(barShade);
            c.drawRoundRect(rect, radius, radius, fill);
            fill.setShader(null);
            float inset = 10 * d, th = 3.5f * d, y = h - 9 * d - th;
            fill.setColor(Palette.PROGRESS_TRACK);
            c.drawRoundRect(inset, y, w - inset, y + th, th / 2, th / 2, fill);
            fill.setColor(Palette.WHITE);
            float end = inset + (w - 2 * inset) * Math.max(0.05f, progress);
            c.drawRoundRect(inset, y, end, y + th, th / 2, th / 2, fill);
        }

        if (dim > 0.001f) {
            fill.setColor(Palette.alpha(0xFF000000, dim * 0.55f));
            c.drawRoundRect(rect, radius, radius, fill);
        }

        if (focus > 0.01f) {
            if (sheen == null) {
                sheen = new android.graphics.LinearGradient(0, 0, w * 0.75f, h,
                        new int[]{0x33FFFFFF, 0x0DFFFFFF, 0x00FFFFFF}, new float[]{0f, 0.4f, 1f},
                        Shader.TileMode.CLAMP);
                sheenPaint.setShader(sheen);
            }
            sheenPaint.setAlpha((int) (255 * Math.min(1f, focus)));
            c.drawRoundRect(rect, radius, radius, sheenPaint);
        }
    }
}
