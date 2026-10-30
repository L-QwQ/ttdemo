package com.ttdemo.modules.notice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 后台新建/编辑通知公告
 *
 * @param publishNow 为 true 时创建后立即发布；false 保存为草稿
 */
public record NoticeSaveRequest(
        @NotBlank(message = "标题不能为空")
        @Size(max = 100, message = "标题不能超过 100 字") String title,

        @NotBlank(message = "内容不能为空")
        @Size(max = 20000, message = "内容不能超过 20000 字") String content,

        @NotBlank(message = "请选择类型") String type,

        String level,

        @NotBlank(message = "请选择接收范围") String targetType,

        List<Long> targetUserIds,

        Boolean publishNow
) {
}
