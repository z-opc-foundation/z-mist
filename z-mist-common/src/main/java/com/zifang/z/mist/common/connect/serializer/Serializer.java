package com.zifang.z.mist.common.connect.serializer;

/**
 * 序列化器接口
 */
public interface Serializer {

    /**
     * 序列化对象为字节数组
     */
    byte[] serialize(Object object);

    /**
     * 反序列化为对象
     */
    <T> T deserialize(byte[] data, Class<T> clazz);
}