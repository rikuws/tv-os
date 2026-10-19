package fi.koti.launcher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/**
 * Keeps Koti's process resident (foreground priority) while other apps run, so pressing
 * Home never pays a cold start (~1.3 s on this TV) and the Bluetooth speaker watcher is
 * always alive to catch the next wake-up. Costs ~70 MB of RAM that the TV has spare.
 * Android TV shows no status bar, so the required notification stays out of sight.
 */
public class KeepWarmService extends Service {
    private static final String CHANNEL = "koti-warm";

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Koti", NotificationManager.IMPORTANCE_MIN);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_home)
                .setContentTitle("Koti")
                .setOngoing(true)
                .build();
        startForeground(1, n);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
