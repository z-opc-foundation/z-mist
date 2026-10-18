import {Navigate, Route, Routes} from 'react-router-dom'
import SecretList from './secret/SecretList'
import AppManage from './app/AppManage'
import AclManage from './acl/AclManage'

export default function MistIndex() {
    return (
        <Routes>
            <Route index element={<Navigate to="secret" replace/>}/>
            <Route path="secret" element={<SecretList/>}/>
            <Route path="app" element={<AppManage/>}/>
            <Route path="acl" element={<AclManage/>}/>
        </Routes>
    )
}
