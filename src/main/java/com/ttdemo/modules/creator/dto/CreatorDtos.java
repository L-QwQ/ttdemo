package com.ttdemo.modules.creator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 创作者模块 DTO 聚合，减少散落的小文件。
 */
public final class CreatorDtos {

    private CreatorDtos() {
    }

    public record RealnameRequest(
            @NotBlank(message = "真实姓名不能为空")
            @Size(min = 2, max = 20, message = "姓名长度 2-20") String realName,
            @NotBlank(message = "身份证号不能为空") String idCardNo
    ) {
    }

    public record ApplyRequest(
            @NotBlank(message = "笔名不能为空")
            @Size(min = 2, max = 16, message = "笔名长度 2-16") String penName,
            @Size(max = 500, message = "简介最多 500 字") String intro,
            @Size(max = 255, message = "标签最多 255 字") String tags,
            @Size(max = 500, message = "申请理由最多 500 字") String applyReason
    ) {
    }

    public record ProfileUpdateRequest(
            @Size(min = 2, max = 16, message = "笔名长度 2-16") String penName,
            @Size(max = 500) String intro,
            @Size(max = 255) String tags
    ) {
    }

    public record AuditRequest(
            Integer result,
            @Size(max = 200) String remark
    ) {
    }

    public record ProfileVO(
            Long id,
            Long userId,
            String penName,
            String intro,
            String tags,
            Integer status,
            String statusName,
            Integer creatorLevel,
            BigDecimal platformFeeRate,
            Integer favCount,
            Long totalIncome,
            Integer totalPredictions,
            BigDecimal accuracyScore,
            Integer sampleSize,
            Integer currentHitStreak,
            String avatar,
            LocalDateTime certifiedAt,
            String auditRemark
    ) {
    }

    public record DashboardVO(
            int totalPredictions,
            int onlineCount,
            int settledCount,
            int hitCount,
            BigDecimal hitRate,
            long totalIncome,
            long monthIncome,
            long totalBuyer,
            long totalView,
            BigDecimal accuracyScore,
            int sampleSize,
            int currentHitStreak
    ) {
    }

    public record AccuracyVO(
            String typeCode,
            BigDecimal score,
            int sampleSize,
            BigDecimal rawHitRate,
            BigDecimal weightedHitRate,
            BigDecimal wilsonLower,
            int currentHitStreak,
            int maxHitStreak,
            BigDecimal recent10Rate,
            String display,
            String calcNote
    ) {
    }

    public record CreatorHomeVO(
            Long creatorId,
            String penName,
            String avatar,
            String intro,
            String tags,
            Integer level,
            int favCount,
            boolean followed,
            BigDecimal accuracyScore,
            int sampleSize,
            int currentHitStreak,
            long totalBuyer,
            long totalIncome,
            LocalDateTime certifiedAt
    ) {
    }
}
