package com.byteentropy.iso8583_adapter_core.config;

import com.byteentropy.iso8583_adapter_core.model.FieldDefinition;
import com.byteentropy.iso8583_adapter_core.decoder.Iso8583Decoder.FieldType;
import java.util.Map;
import java.util.HashMap;

public class IsoFieldRegistry {
    private static final Map<Integer, FieldDefinition> SCHEMA = new HashMap<>();

    static {
        SCHEMA.put(2,  new FieldDefinition(2,  FieldType.LLVAR, 19, "PAN"));
        SCHEMA.put(3,  new FieldDefinition(3,  FieldType.FIXED, 6,  "Processing Code"));
        SCHEMA.put(4,  new FieldDefinition(4,  FieldType.FIXED, 12, "Amount"));
        SCHEMA.put(7,  new FieldDefinition(7,  FieldType.FIXED, 10, "Transmission Date"));
        SCHEMA.put(11, new FieldDefinition(11, FieldType.FIXED, 6,  "STAN"));
        SCHEMA.put(12, new FieldDefinition(12, FieldType.FIXED, 6,  "Local Time"));
        SCHEMA.put(39, new FieldDefinition(39, FieldType.FIXED, 2,  "Response Code"));
        
        // Field 48 must be LLLVAR for the test to pass
        SCHEMA.put(48, new FieldDefinition(48, FieldType.LLLVAR, 999, "Additional Data"));
    }

    public static FieldDefinition getDefinition(int fieldNumber) {
        return SCHEMA.get(fieldNumber);
    }
}