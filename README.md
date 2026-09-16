# z-mist · Secret Management Platform

> **L3 中间件 · Spring Boot Starter 一行集成** · **Maven Central**: `io.github.yuku123:z-mist-*`
>
> 分布式密钥/秘密管理平台：Netty 私有协议 + MyBatis-Plus 数据层 + Spring Boot Starter + Web 控制台 + 调度器（密钥轮换/过期/Webhook 通知）

[![Maven Central](https://img.shields.io/maven-central/v/io.github.yuku123/z-mist)](https://central.sonatype.com/search?q=g%3Aio.github.yuku123%20AND%20a%3Az-mist*)
[![MIT License](https://img.shields.io/badge/license-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![Java 8+](https://img.shields.io/badge/java-8%2B-orange)](https://openjdk.java.net/)

## 模块结构

```
z-mist (aggregator)         → io.github.yuku123:z-mist
├─ z-mist-common            → 协议层（Netty 编解码 / JSON 序列化 / DTO）
├─ z-mist-core              → 服务端核心（Netty 服务 + DataSource + Scheduler）
├─ z-mist-web               → Spring Boot Starter 入口（SecretController + AutoConfiguration）
├─ z-mist-client            → 客户端 SDK（MistClient + ClientBusinessHandler）
└─ z-mist-sdk               → fat-jar 可执行 SDK 演示

z-mist-admin (不入 reactor) → 独立可启动演示（前端 + Admin API）
_frontend    (不入 reactor) → z-mist-admin 的前端源码（vite + vue）
```

## 快速集成

```xml
<dependency>
    <groupId>io.github.yuku123</groupId>
    <artifactId>z-mist-web</artifactId>
    <version>1.0.1</version>
</dependency>
```

业务模块 `application.yml`:

```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL}
    username: ${SPRING_DATASOURCE_USERNAME}
    password: ${SPRING_DATASOURCE_PASSWORD}

z-mist:
  server:
    port: 9085
  master-key: ${Z_MIST_MASTER_KEY}
```

> ⚠️ **凭证规范**：所有敏感字段必须从环境变量读取，详见 [lead/008_组织规范/001_凭证管理规范.md](https://github.com/z-opc-foundation/z-opc-foundation-lead/blob/main/008_组织规范/001_凭证管理规范.md)。

## 客户端 SDK

```xml
<dependency>
    <groupId>io.github.yuku123</groupId>
    <artifactId>z-mist-client</artifactId>
    <version>1.0.1</version>
</dependency>
```

## 本地开发 / Demo 启动

```bash
# 1. 复制凭证模板
cp .env.example .env
# 编辑 .env 填入 SPRING_DATASOURCE_PASSWORD / Z_MIST_MASTER_KEY

# 2. 构建 + 启动 admin demo
mvn clean install -DskipTests
cd z-mist-admin
mvn spring-boot:run \
    -Dspring-boot.run.jvmArguments="-Dspring.profiles.active=local"
```

## 部署到 Maven Central

详见 [deploy_maven_center.sh](./deploy_maven_center.sh) 子命令或 `bash deploy_maven_center.sh readme`。

```bash
# 首次发布
brew install gnupg                                       # GPG 签名依赖
./install-settings.sh                                    # 注入 ~/.m2/settings.xml
./deploy_maven_center.sh gpg-init                        # 生成 GPG 密钥 + 写 .env
./deploy_maven_center.sh publish                         # 实际发布

# 验证
./deploy_maven_center.sh verify

# 日常发版
sed -i '' 's|<revision>X.Y.Z-SNAPSHOT</revision>|<revision>X.Y.Z</revision>|' pom.xml
git add -A && git commit -m "release: X.Y.Z"
git tag vX.Y.Z && git push origin main vX.Y.Z
bash deploy_maven_center.sh publish
```

## 凭证与安全

**本仓库不含任何明文凭证**。所有 Spring 配置、Docker 镜像、K8s manifest 中的连接信息均通过环境变量注入。

参考规范：[z-opc-foundation-lead/008_组织规范/001_凭证管理规范.md](https://github.com/z-opc-foundation/z-opc-foundation-lead/blob/main/008_组织规范/001_凭证管理规范.md)

## 文档

- [_doc/001_项目总览.md](./_doc/001_项目总览.md) — 模块边界、依赖、版本策略
- [_doc/002_模块依赖与构建矩阵.md](./_doc/002_模块依赖与构建矩阵.md) — 模块依赖关系图
- [lead 仓组织规范](https://github.com/z-opc-foundation/z-opc-foundation-lead/blob/main/008_组织规范/) — 全 z-* 仓通用规范

## 许可

MIT License — 详见 [LICENSE](./LICENSE)

## 关联

- 父组织：[z-opc-foundation](https://github.com/z-opc-foundation)
- L2 封装：[z-boot](https://github.com/z-opc-foundation/z-boot)（z-boot-mist-starter 由 z-boot 提供聚合 starter）
- L1 业务：[z-opc](https://github.com/z-opc-foundation/z-opc)（z-mist 实际服务的产品）

## 文档目录

本项目文档统一收口在 `_doc/` 下:

- [`_doc/001_arch/`](_doc/001_arch/) — 架构文档 (项目总览 / 模块结构 / 接口清单 / DB schema / 前端 / 能力 / roadmap):
  - [`00-overview.md`](_doc/001_arch/00-overview.md)

- [`_doc/002_deploy/`](_doc/002_deploy/) — 部署文档 (含 `init.sql`):
  - [`init.sql`](_doc/002_deploy/init.sql)

- [`_doc/003_script/`](_doc/003_script/) — 运维脚本:
  - [`build.sh`](_doc/003_script/build.sh)
  - [`demo.sh`](_doc/003_script/demo.sh)
  - [`deploy_maven_center.sh`](_doc/003_script/deploy_maven_center.sh)
  - [`e2e-mist.sh`](_doc/003_script/e2e-mist.sh)
  - [`install-settings.sh`](_doc/003_script/install-settings.sh)
  - [`migrate_package.py`](_doc/003_script/migrate_package.py)
  - [`package.sh`](_doc/003_script/package.sh)

各文档详细说明见各子目录。
