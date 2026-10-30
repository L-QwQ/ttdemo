package com.ttdemo.modules.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("ord_prediction_order")
public class OrdPredictionOrder {

    public static final int STATUS_PENDING_PAY = 1;
    public static final int STATUS_PAID = 2;
    public static final int STATUS_REFUNDED = 3;
    public static final int STATUS_CLOSED = 4;
    public static final int STATUS_SETTLED = 5;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long userId;
    private Long predictionId;
    private Long creatorId;
    private String typeCode;
    private Long issueId;
    private Integer pricePoints;
    private Integer platformFee;
    private Integer creatorIncome;
    private Integer status;
    private LocalDateTime payTime;
    private LocalDateTime refundTime;
    private Long settleId;
    private String clientIp;
    private String deviceId;
    private Integer riskFlag;
    private String idempotentKey;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getPredictionId() { return predictionId; }
    public void setPredictionId(Long predictionId) { this.predictionId = predictionId; }
    public Long getCreatorId() { return creatorId; }
    public void setCreatorId(Long creatorId) { this.creatorId = creatorId; }
    public String getTypeCode() { return typeCode; }
    public void setTypeCode(String typeCode) { this.typeCode = typeCode; }
    public Long getIssueId() { return issueId; }
    public void setIssueId(Long issueId) { this.issueId = issueId; }
    public Integer getPricePoints() { return pricePoints; }
    public void setPricePoints(Integer pricePoints) { this.pricePoints = pricePoints; }
    public Integer getPlatformFee() { return platformFee; }
    public void setPlatformFee(Integer platformFee) { this.platformFee = platformFee; }
    public Integer getCreatorIncome() { return creatorIncome; }
    public void setCreatorIncome(Integer creatorIncome) { this.creatorIncome = creatorIncome; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getPayTime() { return payTime; }
    public void setPayTime(LocalDateTime payTime) { this.payTime = payTime; }
    public LocalDateTime getRefundTime() { return refundTime; }
    public void setRefundTime(LocalDateTime refundTime) { this.refundTime = refundTime; }
    public Long getSettleId() { return settleId; }
    public void setSettleId(Long settleId) { this.settleId = settleId; }
    public String getClientIp() { return clientIp; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public Integer getRiskFlag() { return riskFlag; }
    public void setRiskFlag(Integer riskFlag) { this.riskFlag = riskFlag; }
    public String getIdempotentKey() { return idempotentKey; }
    public void setIdempotentKey(String idempotentKey) { this.idempotentKey = idempotentKey; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
