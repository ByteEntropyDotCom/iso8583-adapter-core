package com.byteentropy.iso8583_adapter_core.handler;

import com.byteentropy.iso8583_adapter_core.config.IsoFieldRegistry;
import com.byteentropy.iso8583_adapter_core.config.MetricsConfig;
import com.byteentropy.iso8583_adapter_core.model.FieldDefinition;
import com.byteentropy.iso8583_adapter_core.model.IsoMessage;
import com.byteentropy.iso8583_adapter_core.service.IsoTransactionService;
import com.byteentropy.iso8583_adapter_core.util.BitmapUtils;
import com.byteentropy.iso8583_adapter_core.util.HsmMacStrategy;
import com.byteentropy.iso8583_adapter_core.util.IsoUtil;
import com.byteentropy.iso8583_adapter_core.util.MacStrategy;
import com.byteentropy.iso8583_adapter_core.util.SoftwareMacStrategy;

import io.micrometer.core.instrument.Timer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.ChannelHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * High-performance ISO 8583 Handler.
 * Supports recursive sub-fields, LLVAR/LLLVAR, and configurable BCD padding.
 */
@ChannelHandler.Sharable
public class Iso8583ServerHandler extends SimpleChannelInboundHandler<IsoMessage> {
    private static final Logger logger = LoggerFactory.getLogger(Iso8583ServerHandler.class);
    private static final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    
    private final IsoTransactionService txService;
    private final Properties props;
    private final String bcdPaddingStrategy;
    private final MacStrategy macStrategy;

    public Iso8583ServerHandler(Properties props) {
        this.props = props;
        this.txService = new IsoTransactionService(props);

        // LOAD FROM PROPERTY: "RIGHT_ZERO" (standard) or "LEFT_F" (legacy)
        this.bcdPaddingStrategy = props.getProperty("adapter.protocol.bcd-padding", "RIGHT_ZERO");
        
        // ALIGN WITH SERVICE
        boolean useHsm = Boolean.parseBoolean(props.getProperty("adapter.security.use-hsm", "false"));
        this.macStrategy = useHsm ? new HsmMacStrategy() : new SoftwareMacStrategy();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, IsoMessage msg) {

        logger.info("Inbound ISO Request: [MTI: {}, Remote: {}]", msg.mti(), ctx.channel().remoteAddress());
        
        Timer.Sample sample = Timer.start(MetricsConfig.registry);
        int timeout = Integer.parseInt(props.getProperty("adapter.timeout.total-threshold-ms", "180"));
        String systemError = props.getProperty("adapter.response.system-error-code", "96");

        CompletableFuture.runAsync(() -> {
            try {
                    String responseCode = txService.process(msg);
                    respond(ctx, msg, responseCode);
                    MetricsConfig.txnCounter.increment();
            } catch (Exception e) {
                    logger.error("Critical error processing transaction: {}", e.getMessage(), e);
                    respond(ctx, msg, systemError);
            }

        
        }, executor)
        .orTimeout(timeout, TimeUnit.MILLISECONDS)
        .exceptionally(ex -> {
            logger.error("SLA Violation: {}", ex.getMessage());
            respond(ctx, msg, props.getProperty("adapter.response.system-error-code", "96"));
            MetricsConfig.errorCounter.increment();
            return null;
        })
        .whenComplete((res, ex) -> sample.stop(MetricsConfig.txnTimer));
    }

    private void respond(ChannelHandlerContext ctx, IsoMessage msg, String code) {
        
        
        String respCodeField = props.getProperty("adapter.map.field.response-code", "39");
        msg.fields().put(respCodeField, code.getBytes(StandardCharsets.US_ASCII));
        
        ByteBuf body = ctx.alloc().buffer();
        try {
            // 1. MTI
            String respMti = props.getProperty("adapter.mti.map." + msg.mti(), "0900");
            body.writeCharSequence(respMti, StandardCharsets.US_ASCII);
            
            // 2. Bitmap
            TreeSet<Integer> activeFields = msg.fields().keySet().stream()
                    .filter(k -> !k.contains("."))
                    .map(Integer::parseInt)
                    .collect(Collectors.toCollection(TreeSet::new));
            body.writeBytes(BitmapUtils.createBitmap(activeFields));

            // 3. Fields
            int macField = Integer.parseInt(props.getProperty("adapter.security.mac-field", "128"));
            for (Integer fId : activeFields) {
                if (fId == 1 || fId == macField) continue;
                
                FieldDefinition def = IsoFieldRegistry.getDefinition(fId);
                if (def == null) continue;

                byte[] fieldData = def.isContainer() ? 
                    encodeSubFields(fId, msg.fields(), def.subFields()) : 
                    msg.fields().get(String.valueOf(fId));

                if (fieldData != null) {
                    writeField(body, def, fieldData);
                }
            }

            // 4. MAC

            boolean macEnabled = Boolean.parseBoolean(props.getProperty("adapter.security.mac-enabled"));
            boolean isNetworkResponse = respMti.startsWith("08");

            if (macEnabled && !isNetworkResponse) {
                byte[] dataToSign = new byte[body.readableBytes()];
                body.getBytes(0, dataToSign);

                byte[] macRaw = macStrategy.calculate(
                                    dataToSign, 
                                    props.getProperty("adapter.security.mac-key"), 
                                    props.getProperty("adapter.security.mac-algorithm", "HmacSHA256")
                                );
                                
                body.writeCharSequence(IsoUtil.bytesToHex(macRaw), StandardCharsets.US_ASCII);
            
            }

            // 5. Length Header (TCP Framing)
            ByteBuf frame = ctx.alloc().buffer(body.readableBytes() + 2);
            frame.writeShort(body.readableBytes());
            frame.writeBytes(body);
            ctx.writeAndFlush(frame);

        } catch (Exception e) {
            logger.error("Response assembly failed", e);
        } finally {
            body.release();
        }
    }

    private byte[] encodeSubFields(int parentId, Map<String, byte[]> allFields, List<FieldDefinition> subDefs) {
        ByteBuf subBody = Unpooled.buffer();
        try {
            for (FieldDefinition subDef : subDefs) {
                byte[] data = allFields.get(parentId + "." + subDef.id());
                if (data != null) {
                    writeField(subBody, subDef, data);
                }
            }
            byte[] result = new byte[subBody.readableBytes()];
            subBody.readBytes(result);
            return result;
        } finally {
            subBody.release();
        }
    }

    private void writeField(ByteBuf buf, FieldDefinition def, byte[] data) {
        // Calculate logic length: BCD stores 2 digits per byte
        int logicLen = (def.encoding() == FieldDefinition.Encoding.BCD) ? data.length * 2 : data.length;
        
        switch (def.type()) {
            case LLVAR -> writeLength(buf, logicLen, 2, def.encoding());
            case LLLVAR -> writeLength(buf, logicLen, 3, def.encoding());
            case TLV -> {
                writeLength(buf, def.id(), 3, FieldDefinition.Encoding.ASCII); // Tag
                writeLength(buf, logicLen, 3, FieldDefinition.Encoding.ASCII); // Length
            }
            case FIXED -> {} 
        }
        buf.writeBytes(data);
    }

    private void writeLength(ByteBuf buf, int len, int width, FieldDefinition.Encoding encoding) {
        String format = "%0" + width + "d";
        String lenStr = String.format(format, len);
        
        if (encoding == FieldDefinition.Encoding.BCD) {
            // Uses the strategy-aware BCD conversion
            buf.writeBytes(IsoUtil.stringToBcd(lenStr, bcdPaddingStrategy));
        } else {
            buf.writeCharSequence(lenStr, StandardCharsets.US_ASCII);
        }
    }
}