import {useEffect, useState} from 'react'
import {useNavigate, useParams} from 'react-router-dom'
import {Alert, Button, Card, Descriptions, Input, message, Modal, Space, Spin, Table, Tabs, Tag} from 'antd'
import {
    ArrowLeftOutlined,
    CopyOutlined,
    EyeInvisibleOutlined,
    EyeOutlined,
    HistoryOutlined,
    RollbackOutlined
} from '@ant-design/icons'
import request from '../../utils/request'

const {TextArea} = Input

function SecretDetail() {
    const navigate = useNavigate()
    const {id} = useParams()
    const [secret, setSecret] = useState(null)
    const [history, setHistory] = useState([])
    const [tags, setTags] = useState([])
    const [plain, setPlain] = useState('')
    const [showPlain, setShowPlain] = useState(false)
    const [loading, setLoading] = useState(true)
    const [plainLoading, setPlainLoading] = useState(false)

    useEffect(() => {
        if (id) {
            fetchSecret()
            fetchHistory()
            fetchTags()
        }
    }, [id])

    const fetchSecret = async () => {
        setLoading(true)
        try {
            const res = await request.get('/secret/list')
            if (res.success) {
                const found = (res.data || []).find(s => String(s.id) === String(id))
                setSecret(found || null)
                if (found) {
                    fetchTags(found)
                }
            }
        } catch (e) {
            message.error('获取密钥详情失败')
        } finally {
            setLoading(false)
        }
    }

    const fetchHistory = async () => {
        // 通过 id 反查 secretKey/group/namespace 不可行,改用搜索
        const list = await request.get('/secret/list')
        if (list.success) {
            const found = (list.data || []).find(s => String(s.id) === String(id))
            if (found) {
                const res = await request.get(
                    `/secret/history/list?secretKey=${encodeURIComponent(found.secretKey)}&group=${encodeURIComponent(found.group || 'DEFAULT_GROUP')}&namespace=${encodeURIComponent(found.namespace || '')}`
                )
                if (res.success) setHistory(res.data || [])
            }
        }
    }

    const fetchTags = async (s) => {
        const target = s || secret
        if (!target) return
        const res = await request.get(
            `/tag/by-secret?secretKey=${encodeURIComponent(target.secretKey)}&group=${encodeURIComponent(target.group || 'DEFAULT_GROUP')}&namespace=${encodeURIComponent(target.namespace || '')}`
        )
        if (res.success) setTags(res.data || [])
    }

    const fetchPlain = async () => {
        if (!secret) return
        setPlainLoading(true)
        try {
            const res = await request.get(
                `/secret/plain?secretKey=${encodeURIComponent(secret.secretKey)}&group=${encodeURIComponent(secret.group || 'DEFAULT_GROUP')}&namespace=${encodeURIComponent(secret.namespace || '')}`
            )
            if (res.success) {
                setPlain(res.data)
                setShowPlain(true)
            } else {
                message.error(res.message || '解密失败')
            }
        } catch (e) {
            message.error('解密请求失败')
        } finally {
            setPlainLoading(false)
        }
    }

    const handleCopy = (text) => {
        if (navigator.clipboard) {
            navigator.clipboard.writeText(text).then(() => {
                message.success('已复制')
            }).catch(() => message.error('复制失败'))
        } else {
            message.warning('当前浏览器不支持剪贴板 API')
        }
    }

    const handleRotate = async () => {
        if (!secret) return
        Modal.confirm({
            title: '确认轮换密钥?',
            content: `将重新生成 ${secret.secretKey} 的值并自增版本号`,
            onOk: async () => {
                const res = await request.post(
                    `/secret/rotate?secretKey=${encodeURIComponent(secret.secretKey)}&group=${encodeURIComponent(secret.group || 'DEFAULT_GROUP')}&namespace=${encodeURIComponent(secret.namespace || '')}&newValueLength=32`
                )
                if (res.success) {
                    message.success('轮换成功')
                    fetchSecret()
                    fetchHistory()
                } else {
                    message.error(res.message || '轮换失败')
                }
            }
        })
    }

    const handleRollback = (historyId) => {
        Modal.confirm({
            title: '确认回滚到该历史版本?',
            content: '回滚会生成一个新版本,旧值会被快照保留',
            onOk: async () => {
                const res = await request.post(`/secret/rollback?historyId=${historyId}`)
                if (res.success) {
                    message.success('回滚成功')
                    fetchSecret()
                    fetchHistory()
                } else {
                    message.error(res.message || '回滚失败')
                }
            }
        })
    }

    const handleGenerateDynamic = () => {
        let ttl = 3600
        Modal.confirm({
            title: '生成动态密钥(带 TTL)',
            content: (
                <div>
                    <p>关联密钥: {secret?.secretKey}</p>
                    <Input defaultValue="3600" addonAfter="秒" id="dyn-ttl"/>
                </div>
            ),
            onOk: async () => {
                const input = document.getElementById('dyn-ttl')
                ttl = parseInt(input?.value || '3600')
                const res = await request.post(
                    `/secret/dynamic/generate?secretKey=${encodeURIComponent(secret.secretKey)}&group=${encodeURIComponent(secret.group || 'DEFAULT_GROUP')}&namespace=${encodeURIComponent(secret.namespace || '')}&ttlSeconds=${ttl}`
                )
                if (res.success) {
                    Modal.info({
                        title: '动态密钥生成成功',
                        content: (
                            <div>
                                <p>dynKey (UUID): </p>
                                <Input.TextArea rows={3} value={res.data} readOnly/>
                                <p style={{marginTop: 8, color: '#999', fontSize: 12}}>
                                    TTL: {ttl} 秒,过期自动失效
                                </p>
                            </div>
                        )
                    })
                } else {
                    message.error(res.message || '生成失败')
                }
            }
        })
    }

    if (loading) return <Spin size="large" style={{display: 'block', margin: 80}}/>
    if (!secret) {
        return (
            <div>
                <Button icon={<ArrowLeftOutlined/>} onClick={() => navigate('/secret')}>返回</Button>
                <Alert type="error" message="密钥不存在" style={{marginTop: 16}}/>
            </div>
        )
    }

    return (
        <div>
            <Space style={{marginBottom: 16}}>
                <Button icon={<ArrowLeftOutlined/>} onClick={() => navigate('/secret')}>返回列表</Button>
                <Button icon={<HistoryOutlined/>} onClick={handleRotate} type="primary">立即轮换</Button>
                <Button onClick={handleGenerateDynamic}>生成动态密钥</Button>
            </Space>

            <Card title={`密钥详情: ${secret.secretName || secret.secretKey}`}>
                <Descriptions column={2} bordered size="small">
                    <Descriptions.Item label="密钥标识">{secret.secretKey}</Descriptions.Item>
                    <Descriptions.Item label="密钥名称">{secret.secretName}</Descriptions.Item>
                    <Descriptions.Item label="分组">{secret.group}</Descriptions.Item>
                    <Descriptions.Item label="命名空间">{secret.namespace}</Descriptions.Item>
                    <Descriptions.Item label="应用">{secret.appName || '-'}</Descriptions.Item>
                    <Descriptions.Item label="类型">
                        <Tag color="blue">{secret.secretType}</Tag>
                    </Descriptions.Item>
                    <Descriptions.Item label="加密算法">
                        <Tag color="purple">{secret.encryptAlgorithm}</Tag>
                    </Descriptions.Item>
                    <Descriptions.Item label="版本">{secret.keyVersion}</Descriptions.Item>
                    <Descriptions.Item label="MD5 指纹">
                        <code>{secret.valueMd5}</code>
                    </Descriptions.Item>
                    <Descriptions.Item label="过期时间">
                        {secret.expireTime ?
                            <Tag color="orange">{new Date(secret.expireTime).toLocaleString()}</Tag> : '-'}
                    </Descriptions.Item>
                    <Descriptions.Item label="创建时间" span={2}>
                        {secret.gmtCreate ? new Date(secret.gmtCreate).toLocaleString() : '-'}
                    </Descriptions.Item>
                    <Descriptions.Item label="密文(Base64)" span={2}>
                        <Input.TextArea
                            rows={3}
                            value={secret.encryptedValue}
                            readOnly
                            addonAfter={
                                <CopyOutlined onClick={() => handleCopy(secret.encryptedValue)}/>
                            }
                        />
                    </Descriptions.Item>
                    <Descriptions.Item label="明文" span={2}>
                        <Space.Compact style={{width: '100%'}}>
                            <Input
                                type={showPlain ? 'text' : 'password'}
                                value={plain}
                                readOnly
                                placeholder="点击右侧按钮获取明文(将记录访问日志)"
                            />
                            <Button
                                icon={showPlain ? <EyeInvisibleOutlined/> : <EyeOutlined/>}
                                onClick={() => showPlain ? setShowPlain(false) : fetchPlain()}
                                loading={plainLoading}
                            >
                                {showPlain ? '隐藏' : '查看'}
                            </Button>
                            {showPlain && (
                                <Button icon={<CopyOutlined/>} onClick={() => handleCopy(plain)}>复制</Button>
                            )}
                        </Space.Compact>
                    </Descriptions.Item>
                    <Descriptions.Item label="描述" span={2}>
                        {secret.description || '-'}
                    </Descriptions.Item>
                </Descriptions>
            </Card>

            <Card style={{marginTop: 16}}>
                <Tabs
                    items={[
                        {
                            key: 'history',
                            label: `历史版本 (${history.length})`,
                            children: (
                                <Table
                                    size="small"
                                    rowKey="id"
                                    dataSource={history}
                                    pagination={{pageSize: 10}}
                                    columns={[
                                        {title: 'ID', dataIndex: 'id', width: 60},
                                        {title: '版本', dataIndex: 'keyVersion', width: 80},
                                        {
                                            title: '操作类型', dataIndex: 'opType', width: 100,
                                            render: (t) => <Tag>{t}</Tag>
                                        },
                                        {title: 'MD5 指纹', dataIndex: 'valueMd5', ellipsis: true},
                                        {
                                            title: '创建时间', dataIndex: 'gmtCreate', width: 180,
                                            render: (t) => t ? new Date(t).toLocaleString() : '-'
                                        },
                                        {
                                            title: '操作', width: 120, fixed: 'right',
                                            render: (_, r) => (
                                                <Button
                                                    type="link"
                                                    icon={<RollbackOutlined/>}
                                                    onClick={() => handleRollback(r.id)}
                                                    disabled={r.opType === 'ROLLBACK'}
                                                >
                                                    回滚
                                                </Button>
                                            )
                                        },
                                    ]}
                                />
                            )
                        },
                        {
                            key: 'tags',
                            label: `标签 (${tags.length})`,
                            children: (
                                <div>
                                    {tags.length === 0 ?
                                        <Alert message="暂无标签,可通过 /api/tag 添加" type="info"/> :
                                        <Space wrap>
                                            {tags.map(t => (
                                                <Tag key={t.id} color="geekblue">
                                                    {t.tagKey}={t.tagValue}
                                                </Tag>
                                            ))}
                                        </Space>
                                    }
                                </div>
                            )
                        },
                    ]}
                />
            </Card>
        </div>
    )
}

export default SecretDetail
