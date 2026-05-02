package com.byteentropy.iso8583_adapter_core.config;

import com.byteentropy.iso8583_adapter_core.decoder.MessageFrameDecoder;
import com.byteentropy.iso8583_adapter_core.decoder.Iso8583Decoder;
import com.byteentropy.iso8583_adapter_core.handler.Iso8583ServerHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

public class NettyServerConfig {
    private final int port;

    public NettyServerConfig(int port) {
        this.port = port;
    }

    public void start() throws InterruptedException {
        // 1 boss thread is plenty for accepting connections
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        // Default worker count is usually CPU x 2
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
             .channel(NioServerSocketChannel.class)
             .option(ChannelOption.SO_BACKLOG, 1024) // Handle connection spikes
             .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
             .childOption(ChannelOption.TCP_NODELAY, true)
             .childOption(ChannelOption.SO_KEEPALIVE, true)
             .childHandler(new ChannelInitializer<SocketChannel>() {
                 @Override
                 public void initChannel(SocketChannel ch) {
                     ch.pipeline().addLast(
                         new MessageFrameDecoder(), 
                         new Iso8583Decoder(), 
                         new Iso8583ServerHandler()
                     );
                 }
             });

            System.out.println("ISO 8583 Adapter running on port " + port);
            b.bind(port).sync().channel().closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}