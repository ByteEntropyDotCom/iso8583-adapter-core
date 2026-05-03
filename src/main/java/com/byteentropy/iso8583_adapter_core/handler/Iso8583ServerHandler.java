package com.byteentropy.iso8583_adapter_core.handler;

import com.byteentropy.iso8583_adapter_core.config.MetricsConfig;
import com.byteentropy.iso8583_adapter_core.model.IsoMessage;
import com.byteentropy.iso8583_adapter_core.service.IsoTransactionService;
import io.micrometer.core.instrument.Timer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@ChannelHandler.Sharable
public class Iso8583ServerHandler extends SimpleChannelInboundHandler<IsoMessage> {
    private static final Logger logger = LoggerFactory.getLogger(Iso8583ServerHandler.class);
    private static final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    
    private final IsoTransactionService txService;
    private final int timeoutMs;
    private final String systemError;

    public Iso8583ServerHandler(Properties props) {
        this.txService = new IsoTransactionService(props);
        this.timeoutMs = Integer.parseInt(props.getProperty("adapter.timeout.total-threshold-ms", "180"));
        this.systemError = props.getProperty("adapter.response.system-error-code", "96");
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, IsoMessage msg) {
        logger.info("Inbound ISO Request: [MTI: {}, Remote: {}]", msg.mti(), ctx.channel().remoteAddress());
        Timer.Sample sample = Timer.start(MetricsConfig.registry);

        CompletableFuture.runAsync(() -> {
            try {
                // 1. Process and generate the response message object
                IsoMessage responseMsg = txService.processAndPrepareResponse(msg);
                
                // 2. Convert to bytes (handles Bitmap, Fields, and Outbound MAC)
                byte[] responseData = txService.encodeMessage(responseMsg);
                
                sendBytes(ctx, responseData);
                MetricsConfig.txnCounter.increment();
            } catch (Exception e) {
                logger.error("Critical error processing transaction: {}", e.getMessage(), e);
                handleError(ctx, msg, systemError);
            }
        }, executor)
        .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .exceptionally(ex -> {
            logger.error("SLA Violation or Error: {}", ex.getMessage());
            handleError(ctx, msg, systemError);
            MetricsConfig.errorCounter.increment();
            return null;
        })
        .whenComplete((res, ex) -> sample.stop(MetricsConfig.txnTimer));
    }

    private void handleError(ChannelHandlerContext ctx, IsoMessage request, String errorCode) {
        try {
            IsoMessage errorMsg = txService.createErrorResponse(request, errorCode);
            byte[] errorData = txService.encodeMessage(errorMsg);
            sendBytes(ctx, errorData);
        } catch (Exception e) {
            logger.error("Failed to send error response", e);
        }
    }

    private void sendBytes(ChannelHandlerContext ctx, byte[] data) {
        ByteBuf frame = ctx.alloc().buffer(data.length + 2);
        frame.writeShort(data.length); // TCP 2-byte length header
        frame.writeBytes(data);
        ctx.writeAndFlush(frame);
    }

    @Override
public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    if (cause instanceof io.netty.handler.codec.TooLongFrameException) {
        logger.warn("Invalid frame size received ({}). Closing connection.", cause.getMessage());
    } else if (cause instanceof io.netty.handler.timeout.ReadTimeoutException) {
        logger.warn("Connection timed out (Idle).");
    } else {
        logger.error("Netty pipeline error: ", cause);
    }
    ctx.close(); // Crucial: close the socket to stop the error loop
}
}