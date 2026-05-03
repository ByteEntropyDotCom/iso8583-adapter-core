package com.byteentropy.iso8583_adapter_core.service;

import com.byteentropy.iso8583_adapter_core.client.VaultClient;
import com.byteentropy.iso8583_adapter_core.config.IsoFieldRegistry;
import com.byteentropy.iso8583_adapter_core.config.MetricsConfig;
import com.byteentropy.iso8583_adapter_core.model.FieldDefinition;
import com.byteentropy.iso8583_adapter_core.model.IsoMessage;
import com.byteentropy.iso8583_adapter_core.util.*;
import com.byteentropy.iso8583_adapter_core.validator.IsoBusinessValidator;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Centralized Service for ISO 8583 Transaction Orchestration.
 * Handles security rules, business logic, and protocol encoding.
 */
public class IsoTransactionService {
    private static final Logger logger = LoggerFactory.getLogger(IsoTransactionService.class);
    
    private final IsoBusinessValidator validator;
    private final VaultClient vaultClient;
    private final Properties props;
    private final MacStrategy macStrategy; 
    private final String bcdPaddingStrategy;
    private final boolean binaryMti;
    private final boolean binaryBitmap;

    private final boolean vaultEnabled;
    private final boolean cardCoreEnabled;
    private final boolean bankingCoreEnabled;

    public IsoTransactionService(Properties props) {
        this.props = props;
        
        // Feature Toggles
        this.vaultEnabled = Boolean.parseBoolean(props.getProperty("adapter.vault.enabled", "false"));
        this.cardCoreEnabled = Boolean.parseBoolean(props.getProperty("adapter.card-core.enabled", "false"));
        this.bankingCoreEnabled = Boolean.parseBoolean(props.getProperty("adapter.banking-core.enabled", "false"));
        this.bcdPaddingStrategy = props.getProperty("adapter.protocol.bcd-padding", "RIGHT_ZERO");
        
        // Protocol Configs (Synced with Decoder)
        this.binaryMti = Boolean.parseBoolean(props.getProperty("adapter.protocol.mti-binary", "false"));
        this.binaryBitmap = Boolean.parseBoolean(props.getProperty("adapter.protocol.bitmap-binary", "true"));

        this.validator = new IsoBusinessValidator(
            props.getProperty("adapter.business.allowed-mtis"),
            props.getProperty("adapter.business.mandatory-fields")
        );
        
        this.vaultClient = new VaultClient(props);
        
        // Strategy Setup
        boolean useHsm = Boolean.parseBoolean(props.getProperty("adapter.security.use-hsm", "false"));
        this.macStrategy = useHsm ? new HsmMacStrategy() : new SoftwareMacStrategy();
    }

    /**
     * Entry point for the Handler: Processes the request and prepares a Response Object.
     */
    public IsoMessage processAndPrepareResponse(IsoMessage request) {
        // 1. Process the business logic and get response code
        String responseCode = process(request);
        
        // 2. Map MTI (e.g. 0200 -> 0210) - Fixed to use adapter.mti.map.default property
        String defaultMti = props.getProperty("adapter.mti.map.default", "0900");
        String respMti = props.getProperty("adapter.mti.map." + request.mti(), defaultMti);
        IsoMessage response = IsoMessage.create(respMti, null);
        
        // 3. Copy original fields (Filter out Bitmap and MAC placeholder)
        request.fields().forEach((k, v) -> {
            if (!k.equals("1") && !k.equals("128")) {
                response.fields().put(k, v);
            }
        });

        // 4. Set the resulting Response Code (Field 39)
        String respCodeField = props.getProperty("adapter.map.field.response-code", "39");
        response.fields().put(respCodeField, responseCode.getBytes(StandardCharsets.US_ASCII));
        
        return response;
    }

    /**
     * Converts an IsoMessage object into a raw byte array for network transmission.
     */
    public byte[] encodeMessage(IsoMessage msg) throws Exception {
        ByteBuf buffer = Unpooled.buffer();
        try {
            // 1. Write MTI (Synced with Decoder logic)
            if (binaryMti) {
                buffer.writeBytes(IsoUtil.stringToBcd(msg.mti(), bcdPaddingStrategy));
            } else {
                buffer.writeCharSequence(msg.mti(), StandardCharsets.US_ASCII);
            }
            
            // 2. Build and Write Bitmap (Synced with Decoder logic)
            TreeSet<Integer> activeFields = msg.fields().keySet().stream()
                    .filter(k -> !k.contains("."))
                    .map(Integer::parseInt)
                    .collect(Collectors.toCollection(TreeSet::new));
            
            byte[] bitmapBytes = BitmapUtils.createBitmap(activeFields);
            if (binaryBitmap) {
                buffer.writeBytes(bitmapBytes);
            } else {
                buffer.writeCharSequence(IsoUtil.bytesToHex(bitmapBytes).toUpperCase(), StandardCharsets.US_ASCII);
            }

            // 3. Encode Fields
            int macFieldId = Integer.parseInt(props.getProperty("adapter.security.mac-field", "128"));
            for (Integer fId : activeFields) {
                // Skip Bitmaps and MAC (MAC is added last)
                if (fId == 1 || fId == macFieldId) continue;
                
                FieldDefinition def = IsoFieldRegistry.getDefinition(fId);
                if (def == null) continue;

                // Handle Containers vs Standard Fields
                if (def.isContainer()) {
                    encodeSubFields(buffer, fId, def.subFields(), msg.fields());
                } else {
                    byte[] data = msg.fields().get(String.valueOf(fId));
                    if (data != null) {
                        writeField(buffer, def, data);
                    }
                }
            }

            // 4. Final Security Step: Outbound MAC
            if (requiresMac(msg)) {
                byte[] dataToSign = new byte[buffer.readableBytes()];
                buffer.getBytes(0, dataToSign);
                
                byte[] mac = macStrategy.calculate(
                    dataToSign, 
                    props.getProperty("adapter.security.mac-key"), 
                    props.getProperty("adapter.security.mac-algorithm", "HmacSHA256")
                );
                
                buffer.writeCharSequence(IsoUtil.bytesToHex(mac).toUpperCase(), StandardCharsets.US_ASCII);
            }

            byte[] result = new byte[buffer.readableBytes()];
            buffer.readBytes(result);
            return result;
        } finally {
            buffer.release();
        }
    }

    /**
     * Core orchestration logic for inbound messages.
     */
    public String process(IsoMessage msg) {
        logger.info("Inbound Processing Started: {}", msg);

        // --- STAGE 1: SECURITY & VALIDATION ---
        if (requiresMac(msg)) {
            if (!verifyInboundMac(msg)) {
                logger.warn("MAC Verification Failed for MTI {}", msg.mti());
                if (Boolean.parseBoolean(props.getProperty("adapter.security.strict-mode", "true"))) {
                    return props.getProperty("adapter.response.mac-error-code", "A0");
                }
            }
        }

        var validation = validator.validate(msg);
        if (!validation.isValid()) {
            // FIX: Increment validation error metric
            MetricsConfig.validationErrorCounter.increment();
            return validation.responseCode();
        }

        // --- STAGE 2: DATA TOKENIZATION ---
        if (vaultEnabled) {
            try {
                tokenizeSensitiveFields(msg);
            } catch (Exception e) {
                logger.error("Tokenization Service Error: {}", e.getMessage());
                return props.getProperty("adapter.response.system-error-code", "96");
            }
        }

        // --- STAGE 3: CORE AUTHENTICATION ---
        boolean isFinancial = !msg.mti().startsWith("08");

        if (cardCoreEnabled && isFinancial) {
            String cardStatus = checkCardCoreStatus(msg);
            if (!"00".equals(cardStatus)) return cardStatus; 
        }

        if (bankingCoreEnabled && isFinancial) {
            String bankingStatus = authorizeWithBankingCore(msg);
            if (!"00".equals(bankingStatus)) return bankingStatus;
        }

        return "00";
    }

    /**
     * Standard error message generator for fallbacks.
     */
    public IsoMessage createErrorResponse(IsoMessage request, String errorCode) {
        IsoMessage error = IsoMessage.create("0900", null);
        String respCodeField = props.getProperty("adapter.map.field.response-code", "39");
        error.fields().put(respCodeField, errorCode.getBytes(StandardCharsets.US_ASCII));
        return error;
    }

    // --- PRIVATE UTILITIES ---

    private void encodeSubFields(ByteBuf buf, int parentId, List<FieldDefinition> subDefs, Map<String, byte[]> fields) {
        if (subDefs == null || subDefs.isEmpty()) return;
        
        for (FieldDefinition subDef : subDefs) {
            String subKey = parentId + "." + subDef.id();
            byte[] subData = fields.get(subKey);
            if (subData != null) {
                writeField(buf, subDef, subData);
            }
        }
    }

    private void writeField(ByteBuf buf, FieldDefinition def, byte[] data) {
        int logicLen = (def.encoding() == FieldDefinition.Encoding.BCD) ? data.length * 2 : data.length;
        switch (def.type()) {
            case LLVAR -> writeLength(buf, logicLen, 2, def.encoding());
            case LLLVAR -> writeLength(buf, logicLen, 3, def.encoding());
            case TLV -> {
                writeLength(buf, def.id(), 3, FieldDefinition.Encoding.ASCII);
                writeLength(buf, logicLen, 3, FieldDefinition.Encoding.ASCII);
            }
            case FIXED -> {}
        }
        buf.writeBytes(data);
    }

    private void writeLength(ByteBuf buf, int len, int width, FieldDefinition.Encoding encoding) {
        String lenStr = String.format("%0" + width + "d", len);
        if (encoding == FieldDefinition.Encoding.BCD) {
            buf.writeBytes(IsoUtil.stringToBcd(lenStr, bcdPaddingStrategy));
        } else {
            buf.writeCharSequence(lenStr, StandardCharsets.US_ASCII);
        }
    }

    private void tokenizeSensitiveFields(IsoMessage msg) throws Exception {
        String panFieldKey = props.getProperty("adapter.map.field.pan", "2");
        byte[] panData = msg.fields().get(panFieldKey);
        if (panData != null) {
            String rawPan = new String(panData, StandardCharsets.US_ASCII);
            String tokenized = vaultClient.tokenize("{\"pan\":\"" + rawPan + "\"}");
            msg.fields().put(panFieldKey, tokenized.getBytes(StandardCharsets.US_ASCII));
        }
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
            return false;
        }
    }

    private boolean requiresMac(IsoMessage msg) {
        boolean macEnabled = Boolean.parseBoolean(props.getProperty("adapter.security.mac-enabled", "false"));
        boolean isNetworkMsg = msg.mti() != null && msg.mti().startsWith("08");
        return macEnabled && !isNetworkMsg;
    }

    private String checkCardCoreStatus(IsoMessage msg) { return "00"; }
    private String authorizeWithBankingCore(IsoMessage msg) { return "00"; }
}