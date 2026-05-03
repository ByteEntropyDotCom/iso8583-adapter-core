package com.byteentropy.iso8583_adapter_core.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SecurityUtilTest {
    private final String KEY = "test-secret-key";
    private final String ALGO = "HmacSHA256";

    @Test
    void testMacConsistency() throws Exception {
        byte[] data = "MTI0200BITMAPDATA".getBytes();
        byte[] mac1 = SecurityUtil.calculateMac(data, KEY, ALGO);
        byte[] mac2 = SecurityUtil.calculateMac(data, KEY, ALGO);
        
        assertArrayEquals(mac1, mac2, "MAC must be deterministic for same data/key");
    }

    @Test
    void testMacFailureOnTamper() throws Exception {
        byte[] data = "OriginalData".getBytes();
        byte[] tamperedData = "Or1ginalData".getBytes();
        
        byte[] mac1 = SecurityUtil.calculateMac(data, KEY, ALGO);
        byte[] mac2 = SecurityUtil.calculateMac(tamperedData, KEY, ALGO);
        
        assertNotEquals(IsoUtil.bytesToHex(mac1), IsoUtil.bytesToHex(mac2));
    }
}