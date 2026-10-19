package fi.koti.launcher;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.reflect.Method;

/**
 * Keeps the Bluetooth speaker connected across standby.
 *
 * What the TV does on its own (observed with dumpsys): going to standby it disconnects the
 * speaker; on wake it makes exactly one A2DP connect attempt. If the speaker is still
 * waking up, that attempt times out and nothing retries, which is why the TV used to need
 * a second power-on. Here, after a wake or boot, if the speaker the household last used
 * is not connected, we retry with growing gaps for a little over two minutes and stop the
 * moment it connects (or another speaker does).
 */
final class SpeakerReconnect {
    private static final String TAG = "Koti.Speaker";
    private static final String PREF = "audio_device";
    /** Offsets from the wake/boot moment; the first gives the TV's own attempt a chance. */
    private static final long[] RETRY_AT_MS = {4_000, 9_000, 15_000, 22_000, 32_000, 45_000,
            60_000, 80_000, 105_000, 135_000};

    private final Context context;
    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private BluetoothA2dp a2dp;
    private int step = -1;

    SpeakerReconnect(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences("koti", Context.MODE_PRIVATE);
    }

    void start() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) return;
        adapter.getProfileProxy(context, new BluetoothProfile.ServiceListener() {
            @Override public void onServiceConnected(int profile, BluetoothProfile proxy) {
                a2dp = (BluetoothA2dp) proxy;
                for (BluetoothDevice d : a2dp.getConnectedDevices()) remember(d);
                watch("start");
            }
            @Override public void onServiceDisconnected(int profile) { a2dp = null; }
        }, BluetoothProfile.A2DP);

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        f.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
        context.registerReceiver(receiver, f);
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            String action = i.getAction();
            if (Intent.ACTION_SCREEN_ON.equals(action)) {
                watch("wake");
            } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                stop();
            } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                if (i.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_ON) watch("bluetooth on");
            } else if (BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED.equals(action)) {
                BluetoothDevice d = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (d != null && i.getIntExtra(BluetoothProfile.EXTRA_STATE, -1) == BluetoothProfile.STATE_CONNECTED) {
                    remember(d);
                    if (step >= 0) Log.i(TAG, "connected: " + d.getName());
                    stop();
                }
            }
        }
    };

    /** Start (or restart) the retry schedule. */
    void watch(String why) {
        stop();
        if (a2dp == null) return;          // the proxy connecting starts a watch itself
        if (target() == null) return;
        step = 0;
        Log.i(TAG, "watching speaker (" + why + ")");
        main.postDelayed(tick, RETRY_AT_MS[0]);
    }

    private void stop() {
        main.removeCallbacks(tick);
        step = -1;
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            BluetoothDevice d = target();
            if (a2dp == null || d == null || step < 0) return;
            int state = a2dp.getConnectionState(d);
            if (state == BluetoothProfile.STATE_CONNECTED) {
                stop();
                return;
            }
            if (!a2dp.getConnectedDevices().isEmpty()) {   // another speaker took over: respect it
                stop();
                return;
            }
            if (state == BluetoothProfile.STATE_DISCONNECTED) {
                boolean ok = connect(d);
                Log.i(TAG, "connect attempt " + (step + 1) + " to " + d.getName() + (ok ? "" : " (refused)"));
            }
            step++;
            if (step < RETRY_AT_MS.length) {
                main.postDelayed(this, RETRY_AT_MS[step] - RETRY_AT_MS[step - 1]);
            } else {
                Log.i(TAG, "gave up: speaker did not answer");
                step = -1;
            }
        }
    };

    private void remember(BluetoothDevice d) {
        if (!d.getAddress().equals(prefs.getString(PREF, null))) {
            prefs.edit().putString(PREF, d.getAddress()).apply();
            Log.i(TAG, "speaker is now " + d.getName());
        }
    }

    /** The last speaker the household connected, if it is still paired. */
    private BluetoothDevice target() {
        String addr = prefs.getString(PREF, null);
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (addr == null || adapter == null || !adapter.isEnabled()) return null;
        for (BluetoothDevice d : adapter.getBondedDevices()) {
            if (d.getAddress().equals(addr)) return d;
        }
        return null;
    }

    /** BluetoothA2dp.connect is hidden on Android 9 but callable (light greylist). */
    private boolean connect(BluetoothDevice d) {
        try {
            Method m = BluetoothA2dp.class.getMethod("connect", BluetoothDevice.class);
            return (Boolean) m.invoke(a2dp, d);
        } catch (Exception e) {
            Log.w(TAG, "connect failed", e);
            return false;
        }
    }

    /** Test hook: drop the speaker, then let the watcher bring it back. */
    void testDisconnectAndRecover() {
        BluetoothDevice d = target();
        if (a2dp == null || d == null) return;
        try {
            Method m = BluetoothA2dp.class.getMethod("disconnect", BluetoothDevice.class);
            m.invoke(a2dp, d);
            Log.i(TAG, "test: disconnected " + d.getName());
        } catch (Exception e) {
            Log.w(TAG, "disconnect failed", e);
        }
        main.postDelayed(() -> watch("test"), 1500);
    }
}
