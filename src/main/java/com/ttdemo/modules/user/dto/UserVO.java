package com.ttdemo.modules.user.dto;

import com.ttdemo.modules.user.entity.SysUser;

import java.time.LocalDateTime;

public record UserVO(
        Long id,
        String phone,
        String nickname,
        String avatar,
        String role,
        Integer status,
        Integer realnameStatus,
        Integer creatorStatus,
        Integer riskStatus,
        Integer creditScore,
        /** 是否已设置登录密码：验证码自动注册的账号为 false，前端据此展示"设置密码"而非"修改密码" */
        boolean hasPassword,
        /** 会员到期时间，null 或已过期即非会员 */
        LocalDateTime vipExpireAt,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt
) {

    public static UserVO from(SysUser user) {
        if (user == null) {
            return null;
        }
        return new UserVO(user.getId(), user.getPhone(), user.getNickname(), user.getAvatar(),
                user.getRole(), user.getStatus(),
                user.getRealnameStatus() == null ? 0 : user.getRealnameStatus(),
                user.getCreatorStatus() == null ? 0 : user.getCreatorStatus(),
                user.getRiskStatus() == null ? 0 : user.getRiskStatus(),
                user.getCreditScore() == null ? 100 : user.getCreditScore(),
                user.getPassword() != null && !user.getPassword().isBlank(),
                user.getVipExpireAt(),
                user.getLastLoginAt(), user.getCreatedAt());
    }
}
