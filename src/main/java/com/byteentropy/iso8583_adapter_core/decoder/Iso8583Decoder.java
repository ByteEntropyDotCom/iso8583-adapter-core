package com.byteentropy.iso8583_adapter_core.decoder;

import com.byteentropy.iso8583_adapter_core.config.IsoFieldRegistry;
import com.byteentropy.iso8583_adapter_core.model.FieldDefinition;
import com.byteentropy.iso8583_adapter_core.model.IsoMessage;
import com.byteentropy.iso8583_adapter_core.util.IsoUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

/**
 * Optimized ISO 8583 Decoder.
 * Fully synchronized with ServerHandler padding strategies and recursive field structures.
 */
public class Iso8583Decoder extends MessageToMessageDecoder<ByteBuf> {
    private static final Logger logger = LoggerFactory.getLogger(Iso8583Decoder.class);
    private final boolean binaryMti;
    private final boolean binaryBitmap;
    private final int macLength;
    private final String bcdPaddingStrategy;

    public Iso8583Decoder(Properties props) {
        this.binaryMti = Boolean.parseBoolean(props.getProperty("adapter.protocol.mti-binary", "false"));
        this.binaryBitmap = Boolean.parseBoolean(props.getProperty("adapter.protocol.bitmap-binary", "true"));
        this.macLength = Integer.parseInt(props.getProperty("adapter.security.mac-length", "64"));
        this.bcdPaddingStrategy = props.getProperty("adapter.protocol.bcd-padding", "RIGHT_ZERO");
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) {
        // Capture raw body for MAC validation (everything except the MAC itself)
        byte[] rawBody = new byte[Math.max(0, msg.readableBytes() - macLength)];
        msg.getBytes(msg.readerIndex(), rawBody);

        try {
            // 1. Decode MTI
            String mti = binaryMti ? IsoUtil.bcdToString(readBytes(msg, 2)) 
                                   : msg.readCharSequence(4, StandardCharsets.US_ASCII).toString();

            IsoMessage isoMessage = IsoMessage.create(mti, rawBody);

            // 2. Decode Primary Bitmap
            byte[] primary = readBitmap(msg);
            parseRange(msg, isoMessage, primary, 1, 64);

            // 3. Decode Secondary Bitmap if bit 1 is set
            if ((primary[0] & 0x80) != 0) {
                byte[] secondary = readBitmap(msg);
                parseRange(msg, isoMessage, secondary, 65, 128);
            }
            
            out.add(isoMessage);
        } catch (Exception e) {
            logger.error("Decoding failure: {}", e.getMessage(), e);
            ctx.close();
        }
    }

    private void parseRange(ByteBuf buf, IsoMessage iso, byte[] bitmap, int start, int end) {
        for (int i = 1; i <= 64; i++) {
            int fieldNum = start + i - 1;
            if (fieldNum == 1 || fieldNum == 65) continue; // Bitmaps already handled

            int bitOffset = i - 1;
            if ((bitmap[bitOffset / 8] & (0x80 >> (bitOffset % 8))) != 0) {
                FieldDefinition def = IsoFieldRegistry.getDefinition(fieldNum);
                if (def == null) {
                    logger.warn("No definition for field {}, skipping...", fieldNum);
                    continue;
                }
                
                byte[] data = readFieldData(buf, def);
                iso.setField(fieldNum, data);

                // Recursive support for sub-fields
                if (def.isContainer()) {
                    decodeSubFields(iso, fieldNum, data, def.subFields());
                }
            }
        }
    }

    private void decodeSubFields(IsoMessage iso, int parentId, byte[] data, List<FieldDefinition> subDefs) {
        ByteBuf subBuf = Unpooled.wrappedBuffer(data);
        try {
            for (FieldDefinition subDef : subDefs) {
                if (!subBuf.isReadable()) break;
                byte[] subData = readFieldData(subBuf, subDef);
                iso.setSubField(parentId, subDef.id(), subData);
            }
        } finally {
            subBuf.release();
        }
    }

    private byte[] readFieldData(ByteBuf buf, FieldDefinition def) {
        int dataLen = switch (def.type()) {
            case FIXED -> def.length();
            case LLVAR -> readVarLength(buf, 1, 2, def.encoding());
            case LLLVAR -> readVarLength(buf, 2, 3, def.encoding());
            case TLV -> {
                buf.readCharSequence(3, StandardCharsets.US_ASCII); // Skip Tag
                yield Integer.parseInt(buf.readCharSequence(3, StandardCharsets.US_ASCII).toString());
            }
        };

        int bytesToRead = (def.encoding() == FieldDefinition.Encoding.BCD) ? (dataLen + 1) / 2 : dataLen;
        byte[] data = new byte[bytesToRead];
        buf.readBytes(data);
        return data;
    }

    /**
     * Reads variable length headers and applies bcdPaddingStrategy logic.
     */
    private int readVarLength(ByteBuf buf, int bcdBytes, int asciiBytes, FieldDefinition.Encoding encoding) {
        if (encoding == FieldDefinition.Encoding.BCD) {
            String lenStr = IsoUtil.bcdToString(readBytes(buf, bcdBytes));
            
            // Apply Strategy Logic
            if ("LEFT_F".equalsIgnoreCase(bcdPaddingStrategy)) {
                // If LEFT_F, the trailing nibble might be 'F' for odd lengths
                if (lenStr.toUpperCase().endsWith("F")) {
                    lenStr = lenStr.substring(0, lenStr.length() - 1);
                }
            } else {
                // Default: RIGHT_ZERO (Leading zero)
                // Integer.parseInt naturally ignores leading zeros
            }
            return Integer.parseInt(lenStr);
        }
        // Fallback for ASCII length headers
        return Integer.parseInt(buf.readCharSequence(asciiBytes, StandardCharsets.US_ASCII).toString());
    }

    private byte[] readBitmap(ByteBuf buf) {
        byte[] b = new byte[8];
        if (binaryBitmap) {
            buf.readBytes(b);
        } else {
            String hex = buf.readCharSequence(16, StandardCharsets.US_ASCII).toString();
            byte[] decoded = IsoUtil.hexToBytes(hex);
            System.arraycopy(decoded, 0, b, 0, 8);
        }
        return b;
    }

    private byte[] readBytes(ByteBuf buf, int len) {
        byte[] b = new byte[len];
        buf.readBytes(b);
        return b;
    }
}