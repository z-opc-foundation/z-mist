package com.zifang.z.mist.admin.api.response;

import java.io.Serializable;

/**
 * 登录成功响应 DTO(原 AuthController login 端点内联 data Map 下沉为 DTO).
 *
 * <p>JSON 字段名与历史完全一致: token / username / role。
 *
 * @author zifang
 * @since 1.0.0
 */
public class LoginResp implements Serializable {

    private static final long serialVersionUID = 1L;

    private String token;
    private String username;
    private String role;

    public LoginResp() {
    }

    public LoginResp(String token, String username, String role) {
        this.token = token;
        this.username = username;
        this.role = role;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
