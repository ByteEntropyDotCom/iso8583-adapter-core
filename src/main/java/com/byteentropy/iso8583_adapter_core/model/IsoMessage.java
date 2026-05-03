package com.byteentropy.iso8583_adapter_core.model;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.byteentropy.iso8583_adapter_core.util.IsoUtil;

public record IsoMessage(String mti, Map<String, byte[]> fields, byte[] rawBody) {
    
    private static final Set<String> SENSITIVE_FIELDS = Set.of("2", "35", "45");

    public static IsoMessage create(String mti, byte[] rawBody) {
        return new IsoMessage(mti, new ConcurrentHashMap<>(), rawBody);
    }

    public void setField(int fieldId, byte[] data) {
        fields.put(String.valueOf(fieldId), data);
    }

    public void setSubField(int parentId, int subId, byte[] data) {
        fields.put(parentId + "." + subId, data);
    }

    public byte[] getField(int fieldId) {
        return fields.get(String.valueOf(fieldId));
    }

    @Override
    public String toString() {
        String maskedFields = fields.entrySet().stream()
            .map(entry -> {
                String key = entry.getKey();
                String val = new String(entry.getValue(), StandardCharsets.US_ASCII);
                
                // Masking Logic
                if (SENSITIVE_FIELDS.contains(key) || key.startsWith("35") || key.startsWith("45")) {
                    return key + "=" + IsoUtil.mask(val, 6, 4);
                }
                return key + "=" + val;
            })
            .collect(Collectors.joining(", ", "{", "}"));

        return "IsoMessage[MTI=" + mti + ", Fields=" + maskedFields + "]";
    }
}