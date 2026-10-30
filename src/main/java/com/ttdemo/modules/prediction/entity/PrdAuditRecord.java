package com.ttdemo.modules.prediction.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("prd_audit_record")
public class PrdAuditRecord {

    public static final String BIZ_PREDICTION = "PREDICTION";
    public static final String BIZ_CREATOR = "CREATOR";

    @TableId(type = IdType.AUTO)
    private Long id;
    private String bizType;
    private Long bizId;
    private Integer auditResult;
    private String auditRemark;
    private Integer riskLevel;
    private String riskDetail;
    private Long auditBy;
    private LocalDateTime auditTime;
    private Integer costMs;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getBizType() { return bizType; }
    public void setBizType(String bizType) { this.bizType = bizType; }
    public Long getBizId() { return bizId; }
    public void setBizId(Long bizId) { this.bizId = bizId; }
    public Integer getAuditResult() { return auditResult; }
    public void setAuditResult(Integer auditResult) { this.auditResult = auditResult; }
    public String getAuditRemark() { return auditRemark; }
    public void setAuditRemark(String auditRemark) { this.auditRemark = auditRemark; }
    public Integer getRiskLevel() { return riskLevel; }
    public void setRiskLevel(Integer riskLevel) { this.riskLevel = riskLevel; }
    public String getRiskDetail() { return riskDetail; }
    public void setRiskDetail(String riskDetail) { this.riskDetail = riskDetail; }
    public Long getAuditBy() { return auditBy; }
    public void setAuditBy(Long auditBy) { this.auditBy = auditBy; }
    public LocalDateTime getAuditTime() { return auditTime; }
    public void setAuditTime(LocalDateTime auditTime) { this.auditTime = auditTime; }
    public Integer getCostMs() { return costMs; }
    public void setCostMs(Integer costMs) { this.costMs = costMs; }
}
