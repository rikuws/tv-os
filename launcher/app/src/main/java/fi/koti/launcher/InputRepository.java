package fi.koti.launcher;

import android.content.Context;
import android.content.Intent;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** HDMI/AV/tuner inputs, opened in the TV app the same way the stock launcher does. */
final class InputRepository {

    static final class Input {
        final String id;
        final String label;
        final boolean connected;
        final boolean tuner;

        Input(String id, String label, boolean connected, boolean tuner) {
            this.id = id;
            this.label = label;
            this.connected = connected;
            this.tuner = tuner;
        }
    }

    private final Context context;

    InputRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    /** Tuner first (as "TV"), then passthrough inputs in hardware order. */
    List<Input> load() {
        List<Input> out = new ArrayList<>();
        TvInputManager tim = (TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE);
        if (tim == null) return out;
        Input tuner = null;
        List<Input> pass = new ArrayList<>();
        for (TvInputInfo info : tim.getTvInputList()) {
            if (info.isHidden(context)) continue;
            if (info.getParentId() != null) continue;    // HDMI-CEC child devices
            boolean connected = tim.getInputState(info.getId()) != TvInputManager.INPUT_STATE_DISCONNECTED;
            if (info.isPassthroughInput()) {
                CharSequence custom = info.loadCustomLabel(context);
                CharSequence label = TextUtils.isEmpty(custom) ? info.loadLabel(context) : custom;
                pass.add(new Input(info.getId(), String.valueOf(label), connected, false));
            } else if (tuner == null && info.getType() == TvInputInfo.TYPE_TUNER) {
                tuner = new Input(info.getId(), "TV", true, true);
            }
        }
        Collections.sort(pass, (a, b) -> a.id.compareTo(b.id));   // .../HW2, HW3, ...
        if (tuner != null) out.add(tuner);
        out.addAll(pass);
        return out;
    }

    Intent intentFor(Input input) {
        Intent i = input.tuner
                ? new Intent(Intent.ACTION_VIEW, TvContract.buildChannelsUriForInput(input.id))
                : new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(input.id));
        return i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }
}
