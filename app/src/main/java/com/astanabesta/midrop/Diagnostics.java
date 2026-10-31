package com.astanabesta.midrop;

import android.content.Context;
import com.google.android.gms.common.api.ApiException;

/** Explicitly selected event categories only; no addresses, identifiers, file names or keys. */
final class Diagnostics {
    private Diagnostics() { }
    static void event(Context context, String event) { EventLog.add(context, event); }
    static void error(Context context, String stage, Exception e) {
        String code = e instanceof ApiException ? " (Nearby code " + ((ApiException)e).getStatusCode() + ")" : " (" + e.getClass().getSimpleName() + ")";
        EventLog.add(context, stage + code);
    }
}
