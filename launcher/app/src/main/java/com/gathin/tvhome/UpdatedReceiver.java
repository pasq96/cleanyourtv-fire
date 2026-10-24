package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** After an update (adb install -r) the process is dead: start it and put the key helpers back on the enabled list. */
public final class UpdatedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) return;
        if (Config.load(context).restoreKeyServices) ServiceRepair.restore(context);
    }
}
