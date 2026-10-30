package com.ttdemo.modules.feedback.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 提交反馈
 */
public record FeedbackCreateRequest(
        @NotBlank(message = "请选择反馈类型") String type,

        @NotBlank(message = "标题不能为空")
        @Size(max = 100, message = "标题不能超过 100 字") String title,

        @NotBlank(message = "反馈内容不能为空")
        @Size(min = 5, max = 2000, message = "反馈内容需在 5-2000 字之间") String content,

        @Size(max = 100, message = "联系方式不能超过 100 字") String contact
) {
}
