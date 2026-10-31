package com.astanabesta.midrop;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Button;
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

/** Foreground-only, single-image transfer prototype. */
public class SharingActivity extends Activity {
    private static final String SERVICE = "com.astanabesta.midrop.share.v1";
    private static final Strategy STRATEGY = Strategy.P2P_POINT_TO_POINT;
    private ConnectionsClient client;
    private LinearLayout peers;
    private TextView status, chosen;
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
        Ui.setupWindow(this);
        ScrollView page = new ScrollView(this); page.setFillViewport(true); page.setBackgroundColor(Ui.BACK);
        LinearLayout root = Ui.column(this); root.setPadding(Ui.dp(this,24),Ui.dp(this,22),Ui.dp(this,24),Ui.dp(this,28));
        TextView brand = Ui.text(this,"◉  MIdrop",20,Ui.INK,true); root.addView(brand);
        Ui.space(this,root,28);
        TextView label = Ui.text(this,"A BETTER WAY TO SHARE",10,Ui.BLUE,true); root.addView(label);
        Ui.space(this,root,9);
        TextView title = Ui.text(this,"Close enough\nto connect.",30,Ui.INK,true); root.addView(title);
        Ui.space(this,root,8);
        TextView instructions = Ui.text(this,"Share what matters with a simple tap.",14,Ui.MUTED,false); root.addView(instructions);
        Ui.space(this,root,22);
        LinearLayout hero = new LinearLayout(this); hero.setGravity(android.view.Gravity.CENTER); hero.setOrientation(LinearLayout.VERTICAL);
        hero.setBackground(Ui.shape(android.graphics.Color.rgb(232,239,255),Ui.dp(this,24)));
        TextView symbol = Ui.centered(this,"◎",76,Ui.BLUE,true); hero.addView(symbol);
        TextView heroText = Ui.centered(this,"Bring phones together",13,Ui.INK,true); hero.addView(heroText);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1,Ui.dp(this,150)); root.addView(hero,hp);
        Ui.space(this,root,15);
        status = Ui.text(this,"●  Set up receiving to get started",12,Ui.MUTED,true); root.addView(status);
        Ui.space(this,root,14);
        chosen = Ui.text(this,"No photo selected",12,Ui.MUTED,false); root.addView(chosen);
        Ui.space(this,root,15);
        button(root,"Set up background receiving", () -> withPermissions(3));
        Ui.space(this,root,10);
        TextView caption = Ui.text(this,"SEND FROM ANY APP",10,Ui.MUTED,true); root.addView(caption);
        Ui.space(this,root,8);
        TextView guide = Ui.text(this,"Choose files in Gallery or Files, then tap Share → MIdrop. Bring the phones together.",13,Ui.MUTED,false); root.addView(guide);
        Ui.space(this,root,16);
        TextView manual = Ui.text(this,"MANUAL SHARING",10,Ui.MUTED,true); root.addView(manual);
        Ui.space(this,root,9);
        button(root,"Choose photo", () -> { Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("image/*"); i.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(i, 10); });
        button(root,"Receive a photo", () -> withPermissions(1));
        button(root,"Find devices and send", () -> { if (photo == null) { show("Choose a photo first."); return; } withPermissions(2); });
        button(root,"Stop / start over", this::reset);
        Ui.space(this,root,10);
        TextView experimental = Ui.text(this,"EXPERIMENTAL TOOLS",10,Ui.MUTED,true); root.addView(experimental);
        button(root,"Test iPhone NFC (experimental)", () -> startActivity(new Intent(this, IphoneProbeActivity.class)));
        button(root,"Open experimental NFC test", () -> startActivity(new Intent(this, MainActivity.class)));
        Ui.space(this,root,8);
        TextView deviceLabel = Ui.text(this,"Nearby devices",13,Ui.INK,true); root.addView(deviceLabel);
        peers = new LinearLayout(this); peers.setOrientation(LinearLayout.VERTICAL); root.addView(peers);
        page.addView(root); setContentView(page);
        readSharedIntent(getIntent()); updatePhoto();
    }
    private void button(LinearLayout root, String label, Runnable action) { Button b = Ui.button(this, label, label.equals("Set up background receiving"), action); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, Ui.dp(this,50)); p.bottomMargin = Ui.dp(this,8); root.addView(b,p); }
    private void readSharedIntent(Intent intent) {
        if (Intent.ACTION_SEND.equals(intent.getAction()) && intent.getType() != null && intent.getType().startsWith("image/")) {
            Uri u = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (u != null) photo = u;
        }
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); readSharedIntent(intent); updatePhoto(); }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request,result,data);
        if (request == 10 && result == RESULT_OK && data != null) { photo = data.getData(); updatePhoto(); }
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
            show("Searching for nearby receivers…");
            client.startDiscovery(SERVICE, discovery, new DiscoveryOptions.Builder().setStrategy(STRATEGY).build()).addOnFailureListener(e -> show("Search failed: " + e.getMessage()));
        } else {
            show("Ready to receive. Keep this screen open.");
            client.startAdvertising(Build.MODEL, SERVICE, lifecycle, new AdvertisingOptions.Builder().setStrategy(STRATEGY).build()).addOnFailureListener(e -> show("Receive mode failed: " + e.getMessage()));
        }
    }
    private final EndpointDiscoveryCallback discovery = new EndpointDiscoveryCallback() {
        @Override public void onEndpointFound(String id, DiscoveredEndpointInfo info) {
            Button b = new Button(SharingActivity.this); b.setText("Connect: " + info.getEndpointName());
            b.setOnClickListener(v -> { b.setEnabled(false); show("Connecting…"); client.requestConnection(Build.MODEL, id, lifecycle).addOnFailureListener(e -> show("Connection failed: " + e.getMessage())); });
            b.setTag(id); peers.addView(b);
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
            sendText(offer.toString()); show("Waiting for receiver to accept the photo…");
        } catch (Exception e) { show("Could not prepare image: " + e.getMessage()); }
    }
    private void sendText(String message) { if (endpoint != null) client.sendPayload(endpoint, Payload.fromBytes(message.getBytes(StandardCharsets.UTF_8))).addOnFailureListener(e -> show("Message failed: " + e.getMessage())); }
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
                            .setPositiveButton("Accept", (d,w) -> { allowIncoming = true; sendText("{\"kind\":\"accept\"}"); show("Waiting for photo…"); })
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
            client.sendPayload(endpoint,file).addOnFailureListener(e -> { closeOutgoing(); show("Sending failed: " + e.getMessage()); });
            show("Sending photo…");
        } catch (Exception e) { closeOutgoing(); show("Cannot send photo: " + e.getMessage()); }
    }
    private void savePhoto(Payload payload) {
        String mime = imageMime;
        show("Saving received photo…");
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
    private void show(String text) { status.setText(text); }
    private void closeOutgoing() { if (outgoing != null) { try { outgoing.close(); } catch (Exception ignored) {} outgoing = null; } outgoingId = -1; }
    private void resetConnections() { client.stopDiscovery(); client.stopAdvertising(); client.stopAllEndpoints(); endpoint = null; allowIncoming = false; incoming = null; closeOutgoing(); }
    private void reset() { resetConnections(); peers.removeAllViews(); show("Ready."); }
    @Override protected void onDestroy() { resetConnections(); super.onDestroy(); }
}
