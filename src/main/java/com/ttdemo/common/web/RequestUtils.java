package com.ttdemo.common.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 从请求中提取客户端信息（兼容常见反向代理头）
 */
public final class RequestUtils {

    private static final String UNKNOWN = "unknown";
    private static final int MAX_IP_LENGTH = 64;
    private static final int MAX_UA_LENGTH = 255;

    private RequestUtils() {
    }

    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        for (String header : new String[]{"X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP"}) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank() && !UNKNOWN.equalsIgnoreCase(value)) {
                int comma = value.indexOf(',');
                String ip = comma > 0 ? value.substring(0, comma) : value;
                return truncate(ip.trim(), MAX_IP_LENGTH);
            }
        }
        return truncate(request.getRemoteAddr(), MAX_IP_LENGTH);
    }

    public static String getUserAgent(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        return truncate(request.getHeader("User-Agent"), MAX_UA_LENGTH);
    }

    /** 从当前线程绑定的请求中取客户端 IP（Service 层使用） */
    public static String currentClientIp() {
        org.springframework.web.context.request.RequestAttributes attrs =
                org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attrs instanceof org.springframework.web.context.request.ServletRequestAttributes servletAttrs) {
            return getClientIp(servletAttrs.getRequest());
        }
        return null;
    }

    /** 从当前请求取指定请求头（Service 层使用） */
    public static String currentHeader(String name) {
        org.springframework.web.context.request.RequestAttributes attrs =
                org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attrs instanceof org.springframework.web.context.request.ServletRequestAttributes servletAttrs) {
            return servletAttrs.getRequest().getHeader(name);
        }
        return null;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
