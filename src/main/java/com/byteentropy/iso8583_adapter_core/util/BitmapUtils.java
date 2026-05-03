package com.byteentropy.iso8583_adapter_core.util;

import java.util.TreeSet;

public class BitmapUtils {
    public static byte[] createBitmap(TreeSet<Integer> activeFields) {
        // If any field > 64 exists, we MUST have 16 bytes (128 bits)
        boolean hasSecondary = activeFields.stream().anyMatch(f -> f > 64);
        byte[] bitmap = new byte[hasSecondary ? 16 : 8];

        for (Integer field : activeFields) {
            if (field == 1) continue; // Bit 1 is the secondary bitmap indicator
            if (field > 128) continue; 
            
            int byteIndex = (field - 1) / 8;
            int bitIndex = (field - 1) % 8;
            bitmap[byteIndex] |= (0x80 >> bitIndex);
        }

        if (hasSecondary) {
            bitmap[0] |= 0x80; // Explicitly set Bit 1
        }
        
        return bitmap;
    }
}