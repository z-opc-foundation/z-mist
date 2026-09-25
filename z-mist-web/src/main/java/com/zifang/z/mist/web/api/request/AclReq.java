package com.zifang.z.mist.web.api.request;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 创建/更新授权请求 DTO(对应 {@code com.zifang.z.mist.core.domain.entity.ZMistSecretAcl}).
 *
 * <p>字段与实体同名同类型, JSON 字段名与历史直传实体完全一致。
 *
 * @author zifang
 * @since 1.0.0
 */
public class AclReq implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String secretKey;
    private String group;
    private String namespace;
    private String authorizedApp;
    private String authorizedEnv;
    private String permissionLevel;
    private LocalDateTime expireTime;
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
    public String getAuthorizedApp() { return authorizedApp; }
    public void setAuthorizedApp(String authorizedApp) { this.authorizedApp = authorizedApp; }
    public String getAuthorizedEnv() { return authorizedEnv; }
    public void setAuthorizedEnv(String authorizedEnv) { this.authorizedEnv = authorizedEnv; }
    public String getPermissionLevel() { return permissionLevel; }
    public void setPermissionLevel(String permissionLevel) { this.permissionLevel = permissionLevel; }
    public LocalDateTime getExpireTime() { return expireTime; }
    public void setExpireTime(LocalDateTime expireTime) { this.expireTime = expireTime; }
    public LocalDateTime getGmtCreate() { return gmtCreate; }
    public void setGmtCreate(LocalDateTime gmtCreate) { this.gmtCreate = gmtCreate; }
    public LocalDateTime getGmtModified() { return gmtModified; }
    public void setGmtModified(LocalDateTime gmtModified) { this.gmtModified = gmtModified; }
}
