import {useEffect, useState} from 'react'
import {useNavigate, useParams} from 'react-router-dom'
import {Button, Card, Form, Input, message, Select} from 'antd'
import {ArrowLeftOutlined} from '@ant-design/icons'
import {ErrorState} from '@yuku123/z-frontend-common/ui'
import request from '../../services/request'

const {Option} = Select
const {TextArea} = Input

/**
 * 取第一个有值的字段名.
 *
 * z-mist 的 JSON 字段名取决于部署在哪: 平台装配(z-opc)时全局 Jackson 是 snake_case,
 * 独立部署(z-mist-admin)时是 camelCase。读的时候两种都认, 免得表单在一种部署下空着。
 *
 * @param {Object} obj 响应对象
 * @param {...string} keys 候选字段名(按优先级)
 * @returns {*} 第一个有值的字段值, 都没有则 undefined
 */
function pick(obj, ...keys) {
    if (!obj) return undefined
    for (const key of keys) {
        if (obj[key] !== undefined && obj[key] !== null) return obj[key]
    }
    return undefined
}

function SecretEdit() {
    const navigate = useNavigate()
    const {id} = useParams()
    const [form] = Form.useForm()
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState(null)
    const isEdit = Boolean(id)

    useEffect(() => {
        if (isEdit) {
            fetchData()
        }
    }, [id])

    const fetchData = async () => {
        try {
            // 通过 list 端点按 id 查找(list 的 namespace 缺省 = 不过滤, 各命名空间都能查到)
            const res = await request.get(`/secret/list`)
            if (res.success) {
                const secret = res.data.find(item => String(item.id) === String(id))
                if (secret) {
                    form.setFieldsValue({
                        secretName: pick(secret, 'secret_name', 'secretName') || '',
                        secretKey: pick(secret, 'secret_key', 'secretKey') || '',
                        group: pick(secret, 'group') || '',
                        appName: pick(secret, 'app_name', 'appName') || '',
                        secretType: pick(secret, 'secret_type', 'secretType') || 'text',
                        encryptAlgorithm: pick(secret, 'encrypt_algorithm', 'encryptAlgorithm') || 'AES',
                        description: pick(secret, 'description') || '',
                        // 密文不回填到表单: 免得把密文摊在 DOM 里, 也避免把旧密文原样写回去
                        encryptedValue: '',
                    })
                }
            }
            setError(null)
        } catch (error_) {
            // 保留原始 error 对象：ErrorState 会优先取后端 response.data.message，
            // 压成字符串就只剩 axios 的 "Request failed with status code 500"。
            setError(error_)
        }
    }

    const onFinish = async (values) => {
        setLoading(true)
        try {
            // L3 写接口契约: 请求体字段名是 snake_case。
            // (后端对 camelCase 同样兼容, 但两种写法之外的字段会被显式拒绝 —— 不再静默落一条全 NULL 的行)
            const numericId = Number(id)
            const data = {
                id: isEdit && Number.isFinite(numericId) ? numericId : undefined,
                secret_key: values.secretKey || `secret_${Date.now()}`,
                secret_name: values.secretName,
                group: values.group,
                app_name: values.appName,
                secret_type: values.secretType,
                encrypted_value: values.encryptedValue,
                encrypt_algorithm: values.encryptAlgorithm,
                description: values.description,
            }
            // 去掉没填的键(undefined/null): 空串是"显式清空", 不该被当成"没传"
            Object.keys(data).forEach(key => {
                if (data[key] === undefined || data[key] === null) {
                    delete data[key]
                }
            })

            let res
            if (isEdit) {
                res = await request.put('/secret', data)
            } else {
                res = await request.post('/secret', data)
            }

            if (res.success) {
                message.success(isEdit ? '更新成功' : '创建成功')
                navigate('/z-mist/secret')
            } else {
                message.error(res.message || '操作失败')
            }
        } catch (error) {
            message.error(error?.response?.data?.message || '操作失败')
        } finally {
            setLoading(false)
        }
    }

    return (
        <div>
            <Button
                icon={<ArrowLeftOutlined/>}
                onClick={() => navigate('/z-mist/secret')}
                style={{marginBottom: 16}}
            >
                返回列表
            </Button>
            <Card title={isEdit ? '编辑密钥' : '新增密钥'}>
                {error && (
                    <div style={{marginBottom: 16}}>
                        <ErrorState error={error} title="获取密钥详情失败" onRetry={() => fetchData()}/>
                    </div>
                )}
                <Form
                    form={form}
                    layout="vertical"
                    onFinish={onFinish}
                >
                    <Form.Item
                        name="secretName"
                        label="密钥名称"
                        rules={[{required: true, message: '请输入密钥名称'}]}
                    >
                        <Input placeholder="请输入密钥名称"/>
                    </Form.Item>

                    <Form.Item
                        name="secretKey"
                        label="密钥标识"
                        rules={[{required: true, message: '请输入密钥标识'}]}
                    >
                        <Input placeholder="请输入密钥标识，如: database/password" disabled={isEdit}/>
                    </Form.Item>

                    <Form.Item
                        name="group"
                        label="分组"
                        initialValue="DEFAULT_GROUP"
                    >
                        <Input placeholder="请输入分组"/>
                    </Form.Item>

                    <Form.Item
                        name="appName"
                        label="应用名"
                    >
                        <Input placeholder="请输入应用名"/>
                    </Form.Item>

                    <Form.Item
                        name="secretType"
                        label="密钥类型"
                        initialValue="text"
                    >
                        <Select placeholder="请选择密钥类型">
                            <Option value="text">文本</Option>
                            <Option value="password">密码</Option>
                            <Option value="cert">证书</Option>
                            <Option value="key">密钥</Option>
                            <Option value="api_key">API Key</Option>
                        </Select>
                    </Form.Item>

                    <Form.Item
                        name="encryptedValue"
                        label="密钥值"
                        rules={[{required: true, message: '请输入密钥值'}]}
                    >
                        <TextArea rows={4} placeholder="请输入密钥值"/>
                    </Form.Item>

                    <Form.Item
                        name="encryptAlgorithm"
                        label="加密算法"
                        initialValue="AES"
                    >
                        <Select placeholder="请选择加密算法">
                            <Option value="AES">AES</Option>
                            <Option value="RSA">RSA</Option>
                        </Select>
                    </Form.Item>

                    <Form.Item
                        name="description"
                        label="描述"
                    >
                        <TextArea rows={2} placeholder="请输入描述"/>
                    </Form.Item>

                    <Form.Item>
                        <Button type="primary" htmlType="submit" loading={loading}>
                            {isEdit ? '更新' : '创建'}
                        </Button>
                        <Button onClick={() => navigate('/z-mist/secret')} style={{marginLeft: 8}}>
                            取消
                        </Button>
                    </Form.Item>
                </Form>
            </Card>
        </div>
    )
}

export default SecretEdit