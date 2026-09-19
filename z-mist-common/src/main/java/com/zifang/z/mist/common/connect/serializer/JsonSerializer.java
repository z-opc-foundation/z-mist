package com.zifang.z.mist.common.connect.serializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zifang.z.mist.common.connect.ProtocolConstant;

/**
 * JSON 序列化器
 */
public class JsonSerializer implements Serializer {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        objectMapper.findAndRegisterModules();
    }

    @Override
    public byte[] serialize(Object object) {
        try {
            return objectMapper.writeValueAsString(object).getBytes(ProtocolConstant.DEFAULT_CHARSET);
        } catch (Exception e) {
            throw new RuntimeException("Serialize failed", e);
        }
    }

    @Override
    public <T> T deserialize(byte[] data, Class<T> clazz) {
        try {
            return objectMapper.readValue(data, clazz);
        } catch (Exception e) {
            throw new RuntimeException("Deserialize failed", e);
        }
    }
}