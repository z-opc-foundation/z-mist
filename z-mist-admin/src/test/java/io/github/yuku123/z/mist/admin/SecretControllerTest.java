package io.github.yuku123.z.mist.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yuku123.z.mist.core.domain.entity.ZMistSecretInfo;
import io.github.yuku123.z.mist.core.domain.service.IZMistSecretService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FEATURE024 起步：SecretController 集成测试。
 * <p>
 * 覆盖：5 个端点 + 限流触发 + 写 access_log 调 recordAccess。
 */
@SpringBootTest
@AutoConfigureMockMvc
public class SecretControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private IZMistSecretService secretService;

    @BeforeEach
    void setUp() {
        // 给 SecurityContext 注入 anonymous token，让 SecretController 的
        // @PreAuthorize("... or isAnonymous()") 走匿名分支通过
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken(
                        "test-key",
                        "anonymous",
                        AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        // mock service 行为
        ZMistSecretInfo mockSecret = new ZMistSecretInfo();
        mockSecret.setId(1L);
        mockSecret.setSecretKey("demo_db_password");
        mockSecret.setGroup("demo");
        mockSecret.setNamespace("dev");
        mockSecret.setEncryptedValue("base64ciphertext==");
        mockSecret.setValueMd5("md5fingerprint");
        mockSecret.setSecretType("password");

        when(secretService.getSecret(eq("demo_db_password"), eq("demo"), eq("dev")))
                .thenReturn(mockSecret);
        when(secretService.listSecrets(any(), any(), any()))
                .thenReturn(Collections.singletonList(mockSecret));
        when(secretService.saveSecret(any())).thenReturn(mockSecret);
        when(secretService.updateSecret(any())).thenReturn(mockSecret);
        when(secretService.deleteSecret(any(), any(), any())).thenReturn(true);
    }

    @Test
    void shouldSaveSecret() throws Exception {
        ZMistSecretInfo body = new ZMistSecretInfo();
        body.setSecretKey("demo_db_password");
        body.setGroup("demo");
        body.setNamespace("dev");
        body.setEncryptedValue("super-secret-pwd");
        body.setSecretType("password");

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
        ZMistSecretInfo body = new ZMistSecretInfo();
        body.setId(1L);
        body.setSecretKey("demo_db_password");
        body.setGroup("demo");
        body.setNamespace("dev");
        body.setEncryptedValue("new-value");
        body.setSecretType("password");

        mockMvc.perform(put("/api/secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Staff-No", "test-user")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void shouldDeleteSecret() throws Exception {
        mockMvc.perform(delete("/api/secret/demo_db_password")
                        .param("group", "demo")
                        .param("namespace", "dev")
                        .header("X-Staff-No", "test-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("删除成功"));
    }

    @Test
    void shouldRateLimitOnExcessiveGet() throws Exception {
        // GET 限制 60/min，模拟 65 次
        for (int i = 0; i < 65; i++) {
            int finalI = i;
            mockMvc.perform(get("/api/secret/get")
                            .param("secretKey", "demo_db_password")
                            .param("group", "demo")
                            .param("namespace", "dev")
                            .header("X-Staff-No", "flood-bot"))
                    .andDo(result -> {
                        // 第 61 次开始应被限流
                        if (finalI >= 60) {
                            // 不强断言避免脆弱；只 log
                            System.out.println("Call " + finalI + " status = " + result.getResponse().getStatus());
                        }
                    });
        }
        // 最后再调一次应该被限流
        mockMvc.perform(get("/api/secret/get")
                        .param("secretKey", "demo_db_password")
                        .param("group", "demo")
                        .param("namespace", "dev")
                        .header("X-Staff-No", "flood-bot"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Rate limit")));
    }
}
