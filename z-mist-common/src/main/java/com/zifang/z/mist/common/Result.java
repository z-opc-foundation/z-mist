package com.zifang.z.mist.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;

/**
 * 接口统一返回信封(API DTO 化 · z-boot 家族标准 SOP 步骤 6).
 *
 * <p>序列化字段与历史 {@code Map<String, Object>} 返回完全一致:
 * {@code success(boolean)} / {@code message(String)} / {@code data(T)},
 * 不额外输出 code 等任何字段;值为 {@code null} 的字段不输出
 * ({@link JsonInclude.Include#NON_NULL}),保证与原 HashMap 返回的 JSON 形状零变化。
 *
 * @param <T> data 的类型
 * @author zifang
 * @since 1.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 是否成功(对应历史 Map 的 success 键). */
    private boolean success;

    /** 提示信息(对应历史 Map 的 message 键). */
    private String message;

    /** 业务数据(对应历史 Map 的 data 键). */
    private T data;

    public Result() {
    }

    /**
     * 构造 success=true 且无 data/message 的结果.
     *
     * @param <T> data 类型
     * @return 成功结果
     */
    public static <T> Result<T> ok() {
        Result<T> result = new Result<>();
        result.success = true;
        return result;
    }

    /**
     * 构造 success=true 且带 data 的结果.
     *
     * @param data 业务数据
     * @param <T>  data 类型
     * @return 成功结果
     */
    public static <T> Result<T> ok(T data) {
        Result<T> result = ok();
        result.data = data;
        return result;
    }

    /**
     * 构造 success=false 且带 message 的结果.
     *
     * @param message 错误信息
     * @param <T>     data 类型
     * @return 失败结果
     */
    public static <T> Result<T> fail(String message) {
        Result<T> result = new Result<>();
        result.success = false;
        result.message = message;
        return result;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
