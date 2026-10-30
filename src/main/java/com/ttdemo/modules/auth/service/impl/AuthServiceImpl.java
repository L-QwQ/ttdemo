package com.ttdemo.modules.auth.service.impl;

import com.ttdemo.common.api.ResultCode;
import com.ttdemo.common.exception.BizException;
import com.ttdemo.common.web.RequestUtils;
import com.ttdemo.modules.auth.dto.PasswordLoginRequest;
import com.ttdemo.modules.auth.dto.RefreshTokenRequest;
import com.ttdemo.modules.auth.dto.RegisterRequest;
import com.ttdemo.modules.auth.dto.ResetPasswordRequest;
import com.ttdemo.modules.auth.dto.SendCodeRequest;
import com.ttdemo.modules.auth.dto.SmsLoginRequest;
import com.ttdemo.modules.auth.dto.SmsSendResult;
import com.ttdemo.modules.auth.dto.TokenResponse;
import com.ttdemo.modules.auth.service.AuthService;
import com.ttdemo.modules.auth.service.SmsCodeService;
import com.ttdemo.modules.auth.sms.SmsScene;
import com.ttdemo.modules.growth.service.GrowthService;
import com.ttdemo.modules.log.entity.SysLoginLog;
import com.ttdemo.modules.log.service.LoginLogService;
import com.ttdemo.modules.user.dto.UserVO;
import com.ttdemo.modules.user.entity.SysUser;
import com.ttdemo.modules.user.service.UserService;
import com.ttdemo.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import com.ttdemo.modules.system.service.SysConfigService;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final SmsCodeService smsCodeService;
    private final UserService userService;
    private final JwtTokenProvider tokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final LoginLogService loginLogService;
    private final GrowthService growthService;
    private final SysConfigService configService;

    public AuthServiceImpl(SmsCodeService smsCodeService,
                           UserService userService,
                           JwtTokenProvider tokenProvider,
                           PasswordEncoder passwordEncoder,
                           LoginLogService loginLogService,
                           GrowthService growthService,
                           SysConfigService configService) {
        this.smsCodeService = smsCodeService;
        this.userService = userService;
        this.tokenProvider = tokenProvider;
        this.passwordEncoder = passwordEncoder;
        this.loginLogService = loginLogService;
        this.growthService = growthService;
        this.configService = configService;
    }

    @Override
    public SmsSendResult sendCode(SendCodeRequest request, HttpServletRequest httpRequest) {
        SmsScene scene = request.scene();
        String phone = request.phone();
        if (scene == SmsScene.REGISTER && userService.getByPhone(phone) != null) {
            throw new BizException(ResultCode.PHONE_ALREADY_REGISTERED);
        }
        if (scene == SmsScene.RESET_PASSWORD && userService.getByPhone(phone) == null) {
            throw new BizException(ResultCode.ACCOUNT_NOT_FOUND, "该手机号尚未注册");
        }
        return smsCodeService.send(phone, scene, RequestUtils.getClientIp(httpRequest));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TokenResponse register(RegisterRequest request, HttpServletRequest httpRequest) {
        try {
            // 协议勾选是注册前置条件（合规要求），未勾选直接拒绝
            if (configService.getBoolean("agreement.required", true) && !Boolean.TRUE.equals(request.agreed())) {
                throw new BizException(ResultCode.BAD_REQUEST, "请先阅读并同意《用户协议》《积分规则》《免责声明》");
            }
            smsCodeService.verify(request.phone(), SmsScene.REGISTER, request.code());
            SysUser user = userService.create(request.phone(), request.password(), request.nickname(),
                    SysUser.ROLE_USER);
            growthService.onRegistered(user, request.agreed(), request.inviteCode());
            loginLogService.record(user.getId(), user.getPhone(), SysLoginLog.TYPE_REGISTER, true, "注册成功", httpRequest);
            return issueTokens(user);
        } catch (BizException e) {
            loginLogService.record(null, request.phone(), SysLoginLog.TYPE_REGISTER, false, e.getMessage(), httpRequest);
            throw e;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TokenResponse loginBySms(SmsLoginRequest request, HttpServletRequest httpRequest) {
        smsCodeService.verify(request.phone(), SmsScene.LOGIN, request.code());
        SysUser user = userService.getByPhone(request.phone());
        String message = "验证码登录成功";
        if (user == null) {
            // 验证码已证明手机号归属，直接建档，降低注册流失
            user = userService.create(request.phone(), null, null, SysUser.ROLE_USER);
            // 自动建档同样发放新人礼包并记录协议同意（登录页已提示"登录即表示同意"）
            growthService.onRegistered(user, true, null);
            message = "手机号首次登录，已自动创建账号";
        }
        if (!user.isEnabled()) {
            loginLogService.record(user.getId(), user.getPhone(), SysLoginLog.TYPE_LOGIN_SMS, false, "账号已禁用", httpRequest);
            throw new BizException(ResultCode.ACCOUNT_DISABLED);
        }
        user.setLastLoginAt(LocalDateTime.now());
        userService.updateLastLogin(user.getId());
        loginLogService.record(user.getId(), user.getPhone(), SysLoginLog.TYPE_LOGIN_SMS, true, message, httpRequest);
        return issueTokens(user);
    }

    @Override
    public TokenResponse loginByPassword(PasswordLoginRequest request, boolean requireAdmin,
                                         HttpServletRequest httpRequest) {
        String logType = requireAdmin ? SysLoginLog.TYPE_ADMIN_LOGIN : SysLoginLog.TYPE_LOGIN_PASSWORD;
        SysUser user = userService.getByPhone(request.phone());
        if (user == null) {
            loginLogService.record(null, request.phone(), logType, false, "账号不存在", httpRequest);
            throw new BizException(ResultCode.PASSWORD_ERROR);
        }
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            loginLogService.record(user.getId(), user.getPhone(), logType, false, "未设置密码", httpRequest);
            throw new BizException(ResultCode.PASSWORD_NOT_SET);
        }
        // 防爆破：锁定期内直接拒绝，不消耗密码校验
        if (user.isLocked()) {
            loginLogService.record(user.getId(), user.getPhone(), logType, false, "账号锁定中", httpRequest);
            throw new BizException(ResultCode.ACCOUNT_LOCKED,
                    "登录失败次数过多，请于 " + user.getLockUntil().toLocalTime().withNano(0) + " 后再试");
        }
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            int maxFail = configService.getInt("auth.login.fail.limit", 5);
            int lockMinutes = configService.getInt("auth.login.lock.minutes", 15);
            LocalDateTime lockUntil = userService.recordLoginFail(user.getId(), maxFail, lockMinutes);
            loginLogService.record(user.getId(), user.getPhone(), logType, false,
                    lockUntil == null ? "密码错误" : "密码错误达上限，账号锁定", httpRequest);
            if (lockUntil != null) {
                throw new BizException(ResultCode.ACCOUNT_LOCKED,
                        "密码错误次数过多，账号已锁定 " + lockMinutes + " 分钟");
            }
            throw new BizException(ResultCode.PASSWORD_ERROR);
        }
        userService.clearLoginFail(user.getId());
        if (!user.isEnabled()) {
            loginLogService.record(user.getId(), user.getPhone(), logType, false, "账号已禁用", httpRequest);
            throw new BizException(ResultCode.ACCOUNT_DISABLED);
        }
        if (requireAdmin && !user.isAdmin()) {
            loginLogService.record(user.getId(), user.getPhone(), logType, false, "非管理员账号", httpRequest);
            throw new BizException(ResultCode.FORBIDDEN, "该账号不是管理员");
        }
        user.setLastLoginAt(LocalDateTime.now());
        userService.updateLastLogin(user.getId());
        loginLogService.record(user.getId(), user.getPhone(), logType, true, "登录成功", httpRequest);
        return issueTokens(user);
    }

    @Override
    public TokenResponse refresh(RefreshTokenRequest request) {
        Claims claims;
        try {
            claims = tokenProvider.parse(request.refreshToken());
        } catch (JwtException | IllegalArgumentException e) {
            throw new BizException(ResultCode.REFRESH_TOKEN_INVALID);
        }
        String type = claims.get(JwtTokenProvider.CLAIM_TYPE, String.class);
        if (!JwtTokenProvider.TYPE_REFRESH.equals(type)) {
            throw new BizException(ResultCode.REFRESH_TOKEN_INVALID);
        }
        SysUser user = userService.getById(Long.valueOf(claims.getSubject()));
        if (user == null) {
            throw new BizException(ResultCode.ACCOUNT_NOT_FOUND);
        }
        if (!user.isEnabled()) {
            throw new BizException(ResultCode.ACCOUNT_DISABLED);
        }
        assertTokenVersionAlive(user, claims);
        return issueTokens(user);
    }

    /**
     * 令牌版本校验：改密、注销、管理员踢下线都会自增 tokenVersion，旧 refresh 令牌必须立即失效。
     * <p>
     * 老版本签发的 refresh 令牌没有 tv 声明（null），为兼容存量会话按"未知"处理并放行一次；
     * 换发后的新令牌会带上当前版本，下一次续期即纳入校验。
     */
    private void assertTokenVersionAlive(SysUser user, Claims claims) {
        Integer issuedVersion = claims.get("tv", Integer.class);
        if (issuedVersion == null) {
            return;
        }
        int current = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        if (issuedVersion != current) {
            log.debug("刷新令牌版本失效 userId={} tokenTv={} currentTv={}", user.getId(), issuedVersion, current);
            throw new BizException(ResultCode.REFRESH_TOKEN_INVALID);
        }
    }

    @Override
    public void resetPassword(ResetPasswordRequest request, HttpServletRequest httpRequest) {
        smsCodeService.verify(request.phone(), SmsScene.RESET_PASSWORD, request.code());
        SysUser user = userService.getByPhone(request.phone());
        if (user == null) {
            throw new BizException(ResultCode.ACCOUNT_NOT_FOUND);
        }
        userService.resetPassword(user.getId(), request.newPassword());
        loginLogService.record(user.getId(), user.getPhone(), SysLoginLog.TYPE_LOGIN_PASSWORD, true,
                "通过验证码重置密码", httpRequest);
    }

    private TokenResponse issueTokens(SysUser user) {
        String accessToken = tokenProvider.createAccessToken(user.getId(), user.getPhone(), user.getRole(),
                user.getTokenVersion());
        String refreshToken = tokenProvider.createRefreshToken(user.getId(), user.getTokenVersion());
        return TokenResponse.of(accessToken, refreshToken, tokenProvider.getAccessTokenTtlSeconds(), UserVO.from(user));
    }
}
