/**
 * z-mist 请求实例
 *
 * 默认 baseURL=/api，token 从 localStorage 'token' 读取。
 * 所有拦截器逻辑（401 跳转、code 业务码判定）由 @yuku123/z-frontend-common 统一处理。
 */
import {request} from '@yuku123/z-frontend-common'

export default request
