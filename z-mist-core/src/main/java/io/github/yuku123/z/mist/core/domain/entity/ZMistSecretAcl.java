package io.github.yuku123.z.mist.core.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 密钥访问授权表
 *
 * @author zifang
 * @see ZMistSecretAclMapper
 * @since 1.0.0
 */
@TableName("z_mist_secret_acl")
public class ZMistSecretAcl implements Serializable {

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
     * 授权应用名
     */
    private String authorizedApp;

    /**
     * 授权环境
     */
    private String authorizedEnv;

    /**
     * 权限等级
     */
    private String permissionLevel;

    /**
     * 过期时间
     */
    private LocalDateTime expireTime;

    /**
     * 创建时间
     */
    private LocalDateTime gmtCreate;

    /**
     * 修改时间
     */
    private LocalDateTime gmtModified;

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

    public String getAuthorizedApp() {
        return authorizedApp;
    }

    public void setAuthorizedApp(String authorizedApp) {
        this.authorizedApp = authorizedApp;
    }

    public String getAuthorizedEnv() {
        return authorizedEnv;
    }

    public void setAuthorizedEnv(String authorizedEnv) {
        this.authorizedEnv = authorizedEnv;
    }

    public String getPermissionLevel() {
        return permissionLevel;
    }

    public void setPermissionLevel(String permissionLevel) {
        this.permissionLevel = permissionLevel;
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
}