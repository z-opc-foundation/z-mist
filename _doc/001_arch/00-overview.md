# 001 · z-mist 项目总览

> z-mist 是 z-opc-foundation 组织下 L3 层"秘密管理"原子能力中间件。
> 任何 Java 开发者通过 `io.github.yuku123:z-mist-web` 一行 import 即可在自己的 Spring Boot 应用中获得密钥管理能力。

## 0. 凭据泄露历史与消除记录(2026-09-15)

- **背景**:z-mist 拆出时(2026-09-06,commit 693c375)从 z-opc monorepo 直接拷出 application.yml,内含明文 RDS 密码 `Hhzemol!` 与 `1qaz2wsx3edc`,已通过 commit 公开到 GitHub 网络
- **风险评估判定**(组织 lead 2026-09-16):**commit 历史塌缩(force-push 后远端不可见)即视为凭据风险消除**,不再额外要求重置 RDS 密码
  - 理由:第三方扫描工具(fork 镜像 / Google cache / GitHub event log)无法直接命中被 force-push 覆盖的 commit SHA;自动化扫描器对历史的依赖性强
- **执行**:
  1. 删 `.git` 重新 init → 单一 commit 4f8e817(working tree 全部清理)→ 本地无泄露
  2. force-push 后远端 HEAD 指向 4f8e817,693c375 不再可达
  3. 本地敏感词扫描结果(2026-09-16 验证):
     - `Hhzemol`: 0 commits + 0 files
     - `1qaz2wsx3edc`: 0 commits + 0 files
     - `rm-bp108uqlgwn712k72z`: 0 commits + 0 files
     - `mysql.rds.aliyuncs.com`: 0 commits + 0 files
  4. 中央发版走 io.github.yuku123 namespace + GPG 签名,artifact 不含任何明文凭据

## 1. 定位

- **层级**：L3（原子能力层）
- **受众**：所有 Java 开发者（外部社区 + 内部 z-opc 业务模块）
- **发布渠道**：Maven Central（`io.github.yuku123:z-mist-*`）
- **版本策略**：`${revision}` CI-friendly，发版改父 POM 一处即可

## 2. 模块依赖图

```
                ┌──────────────────────────────────────┐
                │            业务模块 (L1)             │
                │  通过 z-boot-mist-starter 一行集成   │
                └──────────────────┬───────────────────┘
                                   │
                                   ▼
       ┌─────────────────────────────────────────────────┐
       │            z-mist-web (Starter 入口)            │
       │  io.github.yuku123:z-mist-web                  │
       │  · SecretController                            │
       │  · MistSecurityConfig / MistMybatisPlusConfig  │
       │  · MistKnife4jConfig / MistWebAutoConfiguration│
       └──────────────────┬──────────────────────────────┘
                          │
                          ▼
       ┌─────────────────────────────────────────────────┐
       │             z-mist-core (服务端核心)           │
       │  io.github.yuku123:z-mist-core                 │
       │  · Netty 服务端实现                            │
       │  · MyBatis-Plus Entity/Mapper/Service          │
       │  · 调度器（密钥轮换/过期/Webhook 通知）        │
       └────────┬─────────────────────────┬─────────────┘
                │                         │
                ▼                         ▼
   ┌─────────────────────┐   ┌──────────────────────────┐
   │  z-mist-common      │   │  z-mist-client (SDK)     │
   │  · Message / Codec  │   │  · MistClient            │
   │  · Serializer       │   │  · ClientBusinessHandler │
   │  · LoginRequest …   │   └──────────────────────────┘
   └─────────────────────┘
                ▲
                │
   ┌────────────┴────────────────────────────────────────┐
   │  z-mist-sdk (fat-jar 可执行 SDK 演示)              │
   │  io.github.yuku123:z-mist-sdk                      │
   │  java -jar z-mist-sdk-1.0.1.jar                    │
   └─────────────────────────────────────────────────────┘
```

## 3. 第三方依赖

| 库 | 版本 | 用途 |
|---|---|---|
| spring-boot-dependencies | 2.7.12 | Spring Boot BOM |
| netty-all | 4.1.108.Final | Netty 服务端/客户端 |
| druid | 1.2.18 | JDBC 连接池（来自 z-boot-datasource-starter）|
| mybatis-plus | 3.5.7 | ORM（来自 z-boot-datasource-starter）|
| knife4j-openapi3 | 4.1.0 | Swagger UI |
| cron-utils | 9.2.1 | Cron 解析（Quartz 格式）|
| okhttp | 4.12.0 | Webhook 通知 HTTP 客户端 |
| log4j-bom | 2.20.0 | Log4j2 |
| io.github.yuku123:z-util-core | 1.0.10 | 通用工具 |
| io.github.yuku123:z-boot-dependencies | 1.0.9 | z-boot BOM（包含全部 L3 starter）|

## 4. 中央仓库发布节奏

参考 z-schedule 1.0.0 → 1.0.1 的发版路径：

1. **首版 1.0.1**：跳过 1.0.0 占坐标
2. **修订**：递增 patch 号 → 1.0.2 / 1.0.3 ...
3. **功能**：递增 minor 号 → 1.1.0
4. **不兼容变更**：递增 major 号 → 2.0.0

> **强制约束**：发布到 Maven Central 前必须用 `flatten-maven-plugin` oss 模式生成 `.flattened-pom.xml`，详见 [lead/005_技术架构/004_flatten_maven_plugin_模式选择.md](https://github.com/z-opc-foundation/z-opc-foundation-lead/blob/main/005_技术架构/004_flatten_maven_plugin_模式选择.md)。

## 5. z-mist-admin 演示应用

- **不入 reactor**：与 z-schedule-admin 相同处理
- **本地启动**：`cd z-mist-admin && mvn spring-boot:run`
- **Docker 部署**：`docker build -t z-mist-admin:latest .`
- **前端**：`_frontend/`（vite + vue），通过 `package.sh` + `build.sh` 集成到 admin jar

## 6. 凭证规范

z-mist 严格遵循 [lead/008_组织规范/001_凭证管理规范.md](https://github.com/z-opc-foundation/z-opc-foundation-lead/blob/main/008_组织规范/001_凭证管理规范.md)：

- `application.yml` / `application-*.yml` 一律不含明文密码
- 所有 `password:` 必须是 `${SPRING_DATASOURCE_PASSWORD:}` 占位符
- 真实值通过环境变量 / `application-local.yml`（gitignore）注入
- 数据库 schema 可 commit（含 DDL），数据不可 commit