package com.ttdemo.modules.lottery.dto;

import java.time.LocalDateTime;

public record IssueVO(
        Long id,
        String typeCode,
        String issueNo,
        LocalDateTime drawTime,
        LocalDateTime saleEndTime,
        String redNumbers,
        String blueNumbers,
        String extra,
        Integer drawStatus,
        String drawStatusName,
        Integer settleStatus,
        boolean saleEnded,
        long remainSeconds,
        Integer dataSource
) {
}
