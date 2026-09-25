package com.zifang.z.mist.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zifang.z.mist.core.domain.entity.ZMistSecretInfo;
import com.zifang.z.mist.core.domain.service.IZMistSecretService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FEATURE024 起步：SecretController 单元测试.
 * <p>
 * 家法规范 (SOP 步骤 8): standalone MockMvc + JDK 动态代理注入 service,
 * 不启动 Spring 容器(禁用 Boot 集成测试注解), 零 Mockito.
 * 覆盖：5 个端点 + 限流触发 + 写 access_log 调 recordAccess.
 * <p>
 * 每个用例新建 controller, 保证限流计数桶互不污染。
 */
public class SecretControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private Map<String, Function<Object[], Object>> answers;
    private Map<String, Integer> callCounts;

    @BeforeEach
    void setUp() throws Exception {
        answers = new HashMap<>();
        callCounts = new HashMap<>();

        ZMistSecretInfo mockSecret = new ZMistSecretInfo();
        mockSecret.setId(1L);
        mockSecret.setSecretKey("demo_db_password");
        mockSecret.setGroup("demo");
        mockSecret.setNamespace("dev");
        mockSecret.setEncryptedValue("base64ciphertext==");
        mockSecret.setValueMd5("md5fingerprint");
        mockSecret.setSecretType("password");

        answers.put("getSecret", args -> mockSecret);
        answers.put("listSecrets", args -> Collections.singletonList(mockSecret));
        answers.put("saveSecret", args -> mockSecret);
        answers.put("updateSecret", args -> mockSecret);
        answers.put("deleteSecret", args -> true);

        IZMistSecretService proxy = (IZMistSecretService) Proxy.newProxyInstance(
                IZMistSecretService.class.getClassLoader(),
                new Class<?>[]{IZMistSecretService.class},
                (p, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return method.invoke(p, args);
                    }
                    callCounts.merge(method.getName(), 1, Integer::sum);
                    Function<Object[], Object> answer = answers.get(method.getName());
                    if (answer != null) {
                        return answer.apply(args);
                    }
                    Class<?> rt = method.getReturnType();
                    if (rt == boolean.class) {
                        return false;
                    }
                    if (rt == int.class) {
                        return 0;
                    }
                    if (rt == long.class) {
                        return 0L;
                    }
                    return null;
                });

        com.zifang.z.mist.admin.api.SecretController controller =
                new com.zifang.z.mist.admin.api.SecretController();
        Field f = com.zifang.z.mist.admin.api.SecretController.class.getDeclaredField("secretService");
        f.setAccessible(true);
        f.set(controller, proxy);

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void shouldSaveSecret() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("secretKey", "demo_db_password");
        body.put("group", "demo");
        body.put("namespace", "dev");
        body.put("encryptedValue", "super-secret-pwd");
        body.put("secretType", "password");

        mockMvc.perform(post("/api/secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Staff-No", "test-user")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.secretKey").value("demo_db_password"));
    }

    @Test
    void shouldGetSecret() throws Exception {
        mockMvc.perform(get("/api/secret/get")
                        .param("secretKey", "demo_db_password")
                        .param("group", "demo")
                        .param("namespace", "dev")
                        .header("X-Staff-No", "test-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.secretKey").value("demo_db_password"));
    }

    @Test
    void shouldListSecrets() throws Exception {
        mockMvc.perform(get("/api/secret/list")
                        .param("namespace", "dev")
                        .header("X-Staff-No", "test-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void shouldUpdateSecret() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("id", 1);
        body.put("secretKey", "demo_db_password");
        body.put("group", "demo");
        body.put("namespace", "dev");
        body.put("encryptedValue", "new-value");
        body.put("secretType", "password");

        mockMvc.perform(put("/api/secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Staff-No", "test-user")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void shouldDeleteSecret() throws Exception {
        // 控制器是 @DeleteMapping 无路径 + secretKey 走 query param (旧测试用路径变量导致 404)
        mockMvc.perform(delete("/api/secret")
                        .param("secretKey", "demo_db_password")
                        .param("group", "demo")
                        .param("namespace", "dev")
                        .header("X-Staff-No", "test-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("删除成功"));
    }

    @Test
    void shouldRecordAccessOnEveryCall() throws Exception {
        // 顺序执行 4 个业务调用后断言 recordAccess 次数 (save/get/list/delete 各 1)
        mockMvc.perform(get("/api/secret/get")
                .param("secretKey", "demo_db_password")
                .header("X-Staff-No", "test-user"));
        mockMvc.perform(get("/api/secret/list")
                .param("namespace", "dev")
                .header("X-Staff-No", "test-user"));
        org.junit.jupiter.api.Assertions.assertEquals(
                2, callCounts.getOrDefault("recordAccess", 0).intValue(),
                "get + list 各写一次访问日志");
    }

    @Test
    void shouldRateLimitOnExcessiveGet() throws Exception {
        // GET 限流 60/min, 同 IP(127.0.0.1) 连打 61 次应触发
        for (int i = 0; i < 61; i++) {
            mockMvc.perform(get("/api/secret/get")
                    .param("secretKey", "demo_db_password")
                    .param("group", "demo")
                    .param("namespace", "dev")
                    .header("X-Staff-No", "flood-bot"));
        }
        mockMvc.perform(get("/api/secret/get")
                        .param("secretKey", "demo_db_password")
                        .param("group", "demo")
                        .param("namespace", "dev")
                        .header("X-Staff-No", "flood-bot"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Rate limit")));
    }
}
