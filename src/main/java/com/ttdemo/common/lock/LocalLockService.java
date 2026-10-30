package com.ttdemo.common.lock;

import com.ttdemo.common.api.ResultCode;
import com.ttdemo.common.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 单机版锁实现：基于 key 的原子占位，带过期时间防死锁。
 * <p>
 * 多实例部署时新增一个基于 Redis SETNX + TTL 的实现并标 @Primary 即可，
 * 对外接口与语义保持一致（拿不到锁立即失败，不排队）。
 */
@Service
public class LocalLockService implements LockService {

    private static final Logger log = LoggerFactory.getLogger(LocalLockService.class);

    private record Holder(String owner, Instant expireAt) {
    }

    private final Map<String, Holder> locks = new ConcurrentHashMap<>();

    @Override
    public <T> T tryWithLock(String key, Duration ttl, Supplier<T> action) {
        if (!acquire(key, ttl)) {
            log.debug("锁已被占用 key={}", key);
            return null;
        }
        try {
            return action.get();
        } finally {
            release(key);
        }
    }

    @Override
    public void runWithLock(String key, Duration ttl, Runnable action) {
        if (!acquire(key, ttl)) {
            throw new BizException(ResultCode.STATE_CONFLICT, "操作正在进行中，请稍后再试");
        }
        try {
            action.run();
        } finally {
            release(key);
        }
    }

    private boolean acquire(String key, Duration ttl) {
        Instant now = Instant.now();
        String owner = Thread.currentThread().getName() + "-" + now.toEpochMilli();
        Holder created = new Holder(owner, now.plus(ttl));
        Holder existing = locks.putIfAbsent(key, created);
        if (existing == null) {
            return true;
        }
        // 已过期则抢占
        if (existing.expireAt().isBefore(now)) {
            if (locks.replace(key, existing, created)) {
                log.warn("锁已过期，被抢占 key={}", key);
                return true;
            }
        }
        return false;
    }

    private void release(String key) {
        locks.remove(key);
    }

    /** 供运维排查：当前持有的锁数量 */
    public int size() {
        locks.entrySet().removeIf(e -> e.getValue().expireAt().isBefore(Instant.now()));
        return locks.size();
    }
}
