package com.byteentropy.iso8583_adapter;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class Iso8583AdapterApplicationTests {

    @Test
    void simpleSanityCheck() {
        // Verifies the test framework is running
        assertDoesNotThrow(() -> System.out.println("Test environment is ready."));
    }
}