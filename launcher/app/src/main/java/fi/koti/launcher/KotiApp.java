package fi.koti.launcher;

import android.app.Application;

/** Process-wide pieces that must run whether or not the home screen is showing. */
public class KotiApp extends Application {
    private static SpeakerReconnect speaker;

    @Override
    public void onCreate() {
        super.onCreate();
        speaker = new SpeakerReconnect(this);
        speaker.start();
    }

    static SpeakerReconnect speaker() { return speaker; }
}
