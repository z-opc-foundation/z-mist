import {BrowserRouter as Router, Navigate, Route, Routes} from 'react-router-dom'
import {AppLayout} from '@yuku123/z-frontend-common'
import {
    AppstoreOutlined,
    DashboardOutlined,
    FileTextOutlined,
    KeyOutlined,
    LockOutlined,
    ReloadOutlined,
    SafetyOutlined,
} from '@ant-design/icons'
import SecretList from './pages/secret/SecretList'
import SecretEdit from './pages/secret/SecretEdit'
import SecretDetail from './pages/secret/SecretDetail'
import Login from './pages/login/index'
import Dashboard from './pages/dashboard/Dashboard'
import AppManage from './pages/app/AppManage'
import AclManage from './pages/acl/AclManage'
import AccessLog from './pages/log/AccessLog'
import RotationPolicy from './pages/rotation/RotationPolicy'
import EaasConsole from './pages/eaas/EaasConsole'

const menuItems = [
    {key: '/dashboard', icon: <DashboardOutlined/>, label: '数据看板'},
    {key: '/secret', icon: <KeyOutlined/>, label: '密钥管理'},
    {key: '/eaas', icon: <LockOutlined/>, label: '加密即服务'},
    {key: '/rotation', icon: <ReloadOutlined/>, label: '轮换策略'},
    {key: '/acl', icon: <SafetyOutlined/>, label: '授权管理'},
    {key: '/app', icon: <AppstoreOutlined/>, label: '应用管理'},
    {key: '/log', icon: <FileTextOutlined/>, label: '访问日志'},
]

function App() {
    return (
        <Router>
            <Routes>
                <Route path="/login" element={<Login/>}/>
                <Route path="/" element={
                    <AppLayout menuItems={menuItems} appTitle="z-mist 密钥管理" appShort="MIST"/>
                }>
                    <Route index element={<Navigate to="/dashboard" replace/>}/>
                    <Route path="dashboard" element={<Dashboard/>}/>
                    <Route path="secret" element={<SecretList/>}/>
                    <Route path="secret/add" element={<SecretEdit/>}/>
                    <Route path="secret/edit/:id" element={<SecretEdit/>}/>
                    <Route path="secret/detail/:id" element={<SecretDetail/>}/>
                    <Route path="eaas" element={<EaasConsole/>}/>
                    <Route path="rotation" element={<RotationPolicy/>}/>
                    <Route path="acl" element={<AclManage/>}/>
                    <Route path="app" element={<AppManage/>}/>
                    <Route path="log" element={<AccessLog/>}/>
                </Route>
            </Routes>
        </Router>
    )
}

export default App
