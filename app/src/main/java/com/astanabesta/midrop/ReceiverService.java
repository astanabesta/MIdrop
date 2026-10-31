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
    @Override public void onCreate() { super.onCreate(); HyperIslandDecorator.refresh(this, null); }
    @Override public byte[] processCommandApdu(byte[] command, Bundle extras) {
        if (Protocol.isSelect(command)) {
            Diagnostics.event(this, "Receiver: NFC select received.");
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
        if (pending && !old.equals(offer.publicName()) && System.currentTimeMillis() - oldTime < TapOffer.TTL_MS) return new byte[]{(byte)0x69, (byte)0x85};
        getSharedPreferences("tap", MODE_PRIVATE).edit().putString("id",offer.publicName()).putLong("created",offer.created).putBoolean("pending", true).apply();
        manager.createNotificationChannel(new NotificationChannel("offers", "Incoming file requests", NotificationManager.IMPORTANCE_HIGH));
        if (manager.getNotificationChannel("offers").getImportance() == NotificationManager.IMPORTANCE_NONE) return new byte[]{(byte)0x69, (byte)0x85};
        Intent accept = new Intent(this, TransferService.class).setAction(ACTION_ACCEPT).putExtra("offer", command);
        PendingIntent yes = PendingIntent.getForegroundService(this, NOTIFICATION_ID, accept, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent open = new Intent(this, SharingActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent details = PendingIntent.getActivity(this, NOTIFICATION_ID, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent decline = new Intent(this, DeclineReceiver.class);
        PendingIntent no = PendingIntent.getBroadcast(this, NOTIFICATION_ID, decline, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        long remaining = Math.max(1, Math.min(TapOffer.TTL_MS, offer.created + TapOffer.TTL_MS - System.currentTimeMillis()));
        Notification.Builder builder = new Notification.Builder(this, "offers").setSmallIcon(android.R.drawable.stat_sys_upload_done)
                .setContentTitle("Nearby device wants to share")
                .setContentText(offer.count + (offer.count == 1 ? " file" : " files") + " · accept or decline")
                .setStyle(new Notification.BigTextStyle().bigText("A nearby device wants to share " + offer.count + (offer.count == 1 ? " file." : " files.")))
                .setContentIntent(details).setAutoCancel(true).setTimeoutAfter(remaining)
                .addAction(new Notification.Action.Builder(null, "Decline", no).build())
                .addAction(new Notification.Action.Builder(null, "Accept", yes).build());
        if (Build.VERSION.SDK_INT >= 36) {
            // Android 16 promoted-notification extras; older releases retain the normal alert.
            Bundle promotion = new Bundle();
            promotion.putBoolean("android.requestPromotedOngoing", true);
            promotion.putCharSequence("android.shortCriticalText", offer.count == 1 ? "1 file" : offer.count + " files");
            builder.addExtras(promotion).setOngoing(true).setOnlyAlertOnce(true);
        }
        builder.addExtras(HyperIslandDecorator.extras(this, "Incoming request", -1));
        Notification n = builder.build();
        Diagnostics.event(this, "Receiver: incoming request notification posted.");
        manager.notify(NOTIFICATION_ID, n);
        return OK;
    }
    @Override public void onDeactivated(int reason) { }
}
