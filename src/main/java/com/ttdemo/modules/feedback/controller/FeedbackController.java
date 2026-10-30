package com.ttdemo.modules.feedback.controller;

import com.ttdemo.common.api.PageQuery;
import com.ttdemo.common.api.PageResult;
import com.ttdemo.common.api.R;
import com.ttdemo.modules.feedback.dto.FeedbackCreateRequest;
import com.ttdemo.modules.feedback.dto.FeedbackVO;
import com.ttdemo.modules.feedback.service.FeedbackService;
import com.ttdemo.security.LoginUser;
import com.ttdemo.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端反馈：提交与「我的反馈」
 */
@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping
    public R<Long> create(@Valid @RequestBody FeedbackCreateRequest request) {
        LoginUser user = SecurityUtils.currentUser();
        return R.ok(feedbackService.create(user.id(), user.phone(), request));
    }

    @GetMapping("/my")
    public R<PageResult<FeedbackVO>> myPage(PageQuery pageQuery) {
        return R.ok(feedbackService.myPage(SecurityUtils.currentUserId(), pageQuery));
    }
}
