package com.ttdemo.modules.feedback.controller;

import com.ttdemo.common.api.PageResult;
import com.ttdemo.common.api.R;
import com.ttdemo.modules.feedback.dto.FeedbackQuery;
import com.ttdemo.modules.feedback.dto.FeedbackReplyRequest;
import com.ttdemo.modules.feedback.dto.FeedbackVO;
import com.ttdemo.modules.feedback.service.AdminFeedbackService;
import com.ttdemo.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ttdemo.security.perm.Permissions;
import com.ttdemo.security.perm.RequirePerm;

/**
 * 后台反馈处理（/api/admin/** 已限定 ROLE_ADMIN）
 */
@RestController
@RequestMapping("/api/admin/feedback")
public class AdminFeedbackController {

    private final AdminFeedbackService adminFeedbackService;

    public AdminFeedbackController(AdminFeedbackService adminFeedbackService) {
        this.adminFeedbackService = adminFeedbackService;
    }

    @GetMapping

    @RequirePerm(Permissions.FEEDBACK_MANAGE)    public R<PageResult<FeedbackVO>> page(FeedbackQuery query) {
        return R.ok(adminFeedbackService.page(query));
    }

    @GetMapping("/{id}")
    @RequirePerm(Permissions.FEEDBACK_MANAGE)
    public R<FeedbackVO> detail(@PathVariable Long id) {
        return R.ok(adminFeedbackService.detail(id));
    }

    @PutMapping("/{id}/reply")
    @RequirePerm(Permissions.FEEDBACK_MANAGE)
    public R<Void> reply(@PathVariable Long id, @Valid @RequestBody FeedbackReplyRequest request) {
        adminFeedbackService.reply(SecurityUtils.currentUserId(), id, request);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @RequirePerm(Permissions.FEEDBACK_MANAGE)
    public R<Void> delete(@PathVariable Long id) {
        adminFeedbackService.delete(id);
        return R.ok();
    }
}
