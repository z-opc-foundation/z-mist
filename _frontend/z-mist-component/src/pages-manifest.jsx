import { AppstoreOutlined, DashboardOutlined, FileTextOutlined, HomeOutlined, KeyOutlined, LockOutlined, ReloadOutlined, SafetyOutlined } from '@ant-design/icons'
import Login from './pages/login/Login'
import Dashboard from './pages/dashboard/Dashboard'
import SecretList from './pages/secret/SecretList'
import SecretEdit from './pages/secret/SecretEdit'
import SecretDetail from './pages/secret/SecretDetail'
import EaasConsole from './pages/eaas/EaasConsole'
import RotationPolicy from './pages/rotation/RotationPolicy'
import AclManage from './pages/acl/AclManage'
import AppManage from './pages/app/AppManage'
import AccessLog from './pages/log/AccessLog'

/** 菜单 + 路由清单（lead 005 §8.2 manifest）。App 壳在 suit/宿主侧组装。 */

// 路由清单（z-mist 子路由较复杂：secret/add、secret/edit/:id、secret/detail/:id，
// 用 path + Component + children 三元组表示；App 壳在 suit/宿主侧展开）。
export {default as Dashboard} from './pages/dashboard/Dashboard'
export {default as Login} from './pages/login/Login'
import HomePage from './pages/HomePage'

/** 菜单 + 路由清单（lead 008 §10/§14/§16 批量落地）。App 壳在 suit 侧组装。 */
export const appMeta = { title: 'z-mist 密钥管理', short: 'z-mist' }

export const menuItems = [
    { key: '/z-mist/home', label: '首页', icon: <HomeOutlined /> },
    { key: '/z-mist/dashboard', label: '数据看板', icon: <DashboardOutlined /> },
    { key: '/z-mist/secret', label: '密钥管理', icon: <KeyOutlined /> },
    { key: '/z-mist/eaas', label: '加密即服务', icon: <LockOutlined /> },
    { key: '/z-mist/rotation', label: '轮换策略', icon: <ReloadOutlined /> },
    { key: '/z-mist/acl', label: '授权管理', icon: <SafetyOutlined /> },
    { key: '/z-mist/app', label: '应用管理', icon: <AppstoreOutlined /> },
    { key: '/z-mist/log', label: '访问日志', icon: <FileTextOutlined /> },
]

export const routes = [
    { path: '/z-mist/home', Component: HomePage },
    { path: '/z-mist/dashboard', Component: Dashboard },
    { path: '/z-mist/secret', Component: SecretList },
    { path: '/z-mist/secret/add', Component: SecretEdit },
    { path: '/z-mist/secret/edit/:id', Component: SecretEdit },
    { path: '/z-mist/secret/detail/:id', Component: SecretDetail },
    { path: '/z-mist/eaas', Component: EaasConsole },
    { path: '/z-mist/rotation', Component: RotationPolicy },
    { path: '/z-mist/acl', Component: AclManage },
    { path: '/z-mist/app', Component: AppManage },
    { path: '/z-mist/log', Component: AccessLog },
]

export { default as HomePage } from './pages/HomePage'
export { default as LoginPage } from './pages/LoginPage'
