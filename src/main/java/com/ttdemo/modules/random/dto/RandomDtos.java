package com.ttdemo.modules.random.dto;

import java.math.BigDecimal;
import java.util.List;

public final class RandomDtos {

    private RandomDtos() {
    }

    public record MethodVO(
            String code,
            String name,
            String description,
            boolean available,
            String dataNote,
            /** 分组：BASIC / STATS / MYSTIC */
            String group,
            String groupName,
            String groupSubtitle,
            /** 是否会员专享 */
            boolean vip,
            /** 当前用户是否被锁（非会员且今日免费次数已用完） */
            boolean locked,
            /** 会员方法每日免费次数（会员或免费方法为 -1 表示不限） */
            int freeQuota,
            int usedToday,
            int remainToday
    ) {
    }

    /** 当前用户的随机预测使用额度 */
    public record QuotaVO(
            boolean vip,
            boolean gradeEnabled,
            int freeQuota,
            int usedToday,
            int remainToday,
            /** 今日干支与建除神，玄学方法用 */
            String almanac
    ) {
    }

    public record GenerateRequest(
            String typeCode,
            String method,
            Integer count,
            Integer historySize,
            Integer sumMin,
            Integer sumMax,
            Long seed,
            /** 投注方式：SINGLE 单式 / MULTI 复式，缺省单式 */
            String betMode,
            /** 复式主号（红球/前区/号码池）个数 */
            Integer multiMain,
            /** 复式副号（蓝球/后区）个数 */
            Integer multiSub
    ) {
    }

    /**
     * 复式规格：standard 是单式一注的号码个数，min/max 是复式可选的号码个数范围。
     * 位号玩法（福彩3D / 排列3）需要按位分别选号，暂不支持复式，supported=false。
     */
    public record BetGroupSpec(
            String key,
            String name,
            int standard,
            int min,
            int max,
            int defaultValue
    ) {
    }

    /** 复式能力说明，供前端渲染号码个数选择器 */
    public record BetSpec(
            boolean supported,
            String note,
            BigDecimal unitPrice,
            List<BetGroupSpec> groups
    ) {
    }

    /** 本注的投注信息：注数 = 各组组合数之积，金额 = 注数 × 单价（2 元/注） */
    public record BetInfo(
            String mode,
            String modeName,
            int betCount,
            BigDecimal amount,
            BigDecimal unitPrice,
            String formula,
            String label
    ) {
    }

    public record GroupResult(
            String key,
            String name,
            List<String> numbers
    ) {
    }

    public record Metrics(
            int sum,
            int span,
            String oddEven,
            String bigSmall
    ) {
    }

    /** 与发布页 prediction item 结构一致，可直接提交 */
    public record ItemPayload(
            String itemType,
            String itemValue,
            String itemLabel,
            Boolean isFree,
            BigDecimal weight
    ) {
    }

    public record SetResult(
            String method,
            String methodName,
            String reason,
            List<GroupResult> groups,
            Metrics metrics,
            List<ItemPayload> items,
            BetInfo bet
    ) {
    }

    /** 参与计算的历史期次（供用户核对数据是否真实） */
    public record HistoryRow(
            String issueNo,
            String drawTime,
            String redNumbers,
            String blueNumbers,
            Integer dataSource,
            String sourceName
    ) {
    }

    /** 历史数据概况 */
    public record HistorySummary(
            String typeCode,
            String typeName,
            int total,
            int autoSyncCount,
            int manualCount,
            String latestIssue,
            String latestDrawTime,
            List<HistoryRow> list
    ) {
    }

    public record GenerateResult(
            String typeCode,
            String typeName,
            int requestedHistorySize,
            int historySize,
            int autoSyncCount,
            int manualCount,
            String dataNote,
            RandomDtos.Metrics metrics,
            List<SetResult> sets,
            List<HistoryRow> historyRows,
            String disclaimer
    ) {
    }
}
