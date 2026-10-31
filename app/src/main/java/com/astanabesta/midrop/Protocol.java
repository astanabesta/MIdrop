package com.astanabesta.midrop;

import java.util.Arrays;

final class Protocol {
    private static final byte[] SELECT = {(byte) 0x00, (byte) 0xA4, 0x04, 0x00, 0x07,
            (byte) 0xF0, 0x4D, 0x49, 0x44, 0x52, 0x4F, 0x50};
    private static final byte[] OK = {0x4D, 0x49, 0x44, 0x52, 0x4F, 0x50, (byte) 0x90, 0x00};
    static byte[] select() { return SELECT.clone(); }
    static boolean isSelect(byte[] data) { return Arrays.equals(SELECT, data); }
    static byte[] reply() { return OK.clone(); }
    static boolean isReply(byte[] data) { return Arrays.equals(OK, data); }
}
