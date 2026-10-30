package com.ttdemo.modules.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 资金流水行（充值 / 权益兑换 / 创作者结算 三表合并）
 */
public class FinFlowRow {

    private String bizType;
    private String bizNo;
    private Long userId;
    private Long points;
    private Long amountFen;
    private Integer status;
    private LocalDateTime bizTime;

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public String getBizNo() {
        return bizNo;
    }

    public void setBizNo(String bizNo) {
        this.bizNo = bizNo;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getPoints() {
        return points;
    }

    public void setPoints(Long points) {
        this.points = points;
    }

    public Long getAmountFen() {
        return amountFen;
    }

    public void setAmountFen(Long amountFen) {
        this.amountFen = amountFen;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDateTime getBizTime() {
        return bizTime;
    }

    public void setBizTime(LocalDateTime bizTime) {
        this.bizTime = bizTime;
    }

    public BigDecimal getAmountYuan() {
        return amountFen == null ? BigDecimal.ZERO : BigDecimal.valueOf(amountFen).movePointLeft(2);
    }
}
