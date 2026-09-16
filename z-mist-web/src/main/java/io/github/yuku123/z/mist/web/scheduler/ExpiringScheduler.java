package io.github.yuku123.z.mist.web.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.yuku123.z.mist.core.domain.entity.ZMistSecretInfo;
import io.github.yuku123.z.mist.core.domain.mapper.ZMistNotificationLogMapper;
import io.github.yuku123.z.mist.core.domain.mapper.ZMistSecretInfoMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 过期告警调度器(FEATURE026 P1).
 * <p>
 * 每小时扫描 z_mist_secret_info:
 * - T-7 天内即将过期:写 expiring 通知(可扩展 webhook)
 * - 已过期:写 expired 通知
 * <p>
 * 默认通知渠道 channel=log,只在 DB 留痕。如需 webhook/email,扩展 NotificationDispatcher。
 */
@Component
public class ExpiringScheduler {

    private static final Logger log = LogManager.getLogger(ExpiringScheduler.class);

    @Autowired
    private ZMistSecretInfoMapper secretInfoMapper;

    @Autowired
    private ZMistNotificationLogMapper notificationLogMapper;

    @Autowired
    private WebhookNotifier webhookNotifier;

    /**
     * 可通过环境变量或系统属性覆盖;默认 log 渠道
     */
    private String notifyChannel = System.getProperty("z.mist.notify.channel", "log");
    private String notifyTarget = System.getProperty("z.mist.notify.target", "");

    /**
     * 每小时第 5 分钟扫描.
     */
    @Scheduled(cron = "0 5 * * * ?")
    public void scan() {
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime sevenDaysLater = now.plusDays(7);

            // 即将过期
            LambdaQueryWrapper<ZMistSecretInfo> expiringWrapper = new LambdaQueryWrapper<>();
            expiringWrapper.isNotNull(ZMistSecretInfo::getExpireTime)
                    .between(ZMistSecretInfo::getExpireTime, now, sevenDaysLater);
            List<ZMistSecretInfo> expiringList = secretInfoMapper.selectList(expiringWrapper);

            // 已过期
            LambdaQueryWrapper<ZMistSecretInfo> expiredWrapper = new LambdaQueryWrapper<>();
            expiredWrapper.isNotNull(ZMistSecretInfo::getExpireTime)
                    .lt(ZMistSecretInfo::getExpireTime, now);
            List<ZMistSecretInfo> expiredList = secretInfoMapper.selectList(expiredWrapper);

            if (expiringList.isEmpty() && expiredList.isEmpty()) {
                return;
            }
            log.info("[z-mist ExpiringScheduler] expiring={}, expired={}",
                    expiringList.size(), expiredList.size());

            expiringList.forEach(s -> writeNotification("expiring", s, "即将过期"));
            expiredList.forEach(s -> writeNotification("expired", s, "已过期"));
        } catch (Exception ex) {
            log.warn("[z-mist ExpiringScheduler] scan failed: {}", ex.getMessage());
        }
    }

    private void writeNotification(String type, ZMistSecretInfo secret, String message) {
        try {
            String title = "[z-mist] " + type + " - " + secret.getSecretKey();
            String detail = "密钥: " + secret.getSecretName() + " (" + secret.getSecretKey() + ")\n"
                    + "分组: " + secret.getGroup() + " | 命名空间: " + secret.getNamespace() + "\n"
                    + "过期时间: " + (secret.getExpireTime() != null ? secret.getExpireTime().toString() : "未知") + "\n"
                    + "状态: " + message;
            // FEATURE065 T3: 使用 WebhookNotifier 发送通知（支持 log/dingtalk/wecom/feishu/generic）
            webhookNotifier.send(type, secret.getSecretKey(), secret.getGroup(), secret.getNamespace(),
                    notifyChannel, notifyTarget, title, detail);
            log.warn("[z-mist] notification {}: secretKey={} {}", type, secret.getSecretKey(), message);
        } catch (Exception ex) {
            log.warn("[z-mist ExpiringScheduler] write notification failed: {}", ex.getMessage());
        }
    }
}
