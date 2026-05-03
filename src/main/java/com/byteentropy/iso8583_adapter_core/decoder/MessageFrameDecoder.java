package com.byteentropy.iso8583_adapter_core.decoder;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.TooLongFrameException;
import java.util.List;

public class MessageFrameDecoder extends ByteToMessageDecoder {
    private static final int MAX_FRAME_LENGTH = 2048; // Standard ISO limit

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (in.readableBytes() < 2) return;

        in.markReaderIndex();
        int length = in.readUnsignedShort();

        if (length > MAX_FRAME_LENGTH || length <= 0) {
            throw new TooLongFrameException("Invalid ISO frame length: " + length);
        }

        if (in.readableBytes() < length) {
            in.resetReaderIndex();
            return;
        }

        out.add(in.readRetainedSlice(length));
    }
}