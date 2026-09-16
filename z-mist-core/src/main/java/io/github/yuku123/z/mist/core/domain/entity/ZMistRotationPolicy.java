package io.github.yuku123.z.mist.core.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 密钥自动轮换策略表(FEATURE026).
 * <p>
 * 每条记录绑定一个 (secretKey, group, namespace) 三元组,定义 cron 周期轮换。
 * 由 {@code RotationScheduler} 每分钟扫描 next_rotation_time 到期的策略并执行。
 *
 * @author zifang
 * @see ZMistRotationPolicyMapper
 * @since 1.0.0
 */
@TableName("z_mist_secret_rotation_policy")
public class ZMistRotationPolicy implements Serializable {

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
     * Cron表达式
     */
    private String cronExpression;

    /**
     * 轮换策略
     */
    private String rotationStrategy;

    /**
     * 新值长度
     */
    private Integer newValueLength;

    /**
     * 是否启用(0:否,1:是)
     */
    private Integer enabled;

    /**
     * 上次轮换时间
     */
    private LocalDateTime lastRotationTime;

    /**
     * 下次轮换时间
     */
    private LocalDateTime nextRotationTime;

    /**
     * 描述
     */
    private String description;

    /**
     * 创建人工号
     */
    private String creatorStaffNo;

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

    public String getCronExpression() {
        return cronExpression;
    }

    public void setCronExpression(String cronExpression) {
        this.cronExpression = cronExpression;
    }

    public String getRotationStrategy() {
        return rotationStrategy;
    }

    public void setRotationStrategy(String rotationStrategy) {
        this.rotationStrategy = rotationStrategy;
    }

    public Integer getNewValueLength() {
        return newValueLength;
    }

    public void setNewValueLength(Integer newValueLength) {
        this.newValueLength = newValueLength;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getLastRotationTime() {
        return lastRotationTime;
    }

    public void setLastRotationTime(LocalDateTime lastRotationTime) {
        this.lastRotationTime = lastRotationTime;
    }

    public LocalDateTime getNextRotationTime() {
        return nextRotationTime;
    }

    public void setNextRotationTime(LocalDateTime nextRotationTime) {
        this.nextRotationTime = nextRotationTime;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCreatorStaffNo() {
        return creatorStaffNo;
    }

    public void setCreatorStaffNo(String creatorStaffNo) {
        this.creatorStaffNo = creatorStaffNo;
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
