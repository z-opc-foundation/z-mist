import {useEffect, useState} from 'react'
import {Button, Card, Form, Input, InputNumber, message, Modal, Select, Space, Switch, Table, Tag} from 'antd'
import {DeleteOutlined, EditOutlined, PlayCircleOutlined, PlusOutlined, ReloadOutlined} from '@ant-design/icons'
import request from '../../utils/request'

const {Option} = Select

function RotationPolicy() {
    const [policies, setPolicies] = useState([])
    const [history, setHistory] = useState([])
    const [loading, setLoading] = useState(false)
    const [modalOpen, setModalOpen] = useState(false)
    const [editing, setEditing] = useState(null)
    const [form] = Form.useForm()

    useEffect(() => {
        fetchPolicies()
        fetchHistory()
    }, [])

    const fetchPolicies = async () => {
        setLoading(true)
        try {
            const res = await request.get('/rotation/policy/page?current=1&size=100')
            if (res.success) setPolicies(res.data || [])
        } catch (e) {
            message.error('查询策略失败')
        } finally {
            setLoading(false)
        }
    }

    const fetchHistory = async () => {
        try {
            const res = await request.get('/rotation/history/page?current=1&size=50')
            if (res.success) setHistory(res.data || [])
        } catch (e) {
            message.error('查询轮换历史失败')
        }
    }

    const handleCreate = () => {
        setEditing(null)
        form.resetFields()
        form.setFieldsValue({
            cronExpression: '0 0 0 * * ?',
            rotationStrategy: 'auto',
            newValueLength: 32,
            enabled: true,
            group: 'DEFAULT_GROUP',
            namespace: '',
        })
        setModalOpen(true)
    }

    const handleEdit = (record) => {
        setEditing(record)
        form.setFieldsValue({...record, enabled: record.enabled === 1})
        setModalOpen(true)
    }

    const handleDelete = (record) => {
        Modal.confirm({
            title: '确认删除策略',
            content: `将停止 ${record.secretKey} 的自动轮换`,
            onOk: async () => {
                const res = await request.delete(`/rotation/policy/${record.id}`)
                if (res.success) {
                    message.success('删除成功')
                    fetchPolicies()
                }
            }
        })
    }

    const handleTrigger = (record) => {
        Modal.confirm({
            title: '立即触发轮换',
            content: `将立即重新生成 ${record.secretKey} 的值,旧值进入历史快照`,
            onOk: async () => {
                const res = await request.post(`/rotation/policy/${record.id}/trigger`)
                if (res.success) {
                    message.success('触发成功')
                    fetchPolicies()
                    fetchHistory()
                } else {
                    message.error(res.message || '触发失败')
                }
            }
        })
    }

    const handleSubmit = async () => {
        const values = await form.validateFields()
        if (typeof values.enabled === 'boolean') {
            values.enabled = values.enabled ? 1 : 0
        }
        try {
            const res = editing
                ? await request.put(`/rotation/policy/${editing.id}`, values)
                : await request.post('/rotation/policy', values)
            if (res.success) {
                message.success(editing ? '更新成功' : '创建成功')
                setModalOpen(false)
                fetchPolicies()
            } else {
                message.error(res.message || '操作失败')
            }
        } catch (e) {
            message.error('操作失败')
        }
    }

    return (
        <div>
            <Card title="轮换策略">
                <Space style={{marginBottom: 16}}>
                    <Button icon={<ReloadOutlined/>} onClick={fetchPolicies}>刷新</Button>
                    <Button type="primary" icon={<PlusOutlined/>} onClick={handleCreate}>新建策略</Button>
                </Space>
                <Table
                    loading={loading}
                    rowKey="id"
                    dataSource={policies}
                    pagination={{pageSize: 10}}
                    columns={[
                        {title: 'ID', dataIndex: 'id', width: 60},
                        {title: '密钥标识', dataIndex: 'secretKey'},
                        {title: '分组', dataIndex: 'group', width: 110},
                        {title: '命名空间', dataIndex: 'namespace', width: 110},
                        {
                            title: 'cron', dataIndex: 'cronExpression', width: 150,
                            render: (c) => <code>{c}</code>
                        },
                        {
                            title: '策略', dataIndex: 'rotationStrategy', width: 80,
                            render: (s) => <Tag color={s === 'auto' ? 'blue' : 'orange'}>{s}</Tag>
                        },
                        {title: '新值长度', dataIndex: 'newValueLength', width: 90},
                        {
                            title: '启用', dataIndex: 'enabled', width: 70,
                            render: (e) => <Tag color={e === 1 ? 'green' : 'default'}>{e === 1 ? '是' : '否'}</Tag>
                        },
                        {
                            title: '上次轮换', dataIndex: 'lastRotationTime', width: 170,
                            render: (t) => t ? new Date(t).toLocaleString() : '从未'
                        },
                        {
                            title: '下次轮换', dataIndex: 'nextRotationTime', width: 170,
                            render: (t) => t ? new Date(t).toLocaleString() : '-'
                        },
                        {
                            title: '操作', width: 240, fixed: 'right',
                            render: (_, r) => (
                                <Space size="small">
                                    <Button type="link" icon={<PlayCircleOutlined/>}
                                            onClick={() => handleTrigger(r)}>立即触发</Button>
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

            <Card title="轮换历史" style={{marginTop: 16}}>
                <Table
                    rowKey="id"
                    dataSource={history}
                    pagination={{pageSize: 10}}
                    columns={[
                        {title: 'ID', dataIndex: 'id', width: 60},
                        {title: '密钥', dataIndex: 'secretKey', ellipsis: true},
                        {title: '策略ID', dataIndex: 'policyId', width: 80},
                        {title: '旧版本', dataIndex: 'oldVersion', width: 80},
                        {title: '新版本', dataIndex: 'newVersion', width: 80},
                        {
                            title: '触发方式', dataIndex: 'triggerType', width: 100,
                            render: (t) => <Tag
                                color={t === 'cron' ? 'blue' : t === 'manual' ? 'orange' : 'default'}>{t}</Tag>
                        },
                        {
                            title: '成功', dataIndex: 'success', width: 80,
                            render: (s) => <Tag color={s === 1 ? 'green' : 'red'}>{s === 1 ? '是' : '否'}</Tag>
                        },
                        {title: '错误信息', dataIndex: 'errorMessage', ellipsis: true},
                        {
                            title: '时间', dataIndex: 'gmtCreate', width: 170,
                            render: (t) => t ? new Date(t).toLocaleString() : '-'
                        },
                    ]}
                />
            </Card>

            <Modal
                title={editing ? '编辑策略' : '新建策略'}
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
                    <Form.Item name="group" label="分组"><Input/></Form.Item>
                    <Form.Item name="namespace" label="命名空间"><Input/></Form.Item>
                    <Form.Item name="cronExpression" label="cron 表达式"
                               tooltip="标准 6 位 Quartz 格式,如 0 0 0 * * ? 表示每天 0 点">
                        <Input placeholder="0 0 0 * * ?"/>
                    </Form.Item>
                    <Form.Item name="rotationStrategy" label="轮换策略">
                        <Select>
                            <Option value="auto">auto (自动生成随机值)</Option>
                            <Option value="manual">manual (调用 API 时手动触发)</Option>
                        </Select>
                    </Form.Item>
                    <Form.Item name="newValueLength" label="新密钥值长度">
                        <InputNumber min={8} max={128} style={{width: '100%'}}/>
                    </Form.Item>
                    <Form.Item name="enabled" label="启用" valuePropName="checked">
                        <Switch/>
                    </Form.Item>
                    <Form.Item name="description" label="描述">
                        <Input.TextArea rows={2}/>
                    </Form.Item>
                </Form>
            </Modal>
        </div>
    )
}

export default RotationPolicy
