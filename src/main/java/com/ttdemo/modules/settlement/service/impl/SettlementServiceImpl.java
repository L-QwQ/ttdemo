package com.ttdemo.modules.settlement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ttdemo.common.api.PageResult;
import com.ttdemo.common.api.ResultCode;
import com.ttdemo.common.exception.BizException;
import com.ttdemo.common.util.IdGen;
import com.ttdemo.modules.creator.entity.CreCreator;
import com.ttdemo.modules.creator.mapper.CreCreatorMapper;
import com.ttdemo.modules.lottery.entity.LotIssue;
import com.ttdemo.modules.lottery.mapper.LotIssueMapper;
import com.ttdemo.modules.lottery.service.LotteryService;
import com.ttdemo.modules.order.entity.OrdPredictionOrder;
import com.ttdemo.modules.order.mapper.OrdPredictionOrderMapper;
import com.ttdemo.modules.point.entity.AccPointTransaction;
import com.ttdemo.modules.point.mapper.AccPointAccountMapper;
import com.ttdemo.modules.point.mapper.AccPointTransactionMapper;
import com.ttdemo.modules.point.service.PointService;
import com.ttdemo.modules.prediction.engine.HitJudge;
import com.ttdemo.modules.prediction.entity.PrdPrediction;
import com.ttdemo.modules.prediction.entity.PrdPredictionItem;
import com.ttdemo.modules.prediction.mapper.PrdPredictionItemMapper;
import com.ttdemo.modules.prediction.mapper.PrdPredictionMapper;
import com.ttdemo.modules.score.service.ScoreService;
import com.ttdemo.modules.settlement.entity.AccSettlement;
import com.ttdemo.modules.settlement.mapper.AccSettlementMapper;
import com.ttdemo.modules.settlement.service.SettlementService;
import com.ttdemo.modules.system.service.AuditLogService;
import com.ttdemo.modules.system.service.SysConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class SettlementServiceImpl implements SettlementService {

    private static final Logger log = LoggerFactory.getLogger(SettlementServiceImpl.class);
    private static final Map<Long, Object> LOCKS = new ConcurrentHashMap<>();

    private final LotIssueMapper issueMapper;
    private final PrdPredictionMapper predictionMapper;
    private final PrdPredictionItemMapper itemMapper;
    private final OrdPredictionOrderMapper orderMapper;
    private final AccSettlementMapper settlementMapper;
    private final AccPointAccountMapper accountMapper;
    private final AccPointTransactionMapper txnMapper;
    private final CreCreatorMapper creatorMapper;
    private final LotteryService lotteryService;
    private final PointService pointService;
    private final ScoreService scoreService;
    private final HitJudge hitJudge;
    private final SysConfigService configService;
    private final AuditLogService auditLogService;
    private final IssueSettleStatusWriter statusWriter;
    private final com.ttdemo.modules.message.service.MessageService messageService;

    public SettlementServiceImpl(LotIssueMapper issueMapper, PrdPredictionMapper predictionMapper,
                                 PrdPredictionItemMapper itemMapper, OrdPredictionOrderMapper orderMapper,
                                 AccSettlementMapper settlementMapper, AccPointAccountMapper accountMapper,
                                 AccPointTransactionMapper txnMapper, CreCreatorMapper creatorMapper,
                                 LotteryService lotteryService, PointService pointService,
                                 ScoreService scoreService, HitJudge hitJudge,
                                 SysConfigService configService, AuditLogService auditLogService,
                                 IssueSettleStatusWriter statusWriter,
                                 com.ttdemo.modules.message.service.MessageService messageService) {
        this.messageService = messageService;
        this.statusWriter = statusWriter;
        this.issueMapper = issueMapper;
        this.predictionMapper = predictionMapper;
        this.itemMapper = itemMapper;
        this.orderMapper = orderMapper;
        this.settlementMapper = settlementMapper;
        this.accountMapper = accountMapper;
        this.txnMapper = txnMapper;
        this.creatorMapper = creatorMapper;
        this.lotteryService = lotteryService;
        this.pointService = pointService;
        this.scoreService = scoreService;
        this.hitJudge = hitJudge;
        this.configService = configService;
        this.auditLogService = auditLogService;
    }

    @Override
    @Transactional
    public Map<String, Object> settleIssue(Long issueId) {
        Object lock = LOCKS.computeIfAbsent(issueId, k -> new Object());
        synchronized (lock) {
            return doSettle(issueId);
        }
    }

    private Map<String, Object> doSettle(Long issueId) {
        LotIssue issue = issueMapper.selectById(issueId);
        if (issue == null) {
            throw new BizException(ResultCode.ISSUE_NOT_FOUND);
        }
        if (!issue.isDrawn()) {
            throw new BizException(ResultCode.ISSUE_NOT_DRAWN);
        }
        if (issue.isSettled()) {
            throw new BizException(ResultCode.SETTLE_ALREADY_DONE);
        }

        // 独立事务写状态：业务回滚后 RUNNING 仍需可见，避免并发二次结算
        statusWriter.markRunning(issueId);

        try {
            List<Integer> reds = lotteryService.redList(issue);
            List<Integer> blues = lotteryService.blueList(issue);
            BigDecimal threshold = configService.getDecimal("hit.threshold", "0.6");

            // 防御性锁定：若开奖早于停售时间（补录/提前开奖），把在线预测一并锁定后再判定
            predictionMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PrdPrediction>()
                    .eq(PrdPrediction::getIssueId, issueId)
                    .eq(PrdPrediction::getStatus, PrdPrediction.STATUS_ONLINE)
                    .set(PrdPrediction::getStatus, PrdPrediction.STATUS_LOCKED)
                    .set(PrdPrediction::getLockTime, LocalDateTime.now()));

            List<PrdPrediction> predictions = predictionMapper.selectList(
                    new LambdaQueryWrapper<PrdPrediction>()
                            .eq(PrdPrediction::getIssueId, issueId)
                            .in(PrdPrediction::getStatus, PrdPrediction.STATUS_LOCKED, PrdPrediction.STATUS_SETTLED));

            int judged = 0;
            Set<Long> affectedCreators = new LinkedHashSet<>();
            for (PrdPrediction prediction : predictions) {
                List<PrdPredictionItem> items = itemMapper.selectList(
                        new LambdaQueryWrapper<PrdPredictionItem>()
                                .eq(PrdPredictionItem::getPredictionId, prediction.getId())
                                .orderByAsc(PrdPredictionItem::getSortNo));
                if (!items.isEmpty()) {
                    HitJudge.JudgeResult result = hitJudge.judge(items, reds, blues);
                    for (HitJudge.ItemResult itemResult : result.items()) {
                        PrdPredictionItem update = new PrdPredictionItem();
                        update.setId(itemResult.itemId());
                        update.setHitResult(itemResult.hit());
                        update.setHitDetail(itemResult.detail());
                        itemMapper.updateById(update);
                    }
                    int isHit = result.hitRate().compareTo(threshold) >= 0 ? 1 : 0;
                    prediction.setHitCount(result.hitCount());
                    prediction.setHitRate(result.hitRate());
                    prediction.setIsHit(isHit);
                }
                prediction.setStatus(PrdPrediction.STATUS_SETTLED);
                prediction.setSettleTime(LocalDateTime.now());
                predictionMapper.updateById(prediction);
                affectedCreators.add(prediction.getCreatorId());
                judged++;
            }

            // 分账
            List<OrdPredictionOrder> paidOrders = orderMapper.selectList(
                    new LambdaQueryWrapper<OrdPredictionOrder>()
                            .eq(OrdPredictionOrder::getIssueId, issueId)
                            .eq(OrdPredictionOrder::getStatus, OrdPredictionOrder.STATUS_PAID)
                            .ne(OrdPredictionOrder::getRiskFlag, 2));
            Map<Long, List<OrdPredictionOrder>> byCreator = paidOrders.stream()
                    .collect(Collectors.groupingBy(OrdPredictionOrder::getCreatorId));

            int settlementCount = 0;
            for (Map.Entry<Long, List<OrdPredictionOrder>> entry : byCreator.entrySet()) {
                Long creatorId = entry.getKey();
                List<OrdPredictionOrder> orders = entry.getValue();
                AccSettlement exists = settlementMapper.selectOne(new LambdaQueryWrapper<AccSettlement>()
                        .eq(AccSettlement::getCreatorId, creatorId)
                        .eq(AccSettlement::getIssueId, issueId));
                if (exists != null) {
                    log.info("创作者 {} 期次 {} 已结算，跳过", creatorId, issueId);
                    continue;
                }
                CreCreator creator = creatorMapper.selectById(creatorId);
                if (creator == null) {
                    continue;
                }
                long gross = orders.stream().mapToLong(OrdPredictionOrder::getPricePoints).sum();
                long fee = orders.stream().mapToLong(OrdPredictionOrder::getPlatformFee).sum();
                long income = orders.stream().mapToLong(OrdPredictionOrder::getCreatorIncome).sum();

                AccSettlement settlement = new AccSettlement();
                settlement.setSettleNo(IdGen.settleNo());
                settlement.setCreatorId(creatorId);
                settlement.setUserId(creator.getUserId());
                settlement.setTypeCode(issue.getTypeCode());
                settlement.setIssueId(issueId);
                settlement.setIssueNo(issue.getIssueNo());
                settlement.setOrderCount(orders.size());
                settlement.setGrossPoints(gross);
                settlement.setPlatformFee(fee);
                settlement.setCreatorPoints(income);
                settlement.setStatus(AccSettlement.STATUS_PENDING);
                settlementMapper.insert(settlement);

                if (income > 0) {
                    pointService.earn(creator.getUserId(), income, "SETTLE_IN", settlement.getSettleNo(),
                            "期次 " + issue.getIssueNo() + " 收益结算");
                }
                settlement.setStatus(AccSettlement.STATUS_CREDITED);
                settlement.setCreditTime(LocalDateTime.now());
                settlementMapper.updateById(settlement);

                try {
                    messageService.settlementCredited(creator.getUserId(), settlement.getId(),
                            issue.getIssueNo(), income);
                } catch (Exception e) {
                    log.warn("结算消息推送失败 creator={} issue={} err={}", creatorId, issue.getIssueNo(), e.getMessage());
                }

                creator.setTotalIncome((creator.getTotalIncome() == null ? 0 : creator.getTotalIncome()) + income);
                creatorMapper.updateById(creator);

                for (OrdPredictionOrder order : orders) {
                    order.setStatus(OrdPredictionOrder.STATUS_SETTLED);
                    order.setSettleId(settlement.getId());
                    orderMapper.updateById(order);
                }
                settlementCount++;
            }

            // 独立事务写终态，保证 DONE 与业务入库同生共死之外的场景下依然落库
            statusWriter.markDone(issueId);

            for (Long creatorId : affectedCreators) {
                try {
                    scoreService.recalculate(creatorId);
                } catch (Exception e) {
                    log.warn("创作者 {} 评分重算失败", creatorId, e);
                }
            }

            log.info("期次 {} {} 结算完成：判定 {} 份预测，生成 {} 张结算单",
                    issue.getTypeCode(), issue.getIssueNo(), judged, settlementCount);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("issueId", issueId);
            result.put("issueNo", issue.getIssueNo());
            result.put("judged", judged);
            result.put("settlements", settlementCount);
            result.put("status", issue.getSettleStatus());
            return result;
        } catch (Exception e) {
            // 业务已回滚，但失败状态必须留下痕证，否则定时任务会无限静默重试且无从排查
            String error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            try {
                statusWriter.markFailed(issueId, error);
            } catch (Exception statusEx) {
                log.error("期次 {} 结算失败状态写入异常", issueId, statusEx);
            }
            log.error("期次 {} 结算失败", issueId, e);
            throw e instanceof BizException biz ? biz : new BizException(ResultCode.ERROR, "结算失败：" + error);
        }
    }

    @Override
    public int settlePending() {
        if (!configService.getBoolean("feature.settle.enabled", true)) {
            return 0;
        }
        List<LotIssue> issues = new ArrayList<>(issueMapper.selectList(new LambdaQueryWrapper<LotIssue>()
                .eq(LotIssue::getDrawStatus, LotIssue.DRAW_DONE)
                .in(LotIssue::getSettleStatus, LotIssue.SETTLE_PENDING, LotIssue.SETTLE_FAILED)
                .last("limit 20")));
        // RUNNING 是独立事务提交的：进程中途崩溃会留下永久 RUNNING。超时后一并捞起重试，
        // 结算单按「创作者 + 期次」去重校验，命中已结算会跳过，重复执行不会重复入账。
        long staleSeconds = configService.getLong("settle.stale.seconds", 1800L);
        List<LotIssue> stale = issueMapper.selectList(new LambdaQueryWrapper<LotIssue>()
                .eq(LotIssue::getDrawStatus, LotIssue.DRAW_DONE)
                .eq(LotIssue::getSettleStatus, LotIssue.SETTLE_RUNNING)
                .lt(LotIssue::getUpdatedAt, LocalDateTime.now().minusSeconds(Math.max(60, staleSeconds)))
                .last("limit 5"));
        stale.stream().filter(i -> issues.stream().noneMatch(x -> x.getId().equals(i.getId())))
                .forEach(issues::add);
        int count = 0;
        for (LotIssue issue : issues) {
            try {
                // 走 settleIssue 复用同一把期次级锁，避免与人工结算并发双开结算单
                settleIssue(issue.getId());
                count++;
            } catch (Exception e) {
                log.warn("期次 {} 自动结算失败：{}", issue.getId(), e.getMessage());
            }
        }
        return count;
    }

    @Override
    public PageResult<AccSettlement> list(long pageNum, long pageSize, Integer status, Long issueId) {
        Page<AccSettlement> page = settlementMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<AccSettlement>()
                        .eq(status != null, AccSettlement::getStatus, status)
                        .eq(issueId != null, AccSettlement::getIssueId, issueId)
                        .orderByDesc(AccSettlement::getId));
        return PageResult.of(page.getTotal(), pageNum, pageSize, page.getRecords());
    }

    @Override
    public PageResult<AccSettlement> listByCreator(Long creatorId, long pageNum, long pageSize) {
        Page<AccSettlement> page = settlementMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<AccSettlement>()
                        .eq(AccSettlement::getCreatorId, creatorId)
                        .orderByDesc(AccSettlement::getId));
        return PageResult.of(page.getTotal(), pageNum, pageSize, page.getRecords());
    }

    @Override
    @Transactional
    public void cancel(Long settlementId, String reason) {
        AccSettlement settlement = settlementMapper.selectById(settlementId);
        if (settlement == null) {
            throw new BizException(ResultCode.SETTLEMENT_NOT_FOUND);
        }
        if (settlement.getStatus() == null || settlement.getStatus() != AccSettlement.STATUS_CREDITED) {
            throw new BizException(ResultCode.SETTLEMENT_CANNOT_CANCEL);
        }
        if (reason == null || reason.trim().length() < 4) {
            throw new BizException(ResultCode.ILLEGAL_ARGUMENT, "撤销原因至少 4 个字");
        }
        String bizNo = "CANCEL" + settlement.getSettleNo();
        pointService.consume(settlement.getUserId(), settlement.getCreatorPoints(), "ADMIN_ADJUST",
                bizNo, "撤销结算单 " + settlement.getSettleNo() + "：" + reason);

        settlement.setStatus(AccSettlement.STATUS_CANCELED);
        settlement.setCancelReason(reason);
        settlementMapper.updateById(settlement);
        auditLogService.record("SETTLEMENT", "CANCEL", "SETTLEMENT", settlementId,
                "2", "3", reason);
    }

    @Override
    public Map<String, Object> reconciliation(String startDate, String endDate, String typeCode) {
        LocalDate start = startDate == null || startDate.isBlank() ? LocalDate.now() : LocalDate.parse(startDate);
        LocalDate end = endDate == null || endDate.isBlank() ? LocalDate.now() : LocalDate.parse(endDate);
        LocalDateTime from = start.atStartOfDay();
        LocalDateTime to = end.plusDays(1).atStartOfDay();

        List<OrdPredictionOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<OrdPredictionOrder>()
                .ge(OrdPredictionOrder::getCreatedAt, from)
                .lt(OrdPredictionOrder::getCreatedAt, to)
                .eq(typeCode != null && !typeCode.isBlank(), OrdPredictionOrder::getTypeCode, typeCode));

        List<OrdPredictionOrder> paid = orders.stream()
                .filter(o -> o.getStatus() != null
                        && (o.getStatus() == OrdPredictionOrder.STATUS_PAID
                        || o.getStatus() == OrdPredictionOrder.STATUS_SETTLED))
                .toList();
        List<OrdPredictionOrder> refunded = orders.stream()
                .filter(o -> o.getStatus() != null && o.getStatus() == OrdPredictionOrder.STATUS_REFUNDED)
                .toList();

        Map<String, Object> consume = new LinkedHashMap<>();
        consume.put("points", paid.stream().mapToLong(OrdPredictionOrder::getPricePoints).sum());
        consume.put("orderCount", paid.size());

        Map<String, Object> settle = new LinkedHashMap<>();
        settle.put("creatorPoints", paid.stream().mapToLong(OrdPredictionOrder::getCreatorIncome).sum());
        settle.put("platformFee", paid.stream().mapToLong(OrdPredictionOrder::getPlatformFee).sum());
        settle.put("orderCount", paid.size());

        Map<String, Object> refund = new LinkedHashMap<>();
        refund.put("points", refunded.stream().mapToLong(OrdPredictionOrder::getPricePoints).sum());
        refund.put("orderCount", refunded.size());

        // 账务校验：账户余额 vs 流水合计
        long diffUsers = accountMapper.selectList(null).stream()
                .filter(account -> {
                    Long sum = txnMapper.selectList(new LambdaQueryWrapper<AccPointTransaction>()
                                    .eq(AccPointTransaction::getUserId, account.getUserId())
                                    .eq(AccPointTransaction::getAccountType, 1))
                            .stream()
                            .map(t -> t.getDirection() != null && t.getDirection() == AccPointTransaction.DIR_IN
                                    ? t.getAmount() : -t.getAmount())
                            .reduce(0L, Long::sum);
                    return !sum.equals(account.getAvailable());
                }).count();

        Map<String, Object> accountCheck = new LinkedHashMap<>();
        accountCheck.put("balanced", diffUsers == 0);
        accountCheck.put("diffUsers", diffUsers);
        accountCheck.put("checkedAt", LocalDateTime.now());

        List<Map<String, Object>> breakdown = new ArrayList<>();
        Map<String, List<OrdPredictionOrder>> byType = paid.stream()
                .collect(Collectors.groupingBy(OrdPredictionOrder::getTypeCode));
        for (Map.Entry<String, List<OrdPredictionOrder>> entry : byType.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("typeCode", entry.getKey());
            row.put("issueNo", null);
            row.put("gross", entry.getValue().stream().mapToLong(OrdPredictionOrder::getPricePoints).sum());
            row.put("fee", entry.getValue().stream().mapToLong(OrdPredictionOrder::getPlatformFee).sum());
            row.put("income", entry.getValue().stream().mapToLong(OrdPredictionOrder::getCreatorIncome).sum());
            breakdown.add(row);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("period", start + " ~ " + end);
        result.put("consume", consume);
        result.put("settle", settle);
        result.put("refund", refund);
        result.put("accountCheck", accountCheck);
        result.put("issueBreakdown", breakdown);
        return result;
    }
}
