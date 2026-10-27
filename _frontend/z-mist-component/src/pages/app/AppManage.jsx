import {useEffect, useState} from 'react'
import {Button, Form, Input, message, Modal, Select, Space, Switch, Tag} from 'antd'
import {DeleteOutlined, EditOutlined, KeyOutlined, PlusOutlined, ReloadOutlined} from '@ant-design/icons'
import {ErrorState, ListTable, SearchInput} from '@yuku123/z-frontend-common/ui'
import request from '../../services/request'

const {Option} = Select

function AppManage() {
    const [data, setData] = useState([])
    const [total, setTotal] = useState(0)
    const [loading, setLoading] = useState(false)
    const [page, setPage] = useState(1)
    const [size, setSize] = useState(20)
    const [keyword, setKeyword] = useState('')
    const [error, setError] = useState(null)
    const [modalOpen, setModalOpen] = useState(false)
    const [editing, setEditing] = useState(null)
    const [form] = Form.useForm()

    useEffect(() => {
        fetchData()
    }, [page, size])

    // kwOverride 让 Enter 用「输入框此刻的关键词」查询，不必等防抖回写 state。
    // typeof 兜一层：antd 的 onPressEnter / onClick 会把事件对象当第一个参数传进来。
    const fetchData = async (kwOverride) => {
        setLoading(true)
        try {
            const kw = typeof kwOverride === 'string' ? kwOverride : keyword
            const res = await request.get(`/app/page?current=${page}&size=${size}&appName=${encodeURIComponent(kw)}`)
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
        form.setFieldsValue({appType: 'server', enabled: 1, namespace: ''})
        setModalOpen(true)
    }

    const handleEdit = (record) => {
        setEditing(record)
        form.setFieldsValue(record)
        setModalOpen(true)
    }

    const handleDelete = (record) => {
        Modal.confirm({
            title: '确认删除',
            content: `确定要删除应用 "${record.appName}" 吗?`,
            onOk: async () => {
                const res = await request.delete(`/app/${record.id}`)
                if (res.success) {
                    message.success('删除成功')
                    fetchData()
                } else {
                    message.error(res.message || '删除失败')
                }
            }
        })
    }

    const handleResetSecret = (record) => {
        Modal.confirm({
            title: '重置 appSecret',
            content: `将重新生成 "${record.appName}" 的 appSecret,旧密钥立即失效`,
            onOk: async () => {
                const res = await request.post(`/app/${record.id}/reset-secret`)
                if (res.success) {
                    Modal.info({
                        title: '新密钥生成成功',
                        content: (
                            <div>
                                <p>应用: {res.data.appName}</p>
                                <p>新 appSecret (仅显示一次,请妥善保存):</p>
                                <Input.TextArea rows={3} value={res.data.newSecret} readOnly/>
                            </div>
                        )
                    })
                    fetchData()
                } else {
                    message.error(res.message || '重置失败')
                }
            }
        })
    }

    const handleSubmit = async () => {
        const values = await form.validateFields()
        try {
            const res = editing
                ? await request.put(`/app/${editing.id}`, values)
                : await request.post('/app', values)
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
                <div style={{marginBottom: 16}}>
                    <ErrorState error={error} title="获取应用列表失败" onRetry={() => fetchData()}/>
                </div>
            )}

            {/* 列表页走基座 ListTable（lead 008 §2 规则 2/3/4）：分页 + 过滤 + EmptyState 三件套。
                本页是**服务端分页**（/app/page?current&size&appName），翻页重新请求后端，
                所以保留原分页对象，不套基座的客户端分页。
                搜索框走的是后端 appName 参数（不是端侧过滤），因此**不传 searchKeys** ——
                否则页面上会出现两个搜索框。动作按钮放 cardExtra。 */}
            <ListTable
                rows={data}
                columns={[
                    {title: 'ID', dataIndex: 'id', width: 60},
                    {title: '应用名', dataIndex: 'appName'},
                    {
                        title: 'appSecret 指纹', dataIndex: 'appSecret', ellipsis: true,
                        render: (s) => s ? <code>{s.substring(0, 8)}...{s.substring(s.length - 4)}</code> : '-'
                    },
                    {
                        title: '类型', dataIndex: 'appType', width: 80,
                        render: (t) => <Tag color={t === 'server' ? 'blue' : 'green'}>{t}</Tag>
                    },
                    {title: '命名空间', dataIndex: 'namespace', width: 120},
                    {
                        title: '启用', dataIndex: 'enabled', width: 80,
                        render: (e) => <Tag color={e === 1 ? 'green' : 'red'}>{e === 1 ? '是' : '否'}</Tag>
                    },
                    {
                        title: '创建时间', dataIndex: 'gmtCreate', width: 170,
                        render: (t) => t ? new Date(t).toLocaleString() : '-'
                    },
                    {
                        title: '操作', width: 280, fixed: 'right',
                        render: (_, r) => (
                            <Space size="small">
                                <Button type="link" icon={<EditOutlined/>}
                                        onClick={() => handleEdit(r)}>编辑</Button>
                                <Button type="link" icon={<KeyOutlined/>}
                                        onClick={() => handleResetSecret(r)}>重置密钥</Button>
                                <Button type="link" danger icon={<DeleteOutlined/>}
                                        onClick={() => handleDelete(r)}>删除</Button>
                            </Space>
                        )
                    },
                ]}
                rowKey="id"
                loading={loading}
                /* 有 fixed:'right' 的操作列时必须给 scroll.x，否则 antd 不建表内滚动容器，
                   表格按列宽总和溢出卡片 ⇒ 页面横向滚动、卡片头部按钮被顶出屏幕。 */
                scroll={{x: 1100}}
                pagination={{
                    current: page,
                    pageSize: size,
                    total,
                    showSizeChanger: true,
                    onChange: (p, s) => {
                        setPage(p);
                        setSize(s)
                    }
                }}
                emptyTitle="暂无应用"
                emptyDescription="点「新建应用」注册第一个调用方"
                cardExtra={
                    <Space>
                        <SearchInput
                            placeholder="搜索应用名"
                            value={keyword}
                            onChange={setKeyword}
                            onSearch={(v) => fetchData(v)}
                            width={240}
                        />
                        <Button icon={<ReloadOutlined/>} onClick={() => fetchData()}>刷新</Button>
                        <Button type="primary" icon={<PlusOutlined/>} onClick={handleCreate}>
                            新建应用
                        </Button>
                    </Space>
                }
            />

            <Modal
                title={editing ? '编辑应用' : '新建应用'}
                open={modalOpen}
                onCancel={() => setModalOpen(false)}
                onOk={handleSubmit}
                width={500}
                destroyOnClose
            >
                <Form form={form} layout="vertical">
                    <Form.Item name="appName" label="应用名" rules={[{required: true}]}>
                        <Input placeholder="如: z-trade-biz"/>
                    </Form.Item>
                    <Form.Item name="appType" label="应用类型">
                        <Select>
                            <Option value="server">server (服务端)</Option>
                            <Option value="client">client (客户端)</Option>
                        </Select>
                    </Form.Item>
                    <Form.Item name="namespace" label="命名空间">
                        <Input placeholder="如: prod/dev"/>
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

export default AppManage
