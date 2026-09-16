import {useEffect, useState} from 'react'
import {Alert, Card, Col, Empty, Row, Spin, Statistic, Table, Tag} from 'antd'
import {CheckCircleOutlined, KeyOutlined, RiseOutlined, ThunderboltOutlined, WarningOutlined,} from '@ant-design/icons'
import request from '../../utils/request'

function Dashboard() {
    const [overview, setOverview] = useState(null)
    const [daily, setDaily] = useState([])
    const [topSecrets, setTopSecrets] = useState([])
    const [expiring, setExpiring] = useState([])
    const [loading, setLoading] = useState(true)

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
        } catch (e) {
            console.error(e)
        } finally {
            setLoading(false)
        }
    }

    if (loading) return <Spin size="large" style={{display: 'block', margin: 80}}/>

    return (
        <div>
            <h2 style={{marginBottom: 24}}>
                <RiseOutlined/> z-mist 密钥管理总览
            </h2>

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
                            <Empty description="暂无访问数据"/> :
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
                <Card title="即将过期密钥(7 天内)" style={{marginTop: 16}}>
                    <Table
                        size="small"
                        pagination={{pageSize: 5}}
                        rowKey="id"
                        dataSource={expiring}
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
                    />
                </Card>
            )}
        </div>
    )
}

export default Dashboard
