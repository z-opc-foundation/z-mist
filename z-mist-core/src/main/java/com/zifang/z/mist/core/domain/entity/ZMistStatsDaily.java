package com.zifang.z.mist.core.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zifang.z.meta.api.annotation.BizField;
import com.zifang.z.meta.api.annotation.BizModel;

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
@BizModel(name = "每日统计表", module = "z-mist", description = "每日统计表(FEATURE026). 由 StatsAggregator 每日凌晨聚合前一天数据写入,供数据看板查询。 stat_date 唯一约束保证 upsert 语义。")
@TableName("z_mist_stats_daily")
public class ZMistStatsDaily implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    @BizField(name = "主键ID", type = BizField.FieldType.INTEGER)
    private Long id;

    /**
     * 统计日期
     */
    @BizField(name = "统计日期", type = BizField.FieldType.DATE)
    private LocalDate statDate;

    /**
     * 密钥总数
     */
    @BizField(name = "密钥总数", type = BizField.FieldType.INTEGER)
    private Integer secretCount;

    /**
     * 新增数量
     */
    @BizField(name = "新增数量", type = BizField.FieldType.INTEGER)
    private Integer newCount;

    /**
     * 更新数量
     */
    @BizField(name = "更新数量", type = BizField.FieldType.INTEGER)
    private Integer updateCount;

    /**
     * 删除数量
     */
    @BizField(name = "删除数量", type = BizField.FieldType.INTEGER)
    private Integer deleteCount;

    /**
     * 获取次数
     */
    @BizField(name = "获取次数", type = BizField.FieldType.INTEGER)
    private Integer getCount;

    /**
     * 加密次数
     */
    @BizField(name = "加密次数", type = BizField.FieldType.INTEGER)
    private Integer encryptCount;

    /**
     * 解密次数
     */
    @BizField(name = "解密次数", type = BizField.FieldType.INTEGER)
    private Integer decryptCount;

    /**
     * 失败次数
     */
    @BizField(name = "失败次数", type = BizField.FieldType.INTEGER)
    private Integer failedCount;

    /**
     * 轮换次数
     */
    @BizField(name = "轮换次数", type = BizField.FieldType.INTEGER)
    private Integer rotationCount;

    /**
     * 创建时间
     */
    @BizField(name = "创建时间", type = BizField.FieldType.DATETIME)
    private LocalDateTime gmtCreate;

    /**
     * 修改时间
     */
    @BizField(name = "修改时间", type = BizField.FieldType.DATETIME)
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



