package com.ttdemo.modules.admin.controller;

import com.ttdemo.common.api.R;
import com.ttdemo.modules.auth.dto.PasswordLoginRequest;
import com.ttdemo.modules.auth.dto.TokenResponse;
import com.ttdemo.modules.auth.service.AuthService;
import com.ttdemo.modules.user.dto.UserVO;
import com.ttdemo.modules.user.service.UserService;
import com.ttdemo.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台登录入口
 */
@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final AuthService authService;
    private final UserService userService;

    public AdminAuthController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    /** 管理员密码登录（仅 role=ADMIN 可登录） */
    @PostMapping("/login")
    public R<TokenResponse> login(@Valid @RequestBody PasswordLoginRequest request, HttpServletRequest httpRequest) {
        return R.ok(authService.loginByPassword(request, true, httpRequest));
    }

    /** 当前管理员信息 */
    @GetMapping("/me")
    public R<UserVO> me() {
        return R.ok(userService.getProfile(SecurityUtils.currentUserId()));
    }

    @PostMapping("/logout")
    public R<Void> logout() {
        return R.ok();
    }
}
