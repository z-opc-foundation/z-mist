package com.zifang.z.mist.web.api.response;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 访问日志响应 DTO(对应 {@code com.zifang.z.mist.core.domain.entity.ZMistSecretAccessLog}).
 *
 * <p>字段与实体公开字段同名同类型, JSON 字段名与历史直返实体完全一致。
 *
 * @author zifang
 * @since 1.0.0
 */
public class AccessLogResp implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String secretKey;
    private String group;
    private String namespace;
    private String opType;
    private String operator;
    private String operatorIp;
    private Boolean success;
    private String errorMessage;
    private LocalDateTime gmtCreate;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    public String getGroup() { return group; }
    public void setGroup(String group) { this.group = group; }
    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }
    public String getOpType() { return opType; }
    public void setOpType(String opType) { this.opType = opType; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getOperatorIp() { return operatorIp; }
    public void setOperatorIp(String operatorIp) { this.operatorIp = operatorIp; }
    public Boolean getSuccess() { return success; }
    public void setSuccess(Boolean success) { this.success = success; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getGmtCreate() { return gmtCreate; }
    public void setGmtCreate(LocalDateTime gmtCreate) { this.gmtCreate = gmtCreate; }
}
