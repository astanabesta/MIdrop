package com.astanabesta.midrop;
import java.util.Locale;
/** Maps a transient interface name to a coarse, non-identifying category. */
final class LinkObservation {
    private LinkObservation() { }
    static int classify(String name) {
        if (name == null) return 0;
        name = name.toLowerCase(Locale.ROOT);
        if (name.matches("(mosey|awdl|llw)[0-9]+")) return 1;
        if (name.startsWith("aware") || name.startsWith("wifi-aware")) return 2;
        if (name.startsWith("p2p")) return 4;
        return 0;
    }
}
