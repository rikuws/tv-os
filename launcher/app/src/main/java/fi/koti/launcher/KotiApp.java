package fi.koti.launcher;

import android.app.Application;

/** Process-wide pieces that must run whether or not the home screen is showing. */
public class KotiApp extends Application {
    private static SpeakerReconnect speaker;

    @Override
    public void onCreate() {
        super.onCreate();
        // Parse the variable font's faces off the main thread while the UI inflates.
        new Thread(() -> {
            for (int w : new int[]{Type.MEDIUM, Type.SEMIBOLD, Type.REGULAR}) Type.face(this, w);
            Type.display(this, Type.BOLD);
            Type.display(this, Type.SEMIBOLD);
        }, "koti-fonts").start();
        speaker = new SpeakerReconnect(this);
        speaker.start();
        startForegroundService(new android.content.Intent(this, KeepWarmService.class));
    }

    static SpeakerReconnect speaker() { return speaker; }
}
