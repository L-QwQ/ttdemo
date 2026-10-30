package com.ttdemo.modules.lottery.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("lot_issue")
public class LotIssue {

    public static final int DRAW_PENDING = 0;
    public static final int DRAW_DONE = 1;
    public static final int DRAW_DELAYED = 2;
    public static final int DRAW_CANCELED = 3;

    public static final int SETTLE_PENDING = 0;
    public static final int SETTLE_RUNNING = 1;
    public static final int SETTLE_DONE = 2;
    public static final int SETTLE_FAILED = 3;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String typeCode;
    private String issueNo;
    private LocalDateTime drawTime;
    private LocalDateTime saleEndTime;
    private String redNumbers;
    private String blueNumbers;
    private String extra;
    private Integer drawStatus;
    private Integer settleStatus;
    private LocalDateTime settleTime;
    private String settleError;
    private Integer dataSource;
    private String reviewTicket;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public boolean isDrawn() { return drawStatus != null && drawStatus == DRAW_DONE; }
    public boolean isSettled() { return settleStatus != null && settleStatus == SETTLE_DONE; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTypeCode() { return typeCode; }
    public void setTypeCode(String typeCode) { this.typeCode = typeCode; }
    public String getIssueNo() { return issueNo; }
    public void setIssueNo(String issueNo) { this.issueNo = issueNo; }
    public LocalDateTime getDrawTime() { return drawTime; }
    public void setDrawTime(LocalDateTime drawTime) { this.drawTime = drawTime; }
    public LocalDateTime getSaleEndTime() { return saleEndTime; }
    public void setSaleEndTime(LocalDateTime saleEndTime) { this.saleEndTime = saleEndTime; }
    public String getRedNumbers() { return redNumbers; }
    public void setRedNumbers(String redNumbers) { this.redNumbers = redNumbers; }
    public String getBlueNumbers() { return blueNumbers; }
    public void setBlueNumbers(String blueNumbers) { this.blueNumbers = blueNumbers; }
    public String getExtra() { return extra; }
    public void setExtra(String extra) { this.extra = extra; }
    public Integer getDrawStatus() { return drawStatus; }
    public void setDrawStatus(Integer drawStatus) { this.drawStatus = drawStatus; }
    public Integer getSettleStatus() { return settleStatus; }
    public void setSettleStatus(Integer settleStatus) { this.settleStatus = settleStatus; }
    public LocalDateTime getSettleTime() { return settleTime; }
    public void setSettleTime(LocalDateTime settleTime) { this.settleTime = settleTime; }
    public String getSettleError() { return settleError; }
    public void setSettleError(String settleError) { this.settleError = settleError; }
    public Integer getDataSource() { return dataSource; }
    public void setDataSource(Integer dataSource) { this.dataSource = dataSource; }
    public String getReviewTicket() { return reviewTicket; }
    public void setReviewTicket(String reviewTicket) { this.reviewTicket = reviewTicket; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
