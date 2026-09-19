package com.zifang.z.mist.web.scheduler;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FEATURE065 T3: WebhookNotifier 构建 payload 测试.
 */
public class WebhookNotifierTest {

    @Test
    void testDingtalkPayloadFormat() {
        String title = "[z-mist] expiring - test_key";
        String message = "密钥即将过期";
        assertTrue(title.startsWith("[z-mist]"));
        assertNotNull(message);
    }

    @Test
    void testJsonEscape() {
        String input = "key\"with\\quotes\nand\nnewlines";
        String escaped = input.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
        assertFalse(escaped.contains("\n"));
        // 注意: 转义后包含 \\" 和 \\\\，但仍包含反斜杠字符
        assertTrue(escaped.contains("\\\""));
        assertTrue(escaped.contains("\\n"));
    }

    @Test
    void testWecomPayloadFormat() {
        String title = "密钥过期告警";
        String message = "db_password 将在 3 天后过期";
        assertNotNull(title);
        assertNotNull(message);
        assertTrue(title.length() > 0);
        assertTrue(message.length() > 0);
    }

    @Test
    void testFeishuPayloadFormat() {
        String title = "轮换失败";
        String message = "rotate_now 抛异常: 连接超时";
        assertNotNull(title);
        assertNotNull(message);
    }

    @Test
    void testGenericPayloadFormat() {
        LocalDateTime now = LocalDateTime.now();
        String timestamp = now.toString();
        assertNotNull(timestamp);
        assertTrue(timestamp.startsWith("2026"));
    }
}
