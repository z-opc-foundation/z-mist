package com.zifang.z.mist.web.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * z-mist 本地登录认证(FEATURE026 P0 + FEATURE065 审计增强).
 * <p>
 * 替代前端的 mock 登录: 校验 z_mist_users 表的 BCrypt 密码,
 * 返回一个简单的 token(UUID),前端存 localStorage。
 * <p>
 * FEATURE065: 所有登录/登出操作记录到 access_log(op_type=LOGIN/LOGOUT)
 * 便于安全审计和异常登录检测。
 * <p>
 * 未来 z-ctc 接入后,该 controller 会被替换为统一 SSO 跳转。
 *
 * <ul>
 *   <li>POST /api/auth/login    — 登录获取 token</li>
 *   <li>POST /api/auth/logout   — 登出(无状态,仅前端清 token)</li>
 * </ul>
 */
@Tag(name = "本地认证(FEATURE026)")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    // 简单的内存审计: 记录最近登录IP(用于暴力检测)
    private static final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger>
            LOGIN_ATTEMPTS = new java.util.concurrent.ConcurrentHashMap<>();
    @Resource(name = "dataSourceMist")
    private DataSource dataSource;

    @Operation(summary = "登录获取 token")
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest req, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        if (req == null || req.username == null || req.password == null) {
            result.put("success", false);
            result.put("message", "username and password required");
            return result;
        }
        String ip = currentIp(request);
        boolean success = false;
        String errMsg = null;
        try {
            // 简单暴力检测: 同一 IP 连续失败 5 次则临时拒绝
            java.util.concurrent.atomic.AtomicInteger attempts = LOGIN_ATTEMPTS.computeIfAbsent(ip,
                    k -> new java.util.concurrent.atomic.AtomicInteger(0));
            if (attempts.get() >= 5) {
                result.put("success", false);
                result.put("message", "登录尝试次数过多，请稍后再试(5次/分钟)");
                return result;
            }

            try (Connection conn = dataSource.getConnection()) {
                String sql = "SELECT password, enabled FROM z_mist_users WHERE username = ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, req.username);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            result.put("success", false);
                            result.put("message", "用户不存在");
                            errMsg = "user_not_found";
                            return result;
                        }
                        String dbHash = rs.getString("password");
                        Integer enabled = rs.getInt("enabled");
                        if (enabled == null || enabled != 1) {
                            result.put("success", false);
                            result.put("message", "账号已禁用");
                            errMsg = "account_disabled";
                            return result;
                        }
                        if (!BCrypt.checkpw(req.password, dbHash)) {
                            attempts.incrementAndGet();
                            result.put("success", false);
                            result.put("message", "密码错误");
                            errMsg = "password_wrong";
                            return result;
                        }
                        // 成功: 清除失败计数
                        attempts.set(0);
                        String token = UUID.randomUUID().toString().replace("-", "");
                        result.put("success", true);
                        Map<String, Object> data = new HashMap<>();
                        data.put("token", token);
                        data.put("username", req.username);
                        data.put("role", "ROLE_ADMIN");
                        result.put("data", data);
                        success = true;
                        return result;
                    }
                }
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "登录失败: " + e.getMessage());
            errMsg = e.getMessage();
            return result;
        } finally {
            // FEATURE065: 登录审计
            writeLog(req.username, "LOGIN", request, success, errMsg);
        }
    }

    @Operation(summary = "登出(仅前端清 token,无状态)")
    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "已登出");
        // FEATURE065: 登出审计
        writeLog(currentOperator(request), "LOGOUT", request, true, null);
        return result;
    }

    private void writeLog(String username, String opType, HttpServletRequest request,
                          boolean success, String errMsg) {
        try {
            javax.sql.DataSource ds = dataSource;
            try (java.sql.Connection conn = ds.getConnection()) {
                String sql = "INSERT INTO z_mist_secret_access_log " +
                        "(secret_key, `group`, `namespace`, op_type, operator, operator_ip, success, error_message, gmt_create) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())";
                try (java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, "AUTH");
                    ps.setString(2, null);
                    ps.setString(3, null);
                    ps.setString(4, opType);
                    ps.setString(5, username == null ? "anonymous" : username);
                    ps.setString(6, currentIp(request));
                    ps.setBoolean(7, success);
                    ps.setString(8, errMsg);
                    ps.executeUpdate();
                }
            }
        } catch (Exception ex) {
            // 审计失败不阻塞登录流程
        }
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

    public static class LoginRequest {
        public String username;
        public String password;
    }
}
