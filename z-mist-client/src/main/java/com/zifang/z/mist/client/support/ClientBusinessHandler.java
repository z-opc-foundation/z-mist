package com.zifang.z.mist.client.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zifang.util.core.json.JsonMapperFactory;
import com.zifang.z.mist.common.connect.CommandType;
import com.zifang.z.mist.common.connect.message.Message;
import com.zifang.z.mist.common.connect.message.NormalMessage;
import com.zifang.z.mist.common.connect.message.NormalResponse;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

/**
 * 客户端业务处理器
 */
@ChannelHandler.Sharable
public class ClientBusinessHandler extends SimpleChannelInboundHandler<Message> {

    private static final Logger logger = LogManager.getLogger(ClientBusinessHandler.class);
    private static final ObjectMapper objectMapper = JsonMapperFactory.getDefault();
    private final Map<String, CountDownLatch> responseMap = new ConcurrentHashMap<>();
    private final Map<String, String> secretCache = new ConcurrentHashMap<>();
    private String appName;
    private String appSecret;
    private boolean authenticated = false;

    public ClientBusinessHandler(String appName, String appSecret) {
        this.appName = appName;
        this.appSecret = appSecret;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) throws Exception {
        CommandType commandType = msg.getCommandType();
        if (commandType == null) {
            return;
        }

        switch (commandType) {
            case HEARTBEAT_RESPONSE:
                logger.debug("Heartbeat response received");
                break;
            case AUTH_RESPONSE:
                handleAuthResponse(msg);
                break;
            case SECRET_GET:
                handleGetSecretResponse(msg);
                break;
            case SECRET_LIST:
                handleListSecretResponse(msg);
                break;
            default:
                break;
        }
    }

    private void handleAuthResponse(Message msg) {
        NormalResponse response = (NormalResponse) msg;
        if (response.isSuccess()) {
            authenticated = true;
            logger.info("Authentication successful");
        } else {
            logger.error("Authentication failed: {}", response.getErrorMessage());
        }
    }

    private void handleGetSecretResponse(Message msg) {
        NormalResponse response = (NormalResponse) msg;
        String requestId = msg.getRequestId();
        if (requestId != null && responseMap.containsKey(requestId)) {
            responseMap.get(requestId).countDown();
            if (response.isSuccess()) {
                secretCache.put(requestId, response.getData());
            }
        }
    }

    private void handleListSecretResponse(Message msg) {
        NormalResponse response = (NormalResponse) msg;
        String requestId = msg.getRequestId();
        if (requestId != null && responseMap.containsKey(requestId)) {
            responseMap.get(requestId).countDown();
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        logger.info("Connected to Mist server");
        // 发送认证请求
        sendAuthRequest(ctx);
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.info("Disconnected from Mist server");
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("Exception caught in client handler", cause);
        ctx.close();
    }

    private void sendAuthRequest(ChannelHandlerContext ctx) {
        try {
            NormalMessage authRequest = new NormalMessage(CommandType.AUTH_REQUEST);
            Map<String, String> authData = new HashMap<>();
            authData.put("appName", appName);
            authData.put("appSecret", appSecret);
            authRequest.setBody(objectMapper.writeValueAsString(authData));
            ctx.writeAndFlush(authRequest);
        } catch (Exception e) {
            logger.error("Failed to send auth request", e);
        }
    }

    public String getSecret(String secretKey, String group, String namespace) {
        // 简化实现，实际应该发送请求到服务端
        logger.info("Getting secret: {}/{}/{}", secretKey, group, namespace);
        return secretCache.get(secretKey);
    }

    public List<String> listSecrets(String group, String namespace) {
        logger.info("Listing secrets: {}/{}", group, namespace);
        return null;
    }
}