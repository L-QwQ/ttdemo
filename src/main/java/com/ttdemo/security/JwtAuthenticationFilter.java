package com.ttdemo.security;

import com.ttdemo.modules.user.entity.SysUser;
import com.ttdemo.modules.user.service.UserService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 解析 Authorization: Bearer <token>，校验通过后写入 SecurityContext。
 * 令牌无效时静默放行，由后续授权规则返回 401。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final UserService userService;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, UserService userService) {
        this.tokenProvider = tokenProvider;
        this.userService = userService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticate(token, request);
        }
        chain.doFilter(request, response);
    }

    private void authenticate(String token, HttpServletRequest request) {
        try {
            Claims claims = tokenProvider.parse(token);
            String type = claims.get(JwtTokenProvider.CLAIM_TYPE, String.class);
            if (!JwtTokenProvider.TYPE_ACCESS.equals(type)) {
                return;
            }
            Long userId = Long.valueOf(claims.getSubject());
            SysUser user = userService.getById(userId);
            if (user == null || user.getStatus() == null || user.getStatus() != 1) {
                return;
            }
            // 账号已注销：直接拒绝，避免注销后令牌仍在有效期内可用
            if (user.isDeactivated()) {
                SecurityContextHolder.clearContext();
                return;
            }
            // 令牌版本校验：改密/注销/管理员踢下线会自增 tokenVersion，旧令牌立即失效
            Integer tokenVersion = claims.get("tv", Integer.class);
            int userVersion = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
            if ((tokenVersion == null ? 0 : tokenVersion) != userVersion) {
                log.debug("令牌版本已失效 userId={} tokenTv={} currentTv={}", userId, tokenVersion, userVersion);
                SecurityContextHolder.clearContext();
                return;
            }
            LoginUser loginUser = new LoginUser(user.getId(), user.getPhone(), user.getNickname(), user.getRole());
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    loginUser, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("令牌校验失败: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (StringUtils.hasText(header) && header.startsWith(PREFIX)) {
            return header.substring(PREFIX.length()).trim();
        }
        return null;
    }
}
