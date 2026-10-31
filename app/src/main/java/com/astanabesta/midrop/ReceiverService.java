package com.astanabesta.midrop;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;
import android.os.Build;

public class ReceiverService extends HostApduService {
    static final int NOTIFICATION_ID = 4021;
    static final String ACTION_ACCEPT = "com.astanabesta.midrop.ACCEPT_TAP";
    private static final byte[] OK = {(byte) 0x90, 0};
    @Override public byte[] processCommandApdu(byte[] command, Bundle extras) {
        if (Protocol.isSelect(command)) {
            EventLog.add(this, "Receiver: NFC tap detected.");
            return Protocol.reply();
        }
        TapOffer offer = TapOffer.parse(command, System.currentTimeMillis());
        if (offer == null) return new byte[]{(byte)0x6A, (byte)0x80};
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return new byte[]{(byte)0x69, (byte)0x85};
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (!manager.areNotificationsEnabled()) return new byte[]{(byte)0x69, (byte)0x85};
        String old = getSharedPreferences("tap", MODE_PRIVATE).getString("id", "");
        long oldTime = getSharedPreferences("tap", MODE_PRIVATE).getLong("created", 0);
        boolean pending = getSharedPreferences("tap", MODE_PRIVATE).getBoolean("pending", false);
        if (old.equals(offer.publicName()) && !pending) return new byte[]{(byte)0x69, (byte)0x85};
        if (!old.equals(offer.publicName()) && System.currentTimeMillis() - oldTime < TapOffer.TTL_MS) return new byte[]{(byte)0x69, (byte)0x85};
        getSharedPreferences("tap", MODE_PRIVATE).edit().putString("id",offer.publicName()).putLong("created",offer.created).putBoolean("pending", true).apply();
        manager.createNotificationChannel(new NotificationChannel("offers", "Incoming file requests", NotificationManager.IMPORTANCE_HIGH));
        if (manager.getNotificationChannel("offers").getImportance() == NotificationManager.IMPORTANCE_NONE) return new byte[]{(byte)0x69, (byte)0x85};
        Intent accept = new Intent(this, TapActivity.class).setAction(ACTION_ACCEPT).putExtra("offer", command).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent yes = PendingIntent.getActivity(this, NOTIFICATION_ID, accept, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent decline = new Intent(this, DeclineReceiver.class);
        PendingIntent no = PendingIntent.getBroadcast(this, NOTIFICATION_ID, decline, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, "offers").setSmallIcon(android.R.drawable.stat_sys_upload_done)
                .setContentTitle("Nearby device wants to share")
                .setContentText(offer.count + (offer.count == 1 ? " file" : " files") + " · accept or decline")
                .setStyle(new Notification.BigTextStyle().bigText("A nearby device wants to share " + offer.count + (offer.count == 1 ? " file." : " files.")))
                .setContentIntent(yes).setAutoCancel(true)
                .addAction(new Notification.Action.Builder(null, "Decline", no).build())
                .addAction(new Notification.Action.Builder(null, "Accept", yes).build()).build();
        manager.notify(NOTIFICATION_ID, n);
        return OK;
    }
    @Override public void onDeactivated(int reason) { }
}
