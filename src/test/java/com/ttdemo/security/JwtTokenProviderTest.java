package com.ttdemo.security;

import com.ttdemo.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtTokenProviderTest {

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("0123456789abcdef0123456789abcdef");
        properties.setIssuer("ttdemo");
        properties.setAccessTokenTtl(Duration.ofMinutes(30));
        properties.setRefreshTokenTtl(Duration.ofDays(7));
        provider = new JwtTokenProvider(properties);
    }

    @Test
    void shouldGenerateAndParseAccessToken() {
        String token = provider.createAccessToken(1L, "13800000000", "USER");
        Claims claims = provider.parse(token);
        assertEquals("1", claims.getSubject());
        assertEquals("13800000000", claims.get(JwtTokenProvider.CLAIM_PHONE, String.class));
        assertEquals("USER", claims.get(JwtTokenProvider.CLAIM_ROLE, String.class));
        assertEquals(JwtTokenProvider.TYPE_ACCESS, claims.get(JwtTokenProvider.CLAIM_TYPE, String.class));
    }

    @Test
    void refreshTokenShouldNotBeAcceptedAsAccessToken() {
        String refresh = provider.createRefreshToken(9L);
        Claims claims = provider.parse(refresh);
        assertEquals(JwtTokenProvider.TYPE_REFRESH, claims.get(JwtTokenProvider.CLAIM_TYPE, String.class));
    }

    @Test
    void shouldRejectTamperedToken() {
        String token = provider.createAccessToken(1L, "13800000000", "USER");
        String tampered = token.substring(0, token.length() - 3) + "abc";
        assertThrows(JwtException.class, () -> provider.parse(tampered));
    }

    @Test
    void shouldRejectExpiredToken() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("0123456789abcdef0123456789abcdef");
        properties.setIssuer("ttdemo");
        properties.setAccessTokenTtl(Duration.ofSeconds(-10));
        JwtTokenProvider expiredProvider = new JwtTokenProvider(properties);
        String token = expiredProvider.createAccessToken(1L, "13800000000", "USER");
        assertThrows(ExpiredJwtException.class, () -> expiredProvider.parse(token));
    }

    @Test
    void shouldRejectShortSecret() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("too-short");
        assertThrows(IllegalStateException.class, () -> new JwtTokenProvider(properties));
    }
}
