package com.byteentropy.iso8583_adapter_core.config;

import com.byteentropy.iso8583_adapter_core.decoder.Iso8583Decoder;
import com.byteentropy.iso8583_adapter_core.decoder.MessageFrameDecoder;
import com.byteentropy.iso8583_adapter_core.handler.Iso8583ServerHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.ResourceLeakDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class NettyServerConfig {
    private static final Logger logger = LoggerFactory.getLogger(NettyServerConfig.class);
    private final int port;
    private final Properties properties;
    private final Iso8583ServerHandler sharedHandler;

    public NettyServerConfig(int port, Properties properties) {
        this.port = port;
        this.properties = properties;
        
        // 1. Configure Leak Detection with safety fallback
        String leakLevel = properties.getProperty("adapter.netty.leak-detection-level", "SIMPLE").toUpperCase();
        try {
            ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.valueOf(leakLevel));
            logger.info("Netty Leak Detection Level set to: {}", leakLevel);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid leak level '{}'. Defaulting to SIMPLE.", leakLevel);
            ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.SIMPLE);
        }

        // 2. Performance Tip: Pre-instantiate the handler to share across all connections
        // Note: Ensure Iso8583ServerHandler is marked with @ChannelHandler.Sharable
        this.sharedHandler = new Iso8583ServerHandler(properties);
    }

    public void start() throws InterruptedException {
        int readTimeout = Integer.parseInt(properties.getProperty("adapter.tcp.read-timeout-ms", "5000"));
        
        // Boss handles accepting connections, Worker handles I/O
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        // FIX: Add graceful shutdown hook to catch SIGTERM (Docker stop / Ctrl+C)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown signal received. Gracefully shutting down Netty event loops...");
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
            try {
                if (!bossGroup.awaitTermination(5, TimeUnit.SECONDS)) {
                    logger.warn("Boss group did not terminate in 5 seconds. Forcing shutdown.");
                }
                if (!workerGroup.awaitTermination(5, TimeUnit.SECONDS)) {
                    logger.warn("Worker group did not terminate in 5 seconds. Forcing shutdown.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "netty-shutdown-hook"));

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
             .channel(NioServerSocketChannel.class)
             .option(ChannelOption.SO_BACKLOG, 1024)
             .childOption(ChannelOption.TCP_NODELAY, true)
             .childOption(ChannelOption.SO_KEEPALIVE, true)
             .childHandler(new ChannelInitializer<SocketChannel>() {
                 @Override
                 public void initChannel(SocketChannel ch) {
                     ch.pipeline().addLast(
                        // Monitor inactivity
                        new ReadTimeoutHandler(readTimeout, TimeUnit.MILLISECONDS), 
                        // Frame the byte stream
                        new MessageFrameDecoder(), 
                        // Parse ISO 8583 content (New instance per channel is required here)
                        new Iso8583Decoder(properties), 
                        // Execute business logic (Shared instance)
                        sharedHandler
                    );
                 }
             });

            ChannelFuture f = b.bind(port).sync();
            logger.info("ISO 8583 Adapter Engine listening on port {}", port);
            f.channel().closeFuture().sync();
        } finally {
            // Standard fallback cleanup if the block exits naturally
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}