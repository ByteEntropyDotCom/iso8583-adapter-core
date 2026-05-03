package com.byteentropy.iso8583_adapter_core.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record FieldDefinition(
    @JsonProperty("id") int id,
    @JsonProperty("type") FieldType type,
    @JsonProperty("encoding") Encoding encoding,
    @JsonProperty("length") int length,
    @JsonProperty("name") String name,
    @JsonProperty("subFields") List<FieldDefinition> subFields
) {
    public enum FieldType { FIXED, LLVAR, LLLVAR, TLV }
    
    public enum Encoding { ASCII, BCD, BINARY }

    public boolean isContainer() {
        return subFields != null && !subFields.isEmpty();
    }
}