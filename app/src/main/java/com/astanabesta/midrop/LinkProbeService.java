package com.astanabesta.midrop;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.IBinder;
import android.os.SystemClock;
import java.net.NetworkInterface;
import java.util.Enumeration;

/** User-initiated, bounded observation of broad interface categories, not a network connection. */
public final class LinkProbeService extends Service {
    private static final String STOP = "com.astanabesta.midrop.STOP_LINK_PROBE";
    private static final int NOTIFICATION_ID = 9413;
    private volatile boolean active;
    private Thread worker;

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && STOP.equals(intent.getAction())) { active = false; if (worker != null) worker.interrupt(); return START_NOT_STICKY; }
        if (active) return START_NOT_STICKY;
        String channel = "link_probe";
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(channel, "iPhone link research", NotificationManager.IMPORTANCE_LOW));
        Intent cancel = new Intent(this, LinkProbeService.class).setAction(STOP);
        PendingIntent stop = PendingIntent.getService(this, 9, cancel, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, channel).setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("MIdrop · link check")
            .setContentText("Observing for up to 60 seconds. Open Quick Share now.")
            .setOngoing(true).addAction(new Notification.Action.Builder(null, "Stop", stop).build()).build();
        startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        active = true;
        Diagnostics.event(this, "iPhone link check: started; open Quick Share within 60 seconds.");
        worker = new Thread(() -> {
            int seen = 0, samples = 0, errors = 0;
            long end = SystemClock.elapsedRealtime() + 60_000;
            try {
                while (active && SystemClock.elapsedRealtime() < end) {
                    try {
                        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                        if (interfaces == null) errors++;
                        else {
                            samples++;
                            while (interfaces.hasMoreElements()) seen |= LinkObservation.classify(interfaces.nextElement().getName());
                        }
                    } catch (Exception e) { errors++; }
                    try { Thread.sleep(1000); } catch (InterruptedException e) { break; }
                }
            } finally {
                active = false;
                Diagnostics.event(this, "iPhone link check: finished; samples=" + samples + "; read errors=" + errors
                    + "; Apple-link-name observed=" + ((seen & 1) != 0) + "; Aware-name observed=" + ((seen & 2) != 0)
                    + "; P2P-name observed=" + ((seen & 4) != 0) + ". These are not connectivity or access tests.");
                stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
            }
        }, "midrop-link-check");
        worker.start();
        return START_NOT_STICKY;
    }
    @Override public void onDestroy() { active = false; if (worker != null) worker.interrupt(); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
