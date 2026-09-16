package io.github.yuku123.z.mist.core.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 主密钥历史表(FEATURE026).
 * <p>
 * 同一 key_alias 可对应多版本主密钥,用于支持 re-encrypt 主密钥轮换。
 * 每次新增主密钥写一行,enabled=1 表示当前活跃版本。
 *
 * @author zifang
 * @see ZMistMasterKeyHistoryMapper
 * @since 1.0.0
 */
@TableName("z_mist_master_key_history")
public class ZMistMasterKeyHistory implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 密钥别名
     */
    private String keyAlias;

    /**
     * 密钥版本
     */
    private String keyVersion;

    /**
     * 密钥MD5
     */
    private String keyMd5;

    /**
     * 加密算法
     */
    private String algorithm;

    /**
     * 是否启用(0:否,1:是)
     */
    private Integer enabled;

    /**
     * 激活时间
     */
    private LocalDateTime activatedTime;

    /**
     * 停用时间
     */
    private LocalDateTime deactivatedTime;

    /**
     * 创建者
     */
    private String creator;

    /**
     * 创建时间
     */
    private LocalDateTime gmtCreate;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getKeyAlias() {
        return keyAlias;
    }

    public void setKeyAlias(String keyAlias) {
        this.keyAlias = keyAlias;
    }

    public String getKeyVersion() {
        return keyVersion;
    }

    public void setKeyVersion(String keyVersion) {
        this.keyVersion = keyVersion;
    }

    public String getKeyMd5() {
        return keyMd5;
    }

    public void setKeyMd5(String keyMd5) {
        this.keyMd5 = keyMd5;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getActivatedTime() {
        return activatedTime;
    }

    public void setActivatedTime(LocalDateTime activatedTime) {
        this.activatedTime = activatedTime;
    }

    public LocalDateTime getDeactivatedTime() {
        return deactivatedTime;
    }

    public void setDeactivatedTime(LocalDateTime deactivatedTime) {
        this.deactivatedTime = deactivatedTime;
    }

    public String getCreator() {
        return creator;
    }

    public void setCreator(String creator) {
        this.creator = creator;
    }

    public LocalDateTime getGmtCreate() {
        return gmtCreate;
    }

    public void setGmtCreate(LocalDateTime gmtCreate) {
        this.gmtCreate = gmtCreate;
    }
}
