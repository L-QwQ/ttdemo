package com.ttdemo.modules.reward.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ttdemo.common.api.PageResult;
import com.ttdemo.common.api.ResultCode;
import com.ttdemo.common.exception.BizException;
import com.ttdemo.common.util.IdGen;
import com.ttdemo.modules.point.service.PointService;
import com.ttdemo.modules.reward.dto.RewardDtos;
import com.ttdemo.modules.reward.entity.AccRewardItem;
import com.ttdemo.modules.reward.entity.AccRewardOrder;
import com.ttdemo.modules.reward.mapper.AccRewardItemMapper;
import com.ttdemo.modules.reward.mapper.AccRewardOrderMapper;
import com.ttdemo.modules.reward.service.RewardService;
import com.ttdemo.modules.system.service.SysConfigService;
import com.ttdemo.modules.user.dto.UserVO;
import com.ttdemo.modules.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RewardServiceImpl implements RewardService {

    private static final String CFG_ENABLED = "reward.exchange.enabled";
    private static final String CFG_MIN_POINTS = "reward.exchange.min.points";
    private static final String CFG_DAILY_LIMIT = "reward.exchange.daily.limit";
    private static final String BIZ_EXCHANGE = "REWARD_EXCHANGE";
    private static final String BIZ_CANCEL = "REWARD_CANCEL";

    private final AccRewardItemMapper itemMapper;
    private final AccRewardOrderMapper orderMapper;
    private final PointService pointService;
    private final UserService userService;
    private final SysConfigService configService;
    private final com.ttdemo.modules.message.service.MessageService messageService;

    public RewardServiceImpl(AccRewardItemMapper itemMapper, AccRewardOrderMapper orderMapper,
                             PointService pointService, UserService userService,
                             SysConfigService configService,
                             com.ttdemo.modules.message.service.MessageService messageService) {
        this.messageService = messageService;
        this.itemMapper = itemMapper;
        this.orderMapper = orderMapper;
        this.pointService = pointService;
        this.userService = userService;
        this.configService = configService;
    }

    @Override
    public List<RewardDtos.ItemVO> listOnline(Long userId) {
        long available = userId == null ? 0 : pointService.balance(userId);
        List<AccRewardItem> items = itemMapper.selectList(new LambdaQueryWrapper<AccRewardItem>()
                .eq(AccRewardItem::getStatus, AccRewardItem.STATUS_ON)
                .orderByAsc(AccRewardItem::getSortNo)
                .orderByAsc(AccRewardItem::getId));
        return items.stream().map(item -> RewardDtos.ItemVO.from(item, available)).toList();
    }

    @Override
    public RewardDtos.SummaryVO summary(Long userId) {
        Map<String, Object> account = pointService.accountView(userId);
        UserVO profile = userService.getProfile(userId);

        List<AccRewardOrder> mine = orderMapper.selectList(new LambdaQueryWrapper<AccRewardOrder>()
                .eq(AccRewardOrder::getUserId, userId));
        long exchangedPoints = mine.stream()
                .filter(o -> o.getStatus() != null && o.getStatus() != AccRewardOrder.STATUS_CANCELED)
                .mapToLong(o -> o.getPointsCost() == null ? 0 : o.getPointsCost()).sum();
        long exchangedCount = mine.stream()
                .filter(o -> o.getStatus() != null && o.getStatus() != AccRewardOrder.STATUS_CANCELED)
                .count();
        int pendingCount = (int) mine.stream()
                .filter(o -> o.getStatus() != null && o.getStatus() == AccRewardOrder.STATUS_PENDING)
                .count();

        LocalDateTime now = LocalDateTime.now();
        boolean vip = profile != null && profile.vipExpireAt() != null && profile.vipExpireAt().isAfter(now);
        int itemCount = Math.toIntExact(itemMapper.selectCount(new LambdaQueryWrapper<AccRewardItem>()
                .eq(AccRewardItem::getStatus, AccRewardItem.STATUS_ON)));

        long todayCount = orderMapper.selectCount(new LambdaQueryWrapper<AccRewardOrder>()
                .eq(AccRewardOrder::getUserId, userId)
                .ge(AccRewardOrder::getCreatedAt, LocalDate.now().atStartOfDay())
                .ne(AccRewardOrder::getStatus, AccRewardOrder.STATUS_CANCELED));

        return new RewardDtos.SummaryVO(
                asLong(account.get("available")),
                asLong(account.get("totalIncome")),
                asLong(account.get("totalRecharge")),
                asLong(account.get("totalConsume")),
                vip,
                profile == null ? null : profile.vipExpireAt(),
                exchangedPoints,
                exchangedCount,
                itemCount,
                pendingCount,
                configService.getBoolean(CFG_ENABLED, true),
                configService.getLong(CFG_MIN_POINTS, 100L),
                configService.getLong(CFG_DAILY_LIMIT, 5L),
                todayCount);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RewardDtos.OrderVO exchange(Long userId, RewardDtos.ExchangeRequest request) {
        if (!configService.getBoolean(CFG_ENABLED, true)) {
            throw new BizException(ResultCode.STATE_CONFLICT, "积分兑换暂未开放");
        }
        AccRewardItem item = itemMapper.selectById(request.itemId());
        if (item == null || item.getStatus() == null || item.getStatus() != AccRewardItem.STATUS_ON) {
            throw new BizException(ResultCode.NOT_FOUND, "该权益不存在或已下架");
        }
        int quantity = request.quantity() == null ? 1 : request.quantity();
        long totalPoints = item.getPointsCost() * quantity;

        long minPoints = configService.getLong(CFG_MIN_POINTS, 100L);
        if (totalPoints < minPoints) {
            throw new BizException(ResultCode.BAD_REQUEST, "单次兑换不得低于 " + minPoints + " 积分");
        }

        long dailyLimit = configService.getLong(CFG_DAILY_LIMIT, 5L);
        long todayCount = orderMapper.selectCount(new LambdaQueryWrapper<AccRewardOrder>()
                .eq(AccRewardOrder::getUserId, userId)
                .ge(AccRewardOrder::getCreatedAt, LocalDate.now().atStartOfDay())
                .ne(AccRewardOrder::getStatus, AccRewardOrder.STATUS_CANCELED));
        if (todayCount >= dailyLimit) {
            throw new BizException(ResultCode.STATE_CONFLICT, "今日兑换次数已达上限（" + dailyLimit + " 次）");
        }

        boolean isVip = item.getCategory() != null && item.getCategory() == AccRewardItem.CATEGORY_VIP;
        if (!isVip) {
            // 实物礼品必须留收货信息，否则无法发货
            if (!StringUtils.hasText(request.contactName())
                    || !StringUtils.hasText(request.contactPhone())
                    || !StringUtils.hasText(request.address())) {
                throw new BizException(ResultCode.BAD_REQUEST, "实物礼品需要填写收货人、电话和地址");
            }
        }

        // 先原子扣库存，防超卖（条件更新，影响行数为 0 即库存不足）
        if (itemMapper.takeStock(item.getId(), quantity) == 0) {
            throw new BizException(ResultCode.STATE_CONFLICT, "该权益库存不足");
        }

        String orderNo = IdGen.rewardNo();
        // 积分变动必须走 PointService（写流水 + 批次），余额不足会抛 60001，事务回滚库存
        pointService.consume(userId, totalPoints, BIZ_EXCHANGE, orderNo, "兑换权益：" + item.getName());

        AccRewardOrder order = new AccRewardOrder();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setItemId(item.getId());
        order.setItemName(item.getName());
        order.setCategory(item.getCategory());
        order.setPointsCost(totalPoints);
        order.setValueFen((item.getValueFen() == null ? 0 : item.getValueFen()) * quantity);
        order.setQuantity(quantity);
        order.setContactName(trim(request.contactName()));
        order.setContactPhone(trim(request.contactPhone()));
        order.setAddress(trim(request.address()));

        if (isVip) {
            userService.grantVipDays(userId, (item.getDurationDays() == null ? 0 : item.getDurationDays()) * quantity);
            order.setStatus(AccRewardOrder.STATUS_ISSUED);
            order.setIssuedAt(LocalDateTime.now());
            order.setIssueRemark("系统自动发放");
        } else {
            order.setStatus(AccRewardOrder.STATUS_PENDING);
        }
        orderMapper.insert(order);
        if (isVip) {
            safeNotify(() -> messageService.rewardIssued(userId, order.getId(), item.getName(),
                    "会员已生效，可在个人中心查看到期时间"));
        }
        return RewardDtos.OrderVO.from(order);
    }

    @Override
    public PageResult<RewardDtos.OrderVO> myOrders(Long userId, long pageNum, long pageSize) {
        Page<AccRewardOrder> page = orderMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<AccRewardOrder>()
                        .eq(AccRewardOrder::getUserId, userId)
                        .orderByDesc(AccRewardOrder::getId));
        List<RewardDtos.OrderVO> list = page.getRecords().stream().map(RewardDtos.OrderVO::from).toList();
        return PageResult.of(page.getTotal(), pageNum, pageSize, list);
    }

    @Override
    public List<AccRewardItem> listAllItems() {
        return itemMapper.selectList(new LambdaQueryWrapper<AccRewardItem>()
                .orderByAsc(AccRewardItem::getStatus)
                .orderByAsc(AccRewardItem::getSortNo)
                .orderByAsc(AccRewardItem::getId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AccRewardItem saveItem(Long id, RewardDtos.ItemRequest request) {
        AccRewardItem item = id == null ? new AccRewardItem() : itemMapper.selectById(id);
        if (item == null) {
            throw new BizException(ResultCode.NOT_FOUND, "权益商品不存在");
        }
        if (id == null) {
            Long exists = itemMapper.selectCount(new LambdaQueryWrapper<AccRewardItem>()
                    .eq(AccRewardItem::getItemCode, request.itemCode()));
            if (exists != null && exists > 0) {
                throw new BizException(ResultCode.STATE_CONFLICT, "商品编码已存在");
            }
        }
        item.setItemCode(request.itemCode().trim());
        item.setName(request.name().trim());
        item.setCategory(request.category());
        item.setPointsCost(request.pointsCost());
        item.setValueFen(request.valueFen() == null ? 0 : request.valueFen());
        item.setDurationDays(request.durationDays() == null ? 0 : request.durationDays());
        item.setStock(request.stock() == null ? -1 : request.stock());
        item.setDescription(request.description());
        item.setStatus(request.status() == null ? AccRewardItem.STATUS_ON : request.status());
        item.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
        if (id == null) {
            item.setExchangedCount(0);
            itemMapper.insert(item);
        } else {
            itemMapper.updateById(item);
        }
        return item;
    }

    @Override
    public PageResult<RewardDtos.OrderVO> listOrders(long pageNum, long pageSize, Integer status, String keyword) {
        LambdaQueryWrapper<AccRewardOrder> query = new LambdaQueryWrapper<AccRewardOrder>()
                .eq(status != null, AccRewardOrder::getStatus, status)
                .like(StringUtils.hasText(keyword), AccRewardOrder::getOrderNo, keyword)
                .orderByDesc(AccRewardOrder::getId);
        Page<AccRewardOrder> page = orderMapper.selectPage(new Page<>(pageNum, pageSize), query);

        // 批量补用户手机号/昵称，避免 N+1
        Set<Long> userIds = new HashSet<>();
        page.getRecords().forEach(o -> userIds.add(o.getUserId()));
        Map<Long, UserVO> users = new HashMap<>();
        if (!userIds.isEmpty()) {
            userService.listByIds(new ArrayList<>(userIds)).forEach(u -> users.put(u.id(), u));
        }
        List<RewardDtos.OrderVO> list = page.getRecords().stream()
                .map(o -> {
                    UserVO u = users.get(o.getUserId());
                    return RewardDtos.OrderVO.from(o).withUser(u == null ? null : u.phone(), u == null ? null : u.nickname());
                })
                .toList();
        return PageResult.of(page.getTotal(), pageNum, pageSize, list);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void issue(Long orderId, String remark) {
        AccRewardOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND, "兑换单不存在");
        }
        if (order.getStatus() != null && order.getStatus() == AccRewardOrder.STATUS_ISSUED) {
            throw new BizException(ResultCode.STATE_CONFLICT, "该兑换单已发放");
        }
        if (order.getStatus() != null && order.getStatus() == AccRewardOrder.STATUS_CANCELED) {
            throw new BizException(ResultCode.STATE_CONFLICT, "该兑换单已取消");
        }
        orderMapper.update(null, new LambdaUpdateWrapper<AccRewardOrder>()
                .eq(AccRewardOrder::getId, orderId)
                .eq(AccRewardOrder::getStatus, AccRewardOrder.STATUS_PENDING)
                .set(AccRewardOrder::getStatus, AccRewardOrder.STATUS_ISSUED)
                .set(AccRewardOrder::getIssuedAt, LocalDateTime.now())
                .set(AccRewardOrder::getIssueRemark, StringUtils.hasText(remark) ? remark : "已发放"));

        safeNotify(() -> messageService.rewardIssued(order.getUserId(), orderId, order.getItemName(), remark));
    }
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long orderId, String remark) {
        AccRewardOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND, "兑换单不存在");
        }
        if (order.getStatus() != null && order.getStatus() == AccRewardOrder.STATUS_CANCELED) {
            throw new BizException(ResultCode.STATE_CONFLICT, "该兑换单已取消");
        }
        // 会员类发放后不可撤销：天数已叠加进账号，回收会牵连用户可见权益
        if (order.getCategory() != null && order.getCategory() == AccRewardItem.CATEGORY_VIP
                && order.getStatus() != null && order.getStatus() == AccRewardOrder.STATUS_ISSUED) {
            throw new BizException(ResultCode.STATE_CONFLICT, "会员权益已发放，不能取消，请联系用户协商");
        }
        orderMapper.update(null, new LambdaUpdateWrapper<AccRewardOrder>()
                .eq(AccRewardOrder::getId, orderId)
                .ne(AccRewardOrder::getStatus, AccRewardOrder.STATUS_CANCELED)
                .set(AccRewardOrder::getStatus, AccRewardOrder.STATUS_CANCELED)
                .set(AccRewardOrder::getIssueRemark, StringUtils.hasText(remark) ? remark : "运营取消"));

        // 退回积分（biz_type+biz_no 唯一索引天然防重复退）并归还库存
        pointService.earn(order.getUserId(), order.getPointsCost(), BIZ_CANCEL, order.getOrderNo(), "兑换取消退回积分");
        itemMapper.returnStock(order.getItemId(), order.getQuantity() == null ? 1 : order.getQuantity());
        safeNotify(() -> messageService.rewardCanceled(order.getUserId(), orderId, order.getItemName(),
                order.getPointsCost()));
    }

    /** 站内信是附属能力，推送失败不能影响兑换主流程 */
    private void safeNotify(Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            // 静默：消息缺失不影响权益与账务
        }
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }
}
