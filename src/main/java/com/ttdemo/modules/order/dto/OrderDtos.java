package com.ttdemo.modules.order.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.Map;

public final class OrderDtos {

    private OrderDtos() {
    }

    public record UnlockRequest(@NotNull(message = "预测 ID 不能为空") Long predictionId) {
    }

    public record UnlockResult(
            String orderNo,
            Long predictionId,
            Integer pricePoints,
            long balanceAfter,
            Integer status
    ) {
    }

    public record OrderVO(
            String orderNo,
            Long predictionId,
            String predictionTitle,
            String typeCode,
            String issueNo,
            Integer pricePoints,
            Integer creatorIncome,
            Integer status,
            String statusName,
            LocalDateTime payTime,
            LocalDateTime createdAt
    ) {
    }

    public record RechargeRequest(
            @NotNull(message = "充值金额不能为空") Integer amountFen,
            Integer payChannel
    ) {
    }

    public record RechargeResult(
            String orderNo,
            Integer amountFen,
            Long points,
            Long bonusPoints,
            Integer payChannel,
            Map<String, String> payParams,
            String mockPayUrl
    ) {
    }

    public record RechargeStatus(
            String orderNo,
            Integer amountFen,
            Long points,
            Long bonusPoints,
            Integer status,
            String statusName,
            LocalDateTime payTime
    ) {
    }
}
