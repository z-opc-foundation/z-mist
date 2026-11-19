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
 * 密钥轮换历史表(FEATURE026).
 * <p>
 * 每次自动或手动轮换都写一行,用于审计和追溯。
 *
 * @author zifang
 * @see ZMistRotationHistoryMapper
 * @since 1.0.0
 */
@BizModel(name = "密钥轮换历史表", module = "z-mist", description = "密钥轮换历史表(FEATURE026). 每次自动或手动轮换都写一行,用于审计和追溯。")
@TableName("z_mist_secret_rotation_history")
public class ZMistRotationHistory implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    @BizField(name = "主键ID", type = BizField.FieldType.INTEGER)
    private Long id;

    /**
     * 轮换策略ID
     */
    @BizField(name = "轮换策略ID", type = BizField.FieldType.INTEGER)
    private Long policyId;

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
     * 旧版本
     */
    @BizField(name = "旧版本", type = BizField.FieldType.STRING)
    private String oldVersion;

    /**
     * 新版本
     */
    @BizField(name = "新版本", type = BizField.FieldType.STRING)
    private String newVersion;

    /**
     * 触发类型
     */
    @BizField(name = "触发类型", type = BizField.FieldType.STRING)
    private String triggerType;

    /**
     * 触发者
     */
    @BizField(name = "触发者", type = BizField.FieldType.STRING)
    private String triggerBy;

    /**
     * 是否成功(0:否,1:是)
     */
    @BizField(name = "success", type = BizField.FieldType.INTEGER)
    private Integer success;

    /**
     * 错误信息
     */
    @BizField(name = "错误信息", type = BizField.FieldType.STRING)
    private String errorMessage;

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

    public Long getPolicyId() {
        return policyId;
    }

    public void setPolicyId(Long policyId) {
        this.policyId = policyId;
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

    public String getOldVersion() {
        return oldVersion;
    }

    public void setOldVersion(String oldVersion) {
        this.oldVersion = oldVersion;
    }

    public String getNewVersion() {
        return newVersion;
    }

    public void setNewVersion(String newVersion) {
        this.newVersion = newVersion;
    }

    public String getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(String triggerType) {
        this.triggerType = triggerType;
    }

    public String getTriggerBy() {
        return triggerBy;
    }

    public void setTriggerBy(String triggerBy) {
        this.triggerBy = triggerBy;
    }

    public Integer getSuccess() {
        return success;
    }

    public void setSuccess(Integer success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getGmtCreate() {
        return gmtCreate;
    }

    public void setGmtCreate(LocalDateTime gmtCreate) {
        this.gmtCreate = gmtCreate;
    }
}



