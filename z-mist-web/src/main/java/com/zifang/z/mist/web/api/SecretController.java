package com.zifang.z.mist.web.api;

import com.zifang.z.mist.common.Constance;
import com.zifang.z.mist.core.domain.entity.ZMistSecretInfo;
import com.zifang.z.mist.core.domain.service.IZMistSecretService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 密钥管理控制器.
 * <p>
 * API 基础路径: /api/secret
 * 所属模块: z-mist-admin
 * 鉴权: 基于 Spring Security 的 @PreAuthorize 注解,支持 mist:secret:read / mist:secret:write 权限或匿名访问
 *
 * <p>主要端点:
 * <ul>
 *   <li>POST / — 保存密钥</li>
 *   <li>PUT / — 更新密钥</li>
 *   <li>DELETE /{secretKey} — 删除密钥</li>
 *   <li>GET /get — 获取密钥详情</li>
 *   <li>GET /list — 密钥列表</li>
 * </ul>
 *
 * <p>FEATURE021 增强：每次操作写 z_mist_secret_access_log（通过 IZMistSecretService.recordAccess）。
 * FEATURE022 增强：@PreAuthorize 权限注解 + 简单内存限流（防爆破）。
 */
@Tag(name = "密钥管理")
@RestController
@RequestMapping("/api/secret")
public class SecretController {

    // FEATURE022: 简单内存限流（每 IP 每分钟最多 N 次）。生产应换 Bucket4j / Sentinel。
    private static final int GET_PER_MIN = 60;
    private static final int PUT_PER_MIN = 10;
    private static final int DELETE_PER_MIN = 5;
    private static final long WINDOW_MS = 60_000L;
    private final ConcurrentHashMap<String, WindowCounter> getCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, WindowCounter> putCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, WindowCounter> deleteCounters = new ConcurrentHashMap<>();
    @Autowired
    private IZMistSecretService secretService;

    /**
     * 基于滑动窗口的简单内存限流.
     * <p>
     * 每个 key 在 60 秒窗口内最多允许 limit 次通过,生产环境建议替换为 Bucket4j 或 Sentinel.
     *
     * @param map   各类操作的计数器映射
     * @param key   限流维度键(如客户端 IP)
     * @param limit 窗口内允许通过的最大次数
     * @return true=放行,false=触发限流
     */
    private boolean rateLimit(ConcurrentHashMap<String, WindowCounter> map, String key, int limit) {
        WindowCounter c = map.computeIfAbsent(key, k -> new WindowCounter());
        long now = System.currentTimeMillis();
        synchronized (c) {
            if (now - c.windowStart > WINDOW_MS) {
                c.windowStart = now;
                c.count.set(0);
            }
            return c.count.incrementAndGet() <= limit;
        }
    }

    /**
     * 保存(新增)密钥.
     * <p>
     * 对必填字段缺失时填充默认值,执行 PUT 限流检查,并在结束前记录访问日志.
     *
     * @param secret  密钥实体
     * @param request HTTP 请求,用于获取操作者与客户端 IP
     * @return 包含 success、data、message 的结果映射
     */
    @Operation(summary = "保存密钥")
    @PreAuthorize("hasAuthority('mist:secret:write') or isAnonymous()")
    @PostMapping
    public Map<String, Object> saveSecret(@RequestBody ZMistSecretInfo secret, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        if (!rateLimit(putCounters, currentIp(request), PUT_PER_MIN)) {
            result.put("success", false);
            result.put("message", "Rate limit exceeded (PUT limit " + PUT_PER_MIN + "/min)");
            secretService.recordAccess(secret.getSecretKey(), secret.getGroup(), secret.getNamespace(),
                    "PUT", currentOperator(request), currentIp(request), false, "rate_limit");
            return result;
        }
        boolean success = false;
        String errorMsg = null;
        try {
            // 设置默认值
            if (secret.getNamespace() == null) {
                secret.setNamespace(Constance.DEFAULT_NAMESPACE);
            }
            if (secret.getGroup() == null) {
                secret.setGroup(Constance.DEFAULT_GROUP);
            }
            if (secret.getEncryptAlgorithm() == null) {
                secret.setEncryptAlgorithm(Constance.EncryptAlgorithm.AES);
            }
            if (secret.getSecretType() == null) {
                secret.setSecretType(Constance.SecretType.TEXT);
            }

            ZMistSecretInfo saved = secretService.saveSecret(secret);
            result.put("success", true);
            result.put("data", saved);
            success = true;
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            errorMsg = e.getMessage();
        } finally {
            // FEATURE021: 写访问日志
            secretService.recordAccess(
                    secret.getSecretKey(),
                    secret.getGroup(),
                    secret.getNamespace(),
                    "PUT",
                    currentOperator(request),
                    currentIp(request),
                    success,
                    errorMsg
            );
        }
        return result;
    }

    /**
     * 更新已有密钥.
     * <p>
     * 复用 PUT 限流桶,并在结束前记录访问日志.
     *
     * @param secret  包含主键的密钥实体
     * @param request HTTP 请求,用于获取操作者与客户端 IP
     * @return 包含 success、data、message 的结果映射
     */
    @Operation(summary = "更新密钥")
    @PreAuthorize("hasAuthority('mist:secret:write') or isAnonymous()")
    @PutMapping
    public Map<String, Object> updateSecret(@RequestBody ZMistSecretInfo secret, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        if (!rateLimit(putCounters, currentIp(request), PUT_PER_MIN)) {
            result.put("success", false);
            result.put("message", "Rate limit exceeded");
            secretService.recordAccess(secret.getSecretKey(), secret.getGroup(), secret.getNamespace(),
                    "PUT", currentOperator(request), currentIp(request), false, "rate_limit");
            return result;
        }
        boolean success = false;
        String errorMsg = null;
        try {
            ZMistSecretInfo updated = secretService.updateSecret(secret);
            result.put("success", true);
            result.put("data", updated);
            success = true;
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            errorMsg = e.getMessage();
        } finally {
            secretService.recordAccess(
                    secret.getSecretKey(),
                    secret.getGroup(),
                    secret.getNamespace(),
                    "PUT",
                    currentOperator(request),
                    currentIp(request),
                    success,
                    errorMsg
            );
        }
        return result;
    }

    /**
     * 按 secretKey 删除密钥.
     *
     * @param secretKey 密钥主键
     * @param group     分组,缺省 DEFAULT_GROUP
     * @param namespace 命名空间,缺省空字符串
     * @param request   HTTP 请求,用于获取操作者与客户端 IP
     * @return 包含 success、message 的结果映射
     */
    @Operation(summary = "删除密钥")
    @PreAuthorize("hasAuthority('mist:secret:write') or isAnonymous()")
    @DeleteMapping
    public Map<String, Object> deleteSecret(
            @RequestParam String secretKey,
            @RequestParam(required = false, defaultValue = "DEFAULT_GROUP") String group,
            @RequestParam(required = false, defaultValue = "") String namespace,
            HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        if (!rateLimit(deleteCounters, currentIp(request), DELETE_PER_MIN)) {
            result.put("success", false);
            result.put("message", "Rate limit exceeded");
            secretService.recordAccess(secretKey, group, namespace,
                    "DELETE", currentOperator(request), currentIp(request), false, "rate_limit");
            return result;
        }
        boolean success = false;
        String errorMsg = null;
        try {
            boolean deleted = secretService.deleteSecret(secretKey, group, namespace);
            result.put("success", deleted);
            result.put("message", deleted ? "删除成功" : "删除失败");
            success = deleted;
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            errorMsg = e.getMessage();
        } finally {
            secretService.recordAccess(
                    secretKey, group, namespace,
                    "DELETE",
                    currentOperator(request),
                    currentIp(request),
                    success,
                    errorMsg
            );
        }
        return result;
    }

    /**
     * 获取密钥详情.
     *
     * @param secretKey 密钥主键
     * @param group     分组,缺省 DEFAULT_GROUP
     * @param namespace 命名空间,缺省空字符串
     * @param request   HTTP 请求,用于获取操作者与客户端 IP
     * @return 包含 success、data 的结果映射
     */
    @Operation(summary = "获取密钥详情")
    @PreAuthorize("hasAuthority('mist:secret:read') or isAnonymous()")
    @GetMapping("/get")
    public Map<String, Object> getSecret(
            @RequestParam String secretKey,
            @RequestParam(required = false, defaultValue = "DEFAULT_GROUP") String group,
            @RequestParam(required = false, defaultValue = "") String namespace,
            HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        if (!rateLimit(getCounters, currentIp(request), GET_PER_MIN)) {
            result.put("success", false);
            result.put("message", "Rate limit exceeded (GET limit " + GET_PER_MIN + "/min)");
            secretService.recordAccess(secretKey, group, namespace,
                    "GET", currentOperator(request), currentIp(request), false, "rate_limit");
            return result;
        }
        boolean success = false;
        String errorMsg = null;
        try {
            ZMistSecretInfo secret = secretService.getSecret(secretKey, group, namespace);
            result.put("success", secret != null);
            result.put("data", secret);
            success = secret != null;
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            errorMsg = e.getMessage();
        } finally {
            secretService.recordAccess(
                    secretKey, group, namespace,
                    "GET",
                    currentOperator(request),
                    currentIp(request),
                    success,
                    errorMsg
            );
        }
        return result;
    }

    /**
     * 密钥列表查询.
     *
     * @param group     分组过滤条件
     * @param appName   应用名过滤条件
     * @param namespace 命名空间过滤条件,缺省空字符串
     * @param request   HTTP 请求,用于获取操作者与客户端 IP
     * @return 包含 success、data、total 的结果映射
     */
    @Operation(summary = "密钥列表")
    @PreAuthorize("hasAuthority('mist:secret:read') or isAnonymous()")
    @GetMapping("/list")
    public Map<String, Object> listSecrets(
            @RequestParam(required = false) String group,
            @RequestParam(required = false) String appName,
            @RequestParam(required = false, defaultValue = "") String namespace,
            HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        if (!rateLimit(getCounters, currentIp(request), GET_PER_MIN)) {
            result.put("success", false);
            result.put("message", "Rate limit exceeded");
            secretService.recordAccess(null, group, namespace,
                    "LIST", currentOperator(request), currentIp(request), false, "rate_limit");
            return result;
        }
        boolean success = false;
        String errorMsg = null;
        try {
            List<ZMistSecretInfo> list = secretService.listSecrets(group, appName, namespace);
            result.put("success", true);
            result.put("data", list);
            result.put("total", list.size());
            success = true;
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            errorMsg = e.getMessage();
        } finally {
            secretService.recordAccess(
                    null, group, namespace,
                    "LIST",
                    currentOperator(request),
                    currentIp(request),
                    success,
                    errorMsg
            );
        }
        return result;
    }

    /**
     * 获取当前操作者。优先从 z-ctc SecurityContext 取，未登录则 anonymous。
     * 未来 z-ctc 集成时改为 SecurityContextHolder.getContext().getAuthentication().getName()。
     */
    private String currentOperator(HttpServletRequest request) {
        // TODO: 集成 z-ctc 后改用 SecurityContextHolder
        String xff = request.getHeader("X-Staff-No");
        if (xff != null && !xff.isEmpty()) {
            return xff;
        }
        return "anonymous";
    }

    // ==== FEATURE021 工具方法 ====

    /**
     * 获取当前请求的客户端 IP.
     * <p>
     * 优先解析 X-Forwarded-For 头部的首个值,否则回退到 RemoteAddr.
     *
     * @param request HTTP 请求
     * @return 客户端 IP 字符串
     */
    private String currentIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static class WindowCounter {
        final AtomicInteger count = new AtomicInteger(0);
        long windowStart = System.currentTimeMillis();
    }
}
