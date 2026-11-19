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
 * 密钥历史版本表
 *
 * @author zifang
 * @see ZMistSecretHistoryMapper
 * @since 1.0.0
 */
@BizModel(name = "密钥历史版本表", module = "z-mist", description = "密钥历史版本表")
@TableName("z_mist_secret_history")
public class ZMistSecretHistory implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    @BizField(name = "主键ID", type = BizField.FieldType.INTEGER)
    private Long id;

    /**
     * 密钥主ID（关联z_mist_secret_info.id）
     */
    @BizField(name = "密钥主ID（关联z_mist_secret_info.id）", type = BizField.FieldType.INTEGER)
    private Long nid;

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
     * 应用名
     */
    @BizField(name = "应用名", type = BizField.FieldType.STRING)
    private String appName;

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
     * 密钥版本
     */
    @BizField(name = "密钥版本", type = BizField.FieldType.STRING)
    private String keyVersion;

    /**
     * 操作类型
     */
    @BizField(name = "操作类型", type = BizField.FieldType.STRING)
    private String opType;

    /**
     * 操作者工号
     */
    @BizField(name = "操作者工号", type = BizField.FieldType.STRING)
    private String operatorStaffNo;

    /**
     * 操作者IP
     */
    @BizField(name = "操作者IP", type = BizField.FieldType.STRING)
    private String operatorIp;

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
     * 命名空间
     */
    @BizField(name = "命名空间", type = BizField.FieldType.STRING)
    private String namespace;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getNid() {
        return nid;
    }

    public void setNid(Long nid) {
        this.nid = nid;
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

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
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

    public String getKeyVersion() {
        return keyVersion;
    }

    public void setKeyVersion(String keyVersion) {
        this.keyVersion = keyVersion;
    }

    public String getOpType() {
        return opType;
    }

    public void setOpType(String opType) {
        this.opType = opType;
    }

    public String getOperatorStaffNo() {
        return operatorStaffNo;
    }

    public void setOperatorStaffNo(String operatorStaffNo) {
        this.operatorStaffNo = operatorStaffNo;
    }

    public String getOperatorIp() {
        return operatorIp;
    }

    public void setOperatorIp(String operatorIp) {
        this.operatorIp = operatorIp;
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

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }
}

