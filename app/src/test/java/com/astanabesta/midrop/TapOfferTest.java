package com.astanabesta.midrop;
import org.junit.Test;
import static org.junit.Assert.*;
public class TapOfferTest {
    @Test public void offerRoundTripAndExpiry() {
        TapOffer original = TapOffer.create(2, 1000000);
        TapOffer decoded = TapOffer.parse(original.command(), 1000001);
        assertNotNull(decoded); assertArrayEquals(original.id,decoded.id); assertArrayEquals(original.secret,decoded.secret);
        assertEquals(2,decoded.count); assertEquals(original.publicName(),decoded.publicName());
        assertNull(TapOffer.parse(original.command(), 1120001));
    }
    @Test public void malformedRejected() {
        TapOffer offer = TapOffer.create(1, 1000000); byte[] command = offer.command(); command[0] = 0;
        assertNull(TapOffer.parse(command, 1000000)); assertNull(TapOffer.parse(new byte[2], 1000000));
    }
}
