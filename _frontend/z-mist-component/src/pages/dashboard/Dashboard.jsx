import {useEffect, useState} from 'react'
import {Alert, Card, Col, Row, Statistic, Table, Tag} from 'antd'
import {CheckCircleOutlined, KeyOutlined, RiseOutlined, ThunderboltOutlined, WarningOutlined,} from '@ant-design/icons'
import {EmptyState, ErrorState, ListTable, LoadingState} from '@yuku123/z-frontend-common/ui'
import request from '../../services/request'

function Dashboard() {
    const [overview, setOverview] = useState(null)
    const [daily, setDaily] = useState([])
    const [topSecrets, setTopSecrets] = useState([])
    const [expiring, setExpiring] = useState([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(null)

    useEffect(() => {
        fetchAll()
    }, [])

    const fetchAll = async () => {
        setLoading(true)
        try {
            const [ov, dy, ts, ex] = await Promise.all([
                request.get('/stats/overview'),
                request.get('/stats/daily?days=7'),
                request.get('/stats/top-secrets?limit=10'),
                request.get('/stats/expiring'),
            ])
            if (ov.success) setOverview(ov.data)
            if (dy.success) setDaily(dy.data)
            if (ts.success) setTopSecrets(ts.data)
            if (ex.success) setExpiring(ex.data)
            setError(null)
        } catch (e) {
            // 保留原始 error 对象：ErrorState 会优先取后端 response.data.message，
            // 压成字符串就只剩 axios 的 "Request failed with status code 500"。
            setError(e)
        } finally {
            setLoading(false)
        }
    }

    // 加载态走共享骨架屏（cards 变体对应下面 4 张统计卡），不再用裸 Spin
    if (loading) return <LoadingState variant="cards" rows={4}/>

    return (
        <div>
            <h2 style={{marginBottom: 24}}>
                <RiseOutlined/> z-mist 密钥管理总览
            </h2>

            {error && (
                <div style={{marginBottom: 16}}>
                    <ErrorState error={error} title="加载密钥总览失败" onRetry={() => fetchAll()}/>
                </div>
            )}

            <Row gutter={16}>
                <Col span={6}>
                    <Card>
                        <Statistic
                            title="密钥总数"
                            value={overview?.totalSecrets || 0}
                            prefix={<KeyOutlined style={{color: '#1890ff'}}/>}
                        />
                    </Card>
                </Col>
                <Col span={6}>
                    <Card>
                        <Statistic
                            title="今日操作次数"
                            value={overview?.todayOps || 0}
                            prefix={<ThunderboltOutlined style={{color: '#52c41a'}}/>}
                        />
                    </Card>
                </Col>
                <Col span={6}>
                    <Card>
                        <Statistic
                            title="今日失败次数"
                            value={overview?.todayFailed || 0}
                            valueStyle={{color: (overview?.todayFailed || 0) > 0 ? '#cf1322' : '#3f8600'}}
                            prefix={<WarningOutlined/>}
                        />
                    </Card>
                </Col>
                <Col span={6}>
                    <Card>
                        <Statistic
                            title="成功率"
                            value={overview?.successRate || 100}
                            precision={2}
                            suffix="%"
                            valueStyle={{color: '#3f8600'}}
                            prefix={<CheckCircleOutlined/>}
                        />
                    </Card>
                </Col>
            </Row>

            {expiring && expiring.length > 0 && (
                <Alert
                    style={{marginTop: 16}}
                    message={`警告: ${expiring.length} 个密钥将在 7 天内过期`}
                    description="请及时轮换或延期,避免密钥失效影响业务"
                    type="warning"
                    showIcon
                />
            )}

            <Row gutter={16} style={{marginTop: 16}}>
                <Col span={12}>
                    <Card title="近 7 天访问趋势">
                        <Table
                            size="small"
                            pagination={false}
                            rowKey="date"
                            dataSource={daily}
                            locale={{emptyText: <EmptyState title="暂无访问趋势" description="近 7 天还没有密钥访问记录" size="sm"/>}}
                            columns={[
                                {title: '日期', dataIndex: 'date', width: 110},
                                {title: 'GET 次数', dataIndex: 'getCount', width: 100},
                                {title: '加密', dataIndex: 'encryptCount', width: 80},
                                {
                                    title: '失败', dataIndex: 'failedCount', width: 80,
                                    render: (v) => v > 0 ?
                                        <Tag color="red">{v}</Tag> : <Tag color="green">0</Tag>
                                },
                                {
                                    title: '趋势',
                                    render: (_, r) => {
                                        const total = (r.getCount || 0) + (r.encryptCount || 0)
                                        const barWidth = Math.min(100, total * 2)
                                        return (
                                            <div style={{background: '#f0f0f0', height: 8, borderRadius: 4}}>
                                                <div style={{
                                                    background: '#1890ff',
                                                    height: 8,
                                                    borderRadius: 4,
                                                    width: `${barWidth}%`,
                                                    transition: 'width 0.3s',
                                                }}/>
                                            </div>
                                        )
                                    }
                                },
                            ]}
                        />
                    </Card>
                </Col>
                <Col span={12}>
                    <Card title="TOP 10 访问密钥(7 天)">
                        {topSecrets.length === 0 ?
                            <EmptyState title="暂无访问数据" description="近 7 天还没有密钥被访问过" size="sm"/> :
                            <Table
                                size="small"
                                pagination={false}
                                rowKey="secretKey"
                                dataSource={topSecrets}
                                columns={[
                                    {title: '#', render: (_, __, idx) => idx + 1, width: 40},
                                    {
                                        title: '密钥标识', dataIndex: 'secretKey',
                                        ellipsis: true
                                    },
                                    {
                                        title: '访问次数', dataIndex: 'accessCount', width: 100,
                                        render: (v) => <Tag color="blue">{v}</Tag>
                                    },
                                ]}
                            />
                        }
                    </Card>
                </Col>
            </Row>

            {expiring && expiring.length > 0 && (
                /* 即将过期密钥表走基座 ListTable（lead 008 §2 规则 2/3/4）：
                   分页用基座默认（pageSize 10 + 每页条数切换 + 总条数），
                   端侧过滤按密钥名称/标识/分组/命名空间，空态由基座 EmptyState 承接。 */
                <ListTable
                    cardTitle="即将过期密钥(7 天内)"
                    rows={expiring}
                    columns={[
                        {title: '密钥标识', dataIndex: 'secretKey'},
                        {title: '名称', dataIndex: 'secretName'},
                        {title: '分组', dataIndex: 'group', width: 120},
                        {title: '命名空间', dataIndex: 'namespace', width: 120},
                        {
                            title: '过期时间',
                            dataIndex: 'expireTime',
                            width: 180,
                            render: (t) => t ? <Tag color="orange">{new Date(t).toLocaleString()}</Tag> : '-'
                        },
                    ]}
                    rowKey="id"
                    searchKeys="secretName,secretKey,group,namespace"
                    searchPlaceholder="按密钥名称 / 标识 / 分组过滤"
                    emptyTitle="暂无即将过期的密钥"
                    emptyDescription="所有密钥的过期时间都在 7 天以外"
                    cardProps={{style: {marginTop: 16}}}
                />
            )}
        </div>
    )
}

export default Dashboard
