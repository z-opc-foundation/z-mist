import {useEffect, useState} from 'react'
import {Button, Form, Input, message, Modal, Select, Space, Tag} from 'antd'
import {DeleteOutlined, EditOutlined, PlusOutlined, ReloadOutlined, SafetyOutlined} from '@ant-design/icons'
import { ErrorState, ListTable, TableToolbar } from '@yuku123/z-frontend-common/ui'
import request from '../../services/request'

const {Option} = Select

function AclManage() {
    const [data, setData] = useState([])
    const [total, setTotal] = useState(0)
    const [loading, setLoading] = useState(false)
    const [page, setPage] = useState(1)
    const [size, setSize] = useState(20)
    const [error, setError] = useState(null)
    const [filters, setFilters] = useState({secretKey: '', authorizedApp: '', authorizedEnv: '', namespace: ''})
    const [modalOpen, setModalOpen] = useState(false)
    const [editing, setEditing] = useState(null)
    const [form] = Form.useForm()

    useEffect(() => {
        fetchData()
    }, [page, size])

    // override 是为了「查询 / 重置」能同拍带上 page=1 和最新过滤条件：
    // setState 是异步的，直接连着调 fetchData() 会先拿旧值再被 effect 用新值补一刀（双请求）。
    const fetchData = async (override) => {
        const p = override?.page ?? page
        const s = override?.size ?? size
        const f = override?.filters ?? filters
        setLoading(true)
        try {
            const qs = new URLSearchParams({
                current: p, size: s,
                secretKey: f.secretKey,
                authorizedApp: f.authorizedApp,
                authorizedEnv: f.authorizedEnv,
                namespace: f.namespace,
            })
            const res = await request.get(`/acl/page?${qs}`)
            if (res.success) {
                setData(res.data || [])
                setTotal(res.total || 0)
            }
            setError(null)
        } catch (e) {
            // 保留原始 error 对象：ErrorState 会优先取后端 response.data.message，
            // 压成字符串就只剩 axios 的 "Request failed with status code 500"。
            setError(e)
        } finally {
            setLoading(false)
        }
    }

    const handleCreate = () => {
        setEditing(null)
        form.resetFields()
        form.setFieldsValue({
            permissionLevel: 'read',
            group: 'DEFAULT_GROUP',
            namespace: '',
            authorizedEnv: 'prod'
        })
        setModalOpen(true)
    }

    const handleSearch = () => {
        setPage(1)
        fetchData({page: 1})
    }

    const handleReset = () => {
        const empty = {secretKey: '', authorizedApp: '', authorizedEnv: '', namespace: ''}
        setFilters(empty)
        setPage(1)
        fetchData({page: 1, filters: empty})
    }

    const handleEdit = (record) => {
        setEditing(record)
        form.setFieldsValue(record)
        setModalOpen(true)
    }

    const handleDelete = (record) => {
        Modal.confirm({
            title: '确认删除授权',
            content: `将撤销 ${record.authorizedApp} 对 ${record.secretKey} 的访问权限`,
            onOk: async () => {
                const res = await request.delete(`/acl/${record.id}`)
                if (res.success) {
                    message.success('删除成功')
                    fetchData()
                } else {
                    message.error(res.message || '删除失败')
                }
            }
        })
    }

    const handleSubmit = async () => {
        const values = await form.validateFields()
        try {
            const res = editing
                ? await request.put(`/acl/${editing.id}`, values)
                : await request.post('/acl', values)
            if (res.success) {
                message.success(editing ? '更新成功' : '创建成功')
                setModalOpen(false)
                fetchData()
            } else {
                message.error(res.message || '操作失败')
            }
        } catch (e) {
            message.error('操作失败')
        }
    }

    return (
        <div>

            {error && (
                <div style={{marginBottom: 12}}>
                    <ErrorState error={error} title="获取授权列表失败" onRetry={() => fetchData()}/>
                </div>
            )}

            {/* 筛选条件走后端（/acl/page 的 secretKey/authorizedApp/authorizedEnv/namespace 参数），
                是一张查询表单而不是端侧过滤，所以保留在卡片上方的 TableToolbar，
                **不接基座 searchKeys**（否则页面上会出现两个搜索框）。
                查询键与筛选条放在一起（输入框回车即查询），「新建授权」留在卡片头部。
                列表是**服务端分页**，翻页重新请求后端 ⇒ 保留原分页对象，不套客户端分页。 */}
            <TableToolbar
                left={
                    <>
                        <Input placeholder="密钥标识" allowClear
                               value={filters.secretKey}
                               onChange={(e) => setFilters({...filters, secretKey: e.target.value})}
                               onPressEnter={handleSearch}
                               style={{width: 150}}/>
                        <Input placeholder="授权应用" allowClear
                               value={filters.authorizedApp}
                               onChange={(e) => setFilters({...filters, authorizedApp: e.target.value})}
                               onPressEnter={handleSearch}
                               style={{width: 150}}/>
                        <Select placeholder="环境" allowClear
                                value={filters.authorizedEnv || undefined}
                                onChange={(v) => setFilters({...filters, authorizedEnv: v || ''})}
                                style={{width: 110}}>
                            <Option value="dev">dev</Option>
                            <Option value="test">test</Option>
                            <Option value="pre">pre</Option>
                            <Option value="prod">prod</Option>
                        </Select>
                        <Input placeholder="命名空间" allowClear
                               value={filters.namespace}
                               onChange={(e) => setFilters({...filters, namespace: e.target.value})}
                               onPressEnter={handleSearch}
                               style={{width: 120}}/>
                    </>
                }
                right={
                    <>
                        <Button icon={<ReloadOutlined/>} onClick={handleReset}>重置</Button>
                        <Button type="primary" icon={<SafetyOutlined/>} onClick={handleSearch}>查询</Button>
                    </>
                }
            />

            <ListTable
                rows={data}
                columns={[
                    {title: 'ID', dataIndex: 'id', width: 60},
                    {title: '密钥', dataIndex: 'secretKey', width: 200, ellipsis: true},
                    {title: '分组', dataIndex: 'group', width: 110},
                    {title: '命名空间', dataIndex: 'namespace', width: 120},
                    {
                        title: '授权应用', dataIndex: 'authorizedApp', width: 140,
                        render: (s) => <Tag color="blue">{s}</Tag>
                    },
                    {
                        title: '环境', dataIndex: 'authorizedEnv', width: 80,
                        render: (e) => e ? <Tag>{e}</Tag> : <Tag color="default">ALL</Tag>
                    },
                    {
                        title: '权限级别', dataIndex: 'permissionLevel', width: 100,
                        render: (l) => <Tag color={l === 'decrypt' ? 'orange' : 'green'}>{l}</Tag>
                    },
                    {
                        title: '过期时间', dataIndex: 'expireTime', width: 170,
                        render: (t) => t ? new Date(t).toLocaleString() : '永久'
                    },
                    {
                        title: '创建时间', dataIndex: 'gmtCreate', width: 170,
                        render: (t) => t ? new Date(t).toLocaleString() : '-'
                    },
                    {
                        title: '操作', width: 140, fixed: 'right',
                        render: (_, r) => (
                            <Space size="small">
                                <Button type="link" icon={<EditOutlined/>}
                                        onClick={() => handleEdit(r)}>编辑</Button>
                                <Button type="link" danger icon={<DeleteOutlined/>}
                                        onClick={() => handleDelete(r)}>删除</Button>
                            </Space>
                        )
                    },
                ]}
                rowKey="id"
                loading={loading}
                cardTitle="授权列表"
                /* 有 fixed:'right' 的列时必须给 scroll.x：不给的话 antd 不建表内滚动容器，
                   整张表按列宽总和把卡片撑爆 ⇒ 页面横向溢出、卡片头部的「新建授权」被顶出屏幕。 */
                scroll={{x: 1290}}
                pagination={{
                    current: page, pageSize: size, total,
                    showSizeChanger: true,
                    showTotal: (t) => `共 ${t} 条`,
                    onChange: (p, s) => {
                        setPage(p);
                        setSize(s)
                    }
                }}
                emptyTitle="暂无授权"
                emptyDescription="点右上角「新建授权」给应用开一把钥匙"
                cardExtra={
                    <Button type="primary" icon={<PlusOutlined/>} onClick={handleCreate}>新建授权</Button>
                }
            />

            <Modal
                title={editing ? '编辑授权' : '新建授权'}
                open={modalOpen}
                onCancel={() => setModalOpen(false)}
                onOk={handleSubmit}
                width={500}
                destroyOnClose
            >
                <Form form={form} layout="vertical">
                    <Form.Item name="secretKey" label="密钥标识" rules={[{required: true}]}>
                        <Input placeholder="如: db_password"/>
                    </Form.Item>
                    <Form.Item name="group" label="分组">
                        <Input placeholder="DEFAULT_GROUP"/>
                    </Form.Item>
                    <Form.Item name="namespace" label="命名空间">
                        <Input placeholder="prod/test/dev"/>
                    </Form.Item>
                    <Form.Item name="authorizedApp" label="授权应用名" rules={[{required: true}]}>
                        <Input placeholder="如: z-trade-biz"/>
                    </Form.Item>
                    <Form.Item name="authorizedEnv" label="授权环境">
                        <Select>
                            <Option value="dev">dev</Option>
                            <Option value="test">test</Option>
                            <Option value="pre">pre</Option>
                            <Option value="prod">prod</Option>
                        </Select>
                    </Form.Item>
                    <Form.Item name="permissionLevel" label="权限级别">
                        <Select>
                            <Option value="read">read (仅读取密文)</Option>
                            <Option value="decrypt">decrypt (读取并解密)</Option>
                        </Select>
                    </Form.Item>
                </Form>
            </Modal>
        </div>
    )
}

export default AclManage
