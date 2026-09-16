package io.github.yuku123.z.mist.core.domain.entity;

import com.baomidou.mybatisplus.annotation.*;

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
@TableName("z_mist_secret_access_log")
public class ZMistSecretAccessLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 密钥Key
     */
    private String secretKey;

    /**
     * 分组
     */
    @TableField("`group`")
    private String group;

    /**
     * 命名空间
     */
    private String namespace;

    /**
     * 操作类型: GET / PUT / DELETE / LIST
     */
    private String opType;

    /**
     * 操作者（z-ctc username）,未登录时为 "anonymous"
     */
    private String operator;

    /**
     * 操作者 IP
     */
    private String operatorIp;

    /**
     * 访问是否成功（true/false）
     */
    private Boolean success;

    /**
     * 失败时的错误信息
     */
    private String errorMessage;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
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
