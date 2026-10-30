package com.ttdemo.modules.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ttdemo.common.api.ResultCode;
import com.ttdemo.common.exception.BizException;
import com.ttdemo.common.util.PhoneUtils;
import com.ttdemo.modules.auth.service.SmsCodeService;
import com.ttdemo.modules.auth.sms.SmsScene;
import com.ttdemo.modules.point.service.PointService;
import com.ttdemo.modules.system.service.SysConfigService;
import com.ttdemo.modules.user.dto.UpdateProfileRequest;
import com.ttdemo.modules.user.dto.UserVO;
import com.ttdemo.modules.user.entity.SysUser;
import com.ttdemo.modules.user.entity.UsrDeactivation;
import com.ttdemo.modules.user.mapper.SysUserMapper;
import com.ttdemo.modules.user.mapper.UsrDeactivationMapper;
import com.ttdemo.modules.user.service.AvatarStorageService;
import com.ttdemo.modules.user.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

@Service
public class UserServiceImpl implements UserService {

    private static final int PASSWORD_MIN = 6;
    private static final int PASSWORD_MAX = 32;
    private static final int NICKNAME_MAX = 20;

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final SmsCodeService smsCodeService;
    private final AvatarStorageService avatarStorageService;
    private final UsrDeactivationMapper deactivationMapper;
    private final PointService pointService;
    private final SysConfigService configService;
    private final com.ttdemo.modules.message.service.MessageService messageService;

    public UserServiceImpl(SysUserMapper userMapper, PasswordEncoder passwordEncoder,
                           SmsCodeService smsCodeService, AvatarStorageService avatarStorageService,
                           UsrDeactivationMapper deactivationMapper, PointService pointService,
                           SysConfigService configService,
                           com.ttdemo.modules.message.service.MessageService messageService) {
        this.messageService = messageService;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.smsCodeService = smsCodeService;
        this.avatarStorageService = avatarStorageService;
        this.deactivationMapper = deactivationMapper;
        this.pointService = pointService;
        this.configService = configService;
    }

    @Override
    public SysUser getById(Long id) {
        return id == null ? null : userMapper.selectById(id);
    }

    @Override
    public SysUser getByPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        return userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPhone, phone)
                .last("limit 1"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysUser create(String phone, String rawPassword, String nickname, String role) {
        if (!PhoneUtils.isValid(phone)) {
            throw new BizException(ResultCode.PHONE_FORMAT_ERROR);
        }
        if (getByPhone(phone) != null) {
            throw new BizException(ResultCode.PHONE_ALREADY_REGISTERED);
        }
        SysUser user = new SysUser();
        user.setPhone(phone);
        if (rawPassword != null && !rawPassword.isBlank()) {
            validatePassword(rawPassword);
            user.setPassword(passwordEncoder.encode(rawPassword));
        }
        user.setNickname(normalizeNickname(nickname, phone));
        user.setAvatar("");
        user.setRole(role == null ? SysUser.ROLE_USER : role);
        user.setStatus(SysUser.STATUS_ENABLED);
        userMapper.insert(user);
        return user;
    }

    @Override
    public void updateLastLogin(Long userId) {
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .set(SysUser::getLastLoginAt, LocalDateTime.now()));
    }

    @Override
    public UserVO getProfile(Long userId) {
        SysUser user = getById(userId);
        if (user == null) {
            throw new BizException(ResultCode.ACCOUNT_NOT_FOUND);
        }
        return UserVO.from(user);
    }

    @Override
    public UserVO updateProfile(Long userId, UpdateProfileRequest request) {
        SysUser user = getById(userId);
        if (user == null) {
            throw new BizException(ResultCode.ACCOUNT_NOT_FOUND);
        }
        LambdaUpdateWrapper<SysUser> update = new LambdaUpdateWrapper<SysUser>().eq(SysUser::getId, userId);
        boolean changed = false;
        String oldAvatar = null;
        if (request.nickname() != null) {
            String nickname = request.nickname().trim();
            if (nickname.isEmpty()) {
                throw new BizException(ResultCode.BAD_REQUEST, "昵称不能为空");
            }
            update.set(SysUser::getNickname, nickname);
            changed = true;
        }
        if (request.avatar() != null) {
            String avatar = request.avatar().trim();
            // 同值不清理文件，避免把正在使用的头像删掉
            if (!avatar.equals(user.getAvatar())) {
                oldAvatar = user.getAvatar();
            }
            update.set(SysUser::getAvatar, avatar);
            changed = true;
        }
        if (changed) {
            userMapper.update(null, update);
            // 换头像后清掉上一张本服务托管的文件，避免磁盘无限增长
            if (oldAvatar != null) {
                avatarStorageService.deleteIfManaged(oldAvatar);
            }
        }
        return getProfile(userId);
    }

    @Override
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        SysUser user = getById(userId);
        if (user == null) {
            throw new BizException(ResultCode.ACCOUNT_NOT_FOUND);
        }
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            throw new BizException(ResultCode.PASSWORD_NOT_SET, "该账号尚未设置密码，请先通过短信验证码设置");
        }
        if (oldPassword == null || !passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BizException(ResultCode.PASSWORD_ERROR, "原密码不正确");
        }
        validatePassword(newPassword);
        // 改密/首次设密都要自增令牌版本，踢掉此前的登录态，防止他人拿着旧令牌继续使用
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .set(SysUser::getPassword, passwordEncoder.encode(newPassword))
                .setSql("token_version = token_version + 1"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void initPassword(Long userId, String code, String newPassword) {
        SysUser user = getById(userId);
        if (user == null) {
            throw new BizException(ResultCode.ACCOUNT_NOT_FOUND);
        }
        if (user.getPassword() != null && !user.getPassword().isBlank()) {
            throw new BizException(ResultCode.PASSWORD_ALREADY_SET);
        }
        if (code == null || code.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "请输入短信验证码");
        }
        // 验证码是唯一凭证，防止拿到登录态的人直接改密码
        smsCodeService.verify(user.getPhone(), SmsScene.RESET_PASSWORD, code);
        validatePassword(newPassword);
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .set(SysUser::getPassword, passwordEncoder.encode(newPassword))
                .setSql("token_version = token_version + 1"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LocalDateTime grantVipDays(Long userId, int days) {
        SysUser user = getById(userId);
        if (user == null) {
            throw new BizException(ResultCode.ACCOUNT_NOT_FOUND);
        }
        if (days <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "会员天数必须大于 0");
        }
        // 单条 SQL 原子叠加：已过期从当前时间起算，未过期在原到期日上顺延。
        // 不能用"先读 elapsed 再写"，并发兑换会员权益时两次读到的都是同一旧值，会互相覆盖丢天数。
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .setSql("vip_expire_at = CASE WHEN vip_expire_at IS NOT NULL AND vip_expire_at > NOW() "
                        + "THEN DATE_ADD(vip_expire_at, INTERVAL " + days + " DAY) "
                        + "ELSE DATE_ADD(NOW(), INTERVAL " + days + " DAY) END"));
        return getById(userId).getVipExpireAt();
    }

    @Override
    public boolean isVip(Long userId) {
        if (userId == null) {
            return false;
        }
        SysUser user = getById(userId);
        return user != null && user.getVipExpireAt() != null
                && user.getVipExpireAt().isAfter(LocalDateTime.now());
    }

    @Override
    public void recordAgreement(Long userId) {
        String version = configService.getString("agreement.version", "v1");
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .set(SysUser::getAgreementVersion, version)
                .set(SysUser::getAgreedAt, LocalDateTime.now()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long applyDeactivation(Long userId, String reason) {
        SysUser user = getById(userId);
        if (user == null) {
            throw new BizException(ResultCode.ACCOUNT_NOT_FOUND);
        }
        UsrDeactivation exists = deactivationMapper.selectOne(new LambdaQueryWrapper<UsrDeactivation>()
                .eq(UsrDeactivation::getUserId, userId));
        if (exists != null && exists.getStatus() != null && exists.getStatus() == UsrDeactivation.STATUS_PENDING) {
            throw new BizException(ResultCode.STATE_CONFLICT, "注销申请已提交，请等待处理");
        }

        long pointsLeft = pointService.balance(userId);
        int refundFen = pointsToFen(pointsLeft);
        if (exists == null) {
            UsrDeactivation record = new UsrDeactivation();
            record.setUserId(userId);
            record.setPhone(user.getPhone());
            record.setReason(reason);
            record.setPointsLeft(pointsLeft);
            record.setRefundFen(refundFen);
            record.setStatus(UsrDeactivation.STATUS_PENDING);
            deactivationMapper.insert(record);
            return record.getId();
        }
        // 被驳回后重新申请：刷新快照并清空上次处理痕迹
        deactivationMapper.update(null, new LambdaUpdateWrapper<UsrDeactivation>()
                .eq(UsrDeactivation::getId, exists.getId())
                .set(UsrDeactivation::getPhone, user.getPhone())
                .set(UsrDeactivation::getReason, reason)
                .set(UsrDeactivation::getPointsLeft, pointsLeft)
                .set(UsrDeactivation::getRefundFen, refundFen)
                .set(UsrDeactivation::getStatus, UsrDeactivation.STATUS_PENDING)
                .set(UsrDeactivation::getHandleBy, null)
                .set(UsrDeactivation::getHandleTime, null)
                .set(UsrDeactivation::getHandleRemark, null)
                .set(UsrDeactivation::getCreatedAt, LocalDateTime.now()));
        return exists.getId();
    }

    @Override
    public java.util.Map<String, Object> myDeactivation(Long userId) {
        UsrDeactivation record = deactivationMapper.selectOne(new LambdaQueryWrapper<UsrDeactivation>()
                .eq(UsrDeactivation::getUserId, userId));
        if (record == null) {
            return null;
        }
        java.util.Map<String, Object> view = new java.util.LinkedHashMap<>();
        view.put("id", record.getId());
        view.put("status", record.getStatus());
        view.put("statusName", switch (record.getStatus() == null ? 0 : record.getStatus()) {
            case UsrDeactivation.STATUS_PENDING -> "待处理";
            case UsrDeactivation.STATUS_DONE -> "已注销";
            default -> "已驳回";
        });
        view.put("pointsLeft", record.getPointsLeft());
        view.put("refundFen", record.getRefundFen());
        view.put("handleRemark", record.getHandleRemark());
        view.put("createdAt", record.getCreatedAt());
        return view;
    }

    @Override
    public com.ttdemo.common.api.PageResult<java.util.Map<String, Object>> listDeactivations(
            long pageNum, long pageSize, Integer status) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<UsrDeactivation> page =
                deactivationMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize),
                        new LambdaQueryWrapper<UsrDeactivation>()
                                .eq(status != null, UsrDeactivation::getStatus, status)
                                .orderByAsc(UsrDeactivation::getStatus)
                                .orderByDesc(UsrDeactivation::getId));
        java.util.List<java.util.Map<String, Object>> list = new java.util.ArrayList<>();
        for (UsrDeactivation r : page.getRecords()) {
            java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("id", r.getId());
            row.put("userId", r.getUserId());
            row.put("phone", r.getPhone());
            row.put("reason", r.getReason());
            row.put("pointsLeft", r.getPointsLeft());
            row.put("refundFen", r.getRefundFen());
            row.put("status", r.getStatus());
            row.put("statusName", switch (r.getStatus() == null ? 0 : r.getStatus()) {
                case UsrDeactivation.STATUS_PENDING -> "待处理";
                case UsrDeactivation.STATUS_DONE -> "已注销";
                default -> "已驳回";
            });
            row.put("handleRemark", r.getHandleRemark());
            row.put("handleTime", r.getHandleTime());
            row.put("createdAt", r.getCreatedAt());
            list.add(row);
        }
        return com.ttdemo.common.api.PageResult.of(page.getTotal(), pageNum, pageSize, list);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleDeactivation(Long id, Long operatorId, boolean approve, String remark) {
        UsrDeactivation record = deactivationMapper.selectById(id);
        if (record == null) {
            throw new BizException(ResultCode.NOT_FOUND, "注销申请不存在");
        }
        if (record.getStatus() != null && record.getStatus() != UsrDeactivation.STATUS_PENDING) {
            throw new BizException(ResultCode.STATE_CONFLICT, "该申请已处理");
        }
        if (!approve) {
            deactivationMapper.update(null, new LambdaUpdateWrapper<UsrDeactivation>()
                    .eq(UsrDeactivation::getId, id)
                    .set(UsrDeactivation::getStatus, UsrDeactivation.STATUS_REJECTED)
                    .set(UsrDeactivation::getHandleBy, operatorId)
                    .set(UsrDeactivation::getHandleTime, LocalDateTime.now())
                    .set(UsrDeactivation::getHandleRemark, remark));
            try {
                messageService.deactivationRejected(record.getUserId(), id, remark);
            } catch (Exception e) {
                // 消息失败不影响审批结论
            }
            return;
        }

        SysUser user = getById(record.getUserId());
        if (user == null) {
            throw new BizException(ResultCode.ACCOUNT_NOT_FOUND);
        }
        long pointsLeft = pointService.balance(user.getId());
        int refundFen = pointsToFen(pointsLeft);
        // 剩余积分清零并写流水，保证账务可追溯
        if (pointsLeft > 0) {
            pointService.consume(user.getId(), pointsLeft, "DEACTIVATE", String.valueOf(id), "注销账号清零剩余积分");
        }
        // 停用 + 令牌版本自增，使已签发令牌立即失效
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, user.getId())
                .set(SysUser::getDeletedFlag, 1)
                .set(SysUser::getStatus, SysUser.STATUS_DISABLED)
                .set(SysUser::getPassword, null)
                .setSql("token_version = token_version + 1"));

        deactivationMapper.update(null, new LambdaUpdateWrapper<UsrDeactivation>()
                .eq(UsrDeactivation::getId, id)
                .set(UsrDeactivation::getStatus, UsrDeactivation.STATUS_DONE)
                .set(UsrDeactivation::getPointsLeft, pointsLeft)
                .set(UsrDeactivation::getRefundFen, refundFen)
                .set(UsrDeactivation::getHandleBy, operatorId)
                .set(UsrDeactivation::getHandleTime, LocalDateTime.now())
                .set(UsrDeactivation::getHandleRemark, remark));
    }

    /** 积分折算为分：1 元 = N 积分 */
    private int pointsToFen(long points) {
        long rate = configService.getLong("point.to.fen.rate", 10L);
        if (rate <= 0) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, points * 100 / rate);
    }

    @Override
    public String ensureInviteCode(Long userId) {
        SysUser user = getById(userId);
        if (user == null) {
            throw new BizException(ResultCode.ACCOUNT_NOT_FOUND);
        }
        if (user.getInviteCode() != null && !user.getInviteCode().isBlank()) {
            return user.getInviteCode();
        }
        // 用 id 的 36 进制 + 随机后缀，保证唯一且不易枚举
        String code;
        int guard = 0;
        do {
            code = Long.toString(userId, 36).toUpperCase()
                    + Integer.toString(java.util.concurrent.ThreadLocalRandom.current().nextInt(36 * 36), 36).toUpperCase();
            guard++;
        } while (getByInviteCode(code) != null && guard < 5);
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .set(SysUser::getInviteCode, code));
        return code;
    }

    @Override
    public SysUser getByInviteCode(String inviteCode) {
        if (inviteCode == null || inviteCode.isBlank()) {
            return null;
        }
        return userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getInviteCode, inviteCode.trim())
                .last("limit 1"));
    }

    @Override
    public long countInvited(Long inviterId) {
        if (inviterId == null) {
            return 0;
        }
        Long count = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getInviterId, inviterId));
        return count == null ? 0 : count;
    }

    @Override
    public LocalDateTime recordLoginFail(Long userId, int maxFail, int lockMinutes) {
        SysUser user = getById(userId);
        if (user == null) {
            return null;
        }
        int failCount = (user.getLoginFailCount() == null ? 0 : user.getLoginFailCount()) + 1;
        LocalDateTime lockUntil = failCount >= maxFail
                ? LocalDateTime.now().plusMinutes(Math.max(1, lockMinutes))
                : null;
        // 达到阈值后计数归零，锁定到期重新开始计数
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .set(SysUser::getLoginFailCount, lockUntil == null ? failCount : 0)
                .set(SysUser::getLockUntil, lockUntil));
        return lockUntil;
    }

    @Override
    public void clearLoginFail(Long userId) {
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .set(SysUser::getLoginFailCount, 0)
                .set(SysUser::getLockUntil, null));
    }

    @Override
    public void bindInviter(Long userId, Long inviterId) {
        if (userId == null || inviterId == null || userId.equals(inviterId)) {
            return;
        }
        SysUser self = getById(userId);
        if (self == null || self.getInviterId() != null) {
            return;
        }
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .isNull(SysUser::getInviterId)
                .set(SysUser::getInviterId, inviterId)
                .set(SysUser::getInvitedAt, LocalDateTime.now()));
    }

    @Override
    public void resetPassword(Long userId, String newPassword) {
        SysUser user = getById(userId);
        if (user == null) {
            throw new BizException(ResultCode.ACCOUNT_NOT_FOUND);
        }
        validatePassword(newPassword);
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .set(SysUser::getPassword, passwordEncoder.encode(newPassword))
                .setSql("token_version = token_version + 1"));
    }

    @Override
    public List<Long> listEnabledUserIds() {
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .select(SysUser::getId)
                        .eq(SysUser::getStatus, SysUser.STATUS_ENABLED))
                .stream()
                .map(SysUser::getId)
                .toList();
    }

    @Override
    public List<Long> filterEnabledUserIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Long> distinctIds = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinctIds.isEmpty()) {
            return List.of();
        }
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .select(SysUser::getId)
                        .in(SysUser::getId, distinctIds)
                        .eq(SysUser::getStatus, SysUser.STATUS_ENABLED))
                .stream()
                .map(SysUser::getId)
                .toList();
    }

    @Override
    public List<UserVO> listByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Long> distinctIds = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinctIds.isEmpty()) {
            return List.of();
        }
        return userMapper.selectBatchIds(distinctIds).stream()
                .map(UserVO::from)
                .toList();
    }

    public static void validatePassword(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < PASSWORD_MIN || rawPassword.length() > PASSWORD_MAX) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "密码长度需在 " + PASSWORD_MIN + "-" + PASSWORD_MAX + " 位之间");
        }
    }

    private String normalizeNickname(String nickname, String phone) {
        String value = nickname == null ? "" : nickname.trim();
        if (value.isEmpty()) {
            value = "用户" + phone.substring(phone.length() - 4);
        }
        return value.length() > NICKNAME_MAX ? value.substring(0, NICKNAME_MAX) : value;
    }
}
