package com.ttdemo.modules.notice.dto;

import com.ttdemo.modules.notice.entity.SysNotice;

import java.time.LocalDateTime;

/**
 * 用户端通知详情
 */
public record UserNoticeDetailVO(
        Long id,
        String title,
        String content,
        String type,
        String level,
        String publisherName,
        LocalDateTime publishedAt,
        LocalDateTime readAt
) {

    public static UserNoticeDetailVO from(SysNotice notice, LocalDateTime readAt) {
        return new UserNoticeDetailVO(notice.getId(), notice.getTitle(), notice.getContent(),
                notice.getType(), notice.getLevel(), notice.getPublisherName(),
                notice.getPublishedAt(), readAt);
    }
}
