package fi.koti.launcher;

import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Every installed app in a five-column grid. OK opens; holding OK adds the app to the
 * dock or takes it out, marked by an amber dot beside its name.
 */
final class AppsSheet extends Sheet {
    interface Callbacks {
        void launch(AppEntry app);
        /** Returns true when the app is in the dock after the toggle. */
        boolean toggleDock(AppEntry app);
    }

    private static final int COLUMNS = 5;
    private final List<AppEntry> apps;
    private final Set<String> docked;
    private final Callbacks cb;
    private final RecyclerView grid;
    private final int tileW, tileH;
    private int index;

    AppsSheet(FrameLayout host, List<AppEntry> apps, Set<String> docked, Callbacks cb) {
        super(host);
        this.apps = new ArrayList<>(apps);
        this.docked = docked;
        this.cb = cb;
        tileW = (int) (148 * d);
        tileH = (int) (tileW * 9f / 16f);

        panel.setBackground(new Glass(Palette.GLASS_SOLID, 30 * d, d));
        panel.setPadding((int) (32 * d), (int) (30 * d), (int) (32 * d), (int) (18 * d));
        TextView heading = new TextView(context);
        heading.setText(R.string.apps_title);
        heading.setTextColor(Palette.TEXT);
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        heading.setTypeface(Type.display(context, Type.BOLD));
        heading.setIncludeFontPadding(false);
        heading.setPadding((int) (8 * d), 0, 0, 0);
        panel.addView(heading);
        TextView hint = title(context.getString(R.string.apps_hint));
        hint.setPadding((int) (8 * d), (int) (10 * d), 0, (int) (18 * d));
        panel.addView(hint);

        grid = new RecyclerView(context);
        grid.setLayoutManager(new GridLayoutManager(context, COLUMNS));
        // The panel clips the grid at the grid's bounds (inside the panel border), while
        // the grid lets each focused tile's lift and ring spill over its cell. (A view is
        // clipped by its parent's clipChildren, so the panel, not the grid, does the clip.)
        panel.setClipChildren(true);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        grid.setPadding((int) (8 * d), 0, (int) (8 * d), (int) (24 * d));
        // Rows past the panel edge fade out instead of being cut, so "more below" reads.
        grid.setVerticalFadingEdgeEnabled(true);
        grid.setFadingEdgeLength((int) (56 * d));
        grid.setItemAnimator(null);
        grid.setAdapter(new Adapter());
        panel.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1f));
    }

    @Override protected FrameLayout.LayoutParams panelParams() {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -1);
        int m = (int) (24 * d);
        lp.setMargins(m, m, m, m);
        return lp;
    }

    @Override protected View initialFocus() {
        return null;   // grid children are not laid out yet; focus after layout
    }

    @Override void show(Runnable onClosed) {
        super.show(onClosed);
        grid.post(() -> focus(0));
    }

    private void focus(int i) {
        RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(i);
        if (vh != null) {
            ((Holder) vh).tile.requestFocus();
        } else {
            grid.scrollToPosition(i);
            grid.post(() -> {
                RecyclerView.ViewHolder v2 = grid.findViewHolderForAdapterPosition(i);
                if (v2 != null) ((Holder) v2).tile.requestFocus();
            });
        }
    }

    @Override protected boolean onDpad(int code) {
        int n = apps.size(), col = index % COLUMNS, next = index;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:  if (col > 0) next--; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: if (col < COLUMNS - 1 && index + 1 < n) next++; break;
            case KeyEvent.KEYCODE_DPAD_UP:    if (index - COLUMNS >= 0) next -= COLUMNS; break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (index + COLUMNS < n) next += COLUMNS;
                else if ((index / COLUMNS + 1) * COLUMNS < n) next = n - 1;   // short last row
                break;
        }
        if (next != index) focus(next);
        return true;
    }

    private final class Holder extends RecyclerView.ViewHolder {
        final DockTileView tile;
        final TextView label;
        Holder(LinearLayout root, DockTileView tile, TextView label) {
            super(root);
            this.tile = tile;
            this.label = label;
        }
    }

    private final class Adapter extends RecyclerView.Adapter<Holder> {
        @Override public Holder onCreateViewHolder(ViewGroup parent, int type) {
            LinearLayout root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER_HORIZONTAL);
            root.setClipChildren(false);
            root.setClipToPadding(false);
            // headroom for the focused tile's lift + scale + ring, so the cell never clips it
            root.setPadding(0, (int) (22 * d), 0, (int) (10 * d));
            DockTileView tile = new DockTileView(context);
            root.addView(tile, new LinearLayout.LayoutParams(tileW, tileH));
            TextView label = new TextView(context);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            Type.apply(label, Type.MEDIUM);
            label.setSingleLine(true);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            label.setGravity(Gravity.CENTER);
            label.setCompoundDrawablePadding((int) (6 * d));
            label.setMaxWidth(tileW);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.topMargin = (int) (12 * d);
            root.addView(label, lp);
            return new Holder(root, tile, label);
        }

        @Override public void onBindViewHolder(Holder h, int position) {
            AppEntry app = apps.get(position);
            h.tile.bind(app, tileW, tileH);
            h.tile.setDim(0.12f);
            h.label.setText(app.label);
            styleLabel(h, app, false);
            h.tile.setOnFocusChangeListener((v, has) -> {
                h.tile.setDim(has ? 0f : 0.12f);
                styleLabel(h, app, has);
                if (has) {
                    index = h.getAdapterPosition();
                    // bring the whole cell (tile + name) into view, not just the tile
                    grid.smoothScrollToPosition(index);
                }
            });
            h.tile.setOnClickListener(v -> { close(); cb.launch(app); });
            h.tile.setOnLongClickListener(v -> {
                cb.toggleDock(app);
                styleLabel(h, app, h.tile.hasFocus());
                return true;
            });
        }

        @Override public int getItemCount() { return apps.size(); }
    }

    private void styleLabel(Holder h, AppEntry app, boolean focused) {
        h.label.setTextColor(focused ? Palette.TEXT : Palette.TEXT_2);
        if (docked.contains(app.packageName)) {
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(Palette.WHITE);
            int s = (int) (7 * d);
            dot.setBounds(0, 0, s, s);
            h.label.setCompoundDrawables(dot, null, null, null);
        } else {
            h.label.setCompoundDrawables(null, null, null, null);
        }
    }
}
