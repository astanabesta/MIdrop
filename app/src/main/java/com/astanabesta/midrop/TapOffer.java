package com.astanabesta.midrop;

import java.nio.ByteBuffer;
import java.security.SecureRandom;

/** Fixed-size NFC APDU. Session secrets must never appear in endpoint names or logs. */
final class TapOffer {
    static final int LENGTH = 64, MAX_FILES = 25;
    static final long TTL_MS = 120_000;
    final byte[] id, secret;
    final long created;
    final int count;
    TapOffer(byte[] id, byte[] secret, long created, int count) {
        this.id = id.clone(); this.secret = secret.clone(); this.created = created; this.count = count;
    }
    static TapOffer create(int count, long now) {
        if (count < 1 || count > MAX_FILES) throw new IllegalArgumentException("file count");
        byte[] id = new byte[16], secret = new byte[32]; SecureRandom r = new SecureRandom(); r.nextBytes(id); r.nextBytes(secret);
        return new TapOffer(id, secret, now, count);
    }
    byte[] command() {
        ByteBuffer b = ByteBuffer.allocate(5 + LENGTH);
        b.put((byte) 0x80).put((byte) 1).put((byte) 0).put((byte) 0).put((byte) LENGTH);
        b.put((byte) 1).put(id).put(secret).putLong(created).putInt(count).put(new byte[3]);
        return b.array();
    }
    static TapOffer parse(byte[] command, long now) {
        if (command == null || command.length != 5 + LENGTH || command[0] != (byte) 0x80 || command[1] != 1 || command[2] != 0 || command[3] != 0 || command[4] != LENGTH) return null;
        ByteBuffer b = ByteBuffer.wrap(command, 5, LENGTH);
        if (b.get() != 1) return null;
        byte[] id = new byte[16], key = new byte[32]; b.get(id); b.get(key);
        long timestamp = b.getLong(); int count = b.getInt();
        if (timestamp > now + 30_000 || timestamp < now - TTL_MS || count < 1 || count > MAX_FILES) return null;
        return new TapOffer(id, key, timestamp, count);
    }
    String publicName() {
        StringBuilder s = new StringBuilder("MD");
        for (byte b : id) s.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
        return s.toString();
    }
}
