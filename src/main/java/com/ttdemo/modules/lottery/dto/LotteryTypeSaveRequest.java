package com.ttdemo.modules.lottery.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalTime;

public record LotteryTypeSaveRequest(
        @NotBlank(message = "彩种编码不能为空") String code,
        @NotBlank(message = "彩种名称不能为空") String name,
        Integer category,
        String drawWeekdays,
        LocalTime drawTimeOfDay,
        Integer saleEndOffsetMin,
        @NotBlank(message = "玩法配置不能为空") String playConfig,
        Integer sortNo,
        Integer status
) {
}
