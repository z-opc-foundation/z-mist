package com.zifang.z.mist.admin.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * SPA history fallback。
 *
 * <p>前端是 BrowserRouter，刷新 {@code /mist/secret} 这类路径时 Spring 找不到静态资源就返回 JSON 404，
 * 浏览器拿不到 index.html 直接白屏。vite dev / preview 自带 fallback，本地永远看不见，只在 jar 里现形。
 *
 * <p>不能写成 {@code /**}：那会把 {@code /api/**} 的 404 也 forward 过去，把"接口不存在"伪造成
 * "返回了一坨 HTML"，客户端 JSON.parse 直接炸。逐条列前端路由前缀。
 */
@Configuration
public class MistWebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/z-mist/login").setViewName("forward:/index.html");
        registry.addViewController("/z-mist/**").setViewName("forward:/index.html");
    }
}
