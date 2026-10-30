package com.ttdemo.modules.point.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ttdemo.common.api.PageResult;
import com.ttdemo.common.api.ResultCode;
import com.ttdemo.common.exception.BizException;
import com.ttdemo.common.util.IdGen;
import com.ttdemo.modules.point.entity.AccPointAccount;
import com.ttdemo.modules.point.entity.AccPointBatch;
import com.ttdemo.modules.point.entity.AccPointTransaction;
import com.ttdemo.modules.point.mapper.AccPointAccountMapper;
import com.ttdemo.modules.point.mapper.AccPointBatchMapper;
import com.ttdemo.modules.point.mapper.AccPointTransactionMapper;
import com.ttdemo.modules.point.service.PointService;
import com.ttdemo.modules.system.service.AuditLogService;
import com.ttdemo.modules.system.service.SysConfigService;
import com.ttdemo.modules.user.entity.SysUser;
import com.ttdemo.modules.user.mapper.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PointServiceImpl implements PointService {

    private static final Logger log = LoggerFactory.getLogger(PointServiceImpl.class);

    private final AccPointAccountMapper accountMapper;
    private final AccPointTransactionMapper txnMapper;
    private final AccPointBatchMapper batchMapper;
    private final SysUserMapper userMapper;
    private final SysConfigService configService;
    private final AuditLogService auditLogService;

    public PointServiceImpl(AccPointAccountMapper accountMapper, AccPointTransactionMapper txnMapper,
                            AccPointBatchMapper batchMapper, SysUserMapper userMapper,
                            SysConfigService configService, AuditLogService auditLogService) {
        this.accountMapper = accountMapper;
        this.txnMapper = txnMapper;
        this.batchMapper = batchMapper;
        this.userMapper = userMapper;
        this.configService = configService;
        this.auditLogService = auditLogService;
    }

    @Override
    @Transactional
    public AccPointAccount getOrCreate(Long userId) {
        AccPointAccount account = accountMapper.selectOne(
                new LambdaQueryWrapper<AccPointAccount>().eq(AccPointAccount::getUserId, userId));
        if (account == null) {
            account = new AccPointAccount();
            account.setUserId(userId);
            account.setAvailable(0L);
            account.setFrozen(0L);
            account.setTotalRecharge(0L);
            account.setTotalIncome(0L);
            account.setTotalConsume(0L);
            account.setTotalExpired(0L);
            account.setStatus(AccPointAccount.STATUS_NORMAL);
            account.setVersion(0);
            try {
                accountMapper.insert(account);
            } catch (Exception e) {
                // 并发创建：唯一键兜底，重新读取
                account = accountMapper.selectOne(
                        new LambdaQueryWrapper<AccPointAccount>().eq(AccPointAccount::getUserId, userId));
                if (account == null) {
                    // 非并发冲突导致的插入失败（如参数/连接异常），不能把 null 传给后续 NPE
                    throw new BizException(ResultCode.ERROR, "积分账户创建失败：" + e.getMessage());
                }
            }
        }
        return account;
    }

    @Override
    public long balance(Long userId) {
        return getOrCreate(userId).getAvailable();
    }

    @Override
    @Transactional
    public long consume(Long userId, long points, String bizType, String bizNo, String remark) {
        if (points <= 0) {
            throw new BizException(ResultCode.ILLEGAL_ARGUMENT, "扣减积分必须大于 0");
        }
        AccPointAccount account = getOrCreate(userId);
        long before = account.getAvailable();
        // 幂等：同一业务号同方向已存在流水则直接返回
        if (txnExists(userId, bizType, bizNo, AccPointTransaction.DIR_OUT)) {
            return before;
        }
        if (before < points) {
            throw new BizException(ResultCode.POINT_NOT_ENOUGH);
        }
        int rows = accountMapper.deduct(userId, points);
        if (rows == 0) {
            throw new BizException(ResultCode.POINT_NOT_ENOUGH);
        }
        long after = before - points;

        writeTxn(userId, AccPointTransaction.DIR_OUT, points, before, after, bizType, bizNo, remark, null);
        // 扣批次（先到期优先）
        deductBatches(userId, points);
        return after;
    }

    @Override
    @Transactional
    public long earn(Long userId, long points, String bizType, String bizNo, String remark) {
        return earn(userId, points, bizType, bizNo, remark, 0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long earn(Long userId, long points, String bizType, String bizNo, String remark, int expireDays) {
        if (points <= 0) {
            return balance(userId);
        }
        getOrCreate(userId);
        if (txnExists(userId, bizType, bizNo, AccPointTransaction.DIR_IN)) {
            return balance(userId);
        }
        AccPointAccount account = getOrCreate(userId);
        long before = account.getAvailable();
        int rows = accountMapper.increase(userId, points, points, 0);
        if (rows == 0) {
            throw new BizException(ResultCode.ERROR, "账户状态异常，无法入账");
        }
        long after = before + points;
        int expireMonths = configService.getInt("point.expire.months", 24);
        LocalDateTime expireAt = expireDays > 0
                ? LocalDateTime.now().plusDays(expireDays)
                : LocalDateTime.now().plusMonths(expireMonths);

        AccPointTransaction txn = writeTxn(userId, AccPointTransaction.DIR_IN, points, before, after,
                bizType, bizNo, remark, expireAt);
        AccPointBatch batch = new AccPointBatch();
        batch.setUserId(userId);
        batch.setTxnId(txn.getId());
        batch.setBizType(bizType);
        batch.setAmount(points);
        batch.setRemain(points);
        batch.setExpireAt(expireAt);
        batchMapper.insert(batch);
        return after;
    }

    @Override
    @Transactional
    public long recharge(Long userId, long points, long bonusPoints, String orderNo) {
        getOrCreate(userId);
        if (txnExists(userId, "RECHARGE", orderNo, AccPointTransaction.DIR_IN)) {
            return balance(userId);
        }
        AccPointAccount account = getOrCreate(userId);
        long before = account.getAvailable();
        long total = points + Math.max(0, bonusPoints);
        int rows = accountMapper.increase(userId, total, Math.max(0, bonusPoints), points);
        if (rows == 0) {
            throw new BizException(ResultCode.ERROR, "账户状态异常，无法入账");
        }
        int expireMonths = configService.getInt("point.expire.months", 24);
        LocalDateTime expireAt = LocalDateTime.now().plusMonths(expireMonths);

        long afterRecharge = before + points;
        AccPointTransaction txn = writeTxn(userId, AccPointTransaction.DIR_IN, points, before, afterRecharge,
                "RECHARGE", orderNo, "充值入账", expireAt);
        saveBatch(userId, txn.getId(), "RECHARGE", points, expireAt);

        if (bonusPoints > 0) {
            long afterBonus = afterRecharge + bonusPoints;
            AccPointTransaction bonusTxn = writeTxn(userId, AccPointTransaction.DIR_IN, bonusPoints,
                    afterRecharge, afterBonus, "BONUS", orderNo, "首充赠送", expireAt);
            saveBatch(userId, bonusTxn.getId(), "BONUS", bonusPoints, expireAt);
        }
        return before + total;
    }

    @Override
    @Transactional
    public long adjust(Long userId, long points, boolean increase, String reason) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.ACCOUNT_NOT_FOUND);
        }
        String bizNo = "ADJ" + System.currentTimeMillis();
        long after;
        if (increase) {
            after = earn(userId, points, "ADMIN_ADJUST", bizNo, reason);
        } else {
            after = consume(userId, points, "ADMIN_ADJUST", bizNo, reason);
        }
        auditLogService.record("POINT", "ADJUST", "USER", userId, null,
                (increase ? "+" : "-") + points, reason);
        log.info("人工调账 userId={}, points={}, increase={}, operator={}", userId, points, increase, reason);
        return after;
    }

    @Override
    public PageResult<AccPointTransaction> transactions(Long userId, long page, long size, String bizType) {
        Page<AccPointTransaction> result = txnMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<AccPointTransaction>()
                        .eq(AccPointTransaction::getUserId, userId)
                        .eq(bizType != null && !bizType.isBlank(), AccPointTransaction::getBizType, bizType)
                        .orderByDesc(AccPointTransaction::getId));
        return PageResult.of(result.getTotal(), page, size, result.getRecords());
    }

    @Override
    public Map<String, Object> accountView(Long userId) {
        AccPointAccount account = getOrCreate(userId);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("available", account.getAvailable());
        view.put("frozen", account.getFrozen());
        view.put("totalRecharge", account.getTotalRecharge());
        view.put("totalIncome", account.getTotalIncome());
        view.put("totalConsume", account.getTotalConsume());
        view.put("totalExpired", account.getTotalExpired());

        AccPointBatch soon = batchMapper.selectOne(new LambdaQueryWrapper<AccPointBatch>()
                .eq(AccPointBatch::getUserId, userId)
                .gt(AccPointBatch::getRemain, 0)
                .isNotNull(AccPointBatch::getExpireAt)
                .orderByAsc(AccPointBatch::getExpireAt)
                .last("limit 1"));
        Map<String, Object> expiring = new LinkedHashMap<>();
        if (soon != null) {
            long points = batchMapper.selectList(new LambdaQueryWrapper<AccPointBatch>()
                    .eq(AccPointBatch::getUserId, userId)
                    .gt(AccPointBatch::getRemain, 0)
                    .le(AccPointBatch::getExpireAt, LocalDateTime.now().plusDays(30)))
                    .stream().mapToLong(AccPointBatch::getRemain).sum();
            expiring.put("points", points);
            expiring.put("expireAt", soon.getExpireAt());
        }
        view.put("expiringSoon", expiring);
        view.put("canWithdraw", false);
        view.put("notice", "积分仅可用于平台内消费，不可提现、不可转让");
        view.put("rechargeRate", configService.getInt("point.recharge.rate", 10) + " 积分 / 元");
        view.put("expireMonths", configService.getInt("point.expire.months", 24));
        return view;
    }

    @Override
    @Transactional
    public int expireAll() {
        List<AccPointBatch> expired = batchMapper.selectList(new LambdaQueryWrapper<AccPointBatch>()
                .gt(AccPointBatch::getRemain, 0)
                .isNotNull(AccPointBatch::getExpireAt)
                .le(AccPointBatch::getExpireAt, LocalDateTime.now()));
        if (expired.isEmpty()) {
            return 0;
        }
        Map<Long, Long> byUser = expired.stream()
                .collect(Collectors.groupingBy(AccPointBatch::getUserId,
                        Collectors.summingLong(AccPointBatch::getRemain)));
        int count = 0;
        for (Map.Entry<Long, Long> entry : byUser.entrySet()) {
            Long userId = entry.getKey();
            long points = entry.getValue();
            AccPointAccount account = getOrCreate(userId);
            long before = account.getAvailable();
            long actual = Math.min(points, before);
            if (actual <= 0) {
                zeroBatches(userId);
                continue;
            }
            int rows = accountMapper.expire(userId, actual);
            if (rows > 0) {
                writeTxn(userId, AccPointTransaction.DIR_OUT, actual, before, before - actual,
                        "EXPIRE", "EXP" + System.currentTimeMillis() + userId, "积分到期扣减", null);
                count++;
            }
            zeroBatches(userId);
        }
        log.info("积分过期任务完成，处理 {} 个用户", count);
        return count;
    }

    private void zeroBatches(Long userId) {
        List<AccPointBatch> list = batchMapper.selectList(new LambdaQueryWrapper<AccPointBatch>()
                .eq(AccPointBatch::getUserId, userId)
                .gt(AccPointBatch::getRemain, 0)
                .isNotNull(AccPointBatch::getExpireAt)
                .le(AccPointBatch::getExpireAt, LocalDateTime.now()));
        for (AccPointBatch batch : list) {
            batch.setRemain(0L);
            batchMapper.updateById(batch);
        }
    }

    private void deductBatches(Long userId, long points) {
        List<AccPointBatch> list = batchMapper.selectList(new LambdaQueryWrapper<AccPointBatch>()
                .eq(AccPointBatch::getUserId, userId)
                .gt(AccPointBatch::getRemain, 0)
                .orderByAsc(AccPointBatch::getExpireAt));
        long remain = points;
        for (AccPointBatch batch : list) {
            if (remain <= 0) {
                break;
            }
            long take = Math.min(batch.getRemain(), remain);
            batch.setRemain(batch.getRemain() - take);
            batchMapper.updateById(batch);
            remain -= take;
        }
    }

    private void saveBatch(Long userId, Long txnId, String bizType, long points, LocalDateTime expireAt) {
        AccPointBatch batch = new AccPointBatch();
        batch.setUserId(userId);
        batch.setTxnId(txnId);
        batch.setBizType(bizType);
        batch.setAmount(points);
        batch.setRemain(points);
        batch.setExpireAt(expireAt);
        batchMapper.insert(batch);
    }

    private boolean txnExists(Long userId, String bizType, String bizNo, int direction) {
        return txnMapper.selectCount(new LambdaQueryWrapper<AccPointTransaction>()
                .eq(AccPointTransaction::getUserId, userId)
                .eq(AccPointTransaction::getBizType, bizType)
                .eq(AccPointTransaction::getBizNo, bizNo)
                .eq(AccPointTransaction::getDirection, direction)
                .eq(AccPointTransaction::getAccountType, 1)) > 0;
    }

    private AccPointTransaction writeTxn(Long userId, int direction, long amount, long before, long after,
                                         String bizType, String bizNo, String remark, LocalDateTime expireAt) {
        AccPointTransaction txn = new AccPointTransaction();
        txn.setTxnNo(IdGen.txnNo());
        txn.setUserId(userId);
        txn.setAccountType(1);
        txn.setDirection(direction);
        txn.setAmount(amount);
        txn.setBalanceBefore(before);
        txn.setBalanceAfter(after);
        txn.setBizType(bizType);
        txn.setBizNo(bizNo);
        txn.setRemark(remark);
        txn.setExpireAt(expireAt);
        txnMapper.insert(txn);
        return txn;
    }
}
