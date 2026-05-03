package com.byteentropy.iso8583_adapter_core.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

public class SoftwareMacStrategy implements MacStrategy {
    @Override
    public byte[] calculate(byte[] data, String key, String algorithm) throws Exception {
        byte[] keyBytes;
        if (key.matches("^[0-9a-fA-F]{32,}$")) {
            keyBytes = IsoUtil.hexToBytes(key);
        } else {
            keyBytes = key.getBytes(StandardCharsets.US_ASCII);
        }

        Mac hmac = Mac.getInstance(algorithm);
        hmac.init(new SecretKeySpec(keyBytes, algorithm));
        return hmac.doFinal(data);
    }
}