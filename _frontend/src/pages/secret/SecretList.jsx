import {useEffect, useState} from 'react'
import {useNavigate} from 'react-router-dom'
import {Button, Input, message, Modal, Space, Table, Tag} from 'antd'
import {
    DeleteOutlined,
    EditOutlined,
    ExclamationCircleOutlined,
    EyeOutlined,
    PlusOutlined,
    ReloadOutlined,
    SearchOutlined,
} from '@ant-design/icons'
import request from '../../utils/request'
import {SecretValueDisplay} from '@yuku123/z-mist-frontend-component'

function SecretList() {
    const navigate = useNavigate()
    const [data, setData] = useState([])
    const [loading, setLoading] = useState(false)
    const [keyword, setKeyword] = useState('')
    const [detailOpen, setDetailOpen] = useState(false)
    const [detailRecord, setDetailRecord] = useState(null)

    const fetchData = async (kw) => {
        setLoading(true)
        try {
            const url = kw ? `/secret/search?keyword=${encodeURIComponent(kw)}` : '/secret/list'
            const res = await request.get(url)
            if (res.success) {
                setData(res.data || [])
            }
        } catch (error) {
            message.error('获取密钥列表失败')
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        fetchData()
    }, [])

    const handleDelete = (record) => {
        Modal.confirm({
            title: '确认删除',
            icon: <ExclamationCircleOutlined/>,
            content: `确定要删除密钥 "${record.secretName}" 吗?`,
            onOk: async () => {
                try {
                    const res = await request.delete(
                        `/secret?secretKey=${encodeURIComponent(record.secretKey)}&group=${encodeURIComponent(record.group || 'DEFAULT_GROUP')}&namespace=${encodeURIComponent(record.namespace || '')}`
                    )
                    if (res.success) {
                        message.success('删除成功')
                        fetchData(keyword)
                    } else {
                        message.error(res.message || '删除失败')
                    }
                } catch (error) {
                    message.error('删除失败')
                }
            },
        })
    }

    const handleRotate = (record) => {
        Modal.confirm({
            title: '立即轮换密钥?',
            content: `将重新生成 "${record.secretKey}" 的值并自增版本`,
            onOk: async () => {
                const res = await request.post(
                    `/secret/rotate?secretKey=${encodeURIComponent(record.secretKey)}&group=${encodeURIComponent(record.group || 'DEFAULT_GROUP')}&namespace=${encodeURIComponent(record.namespace || '')}&newValueLength=32`
                )
                if (res.success) {
                    message.success('轮换成功')
                    fetchData(keyword)
                } else {
                    message.error(res.message || '轮换失败')
                }
            }
        })
    }

    const columns = [
        {
            title: '密钥名称',
            dataIndex: 'secretName',
            key: 'secretName',
        },
        {
            title: '密钥标识',
            dataIndex: 'secretKey',
            key: 'secretKey',
        },
        {
            title: '分组',
            dataIndex: 'group',
            key: 'group',
            width: 120,
        },
        {
            title: '命名空间',
            dataIndex: 'namespace',
            key: 'namespace',
            width: 110,
        },
        {
            title: '密钥类型',
            dataIndex: 'secretType',
            key: 'secretType',
            width: 110,
            render: (type) => {
                const colorMap = {
                    text: 'blue',
                    password: 'red',
                    cert: 'green',
                    key: 'purple',
                    api_key: 'orange',
                }
                return <Tag color={colorMap[type] || 'default'}>{type}</Tag>
            },
        },
        {
            title: '版本',
            dataIndex: 'keyVersion',
            key: 'keyVersion',
            width: 80,
        },
        {
            title: '过期',
            dataIndex: 'expireTime',
            key: 'expireTime',
            width: 170,
            render: (t) => {
                if (!t) return <span style={{color: '#999'}}>-</span>
                const exp = new Date(t)
                const days = Math.floor((exp - new Date()) / (1000 * 60 * 60 * 24))
                let color = 'default'
                if (days < 0) color = 'red'
                else if (days < 7) color = 'orange'
                else if (days < 30) color = 'gold'
                else color = 'green'
                return <Tag color={color}>{exp.toLocaleDateString()}{days >= 0 && ` (${days}d)`}</Tag>
            },
        },
        {
            title: '创建时间',
            dataIndex: 'gmtCreate',
            key: 'gmtCreate',
            width: 170,
            render: (time) => time ? new Date(time).toLocaleString() : '-',
        },
        {
            title: '操作',
            key: 'action',
            width: 320,
            fixed: 'right',
            render: (_, record) => (
                <Space size="small">
                    <Button type="link" icon={<EyeOutlined/>}
                            onClick={() => navigate(`/secret/detail/${record.id}`)}>
                        详情
                    </Button>
                    <Button
                        type="link"
                        icon={<EditOutlined/>}
                        onClick={() => navigate(`/secret/edit/${record.id}`)}
                    >
                        编辑
                    </Button>
                    <Button
                        type="link"
                        onClick={() => handleRotate(record)}
                    >
                        轮换
                    </Button>
                    <Button
                        type="link"
                        danger
                        icon={<DeleteOutlined/>}
                        onClick={() => handleDelete(record)}
                    >
                        删除
                    </Button>
                </Space>
            ),
        },
    ]

    return (
        <>
            <div style={{marginBottom: 16, display: 'flex', justifyContent: 'space-between'}}>
                <h2>密钥管理</h2>
                <Space>
                    <Input.Search
                        placeholder="搜索密钥标识/名称/描述/应用"
                        allowClear
                        onSearch={(v) => {
                            setKeyword(v);
                            fetchData(v)
                        }}
                        style={{width: 320}}
                        enterButton={<><SearchOutlined/> 搜索</>}
                    />
                    <Button icon={<ReloadOutlined/>} onClick={() => {
                        setKeyword('');
                        fetchData('')
                    }}>
                        重置
                    </Button>
                    <Button
                        type="primary"
                        icon={<PlusOutlined/>}
                        onClick={() => navigate('/secret/add')}
                    >
                        新增密钥
                    </Button>
                </Space>
            </div>
            <Table
                columns={columns}
                dataSource={data}
                loading={loading}
                rowKey="id"
                pagination={{pageSize: 10, showSizeChanger: true}}
                scroll={{x: 1200}}
            />

            <Modal
                title={'密钥详情 - ' + (detailRecord?.secretName || '')}
                open={detailOpen}
                onCancel={() => setDetailOpen(false)}
                footer={null}
                width={500}
            >
                {detailRecord && (
                    <SecretValueDisplay
                        value={detailRecord.secretValue}
                        displayValue={'••••••••'}
                    />
                )}
            </Modal>
        </>
    )
}

export default SecretList
