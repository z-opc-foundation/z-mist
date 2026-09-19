package com.zifang.z.mist.admin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * FEATURE022: 启用 @PreAuthorize 注解生效。
 * <p>
 * 必须配合 z-ctc-sso（或其他 Spring Security 适配器）才能真正校验权限。
 * 在没接 z-ctc 的情况下，{@code permitAll()} 放行所有请求，
 * 但 {@code @PreAuthorize} 注解已经挂上，将来 z-ctc 接入后立即生效。
 * <p>
 * 注：默认 SecurityAutoConfiguration 会启用 HTTP Basic，导致所有 API 401。
 * 这里显式声明 SecurityFilterChain.permitAll() 覆盖默认行为。
 */
@Configuration
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class MistSecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .build();
    }
}