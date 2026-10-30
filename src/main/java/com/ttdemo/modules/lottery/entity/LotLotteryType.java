package com.ttdemo.modules.lottery.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import java.time.LocalTime;

@TableName("lot_lottery_type")
public class LotLotteryType {

    @TableId(type = IdType.AUTO)
    private Integer id;
    private String code;
    private String name;
    /** 1福彩 2体彩 */
    private Integer category;
    private String drawWeekdays;
    private LocalTime drawTimeOfDay;
    private Integer saleEndOffsetMin;
    /** 玩法与号码范围 JSON 原文 */
    private String playConfig;
    private String icon;
    private Integer sortNo;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getCategory() { return category; }
    public void setCategory(Integer category) { this.category = category; }
    public String getDrawWeekdays() { return drawWeekdays; }
    public void setDrawWeekdays(String drawWeekdays) { this.drawWeekdays = drawWeekdays; }
    public LocalTime getDrawTimeOfDay() { return drawTimeOfDay; }
    public void setDrawTimeOfDay(LocalTime drawTimeOfDay) { this.drawTimeOfDay = drawTimeOfDay; }
    public Integer getSaleEndOffsetMin() { return saleEndOffsetMin; }
    public void setSaleEndOffsetMin(Integer saleEndOffsetMin) { this.saleEndOffsetMin = saleEndOffsetMin; }
    public String getPlayConfig() { return playConfig; }
    public void setPlayConfig(String playConfig) { this.playConfig = playConfig; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
