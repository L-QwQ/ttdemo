package com.ttdemo.common.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 业务单号生成：前缀 + 时间戳(yyyyMMddHHmmss) + 4 位序列/随机。
 * 单机唯一；分布式部署需替换为雪花算法。
 */
public final class IdGen {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final AtomicInteger SEQ = new AtomicInteger(ThreadLocalRandom.current().nextInt(1000));

    private IdGen() {
    }

    public static String orderNo() {
        return "O" + stamp();
    }

    public static String predictionNo() {
        return "P" + stamp();
    }

    public static String settleNo() {
        return "S" + stamp();
    }

    public static String txnNo() {
        return "T" + stamp();
    }

    public static String rechargeNo() {
        return "R" + stamp();
    }

    /** 积分兑换单号 */
    public static String rewardNo() {
        return "X" + stamp();
    }

    private static String stamp() {
        int seq = SEQ.updateAndGet(v -> v >= 9999 ? 0 : v + 1);
        return LocalDateTime.now().format(FMT) + String.format("%04d", seq);
    }
}
