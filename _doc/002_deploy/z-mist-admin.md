# z-mist 部署与运行手册

> 自包含: 本目录 (z-mist-admin/) + z-mist-core + z-mist-common + z-mist-spring-boot-starter
> 即可对外提供密钥管理 HTTP API。

## 端口

| 协议    | 端口   | 备注                            |
|-------|------|-------------------------------|
| HTTP  | 8080 | SecretController + Knife4j 文档 |
| Netty | 9085 | 自定义协议（遗留，**不推荐新接入**）          |

## 启动

### 本地开发

```bash
# 1. 准备 MySQL
mysql -h127.0.0.1 -P3306 -uroot -p
> CREATE DATABASE z_mist CHARACTER SET utf8mb4;
> USE z_mist;
> SOURCE z-mist/db.sql;

# 2. 设置主密钥（生产必填）
export Z_MIST_MASTER_KEY="$(openssl rand -hex 32)"

# 3. 启动
mvn -pl z-mist/z-mist-admin -am spring-boot:run
```

### 访问 Knife4j 文档

打开 <http://localhost:8080/doc.html>

## 配置项

| 配置                              | 默认                               | 说明                   |
|---------------------------------|----------------------------------|----------------------|
| `server.port`                   | 8080                             | HTTP 端口              |
| `z-mist.server.port`            | 9085                             | Netty 端口（不推荐使用）      |
| `Z_MIST_MASTER_KEY` (env)       | (无)                              | **生产必填**。AES-256 主密钥 |
| `z-mist.master-key` (yml)       | `z-mist-default-master-key-2024` | dev 兜底值，**生产不要用**    |
| `-Dz-mist.master-key=...` (jvm) | (无)                              | 同 env，JVM 系统属性       |

**生产必须** 通过环境变量或 JVM 参数设置 `Z_MIST_MASTER_KEY`。
如未设置，启动日志会输出 warning（ZMistSecretServiceImpl.resolveMasterKey）。

## API 速查

| Method   | Path                                           | 权限                  | 限流 (次/分) | 用途   |
|----------|------------------------------------------------|---------------------|----------|------|
| `POST`   | `/api/secret`                                  | `mist:secret:write` | 10       | 存/创建 |
| `PUT`    | `/api/secret`                                  | `mist:secret:write` | 10       | 更新   |
| `DELETE` | `/api/secret/{key}?group=&namespace=`          | `mist:secret:write` | 5        | 删除   |
| `GET`    | `/api/secret/get?secretKey=&group=&namespace=` | `mist:secret:read`  | 60       | 查一个  |
| `GET`    | `/api/secret/list?group=&appName=&namespace=`  | `mist:secret:read`  | 60       | 查列表  |

## 客户端使用

业务服务（消费者）通过 [`z-mist-spring-boot-starter`](#) 接入：

```xml
<dependency>
    <groupId>com.zifang</groupId>
    <artifactId>z-mist-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

```yaml
# application.yml
z-mist:
  server-host: z-mist-admin.prod.svc.cluster.local
  server-port: 8080
  app-name: z-biz-news
  app-secret: ${Z_MIST_APP_SECRET}
```

```java
@Autowired private MistClient mistClient;

// 取密钥明文
String dbPassword = mistClient.getSecret("db_password", "database", "prod");
```

## 数据库表

| 表                               | 作用                             | FEATURE    |
|---------------------------------|--------------------------------|------------|
| `z_mist_secret_info`            | 密钥主表                           | (原始)       |
| `z_mist_secret_history`         | 密钥历史版本                         | (原始)       |
| `z_mist_secret_acl`             | 授权关系                           | (原始)       |
| `z_mist_secret_access_log`      | 操作流水（GET/PUT/DELETE/LIST 全部留痕） | FEATURE021 |
| `z_mist_app_info`               | 应用元数据                          | (原始)       |
| `z_mist_users` / `z_mist_roles` | 本地账号（与 z-ctc 不冲突的兜底）           | (原始)       |

## FEATURE 演进

| FEATURE         | 内容                                          | 状态 |
|-----------------|---------------------------------------------|----|
| FEATURE012      | Spring Boot starter 自动装配                    | ✅  |
| FEATURE021      | 访问审计（`z_mist_secret_access_log`）            | ✅  |
| FEATURE022      | @PreAuthorize + 限流 + 主密钥外部化 + 1.0 readiness | ✅  |
| FEATURE023 (P1) | RSA 真实现（当前 stub）                            | ⏳  |
| FEATURE024 (P1) | 单元测试                                        | ⏳  |
| FEATURE025 (P1) | z-ctc 完整集成（替换 isAnonymous 兜底）               | ⏳  |

## 故障排查

| 现象                                     | 排查                                                                 |
|----------------------------------------|--------------------------------------------------------------------|
| 启动报 `z_mist_secret_info doesn't exist` | 没跑 `db.sql`，或跑错库了                                                  |
| 存进去取出来是乱码                              | 主密钥不一致 —— 存/取用了不同 master-key                                       |
| 写日志失败但业务成功                             | 看 `z_mist_secret_access_log` 是不是被禁用/无权限（`recordAccess` 失败仅 log 不抛） |
| 报 401                                  | 期望权限未配；`@PreAuthorize` 生效中，需接 z-ctc 或临时改 `isAnonymous()`           |
