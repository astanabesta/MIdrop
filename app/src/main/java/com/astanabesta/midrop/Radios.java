package com.astanabesta.midrop;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;

/**
 * Share-session radio handling: turn Wi-Fi and Bluetooth on before a transfer, then restore
 * what changed when the session ends.
 *
 * Platform limits (targetSdk 35):
 *  - Wi-Fi can never be toggled by an app on any supported API level (system-only since 10).
 *  - Bluetooth enable()/disable() works on Android 10/11; on Android 12+ the system always
 *    refuses apps, so the fallback is a single tap into the system settings panel.
 */
final class Radios {
    private Radios() { }

    static boolean bluetoothOn(Context c) {
        try {
            BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
            return a != null && a.isEnabled();
        } catch (Exception e) { return false; }
    }

    static boolean wifiOn(Context c) {
        try {
            WifiManager w = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            return w != null && w.isWifiEnabled();
        } catch (Exception e) { return false; }
    }

    /** Programmatic Bluetooth on; accepted on Android 10/11, refused on 12+ (returns false). */
    static boolean tryBluetoothOn(Context c) {
        if (Build.VERSION.SDK_INT >= 31) return false;
        try {
            BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
            return a != null && a.enable();
        } catch (Exception e) { return false; }
    }

    /** Programmatic Bluetooth off; accepted on Android 10/11, refused on 12+ (returns false). */
    static boolean tryBluetoothOff(Context c) {
        if (Build.VERSION.SDK_INT >= 31) return false;
        try {
            BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
            return a != null && a.disable();
        } catch (Exception e) { return false; }
    }

    static void openBluetooth(Activity a) {
        try { a.startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); }
        catch (ActivityNotFoundException ignored) { }
    }

    static void openWifi(Activity a) {
        try { a.startActivity(new Intent(Settings.Panel.ACTION_WIFI)); }
        catch (Exception e) {
            try { a.startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)); } catch (ActivityNotFoundException ignored) { }
        }
    }
}
