package com.zifang.z.mist.common.connect.message;

/**
 * 普通响应消息
 */
public class NormalResponse extends Message {

    private static final long serialVersionUID = 1L;

    /**
     * 响应数据（JSON字符串）
     */
    private String data;

    public NormalResponse() {
        super();
    }

    public NormalResponse(boolean success) {
        super();
        setSuccess(success);
    }

    public NormalResponse(boolean success, String data) {
        super();
        setSuccess(success);
        this.data = data;
    }

    public NormalResponse(boolean success, String data, String errorMessage) {
        super();
        setSuccess(success);
        this.data = data;
        setErrorMessage(errorMessage);
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }
}