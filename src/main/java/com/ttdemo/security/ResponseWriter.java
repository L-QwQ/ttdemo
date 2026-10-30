package com.ttdemo.security;

import com.ttdemo.common.api.ResultCode;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import com.ttdemo.common.trace.TraceIds;

/**
 * 过滤器链内直接输出统一格式 JSON（此处不引入 Jackson，避免序列化器版本差异）
 */
final class ResponseWriter {

    private ResponseWriter() {
    }

    /** 业务错误码 ≠ HTTP 状态码，这里显式指定 HTTP 状态 */
    static void write(HttpServletResponse response, int httpStatus, ResultCode resultCode) throws IOException {
        response.setStatus(httpStatus);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + resultCode.getCode()
                + ",\"message\":\"" + resultCode.getMessage()
                + "\",\"data\":null,\"traceId\":\"" + TraceIds.current() + "\"}");
        response.getWriter().flush();
    }

    static void write(HttpServletResponse response, ResultCode resultCode) throws IOException {
        write(response, 400, resultCode);
    }
}
