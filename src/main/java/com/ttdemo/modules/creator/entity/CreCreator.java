package com.ttdemo.modules.creator.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("cre_creator")
public class CreCreator {

    public static final int STATUS_PENDING = 1;
    public static final int STATUS_PASS = 2;
    public static final int STATUS_REJECT = 3;
    public static final int STATUS_BANNED = 4;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String penName;
    private String intro;
    private String tags;
    private String applyReason;
    private Integer status;
    private String auditRemark;
    private Long auditBy;
    private LocalDateTime auditTime;
    private Integer creatorLevel;
    private BigDecimal platformFeeRate;
    private LocalDateTime newbieUntil;
    private LocalDateTime certifiedAt;
    private Integer favCount;
    private Long totalIncome;
    private Integer totalPredictions;
    private BigDecimal withdrawRate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;

    public boolean isApproved() { return status != null && status == STATUS_PASS; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getPenName() { return penName; }
    public void setPenName(String penName) { this.penName = penName; }
    public String getIntro() { return intro; }
    public void setIntro(String intro) { this.intro = intro; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public String getApplyReason() { return applyReason; }
    public void setApplyReason(String applyReason) { this.applyReason = applyReason; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getAuditRemark() { return auditRemark; }
    public void setAuditRemark(String auditRemark) { this.auditRemark = auditRemark; }
    public Long getAuditBy() { return auditBy; }
    public void setAuditBy(Long auditBy) { this.auditBy = auditBy; }
    public LocalDateTime getAuditTime() { return auditTime; }
    public void setAuditTime(LocalDateTime auditTime) { this.auditTime = auditTime; }
    public Integer getCreatorLevel() { return creatorLevel; }
    public void setCreatorLevel(Integer creatorLevel) { this.creatorLevel = creatorLevel; }
    public BigDecimal getPlatformFeeRate() { return platformFeeRate; }
    public void setPlatformFeeRate(BigDecimal platformFeeRate) { this.platformFeeRate = platformFeeRate; }
    public LocalDateTime getNewbieUntil() { return newbieUntil; }
    public void setNewbieUntil(LocalDateTime newbieUntil) { this.newbieUntil = newbieUntil; }
    public LocalDateTime getCertifiedAt() { return certifiedAt; }
    public void setCertifiedAt(LocalDateTime certifiedAt) { this.certifiedAt = certifiedAt; }
    public Integer getFavCount() { return favCount; }
    public void setFavCount(Integer favCount) { this.favCount = favCount; }
    public Long getTotalIncome() { return totalIncome; }
    public void setTotalIncome(Long totalIncome) { this.totalIncome = totalIncome; }
    public Integer getTotalPredictions() { return totalPredictions; }
    public void setTotalPredictions(Integer totalPredictions) { this.totalPredictions = totalPredictions; }
    public BigDecimal getWithdrawRate() { return withdrawRate; }
    public void setWithdrawRate(BigDecimal withdrawRate) { this.withdrawRate = withdrawRate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
