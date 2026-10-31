package com.astanabesta.midrop;

import org.junit.Test;
import static org.junit.Assert.*;

public class ProtocolTest {
    @Test public void commandsMatchAndAreDefensiveCopies() {
        byte[] command = Protocol.select();
        assertTrue(Protocol.isSelect(command));
        command[0] = 1;
        assertFalse(Protocol.isSelect(command));
        assertTrue(Protocol.isSelect(Protocol.select()));
    }
    @Test public void responseMustMatchExactly() {
        assertTrue(Protocol.isReply(Protocol.reply()));
        assertFalse(Protocol.isReply(new byte[]{(byte)0x90, 0}));
        assertFalse(Protocol.isReply(null));
    }
}
