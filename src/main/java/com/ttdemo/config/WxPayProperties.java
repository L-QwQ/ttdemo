package com.ttdemo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 微信支付 v3 配置。留空则视为未接入，仅 mock 通道可用。
 */
@ConfigurationProperties(prefix = "app.pay.wechat")
public class WxPayProperties {

    /** 公众号/小程序 AppID */
    private String appid = "";
    /** 商户号 */
    private String mchId = "";
    /** 商户 API 证书序列号 */
    private String serialNo = "";
    /** 商户 API 私钥（PKCS8 PEM）文件路径 */
    private String privateKeyPath = "";
    /** APIv3 密钥（32 字节），用于回调报文解密 */
    private String apiV3Key = "";
    /** 微信支付公钥（或平台证书公钥 PEM）文件路径，用于验签 */
    private String platformPublicKeyPath = "";
    /** 回调地址，必须是 https 且公网可达 */
    private String notifyUrl = "";
    /** 接口基址 */
    private String baseUrl = "https://api.mch.weixin.qq.com";

    public boolean isConfigured() {
        return notBlank(appid) && notBlank(mchId) && notBlank(serialNo)
                && notBlank(privateKeyPath) && notBlank(apiV3Key) && notBlank(notifyUrl);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    public String getAppid() {
        return appid;
    }

    public void setAppid(String appid) {
        this.appid = appid;
    }

    public String getMchId() {
        return mchId;
    }

    public void setMchId(String mchId) {
        this.mchId = mchId;
    }

    public String getSerialNo() {
        return serialNo;
    }

    public void setSerialNo(String serialNo) {
        this.serialNo = serialNo;
    }

    public String getPrivateKeyPath() {
        return privateKeyPath;
    }

    public void setPrivateKeyPath(String privateKeyPath) {
        this.privateKeyPath = privateKeyPath;
    }

    public String getApiV3Key() {
        return apiV3Key;
    }

    public void setApiV3Key(String apiV3Key) {
        this.apiV3Key = apiV3Key;
    }

    public String getPlatformPublicKeyPath() {
        return platformPublicKeyPath;
    }

    public void setPlatformPublicKeyPath(String platformPublicKeyPath) {
        this.platformPublicKeyPath = platformPublicKeyPath;
    }

    public String getNotifyUrl() {
        return notifyUrl;
    }

    public void setNotifyUrl(String notifyUrl) {
        this.notifyUrl = notifyUrl;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}
