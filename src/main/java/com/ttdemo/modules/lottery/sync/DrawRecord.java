package com.ttdemo.modules.lottery.sync;

import java.time.LocalDate;

/**
 * 从官方数据源拉取到的一条开奖记录（尚未落库）。
 */
public record DrawRecord(
        String issueNo,
        LocalDate drawDate,
        String red,
        String blue,
        String raw
) {
}
