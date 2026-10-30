package com.ttdemo.modules.user.controller;

import com.ttdemo.common.api.R;
import com.ttdemo.modules.user.dto.UpdateProfileRequest;
import com.ttdemo.modules.user.dto.UserVO;
import com.ttdemo.modules.user.service.AvatarStorageService;
import com.ttdemo.modules.user.service.UserService;
import com.ttdemo.security.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 前台用户中心（需登录）
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;
    private final AvatarStorageService avatarStorageService;

    public UserController(UserService userService, AvatarStorageService avatarStorageService) {
        this.userService = userService;
        this.avatarStorageService = avatarStorageService;
    }

    /** 当前登录用户信息 */
    @GetMapping("/me")
    public R<UserVO> me() {
        return R.ok(userService.getProfile(SecurityUtils.currentUserId()));
    }

    /** 修改昵称/头像 */
    @PutMapping("/me")
    public R<UserVO> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return R.ok(userService.updateProfile(SecurityUtils.currentUserId(), request));
    }

    /** 上传头像（multipart），成功后直接返回最新用户信息 */
    @PostMapping("/avatar")
    public R<UserVO> uploadAvatar(@RequestParam("file") MultipartFile file) {
        Long userId = SecurityUtils.currentUserId();
        String url = avatarStorageService.store(userId, file);
        return R.ok(userService.updateProfile(userId, new UpdateProfileRequest(null, url)));
    }

    /** 修改密码（需原密码） */
    @PutMapping("/password")
    public R<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(SecurityUtils.currentUserId(), request.oldPassword(), request.newPassword());
        return R.ok();
    }

    /** 首次设置密码：验证码自动注册的账号没有原密码，凭手机验证码设置 */
    @PutMapping("/password/init")
    public R<Void> initPassword(@Valid @RequestBody InitPasswordRequest request) {
        userService.initPassword(SecurityUtils.currentUserId(), request.code(), request.newPassword());
        return R.ok();
    }

    /** 申请注销账号：剩余积分按充值比例折算登记退款，等待客服处理 */
    @PostMapping("/deactivate")
    public R<Map<String, Object>> deactivate(@RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? null : body.get("reason");
        Long id = userService.applyDeactivation(SecurityUtils.currentUserId(), reason);
        return R.ok(Map.of("id", id));
    }

    /** 我的注销申请状态 */
    @GetMapping("/deactivate")
    public R<Map<String, Object>> deactivation() {
        return R.ok(userService.myDeactivation(SecurityUtils.currentUserId()));
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "原密码不能为空") String oldPassword,
            @NotBlank(message = "新密码不能为空")
            @Size(min = 6, max = 32, message = "密码长度需在 6-32 位之间") String newPassword
    ) {
    }

    public record InitPasswordRequest(
            @NotBlank(message = "验证码不能为空")
            @Pattern(regexp = "^\\d{4,8}$", message = "验证码格式不正确") String code,
            @NotBlank(message = "新密码不能为空")
            @Size(min = 6, max = 32, message = "密码长度需在 6-32 位之间") String newPassword
    ) {
    }
}
