package com.ttdemo.modules.lottery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDateTime;

/**
 * 后台新增/编辑期次
 */
public record IssueSaveRequest(
        @NotBlank(message = "彩种不能为空") String typeCode,
        @NotBlank(message = "期号不能为空")
        @Pattern(regexp = "^\\d{5,12}$", message = "期号应为 5-12 位数字") String issueNo,
        @NotNull(message = "开奖时间不能为空") LocalDateTime drawTime,
        LocalDateTime saleEndTime,
        Integer saleEndOffsetMin
) {
}
