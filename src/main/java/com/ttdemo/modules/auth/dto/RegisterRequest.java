package com.ttdemo.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 手机号 + 验证码注册。password 可空，为空则之后只能用验证码登录。
 * agreed 为用户是否勾选《用户协议》《积分规则》《免责声明》，后端按配置版本记录。
 */
public record RegisterRequest(
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
        String phone,

        @NotBlank(message = "验证码不能为空")
        @Pattern(regexp = "^\\d{4,8}$", message = "验证码格式不正确")
        String code,

        @Size(min = 6, max = 32, message = "密码长度需在 6-32 位之间")
        String password,

        @Size(max = 20, message = "昵称最长 20 个字符")
        String nickname,

        /** 是否已勾选协议 */
        Boolean agreed,

        /** 邀请码（可选） */
        @Size(max = 16, message = "邀请码过长")
        String inviteCode
) {
}
