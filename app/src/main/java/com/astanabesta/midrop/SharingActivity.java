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
import android.os.Handler;
import android.os.Looper;
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
import android.widget.Toast;
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
    private boolean pendingPhotoSend;
    private String endpoint, imageMime = "image/jpeg";
    private boolean sending, allowIncoming;
    private Payload incoming;
    private ParcelFileDescriptor outgoing;
    private long outgoingId = -1;
    private int pendingAction;
    private boolean radioGate, changedBt, changedWifi, radiosDone, btWasOn, wifiWasOn;
    private final Handler radioTimer = new Handler(Looper.getMainLooper());
    private final Runnable radioPoll = this::pollRadios;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        client = Nearby.getConnectionsClient(this);
        HyperIslandDecorator.refresh(this, null);
        Ui.setupWindow(this);
        ScrollView page = new ScrollView(this); page.setFillViewport(true); page.setBackgroundColor(Ui.back(this));
        LinearLayout root = Ui.column(this); root.setPadding(Ui.dp(this,24),Ui.dp(this,22),Ui.dp(this,24),Ui.dp(this,28));

        LinearLayout header = new LinearLayout(this); header.setOrientation(LinearLayout.HORIZONTAL); header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(Ui.displayText(this,"MIdrop",20,true));
        header.addView(new TextView(this), new LinearLayout.LayoutParams(0,-2,1f));
        ImageView bell = new ImageView(this); bell.setImageResource(R.drawable.ic_bell); bell.setColorFilter(Ui.ink(this));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(Ui.dp(this,24),Ui.dp(this,24)); blp.rightMargin = -Ui.dp(this,4);
        header.addView(bell, blp);
        bell.setOnClickListener(v -> openMenu());
        root.addView(header);
        Ui.space(this,root,22);

        root.addView(Ui.displayText(this,"DROP IT.",34,true));
        Ui.space(this,root,6);
        root.addView(Ui.bodyText(this,"Tap to share.",15,400,Ui.muted(this)));
        Ui.space(this,root,18);

        LinearLayout readout = new LinearLayout(this); readout.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable rdBg = Ui.shape(Ui.card(this), Ui.dp(this,Ui.R_CARD)); rdBg.setStroke(1,Ui.line(this));
        readout.setBackground(rdBg); readout.setPadding(Ui.dp(this,14),Ui.dp(this,6),Ui.dp(this,14),Ui.dp(this,6));
        nfcVal = readoutRow(readout, R.drawable.ic_nfc_ring, "NFC");
        btVal = readoutRow(readout, R.drawable.ic_bluetooth, "BLUETOOTH");
        wifiVal = readoutRow(readout, R.drawable.ic_wifi, "WIFI");
        root.addView(readout, new LinearLayout.LayoutParams(-1,LinearLayout.LayoutParams.WRAP_CONTENT));
        Ui.space(this,root,18);

        root.addView(Ui.button(this,"Share by tapping",true, this::openTapPicker), new LinearLayout.LayoutParams(-1,Ui.dp(this,52)));
        Ui.space(this,root,8);
        root.addView(Ui.bodyText(this,"Touch the backs of the phones together. That\u2019s the whole setup.",12,400,Ui.muted(this)));
        Ui.space(this,root,16);

        LinearLayout duo = new LinearLayout(this); duo.setOrientation(LinearLayout.HORIZONTAL);
        Button recv = Ui.button(this,"Receive a photo",false, () -> withPermissions(1));
        Button find = Ui.button(this,"Find devices and send",false, () -> { if (photo == null) { pendingPhotoSend = true; openPhotoPicker(); } else withPermissions(2); });
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(0,Ui.dp(this,50),1f); dl.rightMargin = Ui.dp(this,8);
        duo.addView(recv, dl);
        duo.addView(find, new LinearLayout.LayoutParams(0,Ui.dp(this,50),1f));
        root.addView(duo);
        Ui.space(this,root,8);
        chosen = Ui.bodyText(this,"",12,400,Ui.muted(this));
        root.addView(chosen);
        Ui.space(this,root,6);

        root.addView(rowcard(R.drawable.ic_zap, "NFC handshake test", "Check this device can share", () -> startActivity(new Intent(this, MainActivity.class))));
        Ui.space(this,root,8);
        root.addView(rowcard(R.drawable.ic_share, "iPhone NFC experiment", "Probe without sending files", () -> startActivity(new Intent(this, IphoneProbeActivity.class))));
        Ui.space(this,root,8);
        root.addView(rowcard(R.drawable.ic_radio, "iPhone radio research", "Observe nearby signals, read-only", () -> startActivity(new Intent(this, IphoneRadioTestActivity.class))));
        Ui.space(this,root,20);

        status = Ui.bodyText(this,"●  Set up receiving to get started",12,600,Ui.accent(this));
        root.addView(status);
        Ui.space(this,root,8);
        root.addView(Ui.sectionLabel(this,"NEARBY DEVICES"));
        Ui.space(this,root,6);
        peers = new LinearLayout(this); peers.setOrientation(LinearLayout.VERTICAL);
        root.addView(peers);
        Ui.space(this,root,12);
        root.addView(Ui.button(this,"Stop / start over",false,this::reset), new LinearLayout.LayoutParams(-1,Ui.dp(this,44)));
        page.addView(root); setContentView(page);
        readSharedIntent(getIntent()); updatePhoto();
    }

    private TextView readoutRow(LinearLayout parent, int icon, String label) {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0,Ui.dp(this,7),0,Ui.dp(this,7));
        ImageView iv = new ImageView(this); iv.setImageResource(icon); iv.setColorFilter(Ui.muted(this));
        row.addView(iv, new LinearLayout.LayoutParams(Ui.dp(this,15),Ui.dp(this,15)));
        TextView l = Ui.bodyText(this,label,10,700,Ui.muted(this)); l.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2,-2); lp.leftMargin = Ui.dp(this,10);
        row.addView(l, lp);
        View leader = new View(this);
        leader.setBackground(Ui.shape((Ui.line(this) & 0x00FFFFFF) | 0x40000000, 0));
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(0,Ui.dp(this,1),1);
        ll.leftMargin = Ui.dp(this,8); ll.rightMargin = Ui.dp(this,8);
        row.addView(leader, ll);
        TextView v = Ui.bodyText(this,"\u2014",10,700,Ui.muted(this));
        row.addView(v);
        parent.addView(row);
        return v;
    }
    private LinearLayout rowcard(int icon, String title, String sub, Runnable action) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.HORIZONTAL); card.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = Ui.shape(Ui.card(this), Ui.dp(this,Ui.R_CARD)); bg.setStroke(1,Ui.line(this));
        card.setBackground(bg); card.setPadding(Ui.dp(this,14),Ui.dp(this,12),Ui.dp(this,14),Ui.dp(this,12));
        card.setClickable(true); card.setFocusable(true); card.setOnClickListener(v -> action.run());
        ImageView iv = new ImageView(this); iv.setImageResource(icon); iv.setColorFilter(Ui.ink(this));
        card.addView(iv, new LinearLayout.LayoutParams(Ui.dp(this,20),Ui.dp(this,20)));
        LinearLayout texts = new LinearLayout(this); texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(Ui.bodyText(this,title,14,600,Ui.ink(this)));
        texts.addView(Ui.bodyText(this,sub,12,400,Ui.muted(this)));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0,-2,1f); tp.leftMargin = Ui.dp(this,12);
        card.addView(texts, tp);
        ImageView chev = new ImageView(this); chev.setImageResource(R.drawable.ic_chevron_right); chev.setColorFilter(Ui.muted(this));
        card.addView(chev, new LinearLayout.LayoutParams(Ui.dp(this,18),Ui.dp(this,18)));
        return card;
    }
    private void openPhotoPicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("image/*"); i.addCategory(Intent.CATEGORY_OPENABLE);
        try { startActivityForResult(i, PICK_PHOTO); } catch (Exception e) { show("Could not open the photo picker."); }
    }
    private void openTapPicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*"); i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try { startActivityForResult(i, PICK_TAP_FILES); } catch (Exception e) { show("Could not open the file picker."); }
    }
    private void checkIslandAccess() {
        show("Checking Xiaomi Super Island access\u2026");
        HyperIslandDecorator.refresh(this, () -> new AlertDialog.Builder(this).setTitle("Super Island access")
            .setMessage(HyperIslandDecorator.available()
                ? "Xiaomi reports Focus Notification access for MIdrop. Island display and supported templates still need a device test."
                : "Xiaomi did not report Focus Notification access, or this device could not check it. The normal Android notification remains available. HyperNotification cannot grant this access.")
            .setPositiveButton("OK", null).show());
    }
    private void quickShareLinkCheck() {
        try {
            startForegroundService(new Intent(this, LinkProbeService.class));
            new AlertDialog.Builder(this).setTitle("Link check running")
                .setMessage("For the next 60 seconds, open Quick Share as you normally do to send to the iPhone. No file needs to be sent. Then return to MIdrop and copy connection diagnostics. The check records only broad interface categories; it cannot prove access to the Apple link.")
                .setPositiveButton("OK", null).show();
        } catch (RuntimeException e) {
            Diagnostics.event(this, "iPhone link check: could not start (" + e.getClass().getSimpleName() + ").");
            new AlertDialog.Builder(this).setMessage("Could not start the link check. See connection diagnostics.").setPositiveButton("OK", null).show();
        }
    }
    private void openMenu() {
        String[] items = { "Set up background receiving", "View connection diagnostics", "Check Super Island access", "Run Super Island test", "Check Quick Share link (experimental)" };
        new AlertDialog.Builder(this).setTitle("MIdrop")
            .setItems(items, (d, which) -> {
                switch (which) {
                    case 0: withPermissions(3); break;
                    case 1: showDiagnostics(); break;
                    case 2: checkIslandAccess(); break;
                    case 3: runIslandTest(); break;
                    default: quickShareLinkCheck(); break;
                }
            }).show();
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
        if (request == PICK_PHOTO && result == RESULT_OK && data != null) {
            photo = data.getData(); updatePhoto();
            if (pendingPhotoSend) { pendingPhotoSend = false; withPermissions(2); }
        }
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
    private void updatePhoto() {
        if (photo == null) { chosen.setText(""); chosen.setVisibility(View.GONE); }
        else { chosen.setText("Photo ready: " + displayName(photo)); chosen.setVisibility(View.VISIBLE); }
    }
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
        pendingAction = action;
        if (Radios.bluetoothOn(this) && Radios.wifiOn(this)) { beginAction(action); return; }
        btWasOn = Radios.bluetoothOn(this); wifiWasOn = Radios.wifiOn(this);
        if (!btWasOn && Radios.tryBluetoothOn(this)) changedBt = true;
        if (!Radios.bluetoothOn(this)) Radios.openBluetooth(this);
        if (!Radios.wifiOn(this)) Radios.openWifi(this);
        radioGate = true; show("Turn on Bluetooth and Wi-Fi\u2026 the share starts as soon as they are on.");
        radioTimer.post(radioPoll);
    }
    private void beginAction(int action) {
        resetConnections(); sending = action == 2; peers.removeAllViews();
        if (sending) {
            show("Searching for nearby receivers\u2026");
            client.startDiscovery(SERVICE, discovery, new DiscoveryOptions.Builder().setStrategy(STRATEGY).build()).addOnFailureListener(e -> show("Search failed: " + e.getMessage()));
        } else {
            show("Ready to receive. Keep this screen open.");
            client.startAdvertising(Build.MODEL, SERVICE, lifecycle, new AdvertisingOptions.Builder().setStrategy(STRATEGY).build()).addOnFailureListener(e -> show("Receive mode failed: " + e.getMessage()));
        }
    }
    private void pollRadios() {
        if (isFinishing() || !radioGate) return;
        if (Radios.bluetoothOn(this) && Radios.wifiOn(this)) { radioGate = false; refreshReadout(); beginAction(pendingAction); return; }
        radioTimer.postDelayed(radioPoll, 800);
    }
    private void restoreRadios() {
        if (radiosDone) return; radiosDone = true;
        radioGate = false; radioTimer.removeCallbacksAndMessages(null);
        if (changedBt && !Radios.tryBluetoothOff(this) && Radios.bluetoothOn(this))
            Toast.makeText(this, "Bluetooth left on — the system keeps apps from switching it off.", Toast.LENGTH_SHORT).show();
        if (changedWifi && Radios.wifiOn(this))
            Toast.makeText(this, "Wi-Fi left on — the system keeps apps from switching it off.", Toast.LENGTH_SHORT).show();
        changedBt = changedWifi = false;
        refreshReadout();
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
        @Override public void onDisconnected(String id) { endpoint = null; allowIncoming = false; closeOutgoing(); show("Disconnected."); restoreRadios(); }
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
    private void reset() { radioGate = false; radioTimer.removeCallbacksAndMessages(null); resetConnections(); peers.removeAllViews(); show("Ready."); }
    @Override protected void onDestroy() { resetConnections(); restoreRadios(); super.onDestroy(); }
}
