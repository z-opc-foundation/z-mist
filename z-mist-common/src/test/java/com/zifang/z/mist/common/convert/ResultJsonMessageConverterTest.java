package com.zifang.z.mist.common.convert;

import com.zifang.util.json.JsonUtil;
import com.zifang.z.mist.common.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.mock.http.MockHttpOutputMessage;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 统一信封 {@link Result} 出站形状 + 转换器优先级的钉桩。
 *
 * <p>「null 字段不出键」这条契约原先写在 {@code Result} 类上的
 * {@code @JsonInclude(NON_NULL)}，注解摘掉后由 {@link ResultJsonMessageConverter} 承担。
 * 这里钉三件事，任一件塌了线上形状就会变：
 * <ol>
 *   <li>Spring Boot 把自定义转换器排在默认的 Jackson 转换器<b>前面</b>——
 *       不然 {@code Result} 会落回 Jackson，null 键就全冒出来了；</li>
 *   <li>丢 null 的范围<b>只限信封自己的三个字段</b>，{@code data} 里各 Resp 的可空字段照旧出 null；
 *       换成全局 NON_NULL 就会把这些一起吞掉，是另一种线上变更；</li>
 *   <li>content type 仍是 {@code application/json}（不带 charset）。</li>
 * </ol>
 */
class ResultJsonMessageConverterTest {

    private final ResultJsonMessageConverter converter = new ResultJsonMessageConverter();

    private String write(Result<?> result) throws Exception {
        MockHttpOutputMessage out = new MockHttpOutputMessage();
        converter.write(result, MediaType.APPLICATION_JSON, out);
        return out.getBodyAsString(StandardCharsets.UTF_8);
    }

    /** 线上 data 里就有可空字段（SecretResp.description 之类），不能被顺手吞掉。 */
    public static class Payload {
        private String secretKey;
        private String description;

        Payload(String k, String d) {
            this.secretKey = k;
            this.description = d;
        }
    }

    @Test
    @DisplayName("无 data 的成功响应：只有 success 一个键")
    void bareOkHasOneKey() throws Exception {
        assertEquals("{\"success\":true}", write(Result.ok()));
    }

    @Test
    @DisplayName("失败响应：message 在，没有 data 键")
    void failKeepsMessageOnly() throws Exception {
        assertEquals("{\"success\":false,\"message\":\"删除失败\"}", write(Result.fail("删除失败")));
    }

    @Test
    @DisplayName("data 为 null 时不出 data 键")
    void nullDataKeyOmitted() throws Exception {
        assertEquals("{\"success\":true}", write(Result.<String>ok(null)));
    }

    @Test
    @DisplayName("丢 null 只作用于信封：data 内部的 description:null 必须留着")
    void nullScopeIsEnvelopeOnly() throws Exception {
        String json = write(Result.ok(new Payload("db_pw", null)));
        assertEquals("{\"success\":true,\"data\":{\"secretKey\":\"db_pw\",\"description\":null}}", json);
    }

    @Test
    @DisplayName("content type 仍是 application/json，不带 charset")
    void contentTypeUnchanged() throws Exception {
        MockHttpOutputMessage out = new MockHttpOutputMessage();
        converter.write(Result.ok(), MediaType.APPLICATION_JSON, out);
        assertEquals("application/json", out.getHeaders().getContentType().toString());
    }

    @Test
    @DisplayName("自定义转换器排在默认 Jackson 转换器前面，否则这个类形同虚设")
    void customConverterWinsOverJackson() {
        List<HttpMessageConverter<?>> converters =
                new HttpMessageConverters(converter).getConverters();
        assertInstanceOf(ResultJsonMessageConverter.class, converters.get(0),
                "Boot 把自定义转换器排在默认转换器前面；排在后面就轮不到它，Result 会落回 Jackson");
    }

    @Test
    @DisplayName("只写出站方向：入参侧不该被这个转换器接走")
    void readIsNotSupported() {
        assertFalse(converter.canRead(Result.class, MediaType.APPLICATION_JSON));
        assertTrue(converter.canWrite(Result.class, MediaType.APPLICATION_JSON));
    }

    /**
     * 钉住与 JsonUtil 的一致性：这个转换器不该自己拼字符串，null 规则由 Result 上的
     * {@code @JsonType(nonNull = true)} 决定。哪天注解被摘掉，这里立刻红。
     */
    @Test
    @DisplayName("转换器不自己判断 null：输出与 JsonUtil.toJson 逐字一致")
    void matchesPlainJsonUtil() throws Exception {
        for (Result<?> r : new Result<?>[]{Result.ok(), Result.ok(new Payload("k", null)),
                Result.fail("坏了")}) {
            assertEquals(JsonUtil.toJson(r), write(r));
        }
    }
}
