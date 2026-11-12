/**
 * z-mist 请求实例
 *
 * 默认 baseURL=/api，token 从 localStorage 'token' 读取。
 * 401/业务码拦截逻辑由 @yuku123/z-frontend-common 统一处理。
 *
 * ⚠ 响应字段名归一（snake → camel）：平台装配（z-opc）时全局 Jackson 是
 * SNAKE_CASE，独立部署（z-mist-admin）时是 camelCase（SecretController 头注钉的契约），
 * 而本组件全部页面按 camelCase 写 ⇒ 在请求层深归一一次，两种部署下读路径都成立。
 */
import {createRequest} from '@yuku123/z-frontend-common'

const snakeToCamel = (key) => key.replace(/_([a-zA-Z0-9])/g, (_, c) => c.toUpperCase())

function deepCamelize(value) {
    if (Array.isArray(value)) return value.map(deepCamelize)
    if (value && typeof value === 'object') {
        const out = {}
        for (const [k, v] of Object.entries(value)) {
            out[snakeToCamel(k)] = deepCamelize(v)
        }
        return out
    }
    return value
}

const request = createRequest()
request.interceptors.response.use((body) => deepCamelize(body))

export default request
