package com.ttdemo.modules.auth.dto;

import com.ttdemo.modules.user.dto.UserVO;

/**
 * @param expiresIn accessToken 有效期（秒）
 */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserVO user
) {
    public static TokenResponse of(String accessToken, String refreshToken, long expiresIn, UserVO user) {
        return new TokenResponse(accessToken, refreshToken, "Bearer", expiresIn, user);
    }
}
