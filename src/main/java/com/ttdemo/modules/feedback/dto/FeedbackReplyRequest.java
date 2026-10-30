package com.ttdemo.modules.feedback.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 管理员回复反馈并流转状态
 */
public record FeedbackReplyRequest(
        @NotBlank(message = "回复内容不能为空")
        @Size(max = 2000, message = "回复内容不能超过 2000 字") String reply,

        @NotNull(message = "请选择处理后的状态")
        @Min(value = 0, message = "状态取值不合法")
        @Max(value = 3, message = "状态取值不合法") Integer status
) {
}
