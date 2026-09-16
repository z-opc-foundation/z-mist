package io.github.yuku123.z.mist.common.connect.coder;

import io.github.yuku123.z.mist.common.connect.message.Message;
import io.github.yuku123.z.mist.common.connect.serializer.JsonSerializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

/**
 * JSON 解码器
 */
public class JsonDecoder extends ByteToMessageDecoder {

    private static final JsonSerializer serializer = new JsonSerializer();

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // 4字节长度 + 内容
        if (in.readableBytes() < 4) {
            return;
        }

        in.markReaderIndex();
        int length = in.readInt();

        if (in.readableBytes() < length) {
            in.resetReaderIndex();
            return;
        }

        byte[] data = new byte[length];
        in.readBytes(data);

        Message message = serializer.deserialize(data, Message.class);
        out.add(message);
    }
}