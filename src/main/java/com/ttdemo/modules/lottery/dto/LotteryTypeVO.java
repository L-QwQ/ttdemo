package com.ttdemo.modules.lottery.dto;

import java.time.LocalTime;

/**
 * 彩种字典视图。playConfig 为 JSON 原文，前端 JSON.parse 后动态渲染号码选择器。
 */
public record LotteryTypeVO(
        Integer id,
        String code,
        String name,
        Integer category,
        String drawWeekdays,
        LocalTime drawTimeOfDay,
        Integer saleEndOffsetMin,
        String playConfig,
        Integer sortNo,
        Integer status
) {
}
