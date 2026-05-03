package com.byteentropy.iso8583_adapter_core.validator;

import com.byteentropy.iso8583_adapter_core.model.IsoMessage;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Validates business rules for ISO 8583 messages.
 * Optimized with pre-compiled patterns for high-throughput processing.
 */
public class IsoBusinessValidator {
    private final Set<String> allowedMtis;
    private final Set<String> mandatoryFields;
    
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("\\d{12}");

    public IsoBusinessValidator(String mtis, String fields) {
        this.allowedMtis = (mtis == null || mtis.isBlank()) ? Collections.emptySet() :
            Arrays.stream(mtis.split(","))
                  .map(String::trim)
                  .collect(Collectors.toSet());
        
        this.mandatoryFields = (fields == null || fields.isBlank()) ? Collections.emptySet() :
            Arrays.stream(fields.split(","))
                  .map(String::trim)
                  .filter(s -> !s.isEmpty())
                  .collect(Collectors.toSet());
    }

    // UPDATED: Added 'reason' field to the record
    public record ValidationResult(boolean isValid, String responseCode, String reason) {}

    public ValidationResult validate(IsoMessage msg) {
        // 1. MTI Check - Response Code 40 (Function Not Supported)
        if (!allowedMtis.contains(msg.mti())) {
            return new ValidationResult(false, "40", "MTI " + msg.mti() + " not allowed");
        }

        // 2. Mandatory Fields Check - Response Code 30 (Format Error)
        for (String fieldKey : mandatoryFields) {
            if (!msg.fields().containsKey(fieldKey)) {
                return new ValidationResult(false, "30", "Missing mandatory field: " + fieldKey);
            }
        }

        // 3. Amount validation (Field 4) - Response Code 13 (Invalid Amount)
        byte[] amountData = msg.fields().get("4");
        if (amountData != null) {
            String amt = new String(amountData, StandardCharsets.US_ASCII);
            if (!AMOUNT_PATTERN.matcher(amt).matches()) {
                return new ValidationResult(false, "13", "Invalid amount format in Field 4");
            }
        }

        // Success Case
        return new ValidationResult(true, "00", "Success");
    }
}