package com.zifang.z.mist.common.convert;

import com.zifang.util.json.JsonUtil;
import com.zifang.z.mist.common.Result;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 统一信封 {@link Result} 的出站序列化。
 *
 * <p>为什么单独一个转换器：本仓 18 个 controller、60 个方法的返回类型都是 {@code Result}，
 * 而「null 字段不出键」这条契约原先写死在 Result 类上的 {@code @JsonInclude(NON_NULL)}。
 * 注解摘掉之后，要么逐个方法改成 {@code void} + 自己 {@code resp.getWriter().write(...)}，
 * 要么在这里替框架把 {@code Result} 这一类接过来——后者改动面小一个数量级，
 * 而且同样落在 controller 层的隐式转换里，序列化契约仍然只有一处。
 *
 * <p>范围只限 {@code Result}：其它返回类型（各 controller 里的 {@code Map} 返回、
 * 错误响应等）照旧走原来的转换器，不受影响。
 */
@Component
public class ResultJsonMessageConverter extends AbstractHttpMessageConverter<Result> {

    public ResultJsonMessageConverter() {
        // 与原先 Jackson 转换器的默认 producible 类型保持一致：application/json 不带 charset
        super(MediaType.APPLICATION_JSON);
    }

    @Override
    protected boolean supports(Class<?> clazz) {
        return Result.class.isAssignableFrom(clazz);
    }

    @Override
    protected void writeInternal(Result result, HttpOutputMessage outputMessage) throws IOException {
        outputMessage.getBody().write(JsonUtil.toJson(result).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * {@code Result} 只出现在出站方向。入参侧的 {@code @RequestBody} 不归这里管。
     */
    @Override
    public boolean canRead(Class<?> clazz, MediaType mediaType) {
        return false;
    }

    @Override
    protected Result readInternal(Class<? extends Result> clazz, HttpInputMessage inputMessage)
            throws IOException {
        throw new HttpMessageNotReadableException("Result 只作出站响应，不接受反序列化");
    }
}
