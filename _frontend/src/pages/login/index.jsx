import {useState} from 'react'
import {useNavigate} from 'react-router-dom'
import {Button, Card, Form, Input, message} from 'antd'
import {LockOutlined, UserOutlined} from '@ant-design/icons'
import request from '../../utils/request'

function Login() {
    const navigate = useNavigate()
    const [loading, setLoading] = useState(false)

    const onFinish = async (values) => {
        setLoading(true)
        try {
            const res = await request.post('/auth/login', values)
            if (res.success && res.data && res.data.token) {
                localStorage.setItem('token', res.data.token)
                localStorage.setItem('username', res.data.username || values.username)
                message.success(`欢迎 ${res.data.username || values.username}`)
                navigate('/dashboard')
            } else {
                message.error(res.message || '登录失败')
            }
        } catch (error) {
            message.error(error?.message || '登录失败,请检查网络')
        } finally {
            setLoading(false)
        }
    }

    return (
        <div style={{
            height: '100vh',
            display: 'flex',
            justifyContent: 'center',
            alignItems: 'center',
            background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)'
        }}>
            <Card
                title={<div style={{textAlign: 'center', fontSize: 24}}>z-mist 密钥管理平台</div>}
                style={{width: 400}}
            >
                <Form
                    name="login"
                    onFinish={onFinish}
                    autoComplete="off"
                >
                    <Form.Item
                        name="username"
                        rules={[{required: true, message: '请输入用户名'}]}
                    >
                        <Input
                            prefix={<UserOutlined/>}
                            placeholder="用户名: admin"
                            size="large"
                        />
                    </Form.Item>

                    <Form.Item
                        name="password"
                        rules={[{required: true, message: '请输入密码'}]}
                    >
                        <Input.Password
                            prefix={<LockOutlined/>}
                            placeholder="密码: admin"
                            size="large"
                        />
                    </Form.Item>

                    <Form.Item>
                        <Button
                            type="primary"
                            htmlType="submit"
                            loading={loading}
                            size="large"
                            block
                        >
                            登录
                        </Button>
                    </Form.Item>
                </Form>
                <div style={{textAlign: 'center', color: '#999', fontSize: 12, marginTop: 16}}>
                    提示: z-mist 本地账户,密码与 z-ctc 同步时可由 SSO 接管
                </div>
            </Card>
        </div>
    )
}

export default Login
