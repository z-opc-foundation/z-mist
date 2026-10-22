# z-mist-component

z-mist（密钥管理）跨仓唯一前端面：把「密钥台账 / 加密即服务 / 轮换策略 / 授权管理 / 应用管理 /
访问日志 / 数据看板」这一整套密钥管理页面收敛成一个可复用的 React 组件包。

- **npm 包名**：`@yuku123/z-mist-component`
- **宿主接入**：`@yuku123/z-mist-component/pages`（manifest + 页面），主壳可只取 `ComponentDocPage`
- **后端**：z-mist 的密钥管理接口，基座请求实例 `baseURL='/api'`
- **技术栈**：React 19 + antd 6 + react-router-dom 7 + vite（library mode，双入口 `.` / `./pages`）

---

## 一、能力概览

路由前缀 `/z-mist`，菜单与路由在 `src/pages-manifest.jsx` 里声明。

| 页面 | 路由（相对 `/z-mist/*`） | 源文件 | 能力 |
|------|--------------------------|--------|------|
| 登录 | `login` | `pages/login/Login.jsx` | 用户名密码登录（`POST /auth/login`） |
| 首页 | `home` | `pages/HomePage.jsx` | 入口卡片页 |
| 数据看板 | `dashboard` | `pages/dashboard/Dashboard.jsx` | 密钥总数、今日操作次数、Top 密钥、即将过期密钥、近 7 天趋势 |
| 密钥管理 | `secret` | `pages/secret/SecretList.jsx` | 密钥台账列表，支持关键词搜索 |
| 新建 / 编辑密钥 | `secret/add`、`secret/edit/:id` | `pages/secret/SecretEdit.jsx` | 密钥元信息编辑与保存 |
| 密钥详情 | `secret/detail/:id` | `pages/secret/SecretDetail.jsx` | 密钥详情 + 密文值展示 + 版本回滚 |
| 加密即服务 | `eaas` | `pages/eaas/EaasConsole.jsx` | 明文加密 / 解密控制台 + 信封加密说明 |
| 轮换策略 | `rotation` | `pages/rotation/RotationPolicy.jsx` | 轮换策略增删改查 + 手动触发 + 轮换历史 |
| 授权管理 | `acl` | `pages/acl/AclManage.jsx` | 授权记录增删改查 |
| 应用管理 | `app` | `pages/app/AppManage.jsx` | 应用增删改查 + 重置密钥 |
| 访问日志 | `log` | `pages/log/AccessLog.jsx` | 访问日志分页查询 |
| 组件文档 | `docs` | `pages/ComponentDoc.jsx` | 本文件渲染页（左侧 240px TOC + 右侧正文） |

`src/pages/MistApp.jsx` 是子路由挂载点（`secret` / `app` / `acl`），被 `routes` 里的
`/z-mist/:rest*` 承接，同时从包根具名导出。

## 二、导出面

```js
// 主入口（. 桶）
import {request, MistApp, ComponentDocPage} from '@yuku123/z-mist-component'

// 宿主组装面（./pages 桶）
import {menuItems, routes, HomePage, LoginPage, Dashboard} from '@yuku123/z-mist-component/pages'
```

`./pages` 桶是 `export * from './pages-manifest.jsx'` 的整份转发。

## 三、API 端点

请求实例是基座的 `request`（`createRequest()`，`baseURL='/api'`，token 键 `token`）。
下表是页面里**实际调用**的路径。

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/auth/login` | 登录 |
| GET | `/secret/list` | 密钥列表（详情页 / 编辑页也用它取列表） |
| GET | `/secret/search?keyword=` | 密钥搜索（有关键词时走这个） |
| POST | `/secret` | 新建密钥 |
| PUT | `/secret` | 修改密钥 |
| POST | `/secret/rollback?historyId=` | 回滚到历史版本 |
| POST | `/eaas/encrypt` | 加密即服务：明文加密 |
| POST | `/eaas/decrypt` | 加密即服务：解密 |
| GET | `/rotation/policy/page?current=&size=` | 轮换策略分页 |
| POST | `/rotation/policy` | 新建轮换策略 |
| PUT | `/rotation/policy/{id}` | 修改轮换策略 |
| DELETE | `/rotation/policy/{id}` | 删除轮换策略 |
| POST | `/rotation/policy/{id}/trigger` | 手动触发轮换 |
| GET | `/rotation/history/page?current=&size=` | 轮换历史 |
| GET | `/acl/page?...` | 授权记录分页 |
| POST | `/acl` | 新建授权 |
| PUT | `/acl/{id}` | 修改授权 |
| DELETE | `/acl/{id}` | 删除授权 |
| GET | `/app/page?current=&size=&appName=` | 应用分页（按名搜索） |
| POST | `/app` | 新建应用 |
| PUT | `/app/{id}` | 修改应用 |
| DELETE | `/app/{id}` | 删除应用 |
| POST | `/app/{id}/reset-secret` | 重置应用密钥 |
| GET | `/log/page?...` | 访问日志分页 |
| GET | `/stats/overview` | 密钥总览统计 |
| GET | `/stats/daily?days=7` | 近 N 天操作趋势 |
| GET | `/stats/top-secrets?limit=10` | Top 密钥 |
| GET | `/stats/expiring` | 即将过期的密钥 |

## 四、页面说明

### 4.1 数据看板

并行拉四个统计端点（`/stats/overview`、`/stats/daily?days=7`、`/stats/top-secrets?limit=10`、
`/stats/expiring`），渲染密钥总数、今日操作次数等卡片。加载失败走基座 `ErrorState` + 重试。

### 4.2 密钥管理（列表）

密钥台账。**有关键词走 `/secret/search?keyword=`，没有走 `/secret/list`** —— 两条路径要一起改，
只改一条会出现「搜索能搜到、列表却空」。

### 4.3 新建 / 编辑密钥

同一组件承担新建与编辑两种模式（`/secret/add` 与 `/secret/edit/:id`）：编辑时先取列表定位当前密钥，
保存时 `POST /secret`（新建）或 `PUT /secret`（修改）。加载失败用基座 `ErrorState`（标题「获取密钥详情失败」）。

### 4.4 密钥详情

展示密钥元信息 + 密文值（`pages/secret/SecretValueDisplay.jsx` 负责展示），并支持按 `historyId` 回滚。
密钥不存在时展示「该密钥可能已被删除，返回列表重新选择」。

### 4.5 加密即服务

明文加密 / 解密控制台：选算法 → 输入明文 → 得 Base64 密文，可反向解密。
页面同时说明信封加密（Envelope Encryption）的用法：业务方把敏感数据加密后存 MySQL/Redis，
后续解密反查，**密钥值不落库**。

### 4.6 轮换策略

策略列表 + 新建 / 编辑 / 删除 + 手动触发轮换，下方展示轮换历史（默认取 50 条）。
策略列表固定 `current=1&size=100`，历史固定 `current=1&size=50`。

### 4.7 授权管理

授权记录的分页查询与增删改查。

### 4.8 应用管理

应用分页（支持按 `appName` 搜索）+ 增删改查 + **重置密钥**（`POST /app/{id}/reset-secret`）。

### 4.9 访问日志

访问日志分页查询。

### 4.10 组件文档

本文件。内容以 `?raw` 在编译期内联，零额外请求；左侧 240px TOC 随滚动联动。

## 五、配置

| 配置项 | 默认值 | 说明 |
|---|---|---|
| 请求实例 | 基座 `request` | `baseURL='/api'`，token 键 `token`，401 跳转与业务码判定由基座统一处理 |
| `mistErr(e)` | — | 从 500 响应体里取真因（如 `Table 'oc.z_mist_secret_tag' doesn't exist`），避免被 axios 压成「没有数据」 |
| 轮换策略列表条数 | `size=100` | 固定值，未开放配置 |
| 轮换历史条数 | `size=50` | 固定值，未开放配置 |

## 六、已知限制

- ⚠ **密钥明文/密文值属于敏感信息**：页面只做展示与回滚，没有任何「导出密钥」入口；
  后续加功能时不要顺手加批量导出。
- `services/api.js` 用的是**默认导入**（桶对象）当 axios 用（`import request from '@yuku123/z-frontend-common'`）。
  基座的桶在 1.1.x 上补了 HTTP 动词转发才让这种写法能跑；**新写代码请用具名导入**
  `import { request } from '@yuku123/z-frontend-common'`。
- `services/request.js` 重新导出了一份 `request`，与 `services/api.js` 的默认导出**是同一个实例**
  （都来自基座），但两个文件各写一份导出，容易让人以为有两个实例。
- 密钥列表的搜索路径与列表路径是**两条不同的端点**（`/secret/search` vs `/secret/list`），
  改返回结构时两条都要改。
- 轮换策略 / 轮换历史的分页参数是**写死的**（`current=1`），没有翻页控件。
- 页面里 `request.get('/secret/list')` 拿的是**全量列表**再在本地定位，没有按 id 取单个密钥的端点 ——
  密钥量大时详情页会变慢。
- 组件包必须 external 基座；`vite.config.js` 的 `external` 是精确字符串匹配，
  `@yuku123/z-frontend-common/ui` 与 `/ui/echarts` 两个子路径必须逐个列出，漏了基座会被复制进 dist。

## 七、变更记录

| 版本 | 变更 |
|---|---|
| 0.1.1 | 新增组件文档页 `ComponentDocPage`（lead 008 §7）：`src/DOC.md` + 基座 `ComponentDoc` 薄包装 |
| 0.1.0 | 首版：密钥管理页面集 + manifest + 自洽 request |
