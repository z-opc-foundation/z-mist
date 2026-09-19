package com.zifang.z.mist.web.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;

/**
 * FEATURE022: 启用 @PreAuthorize 注解生效。
 * <p>
 * 必须配合 z-ctc-sso（或其他 Spring Security 适配器）才能真正校验权限。
 * 在没接 z-ctc 的情况下，{@code isAnonymous()} 分支会放行所有请求，
 * 但注解已经挂上，将来 z-ctc 接入后立即生效。
 */
@Configuration
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class MistSecurityConfig {
}
