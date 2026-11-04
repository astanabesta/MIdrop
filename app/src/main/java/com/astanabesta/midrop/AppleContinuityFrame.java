package com.astanabesta.midrop;

/** Observational, not an authentication or device-identification protocol. */
final class AppleContinuityFrame {
    private AppleContinuityFrame() { }
    static boolean hasAirDrop(byte[] bytes) {
        if (bytes == null) return false;
        for (int i = 0; i + 2 <= bytes.length;) {
            int type = bytes[i] & 0xff;
            int length = bytes[i + 1] & 0xff;
            if (length > bytes.length - i - 2) return false;
            if (type == 0x05 && length > 0) return true;
            i += 2 + length;
        }
        return false;
    }
}
