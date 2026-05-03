package com.byteentropy.iso8583_adapter_core.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

public class SecurityUtil {
    /**
     * Calculates HMAC. 
     * If the key contains only hex characters and is long, it decodes it as hex.
     * Otherwise, treats it as a raw ASCII string.
     */
    public static byte[] calculateMac(byte[] data, String key, String algorithm) throws Exception {
        if (key == null) throw new IllegalArgumentException("MAC Key cannot be null");
        
        byte[] keyBytes;
        // Logic: If it looks like a hex key (length > 32 and hex-only), decode it.
        if (key.matches("^[0-9a-fA-F]{32,}$")) {
            keyBytes = IsoUtil.hexToBytes(key);
        } else {
            keyBytes = key.getBytes(StandardCharsets.US_ASCII);
        }

        Mac hmac = Mac.getInstance(algorithm);
        SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, algorithm);
        hmac.init(secretKeySpec);
        return hmac.doFinal(data);
    }
}