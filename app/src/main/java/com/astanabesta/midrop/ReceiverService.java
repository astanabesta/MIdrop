package com.astanabesta.midrop;

import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;

public class ReceiverService extends HostApduService {
    @Override public byte[] processCommandApdu(byte[] command, Bundle extras) {
        if (Protocol.isSelect(command)) {
            EventLog.add(this, "Receiver: NFC tap detected. Check whether app was foreground or background.");
            return Protocol.reply();
        }
        return new byte[]{(byte) 0x6A, (byte) 0x82};
    }
    @Override public void onDeactivated(int reason) { }
}
