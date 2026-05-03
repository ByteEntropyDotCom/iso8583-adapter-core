package com.byteentropy.iso8583_adapter_core.service;

import com.byteentropy.iso8583_adapter_core.client.VaultClient;
import com.byteentropy.iso8583_adapter_core.model.IsoMessage;
import com.byteentropy.iso8583_adapter_core.util.IsoUtil;
import com.byteentropy.iso8583_adapter_core.util.SecurityUtil;
import com.byteentropy.iso8583_adapter_core.validator.IsoBusinessValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Properties;

public class IsoTransactionService {
    private static final Logger logger = LoggerFactory.getLogger(IsoTransactionService.class);
    private final IsoBusinessValidator validator;
    private final VaultClient vaultClient;
    private final Properties props;
    private final boolean vaultEnabled;

    public IsoTransactionService(Properties props) {
        this.props = props;
        this.vaultEnabled = Boolean.parseBoolean(props.getProperty("adapter.vault.enabled", "false"));
        this.validator = new IsoBusinessValidator(
            props.getProperty("adapter.business.allowed-mtis"),
            props.getProperty("adapter.business.mandatory-fields")
        );
        this.vaultClient = new VaultClient(props);
    }

    public String process(IsoMessage msg) {
        // 1. MAC Verification
        if (Boolean.parseBoolean(props.getProperty("adapter.security.mac-enabled", "false"))) {
            if (!verifyInboundMac(msg)) {
                if (Boolean.parseBoolean(props.getProperty("adapter.security.strict-mode", "true"))) {
                    return props.getProperty("adapter.response.mac-error-code", "A0");
                }
            }
        }

        // 2. Business Validation
        var validation = validator.validate(msg);
        if (!validation.isValid()) return validation.responseCode();

        // 3. Vault Integration Logic (Now using String keys for Map)
        if (vaultEnabled) {
            try {
                // Fetch the PAN field number from properties (default to 2 if not set)
                String panFieldKey = props.getProperty("adapter.map.field.pan", "2");
                byte[] panData = msg.fields().get(panFieldKey);
                
                if (panData != null) {
                    String rawPan = new String(panData, StandardCharsets.US_ASCII);
                    String tokenized = vaultClient.tokenize("{\"pan\":\"" + rawPan + "\"}");
                    msg.fields().put(panFieldKey, tokenized.getBytes(StandardCharsets.US_ASCII));
                }
                
                // Example: How to process a sub-field from Field 48 (e.g. 48.1)
                // String subFieldKey = "48.1";
                // byte[] subData = msg.fields().get(subFieldKey);
                // if(subData != null) { ... tokenize ... }

            } catch (Exception e) {
                logger.error("Vault integration failed: {}", e.getMessage());
                return props.getProperty("adapter.response.system-error-code", "96");
            }
        }

        return "00";
    }

    private boolean verifyInboundMac(IsoMessage msg) {
        try {
            String macFieldKey = props.getProperty("adapter.security.mac-field", "128");
            byte[] receivedMacHex = msg.fields().get(macFieldKey);
            if (receivedMacHex == null) return false;

            byte[] receivedBytes = IsoUtil.hexToBytes(new String(receivedMacHex, StandardCharsets.US_ASCII));
            byte[] calculated = SecurityUtil.calculateMac(
                msg.rawBody(), 
                props.getProperty("adapter.security.mac-key"), 
                props.getProperty("adapter.security.mac-algorithm")
            );
            return Arrays.equals(receivedBytes, calculated);
        } catch (Exception e) {
            return false;
        }
    }
}