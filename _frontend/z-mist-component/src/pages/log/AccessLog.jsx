import {useEffect, useState} from 'react'
import {Button, Card, Input, message, Select, Table, Tag} from 'antd'
import {ReloadOutlined, SearchOutlined} from '@ant-design/icons'
import {EmptyState, ErrorState, TableToolbar} from '@yuku123/z-frontend-common/ui'
import request from '../../services/request'

const {Option} = Select

function AccessLog() {
    const [data, setData] = useState([])
    const [total, setTotal] = useState(0)
    const [loading, setLoading] = useState(false)
    const [page, setPage] = useState(1)
    const [size, setSize] = useState(20)
    const [error, setError] = useState(null)
    const [filters, setFilters] = useState({
        secretKey: '',
        operator: '',
        opType: '',
        success: undefined,
    })

    useEffect(() => {
        fetchData()
    }, [page, size])

    const fetchData = async () => {
        setLoading(true)
        try {
            const qs = new URLSearchParams({
                current: page, size,
                secretKey: filters.secretKey || '',
                operator: filters.operator || '',
                opType: filters.opType || '',
                success: filters.success === undefined ? '' : (filters.success ? 1 : 0),
            })
            const res = await request.get(`/log/page?${qs}`)
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

    const opColors = {
        GET: 'blue', LIST: 'cyan', PUT: 'green', DELETE: 'red',
        GET_PLAIN: 'purple', SEARCH: 'orange', HISTORY: 'gold',
        ROLLBACK: 'magenta', ROTATE: 'volcano', DYN_GENERATE: 'geekblue',
        DYN_READ: 'lime', DYN_REVOKE: 'red',
    }

    return (
        <div>
            <Card>
                {error && (
                    <div style={{marginBottom: 16}}>
                        <ErrorState error={error} title="查询日志失败" onRetry={() => fetchData()}/>
                    </div>
                )}
                <TableToolbar
                    left={
                        <>
                            <Input placeholder="密钥标识" allowClear
                                   value={filters.secretKey}
                                   onChange={(e) => setFilters({...filters, secretKey: e.target.value})}
                                   style={{width: 180}}/>
                            <Input placeholder="操作者" allowClear
                                   value={filters.operator}
                                   onChange={(e) => setFilters({...filters, operator: e.target.value})}
                                   style={{width: 140}}/>
                            <Select placeholder="操作类型" allowClear
                                    value={filters.opType || undefined}
                                    onChange={(v) => setFilters({...filters, opType: v || ''})}
                                    style={{width: 160}}>
                                {['GET', 'PUT', 'DELETE', 'LIST', 'GET_PLAIN', 'SEARCH', 'HISTORY', 'ROLLBACK', 'ROTATE', 'DYN_GENERATE', 'DYN_READ', 'DYN_REVOKE'].map(op =>
                                    <Option key={op} value={op}>{op}</Option>
                                )}
                            </Select>
                            <Select placeholder="成功/失败" allowClear
                                    value={filters.success}
                                    onChange={(v) => setFilters({...filters, success: v})}
                                    style={{width: 120}}>
                                <Option value={true}>成功</Option>
                                <Option value={false}>失败</Option>
                            </Select>
                        </>
                    }
                    right={
                        <>
                            <Button type="primary" icon={<SearchOutlined/>} onClick={() => {
                                setPage(1);
                                fetchData()
                            }}>查询</Button>
                            <Button icon={<ReloadOutlined/>} onClick={fetchData}>刷新</Button>
                        </>
                    }
                />

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
                        {title: '密钥', dataIndex: 'secretKey', ellipsis: true, width: 180},
                        {
                            title: '操作类型', dataIndex: 'opType', width: 120,
                            render: (t) => <Tag color={opColors[t] || 'default'}>{t}</Tag>
                        },
                        {title: '操作者', dataIndex: 'operator', width: 110},
                        {title: 'IP', dataIndex: 'operatorIp', width: 130},
                        {
                            title: '成功', dataIndex: 'success', width: 80,
                            render: (s) => s ?
                                <Tag color="green">成功</Tag> :
                                <Tag color="red">失败</Tag>
                        },
                        {
                            title: '错误信息', dataIndex: 'errorMessage', ellipsis: true,
                            render: (m) => m ? <span style={{color: '#cf1322'}}>{m}</span> : '-'
                        },
                        {
                            title: '时间', dataIndex: 'gmtCreate', width: 170,
                            render: (t) => t ? new Date(t).toLocaleString() : '-'
                        },
                    ]}
                    locale={{emptyText: <EmptyState title="暂无访问日志" description="放宽筛选条件再查一次"/>}}
                />
            </Card>
        </div>
    )
}

export default AccessLog
