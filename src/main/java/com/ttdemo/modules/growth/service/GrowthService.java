package com.ttdemo.modules.growth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ttdemo.common.api.ResultCode;
import com.ttdemo.common.exception.BizException;
import com.ttdemo.modules.growth.entity.UsrSignIn;
import com.ttdemo.modules.growth.mapper.UsrSignInMapper;
import com.ttdemo.modules.point.service.PointService;
import com.ttdemo.modules.system.service.SysConfigService;
import com.ttdemo.modules.user.entity.SysUser;
import com.ttdemo.modules.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 增长：新人礼包、每日签到、邀请奖励。
 * 所有积分发放一律走 PointService，保证流水与批次完整。
 */
@Service
public class GrowthService {

    private static final Logger log = LoggerFactory.getLogger(GrowthService.class);

    private final UsrSignInMapper signInMapper;
    private final PointService pointService;
    private final UserService userService;
    private final SysConfigService configService;

    public GrowthService(UsrSignInMapper signInMapper, PointService pointService,
                         UserService userService, SysConfigService configService) {
        this.signInMapper = signInMapper;
        this.pointService = pointService;
        this.userService = userService;
        this.configService = configService;
    }

    // ==================== 注册后处理 ====================

    /**
     * 注册/首次登录建档后调用：记录协议、发新人礼包、绑定邀请关系并发邀请奖励。
     *
     * @param agreed 用户是否已勾选协议（验证码登录自动建档时视为已同意，前端有提示）
     */
    @Transactional(rollbackFor = Exception.class)
    public void onRegistered(SysUser user, Boolean agreed, String inviteCode) {
        if (user == null) {
            return;
        }
        // 1. 协议同意记录
        if (!Boolean.FALSE.equals(agreed) && configService.getBoolean("agreement.required", true)) {
            userService.recordAgreement(user.getId());
        }
        // 2. 邀请码（自己的）
        userService.ensureInviteCode(user.getId());
        // 3. 新人礼包（幂等：bizNo 固定）
        int bonus = configService.getInt("point.newbie.bonus", 30);
        if (bonus > 0) {
            int expireDays = configService.getInt("point.newbie.expire.days", 7);
            pointService.earn(user.getId(), bonus, "BONUS", "NEWBIE_" + user.getId(),
                    "新用户注册礼包", expireDays);
        }
        // 4. 邀请关系 + 邀请人奖励
        if (StringUtils.hasText(inviteCode)) {
            bindAndReward(user, inviteCode.trim());
        }
    }

    private void bindAndReward(SysUser invitee, String inviteCode) {
        SysUser inviter = userService.getByInviteCode(inviteCode);
        if (inviter == null || inviter.getId().equals(invitee.getId())) {
            return;
        }
        // 号段防刷：同前 7 位视为同批注册，不发放邀请奖励（仍记录关系便于风控）
        String a = invitee.getPhone() == null ? "" : invitee.getPhone();
        String b = inviter.getPhone() == null ? "" : inviter.getPhone();
        boolean suspicious = a.length() >= 7 && b.length() >= 7 && a.substring(0, 7).equals(b.substring(0, 7));

        userService.bindInviter(invitee.getId(), inviter.getId());
        int reward = configService.getInt("point.invite.reward", 20);
        if (reward > 0 && !suspicious) {
            pointService.earn(inviter.getId(), reward, "INVITE", "INVITE_" + invitee.getId(),
                    "邀请好友注册奖励");
        } else if (suspicious) {
            log.info("邀请奖励因号段相同跳过 inviter={} invitee={}", inviter.getId(), invitee.getId());
        }
    }

    /** 被邀请人首次充值后，给邀请人追加奖励（幂等） */
    @Transactional(rollbackFor = Exception.class)
    public void onFirstRecharge(Long userId) {
        SysUser user = userService.getById(userId);
        if (user == null || user.getInviterId() == null) {
            return;
        }
        int reward = configService.getInt("point.invite.recharge.reward", 30);
        if (reward <= 0) {
            return;
        }
        pointService.earn(user.getInviterId(), reward, "INVITE_RECHARGE", "INVITE_RC_" + userId,
                "邀请好友首充奖励");
    }

    // ==================== 签到 ====================

    public Map<String, Object> signInStatus(Long userId) {
        LocalDate today = LocalDate.now();
        UsrSignIn todayRecord = signInMapper.selectOne(new LambdaQueryWrapper<UsrSignIn>()
                .eq(UsrSignIn::getUserId, userId)
                .eq(UsrSignIn::getSignDate, today));
        UsrSignIn last = signInMapper.selectOne(new LambdaQueryWrapper<UsrSignIn>()
                .eq(UsrSignIn::getUserId, userId)
                .lt(UsrSignIn::getSignDate, today)
                .orderByDesc(UsrSignIn::getSignDate)
                .last("limit 1"));

        int streak = todayRecord != null ? safe(todayRecord.getStreakDays())
                : (last != null && last.getSignDate() != null && last.getSignDate().equals(today.minusDays(1))
                ? safe(last.getStreakDays()) : 0);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("todaySigned", todayRecord != null);
        view.put("streakDays", streak);
        view.put("basePoints", configService.getInt("point.sign.in.points", 2));
        view.put("streakBonus", configService.getInt("point.sign.streak.bonus", 10));
        view.put("streakCycle", 7);
        view.put("todayPoints", todayRecord == null ? 0 : safe(todayRecord.getPoints()));
        return view;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> signIn(Long userId) {
        LocalDate today = LocalDate.now();
        UsrSignIn exists = signInMapper.selectOne(new LambdaQueryWrapper<UsrSignIn>()
                .eq(UsrSignIn::getUserId, userId)
                .eq(UsrSignIn::getSignDate, today));
        if (exists != null) {
            throw new BizException(ResultCode.STATE_CONFLICT, "今天已经签到过了");
        }

        UsrSignIn yesterday = signInMapper.selectOne(new LambdaQueryWrapper<UsrSignIn>()
                .eq(UsrSignIn::getUserId, userId)
                .eq(UsrSignIn::getSignDate, today.minusDays(1)));
        int streak = yesterday == null ? 1 : safe(yesterday.getStreakDays()) + 1;

        int base = configService.getInt("point.sign.in.points", 2);
        int bonus = 0;
        int cycle = 7;
        if (streak % cycle == 0) {
            bonus = configService.getInt("point.sign.streak.bonus", 10);
        }
        int total = base + bonus;

        UsrSignIn record = new UsrSignIn();
        record.setUserId(userId);
        record.setSignDate(today);
        record.setStreakDays(streak);
        record.setPoints(total);
        try {
            signInMapper.insert(record);
        } catch (DuplicateKeyException e) {
            // 并发重复提交：唯一索引兜底
            throw new BizException(ResultCode.STATE_CONFLICT, "今天已经签到过了");
        }

        pointService.earn(userId, total, "SIGN_IN", "SIGN_" + today, "每日签到" + (bonus > 0 ? "（连签奖励）" : ""));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("points", total);
        result.put("basePoints", base);
        result.put("bonusPoints", bonus);
        result.put("streakDays", streak);
        result.put("balance", pointService.balance(userId));
        return result;
    }

    // ==================== 邀请信息 ====================

    public Map<String, Object> inviteInfo(Long userId) {
        SysUser user = userService.getById(userId);
        if (user == null) {
            throw new BizException(ResultCode.ACCOUNT_NOT_FOUND);
        }
        String code = userService.ensureInviteCode(userId);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("inviteCode", code);
        view.put("inviteReward", configService.getInt("point.invite.reward", 20));
        view.put("firstRechargeReward", configService.getInt("point.invite.recharge.reward", 30));
        view.put("invitedCount", userService.countInvited(userId));
        return view;
    }

    private static int safe(Integer value) {
        return value == null ? 0 : value;
    }
}
