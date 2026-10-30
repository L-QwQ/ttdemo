package com.ttdemo.modules.auth.service;

import com.ttdemo.modules.auth.dto.PasswordLoginRequest;
import com.ttdemo.modules.auth.dto.RefreshTokenRequest;
import com.ttdemo.modules.auth.dto.RegisterRequest;
import com.ttdemo.modules.auth.dto.ResetPasswordRequest;
import com.ttdemo.modules.auth.dto.SendCodeRequest;
import com.ttdemo.modules.auth.dto.SmsLoginRequest;
import com.ttdemo.modules.auth.dto.SmsSendResult;
import com.ttdemo.modules.auth.dto.TokenResponse;
import jakarta.servlet.http.HttpServletRequest;

public interface AuthService {

    SmsSendResult sendCode(SendCodeRequest request, HttpServletRequest httpRequest);

    /** 手机号 + 验证码注册 */
    TokenResponse register(RegisterRequest request, HttpServletRequest httpRequest);

    /** 验证码登录，手机号未注册时自动创建账号 */
    TokenResponse loginBySms(SmsLoginRequest request, HttpServletRequest httpRequest);

    /** 密码登录；requireAdmin=true 时仅允许管理员 */
    TokenResponse loginByPassword(PasswordLoginRequest request, boolean requireAdmin, HttpServletRequest httpRequest);

    /** 用 refreshToken 换取新的双令牌（令牌轮换） */
    TokenResponse refresh(RefreshTokenRequest request);

    /** 验证码重置密码 */
    void resetPassword(ResetPasswordRequest request, HttpServletRequest httpRequest);
}
