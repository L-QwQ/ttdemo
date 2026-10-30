package com.ttdemo.modules.creator.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ttdemo.common.api.PageResult;
import com.ttdemo.common.api.ResultCode;
import com.ttdemo.common.util.IdCardUtils;
import com.ttdemo.common.util.PhoneUtils;
import com.ttdemo.common.exception.BizException;
import com.ttdemo.common.util.AesCrypto;
import com.ttdemo.modules.creator.dto.CreatorDtos;
import com.ttdemo.modules.creator.entity.CreCreator;
import com.ttdemo.modules.creator.entity.CreFollow;
import com.ttdemo.modules.creator.entity.UsrRealname;
import com.ttdemo.modules.creator.mapper.CreCreatorMapper;
import com.ttdemo.modules.creator.mapper.CreFollowMapper;
import com.ttdemo.modules.creator.mapper.UsrRealnameMapper;
import com.ttdemo.modules.creator.service.CreatorService;
import com.ttdemo.modules.score.entity.ScoreCreatorAccuracy;
import com.ttdemo.modules.score.mapper.ScoreCreatorAccuracyMapper;
import com.ttdemo.modules.system.service.AuditLogService;
import com.ttdemo.modules.system.service.SysConfigService;
import com.ttdemo.modules.user.entity.SysUser;
import com.ttdemo.modules.user.mapper.SysUserMapper;
import com.ttdemo.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CreatorServiceImpl implements CreatorService {

    private final CreCreatorMapper creatorMapper;
    private final CreFollowMapper followMapper;
    private final UsrRealnameMapper realnameMapper;
    private final SysUserMapper userMapper;
    private final ScoreCreatorAccuracyMapper accuracyMapper;
    private final AesCrypto aesCrypto;
    private final AuditLogService auditLogService;
    private final SysConfigService configService;

    public CreatorServiceImpl(CreCreatorMapper creatorMapper, CreFollowMapper followMapper,
                              UsrRealnameMapper realnameMapper, SysUserMapper userMapper,
                              ScoreCreatorAccuracyMapper accuracyMapper, AesCrypto aesCrypto,
                              AuditLogService auditLogService, SysConfigService configService) {
        this.creatorMapper = creatorMapper;
        this.followMapper = followMapper;
        this.realnameMapper = realnameMapper;
        this.userMapper = userMapper;
        this.accuracyMapper = accuracyMapper;
        this.aesCrypto = aesCrypto;
        this.auditLogService = auditLogService;
        this.configService = configService;
    }

    // ================= 实名 =================

    @Override
    @Transactional
    public void submitRealname(CreatorDtos.RealnameRequest request) {
        Long userId = SecurityUtils.currentUserId();
        String idCard = request.idCardNo().trim().toUpperCase();
        // 校验位 + 出生日期都要过：只做正则会让 1112 年出生这类脏数据入库
        if (!IdCardUtils.isValid(idCard)) {
            throw new BizException(ResultCode.ID_CARD_FORMAT_ERROR);
        }
        int age = IdCardUtils.age(idCard);
        int minAge = configService.getInt("realname.min.age", 18);
        if (age < minAge) {
            throw new BizException(ResultCode.REALNAME_AGE_LIMIT);
        }
        String hash = aesCrypto.sha256(idCard);

        UsrRealname exists = realnameMapper.selectOne(
                new LambdaQueryWrapper<UsrRealname>().eq(UsrRealname::getUserId, userId));
        if (exists != null && exists.getAuditStatus() != null && exists.getAuditStatus() == UsrRealname.AUDIT_PASS) {
            throw new BizException(ResultCode.REALNAME_ALREADY_PASSED);
        }
        UsrRealname other = realnameMapper.selectOne(
                new LambdaQueryWrapper<UsrRealname>().eq(UsrRealname::getIdCardHash, hash));
        if (other != null && !other.getUserId().equals(userId)) {
            throw new BizException(ResultCode.STATE_CONFLICT, "该身份证已被其他账号使用");
        }

        SysUser user = userMapper.selectById(userId);
        byte[] nameEnc = aesCrypto.encrypt(request.realName().trim());
        byte[] idEnc = aesCrypto.encrypt(idCard);
        byte[] mobileEnc = aesCrypto.encrypt(user.getPhone());
        LocalDateTime now = LocalDateTime.now();

        if (exists == null) {
            UsrRealname entity = new UsrRealname();
            entity.setUserId(userId);
            entity.setRealName(nameEnc);
            entity.setIdCardNo(idEnc);
            entity.setIdCardHash(hash);
            entity.setMobileEnc(mobileEnc);
            entity.setAge(age);
            entity.setAuditStatus(UsrRealname.AUDIT_PENDING);
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            realnameMapper.insert(entity);
        } else {
            // 驳回后重新提交：必须刷新 submitted_at 并清空上一轮审核痕迹，
            // 否则后台仍显示首次提交时间、且排在旧位置，看起来就像「不是这条申请」
            realnameMapper.update(null, new LambdaUpdateWrapper<UsrRealname>()
                    .eq(UsrRealname::getId, exists.getId())
                    .set(UsrRealname::getRealName, nameEnc)
                    .set(UsrRealname::getIdCardNo, idEnc)
                    .set(UsrRealname::getIdCardHash, hash)
                    .set(UsrRealname::getMobileEnc, mobileEnc)
                    .set(UsrRealname::getAge, age)
                    .set(UsrRealname::getAuditStatus, UsrRealname.AUDIT_PENDING)
                    .set(UsrRealname::getAuditRemark, null)
                    .set(UsrRealname::getAuditBy, null)
                    .set(UsrRealname::getAuditTime, null)
                    .set(UsrRealname::getCreatedAt, now)
                    .set(UsrRealname::getUpdatedAt, now));
        }

        user.setRealnameStatus(2);
        user.setBirthday(IdCardUtils.birthDate(idCard));
        userMapper.updateById(user);
    }

    @Override
    public Map<String, Object> myRealname() {
        Long userId = SecurityUtils.currentUserId();
        UsrRealname entity = realnameMapper.selectOne(
                new LambdaQueryWrapper<UsrRealname>().eq(UsrRealname::getUserId, userId));
        Map<String, Object> result = new LinkedHashMap<>();
        SysUser user = userMapper.selectById(userId);
        result.put("realnameStatus", user.getRealnameStatus());
        if (entity == null) {
            result.put("submitted", false);
            return result;
        }
        result.put("submitted", true);
        result.put("realNameMasked", aesCrypto.maskName(aesCrypto.decrypt(entity.getRealName())));
        result.put("idCardMasked", aesCrypto.maskIdCard(aesCrypto.decrypt(entity.getIdCardNo())));
        result.put("auditStatus", entity.getAuditStatus());
        result.put("auditRemark", entity.getAuditRemark());
        result.put("submittedAt", entity.getCreatedAt());
        return result;
    }

    @Override
    public PageResult<Map<String, Object>> listRealnameForAudit(long page, long size, Integer auditStatus,
                                                               String keyword) {
        LambdaQueryWrapper<UsrRealname> wrapper = new LambdaQueryWrapper<UsrRealname>()
                .eq(auditStatus != null, UsrRealname::getAuditStatus, auditStatus)
                // 待审优先，再按最近提交时间倒序（重新提交会刷新 created_at，所以这里就是最新提交序）
                .orderByAsc(UsrRealname::getAuditStatus)
                .orderByDesc(UsrRealname::getCreatedAt)
                .orderByDesc(UsrRealname::getId);

        String kw = keyword == null ? "" : keyword.trim();
        if (!kw.isEmpty()) {
            // 只支持手机号 / 昵称 / 用户ID：姓名与身份证是密文，不能做模糊查询
            List<Long> userIds = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                            .select(SysUser::getId)
                            .and(w -> w.like(SysUser::getPhone, kw).or().like(SysUser::getNickname, kw)))
                    .stream().map(SysUser::getId).collect(Collectors.toList());
            if (kw.matches("\\d{1,10}")) {
                userIds.add(Long.parseLong(kw));
            }
            if (userIds.isEmpty()) {
                return PageResult.empty(page, size);
            }
            wrapper.in(UsrRealname::getUserId, userIds.stream().distinct().collect(Collectors.toList()));
        }

        Page<UsrRealname> result = realnameMapper.selectPage(new Page<>(page, size), wrapper);
        List<Map<String, Object>> list = result.getRecords().stream().map(e -> {
            SysUser user = userMapper.selectById(e.getUserId());
            String plainName = aesCrypto.decrypt(e.getRealName());
            String plainId = aesCrypto.decrypt(e.getIdCardNo());
            // 年龄以身份证现算为准：早期只做正则，像 1112 年出生这种脏数据会存成 914 岁
            Integer age = null;
            boolean idCardInvalid = false;
            try {
                age = IdCardUtils.age(plainId);
            } catch (IllegalArgumentException ex) {
                idCardInvalid = true;
                age = e.getAge();
            }
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", e.getId());
            map.put("userId", e.getUserId());
            map.put("nickname", user == null ? null : user.getNickname());
            map.put("phone", user == null ? null : PhoneUtils.mask(user.getPhone()));
            map.put("realName", aesCrypto.maskName(plainName));
            map.put("idCard", aesCrypto.maskIdCard(plainId));
            map.put("age", age);
            map.put("minor", age != null && age < 18);
            map.put("idCardInvalid", idCardInvalid);
            map.put("auditStatus", e.getAuditStatus());
            map.put("auditRemark", e.getAuditRemark());
            map.put("auditTime", e.getAuditTime());
            // 提交时间 = 最近一次提交（驳回后重新提交会刷新），便于核对「这次提交的是不是这条」
            map.put("submittedAt", e.getCreatedAt());
            map.put("updatedAt", e.getUpdatedAt());
            return map;
        }).collect(Collectors.toList());
        return PageResult.of(result.getTotal(), page, size, list);
    }

    @Override
    public Map<String, Long> realnameAuditStats() {
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("pending", countByAuditStatus(UsrRealname.AUDIT_PENDING));
        stats.put("passed", countByAuditStatus(UsrRealname.AUDIT_PASS));
        stats.put("rejected", countByAuditStatus(UsrRealname.AUDIT_REJECT));
        return stats;
    }

    private long countByAuditStatus(int auditStatus) {
        Long count = realnameMapper.selectCount(new LambdaQueryWrapper<UsrRealname>()
                .eq(UsrRealname::getAuditStatus, auditStatus));
        return count == null ? 0 : count;
    }

    @Override
    @Transactional
    public void auditRealname(Long id, CreatorDtos.AuditRequest request) {
        UsrRealname entity = realnameMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ResultCode.NOT_FOUND, "实名记录不存在");
        }
        if (entity.getAuditStatus() != null && entity.getAuditStatus() != UsrRealname.AUDIT_PENDING) {
            throw new BizException(ResultCode.STATE_CONFLICT, "该记录已审核");
        }
        boolean pass = request.result() != null && request.result() == 1;
        if (!pass && (request.remark() == null || request.remark().trim().length() < 2)) {
            throw new BizException(ResultCode.AUDIT_REMARK_REQUIRED);
        }
        int before = entity.getAuditStatus();
        entity.setAuditStatus(pass ? UsrRealname.AUDIT_PASS : UsrRealname.AUDIT_REJECT);
        entity.setAuditRemark(request.remark());
        entity.setAuditBy(SecurityUtils.currentUserId());
        entity.setAuditTime(LocalDateTime.now());
        realnameMapper.updateById(entity);

        SysUser user = userMapper.selectById(entity.getUserId());
        user.setRealnameStatus(pass ? 1 : 3);
        userMapper.updateById(user);

        auditLogService.record("REALNAME", pass ? "AUDIT_PASS" : "AUDIT_REJECT", "REALNAME", id,
                String.valueOf(before), String.valueOf(entity.getAuditStatus()), request.remark());
    }

    @Override
    @Transactional
    public void resetRealname(Long id, String remark) {
        UsrRealname entity = realnameMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ResultCode.NOT_FOUND, "实名记录不存在");
        }
        if (entity.getAuditStatus() == null || entity.getAuditStatus() != UsrRealname.AUDIT_PASS) {
            throw new BizException(ResultCode.STATE_CONFLICT, "仅「已通过」的实名可撤销");
        }
        String reason = remark == null || remark.trim().length() < 2 ? "管理员撤销实名" : remark.trim();
        realnameMapper.update(null, new LambdaUpdateWrapper<UsrRealname>()
                .eq(UsrRealname::getId, id)
                .set(UsrRealname::getAuditStatus, UsrRealname.AUDIT_REJECT)
                .set(UsrRealname::getAuditRemark, reason)
                .set(UsrRealname::getAuditBy, SecurityUtils.currentUserId())
                .set(UsrRealname::getAuditTime, LocalDateTime.now()));

        SysUser user = userMapper.selectById(entity.getUserId());
        if (user != null) {
            // 3 = 已驳回，用户端可以重新提交
            user.setRealnameStatus(3);
            userMapper.updateById(user);
        }
        auditLogService.record("REALNAME", "RESET", "REALNAME", id,
                String.valueOf(UsrRealname.AUDIT_PASS), String.valueOf(UsrRealname.AUDIT_REJECT), reason);
    }

    // ================= 创作者 =================

    @Override
    @Transactional
    public void apply(CreatorDtos.ApplyRequest request) {
        Long userId = SecurityUtils.currentUserId();
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        if (!user.isRealnamed()) {
            throw new BizException(ResultCode.NOT_REALNAME);
        }
        CreCreator exists = creatorMapper.selectOne(
                new LambdaQueryWrapper<CreCreator>().eq(CreCreator::getUserId, userId));
        if (exists != null) {
            if (exists.isApproved()) {
                throw new BizException(ResultCode.STATE_CONFLICT, "已是认证创作者");
            }
            if (exists.getStatus() != null && exists.getStatus() == CreCreator.STATUS_PENDING) {
                throw new BizException(ResultCode.CREATOR_AUDIT_PENDING);
            }
        }
        Long penNameUsed = creatorMapper.selectCount(new LambdaQueryWrapper<CreCreator>()
                .eq(CreCreator::getPenName, request.penName())
                .ne(exists != null, CreCreator::getId, exists == null ? null : exists.getId()));
        if (penNameUsed != null && penNameUsed > 0) {
            throw new BizException(ResultCode.PEN_NAME_TAKEN);
        }

        CreCreator creator = exists == null ? new CreCreator() : exists;
        creator.setUserId(userId);
        creator.setPenName(request.penName());
        creator.setIntro(request.intro());
        creator.setTags(request.tags());
        creator.setApplyReason(request.applyReason());
        creator.setStatus(CreCreator.STATUS_PENDING);
        creator.setCreatorLevel(1);
        creator.setPlatformFeeRate(configService.getDecimal("platform.fee.rate", "0.20"));
        if (exists == null) {
            creator.setFavCount(0);
            creator.setTotalIncome(0L);
            creator.setTotalPredictions(0);
            creator.setWithdrawRate(BigDecimal.ZERO);
            creatorMapper.insert(creator);
        } else {
            creatorMapper.updateById(creator);
        }

        user.setCreatorStatus(1);
        userMapper.updateById(user);
    }

    @Override
    public PageResult<CreatorDtos.ProfileVO> listForAudit(long page, long size, Integer status, String keyword) {
        LambdaQueryWrapper<CreCreator> query = new LambdaQueryWrapper<CreCreator>()
                .eq(status != null, CreCreator::getStatus, status)
                .like(keyword != null && !keyword.isBlank(), CreCreator::getPenName, keyword)
                .orderByAsc(CreCreator::getStatus)
                .orderByDesc(CreCreator::getCreatedAt);
        Page<CreCreator> result = creatorMapper.selectPage(new Page<>(page, size), query);
        List<CreatorDtos.ProfileVO> list = result.getRecords().stream().map(this::toProfileVO).collect(Collectors.toList());
        return PageResult.of(result.getTotal(), page, size, list);
    }

    @Override
    @Transactional
    public void auditCreator(Long creatorId, CreatorDtos.AuditRequest request) {
        CreCreator creator = requireById(creatorId);
        if (creator.getStatus() != null && creator.getStatus() != CreCreator.STATUS_PENDING) {
            throw new BizException(ResultCode.STATE_CONFLICT, "该申请已审核");
        }
        boolean pass = request.result() != null && request.result() == 1;
        if (!pass && (request.remark() == null || request.remark().trim().length() < 2)) {
            throw new BizException(ResultCode.AUDIT_REMARK_REQUIRED);
        }
        int before = creator.getStatus() == null ? 0 : creator.getStatus();
        creator.setStatus(pass ? CreCreator.STATUS_PASS : CreCreator.STATUS_REJECT);
        creator.setAuditRemark(request.remark());
        creator.setAuditBy(SecurityUtils.currentUserId());
        creator.setAuditTime(LocalDateTime.now());
        if (pass) {
            creator.setCertifiedAt(LocalDateTime.now());
            creator.setNewbieUntil(LocalDateTime.now().plusDays(90));
            creator.setPlatformFeeRate(configService.getDecimal("platform.fee.newbie", "0.10"));
        }
        creatorMapper.updateById(creator);

        SysUser user = userMapper.selectById(creator.getUserId());
        user.setCreatorStatus(pass ? 2 : 3);
        userMapper.updateById(user);

        auditLogService.record("CREATOR", pass ? "AUDIT_PASS" : "AUDIT_REJECT", "CREATOR", creatorId,
                String.valueOf(before), String.valueOf(creator.getStatus()), request.remark());
    }

    @Override
    public CreatorDtos.ProfileVO myProfile() {
        return toProfileVO(requireByUserId(SecurityUtils.currentUserId()));
    }

    @Override
    @Transactional
    public void updateProfile(CreatorDtos.ProfileUpdateRequest request) {
        CreCreator creator = requireByUserId(SecurityUtils.currentUserId());
        if (request.penName() != null && !request.penName().equals(creator.getPenName())) {
            Long used = creatorMapper.selectCount(new LambdaQueryWrapper<CreCreator>()
                    .eq(CreCreator::getPenName, request.penName())
                    .ne(CreCreator::getId, creator.getId()));
            if (used != null && used > 0) {
                throw new BizException(ResultCode.PEN_NAME_TAKEN);
            }
            creator.setPenName(request.penName());
        }
        if (request.intro() != null) {
            creator.setIntro(request.intro());
        }
        if (request.tags() != null) {
            creator.setTags(request.tags());
        }
        creatorMapper.updateById(creator);
    }

    @Override
    public CreatorDtos.DashboardVO dashboard() {
        CreCreator creator = requireByUserId(SecurityUtils.currentUserId());
        ScoreCreatorAccuracy acc = accuracyOf(creator.getId(), ScoreCreatorAccuracy.TYPE_ALL);
        int sample = acc == null ? 0 : acc.getSampleSize();
        int hit = acc == null ? 0 : acc.getHitTimes();
        long income = creator.getTotalIncome() == null ? 0 : creator.getTotalIncome();
        return new CreatorDtos.DashboardVO(
                creator.getTotalPredictions() == null ? 0 : creator.getTotalPredictions(),
                0, sample, hit,
                acc == null ? BigDecimal.ZERO : acc.getRawHitRate(),
                income, income,
                acc == null || acc.getTotalBuyer() == null ? 0 : acc.getTotalBuyer(),
                acc == null || acc.getTotalView() == null ? 0 : acc.getTotalView(),
                acc == null ? BigDecimal.valueOf(30) : acc.getAccuracyScore(),
                sample,
                acc == null || acc.getCurrentHitStreak() == null ? 0 : acc.getCurrentHitStreak());
    }

    @Override
    public CreatorDtos.AccuracyVO accuracy() {
        CreCreator creator = requireByUserId(SecurityUtils.currentUserId());
        ScoreCreatorAccuracy acc = accuracyOf(creator.getId(), ScoreCreatorAccuracy.TYPE_ALL);
        if (acc == null) {
            return new CreatorDtos.AccuracyVO("ALL", BigDecimal.valueOf(30), 0,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, BigDecimal.ZERO,
                    "数据积累中（0 期）", "近 90 天已结算预测，综合命中率 ≥ 60% 记为命中");
        }
        String display = acc.getSampleSize() < 5
                ? "数据积累中（" + acc.getSampleSize() + " 期）"
                : acc.getSampleSize() < 10 ? "新晋创作者" : "正常展示";
        String calcNote = String.format("近 90 天 %d 期样本，综合命中率 %s%%，95%% 置信下界 %s%%",
                acc.getSampleSize(),
                acc.getRawHitRate().multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP),
                acc.getWilsonLower().multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP));
        return new CreatorDtos.AccuracyVO(acc.getTypeCode(), acc.getAccuracyScore(), acc.getSampleSize(),
                acc.getRawHitRate(), acc.getWeightedHitRate(), acc.getWilsonLower(),
                acc.getCurrentHitStreak(), acc.getMaxHitStreak(), acc.getRecent10Rate(), display, calcNote);
    }

    @Override
    public CreatorDtos.CreatorHomeVO home(Long creatorId) {
        CreCreator creator = requireById(creatorId);
        ScoreCreatorAccuracy acc = accuracyOf(creatorId, ScoreCreatorAccuracy.TYPE_ALL);
        SysUser user = userMapper.selectById(creator.getUserId());
        boolean followed = false;
        Long currentUserId = SecurityUtils.currentUserOrNull() == null ? null : SecurityUtils.currentUserOrNull().id();
        if (currentUserId != null) {
            followed = followMapper.selectCount(new LambdaQueryWrapper<CreFollow>()
                    .eq(CreFollow::getUserId, currentUserId)
                    .eq(CreFollow::getCreatorId, creatorId)) > 0;
        }
        return new CreatorDtos.CreatorHomeVO(creator.getId(), creator.getPenName(),
                user == null ? null : user.getAvatar(), creator.getIntro(), creator.getTags(),
                creator.getCreatorLevel(), creator.getFavCount(), followed,
                acc == null ? BigDecimal.valueOf(30) : acc.getAccuracyScore(),
                acc == null ? 0 : acc.getSampleSize(),
                acc == null || acc.getCurrentHitStreak() == null ? 0 : acc.getCurrentHitStreak(),
                acc == null || acc.getTotalBuyer() == null ? 0 : acc.getTotalBuyer(),
                creator.getTotalIncome() == null ? 0 : creator.getTotalIncome(),
                creator.getCertifiedAt());
    }

    @Override
    @Transactional
    public void follow(Long creatorId) {
        Long userId = SecurityUtils.currentUserId();
        requireById(creatorId);
        if (followMapper.selectCount(new LambdaQueryWrapper<CreFollow>()
                .eq(CreFollow::getUserId, userId)
                .eq(CreFollow::getCreatorId, creatorId)) > 0) {
            return;
        }
        CreFollow follow = new CreFollow();
        follow.setUserId(userId);
        follow.setCreatorId(creatorId);
        followMapper.insert(follow);
        CreCreator creator = creatorMapper.selectById(creatorId);
        creator.setFavCount((creator.getFavCount() == null ? 0 : creator.getFavCount()) + 1);
        creatorMapper.updateById(creator);
    }

    @Override
    @Transactional
    public void unfollow(Long creatorId) {
        Long userId = SecurityUtils.currentUserId();
        int deleted = followMapper.delete(new LambdaQueryWrapper<CreFollow>()
                .eq(CreFollow::getUserId, userId)
                .eq(CreFollow::getCreatorId, creatorId));
        if (deleted > 0) {
            CreCreator creator = creatorMapper.selectById(creatorId);
            if (creator != null) {
                creator.setFavCount(Math.max(0, (creator.getFavCount() == null ? 0 : creator.getFavCount()) - 1));
                creatorMapper.updateById(creator);
            }
        }
    }

    @Override
    public CreCreator requireByUserId(Long userId) {
        CreCreator creator = creatorMapper.selectOne(
                new LambdaQueryWrapper<CreCreator>().eq(CreCreator::getUserId, userId));
        if (creator == null) {
            throw new BizException(ResultCode.NOT_CREATOR);
        }
        return creator;
    }

    @Override
    public CreCreator requireById(Long creatorId) {
        CreCreator creator = creatorMapper.selectById(creatorId);
        if (creator == null) {
            throw new BizException(ResultCode.CREATOR_NOT_FOUND);
        }
        return creator;
    }

    private ScoreCreatorAccuracy accuracyOf(Long creatorId, String typeCode) {
        return accuracyMapper.selectOne(new LambdaQueryWrapper<ScoreCreatorAccuracy>()
                .eq(ScoreCreatorAccuracy::getCreatorId, creatorId)
                .eq(ScoreCreatorAccuracy::getTypeCode, typeCode));
    }

    private CreatorDtos.ProfileVO toProfileVO(CreCreator creator) {
        if (creator == null) {
            return null;
        }
        ScoreCreatorAccuracy acc = accuracyOf(creator.getId(), ScoreCreatorAccuracy.TYPE_ALL);
        SysUser user = userMapper.selectById(creator.getUserId());
        return new CreatorDtos.ProfileVO(creator.getId(), creator.getUserId(), creator.getPenName(),
                creator.getIntro(), creator.getTags(), creator.getStatus(), statusName(creator.getStatus()),
                creator.getCreatorLevel(), creator.getPlatformFeeRate(), creator.getFavCount(),
                creator.getTotalIncome(), creator.getTotalPredictions(),
                acc == null ? BigDecimal.valueOf(30) : acc.getAccuracyScore(),
                acc == null ? 0 : acc.getSampleSize(),
                acc == null || acc.getCurrentHitStreak() == null ? 0 : acc.getCurrentHitStreak(),
                user == null ? null : user.getAvatar(), creator.getCertifiedAt(), creator.getAuditRemark());
    }

    private String statusName(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case 1 -> "待审核";
            case 2 -> "已认证";
            case 3 -> "已驳回";
            case 4 -> "已封禁";
            default -> "未知";
        };
    }

    /** 供其他模块批量取笔名，避免 N+1 */
    public Map<Long, CreCreator> mapByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        List<CreCreator> list = creatorMapper.selectBatchIds(new ArrayList<>(ids));
        Map<Long, CreCreator> map = new LinkedHashMap<>();
        for (CreCreator c : list) {
            map.put(c.getId(), c);
        }
        return map;
    }

    @Override
    public long countRealnameByStatus(int auditStatus) {
        Long count = realnameMapper.selectCount(
                new LambdaQueryWrapper<UsrRealname>().eq(UsrRealname::getAuditStatus, auditStatus));
        return count == null ? 0 : count;
    }

    @Override
    public long countCreators() {
        Long count = creatorMapper.selectCount(new LambdaQueryWrapper<>());
        return count == null ? 0 : count;
    }
}
