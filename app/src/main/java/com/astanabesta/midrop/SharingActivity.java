package com.astanabesta.midrop;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.*;
import org.json.JSONObject;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/**
 * Home (Signal design system). Foreground manual photo transfer + entry points for the
 * background tap flow; all state colors and type come from Ui / resources.
 */
public class SharingActivity extends Activity {
    private static final String SERVICE = "com.astanabesta.midrop.share.v1";
    private static final Strategy STRATEGY = Strategy.P2P_POINT_TO_POINT;
    private static final int PICK_PHOTO = 10, PICK_TAP_FILES = 20;
    private ConnectionsClient client;
    private LinearLayout peers;
    private TextView status, chosen, nfcVal, btVal, wifiVal;
    private Uri photo;
    private String endpoint, imageMime = "image/jpeg";
    private boolean sending, allowIncoming;
    private Payload incoming;
    private ParcelFileDescriptor outgoing;
    private long outgoingId = -1;
    private int pendingAction;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        client = Nearby.getConnectionsClient(this);
        HyperIslandDecorator.refresh(this, null);
        Ui.setupWindow(this);
        ScrollView page = new ScrollView(this); page.setFillViewport(true); page.setBackgroundColor(Ui.back(this));
        LinearLayout root = Ui.column(this); root.setPadding(Ui.dp(this,24),Ui.dp(this,22),Ui.dp(this,24),Ui.dp(this,28));

        root.addView(Ui.displayText(this,"MIdrop",20,false));
        Ui.space(this,root,18);

        LinearLayout hero = new LinearLayout(this); hero.setOrientation(LinearLayout.VERTICAL); hero.setGravity(Gravity.CENTER);
        hero.setBackground(Ui.shape(Ui.card(this), Ui.dp(this,Ui.R_HERO)));
        hero.setPadding(0,Ui.dp(this,16),0,Ui.dp(this,16));
        ImageView ring = new ImageView(this); ring.setImageResource(R.drawable.ic_nfc_ring); ring.setColorFilter(Ui.accent(this));
        hero.addView(ring, new LinearLayout.LayoutParams(Ui.dp(this,76),Ui.dp(this,76)));
        Ui.space(this,hero,10);
        hero.addView(Ui.centered(this,"Bring phones together",13,Ui.ink(this),600));
        root.addView(hero, new LinearLayout.LayoutParams(-1,Ui.dp(this,150)));
        Ui.space(this,root,14);

        status = Ui.bodyText(this,"●  Set up receiving to get started",12,600,Ui.accent(this));
        root.addView(status);
        Ui.space(this,root,10);
        chosen = Ui.bodyText(this,"No photo selected",12,400,Ui.muted(this));
        root.addView(chosen);
        Ui.space(this,root,14);

        button(root,"Set up background receiving",true, () -> withPermissions(3));
        Ui.space(this,root,16);

        LinearLayout readout = new LinearLayout(this); readout.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable rdBg = Ui.shape(Ui.card(this), Ui.dp(this,Ui.R_CARD)); rdBg.setStroke(1,Ui.line(this));
        readout.setBackground(rdBg); readout.setPadding(Ui.dp(this,14),Ui.dp(this,8),Ui.dp(this,14),Ui.dp(this,8));
        nfcVal = readoutRow(readout,"NFC");
        btVal = readoutRow(readout,"BLUETOOTH");
        wifiVal = readoutRow(readout,"WIFI");
        root.addView(readout, new LinearLayout.LayoutParams(-1,LinearLayout.LayoutParams.WRAP_CONTENT));
        Ui.space(this,root,16);

        root.addView(Ui.sectionLabel(this,"SEND FROM ANY APP"));
        Ui.space(this,root,7);
        root.addView(Ui.bodyText(this,"Choose files in Gallery or Files, then tap Share \u2192 MIdrop. Bring the phones together.",13,400,Ui.muted(this)));
        Ui.space(this,root,10);
        button(root,"Share by tapping",false, () -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*");
            i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            try { startActivityForResult(i, PICK_TAP_FILES); } catch (Exception e) { show("Could not open the file picker."); }
        });
        Ui.space(this,root,16);

        root.addView(Ui.sectionLabel(this,"MANUAL SHARING"));
        Ui.space(this,root,7);
        button(root,"Choose photo",false, () -> { Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("image/*"); i.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(i, PICK_PHOTO); });
        button(root,"Receive a photo",false, () -> withPermissions(1));
        button(root,"Find devices and send",false, () -> { if (photo == null) { show("Choose a photo first."); return; } withPermissions(2); });
        button(root,"Stop / start over",false, this::reset);
        Ui.space(this,root,16);

        root.addView(Ui.sectionLabel(this,"EXPERIMENTAL TOOLS"));
        Ui.space(this,root,7);
        button(root,"Check Super Island access",false, () -> {
            show("Checking Xiaomi Super Island access\u2026");
            HyperIslandDecorator.refresh(this, () -> new AlertDialog.Builder(this).setTitle("Super Island access")
                .setMessage(HyperIslandDecorator.available()
                    ? "Xiaomi reports Focus Notification access for MIdrop. Island display and supported templates still need a device test."
                    : "Xiaomi did not report Focus Notification access, or this device could not check it. The normal Android notification remains available. HyperNotification cannot grant this access.")
                .setPositiveButton("OK", null).show());
        });
        button(root,"Run Super Island test",false, this::runIslandTest);
        button(root,"View connection diagnostics",false, this::showDiagnostics);
        button(root,"Check Quick Share link (experimental)",false, () -> {
            try {
                startForegroundService(new Intent(this, LinkProbeService.class));
                new AlertDialog.Builder(this).setTitle("Link check running")
                    .setMessage("For the next 60 seconds, open Quick Share as you normally do to send to the iPhone. No file needs to be sent. Then return to MIdrop and copy connection diagnostics. The check records only broad interface categories; it cannot prove access to the Apple link.")
                    .setPositiveButton("OK", null).show();
            } catch (RuntimeException e) {
                Diagnostics.event(this, "iPhone link check: could not start (" + e.getClass().getSimpleName() + ").");
                new AlertDialog.Builder(this).setMessage("Could not start the link check. See connection diagnostics.").setPositiveButton("OK", null).show();
            }
        });
        button(root,"Test iPhone radio (experimental)",false, () -> startActivity(new Intent(this, IphoneRadioTestActivity.class)));
        button(root,"Test iPhone NFC (experimental)",false, () -> startActivity(new Intent(this, IphoneProbeActivity.class)));
        button(root,"Open experimental NFC test",false, () -> startActivity(new Intent(this, MainActivity.class)));
        Ui.space(this,root,18);

        root.addView(Ui.bodyText(this,"Nearby devices",13,600,Ui.ink(this)));
        Ui.space(this,root,8);
        peers = new LinearLayout(this); peers.setOrientation(LinearLayout.VERTICAL);
        root.addView(peers);
        page.addView(root); setContentView(page);
        readSharedIntent(getIntent()); updatePhoto();
    }

    private TextView readoutRow(LinearLayout parent, String label) {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0,Ui.dp(this,5),0,Ui.dp(this,5));
        TextView l = Ui.bodyText(this,label,10,700,Ui.muted(this)); l.setLetterSpacing(0.08f);
        row.addView(l);
        int line = Ui.line(this);
        View leader = new View(this);
        leader.setBackground(Ui.shape((line & 0x00FFFFFF) | 0x40000000, 0));
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(0,Ui.dp(this,1),1);
        ll.leftMargin = Ui.dp(this,8); ll.rightMargin = Ui.dp(this,8);
        row.addView(leader, ll);
        TextView v = Ui.bodyText(this,"\u2014",10,700,Ui.muted(this));
        row.addView(v);
        parent.addView(row);
        return v;
    }
    private void refreshReadout() {
        try {
            android.nfc.NfcAdapter n = android.nfc.NfcAdapter.getDefaultAdapter(this);
            setReadout(nfcVal, n != null && n.isEnabled());
        } catch (Exception e) { setReadout(nfcVal, false); }
        try {
            android.bluetooth.BluetoothAdapter bt = android.bluetooth.BluetoothAdapter.getDefaultAdapter();
            setReadout(btVal, bt != null && bt.getState() == android.bluetooth.BluetoothAdapter.STATE_ON);
        } catch (Exception e) { setReadout(btVal, false); }
        try {
            android.net.wifi.WifiManager w = (android.net.wifi.WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            setReadout(wifiVal, w != null && w.isWifiEnabled());
        } catch (Exception e) { setReadout(wifiVal, false); }
    }
    private void setReadout(TextView v, boolean on) {
        v.setText(on ? "ON" : "OFF");
        v.setTextColor(on ? Ui.accent(this) : Ui.muted(this));
    }
    @Override protected void onResume() { super.onResume(); refreshReadout(); }

    private void button(LinearLayout root, String label, boolean primary, Runnable action) {
        Button b = Ui.button(this, label, primary, action);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, Ui.dp(this,50));
        p.bottomMargin = Ui.dp(this,8);
        root.addView(b,p);
    }
    private static int islandTestGeneration;
    private void runIslandTest() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            new AlertDialog.Builder(this).setMessage("Enable MIdrop notifications in Android settings before the test.").setPositiveButton("OK", null).show(); return;
        }
        HyperIslandDecorator.refresh(this, () -> {
            if (!HyperIslandDecorator.available()) {
                new AlertDialog.Builder(this).setMessage("Xiaomi did not report Focus Notification access. Check diagnostics; this test would only show a normal notification.").setPositiveButton("OK", null).show(); return;
            }
            android.content.Context app = getApplicationContext();
            android.app.NotificationManager nm = app.getSystemService(android.app.NotificationManager.class);
            String channel = "island_test"; int id = 9412; int generation = ++islandTestGeneration;
            nm.createNotificationChannel(new android.app.NotificationChannel(channel, "Super Island test", android.app.NotificationManager.IMPORTANCE_DEFAULT));
            android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
            Diagnostics.event(app, "Super Island: 30-second test started.");
            for (int i = 0; i <= 10; i++) {
                final int progress = i * 10;
                h.postDelayed(() -> {
                    if (generation != islandTestGeneration) return;
                    android.app.Notification.Builder b = new android.app.Notification.Builder(app, channel)
                        .setSmallIcon(R.drawable.ic_stat_midrop).setContentTitle("MIdrop \u00b7 island test")
                        .setContentText("Test progress " + progress + "%")
                        .setOnlyAlertOnce(true).setOngoing(true).setProgress(100, progress, false)
                        .addExtras(HyperIslandDecorator.extras(app, "Test progress", progress));
                    nm.notify(id, b.build());
                }, i * 3000L);
            }
            h.postDelayed(() -> { if (generation == islandTestGeneration) { nm.cancel(id); Diagnostics.event(app, "Super Island: test ended."); } }, 33000L);
            new AlertDialog.Builder(this).setTitle("Super Island test running")
                .setMessage("Return to your home screen and watch the camera area for 30 seconds. This is a simulated progress notification; no files are transferred. Then open connection diagnostics and copy the log.")
                .setPositiveButton("OK", null).show();
        });
    }
    private void showDiagnostics() {
        String text = EventLog.read(this);
        new AlertDialog.Builder(this).setTitle("Connection diagnostics")
                .setMessage("Local event log \u2014 no file names, addresses or keys. Times are shown on this phone.\n\n" + text)
                .setPositiveButton("Copy", (d,w) -> {
                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("MIdrop diagnostics", text));
                })
                .setNeutralButton("Clear", (d,w) -> EventLog.clear(this))
                .setNegativeButton("Close", null).show();
    }
    private void readSharedIntent(Intent intent) {
        if (Intent.ACTION_SEND.equals(intent.getAction()) && intent.getType() != null && intent.getType().startsWith("image/")) {
            Uri u = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (u != null) photo = u;
        }
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); readSharedIntent(intent); updatePhoto(); }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request,result,data);
        if (request == PICK_PHOTO && result == RESULT_OK && data != null) { photo = data.getData(); updatePhoto(); }
        if (request == PICK_TAP_FILES && result == RESULT_OK && data != null) {
            ArrayList<Uri> uris = new ArrayList<>();
            if (data.getClipData() != null) { for (int i = 0; i < data.getClipData().getItemCount(); i++) uris.add(data.getClipData().getItemAt(i).getUri()); }
            else if (data.getData() != null) uris.add(data.getData());
            if (uris.isEmpty() || uris.size() > 25) { show("Select between 1 and 25 files."); return; }
            Intent send = new Intent(this, TapActivity.class);
            if (uris.size() == 1) { send.setAction(Intent.ACTION_SEND); send.putExtra(Intent.EXTRA_STREAM, uris.get(0)); }
            else { send.setAction(Intent.ACTION_SEND_MULTIPLE); send.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris); }
            startActivity(send);
        }
    }
    private void updatePhoto() { chosen.setText(photo == null ? "No photo selected" : "Photo selected: " + displayName(photo)); }
    private String displayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) { if (c != null && c.moveToFirst()) return c.getString(0); }
        catch (Exception ignored) { }
        return "image";
    }
    private void withPermissions(int action) {
        ArrayList<String> p = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) { p.add(Manifest.permission.BLUETOOTH_SCAN); p.add(Manifest.permission.BLUETOOTH_CONNECT); p.add(Manifest.permission.BLUETOOTH_ADVERTISE); }
        if (Build.VERSION.SDK_INT <= 32) { p.add(Manifest.permission.ACCESS_FINE_LOCATION); p.add(Manifest.permission.READ_EXTERNAL_STORAGE); }
        if (Build.VERSION.SDK_INT >= 33) { p.add(Manifest.permission.NEARBY_WIFI_DEVICES); p.add(Manifest.permission.POST_NOTIFICATIONS); }
        ArrayList<String> missing = new ArrayList<>(); for (String s : p) if (checkSelfPermission(s) != PackageManager.PERMISSION_GRANTED) missing.add(s);
        if (!missing.isEmpty()) { pendingAction = action; requestPermissions(missing.toArray(new String[0]), 12); } else startAction(action);
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request,permissions,results);
        if (request == 12) { for (int r : results) if (r != PackageManager.PERMISSION_GRANTED) { show("Nearby permissions are required. Enable them in app settings."); return; } startAction(pendingAction); }
    }
    private void startAction(int action) {
        if (action == 3) { show("Setup complete. Keep NFC, Wi-Fi and Bluetooth enabled. Background tap requests will appear as notifications."); return; }
        resetConnections(); sending = action == 2; peers.removeAllViews();
        if (sending) {
            show("Searching for nearby receivers\u2026");
            client.startDiscovery(SERVICE, discovery, new DiscoveryOptions.Builder().setStrategy(STRATEGY).build()).addOnFailureListener(e -> show("Search failed: " + e.getMessage()));
        } else {
            show("Ready to receive. Keep this screen open.");
            client.startAdvertising(Build.MODEL, SERVICE, lifecycle, new AdvertisingOptions.Builder().setStrategy(STRATEGY).build()).addOnFailureListener(e -> show("Receive mode failed: " + e.getMessage()));
        }
    }
    private final EndpointDiscoveryCallback discovery = new EndpointDiscoveryCallback() {
        @Override public void onEndpointFound(String id, DiscoveredEndpointInfo info) {
            Button b = Ui.button(SharingActivity.this, "Connect: " + info.getEndpointName(), false, () -> { });
            b.setOnClickListener(v -> { b.setEnabled(false); show("Connecting\u2026"); client.requestConnection(Build.MODEL, id, lifecycle).addOnFailureListener(e -> show("Connection failed: " + e.getMessage())); });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, Ui.dp(SharingActivity.this,44)); p.bottomMargin = Ui.dp(SharingActivity.this,8);
            b.setTag(id); peers.addView(b, p);
        }
        @Override public void onEndpointLost(String id) { View v = peers.findViewWithTag(id); if (v != null) peers.removeView(v); }
    };
    private final ConnectionLifecycleCallback lifecycle = new ConnectionLifecycleCallback() {
        @Override public void onConnectionInitiated(String id, ConnectionInfo info) {
            new AlertDialog.Builder(SharingActivity.this).setTitle("Confirm connection")
                    .setMessage("Connect to " + info.getEndpointName() + "? Both phones must show the same code: " + info.getAuthenticationDigits())
                    .setPositiveButton("Codes match", (d,w) -> client.acceptConnection(id, payloads).addOnFailureListener(e -> show("Accept failed: " + e.getMessage())))
                    .setNegativeButton("Reject", (d,w) -> client.rejectConnection(id)).setOnCancelListener(d -> client.rejectConnection(id)).show();
        }
        @Override public void onConnectionResult(String id, ConnectionResolution result) {
            if (!result.getStatus().isSuccess()) { show("Connection rejected or failed: " + result.getStatus().getStatusCode()); return; }
            endpoint = id; client.stopDiscovery(); client.stopAdvertising(); show("Connected.");
            if (sending) offerPhoto();
        }
        @Override public void onDisconnected(String id) { endpoint = null; allowIncoming = false; closeOutgoing(); show("Disconnected."); }
    };
    private void offerPhoto() {
        try {
            String mime = getContentResolver().getType(photo);
            if (mime == null || !mime.startsWith("image/")) { show("Only images are supported."); return; }
            JSONObject offer = new JSONObject(); offer.put("kind","offer"); offer.put("name", displayName(photo)); offer.put("mime",mime);
            sendText(offer.toString()); show("Waiting for receiver to accept the photo\u2026");
        } catch (Exception e) { show("Could not prepare image: " + e.getMessage()); }
    }
    private void sendText(String message) { if (endpoint != null) client.sendPayload(endpoint, Payload.fromBytes(message.getBytes(StandardCharsets.UTF_8))).addOnFailureListener(e -> show("Message failed. ")); }
    private final PayloadCallback payloads = new PayloadCallback() {
        @Override public void onPayloadReceived(String id, Payload payload) {
            if (payload.getType() == Payload.Type.BYTES) {
                try {
                    byte[] data = payload.asBytes(); if (data == null || data.length > 8192) return;
                    JSONObject m = new JSONObject(new String(data, StandardCharsets.UTF_8)); String kind = m.optString("kind");
                    if (!sending && kind.equals("offer") && !allowIncoming) {
                        String mime = m.optString("mime", ""); if (!mime.startsWith("image/")) { show("Rejected non-image offer."); return; }
                        imageMime = mime;
                        String name = m.optString("name", "photo"); if (name.length() > 100) name = name.substring(0,100);
                        new AlertDialog.Builder(SharingActivity.this).setTitle("Receive photo?").setMessage(name)
                            .setPositiveButton("Accept", (d,w) -> { allowIncoming = true; sendText("{\"kind\":\"accept\"}"); show("Waiting for photo\u2026"); })
                            .setNegativeButton("Decline", (d,w) -> { sendText("{\"kind\":\"decline\"}"); show("Photo declined."); })
                            .setOnCancelListener(d -> { sendText("{\"kind\":\"decline\"}"); show("Photo declined."); }).show();
                    } else if (sending && kind.equals("accept")) sendPhoto();
                    else if (sending && kind.equals("decline")) show("Receiver declined the photo.");
                } catch (Exception e) { show("Invalid message received."); }
            } else if (payload.getType() == Payload.Type.FILE) {
                if (!sending && allowIncoming && incoming == null) incoming = payload;
                else client.cancelPayload(payload.getId());
            }
        }
        @Override public void onPayloadTransferUpdate(String id, PayloadTransferUpdate update) {
            if (update.getPayloadId() == outgoingId) {
                if (update.getStatus() == PayloadTransferUpdate.Status.SUCCESS) { closeOutgoing(); show("Photo sent. The receiver is saving it."); }
                else if (update.getStatus() == PayloadTransferUpdate.Status.FAILURE || update.getStatus() == PayloadTransferUpdate.Status.CANCELED) { closeOutgoing(); show("Photo transfer failed or was cancelled."); }
                else if (update.getTotalBytes() > 0) show("Sending: " + (100 * update.getBytesTransferred() / update.getTotalBytes()) + "%");
            }
            if (incoming != null && update.getPayloadId() == incoming.getId()) {
                if (update.getStatus() == PayloadTransferUpdate.Status.SUCCESS) { Payload done = incoming; incoming = null; allowIncoming = false; savePhoto(done); }
                else if (update.getStatus() == PayloadTransferUpdate.Status.FAILURE || update.getStatus() == PayloadTransferUpdate.Status.CANCELED) { incoming = null; allowIncoming = false; show("Photo transfer failed or was cancelled."); }
                else if (update.getTotalBytes() > 0) show("Receiving: " + (100 * update.getBytesTransferred() / update.getTotalBytes()) + "%");
            }
        }
    };
    private void sendPhoto() {
        if (outgoing != null) return;
        try {
            outgoing = getContentResolver().openFileDescriptor(photo, "r");
            if (outgoing == null) throw new IllegalStateException("Cannot open selected photo");
            Payload file = Payload.fromFile(outgoing); outgoingId = file.getId();
            client.sendPayload(endpoint,file).addOnFailureListener(e -> { closeOutgoing(); show("Sending failed. " + e.getMessage()); });
            show("Sending photo\u2026");
        } catch (Exception e) { closeOutgoing(); show("Cannot send photo: " + e.getMessage()); }
    }
    private void savePhoto(Payload payload) {
        String mime = imageMime;
        show("Saving received photo\u2026");
        new Thread(() -> {
            Uri dest = null;
            try {
                ParcelFileDescriptor fd = payload.asFile().asParcelFileDescriptor();
                if (fd == null) throw new IllegalStateException("Received file unavailable");
                ContentValues values = new ContentValues(); values.put(MediaStore.Images.Media.DISPLAY_NAME, "MIdrop_" + System.currentTimeMillis());
                values.put(MediaStore.Images.Media.MIME_TYPE, mime); values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MIdrop"); values.put(MediaStore.Images.Media.IS_PENDING, 1);
                dest = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (dest == null) throw new IllegalStateException("Cannot create image");
                try (ParcelFileDescriptor owned = fd; InputStream in = new FileInputStream(owned.getFileDescriptor()); OutputStream out = getContentResolver().openOutputStream(dest)) {
                    if (out == null) throw new IllegalStateException("Cannot write image"); byte[] buffer = new byte[65536]; int n; while ((n = in.read(buffer)) != -1) out.write(buffer,0,n);
                }
                values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0); getContentResolver().update(dest,values,null,null);
                runOnUiThread(() -> show("Saved to Pictures/MIdrop."));
            } catch (Exception e) { if (dest != null) getContentResolver().delete(dest,null,null); runOnUiThread(() -> show("Could not save photo: " + e.getMessage())); }
        }).start();
    }
    private void show(String text) { status.setText("\u25cf  " + text); }
    private void closeOutgoing() { if (outgoing != null) { try { outgoing.close(); } catch (Exception ignored) {} } outgoingId = -1; }
    private void resetConnections() { client.stopDiscovery(); client.stopAdvertising(); client.stopAllEndpoints(); endpoint = null; allowIncoming = false; incoming = null; closeOutgoing(); }
    private void reset() { resetConnections(); peers.removeAllViews(); show("Ready."); }
    @Override protected void onDestroy() { resetConnections(); super.onDestroy(); }
}
