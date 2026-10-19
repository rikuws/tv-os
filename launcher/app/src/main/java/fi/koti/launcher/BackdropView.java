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
    private final Paint scrimPaint = new Paint(Paint.DITHER_FLAG);
    private final Matrix matrix = new Matrix();
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SharedPreferences prefs;

    private Bitmap current, previous;
    private float fade = 1f;
    private ValueAnimator fadeAnim;
    private LinearGradient topScrim, bottomScrim, fallback;
    private float topEnd, bottomStart;
    private String currentName;
    private boolean homeScrims = true;

    /** Home: deep bottom scrim for the rows. Screensaver: lighter, even scrims. */
    public void setHomeScrims(boolean home) {
        homeScrims = home;
        if (getWidth() > 0) onSizeChanged(getWidth(), getHeight(), 0, 0);
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

    /** Crossfades to featured art (owned by the caller's cache). */
    public void showArt(String key, Bitmap b) {
        if (b == null || key.equals(currentName)) return;
        setPhoto(b, key);
    }

    /** Shows the photo the cycle is on now. */
    public void showPhoto() { showCurrent(); }

    /** Shows the photo the cycle is on now (first launch, or after process restart). */
    public void showCurrent() {
        List<String> order = order();
        if (order.isEmpty()) { setPhoto(null, null); return; }
        int pos = prefs.getInt("pos", 0) % order.size();
        load(order.get(pos));
    }

    /** Advances to the next photo in the shuffled cycle and crossfades to it. */
    public void next() {
        List<String> order = order();
        if (order.isEmpty()) { setPhoto(null, null); return; }
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
        load(order.get(pos));
    }

    /** The saved cycle, reconciled with the files actually present. */
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
        if (name.equals(currentName) && current != null) return;
        File f = new File(folder(getContext()), name);
        int w = Math.max(getWidth(), 1920), h = Math.max(getHeight(), 1080);
        decoder.execute(() -> {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= w && o.outHeight / (sample * 2) >= h) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            o.inPreferredConfig = Bitmap.Config.ARGB_8888;   // RGB_565 bands in skies
            Bitmap b = BitmapFactory.decodeFile(f.getPath(), o);
            main.post(() -> setPhoto(b, name));
        });
    }

    private void setPhoto(Bitmap b, String name) {
        if (fadeAnim != null) fadeAnim.end();
        previous = current;
        current = b;
        currentName = name;
        if (previous == null || Motion.reduced(getContext())) {
            fade = 1f;
            dropPrevious();
            invalidate();
            return;
        }
        fade = 0f;
        fadeAnim = ValueAnimator.ofFloat(0f, 1f);
        fadeAnim.setDuration(FADE_MS);
        fadeAnim.setInterpolator(Motion.EASE_OUT);
        fadeAnim.addUpdateListener(a -> { fade = (float) a.getAnimatedValue(); invalidate(); });
        fadeAnim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) { dropPrevious(); }
        });
        fadeAnim.start();
    }

    private void dropPrevious() {
        previous = null;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        topEnd = h * (homeScrims ? 0.28f : 0.34f);
        bottomStart = h * (homeScrims ? 0.34f : 0.40f);
        topScrim = scrim(0, topEnd, homeScrims ? 0.5f : 0.62f, true);
        bottomScrim = scrim(bottomStart, h * (homeScrims ? 0.82f : 1f), homeScrims ? 0.93f : 0.86f, false);
        fallback = new LinearGradient(0, 0, 0, h, Palette.FALLBACK_TOP, Palette.FALLBACK_BOTTOM,
                Shader.TileMode.CLAMP);
    }

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
        int w = getWidth(), h = getHeight();
        if (current == null && previous == null) {
            scrimPaint.setShader(fallback);
            c.drawRect(0, 0, w, h, scrimPaint);
        } else {
            if (previous != null && fade < 1f) drawPhoto(c, previous, 1f);
            if (current != null) drawPhoto(c, current, fade);
        }
        scrimPaint.setShader(topScrim);
        c.drawRect(0, 0, w, topEnd, scrimPaint);
        scrimPaint.setShader(bottomScrim);
        c.drawRect(0, bottomStart, w, h, scrimPaint);
    }

    private void drawPhoto(Canvas c, Bitmap b, float alpha) {
        int w = getWidth(), h = getHeight();
        float s = Math.max(w / (float) b.getWidth(), h / (float) b.getHeight());
        matrix.setScale(s, s);
        matrix.postTranslate((w - b.getWidth() * s) / 2f, (h - b.getHeight() * s) / 2f);
        photoPaint.setAlpha(Math.round(alpha * 255));
        c.drawBitmap(b, matrix, photoPaint);
    }
}
