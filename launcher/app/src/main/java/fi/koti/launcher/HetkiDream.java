package fi.koti.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.service.dreams.DreamService;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

/**
 * Screensaver: the home screen's photos with a large clock. A new photo every ten
 * minutes; clock and photo drift a few dp each minute so nothing burns into the panel.
 * Updates on the system time tick only; otherwise completely still.
 */
public class HetkiDream extends DreamService {
    private static final Locale FI = new Locale("fi", "FI");
    private FrameLayout stage;
    private BackdropView lake;
    private int ticks;
    private TextView clock, date;
    private final Random rng = new Random();

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(false);

        float d = getResources().getDisplayMetrics().density;
        FrameLayout root = new FrameLayout(this);
        lake = new BackdropView(this);
        lake.setHomeScrims(false);
        root.addView(lake, new FrameLayout.LayoutParams(-1, -1));

        stage = new FrameLayout(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        clock = new TextView(this);
        clock.setTextColor(Palette.TEXT);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 132);
        clock.setIncludeFontPadding(false);
        clock.setSingleLine(true);   // never break "10.24" after the dot
        clock.setTypeface(Type.display(this, Type.SEMIBOLD));
        // room for the soft shadow, so it is never clipped at the view edge
        clock.setPadding(Math.round(24 * d), Math.round(8 * d), Math.round(24 * d), Math.round(12 * d));
        date = new TextView(this);
        date.setTextColor(Palette.TEXT_2);
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        date.setSingleLine(true);
        clock.setShadowLayer(12 * d, 0, 2 * d, 0x99000000);
        date.setShadowLayer(8 * d, 0, 1.5f * d, 0x99000000);
        Type.apply(date, Type.MEDIUM);
        // explicit wrap_content: a vertical LinearLayout defaults children to match_parent
        // width, which squeezed the clock to the date line's width
        col.addView(clock, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-2, -2);
        dlp.topMargin = Math.round(10 * d);
        col.addView(date, dlp);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        clp.topMargin = Math.round(92 * d);
        stage.addView(col, clp);
        root.addView(stage, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        tick();
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        registerReceiver(ticker, f);
        lake.next();
        tick();
    }

    @Override
    public void onDreamingStopped() {
        unregisterReceiver(ticker);
        super.onDreamingStopped();
    }

    private final BroadcastReceiver ticker = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { tick(); }
    };

    private void tick() {
        if (++ticks % 10 == 0) lake.next();
        Date now = new Date();
        clock.setText(new SimpleDateFormat("H.mm", FI).format(now));
        String day = new SimpleDateFormat("cccc d. MMMM", FI).format(now);
        date.setText(Character.toUpperCase(day.charAt(0)) + day.substring(1));
        // Burn-in drift: shift the whole scene up to 14 dp each minute, slowly.
        float d = getResources().getDisplayMetrics().density;
        float dx = (rng.nextFloat() * 2 - 1) * 14 * d, dy = (rng.nextFloat() * 2 - 1) * 10 * d;
        stage.animate().translationX(dx).translationY(dy).setDuration(4000).start();
        lake.setScaleX(1.03f);
        lake.setScaleY(1.03f);
        lake.animate().translationX(-dx * 0.5f).translationY(-dy * 0.5f).setDuration(4000).start();
    }
}
