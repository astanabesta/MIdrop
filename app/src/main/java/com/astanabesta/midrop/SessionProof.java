package com.astanabesta.midrop;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** NFC-shared secret binds a Nearby connection's authentication code to one session. */
final class SessionProof {
    static final int SECRET_BYTES = 32;
    private static final int ID_BYTES = 16;
    private SessionProof() { }

    static byte[] calculate(byte[] secret, byte[] sessionId, String digits, String role)
            throws GeneralSecurityException {
        if (secret == null || secret.length != SECRET_BYTES || sessionId == null || sessionId.length != ID_BYTES
                || digits == null || !digits.matches("[0-9]{4,8}")
                || !("sender".equals(role) || "receiver".equals(role))) {
            throw new IllegalArgumentException("Invalid session proof input");
        }
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(buffer);
            out.write("MIdrop/proof/v1".getBytes(StandardCharsets.US_ASCII));
            out.write(sessionId);
            out.writeUTF(role);
            out.writeUTF(digits);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(buffer.toByteArray());
        } catch (java.io.IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
    static boolean verify(byte[] secret, byte[] sessionId, String digits, String role, byte[] supplied)
            throws GeneralSecurityException {
        return supplied != null && MessageDigest.isEqual(calculate(secret, sessionId, digits, role), supplied);
    }
}
