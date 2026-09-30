# z-mist

> KMS —— 分布式密钥 / 秘密（Secret）管理平台

一人公司基座的机密数据中枢：把数据库口令、API Key、证书这类敏感值集中存储并用主密钥加密，
对内以 **Netty 私有 JSON 协议**（长连接 + 心跳）供客户端 SDK 拉取，对外以 **Spring Boot Starter**
（`z-mist-web` 自动装配 REST 控制器）供业务应用嵌入，并附带 Web 控制台与调度器
（密钥轮换 / 过期告警 / 统计聚合 / Webhook 通知）。

---

## 📋 基本信息

| 字段 | 值 |
|------|-----|
| **仓库** | `z-mist`（Secret Management Platform） |
| **Maven 坐标** | `io.github.yuku123:z-mist:${revision}` |
| **当前版本** | `1.0.4`（根 POM `<revision>`，CI-friendly versions + flatten-maven-plugin `oss` 模式） |
| **父项目** | `io.github.yuku123:z-boot-parent:1.0.21`（`<relativePath/>` 留空，parent 在 repo1 不在磁盘） |
| **Maven Central** | 已发布：`z-mist` / `-common` / `-core` / `-web` / `-client` / `-sdk` 的 `1.0.2`~`1.0.4` 均实测 200 |
| **默认端口** | HTTP `8080`（Spring Boot 默认，未覆盖）· Netty 私有协议 `9085`（`z-mist.server.port`） |
| **运行口径** | Java 8 · Spring Boot 2.7.18 |
| **最近更新** | 2026-09-30 |

---

## 🎯 能力清单

能力都能对应到 `z-mist-web/src/main/java/com/zifang/z/mist/web/` 下的类（`z-mist-admin` 为同集合的独立演示启动器）：

| 能力 | 入口 | 说明 |
|------|------|------|
| 密钥 CRUD | `SecretController`（`/api/secret`） | 存 / 改 / 删 / 取密文 / 列表 |
| 密钥运维 | `SecretOperationController`（`/api/secret`） | 明文取回、搜索、历史、回滚、即时轮换、动态密钥生成/取/删 |
| 主密钥 | `MasterKeyController`（`/api/master-key/*`、`/api/bulk/*`） | 历史查询、滚动重加密、信封加解密、批量导入导出 |
| EaaS 加解密 | `EaaSController`（`/api/eaas`） | Encryption-as-a-Service：`/encrypt`、`/decrypt` |
| 应用接入 | `AppController`（`/api/app`） | 应用注册、重置 appSecret |
| 访问控制 | `AclController`（`/api/acl`） | 密钥 ACL 增删改查、鉴权判定 |
| 标签 | `TagController`（`/api/tag`） | 密钥打标、按密钥/关键字检索 |
| 访问审计 | `AccessLogController`（`/api/log`） | 分页 / 最近 / 失败访问日志 |
| 统计 | `StatsController`（`/api/stats`） | 概览、按日、Top 密钥、临期 |
| 轮换策略 | `RotationPolicyController`（`/api/rotation`） | 策略 CRUD、手动触发、轮换历史 |
| 登录鉴权 | `AuthController`（`/api/auth`） | `login` / `logout`（`@PreAuthorize` 方法级） |
| 健康检查 | `MistBaseHealthController`（`/api/mist/health`） | 存活探针 |
| Netty 服务 | `z-mist-core` `MistNettyServer`（`@PostConstruct` 起 `z-mist.server.port`） | 长连接 JSON 协议，供 SDK 拉取 |
| 定时任务 | `z-mist-web/scheduler/` | `RotationScheduler`（60s）、`ExpiringScheduler`（每小时 05 分）、`StatsAggregator`（每日 01:00）、`WebhookNotifier` |

`z-mist-web` 经 `META-INF/spring.factories` 注册 `MistWebAutoConfiguration`
（`@ComponentScan` web 的 api/config/scheduler + `@EnableScheduling`），业务方引 starter 即获得以上 REST 能力。

---

## 🏗️ 项目结构

```
z-mist/
├── pom.xml                # 根聚合 POM：继承 z-boot-parent:1.0.21，<revision> 统一版本，常开 flatten(oss)
├── z-mist-common/         # 协议层 + 共享类型：Netty 编解码/序列化、Message/心跳、Result、密钥与登录 DTO
├── z-mist-core/           # 领域 + 服务端：Z*Mist 实体/Mapper/Service、AES 加解密、MistNettyServer
├── z-mist-web/            # Spring Boot Starter：REST 控制器 + 自动装配 + 模块数据源 + 调度器 + Knife4j/Security
├── z-mist-client/         # 客户端 SDK：MistClient（Netty 长连接）+ ClientBusinessHandler
├── z-mist-sdk/            # 可执行 fat-jar 演示（maven-shade-plugin，Main 为占位入口）
├── z-mist-admin/          # 独立可启动演示应用（不进 reactor，不上 Maven Central）
├── _frontend/             # z-mist-admin 前端源码：React + Ant Design + Vite（不进 reactor）
├── Dockerfile             # 基于 eclipse-temurin:8-jdk，EXPOSE 8080
└── _doc/                  # 文档，见文末「文档目录」
```

进入 Maven reactor 的只有 5 个模块（`common` / `core` / `web` / `client` / `sdk`）；
`z-mist-admin` 与 `_frontend` 不在 `<modules>` 中，仅本地构建，**不会发布到 Maven Central**。

> 注意 `z-mist-sdk` 的 shade 插件 `<mainClass>` 仍写着迁移前包名 `io.github.yuku123.z.mist.sdk.Main`，
> 而源码已随全仓迁到 `com.zifang.z.mist.sdk.Main`，且 `Main` 目前只打印版本占位，尚未是功能演示。

---

## 🔧 技术栈

| 层级 | 技术（均取自 POM / 源码实测） |
|------|------|
| 语言 / 运行时 | Java 8（全组织口径 1.8） |
| 框架 | Spring Boot 2.7.18（由 `z-boot-parent` → `z-boot-dependencies` 地板供给） |
| 私有协议 | Netty `netty-all` 4.1.138.Final（JSON 编解码 + 长度字段分帧 + 心跳） |
| 持久层 | MyBatis-Plus 3.5.7 + Druid 1.2.23，数据源走 `ModuleDataSourceTemplate` |
| 数据库 | MySQL（`mysql-connector-j` 刻意钉 8.0.33，与地板 8.4.0 分歧） |
| 加解密 | AES（主密钥外部化，支持信封加密） |
| 定时调度 | cron-utils 9.2.1 + Spring `@Scheduled` |
| HTTP 客户端 | OkHttp 4.12.0（Webhook 通知） |
| 接口文档 | Knife4j / springdoc（`knife4j-openapi3-spring-boot-starter` 4.1.0，启动后 `/doc.html`） |
| 日志 | Log4j2 2.17.2（`spring-boot-starter-logging` 被地板排除，不补 logback） |
| 安全 | Spring Security（仅方法级 `@PreAuthorize`，filter chain `permitAll`） |
| 前端 | React + Ant Design + Vite（`_frontend/`，独立 npm 工程，node v18.17.0） |
| 构建 | Maven（后端）· frontend-maven-plugin（前端）· Docker |

---

## 🚀 快速开始

### 编译（reactor 5 模块 + 装本地仓）

```bash
mvn clean install -DskipTests
```

若解析不到 `io.github.yuku123:z-boot-parent:1.0.21`，先确认本地/镜像能拉到该 parent；第三方版本一律由父链供给，
模块 POM 不应再出现字面版本钉。

### 本地跑起 admin 演示

```bash
# 先 export 环境变量（不要写进任何 yml / 提交文件）
export Z_MIST_DB_HOST=127.0.0.1 Z_MIST_DB_PORT=3306 Z_MIST_DB_NAME=z_mist \
       Z_MIST_DB_USER=<你的账号> Z_MIST_DB_PASSWORD=<你的口令> \
       Z_MIST_MASTER_KEY=<你的主密钥>

mvn -pl z-mist-admin spring-boot:run
```

> `z-mist-admin` 不在 reactor，需先完成上一步 install 再单独跑；其 `spring-boot-maven-plugin` 的 `repackage`
> 被设为 `skip=true`（输出普通 library jar），因此走 `spring-boot:run` 而非 `java -jar`。
> HTTP 监听 `8080`（无 context-path），Knife4j 文档 `http://localhost:8080/doc.html`。

### 数据源与密钥配置

数据源由 `z-mist-web` 的 `MistModuleDataSource`（`buildDataSource(env, "mist")`）按模块模板装配，
读取 `z.base.db.mist.*`；`spring.datasource` 在 admin 配置里被显式排除为死配置（避免自动装配抢跑注册第二个 DataSource）。
**所有凭据必须经环境变量注入，禁止写进 yml/jar/镜像层**：

| 配置前缀 / 键 | 环境变量 | 用途 |
|------|----------|------|
| `z.base.db.mist.host` | `Z_MIST_DB_HOST` | 数据库主机 |
| `z.base.db.mist.port` | `Z_MIST_DB_PORT` | 端口（默认 3306） |
| `z.base.db.mist.database` | `Z_MIST_DB_NAME` | 库名（默认 `z_mist`） |
| `z.base.db.mist.username` | `Z_MIST_DB_USER` | 账号 |
| `z.base.db.mist.password` | `Z_MIST_DB_PASSWORD` | 口令（生产必须注入） |
| `z-mist.server.port` | — | Netty 私有协议端口（默认 9085） |
| `z-mist.master-key` | `Z_MIST_MASTER_KEY` | 主密钥；也可用 JVM 参数 `-Dz-mist.master-key` 注入。代码留有 dev 兜底值，生产必须覆盖 |

本地真实值建议放 gitignore 掉的 `application-local.yml`（`.gitignore` 已屏蔽 `.env` 与 `**/application-{local,dev,prod}.yml`）；
仓库内不含任何明文凭证。

---

## 🔌 API 一览

HTTP 默认端口 `8080`，无 context-path；路径与上表控制器一致（节选）：

| 路径 | 说明 |
|------|------|
| `POST/PUT/DELETE /api/secret`、`GET /api/secret/get`、`GET /api/secret/list` | 密钥 CRUD |
| `GET /api/secret/plain`、`/search`、`/history/list`、`POST /rotate`、`/rollback`、`/dynamic/*` | 密钥运维 |
| `GET /api/master-key/list`、`POST /api/master-key/rotate`、`/envelope`、`/unenvelope` | 主密钥 |
| `POST /api/bulk/import`、`GET /api/bulk/export` | 批量导入导出 |
| `POST /api/eaas/encrypt`、`/decrypt` | EaaS 加解密 |
| `/api/app`、`/api/acl`、`/api/tag`、`/api/log`、`/api/stats`、`/api/rotation` | 应用 / ACL / 标签 / 审计 / 统计 / 轮换 |
| `POST /api/auth/login`、`/logout` | 登录鉴权 |
| `GET /api/mist/health` | 健康检查 |

启动后可用 Knife4j 浏览完整 OpenAPI：`http://localhost:8080/doc.html`。

---

## 🧪 测试

```bash
mvn test
```

reactor 内 `z-mist-core` + `z-mist-web` 的单测（对齐前基线为 39 项、全绿）。注意：

- `z-mist-admin` 不在 reactor，其 `SecretControllerTest`（`@SpringBootTest`，需连真实/临时 MySQL）不会随根 `mvn test` 执行。
- `z-mist-client` 的 `SecretsPreloader` 是示例/预置类而非断言用例。
- 组织口径对 Mockito / `@SpringBootTest` 有限制（见 `_doc/003_script/对齐前_基线.txt` 体检记录），后续重构需按 9 步对齐收敛。

端到端脚本：`bash _doc/003_script/demo.sh`（存→取→列表→限流→审计落库，默认 `MIST_HOST=http://localhost:8080`）、
`bash _doc/003_script/e2e-mist.sh [BASE_URL]`（依赖 curl + jq）。

---

## 🐳 部署

根目录 [`Dockerfile`](Dockerfile) 基于 `eclipse-temurin:8-jdk`，`EXPOSE 8080`，
`HEALTHCHECK` 访问 `http://127.0.0.1:8080/doc.html`，`ENTRYPOINT java -jar app.jar`。

> ⚠️ 现状偏差（如实记录）：Dockerfile 里 `COPY z-mist-admin/target/z-mist-admin-1.0.0-SNAPSHOT.jar app.jar`
> 仍指向历史快照名（当前版本为 `1.0.4`），且 admin 的 `repackage` 被 skip（输出非可执行 jar）。
> 直接 `docker build` 前需修正 jar 名与启动方式，勿据此假设镜像开箱可用。

Maven Central 发布脚本见 [`_doc/003_script/deploy_maven_center.sh`](_doc/003_script/deploy_maven_center.sh)、
[`_doc/003_script/install-settings.sh`](_doc/003_script/install-settings.sh)（发布凭据经环境变量 / GPG 注入，不入库）。

---

## 📄 License

MIT License，见根 [`LICENSE`](LICENSE)。

---

## 文档目录

本项目文档统一收口在 `_doc/` 下：

- [`_doc/001_arch/`](_doc/001_arch/) — 架构文档：
  - [`00-overview.md`](_doc/001_arch/00-overview.md)
- [`_doc/002_deploy/`](_doc/002_deploy/) — 部署：
  - [`init.sql`](_doc/002_deploy/init.sql) — 建库建表初始化 SQL
  - [`z-mist-admin.md`](_doc/002_deploy/z-mist-admin.md) — admin 演示应用部署说明
- [`_doc/003_script/`](_doc/003_script/) — 运维脚本与基线记录：
  - [`build.sh`](_doc/003_script/build.sh)
  - [`demo.sh`](_doc/003_script/demo.sh)
  - [`deploy_maven_center.sh`](_doc/003_script/deploy_maven_center.sh)
  - [`e2e-mist.sh`](_doc/003_script/e2e-mist.sh)
  - [`install-settings.sh`](_doc/003_script/install-settings.sh)
  - [`migrate_package.py`](_doc/003_script/migrate_package.py) — 包名迁移到 `com.zifang.z.mist.*` 的脚本
  - [`package.sh`](_doc/003_script/package.sh)
  - [`对齐前_基线.txt`](_doc/003_script/对齐前_基线.txt) — 对齐 z-boot 家族标准前的构建/测试/体检基线
- [`_doc/004_skill/`](_doc/004_skill/) — AI skill 定义（目前为空目录，暂无 skill）

_Maintained by the z-opc-foundation organization._
