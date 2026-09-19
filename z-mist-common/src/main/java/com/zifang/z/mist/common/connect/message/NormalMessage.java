package com.zifang.z.mist.common.connect.message;

import com.zifang.z.mist.common.connect.CommandType;

/**
 * 普通消息（包含业务数据）
 */
public class NormalMessage extends Message {

    private static final long serialVersionUID = 1L;

    /**
     * 业务数据（JSON字符串）
     */
    private String body;

    public NormalMessage() {
        super();
    }

    public NormalMessage(CommandType commandType) {
        super(commandType);
    }

    public NormalMessage(CommandType commandType, String body) {
        super(commandType);
        this.body = body;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }
}