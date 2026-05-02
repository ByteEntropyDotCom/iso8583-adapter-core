package com.byteentropy.iso8583_adapter.util;

import com.byteentropy.iso8583_adapter_core.util.BitmapUtils;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class BitmapUtilsTest {

    @Test
    void testPrimaryBitmapOnly() {
        // Fields 2 and 3
        // Bit 2: 01000000 (0x40)
        // Bit 3: 00100000 (0x20) -> Combined 0x60
        Set<Integer> fields = Set.of(2, 3);
        byte[] bitmap = BitmapUtils.createBitmap(fields);
        
        assertEquals(8, bitmap.length);
        assertEquals((byte) 0x60, bitmap[0]);
    }

    @Test
    void testSecondaryBitmapTrigger() {
        // Field 70 is in the secondary range
        Set<Integer> fields = Set.of(3, 70);
        byte[] bitmap = BitmapUtils.createBitmap(fields);
        
        assertEquals(16, bitmap.length);
        // Bit 1 must be set to indicate secondary bitmap
        assertTrue((bitmap[0] & 0x80) != 0);
    }
}