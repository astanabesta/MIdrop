package com.astanabesta.midrop;

import android.app.Activity;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.Bundle;
import android.provider.Settings;
import android.content.Intent;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.IOException;

public class MainActivity extends Activity {
    private NfcAdapter nfc;
    private TextView status, log;
    private boolean sender;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        nfc = NfcAdapter.getDefaultAdapter(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);
        TextView title = new TextView(this);
        title.setText("MIdrop · NFC test"); title.setTextSize(24); root.addView(title);
        TextView help = new TextView(this);
        help.setText("Install on both phones. Choose Receiver on one and Sender on the other. Keep both unlocked and NFC on. Bring their backs close together. For a background test, press Home on the receiver; reopen it to inspect the log.\n\nThe sender must remain on this screen. This prototype does not transfer files.");
        root.addView(help);
        status = new TextView(this); status.setPadding(0, 20, 0, 10); root.addView(status);
        Button receiver = new Button(this); receiver.setText("Receiver mode");
        receiver.setOnClickListener(v -> { sender = false; updateReader(); EventLog.add(this, "Receiver mode selected; leave unlocked."); refresh(); }); root.addView(receiver);
        Button send = new Button(this); send.setText("Sender mode");
        send.setOnClickListener(v -> { sender = true; updateReader(); EventLog.add(this, "Sender mode selected. Keep this screen open."); refresh(); }); root.addView(send);
        Button nfcSettings = new Button(this); nfcSettings.setText("Open NFC settings");
        nfcSettings.setOnClickListener(v -> { try { startActivity(new Intent(Settings.ACTION_NFC_SETTINGS)); } catch (android.content.ActivityNotFoundException e) { startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS)); } }); root.addView(nfcSettings);
        LinearLayout buttons = new LinearLayout(this);
        Button refresh = new Button(this); refresh.setText("Refresh log"); refresh.setOnClickListener(v -> refresh()); buttons.addView(refresh);
        Button clear = new Button(this); clear.setText("Clear log"); clear.setOnClickListener(v -> { EventLog.clear(this); refresh(); }); buttons.addView(clear); root.addView(buttons);
        ScrollView scroll = new ScrollView(this); log = new TextView(this); log.setTextIsSelectable(true); log.setGravity(Gravity.START); scroll.addView(log);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1)); setContentView(root); refresh();
    }
    @Override protected void onResume() { super.onResume(); updateReader(); refresh(); }
    @Override protected void onPause() { if (nfc != null) nfc.disableReaderMode(this); super.onPause(); }
    private void refresh() {
        String capability = nfc == null ? "NFC unavailable" : (nfc.isEnabled() ? "NFC on" : "NFC off");
        status.setText(capability + " · " + (sender ? "Sender" : "Receiver") + " · HCE support: " + getPackageManager().hasSystemFeature("android.hardware.nfc.hce"));
        log.setText(EventLog.read(this));
    }
    private void updateReader() {
        if (nfc == null) return;
        if (sender) nfc.enableReaderMode(this, this::onTag, NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null);
        else nfc.disableReaderMode(this);
    }
    private void onTag(Tag tag) {
        IsoDep iso = IsoDep.get(tag);
        if (iso == null) { record("Sender: NFC device found, but ISO-DEP unavailable."); return; }
        try {
            iso.connect();
            byte[] response = iso.transceive(Protocol.select());
            record(Protocol.isReply(response) ? "Sender: MIdrop receiver responded successfully." : "Sender: unexpected NFC response.");
        } catch (IOException e) { record("Sender: NFC exchange failed: " + e.getClass().getSimpleName()); }
        finally { try { iso.close(); } catch (IOException ignored) { } }
    }
    private void record(String message) { EventLog.add(this, message); runOnUiThread(this::refresh); }
}
