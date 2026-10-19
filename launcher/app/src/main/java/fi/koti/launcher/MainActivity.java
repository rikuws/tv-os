/*
 * THESIS: The Apple TV app's language on a Sony panel. The family's five apps sit under
 * the focused app's own featured art, like tvOS's top shelf; what they were watching is
 * one row below, labelled. Refuses the stock launcher's promo rows and ad hero chrome.
 * OWN-WORLD: full-bleed app art under a deep smoothstep bottom scrim; dark glass (tinted,
 * bright top edge, no blur) for the sidebar and panels; white type in Inter (SF Pro's
 * open relative); 12 dp cards that lift into a deeper shadow with a specular sheen, no
 * ring; white capsule for the focused menu item; white progress bars.
 * STORY: see your apps and the art of the one in focus, press OK; drop a row to resume a
 * show by its picture and name; press Left or Back for Sovellukset, Tulot, Asetukset.
 * FIRST VIEWPORT: glass capsule with the time top-left; art fills the screen; five
 * 160x90 dp banners at 250 dp; "Jatka katsomista" 20sp bold at 362 dp; 132x74 dp cards
 * with title and subtitle beneath.
 * FORM: user-pinned tvOS direction (photo of the Apple TV app), superseding Sininen hetki
 * (seed cd68bd52). Signature: art crossfades as dock focus rests; sidebar grows from its
 * capsule.
 * FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
 */
package fi.koti.launcher;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity implements Sidebar.Listener {
    private static final String TAG = "Koti";
    private static final Locale FI = new Locale("fi", "FI");

    // Content rows, top to bottom. Up/Down move exactly one visible row; Left/Right stay
    // in the row; Left from a row's first item (or Back) opens the sidebar.
    private static final int ROW_DOCK = 0, ROW_CONTINUE = 1;
    private final int[] lastIndex = {0, 0};
    private int focusRow = ROW_DOCK;

    private static final float DIM_ROWMATE = 0.22f, DIM_DOCK_IDLE = 0.30f, DIM_POSTER_IDLE = 0.34f;
    private static final int CONTINUE_LIMIT = 12;
    /** Dock focus must rest this long before the backdrop changes art. */
    private static final int ART_DELAY_MS = 220;

    private BackdropView backdrop;
    private TextView continueHeading;
    private LinearLayout dock;
    private RecyclerView continueRow;
    private FrameLayout overlay;
    private Sidebar sidebar;
    private ContinueAdapter continueAdapter;

    private Prefs prefs;
    private AppRepository appRepo;
    private WatchNextRepository watchNext;
    private ShelfRepository shelfRepo;
    private InputRepository inputRepo;
    private ImageLoader images;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private Map<String, AppEntry> apps = new java.util.LinkedHashMap<>();
    private Map<String, List<ShelfRepository.Art>> shelves = new HashMap<>();
    private final List<AppEntry> dockApps = new ArrayList<>();
    private Sheet sheet;
    private boolean resumed;
    private boolean firstFocusPending = true;
    private boolean firstResume = true;
    private String artPackage;

    private int tileW, tileH, posterW, posterH, screenW;

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        float d = getResources().getDisplayMetrics().density;
        tileW = Math.round(160 * d);
        tileH = Math.round(90 * d);
        posterW = Math.round(148 * d);
        posterH = Math.round(83 * d);
        screenW = getResources().getDisplayMetrics().widthPixels;

        prefs = new Prefs(this);
        appRepo = new AppRepository(this);
        watchNext = new WatchNextRepository(this);
        shelfRepo = new ShelfRepository(this);
        inputRepo = new InputRepository(this);
        images = new ImageLoader(this);

        backdrop = findViewById(R.id.backdrop);
        dock = findViewById(R.id.dock);
        continueHeading = findViewById(R.id.continue_heading);
        continueRow = findViewById(R.id.continue_row);
        overlay = findViewById(R.id.overlay);
        continueHeading.setTypeface(Type.display(this, Type.BOLD));

        sidebar = new Sidebar(this);
        sidebar.setListener(this);
        ((FrameLayout) findViewById(R.id.sidebar_host)).addView(sidebar, new FrameLayout.LayoutParams(-1, -1));

        continueRow.setLayoutManager(new PeekLayoutManager(this, Math.round(48 * d)));
        continueRow.setItemAnimator(null);
        continueRow.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override public void getItemOffsets(android.graphics.Rect out, View v, RecyclerView p,
                                                 RecyclerView.State s) {
                if (p.getChildAdapterPosition(v) > 0) out.left = Math.round(18 * d);
            }
        });
        continueAdapter = new ContinueAdapter();
        continueRow.setAdapter(continueAdapter);
        setContinueVisible(false);

        getWindow().getDecorView().getViewTreeObserver()
                .addOnGlobalFocusChangeListener((oldFocus, newFocus) -> onFocusMoved(newFocus));

        IntentFilter pkg = new IntentFilter();
        pkg.addAction(Intent.ACTION_PACKAGE_ADDED);
        pkg.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pkg.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pkg.addDataScheme("package");
        registerReceiver(packageReceiver, pkg);

        handleIntent(getIntent());
        reloadApps();

        if (!watchNext.hasPermission()) {
            requestPermissions(new String[]{"android.permission.READ_TV_LISTINGS"}, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        IntentFilter time = new IntentFilter(Intent.ACTION_TIME_TICK);
        time.addAction(Intent.ACTION_TIME_CHANGED);
        time.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(timeReceiver, time);
        updateClock();
        if (!firstResume) {
            // Every return home: art restarts at your newest show; next fallback photo.
            backdrop.next();
            artPackage = null;
        }
        firstResume = false;
        reloadContinue();
        reloadShelves();
        if ((sheet == null || !sheet.isOpen()) && !sidebar.isOpen()) restoreFocus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        stopCarousel();
        unregisterReceiver(timeReceiver);
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packageReceiver);
        io.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        boolean changed = handleIntent(intent);
        if (changed) reloadApps();
        // Home pressed while already home: close sheet and sidebar, return to the dock.
        if (sheet != null && sheet.isOpen()) sheet.close();
        if (sidebar.isOpen()) sidebar.close();
        if (resumed) focusRow(ROW_DOCK, lastIndex[ROW_DOCK]);
        else focusRow = ROW_DOCK;
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        reloadContinue();
        reloadShelves();
    }

    /** ADB configuration: --es dock "pkg1,pkg2", --ez reset_dock true, --ez unhide_all true. */
    private boolean handleIntent(Intent intent) {
        if (intent == null) return false;
        if (intent.getBooleanExtra("unhide_all", false)) {
            intent.removeExtra("unhide_all");
            prefs.clearHidden();
            reloadContinue();
        }
        if (intent.getBooleanExtra("reset_dock", false)) {
            prefs.resetDock();
            intent.removeExtra("reset_dock");
            return true;
        }
        String dockExtra = intent.getStringExtra("dock");
        if (dockExtra != null) {
            prefs.setDock(Arrays.asList(dockExtra.split("\\s*,\\s*")));
            intent.removeExtra("dock");
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- data

    private void reloadApps() {
        io.execute(() -> {
            Map<String, AppEntry> loaded = appRepo.loadAll();
            runOnUiThread(() -> {
                apps = loaded;
                buildDock();
                reloadContinue();
            });
        });
    }

    private void reloadContinue() {
        if (apps.isEmpty()) return;
        final Map<String, AppEntry> installed = apps;
        final Set<String> hidden = prefs.hiddenItems();
        io.execute(() -> {
            List<WatchNextRepository.Item> items = watchNext.load(installed, hidden, CONTINUE_LIMIT);
            runOnUiThread(() -> {
                if (!continueAdapter.sameAs(items)) {
                    continueAdapter.set(items);
                    // Your newest show may have changed: restart the focused app's art.
                    AppEntry focused = focusedDockApp();
                    if (focused != null) { artPackage = null; showArtFor(focused); }
                    setContinueVisible(!items.isEmpty());
                    if (focusRow == ROW_CONTINUE && !sidebar.isOpen()) {
                        if (items.isEmpty()) focusRow(ROW_DOCK, lastIndex[ROW_DOCK]);
                        else focusRow(ROW_CONTINUE, Math.min(lastIndex[ROW_CONTINUE], items.size() - 1));
                    }
                }
            });
        });
    }

    private void reloadShelves() {
        io.execute(() -> {
            Map<String, List<ShelfRepository.Art>> loaded = shelfRepo.load();
            runOnUiThread(() -> {
                shelves = loaded;
                artPackage = null;      // art may now exist for the app that showed a photo
                AppEntry focused = focusedDockApp();
                showArtFor(focused != null ? focused : (dockApps.isEmpty() ? null : dockApps.get(lastIndex[ROW_DOCK])));
            });
        });
    }

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { reloadApps(); }
    };

    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { updateClock(); }
    };

    private void updateClock() {
        Calendar now = Calendar.getInstance();
        int h = now.get(Calendar.HOUR_OF_DAY);
        int greeting = h < 5 ? R.string.greeting_night : h < 10 ? R.string.greeting_morning
                : h < 18 ? R.string.greeting_day : h < 23 ? R.string.greeting_evening : R.string.greeting_night;
        sidebar.setTime(new SimpleDateFormat("H.mm", FI).format(now.getTime()), getString(greeting));
    }

    // ---------------------------------------------------------------- backdrop art

    /** One backdrop image: a backdrop-sized URL and the original to fall back on. */
    private static final class ArtRef {
        final String url, original;
        ArtRef(String url, String original) { this.url = url; this.original = original; }
    }

    private final Runnable artRunnable = () -> {
        AppEntry app = focusedDockApp();
        if (app != null) showArtFor(app);
    };

    /** Slow carousel: the next image every CAROUSEL_MS while a dock app stays focused. */
    private static final int CAROUSEL_MS = 10_000;
    private int artIndex;
    private final Runnable carousel = () -> {
        AppEntry app = focusedDockApp();
        if (app == null || !resumed || !app.packageName.equals(artPackage)) return;
        artIndex++;
        showArt(app);
    };

    private AppEntry focusedDockApp() {
        View f = getCurrentFocus();
        if (f instanceof DockTileView && f.getParent() == dock) return ((DockTileView) f).app();
        return null;
    }

    /**
     * The household's own shows from this app first (its Watch Next art, newest first),
     * then the app's featured titles. Empty when the app publishes neither.
     */
    private List<ArtRef> artFor(String pkg) {
        List<ArtRef> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (WatchNextRepository.Item it : continueAdapter.items) {
            if (!pkg.equals(it.packageName) || TextUtils.isEmpty(it.imageUri)) continue;
            if (seen.add(it.imageUri)) out.add(new ArtRef(ShelfRepository.upsize(it.imageUri), it.imageUri));
        }
        List<ShelfRepository.Art> featured = shelves.get(pkg);
        if (featured != null) {
            for (int i = 0; i < featured.size() && i < 5; i++) {
                ShelfRepository.Art a = featured.get(i);
                if (seen.add(a.imageUri)) out.add(new ArtRef(a.imageUri, a.original));
            }
        }
        return out;
    }

    /** Focus landed on a dock app: start its art at the newest show (if it's a new app). */
    private void showArtFor(AppEntry app) {
        if (app == null) { stopCarousel(); backdrop.showPhoto(); return; }
        if (!app.packageName.equals(artPackage)) {
            artPackage = app.packageName;
            artIndex = 0;
        }
        showArt(app);
    }

    private void showArt(AppEntry app) {
        stopCarousel();
        List<ArtRef> list = artFor(app.packageName);
        if (list.isEmpty()) { backdrop.showPhoto(); return; }
        ArtRef art = list.get(artIndex % list.size());
        final String pkg = app.packageName;
        final boolean more = list.size() > 1;
        images.loadArt(art.url, screenW, (uri, bitmap) -> {
            if (bitmap == null && !art.original.equals(art.url)) {
                images.loadArt(art.original, screenW, (u2, b2) -> onArt(pkg, u2, b2, more));
            } else {
                onArt(pkg, uri, bitmap, more);
            }
        });
    }

    private void onArt(String pkg, String uri, android.graphics.Bitmap bitmap, boolean more) {
        if (!pkg.equals(artPackage)) return;              // focus moved on meanwhile
        if (bitmap != null) backdrop.showArt(uri, bitmap);
        else backdrop.showPhoto();
        if (more && resumed && pkg.equals(focusedDockPackage())) main.postDelayed(carousel, CAROUSEL_MS);
    }

    private String focusedDockPackage() {
        AppEntry a = focusedDockApp();
        return a == null ? null : a.packageName;
    }

    private void stopCarousel() {
        main.removeCallbacks(carousel);
    }

    // ---------------------------------------------------------------- dock

    private void buildDock() {
        dockApps.clear();
        for (String p : prefs.dock()) {
            AppEntry e = apps.get(p);
            if (e != null) dockApps.add(e);
        }
        float d = getResources().getDisplayMetrics().density;
        int gap = Math.round(18 * d);
        int avail = Math.round(864 * d);
        int n = Math.max(1, dockApps.size());
        int w = Math.min(tileW, (avail - gap * (n - 1)) / n);
        int h = Math.round(w * 9f / 16f);

        boolean hadFocus = dock.hasFocus();
        dock.removeAllViews();
        for (int i = 0; i < dockApps.size(); i++) {
            AppEntry app = dockApps.get(i);
            DockTileView tile = new DockTileView(this);
            tile.bind(app, w, h);
            tile.setOnClickListener(v -> launch(app));
            final int index = i;
            tile.setOnLongClickListener(v -> { openDockEdit(index); return true; });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
            lp.topMargin = (tileH - h) / 2;
            if (i > 0) lp.leftMargin = gap;
            dock.addView(tile, lp);
        }
        applyDims();
        if (firstFocusPending && dock.getChildCount() > 0) {
            firstFocusPending = false;
            focusRow = ROW_DOCK;
            dock.post(() -> focusRow(ROW_DOCK, 0));
        } else if (hadFocus || (focusRow == ROW_DOCK && !sidebar.isOpen())) {
            dock.post(() -> focusRow(ROW_DOCK, Math.min(lastIndex[ROW_DOCK], dock.getChildCount() - 1)));
        }
    }

    private void launch(AppEntry app) {
        safeStart(app.launchIntent());
    }

    private void safeStart(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Log.w(TAG, "cannot start " + intent, e);
        }
    }

    private void openDockEdit(int index) {
        List<Sheet.ListSheet.Row> rows = new ArrayList<>();
        if (index > 0) rows.add(new Sheet.ListSheet.Row(getString(R.string.dock_move_left), null,
                () -> moveDock(index, index - 1)));
        if (index < dockApps.size() - 1) rows.add(new Sheet.ListSheet.Row(getString(R.string.dock_move_right), null,
                () -> moveDock(index, index + 1)));
        rows.add(new Sheet.ListSheet.Row(getString(R.string.dock_remove), null, () -> {
            List<String> pk = dockPackages();
            pk.remove(index);
            prefs.setDock(pk);
            lastIndex[ROW_DOCK] = Math.max(0, Math.min(index, pk.size() - 1));
            buildDock();
        }));
        rows.add(new Sheet.ListSheet.Row(getString(R.string.cancel), null, null));
        // Opens upward from the tile, so it never runs off the bottom of the screen.
        View tile = dock.getChildAt(index);
        int screenH = getWindow().getDecorView().getHeight();
        showSheet(new Sheet.ListSheet(overlay, dockApps.get(index).label, rows, Gravity.START | Gravity.BOTTOM,
                screenH - dock.getTop() + px(18), anchoredLeft(dock.getLeft() + tile.getLeft() + tile.getWidth() / 2), 0));
    }

    /** Left edge for a 320 dp panel centred on {@code centerX}, inside the edge rule. */
    private int anchoredLeft(int centerX) {
        int panelW = px(320);
        return Math.max(px(48), Math.min(centerX - panelW / 2, screenW - px(48) - panelW));
    }

    private void moveDock(int from, int to) {
        List<String> pk = dockPackages();
        String p = pk.remove(from);
        pk.add(to, p);
        prefs.setDock(pk);
        lastIndex[ROW_DOCK] = to;
        buildDock();
    }

    private List<String> dockPackages() {
        List<String> out = new ArrayList<>();
        for (AppEntry e : dockApps) out.add(e.packageName);
        return out;
    }

    // ---------------------------------------------------------------- sidebar + sheets

    @Override
    public void onSidebarItem(int item) {
        stopCarousel();
        switch (item) {
            case Sidebar.HOME: sidebar.close(); break;
            case Sidebar.APPS: sidebar.close(); openApps(); break;
            case Sidebar.INPUTS: sidebar.close(); openInputs(); break;
            case Sidebar.SETTINGS:
                sidebar.close();
                safeStart(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                break;
        }
    }

    @Override
    public void onSidebarClosed() {
        if (sheet == null || !sheet.isOpen()) restoreFocus();
    }

    private void openApps() {
        Set<String> docked = new HashSet<>(dockPackages());
        showSheet(new AppsSheet(overlay, new ArrayList<>(apps.values()), docked, new AppsSheet.Callbacks() {
            @Override public void launch(AppEntry app) { MainActivity.this.launch(app); }
            @Override public boolean toggleDock(AppEntry app) {
                List<String> pk = dockPackages();
                boolean in;
                if (pk.remove(app.packageName)) in = false;
                else if (pk.size() < Prefs.MAX_DOCK) { pk.add(app.packageName); in = true; }
                else return true;
                prefs.setDock(pk);
                if (in) docked.add(app.packageName); else docked.remove(app.packageName);
                buildDock();
                return in;
            }
        }));
    }

    private void openInputs() {
        List<Sheet.ListSheet.Row> rows = new ArrayList<>();
        for (InputRepository.Input in : inputRepo.load()) {
            String detail = in.connected ? null : getString(R.string.input_disconnected);
            rows.add(new Sheet.ListSheet.Row(in.label, detail, () -> safeStart(inputRepo.intentFor(in))));
        }
        showSheet(new Sheet.ListSheet(overlay, getString(R.string.inputs_title), rows,
                Gravity.START | Gravity.TOP, px(22), px(28), 0));
    }

    private void openPosterMenu(WatchNextRepository.Item it, View card) {
        List<Sheet.ListSheet.Row> rows = new ArrayList<>();
        rows.add(new Sheet.ListSheet.Row(getString(R.string.wn_hide), null, () -> {
            prefs.hideItem(it.key());
            reloadContinue();
        }));
        rows.add(new Sheet.ListSheet.Row(getString(R.string.cancel), null, null));
        int[] loc = new int[2];
        card.getLocationInWindow(loc);
        int screenH = getWindow().getDecorView().getHeight();
        showSheet(new Sheet.ListSheet(overlay, it.title, rows, Gravity.START | Gravity.BOTTOM,
                screenH - loc[1] + px(18), anchoredLeft(loc[0] + card.getWidth() / 2), 0));
    }

    private void showSheet(Sheet s) {
        if (sheet != null && sheet.isOpen()) sheet.close();
        sheet = s;
        s.show(() -> {
            if (sheet == s) sheet = null;
            if (!sidebar.isOpen()) restoreFocus();
        });
    }

    // ---------------------------------------------------------------- keys + focus

    /** The view an OK press started on, and whether that press became a long press. */
    private View okTarget;
    private boolean okLongDone;

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        // OK is handled here, not by View timers: tap = click on release, hold = the
        // system's long-press flag (after the long-press timeout) = long click, once.
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
            if (down && e.getRepeatCount() == 0) {
                okTarget = getCurrentFocus();
                okLongDone = false;
                if (okTarget != null) okTarget.setPressed(true);
            } else if (down && e.isLongPress() && !okLongDone) {
                okLongDone = true;
                if (okTarget != null) {
                    okTarget.setPressed(false);
                    if (okTarget.isLongClickable()) okTarget.performLongClick();
                }
            } else if (!down) {
                View t = okTarget;
                okTarget = null;
                if (t != null) {
                    t.setPressed(false);
                    if (!okLongDone && t.isFocused()) t.performClick();
                }
            }
            return true;
        }
        if (sheet != null && sheet.isOpen()) {
            if (sheet.dispatchKey(e)) return true;
            return super.dispatchKeyEvent(e);
        }
        if (sidebar.isOpen()) {
            if (sidebar.dispatchKey(e)) return true;
            return super.dispatchKeyEvent(e);
        }
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (down) moveRow(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (down) moveRow(+1);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (down) {
                    if (lastIndex[focusRow] == 0) sidebar.open(Sidebar.HOME);
                    else moveInRow(-1);
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (down) moveInRow(+1);
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (!down) sidebar.open(Sidebar.HOME);    // home has nowhere to go back to
                return true;
            case KeyEvent.KEYCODE_MENU:
                if (down) {
                    View f = getCurrentFocus();
                    if (f != null) f.performLongClick();
                }
                return true;
        }
        if (code >= KeyEvent.KEYCODE_1 && code <= KeyEvent.KEYCODE_9) {
            int i = code - KeyEvent.KEYCODE_1;
            if (down && e.getRepeatCount() == 0 && i < dockApps.size()) launch(dockApps.get(i));
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    private boolean rowVisible(int row) {
        if (row == ROW_DOCK) return dock.getChildCount() > 0;
        return continueRow.getVisibility() == View.VISIBLE && continueAdapter.getItemCount() > 0;
    }

    private int rowSize(int row) {
        return row == ROW_DOCK ? dock.getChildCount() : continueAdapter.getItemCount();
    }

    private void moveRow(int delta) {
        int r = focusRow + delta;
        while (r >= ROW_DOCK && r <= ROW_CONTINUE && !rowVisible(r)) r += delta;
        if (r < ROW_DOCK || r > ROW_CONTINUE) return;    // edges never fall through
        focusRow(r, lastIndex[r]);
    }

    private void moveInRow(int delta) {
        int i = lastIndex[focusRow] + delta;
        if (i < 0 || i >= rowSize(focusRow)) return;     // row ends are walls
        focusRow(focusRow, i);
    }

    private void focusRow(int row, int index) {
        if (!rowVisible(row)) {
            if (row != ROW_DOCK && rowVisible(ROW_DOCK)) focusRow(ROW_DOCK, lastIndex[ROW_DOCK]);
            return;
        }
        index = Math.max(0, Math.min(index, rowSize(row) - 1));
        if (row == ROW_DOCK) dock.getChildAt(index).requestFocus();
        else focusCard(index);
    }

    private void focusCard(int index) {
        RecyclerView.ViewHolder vh = continueRow.findViewHolderForAdapterPosition(index);
        if (vh != null && vh.itemView.isAttachedToWindow()) {
            ((CardHolder) vh).tile.requestFocus();
            return;
        }
        continueRow.scrollToPosition(index);
        continueRow.post(() -> {
            RecyclerView.ViewHolder v2 = continueRow.findViewHolderForAdapterPosition(index);
            if (v2 != null) ((CardHolder) v2).tile.requestFocus();
        });
    }

    /** Back to the remembered tile; immediately when laid out, else after layout. */
    private void restoreFocus() {
        int row = rowVisible(focusRow) ? focusRow : ROW_DOCK;
        if (row == ROW_DOCK && dock.getChildCount() > 0 && dock.isLaidOut()) {
            focusRow(row, lastIndex[row]);
        } else if (row == ROW_CONTINUE && continueRow.isLaidOut()
                && continueRow.findViewHolderForAdapterPosition(lastIndex[row]) != null) {
            focusRow(row, lastIndex[row]);
        } else {
            findViewById(R.id.stage).post(() -> focusRow(row, lastIndex[row]));
        }
    }

    /** Single source of truth for which row/tile has focus; drives dims, labels and art. */
    private void onFocusMoved(View now) {
        if (now == null || firstFocusPending) return;
        if ((sheet != null && sheet.isOpen()) || sidebar.isOpen()) return;
        if (now.getParent() == dock) {
            focusRow = ROW_DOCK;
            lastIndex[ROW_DOCK] = dock.indexOfChild(now);
            main.removeCallbacks(artRunnable);
            main.postDelayed(artRunnable, ART_DELAY_MS);
        } else {
            View item = continueRow.findContainingItemView(now);
            if (item == null) return;
            stopCarousel();                    // keep the current art while browsing shows
            focusRow = ROW_CONTINUE;
            int pos = continueRow.getChildAdapterPosition(item);
            if (pos != RecyclerView.NO_POSITION) lastIndex[ROW_CONTINUE] = pos;
        }
        applyDims();
    }

    private void applyDims() {
        for (int i = 0; i < dock.getChildCount(); i++) {
            DockTileView t = (DockTileView) dock.getChildAt(i);
            t.setDim(focusRow == ROW_DOCK ? (t.isFocused() ? 0f : DIM_ROWMATE) : DIM_DOCK_IDLE);
        }
        for (int i = 0; i < continueRow.getChildCount(); i++) {
            RecyclerView.ViewHolder vh = continueRow.getChildViewHolder(continueRow.getChildAt(i));
            if (vh instanceof CardHolder) ((CardHolder) vh).applyFocus(focusRow == ROW_CONTINUE);
        }
    }

    private void setContinueVisible(boolean visible) {
        int v = visible ? View.VISIBLE : View.GONE;
        continueHeading.setVisibility(v);
        continueRow.setVisibility(v);
    }

    private int px(float dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    // ---------------------------------------------------------------- continue row

    /** A card: artwork tile, then title and subtitle beneath (always visible, tvOS-style). */
    private final class CardHolder extends RecyclerView.ViewHolder {
        final PosterTileView tile;
        final TextView title, sub;

        CardHolder(LinearLayout root, PosterTileView tile, TextView title, TextView sub) {
            super(root);
            this.tile = tile;
            this.title = title;
            this.sub = sub;
        }

        void applyFocus(boolean rowActive) {
            boolean f = tile.isFocused();
            tile.setDim(rowActive ? (f ? 0f : DIM_ROWMATE) : DIM_POSTER_IDLE);
            title.setTextColor(f ? Palette.WHITE : Palette.alpha(Palette.TEXT, 0.82f));
            float drop = f ? px(4) : 0;     // labels make room for the lifted, scaled card
            title.animate().translationY(drop).setDuration(Motion.CAPTION_MS).start();
            sub.animate().translationY(drop).setDuration(Motion.CAPTION_MS).start();
        }
    }

    private String subtitleFor(WatchNextRepository.Item it) {
        String status = null;
        if (it.watchNextType == android.media.tv.TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_NEXT) {
            status = getString(R.string.wn_next);
        } else if (it.watchNextType == android.media.tv.TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_NEW) {
            status = getString(R.string.wn_new);
        } else if (it.minutesLeft > 0) {
            status = getString(R.string.wn_left, it.minutesLeft);
        }
        // One short fact fits under a card: what's next if known, else whose show it is.
        if (status != null) return status;
        AppEntry app = apps.get(it.packageName);
        return app != null ? app.label : null;
    }

    private final class ContinueAdapter extends RecyclerView.Adapter<CardHolder> {
        private List<WatchNextRepository.Item> items = new ArrayList<>();

        void set(List<WatchNextRepository.Item> next) {
            items = next;
            notifyDataSetChanged();
        }

        boolean sameAs(List<WatchNextRepository.Item> other) {
            if (other.size() != items.size()) return false;
            for (int i = 0; i < other.size(); i++) {
                WatchNextRepository.Item a = items.get(i), b = other.get(i);
                if (a.id != b.id || !TextUtils.equals(a.title, b.title)
                        || Math.abs(a.progress - b.progress) > 0.005f
                        || !TextUtils.equals(a.imageUri, b.imageUri)) return false;
            }
            return true;
        }

        @Override public CardHolder onCreateViewHolder(ViewGroup parent, int type) {
            Context c = parent.getContext();
            LinearLayout root = new LinearLayout(c);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setClipChildren(false);
            root.setLayoutParams(new RecyclerView.LayoutParams(posterW, ViewGroup.LayoutParams.WRAP_CONTENT));
            PosterTileView tile = new PosterTileView(c);
            root.addView(tile, new LinearLayout.LayoutParams(posterW, posterH));
            TextView title = label(c, 14, Type.SEMIBOLD, Palette.TEXT);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
            tlp.topMargin = px(10);
            root.addView(title, tlp);
            TextView sub = label(c, 12, Type.MEDIUM, Palette.TEXT_2);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
            slp.topMargin = px(3);
            root.addView(sub, slp);
            return new CardHolder(root, tile, title, sub);
        }

        private TextView label(Context c, int sp, int weight, int color) {
            TextView t = new TextView(c);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
            t.setTextColor(color);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setIncludeFontPadding(false);
            Type.apply(t, weight);
            return t;
        }

        @Override public void onBindViewHolder(CardHolder h, int position) {
            WatchNextRepository.Item it = items.get(position);
            AppEntry app = apps.get(it.packageName);
            h.tile.bind(app != null ? app.icon : null, it.progress, it.title);
            h.title.setText(it.title);
            String sub = subtitleFor(it);
            h.sub.setText(sub == null ? "" : sub);
            h.applyFocus(focusRow == ROW_CONTINUE);
            images.load(it.imageUri, h.tile, Math.round(posterW * DockTileView.RENDER_SCALE));
            h.tile.setOnClickListener(v -> {
                try {
                    startActivity(it.intent);
                } catch (ActivityNotFoundException | SecurityException e) {
                    if (app != null) launch(app);
                }
            });
            h.tile.setOnLongClickListener(v -> { openPosterMenu(it, h.tile); return true; });
            h.tile.setOnFocusChangeListener((v, has) -> h.applyFocus(focusRow == ROW_CONTINUE || has));
        }

        @Override public int getItemCount() { return items.size(); }
    }
}
