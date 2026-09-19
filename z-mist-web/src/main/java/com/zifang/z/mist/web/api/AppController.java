package com.zifang.z.mist.web.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zifang.z.mist.core.domain.entity.ZMistAppInfo;
import com.zifang.z.mist.core.domain.mapper.ZMistAppInfoMapper;
import com.zifang.z.mist.core.domain.service.IZMistSecretService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 应用注册管理(FEATURE026 P1 + FEATURE065 审计增强).
 * <p>
 * 对应 z_mist_app_info 表的 CRUD。
 * app_secret 默认生成 32 位随机串,创建后明文返回一次,后续只显示指纹。
 * <p>
 * FEATURE065: 所有应用变更操作记录到 access_log(op_type=APP_CREATE/APP_UPDATE/APP_DELETE/APP_RESET_SECRET)
 *
 * <ul>
 *   <li>POST   /api/app                  — 创建应用</li>
 *   <li>PUT    /api/app/{id}             — 更新应用</li>
 *   <li>DELETE /api/app/{id}             — 删除应用</li>
 *   <li>GET    /api/app/page             — 分页查询</li>
 *   <li>GET    /api/app/{id}             — 详情</li>
 *   <li>POST   /api/app/{id}/reset-secret — 重置 appSecret</li>
 * </ul>
 */
@Tag(name = "应用管理(FEATURE026)")
@RestController
@RequestMapping("/api/app")
public class AppController {

    private static final String SECRET_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    @Autowired
    private ZMistAppInfoMapper appMapper;
    @Autowired
    private IZMistSecretService secretService;

    @Operation(summary = "创建应用")
    @PreAuthorize("hasAuthority('mist:secret:write') or isAnonymous()")
    @PostMapping
    public Map<String, Object> create(@RequestBody ZMistAppInfo body, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        if (body.getAppName() == null || body.getAppName().isEmpty()) {
            result.put("success", false);
            result.put("message", "appName 必填");
            return result;
        }
        if (body.getAppSecret() == null || body.getAppSecret().isEmpty()) {
            body.setAppSecret(generateAppSecret(32));
        }
        if (body.getAppType() == null) {
            body.setAppType("server");
        }
        if (body.getNamespace() == null) {
            body.setNamespace("");
        }
        if (body.getEnabled() == null) {
            body.setEnabled(1);
        }
        LocalDateTime now = LocalDateTime.now();
        body.setGmtCreate(now);
        body.setGmtModified(now);
        appMapper.insert(body);
        result.put("success", true);
        result.put("data", body);
        // FEATURE065: 审计
        secretService.recordAccess("APP:" + body.getAppName(), null, null,
                "APP_CREATE", currentOperator(request), currentIp(request), true, null);
        return result;
    }

    @Operation(summary = "更新应用")
    @PreAuthorize("hasAuthority('mist:secret:write') or isAnonymous()")
    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody ZMistAppInfo body, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        body.setId(id);
        body.setGmtModified(LocalDateTime.now());
        appMapper.updateById(body);
        result.put("success", true);
        result.put("data", appMapper.selectById(id));
        // FEATURE065: 审计
        secretService.recordAccess("APP:" + id, null, null,
                "APP_UPDATE", currentOperator(request), currentIp(request), true, null);
        return result;
    }

    @Operation(summary = "删除应用")
    @PreAuthorize("hasAuthority('mist:secret:write') or isAnonymous()")
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        ZMistAppInfo app = appMapper.selectById(id);
        int rows = appMapper.deleteById(id);
        result.put("success", rows > 0);
        result.put("message", rows > 0 ? "删除成功" : "应用不存在");
        // FEATURE065: 审计
        if (app != null) {
            secretService.recordAccess("APP:" + app.getAppName(), null, null,
                    "APP_DELETE", currentOperator(request), currentIp(request), rows > 0,
                    rows > 0 ? null : "delete_failed");
        }
        return result;
    }

    @Operation(summary = "分页查询应用")
    @PreAuthorize("hasAuthority('mist:secret:read') or isAnonymous()")
    @GetMapping("/page")
    public Map<String, Object> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String appName,
            @RequestParam(required = false) String namespace) {
        Map<String, Object> result = new HashMap<>();
        Page<ZMistAppInfo> page = new Page<>(current, size);
        LambdaQueryWrapper<ZMistAppInfo> wrapper = new LambdaQueryWrapper<>();
        if (appName != null && !appName.isEmpty()) {
            wrapper.like(ZMistAppInfo::getAppName, appName);
        }
        if (namespace != null && !namespace.isEmpty()) {
            wrapper.eq(ZMistAppInfo::getNamespace, namespace);
        }
        wrapper.orderByDesc(ZMistAppInfo::getGmtCreate);
        Page<ZMistAppInfo> p = appMapper.selectPage(page, wrapper);
        result.put("success", true);
        result.put("data", p.getRecords());
        result.put("total", p.getTotal());
        return result;
    }

    @Operation(summary = "查询应用详情")
    @PreAuthorize("hasAuthority('mist:secret:read') or isAnonymous()")
    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        ZMistAppInfo app = appMapper.selectById(id);
        result.put("success", app != null);
        result.put("data", app);
        return result;
    }

    @Operation(summary = "重置 appSecret")
    @PreAuthorize("hasAuthority('mist:secret:write') or isAnonymous()")
    @PostMapping("/{id}/reset-secret")
    public Map<String, Object> resetSecret(@PathVariable Long id, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        ZMistAppInfo app = appMapper.selectById(id);
        if (app == null) {
            result.put("success", false);
            result.put("message", "应用不存在");
            return result;
        }
        String newSecret = generateAppSecret(32);
        app.setAppSecret(newSecret);
        app.setGmtModified(LocalDateTime.now());
        appMapper.updateById(app);
        result.put("success", true);
        Map<String, Object> data = new HashMap<>();
        data.put("id", id);
        data.put("appName", app.getAppName());
        data.put("newSecret", newSecret);
        result.put("data", data);
        result.put("message", "新 secret 仅返回一次,请妥善保存");
        // FEATURE065: 审计
        secretService.recordAccess("APP:" + app.getAppName(), null, null,
                "APP_RESET_SECRET", currentOperator(request), currentIp(request), true, null);
        return result;
    }

    private String generateAppSecret(int length) {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(SECRET_CHARS.charAt(random.nextInt(SECRET_CHARS.length())));
        }
        return sb.toString();
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
