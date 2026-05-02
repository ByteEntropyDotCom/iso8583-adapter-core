package com.byteentropy.iso8583_adapter_core.util;

import org.apache.commons.codec.binary.Hex;
import java.util.Set;

public class BitmapUtils {
    
    public static byte[] createBitmap(Set<Integer> activeFields) {
        boolean hasSecondary = activeFields.stream().anyMatch(f -> f > 64);
        byte[] bitmap = new byte[hasSecondary ? 16 : 8];

        if (hasSecondary) {
            bitmap[0] |= 0x80;
        }

        for (Integer field : activeFields) {
            if (field <= 1 || field > 128) continue; 
            
            int offset = field - 1;
            int byteIdx = offset / 8;
            int bitIdx = 7 - (offset % 8);
            bitmap[byteIdx] |= (1 << bitIdx);
        }
        return bitmap;
    }

    /**
     * Converts a binary bitmap to a Hex-ASCII string (e.g. for specific protocols or logging)
     */
    public static String bytesToHex(byte[] bytes) {
        return Hex.encodeHexString(bytes).toUpperCase();
    }

    /**
     * Converts a Hex-ASCII string back to binary bytes
     */
    public static byte[] hexToBytes(String hex) throws Exception {
        return Hex.decodeHex(hex);
    }
}