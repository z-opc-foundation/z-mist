import {useState} from 'react'
import {Input, Button, Space, message} from 'antd'
import {EyeOutlined, EyeInvisibleOutlined, CopyOutlined} from '@ant-design/icons'

/**
 * 密钥值显示（脱敏 + 显示切换 + 复制）
 * <p>
 * 抽自 z-mist/_frontend/src/pages/secret/SecretList.jsx
 *
 * @param {string} value - 原始密钥值（密文）
 * @param {string} displayValue - 脱敏后的值（默认 ******）
 * @param {boolean} copyable - 是否显示复制按钮
 */
export default function SecretValueDisplay({value, displayValue = '••••••', copyable = true}) {
    const [visible, setVisible] = useState(false)

    const handleCopy = () => {
        if (value) {
            navigator.clipboard.writeText(value)
            message.success('已复制到剪贴板')
        }
    }

    return (
        <Space>
            <Input
                value={visible ? value : displayValue}
                readOnly
                style={{width: 200}}
            />
            <Button
                size="small"
                icon={visible ? <EyeInvisibleOutlined/> : <EyeOutlined/>}
                onClick={() => setVisible(!visible)}
            />
            {copyable && (
                <Button
                    size="small"
                    icon={<CopyOutlined/>}
                    onClick={handleCopy}
                />
            )}
        </Space>
    )
}
