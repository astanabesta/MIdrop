package com.astanabesta.midrop;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Base64;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.*;
import org.json.JSONObject;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** User-accepted, foreground receiving session; never launched for an unaccepted tap. */
public final class TransferService extends Service {
    private static final String SERVICE_ID = "com.astanabesta.midrop.tap.v1";
    private static final String CHANNEL = "transfers";
    private static final String CANCEL = "com.astanabesta.midrop.CANCEL_TRANSFER";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ConnectionsClient client;
    private TapOffer offer;
    private String endpoint, digits, filename, mime;
    private boolean requested, verified, expecting, saving;
    private volatile boolean terminal;
    private int received, lastPercent = -1;
    private Payload incoming;
    private final Runnable timeout = () -> finishWith("Transfer timed out. Please try again.", false);

    @Override public void onCreate() {
        super.onCreate(); client = Nearby.getConnectionsClient(this);
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL, "File transfers", NotificationManager.IMPORTANCE_LOW));
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && CANCEL.equals(intent.getAction())) { finishWith("Transfer cancelled.", false); return START_NOT_STICKY; }
        if (offer != null) return START_NOT_STICKY;
        Notification initial = progressNotification("Connecting to sender…", -1);
        if (Build.VERSION.SDK_INT >= 29) startForeground(ReceiverService.NOTIFICATION_ID, initial, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        else startForeground(ReceiverService.NOTIFICATION_ID, initial);
        TapOffer parsed = intent == null ? null : TapOffer.parse(intent.getByteArrayExtra("offer"), System.currentTimeMillis());
        android.content.SharedPreferences prefs = getSharedPreferences("tap", MODE_PRIVATE);
        if (parsed == null || !prefs.getBoolean("pending", false) || !parsed.publicName().equals(prefs.getString("id", ""))) {
            finishWith("Request expired or already handled.", false); return START_NOT_STICKY;
        }
        prefs.edit().putBoolean("pending", false).apply(); offer = parsed;
        Diagnostics.event(this, "Receiver: request accepted; starting nearby discovery.");
        handler.postDelayed(timeout, 120_000);
        client.startDiscovery(SERVICE_ID, discovery, new DiscoveryOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build())
            .addOnSuccessListener(v -> Diagnostics.event(this, "Receiver: nearby discovery started."))
            .addOnFailureListener(e -> { Diagnostics.error(this, "Receiver: discovery failed", e); finishWith("Couldn't discover sender. Check nearby permissions and try again.", false); });
        return START_NOT_STICKY;
    }
    private void refreshTimeout() { handler.removeCallbacks(timeout); handler.postDelayed(timeout, 120_000); }
    private final EndpointDiscoveryCallback discovery = new EndpointDiscoveryCallback() {
        @Override public void onEndpointFound(String id, DiscoveredEndpointInfo info) {
            if (!terminal && offer != null && !requested && offer.publicName().equals(info.getEndpointName())) {
                Diagnostics.event(TransferService.this, "Receiver: matching sender found; requesting connection.");
                requested = true;
                client.requestConnection("MIdrop receiver", id, lifecycle)
                    .addOnFailureListener(e -> { Diagnostics.error(TransferService.this, "Receiver: request connection failed", e); finishWith("Couldn't connect to sender.", false); });
            }
        }
        @Override public void onEndpointLost(String id) { }
    };
    private final ConnectionLifecycleCallback lifecycle = new ConnectionLifecycleCallback() {
        @Override public void onConnectionInitiated(String id, ConnectionInfo info) {
            if (terminal || offer == null || System.currentTimeMillis() - offer.created > TapOffer.TTL_MS) { client.rejectConnection(id); return; }
            Diagnostics.event(TransferService.this, "Receiver: nearby connection initiated.");
            digits = info.getAuthenticationDigits();
            client.acceptConnection(id, payloads).addOnFailureListener(e -> finishWith("Connection failed.", false));
        }
        @Override public void onConnectionResult(String id, ConnectionResolution result) {
            if (!result.getStatus().isSuccess()) { Diagnostics.event(TransferService.this, "Receiver: connection failed (Nearby code " + result.getStatus().getStatusCode() + ")."); finishWith("Connection failed.", false); return; }
            Diagnostics.event(TransferService.this, "Receiver: connection established.");
            endpoint = id; client.stopDiscovery(); refreshTimeout(); update("Verifying secure connection…", -1);
            try {
                JSONObject m = new JSONObject(); m.put("kind", "proof");
                m.put("proof", Base64.encodeToString(SessionProof.calculate(offer.secret, offer.id, digits, "receiver"), Base64.NO_WRAP)); send(m);
            } catch (Exception e) { finishWith("Couldn't verify the connection.", false); }
        }
        @Override public void onDisconnected(String id) {
            Diagnostics.event(TransferService.this, "Receiver: nearby disconnected."); endpoint = null;
            if (!terminal) finishWith("Sender disconnected before the transfer finished.", false);
        }
    };
    private void send(JSONObject data) {
        if (endpoint == null || terminal) return;
        client.sendPayload(endpoint, Payload.fromBytes(data.toString().getBytes(StandardCharsets.UTF_8)))
            .addOnFailureListener(e -> finishWith("Connection failed while sending a response.", false));
    }
    private void sendKind(String kind) {
        try { JSONObject obj = new JSONObject(); obj.put("kind", kind); send(obj); }
        catch (Exception e) { finishWith("Transfer response failed.", false); }
    }
    private final PayloadCallback payloads = new PayloadCallback() {
        @Override public void onPayloadReceived(String id, Payload payload) {
            if (terminal || offer == null) return;
            refreshTimeout();
            if (payload.getType() == Payload.Type.BYTES) {
                try {
                    byte[] bytes = payload.asBytes(); if (bytes == null || bytes.length > 8192) { finishWith("Invalid transfer message.", false); return; }
                    JSONObject m = new JSONObject(new String(bytes, StandardCharsets.UTF_8)); String kind = m.optString("kind");
                    if (kind.equals("proof") && !verified) {
                        byte[] proof = Base64.decode(m.getString("proof"), Base64.DEFAULT);
                        if (!SessionProof.verify(offer.secret, offer.id, digits, "sender", proof)) { finishWith("Security verification failed. No file received.", false); return; }
                        Diagnostics.event(TransferService.this, "Receiver: NFC session verified."); verified = true; update("Waiting for files…", -1);
                    } else if (!verified) finishWith("Unverified sender.", false);
                    else if (kind.equals("file") && incoming == null && !saving && !expecting && received < offer.count) {
                        filename = safeName(m.optString("name", "file")); mime = m.optString("mime", "application/octet-stream");
                        if (mime.length() > 128 || !mime.contains("/")) mime = "application/octet-stream";
                        Diagnostics.event(TransferService.this, "Receiver: file offer received."); expecting = true; lastPercent = -1; sendKind("ready"); update("Receiving file " + (received + 1) + " of " + offer.count, 0);
                    } else if (kind.equals("done") && received == offer.count && !saving) finishWith("All " + received + (received == 1 ? " file saved." : " files saved."), true);
                } catch (Exception e) { finishWith("Invalid or unverifiable transfer message.", false); }
            } else if (payload.getType() == Payload.Type.FILE) {
                if (verified && expecting && incoming == null && !saving) incoming = payload; else client.cancelPayload(payload.getId());
            }
        }
        @Override public void onPayloadTransferUpdate(String id, PayloadTransferUpdate u) {
            if (terminal || incoming == null || u.getPayloadId() != incoming.getId()) return;
            refreshTimeout();
            if (u.getStatus() == PayloadTransferUpdate.Status.SUCCESS) {
                Diagnostics.event(TransferService.this, "Receiver: file payload received; saving."); Payload file = incoming; incoming = null; expecting = false; saving = true; update("Saving file " + (received + 1) + " of " + offer.count + "…", -1); save(file, filename, mime);
            } else if (u.getStatus() == PayloadTransferUpdate.Status.FAILURE || u.getStatus() == PayloadTransferUpdate.Status.CANCELED) {
                finishWith("File transfer failed or was cancelled.", false);
            } else if (u.getTotalBytes() > 0) {
                int percent = Progress.percent(u.getBytesTransferred(), u.getTotalBytes());
                if (percent != lastPercent) { lastPercent = percent; update("Receiving file " + (received + 1) + " of " + offer.count, percent); }
            }
        }
    };
    private static String safeName(String s) {
        if (s == null) return "file";
        s = s.replaceAll("[\\\\/\\p{Cntrl}]", "_").trim();
        if (s.length() > 100) s = s.substring(s.length() - 100);
        return s.isEmpty() || s.equals(".") || s.equals("..") ? "file" : s;
    }
    private void save(Payload payload, String name, String type) {
        new Thread(() -> {
            Uri dest = null;
            try {
                ParcelFileDescriptor fd = payload.asFile().asParcelFileDescriptor(); if (fd == null) throw new IllegalStateException("File unavailable");
                boolean image = type.startsWith("image/");
                ContentValues values = new ContentValues(); values.put(MediaStore.MediaColumns.DISPLAY_NAME, name); values.put(MediaStore.MediaColumns.MIME_TYPE, type);
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, image ? "Pictures/MIdrop" : "Download/MIdrop"); values.put(MediaStore.MediaColumns.IS_PENDING, 1);
                dest = getContentResolver().insert(image ? MediaStore.Images.Media.EXTERNAL_CONTENT_URI : MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (dest == null) throw new IllegalStateException("Couldn't create file");
                try (ParcelFileDescriptor owned = fd; InputStream in = new FileInputStream(owned.getFileDescriptor()); OutputStream out = getContentResolver().openOutputStream(dest)) {
                    if (out == null) throw new IllegalStateException("Couldn't write file");
                    byte[] b = new byte[65536]; int n; while ((n = in.read(b)) != -1) { if (terminal) throw new IllegalStateException("Transfer cancelled"); out.write(b, 0, n); }
                }
                if (terminal) throw new IllegalStateException("Transfer cancelled");
                values.clear(); values.put(MediaStore.MediaColumns.IS_PENDING, 0); getContentResolver().update(dest, values, null, null);
                handler.post(() -> { if (terminal) return; saving = false; received++; refreshTimeout(); update("Saved " + received + " of " + offer.count, -1); sendKind("saved"); });
            } catch (Exception e) {
                if (dest != null) getContentResolver().delete(dest, null, null);
                handler.post(() -> finishWith("Couldn't save the file.", false));
            }
        }, "MIdrop-save").start();
    }
    private Notification progressNotification(String text, int percent) {
        Intent open = new Intent(this, SharingActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent details = PendingIntent.getActivity(this, 1, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent cancel = new Intent(this, TransferService.class).setAction(CANCEL);
        PendingIntent stop = PendingIntent.getService(this, 2, cancel, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(progressIcon(percent)).setContentTitle("MIdrop · receiving")
                .setContentText(text).setContentIntent(details).setOnlyAlertOnce(true).setOngoing(true)
                .setCategory(Notification.CATEGORY_PROGRESS).setProgress(100, Math.max(0, percent), percent < 0)
                .addAction(new Notification.Action.Builder(null, "Cancel", stop).build());
        if (Build.VERSION.SDK_INT >= 36) {
            Notification.ProgressStyle style = new Notification.ProgressStyle();
            if (percent < 0) style.setProgressIndeterminate(true); else style.setProgress(percent);
            b.setStyle(style);
            Bundle extras = new Bundle(); extras.putBoolean("android.requestPromotedOngoing", true);
            extras.putCharSequence("android.shortCriticalText", percent < 0 ? "Sharing" : percent + "%"); b.addExtras(extras);
        }
        return b.build();
    }
    private Icon progressIcon(int percent) {
        Bitmap bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888); Canvas c = new Canvas(bitmap);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(9); p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(0x66FFFFFF); c.drawCircle(48, 48, 35, p); p.setColor(Color.WHITE);
        c.drawArc(new RectF(13,13,83,83), -90, percent < 0 ? 85 : Math.max(1, percent) * 3.6f, false, p);
        p.setStyle(Paint.Style.FILL); c.drawCircle(48, 48, 10, p);
        return Icon.createWithBitmap(bitmap);
    }
    private void update(String text, int percent) {
        if (!terminal) getSystemService(NotificationManager.class).notify(ReceiverService.NOTIFICATION_ID, progressNotification(text, percent));
    }
    private void finishWith(String text, boolean success) {
        if (terminal) return; Diagnostics.event(this, "Receiver: " + text); terminal = true; handler.removeCallbacksAndMessages(null);
        client.stopDiscovery(); client.stopAllEndpoints();
        stopForeground(STOP_FOREGROUND_REMOVE);
        Intent open = new Intent(this, SharingActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent details = PendingIntent.getActivity(this, 3, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CHANNEL).setSmallIcon(success ? android.R.drawable.stat_sys_download_done : android.R.drawable.stat_notify_error)
                .setContentTitle(success ? "MIdrop · complete" : "MIdrop · transfer stopped")
                .setContentText(text).setContentIntent(details).setAutoCancel(true).build();
        getSystemService(NotificationManager.class).notify(ReceiverService.NOTIFICATION_ID, n); stopSelf();
    }
    @Override public void onDestroy() { terminal = true; handler.removeCallbacksAndMessages(null); client.stopDiscovery(); client.stopAllEndpoints(); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
