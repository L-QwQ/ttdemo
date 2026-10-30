package com.ttdemo.modules.report.dto;

import com.ttdemo.modules.report.entity.SysReport;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class ReportDtos {

    private ReportDtos() {
    }

    public record SubmitRequest(
            @NotNull(message = "举报对象类型不能为空")
            @Min(value = 1, message = "举报对象类型不合法")
            @Max(value = 3, message = "举报对象类型不合法") Integer targetType,
            @NotNull(message = "举报对象不能为空") Long targetId,
            @NotNull(message = "请选择举报原因")
            @Min(value = 1, message = "举报原因不合法")
            @Max(value = 5, message = "举报原因不合法") Integer reasonType,
            @Size(max = 500, message = "补充说明不能超过 500 字") String content,
            @Size(max = 500, message = "证据链接过长") String evidenceUrl
    ) {
    }

    public record HandleRequest(
            @NotNull(message = "处理结果不能为空") Integer status,
            @Size(max = 500, message = "处理说明不能超过 500 字") String handleResult,
            /** 是否同时下架被举报的预测 */
            Boolean offline
    ) {
    }

    public record ReportVO(
            Long id,
            Long reporterId,
            String reporterPhone,
            Integer targetType,
            String targetTypeName,
            Long targetId,
            String targetTitle,
            Integer reasonType,
            String reasonName,
            String content,
            String evidenceUrl,
            Integer status,
            String statusName,
            String handleResult,
            LocalDateTime handleTime,
            LocalDateTime createdAt
    ) {
        public static ReportVO from(SysReport report) {
            return new ReportVO(report.getId(), report.getReporterId(), null,
                    report.getTargetType(), targetTypeName(report.getTargetType()),
                    report.getTargetId(), null,
                    report.getReasonType(), reasonName(report.getReasonType()),
                    report.getContent(), report.getEvidenceUrl(),
                    report.getStatus(), statusName(report.getStatus()),
                    report.getHandleResult(), report.getHandleTime(), report.getCreatedAt());
        }

        public ReportVO withDetail(String phone, String title) {
            return new ReportVO(id, reporterId, phone, targetType, targetTypeName, targetId, title,
                    reasonType, reasonName, content, evidenceUrl, status, statusName,
                    handleResult, handleTime, createdAt);
        }

        public static String targetTypeName(Integer type) {
            if (type == null) {
                return "-";
            }
            return switch (type) {
                case SysReport.TARGET_PREDICTION -> "预测";
                case SysReport.TARGET_CREATOR -> "创作者";
                default -> "用户";
            };
        }

        public static String reasonName(Integer reason) {
            if (reason == null) {
                return "-";
            }
            return switch (reason) {
                case 1 -> "虚假宣传/承诺中奖";
                case 2 -> "诱导赌博";
                case 3 -> "侵犯版权";
                case 4 -> "辱骂或人身攻击";
                default -> "其他违规";
            };
        }

        public static String statusName(Integer status) {
            if (status == null) {
                return "-";
            }
            return switch (status) {
                case SysReport.STATUS_PENDING -> "待处理";
                case SysReport.STATUS_HANDLED -> "已处理";
                default -> "已驳回";
            };
        }
    }
}
