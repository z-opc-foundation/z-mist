package com.zifang.z.mist.web.scheduler;

import com.zifang.z.mist.core.domain.entity.ZMistNotificationLog;
import com.zifang.z.mist.core.domain.mapper.ZMistNotificationLogMapper;
import okhttp3.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * Webhook 通知服务(FEATURE065 T3).
 * <p>
 * 支持多种 Webhook 渠道:
 * <ul>
 *   <li>dingtalk — 钉钉机器人(自定义关键词)</li>
 *   <li>wecom    — 企业微信机器人</li>
 *   <li>feishu   — 飞书机器人</li>
 *   <li>generic  — 通用 HTTP POST(JSON)</li>
 * </ul>
 * <p>
 * 被 ExpiringScheduler 和 RotationScheduler 调用，
 * 任何失败仅 log 不抛异常。
 *
 * <p>配置方式: 在 z_mist_notification_config 表（或系统属性）中配置 channel + target。
 * 默认为 log 渠道（仅写 DB），无外部调用。
 */
@Component
public class WebhookNotifier {

    private static final Logger log = LogManager.getLogger(WebhookNotifier.class);
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");
    @Autowired
    private ZMistNotificationLogMapper notificationLogMapper;

    /**
     * 发送通知并写入 z_mist_notification_log.
     *
     * @param notifyType 通知类型(expiring/expired/rotation_failed)
     * @param secretKey  密钥标识
     * @param group      分组
     * @param namespace  命名空间
     * @param channel    渠道(dingtalk/wecom/feishu/generic/log)
     * @param target     目标URL(渠道为 log 时可为空)
     * @param title      通知标题
     * @param message    通知消息
     */
    public void send(String notifyType, String secretKey, String group, String namespace,
                     String channel, String target, String title, String message) {
        ZMistNotificationLog logRow = new ZMistNotificationLog();
        logRow.setNotifyType(notifyType);
        logRow.setSecretKey(secretKey);
        logRow.setGroup(group);
        logRow.setNamespace(namespace);
        logRow.setChannel(channel);
        logRow.setTarget(target);
        logRow.setPayload("[" + title + "] " + message);
        logRow.setGmtCreate(LocalDateTime.now());
        try {
            if ("log".equals(channel) || channel == null || channel.isEmpty()) {
                // 仅日志
                log.info("[z-mist WebhookNotifier] {} | {} | {} | {}",
                        notifyType, secretKey, title, message);
                logRow.setSuccess(1);
            } else {
                String jsonBody = buildJsonBody(channel, title, message);
                doSendHttp(channel, target, jsonBody);
                logRow.setSuccess(1);
            }
        } catch (Exception ex) {
            logRow.setSuccess(0);
            logRow.setErrorMessage(ex.getMessage());
            log.warn("[z-mist WebhookNotifier] send failed: channel={}, secretKey={}, err={}",
                    channel, secretKey, ex.getMessage());
        } finally {
            notificationLogMapper.insert(logRow);
        }
    }

    /**
     * 构建各渠道的 JSON payload.
     */
    private String buildJsonBody(String channel, String title, String message) {
        switch (channel) {
            case "dingtalk":
                // 钉钉机器人 (自定义关键词: z-mist)
                return "{\"msgtype\":\"text\",\"text\":{\"content\":\"[z-mist] " + escapeJson(title) + "\\n" + escapeJson(message) + "\"}}";
            case "wecom":
                // 企业微信机器人
                return "{\"msgtype\":\"text\",\"text\":{\"content\":\"[z-mist] " + escapeJson(title) + "\\n" + escapeJson(message) + "\"}}";
            case "feishu":
                // 飞书机器人
                return "{\"msg_type\":\"text\",\"content\":{\"text\":\"[z-mist] " + escapeJson(title) + "\\n" + escapeJson(message) + "\"}}";
            default:
                // 通用 JSON
                return "{\"title\":\"" + escapeJson(title) + "\",\"message\":\"" + escapeJson(message) + "\",\"source\":\"z-mist\",\"timestamp\":\"" + LocalDateTime.now() + "\"}";
        }
    }

    private String escapeJson(String s) {
        if (s == null) {
            return "";
        }

        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    /**
     * 发送 HTTP POST.
     */
    private void doSendHttp(String channel, String target, String jsonBody) {
        try {
            RequestBody body = RequestBody.create(jsonBody, JSON_MEDIA_TYPE);
            Request request = new Request.Builder()
                    .url(target)
                    .post(body)
                    .addHeader("User-Agent", "z-mist-webhook/1.0")
                    .build();
            try (Response response = HTTP_CLIENT.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new RuntimeException("HTTP " + response.code() + " from " + channel);
                }
                log.info("[z-mist WebhookNotifier] {} sent to {}", channel, target);
            }
        } catch (Exception ex) {
            throw new RuntimeException("Webhook send failed: " + channel + " → " + target, ex);
        }
    }
}
