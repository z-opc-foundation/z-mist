package com.zifang.z.mist.admin.api.response;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 轮换策略响应 DTO(对应 {@code com.zifang.z.mist.core.domain.entity.ZMistRotationPolicy}).
 *
 * <p>字段与实体公开字段同名同类型, JSON 字段名与历史直返实体完全一致。
 *
 * @author zifang
 * @since 1.0.0
 */
public class RotationPolicyResp implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String secretKey;
    private String group;
    private String namespace;
    private String cronExpression;
    private String rotationStrategy;
    private Integer newValueLength;
    private Integer enabled;
    private LocalDateTime lastRotationTime;
    private LocalDateTime nextRotationTime;
    private String description;
    private String creatorStaffNo;
    private LocalDateTime gmtCreate;
    private LocalDateTime gmtModified;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    public String getGroup() { return group; }
    public void setGroup(String group) { this.group = group; }
    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }
    public String getCronExpression() { return cronExpression; }
    public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }
    public String getRotationStrategy() { return rotationStrategy; }
    public void setRotationStrategy(String rotationStrategy) { this.rotationStrategy = rotationStrategy; }
    public Integer getNewValueLength() { return newValueLength; }
    public void setNewValueLength(Integer newValueLength) { this.newValueLength = newValueLength; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    public LocalDateTime getLastRotationTime() { return lastRotationTime; }
    public void setLastRotationTime(LocalDateTime lastRotationTime) { this.lastRotationTime = lastRotationTime; }
    public LocalDateTime getNextRotationTime() { return nextRotationTime; }
    public void setNextRotationTime(LocalDateTime nextRotationTime) { this.nextRotationTime = nextRotationTime; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCreatorStaffNo() { return creatorStaffNo; }
    public void setCreatorStaffNo(String creatorStaffNo) { this.creatorStaffNo = creatorStaffNo; }
    public LocalDateTime getGmtCreate() { return gmtCreate; }
    public void setGmtCreate(LocalDateTime gmtCreate) { this.gmtCreate = gmtCreate; }
    public LocalDateTime getGmtModified() { return gmtModified; }
    public void setGmtModified(LocalDateTime gmtModified) { this.gmtModified = gmtModified; }
}
