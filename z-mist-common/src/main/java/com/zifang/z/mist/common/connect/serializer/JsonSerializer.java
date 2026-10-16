package com.zifang.z.mist.common.connect.serializer;

import com.zifang.util.json.JsonUtil;
import com.zifang.z.mist.common.connect.ProtocolConstant;

import java.nio.charset.Charset;

/**
 * JSON 序列化器
 */
public class JsonSerializer implements Serializer {

    @Override
    public byte[] serialize(Object object) {
        try {
            return JsonUtil.toJson(object).getBytes(Charset.forName(ProtocolConstant.DEFAULT_CHARSET));
        } catch (RuntimeException e) {
            throw new RuntimeException("Serialize failed", e);
        }
    }

    @Override
    public <T> T deserialize(byte[] data, Class<T> clazz) {
        try {
            return JsonUtil.fromJson(new String(data, Charset.forName(ProtocolConstant.DEFAULT_CHARSET)), clazz);
        } catch (RuntimeException e) {
            throw new RuntimeException("Deserialize failed", e);
        }
    }
}
