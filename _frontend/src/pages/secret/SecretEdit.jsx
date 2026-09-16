import {useEffect, useState} from 'react'
import {useNavigate, useParams} from 'react-router-dom'
import {Button, Card, Form, Input, message, Select} from 'antd'
import {ArrowLeftOutlined} from '@ant-design/icons'
import request from '../../utils/request'

const {Option} = Select
const {TextArea} = Input

function SecretEdit() {
    const navigate = useNavigate()
    const {id} = useParams()
    const [form] = Form.useForm()
    const [loading, setLoading] = useState(false)
    const isEdit = Boolean(id)

    useEffect(() => {
        if (isEdit) {
            fetchData()
        }
    }, [id])

    const fetchData = async () => {
        try {
            // 通过 search 端点按 id 查找
            const res = await request.get(`/secret/list`)
            if (res.success) {
                const secret = res.data.find(item => String(item.id) === String(id))
                if (secret) {
                    form.setFieldsValue({
                        ...secret,
                        encryptedValue: '',
                    })
                }
            }
        } catch (error) {
            message.error('获取密钥详情失败')
        }
    }

    const onFinish = async (values) => {
        setLoading(true)
        try {
            const data = {
                ...values,
                secretKey: values.secretKey || `secret_${Date.now()}`,
            }

            let res
            if (isEdit) {
                res = await request.put('/secret', data)
            } else {
                res = await request.post('/secret', data)
            }

            if (res.success) {
                message.success(isEdit ? '更新成功' : '创建成功')
                navigate('/secret')
            } else {
                message.error(res.message || '操作失败')
            }
        } catch (error) {
            message.error('操作失败')
        } finally {
            setLoading(false)
        }
    }

    return (
        <div>
            <Button
                icon={<ArrowLeftOutlined/>}
                onClick={() => navigate('/secret')}
                style={{marginBottom: 16}}
            >
                返回列表
            </Button>
            <Card title={isEdit ? '编辑密钥' : '新增密钥'}>
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
                        <Button onClick={() => navigate('/secret')} style={{marginLeft: 8}}>
                            取消
                        </Button>
                    </Form.Item>
                </Form>
            </Card>
        </div>
    )
}

export default SecretEdit