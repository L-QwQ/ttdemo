package com.ttdemo.common.util;

import java.util.regex.Pattern;

/**
 * 富文本白名单清洗：移除脚本、事件属性、javascript: 协议与危险标签内容。
 * 只做服务端兜底，前端仍需二次过滤。
 */
public final class HtmlSanitizer {

    private static final Pattern DANGEROUS_TAGS = Pattern.compile(
            "(?is)<\\s*(script|style|iframe|object|embed|form|input|link|meta)[^>]*>.*?<\\s*/\\s*\\1\\s*>");
    private static final Pattern SELF_CLOSING_DANGEROUS = Pattern.compile(
            "(?i)<\\s*(script|style|iframe|object|embed|link|meta)[^>]*/?\\s*>");
    private static final Pattern EVENT_ATTR = Pattern.compile(
            "(?i)\\s+on[a-z]+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)");
    private static final Pattern JS_PROTOCOL = Pattern.compile(
            "(?i)(href|src)\\s*=\\s*(\"|')?\\s*(javascript|data):[^\"'>\\s]*");
    private static final Pattern STYLE_EXPRESSION = Pattern.compile("(?i)expression\\s*\\(");

    private HtmlSanitizer() {
    }

    public static String clean(String html) {
        if (html == null || html.isBlank()) {
            return html;
        }
        String result = DANGEROUS_TAGS.matcher(html).replaceAll("");
        result = SELF_CLOSING_DANGEROUS.matcher(result).replaceAll("");
        result = EVENT_ATTR.matcher(result).replaceAll("");
        result = JS_PROTOCOL.matcher(result).replaceAll("$1=\"#\"");
        result = STYLE_EXPRESSION.matcher(result).replaceAll("");
        return result;
    }

    /** 转义纯文本，用于站内信等非富文本场景 */
    public static String escape(String text) {
        if (text == null) {
            return null;
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
