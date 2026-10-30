package com.ttdemo.modules.lottery.sync;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 访问彩种官方接口的 HTTP 客户端。
 * 代理优先读 app.draw.proxy，其次读环境变量 HTTPS_PROXY（本地开发需要）。
 */
@Component
public class DrawHttpClient {

    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36";

    private final HttpClient client;

    public DrawHttpClient(@Value("${app.draw.proxy:}") String proxyConfig) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(6))
                .followRedirects(HttpClient.Redirect.NORMAL);
        String proxy = resolveProxy(proxyConfig);
        if (proxy != null) {
            try {
                URI uri = URI.create(proxy.contains("://") ? proxy : "http://" + proxy);
                if (uri.getHost() != null && uri.getPort() > 0) {
                    builder.proxy(ProxySelector.of(new InetSocketAddress(uri.getHost(), uri.getPort())));
                }
            } catch (Exception ignored) {
                // 代理串非法则直连
            }
        }
        this.client = builder.build();
    }

    private String resolveProxy(String configured) {
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        for (String key : new String[]{"HTTPS_PROXY", "https_proxy", "HTTP_PROXY", "http_proxy"}) {
            String value = System.getenv(key);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    public String get(String url, String referer) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", UA)
                .header("Referer", referer)
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .GET()
                .build();
        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() / 100 != 2) {
            throw new IOException("HTTP " + response.statusCode());
        }
        return response.body();
    }
}
