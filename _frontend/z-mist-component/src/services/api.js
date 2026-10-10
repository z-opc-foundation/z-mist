/**
 * z-mist 模块的 API 客户端。
 *
 * 2026-10-04 瘦身：原先本文件是 `src/common/services/api.js` 的一份完整复制品
 * （32 个 makeApi 产物 + 项目/任务/鉴权一组函数 + 4 个假数据桩），
 * 全仓逐条解析 import 后确认**只有 default(request) 与 mistErr 被引用**
 * （导入方 3 个：mist/pages/app/AppList.jsx、mist/pages/secret/SecretList.jsx、
 * mist/pages/acl/AclList.jsx，全是 `import request, {mistErr} from '@/mist/services/api'`）。
 * 其余 51 个导出零引用。
 *
 * 同名的 configApi / approvalApi / designerApi / ctcAcOrgApi 等在 common、
 * wf、agent/api、ctc/components 各有自己的定义，删这里不影响它们。
 *
 * 判定口径：按 bundler 规则解析每个 import 的实际路径（@/ → src/，
 * ./ 与 ../ 逐级上溯，扩展名顺序沿用 Vite 默认），而不是按名字 grep——
 * 早期用「名字是否在全仓出现过」判断过一轮，结果 51 个导出全部误判为「活」，
 * 因为这三个文件的导出名与其他模块大量重名。
 */
import request from '@/common'

export default request

/**
 * z-mist 的 500 响应体里带着真因（例如 "Table 'oc.z_mist_secret_tag' doesn't exist"），
 * 而 axios 默认只给 "Request failed with status code 500" —— 压掉它就变成"没有数据"。
 */
export function mistErr (e) {
    return e?.response?.data?.message || e?.message || '接口错误'
}
