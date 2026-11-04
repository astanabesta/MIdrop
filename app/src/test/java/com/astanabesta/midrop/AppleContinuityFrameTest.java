package com.astanabesta.midrop;

import org.junit.Test;
import static org.junit.Assert.*;

public class AppleContinuityFrameTest {
    @Test public void emptyAndNullAreNotAirDrop() {
        assertFalse(AppleContinuityFrame.hasAirDrop(null));
        assertFalse(AppleContinuityFrame.hasAirDrop(new byte[0]));
    }
    @Test public void findsOnlyCompleteAirDropTlv() {
        assertTrue(AppleContinuityFrame.hasAirDrop(new byte[]{0x05, 0x02, 1, 2}));
        assertTrue(AppleContinuityFrame.hasAirDrop(new byte[]{0x10, 0x01, 8, 0x05, 0x01, 7}));
        assertFalse(AppleContinuityFrame.hasAirDrop(new byte[]{0x10, 0x02, 0x05, 0}));
        assertFalse(AppleContinuityFrame.hasAirDrop(new byte[]{0x05, 0x02, 1}));
        assertFalse(AppleContinuityFrame.hasAirDrop(new byte[]{0x05}));
        assertFalse(AppleContinuityFrame.hasAirDrop(new byte[]{0x10, (byte)255, 0x05, 0}));
    }
}
