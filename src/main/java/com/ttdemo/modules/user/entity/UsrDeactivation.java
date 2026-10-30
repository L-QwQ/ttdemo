package com.ttdemo.modules.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 账号注销申请（消费者权益兜底：未消费积分按充值比例折算登记退款）
 */
@TableName("usr_deactivation")
public class UsrDeactivation {

    public static final int STATUS_PENDING = 1;
    public static final int STATUS_DONE = 2;
    public static final int STATUS_REJECTED = 3;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String phone;
    private String reason;
    private Long pointsLeft;
    private Integer refundFen;
    private Integer status;
    private Long handleBy;
    private LocalDateTime handleTime;
    private String handleRemark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Long getPointsLeft() {
        return pointsLeft;
    }

    public void setPointsLeft(Long pointsLeft) {
        this.pointsLeft = pointsLeft;
    }

    public Integer getRefundFen() {
        return refundFen;
    }

    public void setRefundFen(Integer refundFen) {
        this.refundFen = refundFen;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Long getHandleBy() {
        return handleBy;
    }

    public void setHandleBy(Long handleBy) {
        this.handleBy = handleBy;
    }

    public LocalDateTime getHandleTime() {
        return handleTime;
    }

    public void setHandleTime(LocalDateTime handleTime) {
        this.handleTime = handleTime;
    }

    public String getHandleRemark() {
        return handleRemark;
    }

    public void setHandleRemark(String handleRemark) {
        this.handleRemark = handleRemark;
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
