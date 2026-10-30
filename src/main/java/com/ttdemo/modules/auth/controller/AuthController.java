package com.ttdemo.modules.auth.controller;

import com.ttdemo.common.api.R;
import com.ttdemo.modules.auth.dto.PasswordLoginRequest;
import com.ttdemo.modules.auth.dto.RefreshTokenRequest;
import com.ttdemo.modules.auth.dto.RegisterRequest;
import com.ttdemo.modules.auth.dto.ResetPasswordRequest;
import com.ttdemo.modules.auth.dto.SendCodeRequest;
import com.ttdemo.modules.auth.dto.SmsLoginRequest;
import com.ttdemo.modules.auth.dto.SmsSendResult;
import com.ttdemo.modules.auth.dto.TokenResponse;
import com.ttdemo.modules.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 前台认证接口（全部匿名可访问）
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** 获取短信验证码 */
    @PostMapping("/sms-code")
    public R<SmsSendResult> sendCode(@Valid @RequestBody SendCodeRequest request, HttpServletRequest httpRequest) {
        return R.ok(authService.sendCode(request, httpRequest));
    }

    /** 手机号验证码注册 */
    @PostMapping("/register")
    public R<TokenResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        return R.ok(authService.register(request, httpRequest));
    }

    /** 手机号验证码登录（未注册自动创建） */
    @PostMapping("/login/sms")
    public R<TokenResponse> loginBySms(@Valid @RequestBody SmsLoginRequest request, HttpServletRequest httpRequest) {
        return R.ok(authService.loginBySms(request, httpRequest));
    }

    /** 手机号 + 密码登录 */
    @PostMapping("/login/password")
    public R<TokenResponse> loginByPassword(@Valid @RequestBody PasswordLoginRequest request,
                                            HttpServletRequest httpRequest) {
        return R.ok(authService.loginByPassword(request, false, httpRequest));
    }

    /** 刷新令牌 */
    @PostMapping("/refresh")
    public R<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return R.ok(authService.refresh(request));
    }

    /**
     * 退出登录：JWT 无状态，客户端清除令牌即可。
     * 如需服务端强制失效，可在过滤器中接入令牌黑名单（Redis）。
     */
    @PostMapping("/logout")
    public R<Void> logout() {
        return R.ok();
    }

    /** 验证码重置密码 */
    @PostMapping("/password/reset")
    public R<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request, HttpServletRequest httpRequest) {
        authService.resetPassword(request, httpRequest);
        return R.ok();
    }
}
