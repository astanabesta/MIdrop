package com.astanabesta.midrop;

import android.app.Activity;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.Bundle;
import android.provider.Settings;
import android.content.Intent;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.IOException;

/** Experimental NFC handshake test (no file transfer). Signal-styled. */
public class MainActivity extends Activity {
    private NfcAdapter nfc;
    private TextView status, log;
    private boolean sender;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.setupWindow(this);
        nfc = NfcAdapter.getDefaultAdapter(this);
        LinearLayout root = Ui.column(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this,24), Ui.dp(this,24), Ui.dp(this,24), Ui.dp(this,24));
        root.addView(Ui.displayText(this,"NFC handshake test",24,true));
        Ui.space(this,root,10);
        TextView help = Ui.bodyText(this,
            "Install on both phones. Choose Receiver on one and Sender on the other. Keep both unlocked and NFC on. Bring their backs close together. For a background test, press Home on the receiver; reopen it to inspect the log.\n\nThe sender must remain on this screen. This prototype does not transfer files.",
            13, 400, Ui.muted(this));
        root.addView(help);
        Ui.space(this,root,16);
        status = Ui.bodyText(this,"",13,600,Ui.accent(this)); root.addView(status);
        Ui.space(this,root,16);
        root.addView(Ui.button(this,"Receiver mode",false, () -> { sender = false; updateReader(); EventLog.add(this, "Receiver mode selected; leave unlocked."); refresh(); }), new LinearLayout.LayoutParams(-1,Ui.dp(this,50)));
        Ui.space(this,root,8);
        root.addView(Ui.button(this,"Sender mode",true, () -> { sender = true; updateReader(); EventLog.add(this, "Sender mode selected. Keep this screen open."); refresh(); }), new LinearLayout.LayoutParams(-1,Ui.dp(this,50)));
        Ui.space(this,root,8);
        root.addView(Ui.button(this,"Open NFC settings",false, () -> {
            try { startActivity(new Intent(Settings.ACTION_NFC_SETTINGS)); }
            catch (android.content.ActivityNotFoundException e) { startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS)); }
        }), new LinearLayout.LayoutParams(-1,Ui.dp(this,50)));
        Ui.space(this,root,8);
        LinearLayout buttons = new LinearLayout(this); buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button refresh = Ui.button(this,"Refresh log",false,this::refresh);
        Button clear = Ui.button(this,"Clear log",false,() -> { EventLog.clear(this); refresh(); });
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0,Ui.dp(this,50),1); rp.rightMargin = Ui.dp(this,8);
        buttons.addView(refresh,rp); buttons.addView(clear,new LinearLayout.LayoutParams(0,Ui.dp(this,50),1));
        root.addView(buttons);
        Ui.space(this,root,12);
        ScrollView scroll = new ScrollView(this);
        log = Ui.bodyText(this,"",11,400,Ui.muted(this));
        log.setTypeface(Typeface.MONOSPACE);
        log.setTextIsSelectable(true); log.setGravity(Gravity.START);
        scroll.addView(log);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root); refresh();
    }
    @Override protected void onResume() { super.onResume(); updateReader(); refresh(); }
    @Override protected void onPause() { if (nfc != null) nfc.disableReaderMode(this); super.onPause(); }
    private void refresh() {
        String capability = nfc == null ? "NFC unavailable" : (nfc.isEnabled() ? "NFC on" : "NFC off");
        status.setText("\u25cf  " + capability + " \u00b7 " + (sender ? "Sender" : "Receiver") + " \u00b7 HCE: " + getPackageManager().hasSystemFeature("android.hardware.nfc.hce"));
        log.setText(EventLog.read(this));
    }
    private void updateReader() {
        if (nfc == null) return;
        if (sender) nfc.enableReaderMode(this, this::onTag, NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null);
        else nfc.disableReaderMode(this);
    }
    private void onTag(Tag tag) {
        IsoDep iso = IsoDep.get(tag); if (iso == null) { record("Sender: NFC device found, but ISO-DEP unavailable."); return; }
        try {
            iso.connect();
            byte[] response = iso.transceive(Protocol.select());
            record(Protocol.isReply(response) ? "Sender: MIdrop receiver responded successfully." : "Sender: unexpected NFC response.");
        } catch (IOException e) { record("Sender: NFC exchange failed: " + e.getClass().getSimpleName()); }
        finally { try { iso.close(); } catch (IOException ignored) { } }
    }
    private void record(String message) { EventLog.add(this, message); runOnUiThread(this::refresh); }
}
