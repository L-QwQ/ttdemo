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
            Long seed
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
            List<ItemPayload> items
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
