package fi.koti.launcher;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * tvOS 26-style navigation: a floating glass panel at the left with the greeting and
 * clock, then Koti, Sovellukset, Tulot and Asetukset. Collapsed, it is a small glass
 * capsule showing the time. Opening dims the content behind it; the panel grows out of
 * the capsule's corner.
 */
final class Sidebar extends FrameLayout {
    static final int HOME = 0, APPS = 1, INPUTS = 2, SETTINGS = 3;

    interface Listener {
        void onSidebarItem(int item);
        void onSidebarClosed();
    }

    private final float d;
    private final View dim;
    private final LinearLayout capsule, panel;
    private final TextView capsuleTime, greeting, headerTime;
    private final List<View> items = new ArrayList<>();
    private Listener listener;
    private boolean open;
    private int index;

    Sidebar(Context c) {
        super(c);
        d = getResources().getDisplayMetrics().density;
        setClipChildren(false);

        dim = new View(c);
        dim.setBackgroundColor(Palette.alpha(Palette.SCRIM, 0.5f));
        dim.setAlpha(0f);
        addView(dim, new LayoutParams(-1, -1));

        // Collapsed: [home icon  10.30]
        capsule = new LinearLayout(c);
        capsule.setOrientation(LinearLayout.HORIZONTAL);
        capsule.setGravity(Gravity.CENTER_VERTICAL);
        capsule.setPadding(px(14), 0, px(18), 0);
        capsule.setBackground(new Glass(Palette.GLASS, px(20), d));
        capsule.setElevation(px(6));
        ImageView home = icon(R.drawable.ic_home, Palette.TEXT);
        capsule.addView(home, new LinearLayout.LayoutParams(px(20), px(20)));
        capsuleTime = text(17, Type.SEMIBOLD, Palette.TEXT);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.leftMargin = px(10);
        capsule.addView(capsuleTime, tlp);
        LayoutParams clp = new LayoutParams(-2, px(40), Gravity.TOP | Gravity.START);
        clp.leftMargin = px(40);
        clp.topMargin = px(28);
        addView(capsule, clp);

        // Expanded panel
        panel = new LinearLayout(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(px(12), px(16), px(12), px(12));
        panel.setBackground(new Glass(Palette.GLASS_STRONG, px(30), d));
        panel.setElevation(px(24));
        panel.setClipChildren(false);
        panel.setClipToPadding(false);

        LinearLayout header = new LinearLayout(c);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(px(12), 0, px(10), px(14));
        greeting = text(17, Type.SEMIBOLD, Palette.TEXT);
        header.addView(greeting, new LinearLayout.LayoutParams(0, -2, 1));
        headerTime = text(16, Type.MEDIUM, Palette.TEXT_2);
        header.addView(headerTime);
        panel.addView(header);

        addItem(R.drawable.ic_home, c.getString(R.string.nav_home));
        addItem(R.drawable.ic_apps, c.getString(R.string.action_apps));
        addItem(R.drawable.ic_input, c.getString(R.string.action_inputs));
        addItem(R.drawable.ic_settings, c.getString(R.string.action_settings));

        LayoutParams plp = new LayoutParams(px(250), -2, Gravity.TOP | Gravity.START);
        plp.leftMargin = px(28);
        plp.topMargin = px(22);
        panel.setVisibility(INVISIBLE);
        addView(panel, plp);
    }

    void setListener(Listener l) { listener = l; }

    void setTime(String time, String greetingText) {
        capsuleTime.setText(time);
        headerTime.setText(time);
        greeting.setText(greetingText);
    }

    boolean isOpen() { return open; }

    void open(int focusItem) {
        if (open) return;
        open = true;
        index = Math.max(0, Math.min(focusItem, items.size() - 1));
        boolean reduced = Motion.reduced(getContext());
        panel.setVisibility(VISIBLE);
        panel.setPivotX(0);
        panel.setPivotY(0);
        if (reduced) {
            panel.setAlpha(1); panel.setScaleX(1); panel.setScaleY(1);
            dim.setAlpha(1); capsule.setAlpha(0);
        } else {
            panel.setAlpha(0f);
            panel.setScaleX(0.9f);
            panel.setScaleY(0.9f);
            panel.animate().alpha(1).scaleX(1).scaleY(1).setDuration(Motion.SHEET_MS)
                    .setInterpolator(Motion.EASE_OUT).start();
            dim.animate().alpha(1).setDuration(Motion.SHEET_MS).start();
            capsule.animate().alpha(0).setDuration(Motion.SHEET_MS / 2).start();
        }
        items.get(index).requestFocus();
    }

    void close() {
        if (!open) return;
        open = false;
        boolean reduced = Motion.reduced(getContext());
        Runnable done = () -> panel.setVisibility(INVISIBLE);
        if (reduced) {
            dim.setAlpha(0); capsule.setAlpha(1); done.run();
        } else {
            panel.animate().alpha(0).scaleX(0.94f).scaleY(0.94f).setDuration(Motion.SHEET_MS * 2 / 3)
                    .setInterpolator(Motion.EASE_OUT).withEndAction(done).start();
            dim.animate().alpha(0).setDuration(Motion.SHEET_MS).start();
            capsule.animate().alpha(1).setDuration(Motion.SHEET_MS).start();
        }
        if (listener != null) listener.onSidebarClosed();
    }

    /** D-pad while open: Up/Down walk the items (walls at the ends); Right or Back closes. */
    boolean dispatchKey(KeyEvent e) {
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (down && index > 0) items.get(index - 1).requestFocus();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (down && index < items.size() - 1) items.get(index + 1).requestFocus();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (down) close();
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (!down) close();
                return true;
        }
        return false;
    }

    private void addItem(int iconRes, String label) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(px(14), 0, px(14), 0);
        row.setFocusable(true);
        row.setClickable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(px(24));
        bg.setColor(0);
        row.setBackground(bg);
        ImageView ic = icon(iconRes, Palette.TEXT);
        row.addView(ic, new LinearLayout.LayoutParams(px(22), px(22)));
        TextView t = text(18, Type.MEDIUM, Palette.TEXT);
        t.setText(label);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = px(14);
        row.addView(t, lp);
        final int pos = items.size();
        row.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? Palette.WHITE : 0);
            int fg = has ? Palette.ON_FOCUS : Palette.TEXT;
            t.setTextColor(fg);
            ic.setImageTintList(ColorStateList.valueOf(fg));
            if (has) index = pos;
        });
        row.setOnClickListener(v -> {
            if (listener != null) listener.onSidebarItem(pos);
        });
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, px(48));
        if (pos > 0) rlp.topMargin = px(4);
        panel.addView(row, rlp);
        items.add(row);
    }

    private ImageView icon(int res, int tint) {
        ImageView v = new ImageView(getContext());
        Drawable dr = getContext().getDrawable(res).mutate();
        v.setImageDrawable(dr);
        v.setImageTintList(ColorStateList.valueOf(tint));
        return v;
    }

    private TextView text(int sp, int weight, int color) {
        TextView t = new TextView(getContext());
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setIncludeFontPadding(false);
        Type.apply(t, weight);
        return t;
    }

    private int px(float dp) { return Math.round(dp * d); }
}
