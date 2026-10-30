package com.ttdemo.modules.settlement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("acc_settlement")
public class AccSettlement {

    public static final int STATUS_PENDING = 1;
    public static final int STATUS_CREDITED = 2;
    public static final int STATUS_CANCELED = 3;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String settleNo;
    private Long creatorId;
    private Long userId;
    private String typeCode;
    private Long issueId;
    private String issueNo;
    private Integer orderCount;
    private Long grossPoints;
    private Long platformFee;
    private Long creatorPoints;
    private Integer status;
    private LocalDateTime creditTime;
    private String cancelReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSettleNo() { return settleNo; }
    public void setSettleNo(String settleNo) { this.settleNo = settleNo; }
    public Long getCreatorId() { return creatorId; }
    public void setCreatorId(Long creatorId) { this.creatorId = creatorId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getTypeCode() { return typeCode; }
    public void setTypeCode(String typeCode) { this.typeCode = typeCode; }
    public Long getIssueId() { return issueId; }
    public void setIssueId(Long issueId) { this.issueId = issueId; }
    public String getIssueNo() { return issueNo; }
    public void setIssueNo(String issueNo) { this.issueNo = issueNo; }
    public Integer getOrderCount() { return orderCount; }
    public void setOrderCount(Integer orderCount) { this.orderCount = orderCount; }
    public Long getGrossPoints() { return grossPoints; }
    public void setGrossPoints(Long grossPoints) { this.grossPoints = grossPoints; }
    public Long getPlatformFee() { return platformFee; }
    public void setPlatformFee(Long platformFee) { this.platformFee = platformFee; }
    public Long getCreatorPoints() { return creatorPoints; }
    public void setCreatorPoints(Long creatorPoints) { this.creatorPoints = creatorPoints; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getCreditTime() { return creditTime; }
    public void setCreditTime(LocalDateTime creditTime) { this.creditTime = creditTime; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
