package com.byteentropy.iso8583_adapter_core.model;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe container for ISO 8583 message data.
 * MTI: Message Type Indicator
 * Fields: Map of Field ID to raw byte data
 */
public record IsoMessage(String mti, Map<Integer, byte[]> fields) {
    public static IsoMessage create(String mti) {
        return new IsoMessage(mti, new ConcurrentHashMap<>());
    }
}