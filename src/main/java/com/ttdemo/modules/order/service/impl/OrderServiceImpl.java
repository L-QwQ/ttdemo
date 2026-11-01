package com.ttdemo.modules.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ttdemo.common.api.PageResult;
import com.ttdemo.common.api.ResultCode;
import com.ttdemo.common.exception.BizException;
import com.ttdemo.common.util.IdGen;
import com.ttdemo.common.web.RequestUtils;
import com.ttdemo.modules.creator.entity.CreCreator;
import com.ttdemo.modules.creator.mapper.CreCreatorMapper;
import com.ttdemo.modules.lottery.entity.LotIssue;
import com.ttdemo.modules.lottery.mapper.LotIssueMapper;
import com.ttdemo.modules.order.dto.OrderDtos;
import com.ttdemo.modules.order.entity.OrdPredictionOrder;
import com.ttdemo.modules.order.mapper.OrdPredictionOrderMapper;
import com.ttdemo.modules.order.service.OrderService;
import com.ttdemo.modules.point.service.PointService;
import com.ttdemo.modules.prediction.entity.PrdPrediction;
import com.ttdemo.modules.prediction.mapper.PrdPredictionMapper;
import com.ttdemo.modules.user.entity.SysUser;
import com.ttdemo.modules.user.mapper.SysUserMapper;
import com.ttdemo.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import com.ttdemo.modules.message.service.MessageService;

@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    private final OrdPredictionOrderMapper orderMapper;
    private final PrdPredictionMapper predictionMapper;
    private final CreCreatorMapper creatorMapper;
    private final LotIssueMapper issueMapper;
    private final SysUserMapper userMapper;
    private final PointService pointService;
    private final MessageService messageService;

    public OrderServiceImpl(OrdPredictionOrderMapper orderMapper, PrdPredictionMapper predictionMapper,
                            CreCreatorMapper creatorMapper, LotIssueMapper issueMapper,
                            SysUserMapper userMapper, PointService pointService,
                            MessageService messageService) {
        this.messageService = messageService;
        this.orderMapper = orderMapper;
        this.predictionMapper = predictionMapper;
        this.creatorMapper = creatorMapper;
        this.issueMapper = issueMapper;
        this.userMapper = userMapper;
        this.pointService = pointService;
    }

    @Override
    @Transactional
    public OrderDtos.UnlockResult unlock(Long predictionId, String idempotentKey, String deviceId) {
        Long userId = SecurityUtils.currentUserId();
        // 幂等键必须限定本人：否则用别人的幂等键会把他人订单号与积分余额返回给调用方
        String key = (idempotentKey == null || idempotentKey.isBlank()) ? UUID.randomUUID().toString() : idempotentKey;

        OrdPredictionOrder existing = orderMapper.selectOne(
                new LambdaQueryWrapper<OrdPredictionOrder>()
                        .eq(OrdPredictionOrder::getIdempotentKey, key)
                        .eq(OrdPredictionOrder::getUserId, userId));
        if (existing != null) {
            return new OrderDtos.UnlockResult(existing.getOrderNo(), existing.getPredictionId(),
                    existing.getPricePoints(), pointService.balance(existing.getUserId()), existing.getStatus());
        }

        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        if (!user.isEnabled()) {
            throw new BizException(ResultCode.FORBIDDEN, "账号不可用，请联系客服");
        }
        if (user.getRiskStatus() != null && user.getRiskStatus() == 1) {
            throw new BizException(ResultCode.RISK_LIMITED, "账号已被限制消费");
        }

        PrdPrediction prediction = predictionMapper.selectById(predictionId);
        if (prediction == null) {
            throw new BizException(ResultCode.NOT_FOUND, "预测不存在");
        }
        if (prediction.getStatus() == null
                || (prediction.getStatus() != PrdPrediction.STATUS_ONLINE
                && prediction.getStatus() != PrdPrediction.STATUS_LOCKED)) {
            throw new BizException(ResultCode.PREDICTION_UNAVAILABLE);
        }
        LotIssue issue = issueMapper.selectById(prediction.getIssueId());
        if (issue == null) {
            throw new BizException(ResultCode.ISSUE_NOT_FOUND);
        }
        if (LocalDateTime.now().isAfter(issue.getSaleEndTime())) {
            throw new BizException(ResultCode.SALE_CLOSED);
        }
        if (prediction.getUserId().equals(userId)) {
            throw new BizException(ResultCode.CANNOT_BUY_OWN);
        }
        if (orderMapper.selectCount(new LambdaQueryWrapper<OrdPredictionOrder>()
                .eq(OrdPredictionOrder::getUserId, userId)
                .eq(OrdPredictionOrder::getPredictionId, predictionId)) > 0) {
            throw new BizException(ResultCode.ALREADY_PURCHASED);
        }

        int price = prediction.getPricePoints() == null ? 0 : prediction.getPricePoints();
        // 免费预测不产生订单：pointService.consume 要求积分 > 0，直接走扣减会抛「扣减积分必须大于 0」
        if (price <= 0) {
            return new OrderDtos.UnlockResult("", predictionId, 0, pointService.balance(userId),
                    OrdPredictionOrder.STATUS_PAID);
        }
        CreCreator creator = creatorMapper.selectById(prediction.getCreatorId());
        BigDecimal rate = creator == null || creator.getPlatformFeeRate() == null
                ? new BigDecimal("0.20") : creator.getPlatformFeeRate();
        int platformFee = (int) Math.floor(price * rate.doubleValue());
        int creatorIncome = price - platformFee;

        String orderNo = IdGen.orderNo();
        long balanceAfter = pointService.consume(userId, price, "UNLOCK", orderNo, "解锁预测 " + prediction.getTitle());

        OrdPredictionOrder order = new OrdPredictionOrder();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setPredictionId(predictionId);
        order.setCreatorId(prediction.getCreatorId());
        order.setTypeCode(prediction.getTypeCode());
        order.setIssueId(prediction.getIssueId());
        order.setPricePoints(price);
        order.setPlatformFee(platformFee);
        order.setCreatorIncome(creatorIncome);
        order.setStatus(OrdPredictionOrder.STATUS_PAID);
        order.setPayTime(LocalDateTime.now());
        order.setClientIp(RequestUtils.currentClientIp());
        order.setDeviceId(deviceId);
        order.setRiskFlag(0);
        order.setIdempotentKey(key);
        try {
            orderMapper.insert(order);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 并发双击或重复提交：uk_user_pred / uk_idem 兜底，资金由事务回滚保护，这里转成业务错误
            Long dupPrediction = owns(userId, predictionId);
            if (dupPrediction != null) {
                throw new BizException(ResultCode.ALREADY_PURCHASED);
            }
            throw new BizException(ResultCode.ILLEGAL_ARGUMENT, "幂等键已被其他订单使用，请更换后重试");
        }

        // 原子自增：整行 update 会覆盖同事务外的其它字段变更（浏览量、状态等）
        predictionMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PrdPrediction>()
                        .eq(PrdPrediction::getId, predictionId)
                        .setSql("buyer_count = buyer_count + 1"));

        log.info("解锁成功 user={}, prediction={}, order={}, price={}", userId, predictionId, orderNo, price);
        return new OrderDtos.UnlockResult(orderNo, predictionId, price, balanceAfter,
                OrdPredictionOrder.STATUS_PAID);
    }

    /** 返回该用户在此预测上的订单 id，不存在返回 null */
    private Long owns(Long userId, Long predictionId) {
        OrdPredictionOrder duplicated = orderMapper.selectOne(new LambdaQueryWrapper<OrdPredictionOrder>()
                .eq(OrdPredictionOrder::getUserId, userId)
                .eq(OrdPredictionOrder::getPredictionId, predictionId)
                .last("limit 1"));
        return duplicated == null ? null : duplicated.getId();
    }

    @Override
    public boolean isPurchased(Long userId, Long predictionId) {
        if (userId == null) {
            return false;
        }
        return orderMapper.selectCount(new LambdaQueryWrapper<OrdPredictionOrder>()
                .eq(OrdPredictionOrder::getUserId, userId)
                .eq(OrdPredictionOrder::getPredictionId, predictionId)
                .in(OrdPredictionOrder::getStatus,
                        OrdPredictionOrder.STATUS_PAID, OrdPredictionOrder.STATUS_SETTLED)) > 0;
    }

    @Override
    public OrderDtos.OrderVO detail(String orderNo) {
        OrdPredictionOrder order = orderMapper.selectOne(
                new LambdaQueryWrapper<OrdPredictionOrder>().eq(OrdPredictionOrder::getOrderNo, orderNo));
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND, "订单不存在");
        }
        Long userId = SecurityUtils.currentUserId();
        if (!order.getUserId().equals(userId) && !SecurityUtils.currentUser().isAdmin()) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        return toVO(order, predictionMapper.selectById(order.getPredictionId()));
    }

    @Override
    public PageResult<OrderDtos.OrderVO> mine(long pageNum, long pageSize) {
        Long userId = SecurityUtils.currentUserId();
        Page<OrdPredictionOrder> page = orderMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<OrdPredictionOrder>()
                        .eq(OrdPredictionOrder::getUserId, userId)
                        .orderByDesc(OrdPredictionOrder::getId));
        return PageResult.of(page.getTotal(), pageNum, pageSize, enrich(page.getRecords()));
    }

    @Override
    @Transactional
    public int refundByPrediction(Long predictionId, String reason) {
        List<OrdPredictionOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<OrdPredictionOrder>()
                .eq(OrdPredictionOrder::getPredictionId, predictionId)
                .eq(OrdPredictionOrder::getStatus, OrdPredictionOrder.STATUS_PAID));
        int count = 0;
        for (OrdPredictionOrder order : orders) {
            pointService.earn(order.getUserId(), order.getPricePoints(), "REFUND", order.getOrderNo(),
                    reason == null ? "预测撤回/下架退款" : reason);
            order.setStatus(OrdPredictionOrder.STATUS_REFUNDED);
            order.setRefundTime(LocalDateTime.now());
            orderMapper.updateById(order);
            try {
                messageService.orderRefunded(order.getUserId(), order.getId(), order.getPricePoints(), reason);
            } catch (Exception e) {
                log.warn("退款消息推送失败 orderNo={} err={}", order.getOrderNo(), e.getMessage());
            }
            count++;
        }
        if (count > 0) {
            // 退款后回退买家数，否则页面上的「已解锁 N 人」会虚高
            predictionMapper.update(null,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PrdPrediction>()
                            .eq(PrdPrediction::getId, predictionId)
                            .setSql("buyer_count = GREATEST(buyer_count - " + count + ", 0)"));
        }
        log.info("预测 {} 退款完成，共 {} 笔", predictionId, count);
        return count;
    }

    private List<OrderDtos.OrderVO> enrich(List<OrdPredictionOrder> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }
        List<Long> ids = orders.stream().map(OrdPredictionOrder::getPredictionId).distinct().collect(Collectors.toList());
        Map<Long, PrdPrediction> predictionMap = new LinkedHashMap<>();
        for (PrdPrediction p : predictionMapper.selectBatchIds(ids)) {
            predictionMap.put(p.getId(), p);
        }
        return orders.stream().map(o -> toVO(o, predictionMap.get(o.getPredictionId()))).collect(Collectors.toList());
    }

    private OrderDtos.OrderVO toVO(OrdPredictionOrder order, PrdPrediction prediction) {
        return new OrderDtos.OrderVO(order.getOrderNo(), order.getPredictionId(),
                prediction == null ? null : prediction.getTitle(), order.getTypeCode(),
                prediction == null ? null : prediction.getIssueNo(), order.getPricePoints(),
                order.getCreatorIncome(), order.getStatus(), statusName(order.getStatus()),
                order.getPayTime(), order.getCreatedAt());
    }

    private String statusName(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case 1 -> "待支付";
            case 2 -> "已支付";
            case 3 -> "已退款";
            case 4 -> "已关闭";
            case 5 -> "已结算";
            default -> "未知";
        };
    }

    /** 供结算任务使用：该期次某创作者待结算订单 */
    public List<OrdPredictionOrder> findPaidOrders(Long issueId, Long creatorId) {
        LambdaQueryWrapper<OrdPredictionOrder> query = new LambdaQueryWrapper<OrdPredictionOrder>()
                .eq(OrdPredictionOrder::getIssueId, issueId)
                .eq(OrdPredictionOrder::getStatus, OrdPredictionOrder.STATUS_PAID)
                .ne(OrdPredictionOrder::getRiskFlag, 2);
        if (creatorId != null) {
            query.eq(OrdPredictionOrder::getCreatorId, creatorId);
        }
        return new ArrayList<>(orderMapper.selectList(query));
    }

    @Override
    public long countByStatus(int status) {
        Long count = orderMapper.selectCount(
                new LambdaQueryWrapper<OrdPredictionOrder>().eq(OrdPredictionOrder::getStatus, status));
        return count == null ? 0 : count;
    }
}
