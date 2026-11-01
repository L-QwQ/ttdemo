package com.ttdemo.modules.prediction.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class PredictionDtos {

    private PredictionDtos() {
    }

    public record ItemRequest(
            @NotBlank(message = "预测项类型不能为空") String itemType,
            @NotBlank(message = "预测项值不能为空") @Size(max = 255) String itemValue,
            @Size(max = 128) String itemLabel,
            Boolean isFree,
            BigDecimal weight
    ) {
    }

    public record SaveRequest(
            @NotBlank(message = "彩种不能为空") String typeCode,
            @NotBlank(message = "期号不能为空") String issueNo,
            @NotBlank(message = "标题不能为空")
            @Size(min = 8, max = 40, message = "标题长度 8-40 字") String title,
            @NotBlank(message = "玩法不能为空") String playType,
            @NotBlank(message = "摘要不能为空")
            @Size(min = 20, max = 100, message = "摘要长度 20-100 字") String summary,
            @NotBlank(message = "正文不能为空")
            @Size(min = 100, max = 8000, message = "正文长度 100-8000 字") String contentHtml,
            List<String> contentImages,
            Integer pricePoints,
            @NotEmpty(message = "预测项不能为空") @Size(max = 20, message = "预测项最多 20 个")
            @Valid List<ItemRequest> items
    ) {
    }

    public record AuditRequest(
            Integer result,
            @Size(max = 200) String remark,
            Integer riskLevel
    ) {
    }

    public record OfflineRequest(
            @NotBlank(message = "下架原因不能为空") @Size(min = 2, max = 200) String reason
    ) {
    }

    public record CreatorBrief(
            Long id,
            String penName,
            String avatar,
            BigDecimal accuracyScore,
            int sampleSize,
            int currentHitStreak,
            Integer level
    ) {
    }

    /**
     * 免费可见的预测项。value 只对免费项下发，付费项的号码不出现在任何列表接口里，
     * 前端据此渲染号码球，付费项只给数量。
     */
    public record FreeItem(
            String itemType,
            String label,
            String value
    ) {
    }

    /**
     * 详情页的预测项列表：免费项与已解锁项带 value，付费未解锁项 value 为 null、locked=true，
     * 前端用 count 画出等量的占位球，既说明「这里有几个号」又不泄露号码。
     */
    public record ItemView(
            String itemType,
            String label,
            String value,
            int count,
            boolean free,
            boolean locked
    ) {
    }

    public record PriceBreakdown(
            int base,
            BigDecimal coefficient,
            String text
    ) {
    }

    public record ListItem(
            Long id,
            String predictionNo,
            String title,
            String playType,
            String playTypeName,
            String typeCode,
            String issueNo,
            LocalDateTime saleEndTime,
            Integer pricePoints,
            boolean isFree,
            PriceBreakdown priceBreakdown,
            String summary,
            List<FreeItem> freeItems,
            int itemCount,
            int freeCount,
            CreatorBrief creator,
            int viewCount,
            int buyerCount,
            boolean isPurchased,
            Integer status,
            String statusName,
            BigDecimal hitRate,
            Integer isHit,
            LocalDateTime createdAt
    ) {
    }

    public record Detail(
            Long id,
            String predictionNo,
            String title,
            String playType,
            String playTypeName,
            String typeCode,
            Long issueId,
            String issueNo,
            LocalDateTime saleEndTime,
            long remainSeconds,
            Integer pricePoints,
            boolean isFree,
            PriceBreakdown priceBreakdown,
            String summary,
            List<FreeItem> freeItems,
            List<ItemView> items,
            String contentHtml,
            List<String> contentImages,
            boolean isPurchased,
            boolean isOwner,
            int itemsTotal,
            int itemsFree,
            Integer status,
            String statusName,
            HitInfo hitInfo,
            CreatorBrief creator,
            int viewCount,
            int buyerCount,
            String lockedHint,
            String auditRemark,
            LocalDateTime createdAt
    ) {
    }

    public record HitInfo(
            BigDecimal hitRate,
            Integer isHit,
            int hitCount,
            int itemCount,
            List<ItemHit> items
    ) {
    }

    public record ItemHit(
            String itemType,
            String itemLabel,
            String itemValue,
            Integer hitResult,
            String hitDetail
    ) {
    }

    public record Created(
            Long id,
            String predictionNo,
            Integer status
    ) {
    }

    public record AuditRow(
            Long id,
            String predictionNo,
            String title,
            String typeCode,
            String issueNo,
            String penName,
            Long creatorId,
            Integer status,
            LocalDateTime saleEndTime,
            long remainMinutes,
            Integer pricePoints,
            String summary,
            String submitRemark,
            String riskHit,
            LocalDateTime createdAt
    ) {
    }
}
