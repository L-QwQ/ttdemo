package com.ttdemo.common.trace;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * 链路追踪 ID 上下文。
 * 网关或前端可通过 X-Trace-Id 透传，缺省时由 TraceIdFilter 生成。
 */
public final class TraceIds {

    public static final String HEADER = "X-Trace-Id";
    private static final String KEY = "traceId";

    private TraceIds() {
    }

    public static String current() {
        String value = MDC.get(KEY);
        return value == null ? "" : value;
    }

    public static void set(String traceId) {
        MDC.put(KEY, traceId);
    }

    public static void clear() {
        MDC.remove(KEY);
    }

    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
