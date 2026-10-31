package com.astanabesta.midrop;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
public class DeclineReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        context.getSystemService(NotificationManager.class).cancel(ReceiverService.NOTIFICATION_ID);
        context.getSharedPreferences("tap", Context.MODE_PRIVATE).edit().putBoolean("pending", false).apply();
    }
}
