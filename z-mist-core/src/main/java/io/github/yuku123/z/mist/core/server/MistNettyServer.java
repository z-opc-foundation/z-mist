package io.github.yuku123.z.mist.core.server;

import io.github.yuku123.z.mist.common.connect.coder.JsonDecoder;
import io.github.yuku123.z.mist.common.connect.coder.JsonEncoder;
import io.github.yuku123.z.mist.common.connect.handler.HeartbeatHandler;
import io.github.yuku123.z.mist.core.server.handler.ServerBusinessHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * Netty 服务器组件
 */
@Component
public class MistNettyServer {

    private static final Logger logger = LogManager.getLogger(MistNettyServer.class);

    @Value("${z-mist.server.port:9085}")
    private int port;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    @PostConstruct
    public void start() {
        new Thread(() -> {
            try {
                startServer();
            } catch (Exception e) {
                logger.error("Failed to start Netty server", e);
            }
        }).start();
    }

    private void startServer() throws Exception {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        // 空闲检测
                        pipeline.addLast(new IdleStateHandler(60, 0, 0));
                        // 编解码器
                        pipeline.addLast(new JsonDecoder());
                        pipeline.addLast(new JsonEncoder());
                        // 心跳处理器
                        pipeline.addLast(new HeartbeatHandler());
                        // 业务处理器
                        pipeline.addLast(new ServerBusinessHandler());
                    }
                });

        ChannelFuture future = bootstrap.bind(port).sync();
        serverChannel = future.channel();
        logger.info("Mist Netty Server started on port {}", port);
    }

    @PreDestroy
    public void stop() {
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        logger.info("Mist Netty Server stopped");
    }
}