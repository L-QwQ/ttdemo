package com.ttdemo.config;

import com.ttdemo.security.JwtAuthenticationFilter;
import com.ttdemo.security.RestAccessDeniedHandler;
import com.ttdemo.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 无状态 JWT 鉴权：
 * - /api/auth/**、/api/public/**、/api/admin/auth/login 匿名可访问
 * - /api/admin/** 需要 ROLE_ADMIN
 * - 其余接口登录后访问
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final CorsProperties corsProperties;

    public SecurityConfig(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                  JwtAuthenticationFilter jwtAuthenticationFilter,
                                                  RestAuthenticationEntryPoint entryPoint,
                                                  RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/**", "/api/public/**", "/api/admin/auth/login", "/error").permitAll()
                        // 彩票平台 C 端公开读接口
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/lottery/**",
                                "/api/v1/rank/**",
                                "/api/v1/random/**",
                                "/api/v1/announcements/**",
                                "/api/v1/predictions",
                                "/api/v1/predictions/*",
                                "/api/v1/predictions/*/hit-detail",
                                "/api/v1/creators/*",
                                "/api/v1/creators/*/predictions").permitAll()
                        // 随机预测生成器：纯计算，游客可玩
                        .requestMatchers(HttpMethod.POST, "/api/v1/random/generate").permitAll()
                        // 支付回调：第三方服务器无登录态，靠签名/验签保证可信
                        .requestMatchers(HttpMethod.POST, "/api/v1/recharge/notify/**").permitAll()
                        // 本地上传的静态资源（头像等）
                        .requestMatchers(HttpMethod.GET, "/api/uploads/**").permitAll()
                        // 管理端：内部角色均可进入，具体权限点由 @RequirePerm 校验
                        .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "OPERATOR", "AUDITOR", "FINANCE")
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.getAllowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
