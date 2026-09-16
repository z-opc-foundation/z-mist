import {useEffect, useState} from 'react'
import {Button, Card, Form, Input, message, Modal, Select, Space, Table, Tag} from 'antd'
import {DeleteOutlined, EditOutlined, PlusOutlined, ReloadOutlined, SafetyOutlined} from '@ant-design/icons'
import request from '../../utils/request'

const {Option} = Select

function AclManage() {
    const [data, setData] = useState([])
    const [total, setTotal] = useState(0)
    const [loading, setLoading] = useState(false)
    const [page, setPage] = useState(1)
    const [size, setSize] = useState(20)
    const [filters, setFilters] = useState({secretKey: '', authorizedApp: '', authorizedEnv: '', namespace: ''})
    const [modalOpen, setModalOpen] = useState(false)
    const [editing, setEditing] = useState(null)
    const [form] = Form.useForm()

    useEffect(() => {
        fetchData()
    }, [page, size])

    const fetchData = async () => {
        setLoading(true)
        try {
            const qs = new URLSearchParams({
                current: page, size,
                secretKey: filters.secretKey,
                authorizedApp: filters.authorizedApp,
                authorizedEnv: filters.authorizedEnv,
                namespace: filters.namespace,
            })
            const res = await request.get(`/acl/page?${qs}`)
            if (res.success) {
                setData(res.data || [])
                setTotal(res.total || 0)
            }
        } catch (e) {
            message.error('获取授权列表失败')
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
            <Card>
                <Space wrap style={{marginBottom: 16}}>
                    <Input placeholder="密钥标识" allowClear
                           value={filters.secretKey}
                           onChange={(e) => setFilters({...filters, secretKey: e.target.value})}
                           style={{width: 180}}/>
                    <Input placeholder="授权应用" allowClear
                           value={filters.authorizedApp}
                           onChange={(e) => setFilters({...filters, authorizedApp: e.target.value})}
                           style={{width: 180}}/>
                    <Select placeholder="环境" allowClear
                            value={filters.authorizedEnv || undefined}
                            onChange={(v) => setFilters({...filters, authorizedEnv: v || ''})}
                            style={{width: 120}}>
                        <Option value="dev">dev</Option>
                        <Option value="test">test</Option>
                        <Option value="pre">pre</Option>
                        <Option value="prod">prod</Option>
                    </Select>
                    <Input placeholder="命名空间" allowClear
                           value={filters.namespace}
                           onChange={(e) => setFilters({...filters, namespace: e.target.value})}
                           style={{width: 140}}/>
                    <Button type="primary" icon={<SafetyOutlined/>} onClick={() => {
                        setPage(1);
                        fetchData()
                    }}>查询</Button>
                    <Button icon={<ReloadOutlined/>} onClick={fetchData}>刷新</Button>
                    <Button type="primary" icon={<PlusOutlined/>} onClick={handleCreate}>新建授权</Button>
                </Space>

                <Table
                    loading={loading}
                    rowKey="id"
                    dataSource={data}
                    pagination={{
                        current: page, pageSize: size, total,
                        showSizeChanger: true,
                        onChange: (p, s) => {
                            setPage(p);
                            setSize(s)
                        }
                    }}
                    columns={[
                        {title: 'ID', dataIndex: 'id', width: 60},
                        {title: '密钥', dataIndex: 'secretKey', ellipsis: true},
                        {title: '分组', dataIndex: 'group', width: 110},
                        {title: '命名空间', dataIndex: 'namespace', width: 110},
                        {
                            title: '授权应用', dataIndex: 'authorizedApp',
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
                            title: '操作', width: 150, fixed: 'right',
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
                />
            </Card>

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
