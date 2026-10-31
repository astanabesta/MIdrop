package com.astanabesta.midrop;

import android.content.Context;
import java.text.DateFormat;
import java.util.Date;

final class EventLog {
    private static final String PREFS = "events";
    static synchronized void add(Context context, String message) {
        android.content.SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String entry = DateFormat.getTimeInstance().format(new Date()) + " — " + message + "\n";
        String text = entry + prefs.getString("log", "");
        prefs.edit().putString("log", text.substring(0, Math.min(text.length(), 12000))).apply();
    }
    static String read(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("log", "No events yet.");
    }
    static void clear(Context context) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply(); }
}
