package com.zifang.z.mist.web.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zifang.z.mist.core.domain.entity.ZMistSecretAccessLog;
import com.zifang.z.mist.core.domain.entity.ZMistStatsDaily;
import com.zifang.z.mist.core.domain.mapper.ZMistSecretAccessLogMapper;
import com.zifang.z.mist.core.domain.mapper.ZMistSecretInfoMapper;
import com.zifang.z.mist.core.domain.mapper.ZMistStatsDailyMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 每日统计聚合器(FEATURE026 P1).
 * <p>
 * 每天凌晨 1 点把前一天的 access_log 数据聚合写入 z_mist_stats_daily。
 * 也可手动调用 {@link #aggregateDay(LocalDate)} 补数据。
 */
@Component
public class StatsAggregator {

    private static final Logger log = LogManager.getLogger(StatsAggregator.class);

    @Autowired
    private ZMistStatsDailyMapper statsDailyMapper;

    @Autowired
    private ZMistSecretAccessLogMapper accessLogMapper;

    @Autowired
    private ZMistSecretInfoMapper secretInfoMapper;

    @Scheduled(cron = "0 0 1 * * ?")
    public void aggregateYesterday() {
        aggregateDay(LocalDate.now().minusDays(1));
    }

    public ZMistStatsDaily aggregateDay(LocalDate date) {
        try {
            LocalDateTime startOfDay = date.atStartOfDay();
            LocalDateTime endOfDay = date.atTime(LocalTime.MAX);
            LambdaQueryWrapper<ZMistSecretAccessLog> wrapper = new LambdaQueryWrapper<>();
            wrapper.between(ZMistSecretAccessLog::getGmtCreate, startOfDay, endOfDay);
            java.util.List<ZMistSecretAccessLog> logs = accessLogMapper.selectList(wrapper);
            int getCount = 0, encryptCount = 0, decryptCount = 0, failedCount = 0;
            for (ZMistSecretAccessLog l : logs) {
                String op = l.getOpType();
                if (op == null) {
                    continue;
                }
                if (op.equals("GET") || op.equals("LIST") || op.equals("GET_PLAIN")
                        || op.equals("HISTORY") || op.equals("SEARCH")) {
                    getCount++;
                } else if (op.equals("EaaS_ENCRYPT")) {
                    encryptCount++;
                } else if (op.equals("EaaS_DECRYPT")) {
                    decryptCount++;
                }
                if (l.getSuccess() != null && !l.getSuccess()) {
                    failedCount++;
                }
            }
            long totalSecrets = secretInfoMapper.selectCount(null);
            ZMistStatsDaily stats = new ZMistStatsDaily();
            stats.setStatDate(date);
            stats.setSecretCount((int) totalSecrets);
            stats.setGetCount(getCount);
            stats.setEncryptCount(encryptCount);
            stats.setDecryptCount(decryptCount);
            stats.setFailedCount(failedCount);
            stats.setNewCount(0);
            stats.setUpdateCount(0);
            stats.setDeleteCount(0);
            stats.setRotationCount(0);
            LocalDateTime now = LocalDateTime.now();
            stats.setGmtCreate(now);
            stats.setGmtModified(now);

            // upsert
            LambdaQueryWrapper<ZMistStatsDaily> existWrapper = new LambdaQueryWrapper<>();
            existWrapper.eq(ZMistStatsDaily::getStatDate, date);
            ZMistStatsDaily existing = statsDailyMapper.selectOne(existWrapper);
            if (existing != null) {
                stats.setId(existing.getId());
                statsDailyMapper.updateById(stats);
            } else {
                statsDailyMapper.insert(stats);
            }
            log.info("[z-mist StatsAggregator] {} get={} encrypt={} decrypt={} failed={}",
                    date, getCount, encryptCount, decryptCount, failedCount);
            return stats;
        } catch (Exception ex) {
            log.warn("[z-mist StatsAggregator] aggregate {} failed: {}", date, ex.getMessage());
            return null;
        }
    }
}
