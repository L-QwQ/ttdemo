package com.ttdemo.modules.notice.controller;

import com.ttdemo.common.api.PageResult;
import com.ttdemo.common.api.R;
import com.ttdemo.modules.notice.dto.AdminNoticeDetailVO;
import com.ttdemo.modules.notice.dto.AdminNoticeQuery;
import com.ttdemo.modules.notice.dto.AdminNoticeVO;
import com.ttdemo.modules.notice.dto.NoticeSaveRequest;
import com.ttdemo.modules.notice.service.AdminNoticeService;
import com.ttdemo.security.LoginUser;
import com.ttdemo.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ttdemo.security.perm.Permissions;
import com.ttdemo.security.perm.RequirePerm;

/**
 * 后台通知公告管理（/api/admin/** 已限定 ROLE_ADMIN）
 */
@RestController
@RequestMapping("/api/admin/notices")
public class AdminNoticeController {

    private final AdminNoticeService adminNoticeService;

    public AdminNoticeController(AdminNoticeService adminNoticeService) {
        this.adminNoticeService = adminNoticeService;
    }

    @GetMapping

    @RequirePerm(Permissions.NOTICE_MANAGE)    public R<PageResult<AdminNoticeVO>> page(AdminNoticeQuery query) {
        return R.ok(adminNoticeService.page(query));
    }

    @GetMapping("/{id}")
    @RequirePerm(Permissions.NOTICE_MANAGE)
    public R<AdminNoticeDetailVO> detail(@PathVariable Long id) {
        return R.ok(adminNoticeService.detail(id));
    }

    @PostMapping

    @RequirePerm(Permissions.NOTICE_MANAGE)    public R<Long> create(@Valid @RequestBody NoticeSaveRequest request) {
        LoginUser operator = SecurityUtils.currentUser();
        return R.ok(adminNoticeService.create(operator.id(), displayName(operator), request));
    }

    @PutMapping("/{id}")
    @RequirePerm(Permissions.NOTICE_MANAGE)
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody NoticeSaveRequest request) {
        adminNoticeService.update(id, request);
        return R.ok();
    }

    @PostMapping("/{id}/publish")
    @RequirePerm(Permissions.NOTICE_MANAGE)
    public R<Void> publish(@PathVariable Long id) {
        adminNoticeService.publish(id);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @RequirePerm(Permissions.NOTICE_MANAGE)
    public R<Void> delete(@PathVariable Long id) {
        adminNoticeService.delete(id);
        return R.ok();
    }

    private String displayName(LoginUser user) {
        return StringUtils.hasText(user.nickname()) ? user.nickname() : user.phone();
    }
}
