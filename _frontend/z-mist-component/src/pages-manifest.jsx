import {
    AppstoreOutlined, DashboardOutlined, FileTextOutlined, KeyOutlined,
    LockOutlined, ReloadOutlined, SafetyOutlined,
} from '@ant-design/icons'
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
export const menuItems = [
    {key: '/dashboard', icon: <DashboardOutlined/>, label: '数据看板'},
    {key: '/secret', icon: <KeyOutlined/>, label: '密钥管理'},
    {key: '/eaas', icon: <LockOutlined/>, label: '加密即服务'},
    {key: '/rotation', icon: <ReloadOutlined/>, label: '轮换策略'},
    {key: '/acl', icon: <SafetyOutlined/>, label: '授权管理'},
    {key: '/app', icon: <AppstoreOutlined/>, label: '应用管理'},
    {key: '/log', icon: <FileTextOutlined/>, label: '访问日志'},
]

// 路由清单（z-mist 子路由较复杂：secret/add、secret/edit/:id、secret/detail/:id，
// 用 path + Component + children 三元组表示；App 壳在 suit/宿主侧展开）。
export const routeTable = [
    {path: 'dashboard', Component: Dashboard},
    {path: 'secret', Component: SecretList},
    {path: 'secret/add', Component: SecretEdit},
    {path: 'secret/edit/:id', Component: SecretEdit},
    {path: 'secret/detail/:id', Component: SecretDetail},
    {path: 'eaas', Component: EaasConsole},
    {path: 'rotation', Component: RotationPolicy},
    {path: 'acl', Component: AclManage},
    {path: 'app', Component: AppManage},
    {path: 'log', Component: AccessLog},
]
export {default as Dashboard} from './pages/dashboard/Dashboard'
export {default as Login} from './pages/login/Login'
