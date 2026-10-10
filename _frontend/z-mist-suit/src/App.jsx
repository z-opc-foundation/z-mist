import {Navigate, Route, Routes} from 'react-router-dom'
import {AppLayout} from '../../../../_shared/z-frontend-common-local/dist/z-frontend-common.es.js'
import {Dashboard, Login, menuItems, routeTable} from '@yuku123/z-mist-component/pages'

// 壳只做组装（lead 005 §9.1）：AppLayout + manifest 路由（z-mist 无鉴权守卫）。
export default function App() {
    return (
        <Routes>
            <Route path="/login" element={<Login/>}/>
            <Route path="/" element={
                <AppLayout menuItems={menuItems} appTitle="z-mist 密钥管理" appShort="MIST" appIcon={{icon: <img src="/icon.png" alt="MIST" style={{width: "100%", height: "100%", objectFit: "cover", borderRadius: 8}}/>, color: '#64748b', label: 'MIST'}}/>
            }>
                <Route index element={<Navigate to="/dashboard" replace/>}/>
                {routeTable.map((r) => (
                    <Route key={r.path} path={r.path} element={<r.Component/>}/>
                ))}
            </Route>
        </Routes>
    )
}
