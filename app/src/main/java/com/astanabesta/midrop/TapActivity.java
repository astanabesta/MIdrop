package com.astanabesta.midrop;

import android.Manifest;
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
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.*;
import org.json.JSONObject;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/** NFC bootstrap + authenticated Nearby transfer, while this transfer screen is visible. */
public class TapActivity extends Activity {
    private static final String SERVICE = "com.astanabesta.midrop.tap.v1";
    private final ArrayList<Uri> files = new ArrayList<>();
    private ConnectionsClient client;
    private NfcAdapter nfc;
    private TextView status;
    private TapOffer offer;
    private boolean sender, verified, requested, offerAccepted, saving, advertising, savedEarly;
    private String endpoint, digits, incomingName, incomingMime;
    private int currentIndex, receivedCount;
    private Payload incoming;
    private ParcelFileDescriptor outgoing;
    private long outgoingId = -1;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        client = Nearby.getConnectionsClient(this); nfc = NfcAdapter.getDefaultAdapter(this);
        LinearLayout root = new LinearLayout(this); root.setPadding(36,36,36,36); root.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this); title.setText("MIdrop · Tap to share"); title.setTextSize(25); root.addView(title);
        status = new TextView(this); status.setPadding(0,25,0,25); root.addView(status);
        Button stop = new Button(this); stop.setText("Cancel"); stop.setOnClickListener(v -> finish()); root.addView(stop);
        setContentView(root);
        Intent intent = getIntent();
        if (ReceiverService.ACTION_ACCEPT.equals(intent.getAction())) {
            getSystemService(NotificationManager.class).cancel(ReceiverService.NOTIFICATION_ID);
            offer = TapOffer.parse(intent.getByteArrayExtra("offer"), System.currentTimeMillis());
            String stored = getSharedPreferences("tap", MODE_PRIVATE).getString("id", "");
            if (offer == null || !stored.equals(offer.publicName()) || !getSharedPreferences("tap", MODE_PRIVATE).getBoolean("pending", false)) { show("This request expired or was already declined."); return; }
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
            client.startAdvertising(offer.publicName(), SERVICE, lifecycle, new AdvertisingOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build())
                .addOnSuccessListener(v -> { advertising = true; show("Touch the backs of both unlocked phones together. Keep this screen open."); enableNfc(); })
                .addOnFailureListener(e -> show("Could not start sharing: " + e.getMessage()));
        } else {
            show("Connecting to sender… Keep this screen open.");
            client.startDiscovery(SERVICE, discovery, new DiscoveryOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build())
                .addOnFailureListener(e -> show("Could not find sender: " + e.getMessage()));
        }
    }
    private void enableNfc() { if (sender && advertising && nfc != null && offer != null && !requested) nfc.enableReaderMode(this, this::onTag, NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null); }
    private void onTag(Tag tag) {
        IsoDep iso = IsoDep.get(tag); if (iso == null) return;
        try {
            iso.connect(); if (!Protocol.isReply(iso.transceive(Protocol.select()))) throw new IllegalStateException("MIdrop receiver not found");
            byte[] reply = iso.transceive(offer.command());
            if (reply == null || reply.length != 2 || reply[0] != (byte)0x90 || reply[1] != 0) throw new IllegalStateException("Receiver unavailable or notifications disabled");
            runOnUiThread(() -> { requested = true; if (nfc != null) nfc.disableReaderMode(this); show("Request sent. Waiting for receiver to accept…"); });
        } catch (Exception e) { runOnUiThread(() -> show("Tap failed: " + e.getMessage() + ". Check receiver setup and try again.")); }
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
            digits = info.getAuthenticationDigits();
            client.acceptConnection(id, payloads).addOnFailureListener(e -> show("Connection failed: " + e.getMessage()));
        }
        @Override public void onConnectionResult(String id, ConnectionResolution result) {
            if (!result.getStatus().isSuccess()) { show("Connection rejected or failed."); return; }
            endpoint = id; client.stopDiscovery(); client.stopAdvertising(); show("Verifying NFC session…");
            try {
                JSONObject m = new JSONObject(); m.put("kind","proof");
                m.put("proof", Base64.encodeToString(SessionProof.calculate(offer.secret,offer.id,digits,sender ? "sender" : "receiver"),Base64.NO_WRAP)); send(m);
            } catch (Exception e) { fail("Could not verify connection."); }
        }
        @Override public void onDisconnected(String id) { endpoint = null; closeOutgoing(); if (!isFinishing()) show("Disconnected. Retry the share if it did not finish."); }
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
                        verified = true; show("Verified. " + (sender ? "Preparing files…" : "Waiting for files…")); if (sender) sendManifest();
                    } else if (!verified) fail("Unverified sender.");
                    else if (!sender && kind.equals("file") && incoming == null && !saving && !offerAccepted && receivedCount < offer.count) {
                        incomingName = safeName(msg.optString("name", "file")); incomingMime = msg.optString("mime", "application/octet-stream");
                        if (incomingMime.length() > 128 || !incomingMime.contains("/")) incomingMime = "application/octet-stream";
                        offerAccepted = true; simple("ready"); show("Receiving " + (receivedCount + 1) + " of " + offer.count + "…");
                    } else if (sender && kind.equals("ready")) sendFile();
                    else if (sender && kind.equals("saved")) { if (outgoing != null) savedEarly = true; else advanceFile(); }
                    else if (!sender && kind.equals("done") && receivedCount == offer.count) show("All files saved.");
                } catch (Exception e) { fail("Invalid or unverifiable message."); }
            } else if (payload.getType() == Payload.Type.FILE) {
                if (!sender && verified && offerAccepted && incoming == null && !saving) incoming = payload; else client.cancelPayload(payload.getId());
            }
        }
        @Override public void onPayloadTransferUpdate(String id, PayloadTransferUpdate update) {
            if (update.getPayloadId() == outgoingId) {
                if (update.getStatus() == PayloadTransferUpdate.Status.SUCCESS) { closeOutgoing(); if (savedEarly) { savedEarly = false; advanceFile(); } else show("Sent. Waiting for save confirmation…"); }
                else if (update.getStatus() == PayloadTransferUpdate.Status.FAILURE || update.getStatus() == PayloadTransferUpdate.Status.CANCELED) fail("Transfer failed.");
                else if (update.getTotalBytes() > 0) show("Sending " + (currentIndex+1) + "/" + files.size() + ": " + 100 * update.getBytesTransferred() / update.getTotalBytes() + "%");
            }
            if (incoming != null && update.getPayloadId() == incoming.getId()) {
                if (update.getStatus() == PayloadTransferUpdate.Status.SUCCESS) { Payload p = incoming; incoming = null; offerAccepted = false; saving = true; saveFile(p,incomingName,incomingMime); }
                else if (update.getStatus() == PayloadTransferUpdate.Status.FAILURE || update.getStatus() == PayloadTransferUpdate.Status.CANCELED) fail("Transfer failed.");
            }
        }
    };
    private void advanceFile() { currentIndex++; if (currentIndex < files.size()) sendManifest(); else { simple("done"); show("All files sent and saved."); } }
    private void sendManifest() {
        if (!verified || currentIndex >= files.size()) return;
        Uri file = files.get(currentIndex);
        try { JSONObject m = new JSONObject(); m.put("kind","file"); m.put("name",safeName(name(file))); String mime = getContentResolver().getType(file); m.put("mime",mime == null ? "application/octet-stream" : mime); send(m); }
        catch (Exception e) { fail("Cannot prepare file."); }
    }
    private String name(Uri uri) {
        try (Cursor c = getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)) { if (c != null && c.moveToFirst()) return c.getString(0); } catch (Exception ignored) {} return "file";
    }
    private static String safeName(String s) { if (s == null) return "file"; s = s.replaceAll("[\\\\/\\p{Cntrl}]", "_").trim(); if (s.length() > 100) s = s.substring(s.length()-100); return s.isEmpty() || s.equals(".") || s.equals("..") ? "file" : s; }
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
    private void fail(String error) { show(error); client.stopAllEndpoints(); closeOutgoing(); }
    private void show(String text) { status.setText(text); }
    @Override protected void onPause() { if (nfc != null) nfc.disableReaderMode(this); super.onPause(); }
    @Override protected void onResume() { super.onResume(); if (sender && offer != null && !requested) enableNfc(); }
    @Override protected void onDestroy() { if (nfc != null) nfc.disableReaderMode(this); client.stopDiscovery(); client.stopAdvertising(); client.stopAllEndpoints(); closeOutgoing(); super.onDestroy(); }
}
