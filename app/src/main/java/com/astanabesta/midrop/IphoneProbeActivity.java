package com.astanabesta.midrop;

import android.app.Activity;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Arrays;

/** Local diagnostic only: it does not attempt AirDrop or collect device identifiers. */
public final class IphoneProbeActivity extends Activity {
    private NfcAdapter adapter;
    private TextView result;
    private volatile boolean busy;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        adapter = NfcAdapter.getDefaultAdapter(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(36, 40, 36, 36);
        TextView title = new TextView(this); title.setText("iPhone NFC experiment"); title.setTextSize(24); layout.addView(title);
        TextView help = new TextView(this);
        help.setText("Diagnostic only — this does not identify an iPhone or send files. Turn NFC on, keep this screen open, and touch the backs of the unlocked phones together. On the iPhone, enable AirDrop and Bluetooth/Wi-Fi. Try several positions. The app does not read or store device IDs.\n\nIf a Wallet or payment prompt appears, dismiss it. Do not approve a payment or present a payment card.");
        help.setPadding(0, 20, 0, 20); layout.addView(help);
        result = new TextView(this); result.setTextSize(15); layout.addView(result);
        Button clear = new Button(this); clear.setText("Reset result"); clear.setOnClickListener(v -> initialStatus()); layout.addView(clear);
        Button close = new Button(this); close.setText("Done"); close.setOnClickListener(v -> finish()); layout.addView(close);
        setContentView(layout); initialStatus();
    }
    private void initialStatus() {
        if (adapter == null) result.setText("NFC is unavailable on this phone.");
        else if (!adapter.isEnabled()) result.setText("NFC is off. Enable it and return to this screen.");
        else result.setText("Listening for NFC. No tag event yet; this does not prove either device is incompatible.");
    }
    @Override protected void onResume() {
        super.onResume(); initialStatus();
        if (adapter != null && adapter.isEnabled()) adapter.enableReaderMode(this, this::onTag,
                NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B |
                NfcAdapter.FLAG_READER_NFC_F | NfcAdapter.FLAG_READER_NFC_V |
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null);
    }
    @Override protected void onPause() { if (adapter != null) adapter.disableReaderMode(this); super.onPause(); }
    private void onTag(Tag tag) {
        if (busy) return; busy = true;
        try {
            String techs = Arrays.toString(tag.getTechList()).replace("android.nfc.tech.", "");
            IsoDep iso = IsoDep.get(tag);
            if (iso == null) { report("NFC event detected. Technologies: " + techs + "\n\nNo ISO-DEP interface was exposed. This does not identify the phone or indicate AirDrop support."); return; }
            try {
                iso.connect(); iso.setTimeout(1500);
                byte[] response = iso.transceive(Protocol.select());
                String outcome = Protocol.isReply(response) ? "MIdrop test receiver responded. This does not prove the other device is an iPhone."
                        : "NFC device responded, but did not respond as a MIdrop receiver. This does not establish AirDrop compatibility.";
                report("NFC event detected. Technologies: " + techs + "\n\n" + outcome);
            } catch (Exception e) {
                report("NFC event detected. Technologies: " + techs + "\n\nThe MIdrop test handshake did not complete (" + e.getClass().getSimpleName() + "). This does not establish whether AirDrop is possible.");
            } finally { try { iso.close(); } catch (Exception ignored) { } }
        } finally { busy = false; }
    }
    private void report(String message) { runOnUiThread(() -> result.setText(message)); }
}
