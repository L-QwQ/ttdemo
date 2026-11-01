package com.ttdemo.common.util;

import java.util.regex.Pattern;

/**
 * 富文本白名单清洗：移除脚本、事件属性、javascript: 协议与危险标签内容。
 * 只做服务端兜底，前端仍需二次过滤。
 */
public final class HtmlSanitizer {

    private static final Pattern DANGEROUS_TAGS = Pattern.compile(
            "(?is)<\\s*(script|style|iframe|object|embed|form|input|link|meta|svg|math)[^>]*>.*?<\\s*/\\s*\\1\\s*>");
    private static final Pattern SELF_CLOSING_DANGEROUS = Pattern.compile(
            "(?i)<\\s*(script|style|iframe|object|embed|link|meta|svg|math)[^>]*/?\\s*>");
    /**
     * 事件属性：注意 <svg/onload=x> 这种用「/」而不是空格分隔的写法，
     * 只匹配 \s+ 会漏掉，属性分隔符要把 / 也算进来
     */
    private static final Pattern EVENT_ATTR = Pattern.compile(
            "(?i)[\\s/]on[a-z]+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)");
    /** 伪协议：逐个字符允许空白，覆盖 java\tscript: 、jav&#x09;ascript: 这类绕过写法 */
    private static final Pattern JS_PROTOCOL = Pattern.compile(
            "(?i)(href|src|xlink:href)\\s*=\\s*(\"|')?\\s*(?:j\\s*a\\s*v\\s*a\\s*s\\s*c\\s*r\\s*i\\s*p\\s*t"
                    + "|v\\s*b\\s*s\\s*c\\s*r\\s*i\\s*p\\s*t)\\s*:");
    /** data:text/html 可承载脚本，data:image 保留（正文里常有 base64 图） */
    private static final Pattern DATA_HTML = Pattern.compile(
            "(?i)(href|src)\\s*=\\s*(\"|')?\\s*data\\s*:\\s*text/html");
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
        result = DATA_HTML.matcher(result).replaceAll("$1=\"#\"");
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
