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
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewOutlineProvider;

/**
 * An app tile: its 16:9 banner (or icon on a plate) with tvOS-tight 8 dp corners. Focus
 * behaviour (lift, shadow, sheen, press, wall nudge) lives in {@link FocusFx}.
 */
final class DockTileView extends View implements FocusFx.Host {
    /** Tiles are rendered larger than they rest, so the focus scale never upsamples. */
    static final float RENDER_SCALE = 1.16f;

    private final float d, radius;
    private final Paint bannerPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix shaderMatrix = new Matrix();
    private final RectF rect = new RectF();
    private final FocusFx fx;

    private AppEntry app;
    private Bitmap banner;
    private float dim;

    DockTileView(Context c) {
        super(c);
        d = getResources().getDisplayMetrics().density;
        radius = 8 * d;
        setFocusable(true);
        setClickable(true);
        dimPaint.setColor(0xFF000000);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
        setElevation(3 * d);
        fx = new FocusFx(this, Motion.FOCUS_SCALE, Motion.LIFT_DP, 12, 0x34);
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

    @Override public void nudge(int dirX, int dirY) { fx.nudge(dirX, dirY); }

    @Override
    public void setPressed(boolean pressed) {
        super.setPressed(pressed);
        if (fx != null) fx.onPressed(pressed);
    }

    @Override
    protected void onFocusChanged(boolean gained, int direction, android.graphics.Rect previous) {
        super.onFocusChanged(gained, direction, previous);
        fx.onFocusChanged(gained);
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
        banner.prepareToDraw();     // texture upload ahead of the first frame
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
        rect.set(0, 0, getWidth(), getHeight());
        c.drawRoundRect(rect, radius, radius, bannerPaint);
        if (dim > 0.001f) {
            dimPaint.setAlpha((int) (255 * dim * 0.55f));
            c.drawRoundRect(rect, radius, radius, dimPaint);
        }
        fx.drawSheen(c, rect, radius);
    }
}
