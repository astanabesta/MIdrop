package com.astanabesta.midrop;

import org.junit.Test;
import static org.junit.Assert.*;

public class SessionProofTest {
    @Test public void proofBindsRoleDigitsAndSession() throws Exception {
        byte[] key = new byte[32]; key[0] = 7;
        byte[] id = new byte[16]; id[0] = 9;
        byte[] proof = SessionProof.calculate(key, id, "1234", "sender");
        assertTrue(SessionProof.verify(key, id, "1234", "sender", proof));
        assertFalse(SessionProof.verify(key, id, "1235", "sender", proof));
        assertFalse(SessionProof.verify(key, id, "1234", "receiver", proof));
        id[1] = 1;
        assertFalse(SessionProof.verify(key, id, "1234", "sender", proof));
    }
    @Test public void rejectInvalidInputsAndMissingProof() throws Exception {
        byte[] key = new byte[32], id = new byte[16];
        assertFalse(SessionProof.verify(key, id, "1234", "sender", null));
        try { SessionProof.calculate(key, id, "oops", "sender"); fail(); } catch (IllegalArgumentException expected) { }
        try { SessionProof.calculate(new byte[1], id, "1234", "sender"); fail(); } catch (IllegalArgumentException expected) { }
    }
}
