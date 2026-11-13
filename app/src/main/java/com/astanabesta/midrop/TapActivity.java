package com.astanabesta.midrop;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
 * NFC bootstrap + authenticated Nearby transfer, while this transfer screen is visible.
 * Signal design: one lime signal that pulses while the device waits for the tap.
 */
public class TapActivity extends Activity {
    private static final String SERVICE = "com.astanabesta.midrop.tap.v1";
    private final ArrayList<Uri> files = new ArrayList<>();
    private ConnectionsClient client;
    private NfcAdapter nfc;
    private TextView status, tapSub;
    private TapOffer offer;
    private boolean sender, verified, requested, offerAccepted, saving, advertising, savedEarly;
    private String endpoint, digits, incomingName, incomingMime;
    private int currentIndex, receivedCount;
    private Payload incoming;
    private ParcelFileDescriptor outgoing;
    private long outgoingId = -1;
    private ValueAnimator pulse;
    private boolean radioGate, changedBt, changedWifi, radiosDone, btWasOn, wifiWasOn;
    private LinearLayout radioRow;
    private TextView btLabel, wifiLabel;
    private final Handler radioTimer = new Handler(Looper.getMainLooper());
    private final Runnable radioPoll = this::pollRadios;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        client = Nearby.getConnectionsClient(this); nfc = NfcAdapter.getDefaultAdapter(this);
        Ui.setupWindow(this);
        LinearLayout root = Ui.column(this); root.setPadding(Ui.dp(this,24),Ui.dp(this,24),Ui.dp(this,24),Ui.dp(this,24));

        root.addView(Ui.displayText(this,"TAP.",40,true));
        Ui.space(this,root,8);
        tapSub = Ui.bodyText(this,"Hold this phone to a nearby MIdrop device. Files move when the link lands.",14,400,Ui.muted(this));
        root.addView(tapSub);
        Ui.space(this,root,24);

        LinearLayout hero = new LinearLayout(this); hero.setGravity(Gravity.CENTER);
        hero.setBackground(Ui.shape(Ui.card(this), Ui.dp(this,Ui.R_HERO)));
        ImageView ring = new ImageView(this); ring.setImageResource(R.drawable.ic_nfc_ring); ring.setColorFilter(Ui.accent(this));
        hero.addView(ring, new LinearLayout.LayoutParams(Ui.dp(this,95),Ui.dp(this,95)));
        root.addView(hero, new LinearLayout.LayoutParams(-1,Ui.dp(this,220)));

        pulse = ValueAnimator.ofFloat(0.5f, 1f);
        pulse.setDuration(1300); pulse.setRepeatCount(ValueAnimator.INFINITE); pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.addUpdateListener(an -> ring.setImageAlpha((int)(128 + 127 * (Float)an.getAnimatedValue())));
        pulse.start();

        Ui.space(this,root,24);
        status = Ui.bodyText(this,"Getting ready\u2026",14,600,Ui.ink(this)); root.addView(status);
        Ui.space(this,root,24);
        Button stop = Ui.button(this,"Cancel",false,this::finish); root.addView(stop,new LinearLayout.LayoutParams(-1,Ui.dp(this,50)));
        setContentView(root);

        Intent intent = getIntent();
        if (ReceiverService.ACTION_ACCEPT.equals(intent.getAction())) {
            getSystemService(NotificationManager.class).cancel(ReceiverService.NOTIFICATION_ID);
            offer = TapOffer.parse(intent.getByteArrayExtra("offer"), System.currentTimeMillis());
            String stored = getSharedPreferences("tap", MODE_PRIVATE).getString("id", "");
            if (offer == null || !stored.equals(offer.publicName()) || !getSharedPreferences("tap", MODE_PRIVATE).getBoolean("pending", false)) { show("This request expired or was already declined."); return; }
            tapSub.setText("Hold near a friend\u2019s phone. The files follow.");
            getSharedPreferences("tap", MODE_PRIVATE).edit().putBoolean("pending", false).apply();
            sender = false; withPermissions();
        } else {
            sender = true; collect(intent);
            if (files.isEmpty() || files.size() > TapOffer.MAX_FILES) { show("Select between 1 and " + TapOffer.MAX_FILES + " files."); return; }
            offer = TapOffer.create(files.size(), System.currentTimeMillis()); withPermissions();
        }
    }
    private void collect(Intent intent) {
        if (Intent.ACTION_SEND.equals(intent.getAction())) { Uri u = intent.getParcelableExtra(Intent.EXTRA_STREAM); if (u != null) files.add(u); }
        else if (Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) { ArrayList<Uri> list = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM); if (list != null) for (Uri u : list) if (u != null) files.add(u); }
    }
    private void withPermissions() {
        ArrayList<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) { permissions.add(Manifest.permission.BLUETOOTH_SCAN); permissions.add(Manifest.permission.BLUETOOTH_CONNECT); permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE); }
        if (Build.VERSION.SDK_INT <= 32) { permissions.add(Manifest.permission.ACCESS_FINE_LOCATION); permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE); }
        if (Build.VERSION.SDK_INT >= 33) { permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES); permissions.add(Manifest.permission.POST_NOTIFICATIONS); }
        ArrayList<String> missing = new ArrayList<>(); for (String p : permissions) if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) missing.add(p);
        if (missing.isEmpty()) startSession(); else requestPermissions(missing.toArray(new String[0]), 21);
    }
    @Override public void onRequestPermissionsResult(int request, String[] names, int[] results) {
        super.onRequestPermissionsResult(request,names,results);
        if (request != 21) return; for (int result : results) if (result != PackageManager.PERMISSION_GRANTED) { show("Permissions are required. Open MIdrop to configure them."); return; } startSession();
    }
    private void startSession() {
        if (sender) {
            if (nfc == null || !nfc.isEnabled()) { show("Turn NFC on and retry the share."); return; }
        }
        btWasOn = Radios.bluetoothOn(this); wifiWasOn = Radios.wifiOn(this);
        if (!btWasOn && Radios.tryBluetoothOn(this)) changedBt = true;
        if (Radios.bluetoothOn(this) && Radios.wifiOn(this)) startNearby();
        else { radioGate = true; buildRadioRow(); radioTimer.post(radioPoll); }
    }
    private void startNearby() {
        if (sender) {
            Diagnostics.event(this, "Sender: starting nearby advertising.");
            client.startAdvertising(offer.publicName(), SERVICE, lifecycle, new AdvertisingOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build())
                .addOnSuccessListener(v -> { Diagnostics.event(this, "Sender: advertising ready."); advertising = true; show("Touch the backs of both unlocked phones together. Keep this screen open."); enableNfc(); })
                .addOnFailureListener(e -> { Diagnostics.error(this, "Sender: advertising failed", e); show("Could not start sharing. Check nearby permissions."); });
        } else {
            show("Connecting to sender\u2026 Keep this screen open.");
            client.startDiscovery(SERVICE, discovery, new DiscoveryOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build())
                .addOnFailureListener(e -> show("Could not find sender: " + e.getMessage()));
        }
    }
    private void buildRadioRow() {
        if (radioRow != null) return;
        radioRow = new LinearLayout(this);
        radioRow.addView(radioItem("Bluetooth", true, () -> { if (!btWasOn) changedBt = true; Radios.openBluetooth(this); }));
        radioRow.addView(radioItem("Wi-Fi", false, () -> { if (!wifiWasOn) changedWifi = true; Radios.openWifi(this); }));
        LinearLayout root = (LinearLayout) status.getParent();
        int index = 0; while (root.getChildAt(index) != status) index++;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(this, 12); lp.bottomMargin = Ui.dp(this, 12);
        root.addView(radioRow, index + 1, lp);
    }
    private LinearLayout radioItem(String what, boolean bt, Runnable turnOn) {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = Ui.bodyText(this, what + " off", 13, 500, Ui.muted(this));
        if (bt) btLabel = label; else wifiLabel = label;
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));
        Button b = Ui.button(this, "Turn on", true, turnOn);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, Ui.dp(this, 46)); lp.leftMargin = Ui.dp(this, 10);
        row.addView(b, lp);
        return row;
    }
    private void pollRadios() {
        if (isFinishing() || !radioGate) return;
        boolean bt = Radios.bluetoothOn(this), wifi = Radios.wifiOn(this);
        btLabel.setText(bt ? "Bluetooth on" : "Bluetooth turning on\u2026");
        wifiLabel.setText(wifi ? "Wi-Fi on" : "Wi-Fi turning on\u2026");
        if (bt && wifi) { radioGate = false; radioRow.setVisibility(View.GONE); startNearby(); return; }
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
    }
    private void enableNfc() { if (sender && advertising && nfc != null && offer != null && !requested) nfc.enableReaderMode(this, this::onTag, NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null); }
    private void onTag(Tag tag) {
        IsoDep iso = IsoDep.get(tag); if (iso == null) return;
        try {
            iso.connect(); if (!Protocol.isReply(iso.transceive(Protocol.select()))) throw new IllegalStateException("MIdrop receiver not found");
            byte[] reply = iso.transceive(offer.command());
            if (reply == null || reply.length != 2 || reply[0] != (byte)0x90 || reply[1] != 0) throw new IllegalStateException("Receiver unavailable or notifications disabled");
            runOnUiThread(() -> { Diagnostics.event(this, "Sender: NFC request delivered."); requested = true; if (nfc != null) nfc.disableReaderMode(this); show("Request sent. Waiting for receiver to accept\u2026"); });
        } catch (Exception e) { Diagnostics.error(this, "Sender: NFC handoff failed", e); runOnUiThread(() -> show("Tap failed: " + e.getMessage() + ". Check receiver setup and try again.")); }
        finally { try { iso.close(); } catch (Exception ignored) {} }
    }
    private final EndpointDiscoveryCallback discovery = new EndpointDiscoveryCallback() {
        @Override public void onEndpointFound(String id, DiscoveredEndpointInfo info) {
            if (!sender && !requested && offer != null && info.getEndpointName().equals(offer.publicName())) {
                requested = true; client.requestConnection("MIdrop receiver", id, lifecycle).addOnFailureListener(e -> { requested = false; show("Connection failed: " + e.getMessage()); });
            }
        }
        @Override public void onEndpointLost(String id) { }
    };
    private final ConnectionLifecycleCallback lifecycle = new ConnectionLifecycleCallback() {
        @Override public void onConnectionInitiated(String id, ConnectionInfo info) {
            if (offer == null || System.currentTimeMillis() - offer.created > TapOffer.TTL_MS || (sender && !requested)) { client.rejectConnection(id); return; }
            Diagnostics.event(TapActivity.this, "Sender: nearby connection initiated.");
            digits = info.getAuthenticationDigits();
            client.acceptConnection(id, payloads).addOnFailureListener(e -> show("Connection failed: " + e.getMessage()));
        }
        @Override public void onConnectionResult(String id, ConnectionResolution result) {
            if (!result.getStatus().isSuccess()) { Diagnostics.event(TapActivity.this, "Sender: connection failed (Nearby code " + result.getStatus().getStatusCode() + ")."); show("Connection rejected or failed."); return; }
            Diagnostics.event(TapActivity.this, "Sender: connection established.");
            endpoint = id; client.stopDiscovery(); client.stopAdvertising(); show("Verifying NFC session\u2026");
            try {
                JSONObject m = new JSONObject(); m.put("kind","proof");
                m.put("proof", Base64.encodeToString(SessionProof.calculate(offer.secret,offer.id,digits,sender ? "sender" : "receiver"),Base64.NO_WRAP)); send(m);
            } catch (Exception e) { fail("Could not verify connection."); }
        }
        @Override public void onDisconnected(String id) { Diagnostics.event(TapActivity.this, "Sender: nearby disconnected."); endpoint = null; closeOutgoing(); if (!isFinishing()) show("Disconnected. Retry the share if it did not finish."); }
    };
    private void send(JSONObject m) { if (endpoint != null) client.sendPayload(endpoint,Payload.fromBytes(m.toString().getBytes(StandardCharsets.UTF_8))).addOnFailureListener(e -> fail("Message failed.")); }
    private void simple(String kind) { try { JSONObject j = new JSONObject(); j.put("kind",kind); send(j); } catch (Exception e) { fail("Message failed."); } }
    private final PayloadCallback payloads = new PayloadCallback() {
        @Override public void onPayloadReceived(String id, Payload payload) {
            if (payload.getType() == Payload.Type.BYTES) {
                try {
                    byte[] data = payload.asBytes(); if (data == null || data.length > 8192) { fail("Invalid message."); return; }
                    JSONObject msg = new JSONObject(new String(data,StandardCharsets.UTF_8)); String kind = msg.optString("kind");
                    if (kind.equals("proof") && !verified) {
                        byte[] proof = Base64.decode(msg.getString("proof"),Base64.DEFAULT);
                        if (!SessionProof.verify(offer.secret,offer.id,digits,sender ? "receiver" : "sender",proof)) { fail("Security verification failed. No file sent."); return; }
                        Diagnostics.event(TapActivity.this, "Sender: NFC session verified."); verified = true; show("Verified. " + (sender ? "Preparing files\u2026" : "Waiting for files\u2026")); if (sender) sendManifest();
                    } else if (!verified) fail("Unverified sender.");
                    else if (!sender && kind.equals("file") && incoming == null && !saving && !offerAccepted && receivedCount < offer.count) {
                        incomingName = safeName(msg.optString("name", "file")); incomingMime = msg.optString("mime", "application/octet-stream");
                        if (incomingMime.length() > 128 || !incomingMime.contains("/")) incomingMime = "application/octet-stream";
                        offerAccepted = true; simple("ready"); show("Receiving " + (receivedCount + 1) + " of " + offer.count + "\u2026");
                    } else if (sender && kind.equals("ready")) sendFile();
                    else if (sender && kind.equals("saved")) { if (outgoing != null) savedEarly = true; else advanceFile(); }
                    else if (!sender && kind.equals("done") && receivedCount == offer.count) { show("All files saved."); restoreRadios(); }
                } catch (Exception e) { fail("Invalid or unverifiable message."); }
            } else if (payload.getType() == Payload.Type.FILE) {
                if (!sender && verified && offerAccepted && incoming == null && !saving) incoming = payload; else client.cancelPayload(payload.getId());
            }
        }
        @Override public void onPayloadTransferUpdate(String id, PayloadTransferUpdate update) {
            if (update.getPayloadId() == outgoingId) {
                if (update.getStatus() == PayloadTransferUpdate.Status.SUCCESS) { closeOutgoing(); if (savedEarly) { savedEarly = false; advanceFile(); } else show("Sent. Waiting for save confirmation\u2026"); }
                else if (update.getStatus() == PayloadTransferUpdate.Status.FAILURE || update.getStatus() == PayloadTransferUpdate.Status.CANCELED) fail("Transfer failed.");
                else if (update.getTotalBytes() > 0) show("Sending " + (currentIndex+1) + "/" + files.size() + ": " + 100 * update.getBytesTransferred() / update.getTotalBytes() + "%");
            }
            if (incoming != null && update.getPayloadId() == incoming.getId()) {
                if (update.getStatus() == PayloadTransferUpdate.Status.SUCCESS) { Payload p = incoming; incoming = null; offerAccepted = false; saving = true; saveFile(p,incomingName,incomingMime); }
                else if (update.getStatus() == PayloadTransferUpdate.Status.FAILURE || update.getStatus() == PayloadTransferUpdate.Status.CANCELED) fail("Transfer failed.");
            }
        }
    };
    private void advanceFile() { currentIndex++; if (currentIndex < files.size()) sendManifest(); else { simple("done"); show("All files sent and saved."); restoreRadios(); } }
    private void sendManifest() {
        if (!verified || currentIndex >= files.size()) return;
        Uri file = files.get(currentIndex);
        try { JSONObject m = new JSONObject(); m.put("kind","file"); m.put("name",safeName(name(file))); String mime = getContentResolver().getType(file); m.put("mime",mime == null ? "application/octet-stream" : mime); send(m); }
        catch (Exception e) { fail("Cannot prepare file."); }
    }
    private String name(Uri uri) {
        try (Cursor c = getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)) { if (c != null && c.moveToFirst()) return c.getString(0); } catch (Exception ignored) {} return "file";
    }
    static String safeName(String s) {
        if (s == null) return "file"; s = s.replaceAll("[\\\\/\\p{Cntrl}]", "_").trim(); if (s.length() > 100) s = s.substring(s.length()-100); return s.isEmpty() || s.equals(".") || s.equals("..") ? "file" : s;
    }
    private void sendFile() {
        if (outgoing != null || currentIndex >= files.size()) return;
        try { outgoing = getContentResolver().openFileDescriptor(files.get(currentIndex),"r"); if (outgoing == null) throw new IllegalStateException("Cannot open file"); Payload p = Payload.fromFile(outgoing); outgoingId = p.getId(); client.sendPayload(endpoint,p).addOnFailureListener(e -> fail("Cannot send file.")); }
        catch (Exception e) { fail("Cannot open selected file: " + e.getMessage()); }
    }
    private void saveFile(Payload payload, String name, String mime) {
        new Thread(() -> {
            Uri dest = null;
            try {
                ParcelFileDescriptor fd = payload.asFile().asParcelFileDescriptor(); if (fd == null) throw new IllegalStateException("File unavailable");
                boolean image = mime.startsWith("image/");
                ContentValues v = new ContentValues(); v.put(MediaStore.MediaColumns.DISPLAY_NAME,name); v.put(MediaStore.MediaColumns.MIME_TYPE,mime);
                v.put(MediaStore.MediaColumns.RELATIVE_PATH,image ? "Pictures/MIdrop" : "Download/MIdrop"); v.put(MediaStore.MediaColumns.IS_PENDING,1);
                dest = getContentResolver().insert(image ? MediaStore.Images.Media.EXTERNAL_CONTENT_URI : MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);
                if (dest == null) throw new IllegalStateException("Cannot create file");
                try (ParcelFileDescriptor owned = fd; InputStream in = new FileInputStream(owned.getFileDescriptor()); OutputStream out = getContentResolver().openOutputStream(dest)) {
                    if (out == null) throw new IllegalStateException("Cannot write file"); byte[] buffer = new byte[65536]; int n; while ((n = in.read(buffer)) != -1) out.write(buffer,0,n);
                }
                v.clear(); v.put(MediaStore.MediaColumns.IS_PENDING,0); getContentResolver().update(dest,v,null,null);
                runOnUiThread(() -> { saving = false; receivedCount++; show("Saved " + receivedCount + " of " + offer.count + "."); simple("saved"); });
            } catch (Exception e) { if (dest != null) getContentResolver().delete(dest,null,null); runOnUiThread(() -> fail("Could not save file: " + e.getMessage())); }
        }).start();
    }
    private void closeOutgoing() { if (outgoing != null) try { outgoing.close(); } catch (Exception ignored) {} outgoing = null; outgoingId = -1; }
    private void fail(String error) { Diagnostics.event(this, "Sender: transfer failed."); show(error); client.stopAllEndpoints(); closeOutgoing(); restoreRadios(); }
    private void show(String text) { status.setText(text); }
    @Override protected void onPause() { if (sender) Diagnostics.event(this, "Sender: activity paused."); if (nfc != null) nfc.disableReaderMode(this); super.onPause(); }
    @Override protected void onResume() { super.onResume(); if (sender && offer != null && !requested) enableNfc(); }
    @Override protected void onDestroy() { if (sender) Diagnostics.event(this, "Sender: activity ended; nearby stopped."); if (nfc != null) nfc.disableReaderMode(this); if (pulse != null) pulse.cancel(); client.stopDiscovery(); client.stopAdvertising(); client.stopAllEndpoints(); closeOutgoing(); restoreRadios(); super.onDestroy(); }
}
