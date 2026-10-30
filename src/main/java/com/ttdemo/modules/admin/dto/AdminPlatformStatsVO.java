package com.ttdemo.modules.admin.dto;

/**
 * 彩票平台维度统计（后台仪表盘用）
 */
public record AdminPlatformStatsVO(
        long onlinePredictions,
        long pendingAuditPredictions,
        long settledPredictions,
        long totalCreators,
        long pendingRealname,
        long paidOrders,
        long drawnIssues,
        long officialDrawnIssues
) {
}
