package com.ttdemo.modules.user.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("sys_user")
public class SysUser {

    public static final String ROLE_USER = "USER";
    public static final String ROLE_ADMIN = "ADMIN";
    public static final int STATUS_ENABLED = 1;
    public static final int STATUS_DISABLED = 0;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String phone;

    /** BCrypt 密文 */
    private String password;

    private String nickname;

    private String avatar;

    private String role;

    private Integer status;

    private LocalDateTime lastLoginAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    // ---------- 彩票平台扩展字段 ----------
    /** 0未知 1男 2女 */
    private Integer gender;
    private java.time.LocalDate birthday;
    /** 0未实名 1已实名 2审核中 3驳回 */
    private Integer realnameStatus;
    /** 0普通 1申请中 2已认证 3驳回 4封禁 */
    private Integer creatorStatus;
    private String inviteCode;
    private Long inviterId;
    private String deviceId;
    /** 信誉分 0-100 */
    private Integer creditScore;
    /** 0正常 1限制消费 2限制发布 3冻结 */
    private Integer riskStatus;
    /** 会员到期时间，空或已过期表示非会员 */
    private java.time.LocalDateTime vipExpireAt;
    /** 已同意的协议版本 */
    private String agreementVersion;
    private java.time.LocalDateTime agreedAt;
    private java.time.LocalDateTime invitedAt;
    /** 连续登录失败次数 */
    private Integer loginFailCount;
    /** 锁定到期时间 */
    private java.time.LocalDateTime lockUntil;
    /** 令牌版本，自增即让已签发令牌全部失效 */
    private Integer tokenVersion;
    /** 1 表示已注销（保留数据用于对账，不再允许登录） */
    private Integer deletedFlag;

    public boolean isEnabled() {
        return status != null && status == STATUS_ENABLED;
    }

    public boolean isAdmin() {
        return ROLE_ADMIN.equals(role);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
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

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    public Integer getGender() {
        return gender;
    }

    public void setGender(Integer gender) {
        this.gender = gender;
    }

    public java.time.LocalDate getBirthday() {
        return birthday;
    }

    public void setBirthday(java.time.LocalDate birthday) {
        this.birthday = birthday;
    }

    public Integer getRealnameStatus() {
        return realnameStatus;
    }

    public void setRealnameStatus(Integer realnameStatus) {
        this.realnameStatus = realnameStatus;
    }

    public Integer getCreatorStatus() {
        return creatorStatus;
    }

    public void setCreatorStatus(Integer creatorStatus) {
        this.creatorStatus = creatorStatus;
    }

    public String getInviteCode() {
        return inviteCode;
    }

    public void setInviteCode(String inviteCode) {
        this.inviteCode = inviteCode;
    }

    public Long getInviterId() {
        return inviterId;
    }

    public void setInviterId(Long inviterId) {
        this.inviterId = inviterId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public Integer getCreditScore() {
        return creditScore;
    }

    public void setCreditScore(Integer creditScore) {
        this.creditScore = creditScore;
    }

    public Integer getRiskStatus() {
        return riskStatus;
    }

    public void setRiskStatus(Integer riskStatus) {
        this.riskStatus = riskStatus;
    }

    public java.time.LocalDateTime getVipExpireAt() {
        return vipExpireAt;
    }

    public void setVipExpireAt(java.time.LocalDateTime vipExpireAt) {
        this.vipExpireAt = vipExpireAt;
    }

    public String getAgreementVersion() {
        return agreementVersion;
    }

    public void setAgreementVersion(String agreementVersion) {
        this.agreementVersion = agreementVersion;
    }

    public java.time.LocalDateTime getAgreedAt() {
        return agreedAt;
    }

    public void setAgreedAt(java.time.LocalDateTime agreedAt) {
        this.agreedAt = agreedAt;
    }

    public java.time.LocalDateTime getInvitedAt() {
        return invitedAt;
    }

    public void setInvitedAt(java.time.LocalDateTime invitedAt) {
        this.invitedAt = invitedAt;
    }

    public Integer getLoginFailCount() {
        return loginFailCount;
    }

    public void setLoginFailCount(Integer loginFailCount) {
        this.loginFailCount = loginFailCount;
    }

    public java.time.LocalDateTime getLockUntil() {
        return lockUntil;
    }

    public void setLockUntil(java.time.LocalDateTime lockUntil) {
        this.lockUntil = lockUntil;
    }

    public Integer getTokenVersion() {
        return tokenVersion;
    }

    public void setTokenVersion(Integer tokenVersion) {
        this.tokenVersion = tokenVersion;
    }

    public Integer getDeletedFlag() {
        return deletedFlag;
    }

    public void setDeletedFlag(Integer deletedFlag) {
        this.deletedFlag = deletedFlag;
    }

    /** 是否处于登录锁定期 */
    public boolean isLocked() {
        return lockUntil != null && lockUntil.isAfter(java.time.LocalDateTime.now());
    }

    /** 是否已注销 */
    public boolean isDeactivated() {
        return deletedFlag != null && deletedFlag == 1;
    }

    /** 已认证创作者 */
    public boolean isCertifiedCreator() {
        return creatorStatus != null && creatorStatus == 2;
    }

    /** 已完成实名 */
    public boolean isRealnamed() {
        return realnameStatus != null && realnameStatus == 1;
    }

    /** 未成年（有生日时按年份判断） */
    public boolean isMinor() {
        if (birthday == null) {
            return false;
        }
        return birthday.isAfter(java.time.LocalDate.now().minusYears(18));
    }
}
