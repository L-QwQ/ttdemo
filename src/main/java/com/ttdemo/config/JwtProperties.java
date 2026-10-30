package com.ttdemo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /** HS256 密钥，生产环境必须通过环境变量 APP_JWT_SECRET 覆盖，长度 >= 32 字节 */
    private String secret = "ttdemo-default-dev-secret-change-me-in-production";
    private String issuer = "ttdemo";
    /** 访问令牌有效期 */
    private Duration accessTokenTtl = Duration.ofMinutes(30);
    /** 刷新令牌有效期 */
    private Duration refreshTokenTtl = Duration.ofDays(7);

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public void setAccessTokenTtl(Duration accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
    }

    public Duration getRefreshTokenTtl() {
        return refreshTokenTtl;
    }

    public void setRefreshTokenTtl(Duration refreshTokenTtl) {
        this.refreshTokenTtl = refreshTokenTtl;
    }
}
