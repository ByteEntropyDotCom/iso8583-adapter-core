package com.byteentropy.iso8583_adapter_core.decoder;

import com.byteentropy.iso8583_adapter_core.model.IsoMessage;
import com.byteentropy.iso8583_adapter_core.model.FieldDefinition;
import com.byteentropy.iso8583_adapter_core.config.IsoFieldRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class Iso8583Decoder extends MessageToMessageDecoder<ByteBuf> {
    
    public enum FieldType { FIXED, LLVAR, LLLVAR }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) {
        try {
            // Requirement: 4 bytes (MTI) + 8 bytes (Primary Bitmap) = 12 bytes
            if (msg.readableBytes() < 12) {
                return; 
            }

            String mti = msg.readCharSequence(4, StandardCharsets.US_ASCII).toString();
            IsoMessage isoMessage = IsoMessage.create(mti);

            byte[] primaryBitmap = new byte[8];
            msg.readBytes(primaryBitmap);

            boolean hasSecondary = (primaryBitmap[0] & 0x80) != 0;
            parseRange(msg, isoMessage, primaryBitmap, 1, 64);

            if (hasSecondary && msg.readableBytes() >= 8) {
                byte[] secondaryBitmap = new byte[8];
                msg.readBytes(secondaryBitmap);
                parseRange(msg, isoMessage, secondaryBitmap, 65, 128);
            }

            out.add(isoMessage);
        } catch (Exception e) {
            ctx.fireExceptionCaught(e);
        }
    }

    private void parseRange(ByteBuf buf, IsoMessage iso, byte[] bitmap, int start, int end) {
        for (int i = start; i <= end; i++) {
            int bitOffset = i - start;
            int byteIdx = bitOffset / 8;
            int bitIdx = 7 - (bitOffset % 8);

            if ((bitmap[byteIdx] & (1 << bitIdx)) != 0) {
                if (i == 1 || i == 65) continue; 

                FieldDefinition def = IsoFieldRegistry.getDefinition(i);
                if (def == null) continue; 

                int actualLen;
                try {
                    actualLen = switch (def.type()) {
                        case FIXED -> def.length();
                        case LLVAR -> Integer.parseInt(buf.readCharSequence(2, StandardCharsets.US_ASCII).toString());
                        case LLLVAR -> Integer.parseInt(buf.readCharSequence(3, StandardCharsets.US_ASCII).toString());
                    };
                } catch (Exception e) {
                    // Throwing exception because we cannot determine where the next field begins
                    throw new RuntimeException("Invalid length header for field " + i, e);
                }

                if (buf.readableBytes() >= actualLen) {
                    byte[] data = new byte[actualLen];
                    buf.readBytes(data);
                    iso.fields().put(i, data);
                }
            }
        }
    }
}