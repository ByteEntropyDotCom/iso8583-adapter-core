package com.byteentropy.iso8583_adapter_core.service;

import com.byteentropy.iso8583_adapter_core.client.VaultClient;
import com.byteentropy.iso8583_adapter_core.model.IsoMessage;
import com.byteentropy.iso8583_adapter_core.util.*;
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
    private final MacStrategy macStrategy; 

    // Feature Toggles (Pluggable via Properties)
    private final boolean vaultEnabled;      // tokenizer-vault-core
    private final boolean cardCoreEnabled;   // card-core (Status/Limit check)
    private final boolean bankingCoreEnabled; // core-banking-core (Balance check)

    public IsoTransactionService(Properties props) {
        this.props = props;
        
        // Load configurations
        this.vaultEnabled = Boolean.parseBoolean(props.getProperty("adapter.vault.enabled", "false"));
        this.cardCoreEnabled = Boolean.parseBoolean(props.getProperty("adapter.card-core.enabled", "false"));
        this.bankingCoreEnabled = Boolean.parseBoolean(props.getProperty("adapter.banking-core.enabled", "false"));

        this.validator = new IsoBusinessValidator(
            props.getProperty("adapter.business.allowed-mtis"),
            props.getProperty("adapter.business.mandatory-fields")
        );
        
        this.vaultClient = new VaultClient(props);
        
        boolean useHsm = Boolean.parseBoolean(props.getProperty("adapter.security.use-hsm", "false"));
        this.macStrategy = useHsm ? new HsmMacStrategy() : new SoftwareMacStrategy();
    }

    /**
     * Orchestrates the transaction through the security and core banking pipeline.
     */
    public String process(IsoMessage msg) {
        // SAFE LOGGING: msg.toString() uses masking from IsoMessage record
        logger.info("Inbound Processing Started: {}", msg);

        boolean isNetworkMsg = msg.mti().startsWith("08");
        boolean macEnabled = Boolean.parseBoolean(props.getProperty("adapter.security.mac-enabled", "false"));

        // --- STAGE 1: TECHNICAL & SECURITY VALIDATION ---
        
        // 1.1 MAC Verification
        if (macEnabled && !isNetworkMsg) {
            if (!verifyInboundMac(msg)) {
                logger.warn("MAC Failure on MTI {}", msg.mti());
                if (Boolean.parseBoolean(props.getProperty("adapter.security.strict-mode", "true"))) {
                    return props.getProperty("adapter.response.mac-error-code", "A0");
                }
            }
        }

        // 1.2 Format & Business Validation (Field presence, Regex, etc.)
        var validation = validator.validate(msg);
        if (!validation.isValid()) {
            logger.warn("Validation failed: {} - {}", validation.responseCode(), validation.reason());
            return validation.responseCode();
        }

        // --- STAGE 2: DATA TOKENIZATION (tokenizer-vault-core) ---
        // We do this BEFORE calling other cores so they only handle tokens.
        if (vaultEnabled) {
            try {
                tokenizeSensitiveFields(msg);
            } catch (Exception e) {
                logger.error("Security Fault: Tokenization service unavailable.");
                return props.getProperty("adapter.response.system-error-code", "96");
            }
        }

        // --- STAGE 3: CARD AUTHENTICATION (card-core) ---
        // Check if card is active, blocked, or expired.
        if (cardCoreEnabled && !isNetworkMsg) {
            String cardStatus = checkCardCoreStatus(msg);
            if (!"00".equals(cardStatus)) {
                logger.warn("Card Core rejected transaction: {}", cardStatus);
                return cardStatus; 
            }
        }

        // --- STAGE 4: FINANCIAL AUTHORIZATION (core-banking-core) ---
        // Final stage: Perform the actual money movement/balance check.
        if (bankingCoreEnabled && !isNetworkMsg) {
            String bankingStatus = authorizeWithBankingCore(msg);
            if (!"00".equals(bankingStatus)) {
                logger.warn("Banking Core rejected transaction: {}", bankingStatus);
                return bankingStatus;
            }
        }

        return "00"; // All plugs returned success
    }

    private void tokenizeSensitiveFields(IsoMessage msg) throws Exception {
        String panFieldKey = props.getProperty("adapter.map.field.pan", "2");
        byte[] panData = msg.fields().get(panFieldKey);
        
        if (panData != null) {
            String rawPan = new String(panData, StandardCharsets.US_ASCII);
            // Swaps raw PAN for Token in the msg object
            String tokenized = vaultClient.tokenize("{\"pan\":\"" + rawPan + "\"}");
            msg.fields().put(panFieldKey, tokenized.getBytes(StandardCharsets.US_ASCII));
            logger.debug("Field {} replaced with Token", panFieldKey);
        }
    }

    /**
     * Future Plug: Call card-core for status/expiry checks.
     */
    private String checkCardCoreStatus(IsoMessage msg) {
        // Placeholder for HTTP/gRPC call to card-core
        // Logic: if(card.isExpired()) return "54";
        return "00";
    }

    /**
     * Future Plug: Call core-banking-core for balance/ledger updates.
     */
    private String authorizeWithBankingCore(IsoMessage msg) {
        // Placeholder for HTTP/gRPC call to core-banking-core
        // Logic: if(balance < amount) return "51";
        return "00";
    }

    private boolean verifyInboundMac(IsoMessage msg) {
        try {
            String macFieldKey = props.getProperty("adapter.security.mac-field", "128");
            byte[] receivedMacHex = msg.fields().get(macFieldKey);
            if (receivedMacHex == null) return false;

            byte[] receivedBytes = IsoUtil.hexToBytes(new String(receivedMacHex, StandardCharsets.US_ASCII));
            
            byte[] calculated = macStrategy.calculate(
                msg.rawBody(), 
                props.getProperty("adapter.security.mac-key"), 
                props.getProperty("adapter.security.mac-algorithm")
            );
            return Arrays.equals(receivedBytes, calculated);
        } catch (Exception e) {
            logger.error("MAC calculation error: {}", e.getMessage());
            return false;
        }
    }
}