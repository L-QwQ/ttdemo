package com.ttdemo.modules.feedback.dto;

import com.ttdemo.modules.feedback.entity.SysFeedback;

import java.time.LocalDateTime;

/**
 * 反馈视图（用户端只返回自己的，管理端返回全部）
 */
public record FeedbackVO(
        Long id,
        Long userId,
        String userPhone,
        String type,
        String title,
        String content,
        String contact,
        Integer status,
        String reply,
        LocalDateTime repliedAt,
        LocalDateTime createdAt
) {

    public static FeedbackVO from(SysFeedback entity) {
        if (entity == null) {
            return null;
        }
        return new FeedbackVO(entity.getId(), entity.getUserId(), entity.getUserPhone(), entity.getType(),
                entity.getTitle(), entity.getContent(), entity.getContact(), entity.getStatus(),
                entity.getReply(), entity.getRepliedAt(), entity.getCreatedAt());
    }
}
