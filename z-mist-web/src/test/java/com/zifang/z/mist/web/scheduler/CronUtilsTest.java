package com.zifang.z.mist.web.scheduler;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FEATURE065 T2: cron-utils 解析测试.
 * <p>
 * 验证 Quartz cron 表达式的解析和下次执行时间计算。
 */
public class CronUtilsTest {

    private CronParser parser;

    @BeforeEach
    void setUp() {
        CronDefinition cronDef = CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ);
        parser = new CronParser(cronDef);
    }

    private LocalDateTime nextExecution(Cron cron, LocalDateTime from) {
        ExecutionTime et = ExecutionTime.forCron(cron);
        Optional<ZonedDateTime> next = et.nextExecution(from.atZone(ZoneId.systemDefault()));
        return next.isPresent() ? next.get().toLocalDateTime() : null;
    }

    @Test
    void testDailyMidnight() {
        // 0 0 0 * * ? — 每天 0 点
        Cron cron = parser.parse("0 0 0 * * ?");
        LocalDateTime now = LocalDateTime.of(2026, 9, 6, 14, 30);
        LocalDateTime next = nextExecution(cron, now);
        assertNotNull(next);
        assertEquals(0, next.getHour());
        assertEquals(0, next.getMinute());
        assertTrue(next.isAfter(now));
    }

    @Test
    void testEveryWeekday() {
        // 0 0 9 ? * MON-FRI — 工作日 9 点
        Cron cron = parser.parse("0 0 9 ? * MON-FRI");
        LocalDateTime saturday = LocalDateTime.of(2026, 9, 6, 10, 0); // 周六
        LocalDateTime next = nextExecution(cron, saturday);
        assertNotNull(next);
        assertEquals(9, next.getHour());
        assertTrue(next.getDayOfWeek().getValue() <= 5); // Mon-Fri
    }

    @Test
    void testEvery7Days() {
        // 0 0 12 1/7 * ? — 每 7 天 12 点
        Cron cron = parser.parse("0 0 12 1/7 * ?");
        LocalDateTime from = LocalDateTime.of(2026, 9, 6, 14, 0);
        LocalDateTime next = nextExecution(cron, from);
        assertNotNull(next);
        assertEquals(12, next.getHour());
        long daysBetween = ChronoUnit.DAYS.between(from.toLocalDate(), next.toLocalDate());
        assertTrue(daysBetween >= 1 && daysBetween <= 8, "Next should be within 7 days, got " + daysBetween);
    }

    @Test
    void testInvalidCronFallback() {
        // 无效 cron 表达式会抛异常，调用者应 catch 并 fallback
        assertThrows(Exception.class, () -> parser.parse("INVALID CRON"));
    }

    @Test
    void testQuartzSixBitFormat() {
        String[] crons = {
                "0 0 0 * * ?",       // 每天0点
                "0 30 8 * * ?",      // 每天8:30（6位格式）
                "0 0/30 * * * ?",    // 每30分钟（6位格式）
                "0 0 9 ? * MON-FRI", // 工作日9点
        };
        for (String c : crons) {
            Cron parsed = parser.parse(c);
            assertNotNull(parsed, "Cron should parse: " + c);
            LocalDateTime from = LocalDateTime.now();
            LocalDateTime next = nextExecution(parsed, from);
            assertNotNull(next, "Next execution should exist for: " + c);
            assertTrue(next.isAfter(from), "Next should be in future: " + c);
        }
    }
}
