package com.zifang.z.mist.web.api.request;

import java.io.Serializable;

/**
 * 登录请求体(原 {@code AuthController.LoginRequest} 内部静态类下沉到 request 包).
 *
 * <p>JSON 字段名与历史完全一致: username / password。
 *
 * @author zifang
 * @since 1.0.0
 */
public class LoginRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    public String username;
    public String password;
}
