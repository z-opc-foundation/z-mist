package com.zifang.z.mist.common.connect.coder;

import com.zifang.z.mist.common.connect.message.Message;
import com.zifang.z.mist.common.connect.serializer.JsonSerializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * JSON 编码器
 */
public class JsonEncoder extends MessageToByteEncoder<Message> {

    private static final JsonSerializer serializer = new JsonSerializer();

    @Override
    protected void encode(ChannelHandlerContext ctx, Message msg, ByteBuf out) throws Exception {
        byte[] data = serializer.serialize(msg);

        // 写入长度（4字节）+ 内容
        out.writeInt(data.length);
        out.writeBytes(data);
    }
}