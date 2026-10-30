package com.ttdemo.modules.prediction.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ttdemo.common.api.PageResult;
import com.ttdemo.common.api.ResultCode;
import com.ttdemo.common.exception.BizException;
import com.ttdemo.common.util.AesCrypto;
import com.ttdemo.common.util.HtmlSanitizer;
import com.ttdemo.common.util.IdGen;
import com.ttdemo.modules.creator.entity.CreCreator;
import com.ttdemo.modules.creator.mapper.CreCreatorMapper;
import com.ttdemo.modules.creator.service.CreatorService;
import com.ttdemo.modules.lottery.entity.LotIssue;
import com.ttdemo.modules.lottery.mapper.LotIssueMapper;
import com.ttdemo.modules.lottery.service.LotteryService;
import com.ttdemo.modules.order.entity.OrdPredictionOrder;
import com.ttdemo.modules.order.mapper.OrdPredictionOrderMapper;
import com.ttdemo.modules.order.service.OrderService;
import com.ttdemo.modules.prediction.dto.PredictionDtos;
import com.ttdemo.modules.prediction.dto.PredictionQuery;
import com.ttdemo.modules.prediction.engine.PriceCalculator;
import com.ttdemo.modules.prediction.entity.PrdAuditRecord;
import com.ttdemo.modules.prediction.entity.PrdPrediction;
import com.ttdemo.modules.prediction.entity.PrdPredictionItem;
import com.ttdemo.modules.prediction.mapper.PrdAuditRecordMapper;
import com.ttdemo.modules.prediction.mapper.PrdPredictionItemMapper;
import com.ttdemo.modules.prediction.mapper.PrdPredictionMapper;
import com.ttdemo.modules.prediction.service.PredictionService;
import com.ttdemo.modules.risk.service.RiskService;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import com.ttdemo.modules.message.service.MessageService;
import com.ttdemo.modules.point.service.PointService;
import com.ttdemo.modules.prediction.mapper.PromotionMapper;

@Service
public class PredictionServiceImpl implements PredictionService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PredictionServiceImpl.class);

    private static final Set<String> ITEM_TYPES = Set.of(
            "RED_SINGLE", "RED_MULTI", "DAN", "KILL", "BLUE_SINGLE",
            "SUM_RANGE", "SPAN", "ODD_EVEN", "BIG_SMALL", "FORM_COMBO", "AI_SCORE");

    private static final Map<String, String> PLAY_TYPE_NAMES = Map.of(
            "SINGLE", "单式推荐", "MULTI", "复式推荐", "DAN", "胆码推荐",
            "KILL", "杀号推荐", "FORM", "形态预测", "AI_PACK", "综合方案包");

    private static final Map<String, BigDecimal> DEFAULT_WEIGHTS = Map.ofEntries(
            Map.entry("RED_SINGLE", new BigDecimal("5.0")),
            Map.entry("BLUE_SINGLE", new BigDecimal("3.0")),
            Map.entry("SUM_RANGE", new BigDecimal("2.0")),
            Map.entry("SPAN", new BigDecimal("1.5")),
            Map.entry("KILL", new BigDecimal("1.5")),
            Map.entry("RED_MULTI", new BigDecimal("2.0")),
            Map.entry("DAN", new BigDecimal("1.0")),
            Map.entry("ODD_EVEN", new BigDecimal("1.0")),
            Map.entry("BIG_SMALL", new BigDecimal("1.0")),
            Map.entry("FORM_COMBO", new BigDecimal("2.5")),
            Map.entry("AI_SCORE", new BigDecimal("3.0")));

    private final PrdPredictionMapper predictionMapper;
    private final PrdPredictionItemMapper itemMapper;
    private final PrdAuditRecordMapper auditRecordMapper;
    private final LotIssueMapper issueMapper;
    private final CreCreatorMapper creatorMapper;
    private final SysUserMapper userMapper;
    private final ScoreCreatorAccuracyMapper accuracyMapper;
    private final OrdPredictionOrderMapper orderMapper;
    private final LotteryService lotteryService;
    private final CreatorService creatorService;
    private final OrderService orderService;
    private final PriceCalculator priceCalculator;
    private final RiskService riskService;
    private final SysConfigService configService;
    private final AuditLogService auditLogService;
    private final AesCrypto aesCrypto;
    private final PointService pointService;
    private final PromotionMapper promotionMapper;
    private final MessageService messageService;

    public PredictionServiceImpl(PrdPredictionMapper predictionMapper, PrdPredictionItemMapper itemMapper,
                                 PrdAuditRecordMapper auditRecordMapper, LotIssueMapper issueMapper,
                                 CreCreatorMapper creatorMapper, SysUserMapper userMapper,
                                 ScoreCreatorAccuracyMapper accuracyMapper, OrdPredictionOrderMapper orderMapper,
                                 LotteryService lotteryService, CreatorService creatorService,
                                 OrderService orderService, PriceCalculator priceCalculator,
                                 RiskService riskService, SysConfigService configService,
                                 AuditLogService auditLogService, AesCrypto aesCrypto,
                                 PointService pointService,
                                 PromotionMapper promotionMapper,
                                 MessageService messageService) {
        this.messageService = messageService;
        this.pointService = pointService;
        this.promotionMapper = promotionMapper;
        this.predictionMapper = predictionMapper;
        this.itemMapper = itemMapper;
        this.auditRecordMapper = auditRecordMapper;
        this.issueMapper = issueMapper;
        this.creatorMapper = creatorMapper;
        this.userMapper = userMapper;
        this.accuracyMapper = accuracyMapper;
        this.orderMapper = orderMapper;
        this.lotteryService = lotteryService;
        this.creatorService = creatorService;
        this.orderService = orderService;
        this.priceCalculator = priceCalculator;
        this.riskService = riskService;
        this.configService = configService;
        this.auditLogService = auditLogService;
        this.aesCrypto = aesCrypto;
    }

    @Override
    @Transactional
    public PredictionDtos.Created create(PredictionDtos.SaveRequest request) {
        Long userId = SecurityUtils.currentUserId();
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        CreCreator creator = creatorService.requireByUserId(userId);
        if (!creator.isApproved()) {
            throw new BizException(ResultCode.NOT_CREATOR);
        }
        if (!user.isRealnamed()) {
            throw new BizException(ResultCode.NOT_REALNAME);
        }

        LotIssue issue = lotteryService.requireIssueByNo(request.typeCode(), request.issueNo());
        if (LocalDateTime.now().isAfter(issue.getSaleEndTime())) {
            throw new BizException(ResultCode.ISSUE_CLOSED);
        }
        checkQuota(creator.getId(), issue.getId());
        validateItems(request.items());

        PriceCalculator.Quote quote = quoteOf(creator.getId(), request.playType());
        int price = request.pricePoints() == null ? quote.suggestPrice() : request.pricePoints();
        if (price != 0 && (price < quote.minPrice() || price > quote.maxPrice())) {
            throw new BizException(ResultCode.PRICE_OUT_OF_RANGE,
                    String.format("可售区间 %d-%d 积分", quote.minPrice(), quote.maxPrice()));
        }

        String risk = riskService.scanBlocking(request.title(), request.summary(), request.contentHtml());
        if (!risk.isBlank()) {
            throw new BizException(ResultCode.CONTENT_AUDIT_FAILED, "内容含违禁词：" + risk);
        }

        String cleanHtml = HtmlSanitizer.clean(request.contentHtml());
        PrdPrediction prediction = new PrdPrediction();
        prediction.setPredictionNo(IdGen.predictionNo());
        prediction.setCreatorId(creator.getId());
        prediction.setUserId(userId);
        prediction.setTypeCode(request.typeCode());
        prediction.setIssueId(issue.getId());
        prediction.setIssueNo(issue.getIssueNo());
        prediction.setTitle(request.title().trim());
        prediction.setPlayType(request.playType());
        prediction.setSummary(request.summary().trim());
        prediction.setContentHtml(cleanHtml);
        prediction.setContentImages(request.contentImages() == null || request.contentImages().isEmpty()
                ? null : String.join(",", request.contentImages()));
        prediction.setPriceBase(quote.base());
        prediction.setPriceCoefficient(quote.coefficient());
        prediction.setPricePoints(price);
        prediction.setIsFree(price == 0 ? 1 : 0);
        prediction.setStatus(PrdPrediction.STATUS_DRAFT);
        prediction.setItemCount(request.items().size());
        prediction.setHitCount(0);
        prediction.setViewCount(0);
        prediction.setBuyerCount(0);
        prediction.setTotalIncome(0L);
        prediction.setLikeCount(0);
        prediction.setVersion(0);
        prediction.setContentHash(contentHash(request, cleanHtml));
        predictionMapper.insert(prediction);

        int sort = 0;
        for (PredictionDtos.ItemRequest item : request.items()) {
            saveItem(prediction.getId(), item, sort++);
        }
        return new PredictionDtos.Created(prediction.getId(), prediction.getPredictionNo(),
                prediction.getStatus());
    }

    @Override
    @Transactional
    public void update(Long id, PredictionDtos.SaveRequest request) {
        PrdPrediction prediction = requireOwned(id);
        if (!prediction.isEditable()) {
            throw new BizException(ResultCode.PREDICTION_NOT_EDITABLE);
        }
        LotIssue issue = lotteryService.requireIssue(prediction.getIssueId());
        if (LocalDateTime.now().isAfter(issue.getSaleEndTime())) {
            throw new BizException(ResultCode.STATE_CONFLICT, "已过停售时间，不可编辑");
        }
        validateItems(request.items());

        CreCreator creator = creatorMapper.selectById(prediction.getCreatorId());
        PriceCalculator.Quote quote = quoteOf(creator.getId(), request.playType());
        int price = request.pricePoints() == null ? quote.suggestPrice() : request.pricePoints();
        if (price != 0 && (price < quote.minPrice() || price > quote.maxPrice())) {
            throw new BizException(ResultCode.PRICE_OUT_OF_RANGE,
                    String.format("可售区间 %d-%d 积分", quote.minPrice(), quote.maxPrice()));
        }
        String risk = riskService.scanBlocking(request.title(), request.summary(), request.contentHtml());
        if (!risk.isBlank()) {
            throw new BizException(ResultCode.CONTENT_AUDIT_FAILED, "内容含违禁词：" + risk);
        }
        String cleanHtml = HtmlSanitizer.clean(request.contentHtml());

        prediction.setTitle(request.title().trim());
        prediction.setPlayType(request.playType());
        prediction.setSummary(request.summary().trim());
        prediction.setContentHtml(cleanHtml);
        prediction.setContentImages(request.contentImages() == null || request.contentImages().isEmpty()
                ? null : String.join(",", request.contentImages()));
        prediction.setPriceBase(quote.base());
        prediction.setPriceCoefficient(quote.coefficient());
        prediction.setPricePoints(price);
        prediction.setIsFree(price == 0 ? 1 : 0);
        prediction.setItemCount(request.items().size());
        prediction.setContentHash(contentHash(request, cleanHtml));
        if (prediction.getStatus() == PrdPrediction.STATUS_REJECTED) {
            prediction.setStatus(PrdPrediction.STATUS_DRAFT);
        }
        predictionMapper.updateById(prediction);

        itemMapper.delete(new LambdaQueryWrapper<PrdPredictionItem>()
                .eq(PrdPredictionItem::getPredictionId, id));
        int sort = 0;
        for (PredictionDtos.ItemRequest item : request.items()) {
            saveItem(id, item, sort++);
        }
    }

    @Override
    @Transactional
    public Map<String, Object> submit(Long id) {
        PrdPrediction prediction = requireOwned(id);
        if (prediction.getStatus() != PrdPrediction.STATUS_DRAFT
                && prediction.getStatus() != PrdPrediction.STATUS_REJECTED) {
            throw new BizException(ResultCode.STATE_CONFLICT, "仅草稿或被驳回的预测可提交");
        }
        LotIssue issue = lotteryService.requireIssue(prediction.getIssueId());
        if (LocalDateTime.now().isAfter(issue.getSaleEndTime())) {
            throw new BizException(ResultCode.ISSUE_CLOSED);
        }
        checkQuota(prediction.getCreatorId(), prediction.getIssueId());
        long itemCount = itemMapper.selectCount(new LambdaQueryWrapper<PrdPredictionItem>()
                .eq(PrdPredictionItem::getPredictionId, id));
        if (itemCount == 0) {
            throw new BizException(ResultCode.ITEM_EMPTY);
        }
        prediction.setStatus(PrdPrediction.STATUS_PENDING_AUDIT);
        predictionMapper.updateById(prediction);
        return Map.of("status", PrdPrediction.STATUS_PENDING_AUDIT, "auditEstimateMinutes", 10);
    }

    @Override
    @Transactional
    public void withdraw(Long id) {
        PrdPrediction prediction = requireOwned(id);
        if (prediction.getStatus() != PrdPrediction.STATUS_ONLINE) {
            throw new BizException(ResultCode.STATE_CONFLICT, "仅已上架的预测可撤回");
        }
        LotIssue issue = lotteryService.requireIssue(prediction.getIssueId());
        if (LocalDateTime.now().isAfter(issue.getSaleEndTime())) {
            throw new BizException(ResultCode.STATE_CONFLICT, "已过停售时间，不可撤回");
        }
        prediction.setStatus(PrdPrediction.STATUS_WITHDRAWN);
        predictionMapper.updateById(prediction);
        int refunded = orderService.refundByPrediction(id, "创作者撤回，全额退款");

        CreCreator creator = creatorMapper.selectById(prediction.getCreatorId());
        if (creator != null) {
            creator.setWithdrawRate(calcWithdrawRate(creator));
            creatorMapper.updateById(creator);
            SysUser user = userMapper.selectById(creator.getUserId());
            if (user != null && user.getCreditScore() != null) {
                user.setCreditScore(Math.max(0, user.getCreditScore() - 5));
                userMapper.updateById(user);
            }
        }
        auditLogService.record("PREDICTION", "WITHDRAW", "PREDICTION", id, "3", "6",
                "创作者撤回，退款 " + refunded + " 笔");
    }

    @Override
    @Transactional
    public void delete(Long id) {
        PrdPrediction prediction = requireOwned(id);
        if (prediction.getStatus() != PrdPrediction.STATUS_DRAFT) {
            throw new BizException(ResultCode.STATE_CONFLICT, "仅草稿可删除");
        }
        itemMapper.delete(new LambdaQueryWrapper<PrdPredictionItem>()
                .eq(PrdPredictionItem::getPredictionId, id));
        predictionMapper.deleteById(id);
    }

    @Override
    public PageResult<PredictionDtos.ListItem> list(PredictionQuery query) {
        LambdaQueryWrapper<PrdPrediction> wrapper = new LambdaQueryWrapper<PrdPrediction>()
                .in(PrdPrediction::getStatus, PrdPrediction.STATUS_ONLINE, PrdPrediction.STATUS_LOCKED)
                .eq(query.getTypeCode() != null && !query.getTypeCode().isBlank(),
                        PrdPrediction::getTypeCode, query.getTypeCode())
                .eq(query.getIssueNo() != null && !query.getIssueNo().isBlank(),
                        PrdPrediction::getIssueNo, query.getIssueNo())
                .eq(query.getPlayType() != null && !query.getPlayType().isBlank(),
                        PrdPrediction::getPlayType, query.getPlayType())
                .eq(Boolean.TRUE.equals(query.getOnlyFree()), PrdPrediction::getIsFree, 1)
                .ge(query.getPriceMin() != null, PrdPrediction::getPricePoints, query.getPriceMin())
                .le(query.getPriceMax() != null, PrdPrediction::getPricePoints, query.getPriceMax())
                // C 端只展示关联官方期号（福彩 7 位 / 体彩 5 位）的预测，8 位历史测试期号不对外展示
                .apply("LENGTH(issue_no) IN (5, 7)");

        // 置顶优先：MySQL 中 DESC 排序时 NULL 排最后，未推广的预测自然靠后
        wrapper.orderByDesc(PrdPrediction::getTopUntil);
        switch (query.getSortBy() == null ? "hot" : query.getSortBy()) {
            case "new" -> wrapper.orderByDesc(PrdPrediction::getCreatedAt);
            case "priceAsc" -> wrapper.orderByAsc(PrdPrediction::getPricePoints);
            case "priceDesc" -> wrapper.orderByDesc(PrdPrediction::getPricePoints);
            default -> wrapper.orderByDesc(PrdPrediction::getBuyerCount)
                    .orderByDesc(PrdPrediction::getViewCount);
        }

        Page<PrdPrediction> page = predictionMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        return PageResult.of(page.getTotal(), query.getPageNum(), query.getPageSize(),
                toListItems(page.getRecords(), null));
    }

    @Override
    public PredictionDtos.Detail detail(Long id) {
        PrdPrediction prediction = requireById(id);
        Long userId = SecurityUtils.currentUserOrNull() == null ? null : SecurityUtils.currentUserOrNull().id();
        boolean owner = userId != null && prediction.getUserId().equals(userId);
        boolean purchased = orderService.isPurchased(userId, id);
        boolean visible = owner || purchased || prediction.isFree();

        List<PrdPredictionItem> items = itemMapper.selectList(new LambdaQueryWrapper<PrdPredictionItem>()
                .eq(PrdPredictionItem::getPredictionId, id)
                .orderByAsc(PrdPredictionItem::getSortNo));
        List<PredictionDtos.FreeItem> freeItems = items.stream()
                .filter(i -> i.getIsFree() != null && i.getIsFree() == 1)
                .map(i -> new PredictionDtos.FreeItem(i.getItemType(),
                        i.getItemLabel() == null ? i.getItemValue() : i.getItemLabel()))
                .collect(Collectors.toList());

        LotIssue issue = issueMapper.selectById(prediction.getIssueId());
        long remain = issue == null ? 0 : Duration.between(LocalDateTime.now(), issue.getSaleEndTime()).getSeconds();

        PredictionDtos.HitInfo hitInfo = null;
        if (prediction.isSettled()) {
            hitInfo = buildHitInfo(prediction, items, visible);
        }

        // 浏览量 +1（简化同步累加；生产应走 Redis 聚合后落库）
        prediction.setViewCount((prediction.getViewCount() == null ? 0 : prediction.getViewCount()) + 1);
        predictionMapper.updateById(prediction);

        int itemsFree = (int) items.stream().filter(i -> i.getIsFree() != null && i.getIsFree() == 1).count();
        String lockedHint = String.format("已解锁 %d 人，正文含 %d 个号码项与完整分析",
                prediction.getBuyerCount() == null ? 0 : prediction.getBuyerCount(),
                Math.max(0, items.size() - itemsFree));

        return new PredictionDtos.Detail(prediction.getId(), prediction.getPredictionNo(),
                prediction.getTitle(), prediction.getPlayType(), playTypeName(prediction.getPlayType()),
                prediction.getTypeCode(), prediction.getIssueId(), prediction.getIssueNo(),
                issue == null ? null : issue.getSaleEndTime(), Math.max(remain, 0),
                prediction.getPricePoints(), prediction.isFree(),
                new PredictionDtos.PriceBreakdown(prediction.getPriceBase(), prediction.getPriceCoefficient(),
                        String.format("基础价%d × 准确度系数%s", prediction.getPriceBase(),
                                prediction.getPriceCoefficient().stripTrailingZeros().toPlainString())),
                prediction.getSummary(), freeItems,
                visible ? prediction.getContentHtml() : null,
                visible && prediction.getContentImages() != null
                        ? Arrays.asList(prediction.getContentImages().split(",")) : List.of(),
                purchased, owner, items.size(), itemsFree, prediction.getStatus(),
                statusName(prediction.getStatus()), hitInfo,
                creatorBrief(prediction.getCreatorId()),
                prediction.getViewCount(), prediction.getBuyerCount(), lockedHint,
                prediction.getAuditRemark(), prediction.getCreatedAt());
    }

    @Override
    public Map<String, Object> content(Long id) {
        PrdPrediction prediction = requireById(id);
        Long userId = SecurityUtils.currentUserId();
        boolean owner = prediction.getUserId().equals(userId);
        if (!owner && !orderService.isPurchased(userId, id) && !prediction.isFree()) {
            throw new BizException(ResultCode.FORBIDDEN, "购买后可见");
        }
        if (LocalDateTime.now().isAfter(requireIssue(prediction).getSaleEndTime())
                && !owner && !orderService.isPurchased(userId, id)) {
            throw new BizException(ResultCode.SALE_CLOSED);
        }
        SysUser user = userMapper.selectById(userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("contentHtml", prediction.getContentHtml());
        result.put("contentImages", prediction.getContentImages() == null
                ? List.of() : Arrays.asList(prediction.getContentImages().split(",")));
        result.put("watermark", (user == null ? "U" + userId : (user.getNickname() == null ? "U" + userId : user.getNickname()))
                + " · " + userId);
        result.put("expireSeconds", 600);
        return result;
    }

    @Override
    public PredictionDtos.HitInfo hitDetail(Long id) {
        PrdPrediction prediction = requireById(id);
        if (!prediction.isSettled()) {
            return null;
        }
        Long userId = SecurityUtils.currentUserOrNull() == null ? null : SecurityUtils.currentUserOrNull().id();
        boolean owner = userId != null && prediction.getUserId().equals(userId);
        boolean visible = owner || orderService.isPurchased(userId, id) || prediction.isFree();
        List<PrdPredictionItem> items = itemMapper.selectList(new LambdaQueryWrapper<PrdPredictionItem>()
                .eq(PrdPredictionItem::getPredictionId, id)
                .orderByAsc(PrdPredictionItem::getSortNo));
        return buildHitInfo(prediction, items, visible);
    }

    /**
     * 命中明细。未解锁者只能看到「哪些项命中」的统计，看不到号码与命中说明，
     * 否则结算后的 hit-detail 就成了免费拿付费内容的入口。
     */
    private PredictionDtos.HitInfo buildHitInfo(PrdPrediction prediction, List<PrdPredictionItem> items,
                                                boolean visible) {
        return new PredictionDtos.HitInfo(prediction.getHitRate(), prediction.getIsHit(),
                prediction.getHitCount() == null ? 0 : prediction.getHitCount(),
                prediction.getItemCount() == null ? 0 : prediction.getItemCount(),
                items.stream().map(i -> new PredictionDtos.ItemHit(
                        i.getItemType(),
                        visible ? i.getItemLabel() : null,
                        visible ? i.getItemValue() : null,
                        i.getHitResult(),
                        visible ? i.getHitDetail() : null)).collect(Collectors.toList()));
    }

    @Override
    public PageResult<PredictionDtos.ListItem> mine(long pageNum, long pageSize, Integer status) {
        CreCreator creator = creatorService.requireByUserId(SecurityUtils.currentUserId());
        Page<PrdPrediction> page = predictionMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<PrdPrediction>()
                        .eq(PrdPrediction::getCreatorId, creator.getId())
                        .eq(status != null, PrdPrediction::getStatus, status)
                        .orderByDesc(PrdPrediction::getId));
        return PageResult.of(page.getTotal(), pageNum, pageSize, toListItems(page.getRecords(), null));
    }

    @Override
    public PageResult<PredictionDtos.ListItem> purchased(long pageNum, long pageSize) {
        Long userId = SecurityUtils.currentUserId();
        Page<OrdPredictionOrder> orders = orderMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<OrdPredictionOrder>()
                        .eq(OrdPredictionOrder::getUserId, userId)
                        .in(OrdPredictionOrder::getStatus,
                                OrdPredictionOrder.STATUS_PAID, OrdPredictionOrder.STATUS_SETTLED)
                        .orderByDesc(OrdPredictionOrder::getId));
        List<Long> ids = orders.getRecords().stream()
                .map(OrdPredictionOrder::getPredictionId).collect(Collectors.toList());
        List<PrdPrediction> list = ids.isEmpty() ? List.of() : predictionMapper.selectBatchIds(ids);
        return PageResult.of(orders.getTotal(), pageNum, pageSize, toListItems(list, userId));
    }

    @Override
    public PriceCalculator.Quote priceQuote(String typeCode, String playType) {
        CreCreator creator = creatorService.requireByUserId(SecurityUtils.currentUserId());
        return quoteOf(creator.getId(), playType);
    }

    // ================= 后台 =================

    @Override
    public PageResult<PredictionDtos.AuditRow> auditQueue(long pageNum, long pageSize, Integer status) {
        Integer targetStatus = status == null ? PrdPrediction.STATUS_PENDING_AUDIT : status;
        Page<PrdPrediction> page = predictionMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<PrdPrediction>()
                        .eq(PrdPrediction::getStatus, targetStatus)
                        .orderByAsc(PrdPrediction::getCreatedAt));

        List<PredictionDtos.AuditRow> rows = new ArrayList<>();
        for (PrdPrediction p : page.getRecords()) {
            LotIssue issue = issueMapper.selectById(p.getIssueId());
            long remainMinutes = issue == null ? 0
                    : Duration.between(LocalDateTime.now(), issue.getSaleEndTime()).toMinutes();
            CreCreator creator = creatorMapper.selectById(p.getCreatorId());
            rows.add(new PredictionDtos.AuditRow(p.getId(), p.getPredictionNo(), p.getTitle(),
                    p.getTypeCode(), p.getIssueNo(), creator == null ? null : creator.getPenName(),
                    p.getCreatorId(), p.getStatus(), issue == null ? null : issue.getSaleEndTime(),
                    remainMinutes, p.getPricePoints(), p.getSummary(), p.getAuditRemark(),
                    p.getRiskHit(), p.getCreatedAt()));
        }
        return PageResult.of(page.getTotal(), pageNum, pageSize, rows);
    }

    @Override
    @Transactional
    public void audit(Long id, PredictionDtos.AuditRequest request) {
        PrdPrediction prediction = requireById(id);
        boolean pass = request.result() != null && request.result() == 1;
        if (!pass && (request.remark() == null || request.remark().trim().length() < 5)) {
            throw new BizException(ResultCode.AUDIT_REMARK_REQUIRED);
        }
        LotIssue issue = requireIssue(prediction);
        if (pass && LocalDateTime.now().isAfter(issue.getSaleEndTime())) {
            throw new BizException(ResultCode.STATE_CONFLICT, "已过停售时间，不能上架（马后炮）");
        }

        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<PrdPrediction> update = new LambdaUpdateWrapper<PrdPrediction>()
                .eq(PrdPrediction::getId, id)
                .eq(PrdPrediction::getStatus, PrdPrediction.STATUS_PENDING_AUDIT)
                .set(PrdPrediction::getStatus, pass ? PrdPrediction.STATUS_ONLINE : PrdPrediction.STATUS_REJECTED)
                .set(PrdPrediction::getAuditBy, SecurityUtils.currentUserId())
                .set(PrdPrediction::getAuditTime, now)
                .set(PrdPrediction::getAuditRemark, request.remark());
        if (pass) {
            update.set(PrdPrediction::getOnlineTime, now);
        }
        int rows = predictionMapper.update(null, update);
        if (rows == 0) {
            throw new BizException(ResultCode.STATE_CONFLICT, "该预测已被其他审核员处理");
        }

        PrdAuditRecord record = new PrdAuditRecord();
        record.setBizType(PrdAuditRecord.BIZ_PREDICTION);
        record.setBizId(id);
        record.setAuditResult(pass ? 1 : 2);
        record.setAuditRemark(request.remark());
        record.setRiskLevel(request.riskLevel() == null ? 0 : request.riskLevel());
        record.setRiskDetail(prediction.getRiskHit());
        record.setAuditBy(SecurityUtils.currentUserId());
        record.setAuditTime(now);
        if (prediction.getCreatedAt() != null) {
            record.setCostMs((int) Duration.between(prediction.getCreatedAt(), now).toMillis());
        }
        auditRecordMapper.insert(record);
        auditLogService.record("PREDICTION", pass ? "AUDIT_PASS" : "AUDIT_REJECT", "PREDICTION", id,
                "1", pass ? "3" : "2", request.remark());

        CreCreator creator = creatorMapper.selectById(prediction.getCreatorId());
        if (creator != null) {
            if (pass) {
                creator.setTotalPredictions((creator.getTotalPredictions() == null ? 0 : creator.getTotalPredictions()) + 1);
                creatorMapper.updateById(creator);
            } else {
                SysUser user = userMapper.selectById(creator.getUserId());
                if (user != null && user.getCreditScore() != null) {
                    user.setCreditScore(Math.max(0, user.getCreditScore() - 2));
                    userMapper.updateById(user);
                }
            }
        }

        // 审核结论必须让作者第一时间知道，否则驳回理由只能靠客服转达
        try {
            messageService.predictionAudited(prediction.getUserId(), id, prediction.getTitle(), pass, request.remark());
        } catch (Exception e) {
            log.warn("预测审核消息推送失败 predictionId={} err={}", id, e.getMessage());
        }
    }

    @Override
    @Transactional
    public void offline(Long id, String reason) {
        PrdPrediction prediction = requireById(id);
        if (prediction.getStatus() != PrdPrediction.STATUS_ONLINE
                && prediction.getStatus() != PrdPrediction.STATUS_LOCKED) {
            throw new BizException(ResultCode.STATE_CONFLICT, "仅已上架/已锁定的预测可下架");
        }
        prediction.setStatus(PrdPrediction.STATUS_OFFLINE);
        prediction.setAuditRemark(reason);
        predictionMapper.updateById(prediction);
        int refunded = orderService.refundByPrediction(id, "平台下架，全额退款");
        auditLogService.record("PREDICTION", "OFFLINE", "PREDICTION", id,
                String.valueOf(prediction.getStatus()), "7", reason + "，退款 " + refunded + " 笔");
        try {
            messageService.predictionOffline(prediction.getUserId(), id, prediction.getTitle(), reason);
        } catch (Exception e) {
            log.warn("下架消息推送失败 predictionId={} err={}", id, e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> promote(Long id, int days, Long userId) {
        PrdPrediction prediction = requireById(id);
        if (prediction.getUserId() == null || !prediction.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN, "只能推广自己发布的预测");
        }
        if (prediction.getStatus() == null || prediction.getStatus() != PrdPrediction.STATUS_ONLINE) {
            throw new BizException(ResultCode.STATE_CONFLICT, "仅已上架的预测可以置顶推广");
        }
        int safeDays = Math.max(1, Math.min(days, 30));
        int perDay = configService.getInt("promote.points.per.day", 50);
        long cost = (long) perDay * safeDays;

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime base = prediction.getTopUntil() != null && prediction.getTopUntil().isAfter(now)
                ? prediction.getTopUntil() : now;
        LocalDateTime end = base.plusDays(safeDays);

        // 先扣积分（余额不足会抛 60001，事务回滚不写置顶）
        pointService.consume(userId, cost, "PROMOTE", "PROMOTE_" + id + "_" + System.currentTimeMillis(),
                "预测置顶推广 " + safeDays + " 天");
        predictionMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PrdPrediction>()
                .eq(PrdPrediction::getId, id)
                .set(PrdPrediction::getTopUntil, end));
        promotionMapper.insert(id, prediction.getCreatorId(), userId, cost, safeDays,
                base.isBefore(now) ? now : base, end);

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("predictionId", id);
        result.put("days", safeDays);
        result.put("pointsCost", cost);
        result.put("topUntil", end.toString());
        result.put("balance", pointService.balance(userId));
        return result;
    }

    @Override
    @Transactional
    public int lockExpired() {
        List<LotIssue> issues = issueMapper.selectList(new LambdaQueryWrapper<LotIssue>()
                .le(LotIssue::getSaleEndTime, LocalDateTime.now())
                .in(LotIssue::getDrawStatus, LotIssue.DRAW_PENDING, LotIssue.DRAW_DELAYED));
        if (issues.isEmpty()) {
            return 0;
        }
        List<Long> issueIds = issues.stream().map(LotIssue::getId).collect(Collectors.toList());
        LocalDateTime now = LocalDateTime.now();
        int total = 0;
        for (Long issueId : issueIds) {
            total += predictionMapper.update(null, new LambdaUpdateWrapper<PrdPrediction>()
                    .eq(PrdPrediction::getIssueId, issueId)
                    .eq(PrdPrediction::getStatus, PrdPrediction.STATUS_ONLINE)
                    .set(PrdPrediction::getStatus, PrdPrediction.STATUS_LOCKED)
                    .set(PrdPrediction::getLockTime, now));
        }
        return total;
    }

    @Override
    public PrdPrediction requireById(Long id) {
        PrdPrediction prediction = predictionMapper.selectById(id);
        if (prediction == null) {
            throw new BizException(ResultCode.PREDICTION_NOT_FOUND);
        }
        return prediction;
    }

    // ================= 内部 =================

    private PrdPrediction requireOwned(Long id) {
        PrdPrediction prediction = requireById(id);
        Long userId = SecurityUtils.currentUserId();
        if (!prediction.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        return prediction;
    }

    private LotIssue requireIssue(PrdPrediction prediction) {
        LotIssue issue = issueMapper.selectById(prediction.getIssueId());
        if (issue == null) {
            throw new BizException(ResultCode.ISSUE_NOT_FOUND);
        }
        return issue;
    }

    private void checkQuota(Long creatorId, Long issueId) {
        int max = configService.getInt("risk.max.prediction.per.issue", 5);
        long count = predictionMapper.selectCount(new LambdaQueryWrapper<PrdPrediction>()
                .eq(PrdPrediction::getCreatorId, creatorId)
                .eq(PrdPrediction::getIssueId, issueId)
                .in(PrdPrediction::getStatus, PrdPrediction.STATUS_DRAFT, PrdPrediction.STATUS_PENDING_AUDIT,
                        PrdPrediction.STATUS_ONLINE, PrdPrediction.STATUS_LOCKED, PrdPrediction.STATUS_SETTLED));
        if (count >= max) {
            throw new BizException(ResultCode.PREDICTION_QUOTA_EXCEEDED,
                    "该期次已达 " + max + " 篇上限");
        }
    }

    private void validateItems(List<PredictionDtos.ItemRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new BizException(ResultCode.ITEM_EMPTY);
        }
        if (items.size() > 20) {
            throw new BizException(ResultCode.ITEM_FORMAT_INVALID, "预测项最多 20 个");
        }
        int freeCount = 0;
        for (PredictionDtos.ItemRequest item : items) {
            if (!ITEM_TYPES.contains(item.itemType())) {
                throw new BizException(ResultCode.ITEM_FORMAT_INVALID, "未知预测项类型: " + item.itemType());
            }
            if (Boolean.TRUE.equals(item.isFree())) {
                freeCount++;
            }
        }
        if (freeCount == 0) {
            throw new BizException(ResultCode.ITEM_FORMAT_INVALID, "至少需要 1 个免费可见的预测项");
        }
        if (freeCount * 5 > items.size() * 2) {
            throw new BizException(ResultCode.FREE_ITEM_RATIO_INVALID);
        }
    }

    private void saveItem(Long predictionId, PredictionDtos.ItemRequest item, int sortNo) {
        PrdPredictionItem entity = new PrdPredictionItem();
        entity.setPredictionId(predictionId);
        entity.setItemType(item.itemType());
        entity.setItemValue(item.itemValue().trim());
        entity.setItemLabel(item.itemLabel());
        entity.setIsFree(Boolean.TRUE.equals(item.isFree()) ? 1 : 0);
        entity.setWeight(item.weight() != null ? item.weight()
                : DEFAULT_WEIGHTS.getOrDefault(item.itemType(), BigDecimal.ONE));
        entity.setSortNo(sortNo);
        itemMapper.insert(entity);
    }

    private PriceCalculator.Quote quoteOf(Long creatorId, String playType) {
        ScoreCreatorAccuracy acc = accuracyMapper.selectOne(new LambdaQueryWrapper<ScoreCreatorAccuracy>()
                .eq(ScoreCreatorAccuracy::getCreatorId, creatorId)
                .eq(ScoreCreatorAccuracy::getTypeCode, ScoreCreatorAccuracy.TYPE_ALL));
        BigDecimal score = acc == null || acc.getAccuracyScore() == null
                ? BigDecimal.valueOf(configService.getInt("score.cold.start", 30)) : acc.getAccuracyScore();
        int sample = acc == null || acc.getSampleSize() == null ? 0 : acc.getSampleSize();
        return priceCalculator.quote(playType, score, sample);
    }

    private String contentHash(PredictionDtos.SaveRequest request, String cleanHtml) {
        String raw = request.title() + "|" + request.summary() + "|" + cleanHtml + "|"
                + request.items().stream()
                .map(i -> i.itemType() + ":" + i.itemValue())
                .collect(Collectors.joining(","));
        return aesCrypto.sha256(raw);
    }

    private BigDecimal calcWithdrawRate(CreCreator creator) {
        long withdrawn = predictionMapper.selectCount(new LambdaQueryWrapper<PrdPrediction>()
                .eq(PrdPrediction::getCreatorId, creator.getId())
                .eq(PrdPrediction::getStatus, PrdPrediction.STATUS_WITHDRAWN));
        long total = predictionMapper.selectCount(new LambdaQueryWrapper<PrdPrediction>()
                .eq(PrdPrediction::getCreatorId, creator.getId())
                .ne(PrdPrediction::getStatus, PrdPrediction.STATUS_DRAFT));
        if (total == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(withdrawn).divide(BigDecimal.valueOf(total), 4, java.math.RoundingMode.HALF_UP);
    }

    private List<PredictionDtos.ListItem> toListItems(List<PrdPrediction> records, Long currentUserId) {
        if (records.isEmpty()) {
            return List.of();
        }
        List<Long> creatorIds = records.stream().map(PrdPrediction::getCreatorId)
                .distinct().collect(Collectors.toList());
        Map<Long, CreCreator> creators = new LinkedHashMap<>();
        for (CreCreator c : creatorMapper.selectBatchIds(creatorIds)) {
            creators.put(c.getId(), c);
        }
        Map<Long, ScoreCreatorAccuracy> accuracies = new LinkedHashMap<>();
        for (ScoreCreatorAccuracy a : accuracyMapper.selectList(new LambdaQueryWrapper<ScoreCreatorAccuracy>()
                .in(ScoreCreatorAccuracy::getCreatorId, creatorIds)
                .eq(ScoreCreatorAccuracy::getTypeCode, ScoreCreatorAccuracy.TYPE_ALL))) {
            accuracies.put(a.getCreatorId(), a);
        }
        Set<Long> purchasedIds = new LinkedHashSet<>();
        if (currentUserId != null) {
            List<Long> predIds = records.stream().map(PrdPrediction::getId).collect(Collectors.toList());
            for (OrdPredictionOrder o : orderMapper.selectList(new LambdaQueryWrapper<OrdPredictionOrder>()
                    .eq(OrdPredictionOrder::getUserId, currentUserId)
                    .in(OrdPredictionOrder::getPredictionId, predIds)
                    .in(OrdPredictionOrder::getStatus,
                            OrdPredictionOrder.STATUS_PAID, OrdPredictionOrder.STATUS_SETTLED))) {
                purchasedIds.add(o.getPredictionId());
            }
        }

        List<Long> predIds = records.stream().map(PrdPrediction::getId).collect(Collectors.toList());
        Map<Long, List<PrdPredictionItem>> itemMap = new LinkedHashMap<>();
        for (PrdPredictionItem item : itemMapper.selectList(new LambdaQueryWrapper<PrdPredictionItem>()
                .in(PrdPredictionItem::getPredictionId, predIds)
                .eq(PrdPredictionItem::getIsFree, 1)
                .orderByAsc(PrdPredictionItem::getSortNo))) {
            itemMap.computeIfAbsent(item.getPredictionId(), k -> new ArrayList<>()).add(item);
        }

        List<PredictionDtos.ListItem> list = new ArrayList<>();
        for (PrdPrediction p : records) {
            CreCreator creator = creators.get(p.getCreatorId());
            ScoreCreatorAccuracy acc = accuracies.get(p.getCreatorId());
            SysUser creatorUser = creator == null ? null : userMapper.selectById(creator.getUserId());
            List<PredictionDtos.FreeItem> freeItems = itemMap.getOrDefault(p.getId(), List.of()).stream()
                    .map(i -> new PredictionDtos.FreeItem(i.getItemType(),
                            i.getItemLabel() == null ? i.getItemValue() : i.getItemLabel()))
                    .collect(Collectors.toList());
            LotIssue issue = issueMapper.selectById(p.getIssueId());
            list.add(new PredictionDtos.ListItem(p.getId(), p.getPredictionNo(), p.getTitle(),
                    p.getPlayType(), playTypeName(p.getPlayType()), p.getTypeCode(), p.getIssueNo(),
                    issue == null ? null : issue.getSaleEndTime(), p.getPricePoints(), p.isFree(),
                    new PredictionDtos.PriceBreakdown(p.getPriceBase(), p.getPriceCoefficient(),
                            String.format("基础价%d × 准确度系数%s", p.getPriceBase(),
                                    p.getPriceCoefficient() == null ? "1.00"
                                            : p.getPriceCoefficient().stripTrailingZeros().toPlainString())),
                    p.getSummary(), freeItems,
                    new PredictionDtos.CreatorBrief(p.getCreatorId(),
                            creator == null ? null : creator.getPenName(),
                            creatorUser == null ? null : creatorUser.getAvatar(),
                            acc == null ? BigDecimal.valueOf(30) : acc.getAccuracyScore(),
                            acc == null ? 0 : acc.getSampleSize(),
                            acc == null || acc.getCurrentHitStreak() == null ? 0 : acc.getCurrentHitStreak(),
                            creator == null ? 1 : creator.getCreatorLevel()),
                    p.getViewCount() == null ? 0 : p.getViewCount(),
                    p.getBuyerCount() == null ? 0 : p.getBuyerCount(),
                    purchasedIds.contains(p.getId()), p.getStatus(), statusName(p.getStatus()),
                    p.getHitRate(), p.getIsHit(), p.getCreatedAt()));
        }
        return list;
    }

    private PredictionDtos.CreatorBrief creatorBrief(Long creatorId) {
        CreCreator creator = creatorMapper.selectById(creatorId);
        ScoreCreatorAccuracy acc = accuracyMapper.selectOne(new LambdaQueryWrapper<ScoreCreatorAccuracy>()
                .eq(ScoreCreatorAccuracy::getCreatorId, creatorId)
                .eq(ScoreCreatorAccuracy::getTypeCode, ScoreCreatorAccuracy.TYPE_ALL));
        SysUser user = creator == null ? null : userMapper.selectById(creator.getUserId());
        return new PredictionDtos.CreatorBrief(creatorId, creator == null ? null : creator.getPenName(),
                user == null ? null : user.getAvatar(),
                acc == null ? BigDecimal.valueOf(30) : acc.getAccuracyScore(),
                acc == null ? 0 : acc.getSampleSize(),
                acc == null || acc.getCurrentHitStreak() == null ? 0 : acc.getCurrentHitStreak(),
                creator == null ? 1 : creator.getCreatorLevel());
    }

    private String playTypeName(String playType) {
        return PLAY_TYPE_NAMES.getOrDefault(playType, playType);
    }

    @Override
    public long countByStatus(int status) {
        Long count = predictionMapper.selectCount(
                new LambdaQueryWrapper<PrdPrediction>().eq(PrdPrediction::getStatus, status));
        return count == null ? 0 : count;
    }

    private String statusName(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case 0 -> "草稿";
            case 1 -> "待审核";
            case 2 -> "已驳回";
            case 3 -> "已上架";
            case 4 -> "已锁定";
            case 5 -> "已结算";
            case 6 -> "已撤回";
            case 7 -> "已下架";
            default -> "未知";
        };
    }
}
