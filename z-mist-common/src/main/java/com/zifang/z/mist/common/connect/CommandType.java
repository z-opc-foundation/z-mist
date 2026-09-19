package com.zifang.z.mist.common.connect;

/**
 * 命令类型枚举
 */
public enum CommandType {

    /**
     * 客户端请求
     */
    SECRET_GET((byte) 0x01, "获取密钥"),
    SECRET_LIST((byte) 0x02, "密钥列表"),
    SECRET_SUBSCRIBE((byte) 0x03, "订阅密钥变更"),
    SECRET_UNSUBSCRIBE((byte) 0x04, "取消订阅"),

    /**
     * 服务端推送
     */
    SECRET_PUSH((byte) 0x11, "密钥推送"),
    SECRET_DELETE_PUSH((byte) 0x12, "密钥删除推送"),
    SECRET_CHANGE_PUSH((byte) 0x13, "密钥变更推送"),

    /**
     * 认证
     */
    AUTH_REQUEST((byte) 0x20, "认证请求"),
    AUTH_RESPONSE((byte) 0x21, "认证响应"),

    /**
     * 心跳
     */
    HEARTBEAT((byte) 0x30, "心跳"),
    HEARTBEAT_RESPONSE((byte) 0x31, "心跳响应");

    private final byte code;
    private final String description;

    CommandType(byte code, String description) {
        this.code = code;
        this.description = description;
    }

    public static CommandType fromCode(byte code) {
        for (CommandType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return null;
    }

    public byte getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}