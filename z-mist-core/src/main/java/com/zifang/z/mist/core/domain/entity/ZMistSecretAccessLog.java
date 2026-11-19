package com.zifang.z.mist.core.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.zifang.z.meta.api.annotation.BizField;
import com.zifang.z.meta.api.annotation.BizModel;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 密钥访问日志表
 * <p>
 * 区别于 {@link ZMistSecretAcl}（授权关系表,本表是操作流水表）。
 * 每次 secret_get / secret_put / secret_delete 都会写一行。
 * <p>
 * 表名: z_mist_secret_access_log
 * DDL 见 z-mist/db.sql（FEATURE021 同步补 DDL）
 *
 * @author zifang
 * @see ZMistSecretAccessLogMapper
 * @since 1.0.0
 */
@BizModel(name = "密钥访问日志表", module = "z-mist", description = "密钥访问日志表 区别于 ZMistSecretAcl（授权关系表,本表是操作流水表）。 每次 secret_get / secret_put / secret_delete 都会写一行。 表名: z_mist_secret_access_log DDL 见 z-mist/db.sql（FEATURE021 同步补 DDL）")
@TableName("z_mist_secret_access_log")
public class ZMistSecretAccessLog implements Serializable {

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
     * 操作类型: GET / PUT / DELETE / LIST
     */
    @BizField(name = "操作类型: GET / PUT / DELETE / LIST", type = BizField.FieldType.STRING)
    private String opType;

    /**
     * 操作者（z-ctc username）,未登录时为 "anonymous"
     */
    @BizField(name = "操作者（z-ctc username）,未登录时为 \"anonymous\"", type = BizField.FieldType.STRING)
    private String operator;

    /**
     * 操作者 IP
     */
    @BizField(name = "操作者 IP", type = BizField.FieldType.STRING)
    private String operatorIp;

    /**
     * 访问是否成功（true/false）
     */
    @BizField(name = "访问是否成功（true/false）", type = BizField.FieldType.BOOLEAN)
    private Boolean success;

    /**
     * 失败时的错误信息
     */
    @BizField(name = "失败时的错误信息", type = BizField.FieldType.STRING)
    private String errorMessage;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    @BizField(name = "创建时间", type = BizField.FieldType.DATETIME)
    private LocalDateTime gmtCreate;

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

    public String getOpType() {
        return opType;
    }

    public void setOpType(String opType) {
        this.opType = opType;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getOperatorIp() {
        return operatorIp;
    }

    public void setOperatorIp(String operatorIp) {
        this.operatorIp = operatorIp;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
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



