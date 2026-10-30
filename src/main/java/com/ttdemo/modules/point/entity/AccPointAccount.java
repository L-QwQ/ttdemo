package com.ttdemo.modules.point.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("acc_point_account")
public class AccPointAccount {

    public static final int STATUS_NORMAL = 1;
    public static final int STATUS_FROZEN = 2;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long available;
    private Long frozen;
    private Long totalRecharge;
    private Long totalIncome;
    private Long totalConsume;
    private Long totalExpired;
    private Integer status;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getAvailable() { return available; }
    public void setAvailable(Long available) { this.available = available; }
    public Long getFrozen() { return frozen; }
    public void setFrozen(Long frozen) { this.frozen = frozen; }
    public Long getTotalRecharge() { return totalRecharge; }
    public void setTotalRecharge(Long totalRecharge) { this.totalRecharge = totalRecharge; }
    public Long getTotalIncome() { return totalIncome; }
    public void setTotalIncome(Long totalIncome) { this.totalIncome = totalIncome; }
    public Long getTotalConsume() { return totalConsume; }
    public void setTotalConsume(Long totalConsume) { this.totalConsume = totalConsume; }
    public Long getTotalExpired() { return totalExpired; }
    public void setTotalExpired(Long totalExpired) { this.totalExpired = totalExpired; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
