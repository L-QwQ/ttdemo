package com.ttdemo.modules.prediction.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("prd_prediction")
public class PrdPrediction {

    public static final int STATUS_DRAFT = 0;
    public static final int STATUS_PENDING_AUDIT = 1;
    public static final int STATUS_REJECTED = 2;
    public static final int STATUS_ONLINE = 3;
    public static final int STATUS_LOCKED = 4;
    public static final int STATUS_SETTLED = 5;
    public static final int STATUS_WITHDRAWN = 6;
    public static final int STATUS_OFFLINE = 7;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String predictionNo;
    private Long creatorId;
    private Long userId;
    private String typeCode;
    private Long issueId;
    private String issueNo;
    private String title;
    private String playType;
    private String summary;
    private String contentHtml;
    private String contentImages;
    private Integer priceBase;
    private BigDecimal priceCoefficient;
    private Integer pricePoints;
    private Integer isFree;
    private Integer status;
    private LocalDateTime onlineTime;
    private LocalDateTime lockTime;
    private LocalDateTime settleTime;
    private String contentHash;
    private Long auditBy;
    private LocalDateTime auditTime;
    private String auditRemark;
    private String riskHit;
    private Integer itemCount;
    private Integer hitCount;
    private BigDecimal hitRate;
    private Integer isHit;
    private Integer viewCount;
    private Integer buyerCount;
    private Long totalIncome;
    private Integer likeCount;
    /** 置顶推广到期时间，NULL 表示未推广 */
    private LocalDateTime topUntil;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;

    public boolean isFree() { return isFree != null && isFree == 1; }
    public boolean isSettled() { return status != null && status == STATUS_SETTLED; }
    public boolean isEditable() {
        return status != null && (status == STATUS_DRAFT || status == STATUS_REJECTED);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPredictionNo() { return predictionNo; }
    public void setPredictionNo(String predictionNo) { this.predictionNo = predictionNo; }
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
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getPlayType() { return playType; }
    public void setPlayType(String playType) { this.playType = playType; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getContentHtml() { return contentHtml; }
    public void setContentHtml(String contentHtml) { this.contentHtml = contentHtml; }
    public String getContentImages() { return contentImages; }
    public void setContentImages(String contentImages) { this.contentImages = contentImages; }
    public Integer getPriceBase() { return priceBase; }
    public void setPriceBase(Integer priceBase) { this.priceBase = priceBase; }
    public BigDecimal getPriceCoefficient() { return priceCoefficient; }
    public void setPriceCoefficient(BigDecimal priceCoefficient) { this.priceCoefficient = priceCoefficient; }
    public Integer getPricePoints() { return pricePoints; }
    public void setPricePoints(Integer pricePoints) { this.pricePoints = pricePoints; }
    public Integer getIsFree() { return isFree; }
    public void setIsFree(Integer isFree) { this.isFree = isFree; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getOnlineTime() { return onlineTime; }
    public void setOnlineTime(LocalDateTime onlineTime) { this.onlineTime = onlineTime; }
    public LocalDateTime getLockTime() { return lockTime; }
    public void setLockTime(LocalDateTime lockTime) { this.lockTime = lockTime; }
    public LocalDateTime getSettleTime() { return settleTime; }
    public void setSettleTime(LocalDateTime settleTime) { this.settleTime = settleTime; }
    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }
    public Long getAuditBy() { return auditBy; }
    public void setAuditBy(Long auditBy) { this.auditBy = auditBy; }
    public LocalDateTime getAuditTime() { return auditTime; }
    public void setAuditTime(LocalDateTime auditTime) { this.auditTime = auditTime; }
    public String getAuditRemark() { return auditRemark; }
    public void setAuditRemark(String auditRemark) { this.auditRemark = auditRemark; }
    public String getRiskHit() { return riskHit; }
    public void setRiskHit(String riskHit) { this.riskHit = riskHit; }
    public Integer getItemCount() { return itemCount; }
    public void setItemCount(Integer itemCount) { this.itemCount = itemCount; }
    public Integer getHitCount() { return hitCount; }
    public void setHitCount(Integer hitCount) { this.hitCount = hitCount; }
    public BigDecimal getHitRate() { return hitRate; }
    public void setHitRate(BigDecimal hitRate) { this.hitRate = hitRate; }
    public Integer getIsHit() { return isHit; }
    public void setIsHit(Integer isHit) { this.isHit = isHit; }
    public Integer getViewCount() { return viewCount; }
    public void setViewCount(Integer viewCount) { this.viewCount = viewCount; }
    public Integer getBuyerCount() { return buyerCount; }
    public void setBuyerCount(Integer buyerCount) { this.buyerCount = buyerCount; }
    public Long getTotalIncome() { return totalIncome; }
    public void setTotalIncome(Long totalIncome) { this.totalIncome = totalIncome; }
    public Integer getLikeCount() { return likeCount; }
    public void setLikeCount(Integer likeCount) { this.likeCount = likeCount; }
    public LocalDateTime getTopUntil() { return topUntil; }
    public void setTopUntil(LocalDateTime topUntil) { this.topUntil = topUntil; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
