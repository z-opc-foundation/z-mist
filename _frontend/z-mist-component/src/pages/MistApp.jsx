import {Navigate, Route, Routes} from 'react-router-dom'
import SecretList from './secret/SecretList'
import AppList from './app/AppList'
import AclList from './acl/AclList'

export default function MistIndex() {
    return (
        <Routes>
            <Route index element={<Navigate to="secret" replace/>}/>
            <Route path="secret" element={<SecretList/>}/>
            <Route path="app" element={<AppList/>}/>
            <Route path="acl" element={<AclList/>}/>
        </Routes>
    )
}
