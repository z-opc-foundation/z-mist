package com.zifang.z.mist.web.api;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zifang.z.mist.common.Constance;
import com.zifang.z.mist.common.Result;
import com.zifang.z.mist.core.domain.entity.ZMistSecretInfo;
import com.zifang.z.mist.core.domain.service.IZMistSecretService;
import com.zifang.z.mist.web.api.request.SecretReq;
import com.zifang.z.mist.web.api.response.SecretResp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
@RestController("zMistSecretController")
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

    // ==== 写请求体字段契约（L3 显式化）====

    /**
     * 写请求体允许的字段名 → {@link SecretReq} 属性名。
     *
     * <p><b>为什么需要这张表</b>：本服务被平台（z-opc）装配时，全局 Jackson 是
     * {@code SNAKE_CASE}；独立部署（z-mist-admin）时则是 camelCase。
     * 原实现把请求体直接交给 Spring 绑定 ⇒ "字段叫什么"取决于<b>部署在哪</b>，
     * 而不是取决于<b>接口约定是什么</b>。孵化页 {@code SecretEdit.jsx} 提交 camelCase，
     * 在平台上被反序列化成全字段 null 的 {@code SecretReq}，再被下面的默认值逻辑补成
     * "key/name 全 NULL 的一行"（PUT 路径同理，把已有行刷成 null）。
     *
     * <p><b>现在的契约</b>（钉在 L3，不随部署环境漂移）：
     * <ul>
     *   <li>canonical 写法 = snake_case（与平台一致）；camelCase 作为兼容别名同样接受；</li>
     *   <li>两种写法之外的字段名 ⇒ <b>显式拒绝</b>（success=false + 指出是哪个字段），不再静默丢弃；</li>
     *   <li>两种写法都认 ⇒ 不会再出现"字段全是 null"的行；</li>
     *   <li>{@code secret_key} 与 {@code encrypted_value} 必填 ⇒ 缺字段早失败，不落残行。</li>
     * </ul>
     */
    private static final Map<String, String> BODY_FIELD_ALIASES = buildBodyFieldAliases();

    /**
     * 请求体绑定用的 ObjectMapper 兜底（无 Spring 注入时用，例如单元测试直接 new 控制器）。
     * <p>字段名固定按 camelCase 匹配：{@link #bindRequestBody} 已把入参键名归一化过了。
     */
    private static final ObjectMapper FALLBACK_BODY_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .setPropertyNamingStrategy(PropertyNamingStrategy.LOWER_CAMEL_CASE)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    /** 实际使用的请求体绑定 mapper（Spring 注入时按平台配置派生，独立部署时用兜底实例）。 */
    private volatile ObjectMapper bodyMapper = FALLBACK_BODY_MAPPER;

    /**
     * 注入平台自己的 ObjectMapper，再把字段命名策略固定成 camelCase（入参已归一化）。
     * <p>用平台实例是为了沿用它的模块注册与日期格式；只改命名策略，不改其它行为。
     *
     * @param objectMapper 平台 ObjectMapper（可能为 null，例如未走 Spring 的单元测试）
     */
    @Autowired(required = false)
    public void setObjectMapper(ObjectMapper objectMapper) {
        if (objectMapper == null) {
            return;
        }
        this.bodyMapper = objectMapper.copy()
                .setPropertyNamingStrategy(PropertyNamingStrategy.LOWER_CAMEL_CASE)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    /**
     * 从 {@link SecretReq} 的 setter 反推"允许的字段名"⇒ 属性名映射表。
     * <p>反射取而不是手写常量：DTO 加字段时这张表自动跟着长，不会漏。
     *
     * @return 键 = 允许的入参字段名（camelCase 与 snake_case 两种写法都在里面），值 = DTO 属性名
     */
    private static Map<String, String> buildBodyFieldAliases() {
        Map<String, String> map = new HashMap<>();
        for (Method m : SecretReq.class.getMethods()) {
            if (!m.getName().startsWith("set") || m.getParameterCount() != 1) {
                continue;
            }
            String suffix = m.getName().substring(3);
            if (suffix.isEmpty()) {
                continue;
            }
            String prop = Character.toLowerCase(suffix.charAt(0)) + suffix.substring(1);
            map.put(prop, prop);
            map.put(toSnakeCase(prop), prop);
        }
        return Collections.unmodifiableMap(map);
    }

    /**
     * camelCase → snake_case（{@code secretKey} ⇒ {@code secret_key}）。
     *
     * @param camel camelCase 字符串
     * @return snake_case 字符串
     */
    private static String toSnakeCase(String camel) {
        StringBuilder sb = new StringBuilder(camel.length() + 8);
        for (int i = 0; i < camel.length(); i++) {
            char c = camel.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 把原始请求体绑成 {@link SecretReq}，对未知字段显式拒绝。
     *
     * @param rawBody 原始 JSON 对象（键名原样保留，不受全局命名策略影响）
     * @return 绑定后的请求对象
     * @throws IllegalArgumentException 请求体为空、含未知字段、或缺少必填字段
     */
    private SecretReq bindRequestBody(Map<String, Object> rawBody) {
        if (rawBody == null || rawBody.isEmpty()) {
            throw new IllegalArgumentException(
                    "请求体为空: 至少需要 secret_key 与 encrypted_value");
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        List<String> unknown = new ArrayList<>();
        for (Map.Entry<String, Object> entry : rawBody.entrySet()) {
            String target = BODY_FIELD_ALIASES.get(entry.getKey());
            if (target == null) {
                unknown.add(entry.getKey());
            } else {
                normalized.put(target, entry.getValue());
            }
        }
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("请求体包含未知字段: " + unknown
                    + "（本接口字段名为 snake_case，如 secret_key / secret_name / encrypted_value）");
        }
        SecretReq req = bodyMapper.convertValue(normalized, SecretReq.class);
        if (req.getSecretKey() == null || req.getSecretKey().trim().isEmpty()) {
            throw new IllegalArgumentException("secret_key 不能为空");
        }
        if (req.getEncryptedValue() == null || req.getEncryptedValue().trim().isEmpty()) {
            throw new IllegalArgumentException("encrypted_value 不能为空");
        }
        return req;
    }

    /**
     * 从原始请求体里取 {@code secret_key}（仅用于审计行：被拒的请求也要留下"想写哪个 key"）。
     *
     * @param rawBody 原始请求体
     * @return secret_key，取不到返回 null
     */
    private String rawSecretKey(Map<String, Object> rawBody) {
        if (rawBody == null) {
            return null;
        }
        Object v = rawBody.get("secret_key");
        if (v == null) {
            v = rawBody.get("secretKey");
        }
        return v == null ? null : String.valueOf(v);
    }

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
     * <p>
     * 请求体先经 {@link #bindRequestBody} 归一化并校验：未知字段一律拒绝，
     * 不会再像早前那样"反序列化成全 null → 补默认值 → 落一条 key/name 全 NULL 的行"。
     *
     * @param rawBody 原始请求体
     * @param request HTTP 请求,用于获取操作者与客户端 IP
     * @return 包含 success、data、message 的结果映射
     */
    @Operation(summary = "保存密钥")
    @PreAuthorize("hasAuthority('mist:secret:write') or isAnonymous()")
    @PostMapping
    public Result<SecretResp> saveSecret(@RequestBody Map<String, Object> rawBody, HttpServletRequest request) {
        Result<SecretResp> result = new Result<>();
        if (!rateLimit(putCounters, currentIp(request), PUT_PER_MIN)) {
            result.setSuccess(false);
            result.setMessage("Rate limit exceeded (PUT limit " + PUT_PER_MIN + "/min)");
            secretService.recordAccess(rawSecretKey(rawBody), null, null,
                    "PUT", currentOperator(request), currentIp(request), false, "rate_limit");
            return result;
        }
        boolean success = false;
        String errorMsg = null;
        ZMistSecretInfo secret = null;
        try {
            SecretReq secretReq = bindRequestBody(rawBody);
            secret = new ZMistSecretInfo();
            BeanUtils.copyProperties(secretReq, secret);
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
            result.setSuccess(true);
            result.setData(saved == null ? null : toResp(saved));
            success = true;
        } catch (Exception e) {
            result.setSuccess(false);
            result.setMessage(e.getMessage());
            errorMsg = e.getMessage();
        } finally {
            // FEATURE021: 写访问日志
            secretService.recordAccess(
                    secret == null ? rawSecretKey(rawBody) : secret.getSecretKey(),
                    secret == null ? null : secret.getGroup(),
                    secret == null ? null : secret.getNamespace(),
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
     * <p>
     * 与 {@link #saveSecret} 同一套请求体契约：未知字段拒绝、{@code secret_key} 与
     * {@code encrypted_value} 必填 ⇒ 不会再把已有行刷成 null。
     *
     * @param rawBody 原始请求体
     * @param request HTTP 请求,用于获取操作者与客户端 IP
     * @return 包含 success、data、message 的结果映射
     */
    @Operation(summary = "更新密钥")
    @PreAuthorize("hasAuthority('mist:secret:write') or isAnonymous()")
    @PutMapping
    public Result<SecretResp> updateSecret(@RequestBody Map<String, Object> rawBody, HttpServletRequest request) {
        Result<SecretResp> result = new Result<>();
        if (!rateLimit(putCounters, currentIp(request), PUT_PER_MIN)) {
            result.setSuccess(false);
            result.setMessage("Rate limit exceeded");
            secretService.recordAccess(rawSecretKey(rawBody), null, null,
                    "PUT", currentOperator(request), currentIp(request), false, "rate_limit");
            return result;
        }
        boolean success = false;
        String errorMsg = null;
        ZMistSecretInfo secret = null;
        try {
            SecretReq secretReq = bindRequestBody(rawBody);
            secret = new ZMistSecretInfo();
            BeanUtils.copyProperties(secretReq, secret);
            ZMistSecretInfo updated = secretService.updateSecret(secret);
            result.setSuccess(true);
            result.setData(updated == null ? null : toResp(updated));
            success = true;
        } catch (Exception e) {
            result.setSuccess(false);
            result.setMessage(e.getMessage());
            errorMsg = e.getMessage();
        } finally {
            secretService.recordAccess(
                    secret == null ? rawSecretKey(rawBody) : secret.getSecretKey(),
                    secret == null ? null : secret.getGroup(),
                    secret == null ? null : secret.getNamespace(),
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
    public Result<Void> deleteSecret(
            @RequestParam String secretKey,
            @RequestParam(required = false, defaultValue = "DEFAULT_GROUP") String group,
            @RequestParam(required = false, defaultValue = "") String namespace,
            HttpServletRequest request) {
        Result<Void> result = new Result<>();
        if (!rateLimit(deleteCounters, currentIp(request), DELETE_PER_MIN)) {
            result.setSuccess(false);
            result.setMessage("Rate limit exceeded");
            secretService.recordAccess(secretKey, group, namespace,
                    "DELETE", currentOperator(request), currentIp(request), false, "rate_limit");
            return result;
        }
        boolean success = false;
        String errorMsg = null;
        try {
            boolean deleted = secretService.deleteSecret(secretKey, group, namespace);
            result.setSuccess(deleted);
            result.setMessage(deleted ? "删除成功" : "删除失败");
            success = deleted;
        } catch (Exception e) {
            result.setSuccess(false);
            result.setMessage(e.getMessage());
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
    public Result<SecretResp> getSecret(
            @RequestParam String secretKey,
            @RequestParam(required = false, defaultValue = "DEFAULT_GROUP") String group,
            @RequestParam(required = false, defaultValue = "") String namespace,
            HttpServletRequest request) {
        Result<SecretResp> result = new Result<>();
        if (!rateLimit(getCounters, currentIp(request), GET_PER_MIN)) {
            result.setSuccess(false);
            result.setMessage("Rate limit exceeded (GET limit " + GET_PER_MIN + "/min)");
            secretService.recordAccess(secretKey, group, namespace,
                    "GET", currentOperator(request), currentIp(request), false, "rate_limit");
            return result;
        }
        boolean success = false;
        String errorMsg = null;
        try {
            ZMistSecretInfo secret = secretService.getSecret(secretKey, group, namespace);
            result.setSuccess(secret != null);
            result.setData(secret == null ? null : toResp(secret));
            success = secret != null;
        } catch (Exception e) {
            result.setSuccess(false);
            result.setMessage(e.getMessage());
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
     * <p>
     * {@code namespace} 缺省语义与 {@code /api/secret/search} 对齐：<b>不传 = 不过滤</b>。
     * <p>
     * 早前这里是 {@code defaultValue = ""}，空串又会被当成"精确匹配 namespace=''"，
     * 于是同一个参数名在 list 与 search 上给出两份答案（实测 list total=1 / search total=11）。
     * 现在缺省传 null，由服务层统一按"空 = 不过滤"处理。
     *
     * @param group     分组过滤条件
     * @param appName   应用名过滤条件
     * @param namespace 命名空间过滤条件,不传 = 不过滤
     * @param request   HTTP 请求,用于获取操作者与客户端 IP
     * @return 包含 success、data、total 的结果映射
     */
    @Operation(summary = "密钥列表")
    @PreAuthorize("hasAuthority('mist:secret:read') or isAnonymous()")
    @GetMapping("/list")
    public Map<String, Object> listSecrets(
            @RequestParam(required = false) String group,
            @RequestParam(required = false) String appName,
            @RequestParam(required = false) String namespace,
            HttpServletRequest request) {
        // 不规则聚合输出(total 自定义 key), 保持原状
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

    private SecretResp toResp(ZMistSecretInfo secret) {
        SecretResp resp = new SecretResp();
        BeanUtils.copyProperties(secret, resp);
        return resp;
    }

    private static class WindowCounter {
        final AtomicInteger count = new AtomicInteger(0);
        long windowStart = System.currentTimeMillis();
    }
}
