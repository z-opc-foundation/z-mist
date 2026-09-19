package com.zifang.z.mist.web.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zifang.z.mist.core.domain.entity.ZMistRotationHistory;
import com.zifang.z.mist.core.domain.entity.ZMistRotationPolicy;
import com.zifang.z.mist.core.domain.mapper.ZMistRotationHistoryMapper;
import com.zifang.z.mist.core.domain.mapper.ZMistRotationPolicyMapper;
import com.zifang.z.mist.core.domain.service.IZMistSecretService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 自动轮换策略管理(FEATURE026 P1 + FEATURE065 审计增强).
 * <p>
 * 对应 z_mist_secret_rotation_policy 与 z_mist_secret_rotation_history。
 * 配合 {@code RotationScheduler} 每分钟扫描到期的策略执行轮换。
 * <p>
 * FEATURE065: 轮换策略变更和触发操作记录到 access_log(op_type=ROTATION_CREATE/ROTATION_DELETE/ROTATION_TRIGGER)
 *
 * <ul>
 *   <li>POST   /api/rotation/policy             — 创建策略</li>
 *   <li>PUT    /api/rotation/policy/{id}        — 更新策略</li>
 *   <li>DELETE /api/rotation/policy/{id}        — 删除策略</li>
 *   <li>GET    /api/rotation/policy/page        — 分页查询策略</li>
 *   <li>POST   /api/rotation/policy/{id}/trigger — 立即触发</li>
 *   <li>GET    /api/rotation/history/page       — 查询轮换历史</li>
 * </ul>
 */
@Tag(name = "轮换策略(FEATURE026)")
@RestController
@RequestMapping("/api/rotation")
public class RotationPolicyController {

    @Autowired
    private ZMistRotationPolicyMapper policyMapper;

    @Autowired
    private ZMistRotationHistoryMapper historyMapper;

    @Autowired
    private IZMistSecretService secretService;

    @Operation(summary = "创建轮换策略")
    @PreAuthorize("hasAuthority('mist:secret:write') or isAnonymous()")
    @PostMapping("/policy")
    public Map<String, Object> create(@RequestBody ZMistRotationPolicy body, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        if (body.getSecretKey() == null || body.getCronExpression() == null) {
            result.put("success", false);
            result.put("message", "secretKey 与 cronExpression 必填");
            return result;
        }
        if (body.getGroup() == null) {
            body.setGroup("DEFAULT_GROUP");
        }
        if (body.getNamespace() == null) {
            body.setNamespace("");
        }
        if (body.getRotationStrategy() == null) {
            body.setRotationStrategy("auto");
        }
        if (body.getNewValueLength() == null) {
            body.setNewValueLength(32);
        }
        if (body.getEnabled() == null) {
            body.setEnabled(1);
        }
        LocalDateTime now = LocalDateTime.now();
        body.setGmtCreate(now);
        body.setGmtModified(now);
        policyMapper.insert(body);
        result.put("success", true);
        result.put("data", body);
        // FEATURE065: 审计
        secretService.recordAccess(body.getSecretKey(), body.getGroup(), body.getNamespace(),
                "ROTATION_CREATE", currentOperator(request), currentIp(request), true,
                "cron=" + body.getCronExpression());
        return result;
    }

    @Operation(summary = "更新轮换策略")
    @PreAuthorize("hasAuthority('mist:secret:write') or isAnonymous()")
    @PutMapping("/policy/{id}")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody ZMistRotationPolicy body, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        body.setId(id);
        body.setGmtModified(LocalDateTime.now());
        policyMapper.updateById(body);
        result.put("success", true);
        result.put("data", policyMapper.selectById(id));
        // FEATURE065: 审计
        secretService.recordAccess(body.getSecretKey(), body.getGroup(), body.getNamespace(),
                "ROTATION_UPDATE", currentOperator(request), currentIp(request), true, null);
        return result;
    }

    @Operation(summary = "删除轮换策略")
    @PreAuthorize("hasAuthority('mist:secret:write') or isAnonymous()")
    @DeleteMapping("/policy/{id}")
    public Map<String, Object> delete(@PathVariable Long id, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        ZMistRotationPolicy policy = policyMapper.selectById(id);
        int rows = policyMapper.deleteById(id);
        result.put("success", rows > 0);
        // FEATURE065: 审计
        if (policy != null) {
            secretService.recordAccess(policy.getSecretKey(), policy.getGroup(), policy.getNamespace(),
                    "ROTATION_DELETE", currentOperator(request), currentIp(request), rows > 0,
                    rows > 0 ? null : "delete_failed");
        }
        return result;
    }

    @Operation(summary = "分页查询轮换策略")
    @PreAuthorize("hasAuthority('mist:secret:read') or isAnonymous()")
    @GetMapping("/policy/page")
    public Map<String, Object> policyPage(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String secretKey) {
        Map<String, Object> result = new HashMap<>();
        Page<ZMistRotationPolicy> page = new Page<>(current, size);
        LambdaQueryWrapper<ZMistRotationPolicy> wrapper = new LambdaQueryWrapper<>();
        if (secretKey != null && !secretKey.isEmpty()) {
            wrapper.like(ZMistRotationPolicy::getSecretKey, secretKey);
        }
        wrapper.orderByDesc(ZMistRotationPolicy::getGmtCreate);
        Page<ZMistRotationPolicy> p = policyMapper.selectPage(page, wrapper);
        result.put("success", true);
        result.put("data", p.getRecords());
        result.put("total", p.getTotal());
        return result;
    }

    @Operation(summary = "立即触发轮换")
    @PreAuthorize("hasAuthority('mist:secret:write') or isAnonymous()")
    @PostMapping("/policy/{id}/trigger")
    public Map<String, Object> trigger(@PathVariable Long id, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        ZMistRotationPolicy policy = policyMapper.selectById(id);
        if (policy == null) {
            result.put("success", false);
            result.put("message", "策略不存在");
            return result;
        }
        try {
            secretService.rotateNow(policy.getSecretKey(), policy.getGroup(),
                    policy.getNamespace(), policy.getNewValueLength());
            // 写轮换历史
            ZMistRotationHistory history = new ZMistRotationHistory();
            history.setPolicyId(id);
            history.setSecretKey(policy.getSecretKey());
            history.setGroup(policy.getGroup());
            history.setNamespace(policy.getNamespace());
            history.setTriggerType("manual");
            history.setTriggerBy(currentOperator(request));
            history.setSuccess(1);
            history.setGmtCreate(LocalDateTime.now());
            historyMapper.insert(history);
            // 更新策略 last_rotation_time
            policy.setLastRotationTime(LocalDateTime.now());
            policy.setGmtModified(LocalDateTime.now());
            policyMapper.updateById(policy);
            result.put("success", true);
            result.put("message", "触发成功");
            // FEATURE065: 审计
            secretService.recordAccess(policy.getSecretKey(), policy.getGroup(), policy.getNamespace(),
                    "ROTATION_TRIGGER", currentOperator(request), currentIp(request), true, null);
        } catch (Exception e) {
            // 失败也写历史
            ZMistRotationHistory history = new ZMistRotationHistory();
            history.setPolicyId(id);
            history.setSecretKey(policy.getSecretKey());
            history.setGroup(policy.getGroup());
            history.setNamespace(policy.getNamespace());
            history.setTriggerType("manual");
            history.setTriggerBy(currentOperator(request));
            history.setSuccess(0);
            history.setErrorMessage(e.getMessage());
            history.setGmtCreate(LocalDateTime.now());
            historyMapper.insert(history);
            result.put("success", false);
            result.put("message", e.getMessage());
            // FEATURE065: 审计
            secretService.recordAccess(policy.getSecretKey(), policy.getGroup(), policy.getNamespace(),
                    "ROTATION_TRIGGER", currentOperator(request), currentIp(request), false, e.getMessage());
        }
        return result;
    }

    @Operation(summary = "分页查询轮换历史")
    @PreAuthorize("hasAuthority('mist:secret:read') or isAnonymous()")
    @GetMapping("/history/page")
    public Map<String, Object> historyPage(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String secretKey,
            @RequestParam(required = false) String triggerType) {
        Map<String, Object> result = new HashMap<>();
        Page<ZMistRotationHistory> page = new Page<>(current, size);
        LambdaQueryWrapper<ZMistRotationHistory> wrapper = new LambdaQueryWrapper<>();
        if (secretKey != null && !secretKey.isEmpty()) {
            wrapper.eq(ZMistRotationHistory::getSecretKey, secretKey);
        }
        if (triggerType != null && !triggerType.isEmpty()) {
            wrapper.eq(ZMistRotationHistory::getTriggerType, triggerType);
        }
        wrapper.orderByDesc(ZMistRotationHistory::getGmtCreate);
        Page<ZMistRotationHistory> p = historyMapper.selectPage(page, wrapper);
        result.put("success", true);
        result.put("data", p.getRecords());
        result.put("total", p.getTotal());
        return result;
    }

    private String currentOperator(HttpServletRequest request) {
        String xff = request.getHeader("X-Staff-No");
        return (xff != null && !xff.isEmpty()) ? xff : "anonymous";
    }

    private String currentIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
