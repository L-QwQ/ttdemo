package com.ttdemo.modules.admin.controller;

import com.ttdemo.common.api.PageResult;
import com.ttdemo.common.api.R;
import com.ttdemo.modules.admin.dto.AdminUserQuery;
import com.ttdemo.modules.admin.service.AdminUserService;
import com.ttdemo.modules.user.dto.UserVO;
import com.ttdemo.security.SecurityUtils;
import com.ttdemo.security.perm.Permissions;
import com.ttdemo.security.perm.RequirePerm;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台用户管理（/api/admin/** 已被 SecurityConfig 限定为 ROLE_ADMIN）
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    @RequirePerm(Permissions.USER_VIEW)
    public R<PageResult<UserVO>> page(AdminUserQuery query) {
        return R.ok(adminUserService.page(query));
    }

    @GetMapping("/{id}")
    @RequirePerm(Permissions.USER_VIEW)
    public R<UserVO> detail(@PathVariable Long id) {
        return R.ok(adminUserService.detail(id));
    }

    @PutMapping("/{id}/status")
    @RequirePerm(Permissions.USER_MANAGE)
    public R<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateStatusRequest request) {
        adminUserService.updateStatus(SecurityUtils.currentUserId(), id, request.status());
        return R.ok();
    }

    @PutMapping("/{id}/role")
    @RequirePerm(Permissions.ROLE_GRANT)
    public R<Void> updateRole(@PathVariable Long id, @Valid @RequestBody UpdateRoleRequest request) {
        adminUserService.updateRole(SecurityUtils.currentUserId(), id, request.role());
        return R.ok();
    }

    @PutMapping("/{id}/password")
    @RequirePerm(Permissions.USER_MANAGE)
    public R<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest request) {
        adminUserService.resetPassword(id, request.newPassword());
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @RequirePerm(Permissions.USER_MANAGE)
    public R<Void> delete(@PathVariable Long id) {
        adminUserService.delete(SecurityUtils.currentUserId(), id);
        return R.ok();
    }

    public record UpdateStatusRequest(
            @NotNull(message = "状态不能为空")
            @Min(value = 0, message = "状态值只能是 0 或 1")
            @Max(value = 1, message = "状态值只能是 0 或 1") Integer status
    ) {
    }

    public record UpdateRoleRequest(
            @NotBlank(message = "角色不能为空") String role
    ) {
    }

    public record ResetPasswordRequest(
            @NotBlank(message = "新密码不能为空")
            @Size(min = 6, max = 32, message = "密码长度需在 6-32 位之间") String newPassword
    ) {
    }
}
