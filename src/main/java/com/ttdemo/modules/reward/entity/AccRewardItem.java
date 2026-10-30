package com.ttdemo.modules.reward.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 积分兑换权益商品（会员 / 实物礼品），不含任何现金出款能力
 */
@TableName("acc_reward_item")
public class AccRewardItem {

    public static final int CATEGORY_VIP = 1;
    public static final int CATEGORY_GIFT = 2;

    public static final int STATUS_OFF = 0;
    public static final int STATUS_ON = 1;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String itemCode;
    private String name;
    /** 1会员 2实物礼品 */
    private Integer category;
    private Long pointsCost;
    /** 折算价值（分），兑换时快照进订单用于财务统计 */
    private Integer valueFen;
    private Integer durationDays;
    /** -1 表示不限量 */
    private Integer stock;
    private Integer exchangedCount;
    private String description;
    private Integer status;
    private Integer sortNo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getItemCode() {
        return itemCode;
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getCategory() {
        return category;
    }

    public void setCategory(Integer category) {
        this.category = category;
    }

    public Long getPointsCost() {
        return pointsCost;
    }

    public void setPointsCost(Long pointsCost) {
        this.pointsCost = pointsCost;
    }

    public Integer getValueFen() {
        return valueFen;
    }

    public void setValueFen(Integer valueFen) {
        this.valueFen = valueFen;
    }

    public Integer getDurationDays() {
        return durationDays;
    }

    public void setDurationDays(Integer durationDays) {
        this.durationDays = durationDays;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    public Integer getExchangedCount() {
        return exchangedCount;
    }

    public void setExchangedCount(Integer exchangedCount) {
        this.exchangedCount = exchangedCount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
