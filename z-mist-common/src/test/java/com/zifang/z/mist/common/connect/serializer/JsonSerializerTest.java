package com.zifang.z.mist.common.connect.serializer;

import com.zifang.z.mist.common.connect.CommandType;
import com.zifang.z.mist.common.connect.message.Message;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link JsonSerializer} 的线上形状钉桩。
 *
 * <p>这个类是 {@code JsonEncoder} / {@code JsonDecoder} 的唯一实现，也就是 client↔server
 * 走 Netty 时真正上线的那份字节。它从 Jackson 换到 JsonUtil 之后没有任何测试盖着，
 * 而 Jackson→JsonUtil 有五处静默变行为（枚举、transient、getter、Map 接口目标、日期格式），
 * 编译和别处的绿都拦不住 —— 所以这里逐条钉住 {@link Message} 上真正会用到的字段。
 */
class JsonSerializerTest {

    private final JsonSerializer serializer = new JsonSerializer();

    private static Message sample() {
        Message m = new Message(CommandType.AUTH_REQUEST);
        m.setMessageId("m-1");
        m.setRequestId("r-1");
        m.setTimestamp(1760000000123L);
        m.setSuccess(true);
        m.setErrorMessage("错误信息");
        return m;
    }

    @Test
    @DisplayName("枚举出名字而不是序号：两端都是 CommandType，换成数字就解不回来")
    void enumGoesOutAsName() {
        String json = new String(serializer.serialize(sample()), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"AUTH_REQUEST\""), "枚举必须出名字: " + json);
    }

    @Test
    @DisplayName("primitive long 出 JSON 数字：出字符串会改线上形状")
    void primitiveLongStaysNumber() {
        String json = new String(serializer.serialize(sample()), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"timestamp\":1760000000123"), "时间戳出数字: " + json);
    }

    @Test
    @DisplayName("null 的 String 字段出 null 键：信封形状不能因为换库而缩字段")
    void nullFieldsStillPresent() {
        Message bare = new Message(CommandType.HEARTBEAT);
        String json = new String(serializer.serialize(bare), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"errorMessage\":null"), "未赋值的字段仍要出键: " + json);
        assertTrue(json.contains("\"requestId\":null"), "未赋值的字段仍要出键: " + json);
    }

    @Test
    @DisplayName("往返：编出去再解回来，六个字段逐个相等")
    void roundTripKeepsEveryField() {
        Message back = serializer.deserialize(serializer.serialize(sample()), Message.class);
        assertEquals("m-1", back.getMessageId());
        assertEquals("r-1", back.getRequestId());
        assertEquals(CommandType.AUTH_REQUEST, back.getCommandType());
        assertEquals(1760000000123L, back.getTimestamp());
        assertTrue(back.isSuccess());
        assertEquals("错误信息", back.getErrorMessage());
    }

    @Test
    @DisplayName("非 ASCII 按 UTF-8 走：中文错误消息不该被替换成问号")
    void utf8SurvivesTheWire() {
        Message m = new Message(CommandType.SECRET_GET);
        m.setErrorMessage("密钥轮转失败：磁盘配额不足");
        Message back = serializer.deserialize(serializer.serialize(m), Message.class);
        assertEquals("密钥轮转失败：磁盘配额不足", back.getErrorMessage());
    }

    @Test
    @DisplayName("缺字段的报文解出 null，而不是抛异常")
    void missingFieldBecomesNull() {
        byte[] raw = "{\"messageId\":\"m-2\"}".getBytes(StandardCharsets.UTF_8);
        Message back = serializer.deserialize(raw, Message.class);
        assertEquals("m-2", back.getMessageId());
        assertNull(back.getCommandType());
        assertNull(back.getErrorMessage());
    }
}
