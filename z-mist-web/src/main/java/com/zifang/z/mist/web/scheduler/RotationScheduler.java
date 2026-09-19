package com.zifang.z.mist.web.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import com.zifang.z.mist.core.domain.entity.ZMistRotationHistory;
import com.zifang.z.mist.core.domain.entity.ZMistRotationPolicy;
import com.zifang.z.mist.core.domain.entity.ZMistSecretInfo;
import com.zifang.z.mist.core.domain.mapper.ZMistRotationHistoryMapper;
import com.zifang.z.mist.core.domain.mapper.ZMistRotationPolicyMapper;
import com.zifang.z.mist.core.domain.service.IZMistSecretService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 密钥自动轮换调度器(FEATURE026 P1 + FEATURE065 T2 cron-utils 真实解析).
 * <p>
 * 每分钟扫描 z_mist_secret_rotation_policy 表:
 * <ul>
 *   <li>enabled = 1</li>
 *   <li>next_rotation_time <= now</li>
 * </ul>
 * 对满足条件的策略执行 rotateNow,记录轮换历史,并更新 last/next 时间。
 * <p>
 * FEATURE065 T2: 使用 cron-utils 库真实解析 Quartz cron 表达式（6 位标准格式）。
 * 计算 next_rotation_time = 上次执行时间 + cron 间隔。
 * <p>
 * 使用示例:
 * <ul>
 *   <li>{@code 0 0 0 * * ?} — 每天 0 点</li>
 *   <li>{@code 0 0 12 1/7 * ?} — 每 7 天 12 点</li>
 *   <li>{@code 0 0 9 ? * MON-FRI} — 工作日 9 点</li>
 * </ul>
 */
@Component
public class RotationScheduler {

    private static final Logger log = LogManager.getLogger(RotationScheduler.class);
    private final CronParser cronParser;
    @Autowired
    private ZMistRotationPolicyMapper policyMapper;
    @Autowired
    private ZMistRotationHistoryMapper historyMapper;
    @Autowired
    private IZMistSecretService secretService;

    public RotationScheduler() {
        // Quartz cron 定义（6 位: 秒 分 时 日 月 周）
        CronDefinition cronDef = CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ);
        this.cronParser = new CronParser(cronDef);
    }

    /**
     * 每 60 秒扫描一次.
     */
    @Scheduled(fixedRate = 60_000L)
    public void scan() {
        try {
            LocalDateTime now = LocalDateTime.now();
            LambdaQueryWrapper<ZMistRotationPolicy> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ZMistRotationPolicy::getEnabled, 1)
                    .le(ZMistRotationPolicy::getNextRotationTime, now);
            List<ZMistRotationPolicy> dueList = policyMapper.selectList(wrapper);
            if (dueList.isEmpty()) {
                return;
            }
            log.info("[z-mist RotationScheduler] due policies count={}", dueList.size());
            for (ZMistRotationPolicy policy : dueList) {
                rotateOne(policy, now);
            }
        } catch (Exception ex) {
            log.warn("[z-mist RotationScheduler] scan failed: {}", ex.getMessage());
        }
    }

    private void rotateOne(ZMistRotationPolicy policy, LocalDateTime now) {
        ZMistRotationHistory history = new ZMistRotationHistory();
        history.setPolicyId(policy.getId());
        history.setSecretKey(policy.getSecretKey());
        history.setGroup(policy.getGroup());
        history.setNamespace(policy.getNamespace());
        history.setTriggerType("cron");
        history.setGmtCreate(now);
        try {
            String oldVer = "v0";
            ZMistSecretInfo oldSecret = secretService.getSecret(policy.getSecretKey(),
                    policy.getGroup(), policy.getNamespace());
            if (oldSecret != null) {
                oldVer = oldSecret.getKeyVersion();
            }
            secretService.rotateNow(policy.getSecretKey(), policy.getGroup(),
                    policy.getNamespace(), policy.getNewValueLength());
            String newVer = oldVer;
            ZMistSecretInfo newSecret = secretService.getSecret(policy.getSecretKey(),
                    policy.getGroup(), policy.getNamespace());
            if (newSecret != null) {
                newVer = newSecret.getKeyVersion();
            }
            history.setOldVersion(oldVer);
            history.setNewVersion(newVer);
            history.setSuccess(1);
            policy.setLastRotationTime(now);

            // FEATURE065 T2: 用 cron-utils 计算下次执行时间
            LocalDateTime nextTime = calculateNextExecution(policy.getCronExpression(), now);
            policy.setNextRotationTime(nextTime);
            policy.setGmtModified(now);
            policyMapper.updateById(policy);
            log.info("[z-mist RotationScheduler] rotated {}/{}/{}  {}→{}  next={}",
                    policy.getSecretKey(), policy.getGroup(), policy.getNamespace(),
                    oldVer, newVer, nextTime);
        } catch (Exception ex) {
            history.setSuccess(0);
            history.setErrorMessage(ex.getMessage());
            // 失败时尝试计算下次执行时间
            try {
                LocalDateTime nextTime = calculateNextExecution(policy.getCronExpression(), now);
                policy.setNextRotationTime(nextTime);
            } catch (Exception ignore) {
                policy.setNextRotationTime(now.plusDays(1));
            }
            policy.setGmtModified(now);
            policyMapper.updateById(policy);
            log.warn("[z-mist RotationScheduler] rotate failed: {}/{}/{} - {}",
                    policy.getSecretKey(), policy.getGroup(), policy.getNamespace(), ex.getMessage());
        } finally {
            historyMapper.insert(history);
        }
    }

    /**
     * FEATURE065 T2: 用 cron-utils 解析 cron 表达式,计算下一次执行时间.
     *
     * @param cronExpr Quartz cron 表达式(6 位)
     * @param from     参考时间
     * @return 下一次执行时间
     */
    private LocalDateTime calculateNextExecution(String cronExpr, LocalDateTime from) {
        try {
            Cron cron = cronParser.parse(cronExpr);
            ExecutionTime executionTime = ExecutionTime.forCron(cron);
            java.time.ZonedDateTime fromZdt = from.atZone(ZoneId.systemDefault());
            java.util.Optional<java.time.ZonedDateTime> nextOpt = executionTime.nextExecution(fromZdt);
            if (nextOpt.isPresent()) {
                return nextOpt.get().toLocalDateTime();
            }
        } catch (Exception ex) {
            log.warn("[z-mist] cron parse failed for '{}': {}", cronExpr, ex.getMessage());
        }
        // fallback: 明天同一时间
        return from.plusDays(1);
    }
}
