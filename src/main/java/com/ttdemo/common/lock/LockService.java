package com.ttdemo.common.lock;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 分布式锁抽象。单机部署用本地实现即可；多实例部署时提供 Redis 实现替换本 Bean，
 * 业务代码无需改动。
 */
public interface LockService {

    /**
     * 在锁内执行任务，拿不到锁时立即返回 null（不阻塞）。
     *
     * @param key 锁键，建议带业务前缀，如 "settle:issue:123"
     */
    <T> T tryWithLock(String key, Duration ttl, Supplier<T> action);

    /**
     * 在锁内执行任务，拿不到锁时抛 409 状态冲突。
     */
    void runWithLock(String key, Duration ttl, Runnable action);
}
