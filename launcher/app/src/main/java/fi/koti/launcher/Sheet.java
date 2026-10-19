package fi.koti.launcher;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * A panel that rises over the dimmed home screen. Owns the D-pad while open:
 * Up/Down walk its rows, Left/Right do nothing, Back closes. Subclasses fill {@link #panel}.
 */
abstract class Sheet {
    protected final Context context;
    protected final float d;
    protected final FrameLayout host;
    protected final View scrim;
    protected final LinearLayout panel;
    private Runnable onClosed;
    private boolean open;

    Sheet(FrameLayout host) {
        this.host = host;
        this.context = host.getContext();
        this.d = context.getResources().getDisplayMetrics().density;
        scrim = new View(context);
        scrim.setBackgroundColor(Palette.alpha(Palette.SCRIM, 0.5f));
        panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(new Glass(Palette.GLASS_STRONG, 30 * d, d));
        panel.setElevation(24 * d);
        panel.setClipChildren(false);
        panel.setClipToPadding(false);
    }

    /** Where the panel sits on screen. */
    protected abstract FrameLayout.LayoutParams panelParams();

    /** The view that gets focus when the sheet opens. */
    protected abstract View initialFocus();

    /** Key handling for the sheet's own layout; return true when consumed. */
    protected abstract boolean onDpad(int keyCode);

    void show(Runnable onClosed) {
        this.onClosed = onClosed;
        open = true;
        host.removeAllViews();
        host.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
        host.addView(panel, panelParams());
        host.setVisibility(View.VISIBLE);
        boolean reduced = Motion.reduced(context);
        scrim.setAlpha(reduced ? 1 : 0);
        panel.setAlpha(reduced ? 1 : 0);
        panel.setTranslationY(reduced ? 0 : Motion.dp(context, Motion.SHEET_RISE_DP));
        scrim.animate().alpha(1).setDuration(Motion.SHEET_MS).setInterpolator(Motion.EASE_OUT).start();
        panel.animate().alpha(1).translationY(0).setDuration(Motion.SHEET_MS)
                .setInterpolator(Motion.EASE_OUT).start();
        View f = initialFocus();
        if (f != null) f.post(f::requestFocus);
    }

    boolean isOpen() { return open; }

    void close() {
        if (!open) return;
        open = false;
        boolean reduced = Motion.reduced(context);
        // Hand focus back first: removing the focused view would let Android pick a
        // default focus, which the home screen would then mistake for the user's choice.
        Runnable done = () -> {
            if (onClosed != null) onClosed.run();
            host.removeAllViews();
            host.setVisibility(View.GONE);
        };
        if (reduced) { done.run(); return; }
        scrim.animate().alpha(0).setDuration(Motion.SHEET_MS / 2).start();
        panel.animate().alpha(0).translationY(Motion.dp(context, Motion.SHEET_RISE_DP / 2))
                .setDuration(Motion.SHEET_MS / 2).withEndAction(done).start();
    }

    /** Returns true when the event was handled by the sheet. */
    boolean dispatchKey(KeyEvent e) {
        int code = e.getKeyCode();
        boolean dpad = code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
                || code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT;
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_HOME) {
            if (e.getAction() == KeyEvent.ACTION_UP) close();
            return true;
        }
        if (dpad) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) onDpad(code);
            return true;
        }
        return false;   // OK / long-press go to the focused view
    }

    protected TextView title(String text) {
        TextView t = new TextView(context);
        t.setText(text);
        t.setTextColor(Palette.TEXT_2);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        Type.apply(t, Type.SEMIBOLD);
        t.setIncludeFontPadding(false);
        return t;
    }

    // ---------------------------------------------------------------------------------

    /** A short vertical list of actions, e.g. inputs or dock editing. */
    static final class ListSheet extends Sheet {
        static final class Row {
            final String label, detail;
            final Runnable action;
            Row(String label, String detail, Runnable action) {
                this.label = label; this.detail = detail; this.action = action;
            }
        }

        private final List<View> rows = new ArrayList<>();
        private final int gravity;
        private final int marginTop, marginSide;
        private int index;

        ListSheet(FrameLayout host, String heading, List<Row> items, int gravity,
                  int marginTopPx, int marginSidePx, int initialIndex) {
            super(host);
            this.gravity = gravity;
            this.marginTop = marginTopPx;
            this.marginSide = marginSidePx;
            int pad = (int) (12 * d);
            panel.setPadding(pad, (int) (18 * d), pad, pad);
            TextView t = title(heading);
            t.setPadding((int) (14 * d), 0, 0, (int) (12 * d));
            panel.addView(t);
            for (Row r : items) {
                View row = makeRow(r);
                rows.add(row);
                panel.addView(row);
            }
            index = Math.max(0, Math.min(initialIndex, rows.size() - 1));
        }

        private View makeRow(Row r) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setFocusable(true);
            row.setClickable(true);
            int padH = (int) (14 * d);
            row.setPadding(padH, 0, padH, 0);
            row.setMinimumHeight((int) (48 * d));
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(24 * d);
            bg.setColor(0);
            row.setBackground(bg);

            TextView label = new TextView(context);
            label.setText(r.label);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            label.setTextColor(Palette.TEXT);
            Type.apply(label, Type.MEDIUM);
            label.setSingleLine(true);
            row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            TextView detail = null;
            if (r.detail != null) {
                detail = new TextView(context);
                detail.setText(r.detail);
                detail.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                detail.setTextColor(Palette.TEXT_2);
                Type.apply(detail, Type.REGULAR);
                row.addView(detail);
            }
            final TextView detailF = detail;
            row.setOnFocusChangeListener((v, has) -> {
                bg.setColor(has ? Palette.WHITE : 0);
                label.setTextColor(has ? Palette.ON_FOCUS : Palette.TEXT);
                if (detailF != null) detailF.setTextColor(has ? Palette.alpha(Palette.ON_FOCUS, 0.6f) : Palette.TEXT_2);
                if (has) index = rows.indexOf(v);
            });
            row.setOnClickListener(v -> {
                close();
                if (r.action != null) r.action.run();
            });
            return row;
        }

        @Override protected FrameLayout.LayoutParams panelParams() {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams((int) (320 * d), -2, gravity);
            // With BOTTOM gravity the vertical margin anchors the panel's bottom edge, so a
            // menu can open upward from what it belongs to.
            if ((gravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.BOTTOM) lp.bottomMargin = marginTop;
            else lp.topMargin = marginTop;
            lp.leftMargin = marginSide;
            lp.rightMargin = marginSide;
            return lp;
        }

        @Override protected View initialFocus() { return rows.isEmpty() ? null : rows.get(index); }

        @Override protected boolean onDpad(int code) {
            int next = index;
            if (code == KeyEvent.KEYCODE_DPAD_UP) next--;
            else if (code == KeyEvent.KEYCODE_DPAD_DOWN) next++;
            if (next >= 0 && next < rows.size()) rows.get(next).requestFocus();
            return true;
        }
    }
}
