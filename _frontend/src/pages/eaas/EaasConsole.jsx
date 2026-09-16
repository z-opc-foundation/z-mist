import {useState} from 'react'
import {Alert, Button, Card, Col, Input, message, Row, Select, Space} from 'antd'
import {LockOutlined, SwapOutlined, UnlockOutlined} from '@ant-design/icons'
import request from '../../utils/request'

const {TextArea} = Input
const {Option} = Select

function EaasConsole() {
    const [algorithm, setAlgorithm] = useState('AES')
    const [encryptInput, setEncryptInput] = useState('')
    const [encryptOutput, setEncryptOutput] = useState('')
    const [decryptInput, setDecryptInput] = useState('')
    const [decryptOutput, setDecryptOutput] = useState('')
    const [loading, setLoading] = useState(false)

    const handleEncrypt = async () => {
        if (!encryptInput) {
            message.warning('请输入明文')
            return
        }
        setLoading(true)
        try {
            const res = await request.post('/eaas/encrypt', {
                plainText: encryptInput,
                algorithm,
            })
            if (res.success) {
                setEncryptOutput(res.data)
                message.success('加密成功')
            } else {
                message.error(res.message || '加密失败')
            }
        } catch (e) {
            message.error('加密请求失败')
        } finally {
            setLoading(false)
        }
    }

    const handleDecrypt = async () => {
        if (!decryptInput) {
            message.warning('请输入密文')
            return
        }
        setLoading(true)
        try {
            const res = await request.post('/eaas/decrypt', {
                cipherText: decryptInput,
                algorithm,
            })
            if (res.success) {
                setDecryptOutput(res.data)
                message.success('解密成功')
            } else {
                message.error(res.message || '解密失败')
            }
        } catch (e) {
            message.error('解密请求失败')
        } finally {
            setLoading(false)
        }
    }

    const handleCopy = (text, label) => {
        if (navigator.clipboard) {
            navigator.clipboard.writeText(text).then(() => message.success(`${label}已复制`))
        }
    }

    const handleSwap = () => {
        // 把加密结果搬到解密输入,方便连测
        setDecryptInput(encryptOutput)
        setEncryptInput('')
        setEncryptOutput('')
        message.info('已把加密结果搬到解密输入框')
    }

    return (
        <div>
            <Alert
                type="info"
                showIcon
                message="加密即服务(EaaS) — 不存储数据"
                description="对任意明文按指定算法加密,返回 Base64 密文。可用于业务方把敏感数据加密后存到 MySQL/Redis,后续解密反查。密钥值不会落库。"
                style={{marginBottom: 16}}
            />

            <Row gutter={16}>
                <Col span={2}>
                    <Card title="算法">
                        <Select value={algorithm} onChange={setAlgorithm} style={{width: '100%'}}>
                            <Option value="AES">AES</Option>
                            <Option value="RSA">RSA</Option>
                        </Select>
                        <div style={{marginTop: 16, fontSize: 12, color: '#999'}}>
                            AES: 对称加密,速度快,适合大数据<br/>
                            RSA: 非对称加密,密文较长,适合小数据/密钥交换
                        </div>
                    </Card>
                </Col>

                <Col span={11}>
                    <Card
                        title={<><LockOutlined/> 加密</>}
                        extra={
                            <Space>
                                <Button onClick={handleSwap} icon={<SwapOutlined/>} size="small">
                                    密文→解密
                                </Button>
                                <Button type="primary" onClick={handleEncrypt} loading={loading}>
                                    加密
                                </Button>
                            </Space>
                        }
                    >
                        <div style={{marginBottom: 8}}>明文:</div>
                        <TextArea
                            rows={4}
                            value={encryptInput}
                            onChange={(e) => setEncryptInput(e.target.value)}
                            placeholder="输入任意明文,例如: my-secret-password-123"
                        />
                        <div style={{marginTop: 12, marginBottom: 8}}>密文 (Base64):</div>
                        <TextArea
                            rows={4}
                            value={encryptOutput}
                            readOnly
                            placeholder="加密结果会显示在这里"
                        />
                        {encryptOutput && (
                            <Button
                                style={{marginTop: 8}}
                                size="small"
                                onClick={() => handleCopy(encryptOutput, '密文')}
                            >
                                复制密文
                            </Button>
                        )}
                    </Card>
                </Col>

                <Col span={11}>
                    <Card
                        title={<><UnlockOutlined/> 解密</>}
                        extra={
                            <Button type="primary" onClick={handleDecrypt} loading={loading}>
                                解密
                            </Button>
                        }
                    >
                        <div style={{marginBottom: 8}}>密文 (Base64):</div>
                        <TextArea
                            rows={4}
                            value={decryptInput}
                            onChange={(e) => setDecryptInput(e.target.value)}
                            placeholder="粘贴 Base64 密文"
                        />
                        <div style={{marginTop: 12, marginBottom: 8}}>明文:</div>
                        <TextArea
                            rows={4}
                            value={decryptOutput}
                            readOnly
                            placeholder="解密结果会显示在这里"
                        />
                        {decryptOutput && (
                            <Button
                                style={{marginTop: 8}}
                                size="small"
                                onClick={() => handleCopy(decryptOutput, '明文')}
                            >
                                复制明文
                            </Button>
                        )}
                    </Card>
                </Col>
            </Row>

            <Card title="信封加密(Envelope Encryption)" style={{marginTop: 16}}>
                <Alert
                    type="warning"
                    showIcon
                    message="信封加密 = 主密钥加密数据密钥 + 数据密钥加密业务数据"
                    description="这是云厂商 KMS 的核心模式: 业务数据用数据密钥 AES-256 加密;数据密钥用主密钥加密后随密文一起保存。优势: 主密钥不离开服务,业务可脱机保存加密数据。"
                />
                <div style={{marginTop: 16, fontSize: 12, color: '#666'}}>
                    调用示例 (使用 curl):<br/>
                    <code style={{display: 'block', background: '#f5f5f5', padding: 8, marginTop: 8, borderRadius: 4}}>
                        curl -X POST 'http://localhost:8888/api/master-key/envelope?plainText=hello-mist'
                    </code>
                    返回结构: <code>{`{wrappedKey, cipherText, algorithm}`}</code>。<br/>
                    <code style={{display: 'block', background: '#f5f5f5', padding: 8, marginTop: 8, borderRadius: 4}}>
                        curl -X POST 'http://localhost:8888/api/master-key/unenvelope?wrappedKey=...&cipherText=...'
                    </code>
                    即解封还原明文。
                </div>
            </Card>
        </div>
    )
}

export default EaasConsole
