package com.ttdemo.modules.admin.dto;

public record AdminStatsVO(
        long totalUsers,
        long todayNewUsers,
        long enabledUsers,
        long disabledUsers,
        long todayLogins
) {
}
