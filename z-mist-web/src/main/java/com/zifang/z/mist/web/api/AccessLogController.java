package com.zifang.z.mist.web.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zifang.z.mist.common.Result;
import com.zifang.z.mist.core.domain.entity.ZMistSecretAccessLog;
import com.zifang.z.mist.core.domain.mapper.ZMistSecretAccessLogMapper;
import com.zifang.z.mist.web.api.response.AccessLogResp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 访问日志查询(FEATURE026 P1).
 * <p>
 * 对应 z_mist_secret_access_log 表的查询,支持多维筛选。
 *
 * <ul>
 *   <li>GET /api/log/page       — 分页查询</li>
 *   <li>GET /api/log/recent     — 最近 N 条</li>
 *   <li>GET /api/log/failed     — 失败日志</li>
 * </ul>
 */
@Tag(name = "访问日志(FEATURE026)")
@RestController("zMistAccessLogController")
@RequestMapping("/api/log")
public class AccessLogController {

    @Autowired
    private ZMistSecretAccessLogMapper accessLogMapper;

    @Operation(summary = "分页查询访问日志")
    @PreAuthorize("hasAuthority('mist:secret:read') or isAnonymous()")
    @GetMapping("/page")
    public Map<String, Object> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String secretKey,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String opType,
            @RequestParam(required = false) Integer success,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        // 不规则聚合输出(total 自定义 key), 保持原状
        Map<String, Object> result = new HashMap<>();
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<ZMistSecretAccessLog> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(current, size);
        LambdaQueryWrapper<ZMistSecretAccessLog> wrapper = new LambdaQueryWrapper<>();
        if (secretKey != null && !secretKey.isEmpty()) {
            wrapper.like(ZMistSecretAccessLog::getSecretKey, secretKey);
        }
        if (operator != null && !operator.isEmpty()) {
            wrapper.eq(ZMistSecretAccessLog::getOperator, operator);
        }
        if (opType != null && !opType.isEmpty()) {
            wrapper.eq(ZMistSecretAccessLog::getOpType, opType);
        }
        if (success != null) {
            wrapper.eq(ZMistSecretAccessLog::getSuccess, success != 0);
        }
        if (startTime != null && !startTime.isEmpty()) {
            wrapper.ge(ZMistSecretAccessLog::getGmtCreate, LocalDateTime.parse(startTime));
        }
        if (endTime != null && !endTime.isEmpty()) {
            wrapper.le(ZMistSecretAccessLog::getGmtCreate, LocalDateTime.parse(endTime));
        }
        wrapper.orderByDesc(ZMistSecretAccessLog::getGmtCreate);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<ZMistSecretAccessLog> p =
                accessLogMapper.selectPage(page, wrapper);
        result.put("success", true);
        result.put("data", p.getRecords());
        result.put("total", p.getTotal());
        return result;
    }

    @Operation(summary = "查询最近 N 条日志")
    @PreAuthorize("hasAuthority('mist:secret:read') or isAnonymous()")
    @GetMapping("/recent")
    public Result<List<AccessLogResp>> recent(@RequestParam(defaultValue = "10") int limit) {
        Result<List<AccessLogResp>> result = new Result<>();
        LambdaQueryWrapper<ZMistSecretAccessLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(ZMistSecretAccessLog::getGmtCreate).last("LIMIT " + limit);
        List<ZMistSecretAccessLog> logs = accessLogMapper.selectList(wrapper);
        result.setSuccess(true);
        result.setData(toRespList(logs));
        return result;
    }

    @Operation(summary = "查询失败日志")
    @PreAuthorize("hasAuthority('mist:secret:read') or isAnonymous()")
    @GetMapping("/failed")
    public Result<List<AccessLogResp>> failed(@RequestParam(defaultValue = "50") int limit) {
        Result<List<AccessLogResp>> result = new Result<>();
        LambdaQueryWrapper<ZMistSecretAccessLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ZMistSecretAccessLog::getSuccess, false)
                .orderByDesc(ZMistSecretAccessLog::getGmtCreate)
                .last("LIMIT " + limit);
        List<ZMistSecretAccessLog> logs = accessLogMapper.selectList(wrapper);
        result.setSuccess(true);
        result.setData(toRespList(logs));
        return result;
    }

    private List<AccessLogResp> toRespList(List<ZMistSecretAccessLog> logs) {
        List<AccessLogResp> respList = new ArrayList<>(logs.size());
        for (ZMistSecretAccessLog log : logs) {
            AccessLogResp resp = new AccessLogResp();
            BeanUtils.copyProperties(log, resp);
            respList.add(resp);
        }
        return respList;
    }
}
