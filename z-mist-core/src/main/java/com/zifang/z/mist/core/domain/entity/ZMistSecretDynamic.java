package com.zifang.z.mist.core.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zifang.z.meta.api.annotation.BizField;
import com.zifang.z.meta.api.annotation.BizModel;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 动态密钥表(FEATURE026).
 * <p>
 * 对应 Vault 的 Dynamic Secret: 生成后带 TTL 自动过期,支持主动 revoke。
 * dyn_key 是 UUID,客户端拿到后必须通过该字段回查密文。
 *
 * @author zifang
 * @see ZMistSecretDynamicMapper
 * @since 1.0.0
 */
@BizModel(name = "动态密钥表", module = "z-mist", description = "动态密钥表(FEATURE026). 对应 Vault 的 Dynamic Secret: 生成后带 TTL 自动过期,支持主动 revoke。 dyn_key 是 UUID,客户端拿到后必须通过该字段回查密文。")
@TableName("z_mist_secret_dynamic")
public class ZMistSecretDynamic implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    @BizField(name = "主键ID", type = BizField.FieldType.INTEGER)
    private Long id;

    /**
     * 动态密钥标识(UUID)
     */
    @BizField(name = "动态密钥标识(UUID)", type = BizField.FieldType.STRING)
    private String dynKey;

    /**
     * 密钥Key
     */
    @BizField(name = "密钥Key", type = BizField.FieldType.STRING)
    private String secretKey;

    /**
     * 分组
     */
    @TableField("`group`")
    @BizField(name = "分组", type = BizField.FieldType.STRING)
    private String group;

    /**
     * 命名空间
     */
    @BizField(name = "命名空间", type = BizField.FieldType.STRING)
    private String namespace;

    /**
     * 加密后的值
     */
    @BizField(name = "加密后的值", type = BizField.FieldType.STRING)
    private String encryptedValue;

    /**
     * 加密算法
     */
    @BizField(name = "加密算法", type = BizField.FieldType.STRING)
    private String algorithm;

    /**
     * 租约ID
     */
    @BizField(name = "租约ID", type = BizField.FieldType.STRING)
    private String leaseId;

    /**
     * TTL秒数
     */
    @BizField(name = "TTL秒数", type = BizField.FieldType.INTEGER)
    private Integer ttlSeconds;

    /**
     * 过期时间
     */
    @BizField(name = "过期时间", type = BizField.FieldType.DATETIME)
    private LocalDateTime expireTime;

    /**
     * 是否已吊销(0:否,1:是)
     */
    @BizField(name = "是否撤销", type = BizField.FieldType.INTEGER)
    private Integer revoked;

    /**
     * 吊销时间
     */
    @BizField(name = "吊销时间", type = BizField.FieldType.DATETIME)
    private LocalDateTime revokeTime;

    /**
     * 创建者
     */
    @BizField(name = "创建者", type = BizField.FieldType.STRING)
    private String creator;

    /**
     * 创建时间
     */
    @BizField(name = "创建时间", type = BizField.FieldType.DATETIME)
    private LocalDateTime gmtCreate;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDynKey() {
        return dynKey;
    }

    public void setDynKey(String dynKey) {
        this.dynKey = dynKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getEncryptedValue() {
        return encryptedValue;
    }

    public void setEncryptedValue(String encryptedValue) {
        this.encryptedValue = encryptedValue;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public String getLeaseId() {
        return leaseId;
    }

    public void setLeaseId(String leaseId) {
        this.leaseId = leaseId;
    }

    public Integer getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(Integer ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    public LocalDateTime getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(LocalDateTime expireTime) {
        this.expireTime = expireTime;
    }

    public Integer getRevoked() {
        return revoked;
    }

    public void setRevoked(Integer revoked) {
        this.revoked = revoked;
    }

    public LocalDateTime getRevokeTime() {
        return revokeTime;
    }

    public void setRevokeTime(LocalDateTime revokeTime) {
        this.revokeTime = revokeTime;
    }

    public String getCreator() {
        return creator;
    }

    public void setCreator(String creator) {
        this.creator = creator;
    }

    public LocalDateTime getGmtCreate() {
        return gmtCreate;
    }

    public void setGmtCreate(LocalDateTime gmtCreate) {
        this.gmtCreate = gmtCreate;
    }
}



