package fi.koti.launcher;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.security.MessageDigest;

/**
 * Lets tools/push-wallpapers.sh manage the backdrop photos on a release (non-debuggable)
 * build, where `run-as` is unavailable. Guarded by android.permission.DUMP, which the ADB
 * shell holds and ordinary apps cannot get.
 *   content query  --uri content://fi.koti.launcher.files/wallpapers        (name, md5)
 *   content write  --uri content://fi.koti.launcher.files/wallpapers/x.jpg < file
 *   content delete --uri content://fi.koti.launcher.files/wallpapers/x.jpg
 */
public class WallpaperProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    private File folder() {
        File f = BackdropView.folder(getContext());
        f.mkdirs();
        return f;
    }

    /** Only plain file names: no paths, no traversal. */
    private File fileFor(Uri uri) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        if (name == null || !name.matches("[A-Za-z0-9._-]+") || name.startsWith(".")) {
            throw new FileNotFoundException("bad name");
        }
        return new File(folder(), name);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        MatrixCursor c = new MatrixCursor(new String[]{"name", "md5"});
        File[] files = folder().listFiles();
        if (files != null) for (File f : files) if (f.isFile()) c.addRow(new Object[]{f.getName(), md5(f)});
        return c;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = fileFor(uri);
        int m = mode.contains("w")
                ? ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE
                : ParcelFileDescriptor.MODE_READ_ONLY;
        return ParcelFileDescriptor.open(f, m);
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
        try {
            return fileFor(uri).delete() ? 1 : 0;
        } catch (FileNotFoundException e) {
            return 0;
        }
    }

    @Override public String getType(Uri uri) { return "image/*"; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }

    private static String md5(File f) {
        try (FileInputStream in = new FileInputStream(f)) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
