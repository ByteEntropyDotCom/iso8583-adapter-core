package com.byteentropy.iso8583_adapter_core.model;

import com.byteentropy.iso8583_adapter_core.decoder.Iso8583Decoder.FieldType;

public record FieldDefinition(
    int fieldNumber,
    FieldType type,
    int length,
    String description
) {}