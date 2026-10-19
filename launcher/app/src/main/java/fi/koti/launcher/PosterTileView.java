package fi.koti.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewOutlineProvider;

/**
 * A show to continue: 16:9 artwork (the app's icon on a plate until it loads), 8 dp
 * corners, a white progress bar on a dark bottom fade and an "Uusi" badge for new
 * episodes, as in the Apple TV app. Focus behaviour lives in {@link FocusFx}.
 */
final class PosterTileView extends View implements ImageLoader.Target, FocusFx.Host {
    private final float d, radius;
    private final Paint artPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgeText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final RectF badge = new RectF();
    private final Matrix matrix = new Matrix();
    private final FocusFx fx;
    private LinearGradient barShade;

    private Bitmap art;
    private Drawable icon;
    private float progress = -1;
    private String badgeLabel;
    private float dim;

    PosterTileView(Context c) {
        super(c);
        d = getResources().getDisplayMetrics().density;
        radius = 8 * d;
        setFocusable(true);
        setClickable(true);
        badgeText.setColor(Palette.WHITE);
        badgeText.setTextSize(11 * d);
        badgeText.setTypeface(Type.face(c, Type.SEMIBOLD));
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
        setElevation(2 * d);
        fx = new FocusFx(this, Motion.POSTER_SCALE, Motion.LIFT_DP * 0.6f, 12, 0x30);
    }

    /** {@code badge} is a short label drawn top-left (e.g. "Uusi"), or null. */
    void bind(Drawable appIcon, float progress, String badge, String description) {
        this.icon = appIcon;
        this.progress = progress;
        this.badgeLabel = badge;
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

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        rect.set(0, 0, w, h);

        if (art != null) {
            // centre-crop the artwork into the card
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
            if (barShade == null) {
                barShade = new LinearGradient(0, h * 0.6f, 0, h, 0x00000000, 0x8C000000, Shader.TileMode.CLAMP);
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

        if (badgeLabel != null) {
            float padH = 7 * d, bh = 19 * d, inset = 8 * d;
            float tw = badgeText.measureText(badgeLabel);
            badge.set(inset, inset, inset + tw + padH * 2, inset + bh);
            fill.setColor(0x99000000);
            c.drawRoundRect(badge, 5 * d, 5 * d, fill);
            Paint.FontMetrics fm = badgeText.getFontMetrics();
            float base = badge.centerY() - (fm.ascent + fm.descent) / 2f;
            c.drawText(badgeLabel, badge.left + padH, base, badgeText);
        }

        if (dim > 0.001f) {
            fill.setColor(Palette.alpha(0xFF000000, dim * 0.55f));
            c.drawRoundRect(rect, radius, radius, fill);
        }

        fx.drawSheen(c, rect, radius);
    }
}
