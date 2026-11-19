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
 * 密钥信息表.
 *
 * @author zifang
 * @see ZMistSecretInfoMapper
 * @since 1.0.0
 */
@BizModel(name = "密钥信息表", module = "z-mist", description = "密钥信息表.")
@TableName("z_mist_secret_info")
public class ZMistSecretInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    @BizField(name = "主键ID", type = BizField.FieldType.INTEGER)
    private Long id;

    /**
     * 密钥Key
     */
    @BizField(name = "密钥Key", type = BizField.FieldType.STRING)
    private String secretKey;

    /**
     * 密钥名称
     */
    @BizField(name = "密钥名称", type = BizField.FieldType.STRING)
    private String secretName;

    /**
     * 分组
     */
    @TableField("`group`")
    @BizField(name = "分组", type = BizField.FieldType.STRING)
    private String group;

    /**
     * 应用名
     */
    @BizField(name = "应用名", type = BizField.FieldType.STRING)
    private String appName;

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
     * 值的MD5
     */
    @BizField(name = "值的MD5", type = BizField.FieldType.STRING)
    private String valueMd5;

    /**
     * 加密算法
     */
    @BizField(name = "加密算法", type = BizField.FieldType.STRING)
    private String encryptAlgorithm;

    /**
     * 密钥版本
     */
    @BizField(name = "密钥版本", type = BizField.FieldType.STRING)
    private String keyVersion;

    /**
     * 密钥类型
     */
    @BizField(name = "密钥类型", type = BizField.FieldType.STRING)
    private String secretType;

    /**
     * 描述
     */
    @BizField(name = "描述", type = BizField.FieldType.STRING)
    private String description;

    /**
     * 过期时间
     */
    @BizField(name = "过期时间", type = BizField.FieldType.DATETIME)
    private LocalDateTime expireTime;

    /**
     * 创建时间
     */
    @BizField(name = "创建时间", type = BizField.FieldType.DATETIME)
    private LocalDateTime gmtCreate;

    /**
     * 修改时间
     */
    @BizField(name = "修改时间", type = BizField.FieldType.DATETIME)
    private LocalDateTime gmtModified;

    /**
     * 创建人工号
     */
    @BizField(name = "创建人工号", type = BizField.FieldType.STRING)
    private String creatorStaffNo;

    /**
     * 创建人昵称
     */
    @BizField(name = "创建人昵称", type = BizField.FieldType.STRING)
    private String creatorStaffNickNm;

    /**
     * 源IP地址
     */
    @BizField(name = "源IP地址", type = BizField.FieldType.STRING)
    private String sourceIp;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getSecretName() {
        return secretName;
    }

    public void setSecretName(String secretName) {
        this.secretName = secretName;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
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

    public String getValueMd5() {
        return valueMd5;
    }

    public void setValueMd5(String valueMd5) {
        this.valueMd5 = valueMd5;
    }

    public String getEncryptAlgorithm() {
        return encryptAlgorithm;
    }

    public void setEncryptAlgorithm(String encryptAlgorithm) {
        this.encryptAlgorithm = encryptAlgorithm;
    }

    public String getKeyVersion() {
        return keyVersion;
    }

    public void setKeyVersion(String keyVersion) {
        this.keyVersion = keyVersion;
    }

    public String getSecretType() {
        return secretType;
    }

    public void setSecretType(String secretType) {
        this.secretType = secretType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(LocalDateTime expireTime) {
        this.expireTime = expireTime;
    }

    public LocalDateTime getGmtCreate() {
        return gmtCreate;
    }

    public void setGmtCreate(LocalDateTime gmtCreate) {
        this.gmtCreate = gmtCreate;
    }

    public LocalDateTime getGmtModified() {
        return gmtModified;
    }

    public void setGmtModified(LocalDateTime gmtModified) {
        this.gmtModified = gmtModified;
    }

    public String getCreatorStaffNo() {
        return creatorStaffNo;
    }

    public void setCreatorStaffNo(String creatorStaffNo) {
        this.creatorStaffNo = creatorStaffNo;
    }

    public String getCreatorStaffNickNm() {
        return creatorStaffNickNm;
    }

    public void setCreatorStaffNickNm(String creatorStaffNickNm) {
        this.creatorStaffNickNm = creatorStaffNickNm;
    }

    public String getSourceIp() {
        return sourceIp;
    }

    public void setSourceIp(String sourceIp) {
        this.sourceIp = sourceIp;
    }
}

