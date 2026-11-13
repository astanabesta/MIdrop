package com.astanabesta.midrop;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Opt-in short radio observation. Never associates or sends to an unauthenticated peer. */
public final class IphoneRadioTestActivity extends Activity {
    private static final long DURATION_MS = 20_000;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView result;
    private BluetoothLeScanner scanner;
    private ScanCallback callback;
    private int appleEvents, airDropEvents;
    private boolean scanning;
    private final Runnable timeout = () -> stopScan(true);

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.setupWindow(this);
        LinearLayout root = Ui.column(this); root.setPadding(Ui.dp(this,24),Ui.dp(this,24),Ui.dp(this,24),Ui.dp(this,24));
        root.addView(Ui.displayText(this, "iPhone radio research", 25, true));
        Ui.space(this, root, 12);
        root.addView(Ui.bodyText(this, "Experimental observation only. This does not send files, identify an iPhone, or trigger AirDrop. Nearby Apple-format radio messages can also come from other devices and can be spoofed. Android may filter some beacons.", 14, 400, Ui.muted(this)));
        Ui.space(this, root, 18);
        result = Ui.bodyText(this, "Tap Start to observe for 20 seconds. No addresses, names or raw radio data are saved.", 14, 600, Ui.ink(this)); root.addView(result);
        Ui.space(this, root, 18);
        root.addView(Ui.button(this, "Check phone capabilities", false, this::checkCapabilities));
        root.addView(Ui.button(this, "Start 20-second scan", false, this::start));
        root.addView(Ui.button(this, "Stop", false, () -> stopScan(true)));
        root.addView(Ui.button(this, "Done", false, this::finish));
        setContentView(root);
    }
    /** Read-only platform capability flags; none establishes AirDrop interoperability. */
    private void checkCapabilities() {
        if (scanning) stopScan(true);
        String observe = "unknown", annotation = "unavailable before Android 17", aware = "unknown";
        try {
            android.nfc.NfcAdapter nfc = android.nfc.NfcAdapter.getDefaultAdapter(this);
            if (nfc == null) { observe = "no NFC"; annotation = "no NFC"; }
            else {
                if (Build.VERSION.SDK_INT >= 35) observe = nfc.isObserveModeSupported() ? "supported" : "not supported";
                else observe = "unavailable before Android 15";
                if (Build.VERSION.SDK_INT >= 37) {
                    Object value = android.nfc.NfcAdapter.class.getMethod("isReaderModeAnnotationSupported").invoke(nfc);
                    annotation = Boolean.TRUE.equals(value) ? "supported" : "not supported";
                }
            }
        } catch (Exception | LinkageError ignored) { observe += " (check incomplete)"; }
        try {
            if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)) aware = "not advertised";
            else {
                android.net.wifi.aware.WifiAwareManager manager = (android.net.wifi.aware.WifiAwareManager) getSystemService(WIFI_AWARE_SERVICE);
                aware = manager == null ? "advertised; service unavailable" : (manager.isAvailable() ? "advertised and available" : "advertised but currently unavailable");
            }
        } catch (RuntimeException ignored) { aware = "check unavailable"; }
        String report = "Android API " + Build.VERSION.SDK_INT + "\nNFC observe mode: " + observe
            + "\nNFC polling annotation: " + annotation + "\nWi-Fi Aware: " + aware;
        result.setText(report + "\n\nThese are general device flags only. They do not confirm access to Quick Share's Apple link, iPhone identification, or an AirDrop transfer.");
        Diagnostics.event(this, "iPhone capability: API=" + Build.VERSION.SDK_INT + "; observe=" + observe + "; annotation=" + annotation + "; Wi-Fi Aware=" + aware + ".");
    }
    private void start() {
        if (scanning) return;
        java.util.ArrayList<String> missing = new java.util.ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) missing.add(Manifest.permission.BLUETOOTH_SCAN);
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) missing.add(Manifest.permission.BLUETOOTH_CONNECT);
        } else if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) missing.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (!missing.isEmpty()) { requestPermissions(missing.toArray(new String[0]), 42); return; }
        try {
            BluetoothManager manager = getSystemService(BluetoothManager.class);
            BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
            if (adapter == null || !adapter.isEnabled()) { result.setText("Bluetooth is off or unavailable. Turn it on and retry."); return; }
            scanner = adapter.getBluetoothLeScanner();
            if (scanner == null) { result.setText("Bluetooth scanning is unavailable."); return; }
            appleEvents = 0; airDropEvents = 0; scanning = true;
            callback = new ScanCallback() {
                @Override public void onScanResult(int type, ScanResult item) {
                    if (!scanning || item == null || item.getScanRecord() == null) return;
                    byte[] data = item.getScanRecord().getManufacturerSpecificData(0x004c);
                    if (data == null) return;
                    boolean drop = AppleContinuityFrame.hasAirDrop(data);
                    handler.post(() -> { if (scanning && callback == this) { appleEvents++; if (drop) airDropEvents++; updateText("Scanning"); } });
                }
                @Override public void onScanFailed(int error) { handler.post(() -> {
                    if (!scanning || callback != this) return;
                    Diagnostics.event(IphoneRadioTestActivity.this, "iPhone radio: scan failed (code " + error + ").");
                    stopScan(false); result.setText("Bluetooth scan failed (code " + error + ").");
                }); }
            };
            scanner.startScan(null, new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback);
            Diagnostics.event(this, "iPhone radio: scan started.");
            updateText("Scanning"); handler.postDelayed(timeout, DURATION_MS);
        } catch (SecurityException e) { stopScan(false); result.setText("Bluetooth permission is unavailable."); }
        catch (RuntimeException e) { stopScan(false); Diagnostics.event(this, "iPhone radio: could not start (" + e.getClass().getSimpleName() + ")."); result.setText("Could not start Bluetooth scan."); }
    }
    private void updateText(String status) {
        result.setText(status + ": Apple-format events " + appleEvents + "; AirDrop-type events " + airDropEvents + ".\n\nThese are radio event counts, not device counts. No event does not prove incompatibility; an event does not identify a device or prove AirDrop works.");
    }
    private void stopScan(boolean record) {
        if (!scanning) return;
        scanning = false; handler.removeCallbacks(timeout);
        try { if (scanner != null && callback != null) scanner.stopScan(callback); } catch (RuntimeException ignored) { }
        scanner = null; callback = null;
        if (record) Diagnostics.event(this, "iPhone radio: scan finished; Apple-format events=" + appleEvents + ", AirDrop-type events=" + airDropEvents + ".");
        updateText("Finished");
    }
    @Override public void onRequestPermissionsResult(int request, String[] names, int[] results) {
        super.onRequestPermissionsResult(request, names, results);
        if (request == 42) {
            if (results.length == 0) { result.setText("Bluetooth permissions were not granted."); return; }
            for (int value : results) if (value != PackageManager.PERMISSION_GRANTED) { result.setText("Bluetooth permissions were not granted."); return; }
            start();
        }
    }
    @Override protected void onPause() { stopScan(true); super.onPause(); }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy(); }
}
