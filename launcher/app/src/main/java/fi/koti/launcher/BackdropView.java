package fi.koti.launcher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Full-screen image behind the home screen: the focused app's featured art
 * ({@link #showArt}), or a landscape photo from files/wallpapers when an app has none
 * ({@link #showPhoto}; the screensaver cycles them with {@link #next()}). Crossfades on
 * change; static otherwise. Bitmaps are never recycled here: art belongs to the
 * ImageLoader cache, and the GC frees the rest.
 *
 * Smoothstep scrims keep text legible over any image: a light one at the top, and a deep
 * one rising behind the dock and the continue-watching row, as in the Apple TV app.
 */
public class BackdropView extends View {
    private static final String PREFS = "backdrop";
    private static final int FADE_MS = 700;

    private final Paint photoPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Matrix matrix = new Matrix();
    private final ExecutorService decoder = Workers.pool("koti-backdrop", 1);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SharedPreferences prefs;

    private Bitmap current, previous;
    private float fade = 1f;
    private ValueAnimator fadeAnim;
    private String currentName;
    private boolean homeScrims = true;

    /** Home: deep bottom scrim for the rows. Screensaver: lighter, even scrims. */
    public void setHomeScrims(boolean home) {
        homeScrims = home;
        invalidate();
    }

    public BackdropView(Context c) { this(c, null); }
    public BackdropView(Context c, AttributeSet a) {
        super(c, a);
        prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Folder the photos live in. */
    static File folder(Context c) {
        return new File(c.getFilesDir(), "wallpapers");
    }

    /** Crossfades to featured art (owned by the caller's cache; never modified). */
    public void showArt(String key, Bitmap b) {
        if (b == null) return;
        show("art:" + key, b);
    }

    /** Shows the photo the cycle is on now. */
    public void showPhoto() { showCurrent(); }

    /** Shows the photo the cycle is on now (first launch, or after process restart). */
    public void showCurrent() {
        decoder.execute(() -> {
            List<String> order = order();
            if (order.isEmpty()) { main.post(() -> show("fallback", null)); return; }
            int pos = prefs.getInt("pos", 0) % order.size();
            String name = order.get(pos);
            main.post(() -> load(name));
        });
    }

    /** Advances to the next photo in the shuffled cycle and crossfades to it. */
    public void next() {
        decoder.execute(() -> {
            List<String> order = order();
            if (order.isEmpty()) { main.post(() -> show("fallback", null)); return; }
            int pos = prefs.getInt("pos", -1) + 1;
            if (pos >= order.size()) {
                // New cycle: reshuffle, but never start with the photo just shown.
                String last = order.get(order.size() - 1);
                Collections.shuffle(order);
                if (order.size() > 1 && order.get(0).equals(last)) Collections.swap(order, 0, order.size() - 1);
                prefs.edit().putString("order", TextUtils.join("/", order)).apply();
                pos = 0;
            }
            prefs.edit().putInt("pos", pos).apply();
            String name = order.get(pos);
            main.post(() -> load(name));
        });
    }

    /** The saved cycle, reconciled with the files actually present (disk: call off-main). */
    private List<String> order() {
        String[] files = folder(getContext()).list((d, n) -> {
            String l = n.toLowerCase();
            return l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".png") || l.endsWith(".webp");
        });
        List<String> present = files == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(files));
        List<String> saved = new ArrayList<>(Arrays.asList(prefs.getString("order", "").split("/")));
        saved.retainAll(present);
        List<String> added = new ArrayList<>(present);
        added.removeAll(saved);
        if (saved.isEmpty() || !added.isEmpty()) {
            Collections.shuffle(added);
            saved.addAll(added);
            prefs.edit().putString("order", TextUtils.join("/", saved)).apply();
        }
        return saved;
    }

    private void load(String name) {
        String key = "photo:" + name;
        wanted = key;
        Bitmap ready = composites.get(compositeKey(key));
        if (ready != null) { crossfadeTo(key, ready); return; }
        File f = new File(folder(getContext()), name);
        int w = width(), h = height();
        decoder.execute(() -> {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= w && o.outHeight / (sample * 2) >= h) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            o.inPreferredConfig = Bitmap.Config.ARGB_8888;   // RGB_565 bands in skies
            Bitmap src = BitmapFactory.decodeFile(f.getPath(), o);
            Bitmap out = compose(src, w, h);
            main.post(() -> deliver(key, out));
        });
    }

    // ---------------------------------------------------------------- compositing

    /*
     * Performance: the image and both scrims are painted ONCE, off the main thread, into a
     * single opaque bitmap. Each frame then draws one full-screen texture without blending
     * (instead of a bitmap plus two full-screen gradients), which matters on this TV: its
     * compositor is double-buffered, so a frame the Mali GPU can't finish in time halves
     * the frame rate. The result is pixel-identical to drawing the layers live.
     */
    private final android.util.LruCache<String, Bitmap> composites =
            // dock apps' first art + the current and next carousel images
            new android.util.LruCache<String, Bitmap>(9 * 1920 * 1080 * 4) {
                @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
            };
    private String wanted;

    private String compositeKey(String key) { return (homeScrims ? "h|" : "d|") + key; }

    private int width() { return getWidth() > 0 ? getWidth() : getResources().getDisplayMetrics().widthPixels; }
    private int height() { return getHeight() > 0 ? getHeight() : getResources().getDisplayMetrics().heightPixels; }

    /** Shows {@code src} (null = fallback gradient) under the scrims, composing if needed. */
    private void show(String key, Bitmap src) {
        wanted = key;
        Bitmap ready = composites.get(compositeKey(key));
        if (ready != null) { crossfadeTo(key, ready); return; }
        int w = width(), h = height();
        decoder.execute(() -> {
            Bitmap out = compose(src, w, h);
            main.post(() -> deliver(key, out));
        });
    }

    private void deliver(String key, Bitmap composite) {
        if (composite == null) return;
        composites.put(compositeKey(key), composite);
        if (key.equals(wanted)) crossfadeTo(key, composite);   // stale results only warm the cache
    }

    /** Pre-warm: compose art the user is likely to focus next, without showing it. */
    public void prepareArt(String key, Bitmap b) {
        if (b == null || composites.get(compositeKey("art:" + key)) != null) return;
        int w = width(), h = height();
        String k = "art:" + key;
        decoder.execute(() -> {
            Bitmap out = compose(b, w, h);
            if (out != null) main.post(() -> composites.put(compositeKey(k), out));
        });
    }

    /** Runs on the decoder thread. */
    private Bitmap compose(Bitmap src, int w, int h) {
        if (w <= 0 || h <= 0) return null;
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG | Paint.ANTI_ALIAS_FLAG);
        if (src == null) {
            p.setShader(new LinearGradient(0, 0, 0, h, Palette.FALLBACK_TOP, Palette.FALLBACK_BOTTOM,
                    Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, w, h, p);
            p.setShader(null);
        } else {
            float s = Math.max(w / (float) src.getWidth(), h / (float) src.getHeight());
            Matrix m = new Matrix();
            m.setScale(s, s);
            m.postTranslate((w - src.getWidth() * s) / 2f, (h - src.getHeight() * s) / 2f);
            cv.drawColor(0xFF000000);
            cv.drawBitmap(src, m, p);
        }
        float topEnd = h * (homeScrims ? 0.28f : 0.34f);
        float bottomStart = h * (homeScrims ? 0.34f : 0.40f);
        p.setShader(scrim(0, topEnd, homeScrims ? 0.5f : 0.62f, true));
        cv.drawRect(0, 0, w, topEnd, p);
        p.setShader(scrim(bottomStart, h * (homeScrims ? 0.82f : 1f), homeScrims ? 0.93f : 0.86f, false));
        cv.drawRect(0, bottomStart, w, h, p);
        out.setHasAlpha(false);     // opaque: drawn without blending
        // Plain bitmap on purpose: measured on this TV's Mali driver, HARDWARE bitmaps
        // make full-screen crossfades drop ~30% more frames. prepareToDraw() instead asks
        // the render thread to upload the texture ahead of its first frame.
        out.prepareToDraw();
        return out;
    }

    private void crossfadeTo(String key, Bitmap composite) {
        if (composite == current) return;
        if (fadeAnim != null) fadeAnim.end();
        previous = current;
        current = composite;
        currentName = key;
        if (previous == null || Motion.reduced(getContext())) {
            fade = 1f;
            previous = null;
            invalidate();
            return;
        }
        fade = 0f;
        fadeAnim = ValueAnimator.ofFloat(0f, 1f);
        fadeAnim.setDuration(FADE_MS);
        fadeAnim.setInterpolator(Motion.EASE_OUT);
        fadeAnim.addUpdateListener(a -> { fade = (float) a.getAnimatedValue(); invalidate(); });
        fadeAnim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) { previous = null; invalidate(); }
        });
        fadeAnim.start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        if (ow != 0 && (w != ow || h != oh)) composites.evictAll();
    }

    @Override
    public boolean isOpaque() { return current != null && previous == null; }

    /** Smoothstep-shaped scrim in 12 stops, so there is no visible edge. */
    private static LinearGradient scrim(float y0, float y1, float max, boolean fromTop) {
        int n = 12;
        int[] colors = new int[n];
        float[] stops = new float[n];
        for (int i = 0; i < n; i++) {
            float k = i / (float) (n - 1);
            float e = k * k * (3 - 2 * k);
            colors[i] = Palette.alpha(Palette.SCRIM, fromTop ? max * (1 - e) : max * e);
            stops[i] = k;
        }
        return new LinearGradient(0, y0, 0, y1, colors, stops, Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (current == null) {
            c.drawColor(Palette.FALLBACK_TOP);
            return;
        }
        if (previous != null && fade < 1f) {
            c.drawBitmap(previous, 0, 0, null);
            photoPaint.setAlpha(Math.round(fade * 255));
            c.drawBitmap(current, 0, 0, photoPaint);
        } else {
            c.drawBitmap(current, 0, 0, null);
        }
    }
}
