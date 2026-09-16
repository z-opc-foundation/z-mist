package io.github.yuku123.z.mist.client.config;

import io.github.yuku123.z.mist.client.support.ClientBusinessHandler;
import io.github.yuku123.z.mist.common.connect.ProtocolConstant;
import io.github.yuku123.z.mist.common.connect.coder.JsonDecoder;
import io.github.yuku123.z.mist.common.connect.coder.JsonEncoder;
import io.github.yuku123.z.mist.common.connect.handler.HeartbeatHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.timeout.IdleStateHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;

/**
 * Mist 客户端
 */
public class MistClient {

    private static final Logger logger = LogManager.getLogger(MistClient.class);

    private String serverHost = "localhost";
    private int serverPort = ProtocolConstant.DEFAULT_PORT;
    private String appName;
    private String appSecret;

    private EventLoopGroup group;
    private Channel channel;
    private ClientBusinessHandler businessHandler;

    public MistClient(String appName, String appSecret) {
        this.appName = appName;
        this.appSecret = appSecret;
    }

    public MistClient(String serverHost, int serverPort, String appName, String appSecret) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.appName = appName;
        this.appSecret = appSecret;
    }

    @PostConstruct
    public void start() {
        new Thread(() -> {
            try {
                connect();
            } catch (Exception e) {
                logger.error("Failed to connect to Mist server", e);
            }
        }).start();
    }

    private void connect() throws Exception {
        group = new NioEventLoopGroup();

        businessHandler = new ClientBusinessHandler(appName, appSecret);

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        // 空闲检测
                        pipeline.addLast(new IdleStateHandler(0, 30, 0, TimeUnit.SECONDS));
                        // 编解码器
                        pipeline.addLast(new LengthFieldBasedFrameDecoder(1024 * 1024, 0, 4, 0, 4));
                        pipeline.addLast(new JsonDecoder());
                        pipeline.addLast(new JsonEncoder());
                        // 心跳处理器
                        pipeline.addLast(new HeartbeatHandler());
                        // 业务处理器
                        pipeline.addLast(businessHandler);
                    }
                });

        ChannelFuture future = bootstrap.connect(serverHost, serverPort).sync();
        channel = future.channel();
        logger.info("Mist Client connected to {}:{}", serverHost, serverPort);
    }

    @PreDestroy
    public void stop() {
        if (channel != null) {
            channel.close();
        }
        if (group != null) {
            group.shutdownGracefully();
        }
        logger.info("Mist Client stopped");
    }

    /**
     * 获取密钥
     */
    public String getSecret(String secretKey, String group, String namespace) {
        return businessHandler.getSecret(secretKey, group, namespace);
    }

    /**
     * 获取密钥列表
     */
    public java.util.List<String> listSecrets(String group, String namespace) {
        return businessHandler.listSecrets(group, namespace);
    }

    /**
     * 是否已连接
     */
    public boolean isConnected() {
        return channel != null && channel.isActive();
    }
}