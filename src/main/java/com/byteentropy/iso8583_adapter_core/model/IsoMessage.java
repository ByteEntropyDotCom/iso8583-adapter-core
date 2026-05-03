package com.byteentropy.iso8583_adapter_core.model;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public record IsoMessage(String mti, Map<String, byte[]> fields, byte[] rawBody) {
    public static IsoMessage create(String mti, byte[] rawBody) {
        return new IsoMessage(mti, new ConcurrentHashMap<>(), rawBody);
    }

    // Helper for main fields
    public void setField(int fieldId, byte[] data) {
        fields.put(String.valueOf(fieldId), data);
    }

    // Helper for sub-fields (e.g., 48.1)
    public void setSubField(int parentId, int subId, byte[] data) {
        fields.put(parentId + "." + subId, data);
    }

    public byte[] getField(int fieldId) {
        return fields.get(String.valueOf(fieldId));
    }
}