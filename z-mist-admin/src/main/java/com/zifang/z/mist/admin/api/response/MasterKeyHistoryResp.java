package com.zifang.z.mist.admin.api.response;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 主密钥历史响应 DTO(对应 {@code com.zifang.z.mist.core.domain.entity.ZMistMasterKeyHistory}).
 *
 * <p>字段与实体公开字段同名同类型, JSON 字段名与历史直返实体完全一致。
 *
 * @author zifang
 * @since 1.0.0
 */
public class MasterKeyHistoryResp implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String keyAlias;
    private String keyVersion;
    private String keyMd5;
    private String algorithm;
    private Integer enabled;
    private LocalDateTime activatedTime;
    private LocalDateTime deactivatedTime;
    private String creator;
    private LocalDateTime gmtCreate;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getKeyAlias() { return keyAlias; }
    public void setKeyAlias(String keyAlias) { this.keyAlias = keyAlias; }
    public String getKeyVersion() { return keyVersion; }
    public void setKeyVersion(String keyVersion) { this.keyVersion = keyVersion; }
    public String getKeyMd5() { return keyMd5; }
    public void setKeyMd5(String keyMd5) { this.keyMd5 = keyMd5; }
    public String getAlgorithm() { return algorithm; }
    public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    public LocalDateTime getActivatedTime() { return activatedTime; }
    public void setActivatedTime(LocalDateTime activatedTime) { this.activatedTime = activatedTime; }
    public LocalDateTime getDeactivatedTime() { return deactivatedTime; }
    public void setDeactivatedTime(LocalDateTime deactivatedTime) { this.deactivatedTime = deactivatedTime; }
    public String getCreator() { return creator; }
    public void setCreator(String creator) { this.creator = creator; }
    public LocalDateTime getGmtCreate() { return gmtCreate; }
    public void setGmtCreate(LocalDateTime gmtCreate) { this.gmtCreate = gmtCreate; }
}
