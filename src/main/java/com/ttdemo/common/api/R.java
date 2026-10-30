package com.ttdemo.common.api;

import com.ttdemo.common.trace.TraceIds;

/**
 * 统一响应结果（对齐需求文档 7.1.2：code=0 成功，附 traceId）
 *
 * @param code    业务状态码，0 表示成功
 * @param message 提示信息
 * @param data    业务数据
 * @param traceId 链路追踪 ID
 */
public record R<T>(int code, String message, T data, String traceId) {

    public static <T> R<T> ok(T data) {
        return new R<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data, TraceIds.current());
    }

    public static R<Void> ok() {
        return new R<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null, TraceIds.current());
    }

    public static <T> R<T> fail(ResultCode resultCode) {
        return new R<>(resultCode.getCode(), resultCode.getMessage(), null, TraceIds.current());
    }

    public static <T> R<T> fail(ResultCode resultCode, String message) {
        return new R<>(resultCode.getCode(), message, null, TraceIds.current());
    }

    public static <T> R<T> fail(int code, String message) {
        return new R<>(code, message, null, TraceIds.current());
    }
}
