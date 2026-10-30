package com.ttdemo.modules.lottery.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 人工录入开奖号。redNumbers 为主号码（含 3D/排列3 的位号），blueNumbers 为蓝球/后区。
 * reviewTicket 非空表示复核确认；为空则仅生成待复核票据。
 */
public record DrawResultRequest(
        @NotBlank(message = "开奖号码不能为空") String redNumbers,
        String blueNumbers,
        String reviewTicket,
        Boolean force
) {
}
