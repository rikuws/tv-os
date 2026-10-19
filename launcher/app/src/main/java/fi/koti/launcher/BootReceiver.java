package fi.koti.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Starting the process is enough: KotiApp starts the keep-warm service and the speaker watcher. */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) { }
}
