package com.ttdemo.modules.notice.controller;

import com.ttdemo.common.api.PageQuery;
import com.ttdemo.common.api.PageResult;
import com.ttdemo.common.api.R;
import com.ttdemo.modules.notice.dto.MyNoticeVO;
import com.ttdemo.modules.notice.dto.UserNoticeDetailVO;
import com.ttdemo.modules.notice.service.UserNoticeService;
import com.ttdemo.security.SecurityUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端通知中心：只看得到「发布给我」的通知
 */
@RestController
@RequestMapping("/api/notices")
public class NoticeController {

    private final UserNoticeService userNoticeService;

    public NoticeController(UserNoticeService userNoticeService) {
        this.userNoticeService = userNoticeService;
    }

    @GetMapping
    public R<PageResult<MyNoticeVO>> page(PageQuery pageQuery,
                                          @RequestParam(required = false) Boolean unreadOnly,
                                          @RequestParam(required = false) String type) {
        return R.ok(userNoticeService.page(SecurityUtils.currentUserId(), pageQuery, unreadOnly, type));
    }

    @GetMapping("/unread-count")
    public R<Long> unreadCount() {
        return R.ok(userNoticeService.unreadCount(SecurityUtils.currentUserId()));
    }

    @GetMapping("/{id}")
    public R<UserNoticeDetailVO> detail(@PathVariable Long id) {
        return R.ok(userNoticeService.detail(SecurityUtils.currentUserId(), id));
    }

    @PostMapping("/read-all")
    public R<Integer> markAllRead() {
        return R.ok(userNoticeService.markAllRead(SecurityUtils.currentUserId()));
    }
}
