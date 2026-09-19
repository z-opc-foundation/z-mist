package com.zifang.z.mist.core.server.handler;

import com.zifang.z.mist.common.connect.CommandType;
import com.zifang.z.mist.common.connect.message.HeartbeatMessage;
import com.zifang.z.mist.common.connect.message.Message;
import com.zifang.z.mist.common.connect.message.NormalResponse;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 服务端业务处理器
 */
@ChannelHandler.Sharable
public class ServerBusinessHandler extends SimpleChannelInboundHandler<Message> {

    private static final Logger logger = LogManager.getLogger(ServerBusinessHandler.class);

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) throws Exception {
        logger.info("Received message: {}", msg.getCommandType());

        CommandType commandType = msg.getCommandType();
        if (commandType == null) {
            return;
        }

        switch (commandType) {
            case HEARTBEAT:
                handleHeartbeat(ctx, msg);
                break;
            case SECRET_GET:
                handleGetSecret(ctx, msg);
                break;
            case SECRET_LIST:
                handleListSecret(ctx, msg);
                break;
            case SECRET_SUBSCRIBE:
                handleSubscribe(ctx, msg);
                break;
            case SECRET_UNSUBSCRIBE:
                handleUnsubscribe(ctx, msg);
                break;
            case AUTH_REQUEST:
                handleAuth(ctx, msg);
                break;
            default:
                logger.warn("Unknown command type: {}", commandType);
                break;
        }
    }

    private void handleHeartbeat(ChannelHandlerContext ctx, Message msg) {
        HeartbeatMessage response = new HeartbeatMessage();
        response.setCommandType(CommandType.HEARTBEAT_RESPONSE);
        response.setSuccess(true);
        ctx.writeAndFlush(response);
    }

    private void handleGetSecret(ChannelHandlerContext ctx, Message msg) {
        NormalResponse response = new NormalResponse();
        response.setCommandType(CommandType.SECRET_GET);
        response.setSuccess(true);
        response.setData("{}");
        ctx.writeAndFlush(response);
    }

    private void handleListSecret(ChannelHandlerContext ctx, Message msg) {
        NormalResponse response = new NormalResponse();
        response.setCommandType(CommandType.SECRET_LIST);
        response.setSuccess(true);
        response.setData("[]");
        ctx.writeAndFlush(response);
    }

    private void handleSubscribe(ChannelHandlerContext ctx, Message msg) {
        NormalResponse response = new NormalResponse();
        response.setCommandType(CommandType.SECRET_SUBSCRIBE);
        response.setSuccess(true);
        ctx.writeAndFlush(response);
    }

    private void handleUnsubscribe(ChannelHandlerContext ctx, Message msg) {
        NormalResponse response = new NormalResponse();
        response.setCommandType(CommandType.SECRET_UNSUBSCRIBE);
        response.setSuccess(true);
        ctx.writeAndFlush(response);
    }

    private void handleAuth(ChannelHandlerContext ctx, Message msg) {
        NormalResponse response = new NormalResponse();
        response.setCommandType(CommandType.AUTH_RESPONSE);
        response.setSuccess(true);
        response.setData("{\"token\":\"mock-token\"}");
        ctx.writeAndFlush(response);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        logger.info("Client connected: {}", ctx.channel().remoteAddress());
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.info("Client disconnected: {}", ctx.channel().remoteAddress());
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("Exception caught in server handler", cause);
        ctx.close();
    }
}