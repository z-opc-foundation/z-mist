package com.zifang.z.mist.core;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * z-mist core 层装配 (2026-10-03 自 z-opc main-starter 的 MistCoreScanConfig 平移收编):
 * <ul>
 *   <li>@ComponentScan 收 com.zifang.z.mist.core 的 @Service / @Component
 *       (MistWebAutoConfiguration 只扫 web.*, core 的 Service 走这条入口)</li>
 *   <li>@MapperScan 把 z-mist core mapper 注册到宿主的 SqlSessionFactory (缺省 sqlSessionFactoryCtc).
 *       宿主已带 z-mist-web 时 MistModuleDataSource 同名 sqlSessionFactoryMist 优先
 *       (ConditionalOnProperty 与 mapperScan 的 bean 名解析: 走名字最具体的那个)</li>
 * </ul>
 *
 * <p>为什么不自己声明 dataSourceMist / sqlSessionFactoryMist:
 * Spring 容器里同时挂两个 SqlSessionFactory 会让 MapperScannerConfigurer 不知道挑;
 * 走 sqlSessionFactoryCtc 的好处是复用已有连接池, mist mapper 的 DDL 假定与 ctc 同库
 * (本仓默认 oc). 生产 mist 独立库 / 独立容器时, 宿主引入 z-mist-web
 * (含 MistModuleDataSource 注册的 sqlSessionFactoryMist), 本条 @MapperScan 自然让位.
 *
 * <p>历史: 旧 MistCoreScanConfig 仅 @ComponentScan, mapper 没注册,
 * ZMistSecretServiceImpl 一实例化就 NoSuchBeanDefinitionException. 这次连 mapper 注册一并下沉.
 */
@Configuration
@ConditionalOnProperty(prefix = "z-mist", name = "enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan(basePackages = "com.zifang.z.mist.core")
@MapperScan(basePackages = "com.zifang.z.mist.core.domain.mapper", sqlSessionFactoryRef = "sqlSessionFactoryCtc")
public class MistCoreAutoConfiguration {
}