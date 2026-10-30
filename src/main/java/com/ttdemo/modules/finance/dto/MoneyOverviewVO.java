package com.ttdemo.modules.finance.dto;

import java.util.List;

/**
 * 真实资金总览。金额单位统一为「分」，前端按币种格式化。
 * cashOutFen 当前恒为 0——平台不提供积分兑现/提现，没有任何现金流出通道。
 */
public record MoneyOverviewVO(
        String currency,
        /** 1 元兑换多少积分（财务折算基准） */
        long pointRate,
        /** 充值实收（真实现金流入） */
        long cashInFen,
        /** 现金流出（恒 0：无提现通道） */
        long cashOutFen,
        long rechargeCount,
        long rechargePoints,
        /** 预测消费积分 */
        long consumePoints,
        long consumeFen,
        long consumeCount,
        /** 平台抽成（积分 / 折算金额） */
        long platformFeePoints,
        long platformFeeFen,
        /** 创作者收益（积分 / 折算金额） */
        long creatorIncomePoints,
        long creatorIncomeFen,
        /** 已入账结算单口径 */
        long settleGrossPoints,
        long settleGrossFen,
        long settleCount,
        /** 权益兑换（积分 / 平台折算成本 / 笔数） */
        long exchangePoints,
        long exchangeFen,
        long exchangeCount,
        /** 待发放权益单数量 */
        long pendingRewardCount,
        /** 平台净入 = 充值实收 - 权益成本 */
        long netPlatformFen,
        /** 用户未消费积分（预付负债） */
        long pointLiabilityPoints,
        long pointLiabilityFen,
        long accountCount,
        String note,
        List<DailyVO> trend
) {

    public record DailyVO(String day, long rechargeFen, long exchangeFen, long netFen, long exchangePoints) {
    }
}
