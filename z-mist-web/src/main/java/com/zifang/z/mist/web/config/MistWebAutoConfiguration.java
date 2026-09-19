package com.zifang.z.mist.web.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * z-mist-web AutoConfiguration (供 main-starter embed).
 *
 * <p>职责:
 * <ul>
 *   <li>@ComponentScan 扫描 z-mist-web/api (controllers) + z-mist-web/config (configs) + z-mist-web/scheduler</li>
 *   <li>@EnableScheduling 启用 @Scheduled 定时任务(轮换/过期告警/统计聚合)</li>
 *   <li>依赖 z-mist-core: 业务 entity/mapper/service 也会被 Spring 找到 (因 core 的 @MapperScan 在 z-mist-core/.../config 下)</li>
 * </ul>
 *
 * <p>历史: z-mist-admin 是独立启动器, controllers + configs 都在 admin 包下.
 * admin 的 application.yml 含 server.port=8080, 作为 main-starter 依赖会污染 Spring 环境
 * (server.port 被 yml 后加载覆盖, 8888 启不起来).
 *
 * <p>解决: 把 controllers + configs 拆到 z-mist-web, 用 spring.factories 注册.
 * z-mist-admin 留作独立启动器, 只包含 ZMistApplication 入口.
 */
@Configuration
@ComponentScan(
        basePackages = {
                "com.zifang.z.mist.web.api",
                "com.zifang.z.mist.web.config",
                "com.zifang.z.mist.web.scheduler"
        },
        nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class
)
@EnableScheduling
public class MistWebAutoConfiguration {
}