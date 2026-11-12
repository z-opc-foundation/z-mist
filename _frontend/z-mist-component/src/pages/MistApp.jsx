import {Navigate, Route, Routes} from 'react-router-dom'
import SecretList from './secret/SecretList'
import SecretEdit from './secret/SecretEdit'
import SecretDetail from './secret/SecretDetail'
import AppManage from './app/AppManage'
import AclManage from './acl/AclManage'

export default function MistIndex() {
    return (
        <Routes>
            <Route index element={<Navigate to="secret" replace/>}/>
            <Route path="secret" element={<SecretList/>}/>
            {/* 宿主壳把本组件挂 /mist/*；suit 独立跑时走 pages-manifest 的 /z-mist/*。
                页面内跳转一律用相对路径，两处挂载才都不断链。 */}
            <Route path="secret/add" element={<SecretEdit/>}/>
            <Route path="secret/edit/:id" element={<SecretEdit/>}/>
            <Route path="secret/detail/:id" element={<SecretDetail/>}/>
            <Route path="app" element={<AppManage/>}/>
            <Route path="acl" element={<AclManage/>}/>
        </Routes>
    )
}
