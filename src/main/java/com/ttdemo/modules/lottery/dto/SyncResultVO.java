package com.ttdemo.modules.lottery.dto;

import java.util.List;

/**
 * 开奖同步结果
 */
public record SyncResultVO(
        String typeCode,
        String typeName,
        int fetched,
        int inserted,
        int updated,
        int skipped,
        int failed,
        String message,
        List<String> errors
) {
}
