package com.astanabesta.midrop;
import org.junit.Test;
import static org.junit.Assert.*;
public class LinkObservationTest {
    @Test public void onlyKnownCategoriesAreReported() {
        assertEquals(1, LinkObservation.classify("mosey0"));
        assertEquals(1, LinkObservation.classify("awdl0"));
        assertEquals(2, LinkObservation.classify("aware_data0"));
        assertEquals(4, LinkObservation.classify("p2p-wlan0-0"));
        assertEquals(0, LinkObservation.classify("wlan0"));
        assertEquals(0, LinkObservation.classify("mosey-not-an-interface"));
        assertEquals(0, LinkObservation.classify(null));
    }
}
