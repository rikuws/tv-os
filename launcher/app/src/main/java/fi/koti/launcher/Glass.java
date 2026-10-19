package fi.koti.launcher;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * tvOS-style glass without blur: a tinted dark fill, a faint light wash in the top half,
 * and a 1 px edge that is bright at the top and fades toward the bottom, the way light
 * catches the rim of a glass pane. Cheap to draw: two round rects, no offscreen layers.
 */
final class Glass extends Drawable {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wash = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float radius, stroke;

    Glass(int fillColor, float radiusPx, float density) {
        this.radius = radiusPx;
        this.stroke = Math.max(1f, density * 0.75f);
        fill.setColor(fillColor);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(stroke);
    }

    void setFill(int color) {
        fill.setColor(color);
        invalidateSelf();
    }

    @Override
    protected void onBoundsChange(Rect b) {
        edge.setShader(new LinearGradient(0, b.top, 0, b.bottom,
                new int[]{Palette.GLASS_EDGE_TOP, Palette.GLASS_EDGE_BOTTOM, Palette.GLASS_EDGE_BOTTOM},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        wash.setShader(new LinearGradient(0, b.top, 0, b.top + b.height() * 0.5f,
                0x12FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
    }

    @Override
    public void draw(Canvas c) {
        r.set(getBounds());
        c.drawRoundRect(r, radius, radius, fill);
        c.drawRoundRect(r, radius, radius, wash);
        float h = stroke / 2f;
        r.inset(h, h);
        c.drawRoundRect(r, radius - h, radius - h, edge);
    }

    @Override
    public void getOutline(Outline outline) {
        outline.setRoundRect(getBounds(), radius);
        outline.setAlpha(1f);
    }

    @Override public void setAlpha(int alpha) { fill.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter cf) { fill.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
