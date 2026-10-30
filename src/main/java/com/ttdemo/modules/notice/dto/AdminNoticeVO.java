package com.ttdemo.modules.notice.dto;

import java.time.LocalDateTime;

/**
 * 后台通知列表项，带接收人数与已读人数
 */
public record AdminNoticeVO(
        Long id,
        String title,
        String summary,
        String type,
        String level,
        String targetType,
        Integer status,
        String publisherName,
        LocalDateTime publishedAt,
        LocalDateTime createdAt,
        long receiverCount,
        long readCount
) {
}
