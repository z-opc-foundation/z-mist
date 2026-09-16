package io.github.yuku123.z.mist.web.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.yuku123.z.mist.core.domain.entity.ZMistSecretInfo;
import io.github.yuku123.z.mist.core.domain.entity.ZMistStatsDaily;
import io.github.yuku123.z.mist.core.domain.mapper.ZMistSecretAccessLogMapper;
import io.github.yuku123.z.mist.core.domain.mapper.ZMistSecretInfoMapper;
import io.github.yuku123.z.mist.core.domain.mapper.ZMistStatsDailyMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 数据看板(FEATURE026 P1).
 * <p>
 * 汇总密钥总数、今日操作次数、失败率、TOP 访问密钥 等核心指标。
 * 数据源: z_mist_stats_daily(每日聚合) + 实时统计(今日)。
 *
 * <ul>
 *   <li>GET /api/stats/overview        — 总览(密钥总数/今日操作)</li>
 *   <li>GET /api/stats/daily           — 最近 N 天每日统计</li>
 *   <li>GET /api/stats/top-secrets     — TOP N 访问密钥</li>
 *   <li>GET /api/stats/expiring        — 即将过期密钥(7 天内)</li>
 * </ul>
 */
@Tag(name = "数据看板(FEATURE026)")
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    @Autowired
    private ZMistStatsDailyMapper statsDailyMapper;

    @Autowired
    private ZMistSecretInfoMapper secretInfoMapper;

    @Autowired
    private ZMistSecretAccessLogMapper accessLogMapper;

    @Operation(summary = "总览数据")
    @PreAuthorize("hasAuthority('mist:secret:read') or isAnonymous()")
    @GetMapping("/overview")
    public Map<String, Object> overview() {
        Map<String, Object> result = new HashMap<>();
        // 总密钥数
        long total = secretInfoMapper.selectCount(null);
        // 今日 GET/失败 次数(直接聚合 access_log,避免 stats_daily 不准)
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LambdaQueryWrapper<io.github.yuku123.z.mist.core.domain.entity.ZMistSecretAccessLog> today =
                new LambdaQueryWrapper<>();
        today.ge(io.github.yuku123.z.mist.core.domain.entity.ZMistSecretAccessLog::getGmtCreate, startOfDay);
        long todayOps = accessLogMapper.selectCount(today);
        LambdaQueryWrapper<io.github.yuku123.z.mist.core.domain.entity.ZMistSecretAccessLog> failed =
                new LambdaQueryWrapper<>();
        failed.ge(io.github.yuku123.z.mist.core.domain.entity.ZMistSecretAccessLog::getGmtCreate, startOfDay)
                .eq(io.github.yuku123.z.mist.core.domain.entity.ZMistSecretAccessLog::getSuccess, false);
        long todayFailed = accessLogMapper.selectCount(failed);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalSecrets", total);
        data.put("todayOps", todayOps);
        data.put("todayFailed", todayFailed);
        data.put("successRate", todayOps == 0 ? 100.0 :
                Math.round(((todayOps - todayFailed) * 10000.0) / todayOps) / 100.0);
        result.put("success", true);
        result.put("data", data);
        return result;
    }

    @Operation(summary = "最近 N 天每日统计")
    @PreAuthorize("hasAuthority('mist:secret:read') or isAnonymous()")
    @GetMapping("/daily")
    public Map<String, Object> daily(@RequestParam(defaultValue = "7") int days) {
        Map<String, Object> result = new HashMap<>();
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1L);
        LambdaQueryWrapper<ZMistStatsDaily> wrapper = new LambdaQueryWrapper<>();
        wrapper.between(ZMistStatsDaily::getStatDate, startDate, endDate)
                .orderByAsc(ZMistStatsDaily::getStatDate);
        List<ZMistStatsDaily> list = statsDailyMapper.selectList(wrapper);
        // 补齐缺失日期为 0
        Map<LocalDate, ZMistStatsDaily> map = new HashMap<>();
        for (ZMistStatsDaily s : list) {
            map.put(s.getStatDate(), s);
        }
        List<Map<String, Object>> series = new ArrayList<>();
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            ZMistStatsDaily s = map.get(d);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", d.toString());
            row.put("secretCount", s == null ? 0 : s.getSecretCount());
            row.put("getCount", s == null ? 0 : s.getGetCount());
            row.put("encryptCount", s == null ? 0 : s.getEncryptCount());
            row.put("failedCount", s == null ? 0 : s.getFailedCount());
            series.add(row);
        }
        result.put("success", true);
        result.put("data", series);
        return result;
    }

    @Operation(summary = "TOP N 访问密钥(7 天内)")
    @PreAuthorize("hasAuthority('mist:secret:read') or isAnonymous()")
    @GetMapping("/top-secrets")
    public Map<String, Object> topSecrets(@RequestParam(defaultValue = "10") int limit) {
        Map<String, Object> result = new HashMap<>();
        // 用 access_log 实时聚合:GROUP BY secret_key
        // MyBatis-Plus 简单做法: 取最近 1000 条日志后内存聚合
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        LambdaQueryWrapper<io.github.yuku123.z.mist.core.domain.entity.ZMistSecretAccessLog> wrapper =
                new LambdaQueryWrapper<>();
        wrapper.ge(io.github.yuku123.z.mist.core.domain.entity.ZMistSecretAccessLog::getGmtCreate, since)
                .isNotNull(io.github.yuku123.z.mist.core.domain.entity.ZMistSecretAccessLog::getSecretKey)
                .orderByDesc(io.github.yuku123.z.mist.core.domain.entity.ZMistSecretAccessLog::getGmtCreate)
                .last("LIMIT 1000");
        List<io.github.yuku123.z.mist.core.domain.entity.ZMistSecretAccessLog> logs =
                accessLogMapper.selectList(wrapper);
        Map<String, Long> counter = new HashMap<>();
        for (io.github.yuku123.z.mist.core.domain.entity.ZMistSecretAccessLog l : logs) {
            counter.merge(l.getSecretKey(), 1L, Long::sum);
        }
        List<Map<String, Object>> top = new ArrayList<>();
        counter.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit)
                .forEach(e -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("secretKey", e.getKey());
                    row.put("accessCount", e.getValue());
                    top.add(row);
                });
        result.put("success", true);
        result.put("data", top);
        return result;
    }

    @Operation(summary = "即将过期密钥(7 天内)")
    @PreAuthorize("hasAuthority('mist:secret:read') or isAnonymous()")
    @GetMapping("/expiring")
    public Map<String, Object> expiring() {
        Map<String, Object> result = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime sevenDaysLater = now.plusDays(7);
        LambdaQueryWrapper<ZMistSecretInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNotNull(ZMistSecretInfo::getExpireTime)
                .between(ZMistSecretInfo::getExpireTime, now, sevenDaysLater)
                .orderByAsc(ZMistSecretInfo::getExpireTime);
        List<ZMistSecretInfo> list = secretInfoMapper.selectList(wrapper);
        result.put("success", true);
        result.put("data", list);
        result.put("total", list.size());
        return result;
    }
}
