package com.byteentropy.iso8583_adapter_core.handler;

import com.byteentropy.iso8583_adapter_core.model.IsoMessage;
import com.byteentropy.iso8583_adapter_core.model.FieldDefinition;
import com.byteentropy.iso8583_adapter_core.config.IsoFieldRegistry;
import com.byteentropy.iso8583_adapter_core.util.BitmapUtils;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.TreeSet;

/**
 * Handles ISO 8583 messages using Java 21 Virtual Threads.
 * Optimized for high-throughput and low-latency financial transactions.
 */
public class Iso8583ServerHandler extends SimpleChannelInboundHandler<IsoMessage> {
    
    private static final Logger logger = LoggerFactory.getLogger(Iso8583ServerHandler.class);
    
    // Virtual Thread Executor: Scalable beyond platform thread limits
    private static final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, IsoMessage msg) {
        // Offload the processing to a Virtual Thread to keep Netty's EventLoop free
        executor.submit(() -> {
            // Allocate pooled buffer from Netty's allocator
            ByteBuf body = ctx.alloc().buffer(); 
            try {
                logger.info("Processing MTI: {}", msg.mti());

                // 1. Transaction Logic: Set Response Code "00" (Approved)
                // In a real app, this is where you'd call your DB or HSM
                msg.fields().put(39, "00".getBytes(StandardCharsets.US_ASCII));
                
                // MTI Conversion: Request (e.g., 0200) -> Response (e.g., 0210)
                char[] mtiChars = msg.mti().toCharArray();
                if (mtiChars.length >= 3) {
                    mtiChars[2] = (mtiChars[2] == '0') ? '1' : (char)(mtiChars[2] + 1);
                }
                String resMti = new String(mtiChars);

                // 2. Build Response Body
                body.writeCharSequence(resMti, StandardCharsets.US_ASCII);
                
                // Fields MUST be encoded in ascending numerical order
                TreeSet<Integer> sortedFields = new TreeSet<>(msg.fields().keySet());
                
                // Generate and write Bitmap (Binary format)
                byte[] bitmap = BitmapUtils.createBitmap(sortedFields);
                body.writeBytes(bitmap);

                // Encode Fields
                for (Integer fId : sortedFields) {
                    if (fId == 1) continue; // Skip as Bitmap is already written
                    
                    byte[] data = msg.fields().get(fId);
                    FieldDefinition def = IsoFieldRegistry.getDefinition(fId);
                    
                    if (def == null) {
                        logger.warn("Skipping field {}: No definition found in registry", fId);
                        continue;
                    }

                    // Handle variable length headers
                    switch (def.type()) {
                        case LLVAR -> body.writeCharSequence(String.format("%02d", data.length), StandardCharsets.US_ASCII);
                        case LLLVAR -> body.writeCharSequence(String.format("%03d", data.length), StandardCharsets.US_ASCII);
                        case FIXED -> { /* No header needed */ }
                    }
                    body.writeBytes(data);
                }

                // 3. Final Framing: [Length (2 bytes)][Body]
                ByteBuf responseFrame = ctx.alloc().buffer(body.readableBytes() + 2);
                responseFrame.writeShort(body.readableBytes());
                responseFrame.writeBytes(body);

                // Write to wire and flush
                ctx.writeAndFlush(responseFrame);
                
            } catch (Exception e) {
                logger.error("Error processing transaction {}: ", msg.mti(), e);
                // In production, send a '96' (System Malfunction) response here if possible
            } finally {
                // Manually release the body buffer as it was manually allocated
                if (body.refCnt() > 0) {
                    body.release();
                }
            }
        });
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("Connection exception: {}", cause.getMessage());
        ctx.close();
    }
}