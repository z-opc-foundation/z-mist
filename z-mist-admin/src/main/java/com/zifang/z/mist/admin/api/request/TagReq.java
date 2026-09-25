package com.zifang.z.mist.admin.api.request;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 添加/更新标签请求 DTO(对应 {@code com.zifang.z.mist.core.domain.entity.ZMistSecretTag}).
 *
 * <p>字段与实体同名同类型, JSON 字段名与历史直传实体完全一致。
 *
 * @author zifang
 * @since 1.0.0
 */
public class TagReq implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String secretKey;
    private String group;
    private String namespace;
    private String tagKey;
    private String tagValue;
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
    public String getTagKey() { return tagKey; }
    public void setTagKey(String tagKey) { this.tagKey = tagKey; }
    public String getTagValue() { return tagValue; }
    public void setTagValue(String tagValue) { this.tagValue = tagValue; }
    public LocalDateTime getGmtCreate() { return gmtCreate; }
    public void setGmtCreate(LocalDateTime gmtCreate) { this.gmtCreate = gmtCreate; }
    public LocalDateTime getGmtModified() { return gmtModified; }
    public void setGmtModified(LocalDateTime gmtModified) { this.gmtModified = gmtModified; }
}
