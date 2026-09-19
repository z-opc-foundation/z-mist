package com.zifang.z.mist.common.connect;

/**
 * 协议常量
 */
public class ProtocolConstant {

    /**
     * 协议版本
     */
    public static final String VERSION = "1.0.0";

    /**
     * 魔数
     */
    public static final int MAGIC_NUMBER = 0xA1B2;

    /**
     * 默认编码
     */
    public static final String DEFAULT_CHARSET = "UTF-8";

    /**
     * 心跳间隔（秒）
     */
    public static final int HEARTBEAT_INTERVAL = 30;

    /**
     * 默认端口
     */
    public static final int DEFAULT_PORT = 9085;
}