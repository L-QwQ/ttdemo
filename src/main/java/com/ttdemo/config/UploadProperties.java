package com.ttdemo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 本地文件存储配置（当前用于头像）
 */
@ConfigurationProperties(prefix = "app.upload")
public class UploadProperties {

    /** 存储根目录，相对路径按启动目录解析 */
    private String dir = "./data/uploads";

    /** URL 前缀，必须与 WebMvcConfig 的资源映射保持一致 */
    private String urlPrefix = "/api/uploads";

    /** 单文件大小上限（字节） */
    private long maxSize = 2 * 1024 * 1024L;

    public String getDir() {
        return dir;
    }

    public void setDir(String dir) {
        this.dir = dir;
    }

    public String getUrlPrefix() {
        return urlPrefix;
    }

    public void setUrlPrefix(String urlPrefix) {
        this.urlPrefix = urlPrefix;
    }

    public long getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(long maxSize) {
        this.maxSize = maxSize;
    }
}
