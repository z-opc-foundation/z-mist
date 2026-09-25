package com.zifang.z.mist.web.api.request;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 创建/更新应用请求 DTO(对应 {@code com.zifang.z.mist.core.domain.entity.ZMistAppInfo}).
 *
 * <p>字段与实体同名同类型, JSON 字段名与历史直传实体完全一致。
 *
 * @author zifang
 * @since 1.0.0
 */
public class AppReq implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String appName;
    private String appSecret;
    private String appType;
    private String namespace;
    private String description;
    private Integer enabled;
    private LocalDateTime gmtCreate;
    private LocalDateTime gmtModified;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }
    public String getAppSecret() { return appSecret; }
    public void setAppSecret(String appSecret) { this.appSecret = appSecret; }
    public String getAppType() { return appType; }
    public void setAppType(String appType) { this.appType = appType; }
    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    public LocalDateTime getGmtCreate() { return gmtCreate; }
    public void setGmtCreate(LocalDateTime gmtCreate) { this.gmtCreate = gmtCreate; }
    public LocalDateTime getGmtModified() { return gmtModified; }
    public void setGmtModified(LocalDateTime gmtModified) { this.gmtModified = gmtModified; }
}
