package com.ttdemo.modules.message.controller;

import com.ttdemo.common.api.PageResult;
import com.ttdemo.common.api.R;
import com.ttdemo.modules.message.entity.NotMessage;
import com.ttdemo.modules.message.service.MessageService;
import com.ttdemo.security.SecurityUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping
    public R<PageResult<NotMessage>> list(@RequestParam(defaultValue = "1") long pageNum,
                                          @RequestParam(defaultValue = "10") long pageSize,
                                          @RequestParam(required = false) String msgType) {
        return R.ok(messageService.list(SecurityUtils.currentUserId(), pageNum, pageSize, msgType));
    }

    @GetMapping("/unread-count")
    public R<Map<String, Long>> unreadCount() {
        return R.ok(Map.of("count", messageService.unreadCount(SecurityUtils.currentUserId())));
    }

    /** 未读数按类型分组：total + TRADE/AUDIT/SETTLE/SYSTEM */
    @GetMapping("/unread-stats")
    public R<Map<String, Long>> unreadStats() {
        return R.ok(messageService.unreadStats(SecurityUtils.currentUserId()));
    }

    @PostMapping("/read")
    public R<Void> markRead(@RequestBody Map<String, List<Long>> body) {
        messageService.markRead(SecurityUtils.currentUserId(), body.get("ids"));
        return R.ok();
    }

    @PostMapping("/read-all")
    public R<Void> markAllRead() {
        messageService.markAllRead(SecurityUtils.currentUserId());
        return R.ok();
    }
}
