package com.ttdemo.security;

import com.ttdemo.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 签发与解析。access 令牌携带身份与角色，refresh 令牌只携带用户 id。
 */
@Component
public class JwtTokenProvider {

    public static final String CLAIM_TYPE = "typ";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_PHONE = "phone";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final JwtProperties properties;
    private final SecretKey secretKey;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        byte[] keyBytes = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("app.jwt.secret 长度必须不小于 32 字节");
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String createAccessToken(Long userId, String phone, String role) {
        return createAccessToken(userId, phone, role, 0);
    }

    /**
     * 令牌携带用户当前的 tokenVersion：注销/改密/踢下线时自增版本号，
     * 已签发但版本落后的令牌在过滤器里会被判定失效。
     */
    public String createAccessToken(Long userId, String phone, String role, Integer tokenVersion) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(properties.getIssuer())
                .subject(String.valueOf(userId))
                .claim(CLAIM_PHONE, phone)
                .claim(CLAIM_ROLE, role)
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .claim("tv", tokenVersion == null ? 0 : tokenVersion)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.getAccessTokenTtl())))
                .signWith(secretKey)
                .compact();
    }

    public String createRefreshToken(Long userId) {
        return createRefreshToken(userId, 0);
    }

    /**
     * 刷新令牌同样携带签发时的 tokenVersion。
     * <p>
     * 早期版本不带版本声明，导致改密/注销/管理员踢下线后，旧 refresh 令牌仍能换出携带
     * 最新版本的 access 令牌，"强踢下线"形同虚设。这里补上版本声明，由 AuthService 校验。
     */
    public String createRefreshToken(Long userId, Integer tokenVersion) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(properties.getIssuer())
                .subject(String.valueOf(userId))
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .claim("tv", tokenVersion == null ? 0 : tokenVersion)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.getRefreshTokenTtl())))
                .signWith(secretKey)
                .compact();
    }

    /**
     * @throws JwtException 令牌无效或已过期
     */
    public Claims parse(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(secretKey)
                .requireIssuer(properties.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getAccessTokenTtlSeconds() {
        return properties.getAccessTokenTtl().toSeconds();
    }

    public long getRefreshTokenTtlSeconds() {
        return properties.getRefreshTokenTtl().toSeconds();
    }
}
