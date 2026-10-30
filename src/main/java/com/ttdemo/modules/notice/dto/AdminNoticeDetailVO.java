package com.ttdemo.modules.notice.dto;

import com.ttdemo.modules.user.dto.UserVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台通知详情。receivers 仅 SPECIFIC 类型返回，用于草稿编辑回显。
 */
public record AdminNoticeDetailVO(
        Long id,
        String title,
        String content,
        String type,
        String level,
        String targetType,
        Integer status,
        String publisherName,
        LocalDateTime publishedAt,
        LocalDateTime createdAt,
        long receiverCount,
        long readCount,
        List<UserVO> receivers
) {
}
