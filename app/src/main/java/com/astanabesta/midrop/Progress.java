package com.astanabesta.midrop;
final class Progress {
    static int percent(long bytes, long total) {
        if (total <= 0 || bytes <= 0) return 0;
        if (bytes >= total) return 100;
        return (int) (100.0 * bytes / total);
    }
}
