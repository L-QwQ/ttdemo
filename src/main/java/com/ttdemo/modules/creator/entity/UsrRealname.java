package com.ttdemo.modules.creator.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("usr_realname")
public class UsrRealname {

    public static final int AUDIT_PENDING = 0;
    public static final int AUDIT_PASS = 1;
    public static final int AUDIT_REJECT = 2;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private byte[] realName;
    private byte[] idCardNo;
    private String idCardHash;
    private byte[] mobileEnc;
    private Integer age;
    private Integer auditStatus;
    private String auditRemark;
    private Long auditBy;
    private LocalDateTime auditTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public byte[] getRealName() { return realName; }
    public void setRealName(byte[] realName) { this.realName = realName; }
    public byte[] getIdCardNo() { return idCardNo; }
    public void setIdCardNo(byte[] idCardNo) { this.idCardNo = idCardNo; }
    public String getIdCardHash() { return idCardHash; }
    public void setIdCardHash(String idCardHash) { this.idCardHash = idCardHash; }
    public byte[] getMobileEnc() { return mobileEnc; }
    public void setMobileEnc(byte[] mobileEnc) { this.mobileEnc = mobileEnc; }
    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }
    public Integer getAuditStatus() { return auditStatus; }
    public void setAuditStatus(Integer auditStatus) { this.auditStatus = auditStatus; }
    public String getAuditRemark() { return auditRemark; }
    public void setAuditRemark(String auditRemark) { this.auditRemark = auditRemark; }
    public Long getAuditBy() { return auditBy; }
    public void setAuditBy(Long auditBy) { this.auditBy = auditBy; }
    public LocalDateTime getAuditTime() { return auditTime; }
    public void setAuditTime(LocalDateTime auditTime) { this.auditTime = auditTime; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
