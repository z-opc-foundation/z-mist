package io.github.yuku123.z.mist.core.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 每日统计表(FEATURE026).
 * <p>
 * 由 {@code StatsAggregator} 每日凌晨聚合前一天数据写入,供数据看板查询。
 * stat_date 唯一约束保证 upsert 语义。
 *
 * @author zifang
 * @see ZMistStatsDailyMapper
 * @since 1.0.0
 */
@TableName("z_mist_stats_daily")
public class ZMistStatsDaily implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 统计日期
     */
    private LocalDate statDate;

    /**
     * 密钥总数
     */
    private Integer secretCount;

    /**
     * 新增数量
     */
    private Integer newCount;

    /**
     * 更新数量
     */
    private Integer updateCount;

    /**
     * 删除数量
     */
    private Integer deleteCount;

    /**
     * 获取次数
     */
    private Integer getCount;

    /**
     * 加密次数
     */
    private Integer encryptCount;

    /**
     * 解密次数
     */
    private Integer decryptCount;

    /**
     * 失败次数
     */
    private Integer failedCount;

    /**
     * 轮换次数
     */
    private Integer rotationCount;

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

    public LocalDate getStatDate() {
        return statDate;
    }

    public void setStatDate(LocalDate statDate) {
        this.statDate = statDate;
    }

    public Integer getSecretCount() {
        return secretCount;
    }

    public void setSecretCount(Integer secretCount) {
        this.secretCount = secretCount;
    }

    public Integer getNewCount() {
        return newCount;
    }

    public void setNewCount(Integer newCount) {
        this.newCount = newCount;
    }

    public Integer getUpdateCount() {
        return updateCount;
    }

    public void setUpdateCount(Integer updateCount) {
        this.updateCount = updateCount;
    }

    public Integer getDeleteCount() {
        return deleteCount;
    }

    public void setDeleteCount(Integer deleteCount) {
        this.deleteCount = deleteCount;
    }

    public Integer getGetCount() {
        return getCount;
    }

    public void setGetCount(Integer getCount) {
        this.getCount = getCount;
    }

    public Integer getEncryptCount() {
        return encryptCount;
    }

    public void setEncryptCount(Integer encryptCount) {
        this.encryptCount = encryptCount;
    }

    public Integer getDecryptCount() {
        return decryptCount;
    }

    public void setDecryptCount(Integer decryptCount) {
        this.decryptCount = decryptCount;
    }

    public Integer getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(Integer failedCount) {
        this.failedCount = failedCount;
    }

    public Integer getRotationCount() {
        return rotationCount;
    }

    public void setRotationCount(Integer rotationCount) {
        this.rotationCount = rotationCount;
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
