package fi.koti.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;
import android.view.View;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Small poster loader for a low-memory TV: one fetch per URL (disk cache), RGB_565
 * bitmaps downsampled to the tile size, a 6 MB memory cache, two worker threads.
 * Nothing is fetched unless a tile asks for it.
 */
final class ImageLoader {
    private static final String TAG = "Koti.Images";
    private static final int MEM_BYTES = 6 * 1024 * 1024;

    private final Context context;
    private final File diskDir;
    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> mem = new LruCache<String, Bitmap>(MEM_BYTES) {
        @Override protected int sizeOf(String key, Bitmap b) { return b.getByteCount(); }
    };
    /** Backdrop art: full-screen ARGB (no banding in dark gradients), at most ~3 kept. */
    private final LruCache<String, Bitmap> art = new LruCache<String, Bitmap>(25 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap b) { return b.getByteCount(); }
    };

    interface BitmapCallback {
        void onBitmap(String uri, Bitmap bitmap);
    }

    /** Full-size art for the backdrop; the callback runs on the main thread, null on failure. */
    void loadArt(String uri, int widthPx, BitmapCallback cb) {
        Bitmap cached = art.get(uri);
        if (cached != null) { cb.onBitmap(uri, cached); return; }
        pool.execute(() -> {
            Bitmap b = null;
            try {
                byte[] data = readCached(uri);
                if (data != null) {
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(data, 0, data.length, o);
                    int sample = 1;
                    while (o.outWidth / (sample * 2) >= widthPx) sample *= 2;
                    o = new BitmapFactory.Options();
                    o.inSampleSize = sample;
                    o.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    b = BitmapFactory.decodeByteArray(data, 0, data.length, o);
                }
            } catch (Throwable t) {
                Log.w(TAG, "art failed " + uri + ": " + t);
            }
            if (b == null) Log.w(TAG, "no art from " + uri);
            final Bitmap result = b;
            if (result != null) art.put(uri, result);
            main.post(() -> cb.onBitmap(uri, result));
        });
    }

    ImageLoader(Context context) {
        this.context = context.getApplicationContext();
        diskDir = new File(context.getCacheDir(), "posters");
        diskDir.mkdirs();
    }

    /** A view that shows one remote image. */
    interface Target {
        void setImage(Bitmap bitmap);
    }

    /** Loads {@code uri} into {@code target} at about {@code widthPx} wide. */
    <T extends View & Target> void load(String uri, T target, int widthPx) {
        target.setTag(R.id.image_uri, uri);
        if (uri == null) { target.setImage(null); return; }
        Bitmap cached = mem.get(uri);
        if (cached != null) { target.setImage(cached); return; }
        target.setImage(null);
        pool.execute(() -> {
            Bitmap b = fetch(uri, widthPx);
            if (b == null) return;
            mem.put(uri, b);
            main.post(() -> {
                if (uri.equals(target.getTag(R.id.image_uri))) target.setImage(b);
            });
        });
    }

    private Bitmap fetch(String uri, int widthPx) {
        try {
            byte[] data = readCached(uri);
            if (data == null) return null;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= widthPx) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeByteArray(data, 0, data.length, o);
        } catch (Throwable t) {
            Log.w(TAG, "decode failed " + uri + ": " + t);
            return null;
        }
    }

    private byte[] readCached(String uri) throws IOException {
        File f = new File(diskDir, sha1(uri));
        if (f.exists() && f.length() > 0) return readAll(new java.io.FileInputStream(f));
        byte[] data;
        if (uri.startsWith("http://") || uri.startsWith("https://")) {
            HttpURLConnection conn = (HttpURLConnection) new URL(uri).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 9; BRAVIA 4K GB ATV3) Koti/1.0");
            try {
                int code = conn.getResponseCode();
                if (code / 100 == 3) {
                    // cross-host redirects (http <-> https) are not followed automatically
                    String loc = conn.getHeaderField("Location");
                    conn.disconnect();
                    if (loc == null) return null;
                    conn = (HttpURLConnection) new URL(new URL(uri), loc).openConnection();
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(8000);
                    code = conn.getResponseCode();
                }
                if (code / 100 != 2) {
                    Log.w(TAG, "HTTP " + code + " for " + uri);
                    return null;
                }
                data = readAll(conn.getInputStream());
            } finally {
                conn.disconnect();
            }
        } else {
            InputStream in = context.getContentResolver().openInputStream(Uri.parse(uri));
            if (in == null) return null;
            data = readAll(in);
        }
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(data);
        }
        trimDisk();
        return data;
    }

    /** Keep the image cache under ~40 MB by deleting the oldest files. */
    private void trimDisk() {
        File[] files = diskDir.listFiles();
        if (files == null) return;
        long total = 0;
        for (File f : files) total += f.length();
        if (total < 40L * 1024 * 1024) return;
        java.util.Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (File f : files) {
            if (total < 30L * 1024 * 1024) break;
            total -= f.length();
            f.delete();
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        try (InputStream is = new BufferedInputStream(in)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(64 * 1024);
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toByteArray();
        }
    }

    private static String sha1(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }
}
