package fi.koti.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

/**
 * An app tile: its 16:9 banner (or icon on a plate) with 12 dp corners. Focus works the
 * tvOS way: the tile lifts on a spring (scale, rise, deeper shadow), brightens, and a
 * soft specular sheen lights its top-left. No ring.
 */
final class DockTileView extends View {
    private final float d;
    private final float radius;
    private final Paint bannerPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private android.graphics.LinearGradient sheen;
    private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private AppEntry app;
    private Bitmap banner;
    /** Tiles are rendered larger than they rest, so the focus scale never upsamples. */
    static final float RENDER_SCALE = 1.16f;
    private final android.graphics.Matrix shaderMatrix = new android.graphics.Matrix();

    /** 0 = resting, 1 = focused. Drives the sheen. */
    private float focus;
    /** 0 = fully lit, 1 = dimmed. */
    private float dim = 0.25f;
    private final SpringAnimation focusSpring;
    private final float restElevation, focusElevation;

    private static final FloatPropertyCompat<DockTileView> FOCUS =
            new FloatPropertyCompat<DockTileView>("focus") {
                @Override public float getValue(DockTileView v) { return v.focus; }
                @Override public void setValue(DockTileView v, float f) { v.focus = f; v.invalidate(); }
            };

    DockTileView(Context c) {
        super(c);
        d = getResources().getDisplayMetrics().density;
        radius = 12 * d;
        restElevation = 3 * d;
        focusElevation = 14 * d;
        setFocusable(true);
        setClickable(true);
        dimPaint.setColor(0xFF000000);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
        setElevation(restElevation);
        focusSpring = new SpringAnimation(this, FOCUS);
        focusSpring.setSpring(new SpringForce()
                .setStiffness(Motion.SPRING_STIFFNESS)
                .setDampingRatio(Motion.SPRING_DAMPING));
    }

    void bind(AppEntry app, int w, int h) {
        this.app = app;
        setContentDescription(app.label);
        render(w, h);
        invalidate();
    }

    AppEntry app() { return app; }

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
        float s = gained ? Motion.FOCUS_SCALE : 1f;
        Motion.spring(this, DynamicAnimation.SCALE_X, s);
        Motion.spring(this, DynamicAnimation.SCALE_Y, s);
        Motion.spring(this, DynamicAnimation.TRANSLATION_Y, gained ? -Motion.LIFT_DP * d : 0f);
        Motion.spring(this, DynamicAnimation.Z, gained ? focusElevation - restElevation : 0f);
        if (Motion.reduced(getContext())) { focus = gained ? 1 : 0; invalidate(); }
        else focusSpring.animateToFinalPosition(gained ? 1f : 0f);
    }

    private void render(int w, int h) {
        if (w <= 0 || h <= 0) return;
        int bw = Math.round(w * RENDER_SCALE), bh = Math.round(h * RENDER_SCALE);
        banner = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        Canvas bc = new Canvas(banner);
        bc.scale(bw / (float) w, bh / (float) h);
        Drawable art = app.banner;
        if (art != null) {
            art.setBounds(0, 0, w, h);
            art.draw(bc);
        } else {
            drawIconPlate(bc, w, h);
        }
        BitmapShader shader = new BitmapShader(banner, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        shaderMatrix.setScale(w / (float) bw, h / (float) bh);
        shader.setLocalMatrix(shaderMatrix);
        bannerPaint.setShader(shader);
    }

    private void drawIconPlate(Canvas c, int w, int h) {
        Paint plate = new Paint(Paint.ANTI_ALIAS_FLAG);
        plate.setColor(Palette.PLATE);
        c.drawRect(0, 0, w, h, plate);
        int icon = (int) (h * 0.42f);
        int left = (w - icon) / 2, top = (int) (h * 0.16f);
        if (app.icon != null) {
            app.icon.setBounds(left, top, left + icon, top + icon);
            app.icon.draw(c);
        }
        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setColor(Palette.TEXT);
        tp.setTextSize(13 * d);
        tp.setTypeface(Type.face(getContext(), Type.MEDIUM));
        tp.setTextAlign(Paint.Align.CENTER);
        CharSequence label = TextUtils.ellipsize(app.label, tp, w - 16 * d, TextUtils.TruncateAt.END);
        c.drawText(label, 0, label.length(), w / 2f, h - 12 * d, tp);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (banner == null) return;
        int w = getWidth(), h = getHeight();
        rect.set(0, 0, w, h);
        c.drawRoundRect(rect, radius, radius, bannerPaint);
        if (dim > 0.001f) {
            dimPaint.setAlpha((int) (255 * dim * 0.55f));
            c.drawRoundRect(rect, radius, radius, dimPaint);
        }
        if (focus > 0.01f) {
            if (sheen == null) {
                sheen = new android.graphics.LinearGradient(0, 0, w * 0.75f, h,
                        new int[]{0x38FFFFFF, 0x0FFFFFFF, 0x00FFFFFF}, new float[]{0f, 0.4f, 1f},
                        Shader.TileMode.CLAMP);
                sheenPaint.setShader(sheen);
            }
            sheenPaint.setAlpha((int) (255 * Math.min(1f, focus)));
            c.drawRoundRect(rect, radius, radius, sheenPaint);
        }
    }
}
