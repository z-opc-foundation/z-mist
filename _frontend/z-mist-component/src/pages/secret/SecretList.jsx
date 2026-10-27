import {useEffect, useState} from 'react'
import {useNavigate} from 'react-router-dom'
import {Button, message, Modal, Space, Tag} from 'antd'
import {
    DeleteOutlined,
    EditOutlined,
    ExclamationCircleOutlined,
    EyeOutlined,
    PlusOutlined,
    ReloadOutlined,
} from '@ant-design/icons'
import {ErrorState, ListTable, SearchInput} from '@yuku123/z-frontend-common/ui'
import request from '../../services/request'
import SecretValueDisplay from './SecretValueDisplay'

function SecretList() {
    const navigate = useNavigate()
    const [data, setData] = useState([])
    const [loading, setLoading] = useState(false)
    const [keyword, setKeyword] = useState('')
    // lead 008 §17：读失败常驻报错 + 重试，避免空表被误读成“没有密钥”。
    const [loadError, setLoadError] = useState(null)
    const [detailOpen, setDetailOpen] = useState(false)
    const [detailRecord, setDetailRecord] = useState(null)

    const fetchData = async (kw) => {
        setLoading(true)
        try {
            const url = kw ? `/secret/search?keyword=${encodeURIComponent(kw)}` : '/secret/list'
            const res = await request.get(url)
            if (res.success) {
                setData(res.data || [])
                setLoadError(null)
            } else {
                setLoadError(res.message || '密钥列表没有读到')
            }
        } catch (error) {
            setLoadError(error)
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
                            onClick={() => navigate(`/z-mist/secret/detail/${record.id}`)}>
                        详情
                    </Button>
                    <Button
                        type="link"
                        icon={<EditOutlined/>}
                        onClick={() => navigate(`/z-mist/secret/edit/${record.id}`)}
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
            {/* lead 008 §13：菜单直开页不写页内标题（菜单 label 已是页面名）。 */}
            {loadError && (
                <ErrorState
                    error={loadError}
                    title="密钥列表没有读到"
                    description="接口没有返回数据，下方空表不代表真的没有密钥。"
                    onRetry={() => fetchData(keyword)}
                    style={{marginBottom: 16}}
                />
            )}
            {/* 列表页走基座 ListTable（lead 008 §2 规则 2/3/4）：分页 / 过滤 / EmptyState 三件套。
                搜索走后端（/secret/search?keyword=），不是端侧过滤 ⇒ **不传 searchKeys**，
                否则页面上会出现两个搜索框；动作按钮放 cardExtra。
                密钥列表是一次性全量拉回的，分页用基座默认的客户端分页。 */}
            <ListTable
                rows={data}
                columns={columns}
                rowKey="id"
                loading={loading}
                scroll={{x: 1200}}
                emptyTitle={loadError ? '数据没有读到' : '暂无密钥'}
                emptyDescription={loadError ? '上方错误条已说明原因，点「重新加载」重试。' : '点右上角「新增密钥」创建第一个'}
                cardExtra={
                    <Space>
                        <SearchInput
                            placeholder="搜索密钥标识/名称/描述/应用"
                            value={keyword}
                            onChange={setKeyword}
                            onSearch={(v) => {
                                setKeyword(v)
                                fetchData(v)
                            }}
                            width={320}
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
                            onClick={() => navigate('/z-mist/secret/add')}
                        >
                            新增密钥
                        </Button>
                    </Space>
                }
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
