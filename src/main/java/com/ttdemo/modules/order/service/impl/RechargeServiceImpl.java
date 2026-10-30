package com.ttdemo.modules.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ttdemo.common.api.ResultCode;
import com.ttdemo.common.exception.BizException;
import com.ttdemo.common.util.IdGen;
import com.ttdemo.common.web.RequestUtils;
import com.ttdemo.modules.order.dto.OrderDtos;
import com.ttdemo.modules.order.entity.OrdPayNotifyLog;
import com.ttdemo.modules.order.entity.OrdRechargeOrder;
import com.ttdemo.modules.order.mapper.OrdPayNotifyLogMapper;
import com.ttdemo.modules.order.mapper.OrdRechargeOrderMapper;
import com.ttdemo.modules.order.service.RechargeService;
import com.ttdemo.modules.point.service.PointService;
import com.ttdemo.modules.system.service.SysConfigService;
import com.ttdemo.modules.user.entity.SysUser;
import com.ttdemo.modules.user.mapper.SysUserMapper;
import com.ttdemo.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import com.ttdemo.modules.growth.service.GrowthService;
import com.ttdemo.modules.order.pay.WxPayClient;

@Service
public class RechargeServiceImpl implements RechargeService {

    private static final Logger log = LoggerFactory.getLogger(RechargeServiceImpl.class);

    private final OrdRechargeOrderMapper orderMapper;
    private final OrdPayNotifyLogMapper notifyLogMapper;
    private final SysUserMapper userMapper;
    private final PointService pointService;
    private final SysConfigService configService;

    private static final String DEFAULT_MOCK_SECRET = "ttdemo-mock-pay-secret";

    @Value("${app.pay.mock-secret:ttdemo-mock-pay-secret}")
    private String mockSecret;

    @Value("${app.pay.provider:mock}")
    private String payProvider;

    private final GrowthService growthService;
    private final WxPayClient wxPayClient;

    public RechargeServiceImpl(OrdRechargeOrderMapper orderMapper, OrdPayNotifyLogMapper notifyLogMapper,
                               SysUserMapper userMapper, PointService pointService,
                               SysConfigService configService,
                               GrowthService growthService,
                               WxPayClient wxPayClient) {
        this.orderMapper = orderMapper;
        this.notifyLogMapper = notifyLogMapper;
        this.userMapper = userMapper;
        this.pointService = pointService;
        this.configService = configService;
        this.growthService = growthService;
        this.wxPayClient = wxPayClient;
    }

    @Override
    @Transactional
    public OrderDtos.RechargeResult create(int amountFen, Integer payChannel) {
        if (!configService.getBoolean("feature.recharge.enabled", true)) {
            throw new BizException(ResultCode.STATE_CONFLICT, "充值功能已关闭");
        }
        Long userId = SecurityUtils.currentUserId();
        SysUser user = userMapper.selectById(userId);
        if (user != null && user.isMinor()) {
            throw new BizException(ResultCode.MINOR_FORBIDDEN);
        }
        int min = configService.getInt("point.recharge.min.fen", 600);
        int max = configService.getInt("point.recharge.max.fen", 200000);
        if (amountFen < min || amountFen > max) {
            throw new BizException(ResultCode.ILLEGAL_ARGUMENT,
                    String.format("单笔充值金额需在 %d-%d 元之间", min / 100, max / 100));
        }
        int dailyLimit = configService.getInt("point.recharge.daily.fen", 500000);
        long todaySum = orderMapper.selectList(new LambdaQueryWrapper<OrdRechargeOrder>()
                        .eq(OrdRechargeOrder::getUserId, userId)
                        .ge(OrdRechargeOrder::getCreatedAt, LocalDate.now().atStartOfDay())
                        .in(OrdRechargeOrder::getStatus,
                                OrdRechargeOrder.STATUS_PENDING, OrdRechargeOrder.STATUS_PAID))
                .stream().mapToLong(OrdRechargeOrder::getAmountFen).sum();
        if (todaySum + amountFen > dailyLimit) {
            throw new BizException(ResultCode.ILLEGAL_ARGUMENT, "超出每日充值上限");
        }

        int rate = configService.getInt("point.recharge.rate", 10);
        long points = (long) amountFen / 100 * rate;
        long bonus = 0;
        boolean firstRecharge = orderMapper.selectCount(new LambdaQueryWrapper<OrdRechargeOrder>()
                .eq(OrdRechargeOrder::getUserId, userId)
                .eq(OrdRechargeOrder::getStatus, OrdRechargeOrder.STATUS_PAID)) == 0;
        if (firstRecharge) {
            bonus = Math.round(points * 0.10);
        }

        OrdRechargeOrder order = new OrdRechargeOrder();
        order.setOrderNo(IdGen.rechargeNo());
        order.setUserId(userId);
        order.setAmountFen(amountFen);
        order.setPoints(points);
        order.setBonusPoints(bonus);
        order.setPayChannel(payChannel == null ? 9 : payChannel);
        order.setStatus(OrdRechargeOrder.STATUS_PENDING);
        order.setClientIp(RequestUtils.currentClientIp());
        orderMapper.insert(order);

        Map<String, String> payParams = new LinkedHashMap<>();
        payParams.put("provider", payProvider);
        payParams.put("orderNo", order.getOrderNo());
        payParams.put("amountFen", String.valueOf(amountFen));
        payParams.put("subject", "积分充值 " + points + " 积分");
        String mockPayUrl = null;
        if ("mock".equalsIgnoreCase(payProvider)) {
            payParams.put("prepayId", "mock_" + order.getOrderNo());
            payParams.put("sign", sign(order.getOrderNo() + amountFen));
            mockPayUrl = "/api/v1/recharge/orders/" + order.getOrderNo() + "/mock-pay";
        } else if ("wechat".equalsIgnoreCase(payProvider)) {
            // 真实通道：下单拿二维码链接，前端渲染二维码供用户扫码
            String codeUrl = wxPayClient.nativePay(order.getOrderNo(), amountFen,
                    "积分充值 " + points + " 积分");
            payParams.put("codeUrl", codeUrl);
            payParams.put("channel", "wechat");
        } else {
            throw new BizException(ResultCode.STATE_CONFLICT, "不支持的支付通道：" + payProvider);
        }

        return new OrderDtos.RechargeResult(order.getOrderNo(), amountFen, points, bonus,
                order.getPayChannel(), payParams, mockPayUrl);
    }

    @Override
    public OrderDtos.RechargeStatus query(String orderNo) {
        OrdRechargeOrder order = requireOwned(orderNo);
        return new OrderDtos.RechargeStatus(order.getOrderNo(), order.getAmountFen(), order.getPoints(),
                order.getBonusPoints(), order.getStatus(), statusName(order.getStatus()), order.getPayTime());
    }

    @Override
    @Transactional
    public boolean mockPay(String orderNo) {
        assertMockChannelOpen("模拟支付");
        OrdRechargeOrder order = requireOwned(orderNo);
        if (order.getStatus() == OrdRechargeOrder.STATUS_PAID) {
            return true;
        }
        if (order.getStatus() != OrdRechargeOrder.STATUS_PENDING) {
            throw new BizException(ResultCode.RECHARGE_HANDLED);
        }
        credit(order);
        return true;
    }

    @Override
    @Transactional
    public String notify(int channel, Map<String, String> body) {
        // 自签回调只允许 mock 通道使用；真实支付必须走 /notify/wechat 的微信验签，
        // 否则任何人都能拿着默认密钥伪造入账回调刷积分。
        assertMockChannelOpen("自签回调通道");
        String orderNo = body.get("orderNo");
        String sign = body.get("sign");
        String amountRaw = body.get("amountFen");
        OrdPayNotifyLog logEntity = new OrdPayNotifyLog();
        logEntity.setChannel(channel);
        logEntity.setOrderNo(orderNo);
        logEntity.setRawBody(body.toString());
        try {
            if (orderNo == null || sign == null || !sign.equals(sign(orderNo + amountRaw))) {
                logEntity.setSignValid(0);
                logEntity.setHandleResult("FAIL");
                logEntity.setErrorMsg("验签失败");
                notifyLogMapper.insert(logEntity);
                throw new BizException(ResultCode.FORBIDDEN, "验签失败");
            }
            logEntity.setSignValid(1);
            OrdRechargeOrder order = orderMapper.selectOne(
                    new LambdaQueryWrapper<OrdRechargeOrder>().eq(OrdRechargeOrder::getOrderNo, orderNo));
            if (order == null) {
                logEntity.setHandleResult("FAIL");
                logEntity.setErrorMsg("订单不存在");
                notifyLogMapper.insert(logEntity);
                throw new BizException(ResultCode.NOT_FOUND, "订单不存在");
            }
            int amount = amountRaw == null ? -1 : Integer.parseInt(amountRaw);
            if (amount != order.getAmountFen()) {
                logEntity.setHandleResult("FAIL");
                logEntity.setErrorMsg("金额不一致 order=" + order.getAmountFen() + " notify=" + amount);
                notifyLogMapper.insert(logEntity);
                throw new BizException(ResultCode.STATE_CONFLICT, "金额不一致");
            }
            if (order.getStatus() == OrdRechargeOrder.STATUS_PAID) {
                logEntity.setHandleResult("IGNORED");
                notifyLogMapper.insert(logEntity);
                return "SUCCESS";
            }
            credit(order);
            logEntity.setHandleResult("SUCCESS");
            notifyLogMapper.insert(logEntity);
            return "SUCCESS";
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            logEntity.setHandleResult("FAIL");
            logEntity.setErrorMsg(e.getMessage());
            notifyLogMapper.insert(logEntity);
            throw new BizException(ResultCode.ERROR, "回调处理失败");
        }
    }

    @Override
    @Transactional
    public String notifyWechat(String rawBody, String signature, String timestamp, String nonce) {
        OrdPayNotifyLog logEntity = new OrdPayNotifyLog();
        logEntity.setChannel(1);
        logEntity.setRawBody(rawBody);
        try {
            Map<String, String> data = wxPayClient.parseNotify(rawBody, signature, timestamp, nonce);
            String orderNo = data.get("orderNo");
            logEntity.setOrderNo(orderNo);
            if (!"SUCCESS".equalsIgnoreCase(data.get("tradeState"))) {
                logEntity.setSignValid(1);
                logEntity.setHandleResult("IGNORED");
                logEntity.setErrorMsg("非成功状态：" + data.get("tradeState"));
                notifyLogMapper.insert(logEntity);
                return "SUCCESS";
            }
            OrdRechargeOrder order = orderMapper.selectOne(
                    new LambdaQueryWrapper<OrdRechargeOrder>().eq(OrdRechargeOrder::getOrderNo, orderNo));
            if (order == null) {
                logEntity.setSignValid(1);
                logEntity.setHandleResult("FAIL");
                logEntity.setErrorMsg("订单不存在");
                notifyLogMapper.insert(logEntity);
                return "订单不存在";
            }
            int amount = Integer.parseInt(data.getOrDefault("amountFen", "-1"));
            if (amount != order.getAmountFen()) {
                logEntity.setSignValid(1);
                logEntity.setHandleResult("FAIL");
                logEntity.setErrorMsg("金额不一致 order=" + order.getAmountFen() + " notify=" + amount);
                notifyLogMapper.insert(logEntity);
                return "金额不一致";
            }
            if (order.getStatus() == OrdRechargeOrder.STATUS_PAID) {
                logEntity.setSignValid(1);
                logEntity.setHandleResult("IGNORED");
                notifyLogMapper.insert(logEntity);
                return "SUCCESS";
            }
            credit(order, data.get("transactionId"));
            logEntity.setSignValid(1);
            logEntity.setHandleResult("SUCCESS");
            notifyLogMapper.insert(logEntity);
            return "SUCCESS";
        } catch (BizException e) {
            logEntity.setSignValid(0);
            logEntity.setHandleResult("FAIL");
            logEntity.setErrorMsg(e.getMessage());
            notifyLogMapper.insert(logEntity);
            throw e;
        } catch (Exception e) {
            logEntity.setSignValid(0);
            logEntity.setHandleResult("FAIL");
            logEntity.setErrorMsg(e.getMessage());
            notifyLogMapper.insert(logEntity);
            throw new BizException(ResultCode.ERROR, "回调处理失败");
        }
    }

    private void credit(OrdRechargeOrder order) {
        credit(order, null);
    }

    private void credit(OrdRechargeOrder order, String channelTradeNo) {
        pointService.recharge(order.getUserId(), order.getPoints(), order.getBonusPoints(), order.getOrderNo());
        order.setStatus(OrdRechargeOrder.STATUS_PAID);
        order.setPayTime(LocalDateTime.now());
        order.setNotifyTime(LocalDateTime.now());
        order.setChannelTradeNo(channelTradeNo == null ? "MOCK" + order.getOrderNo() : channelTradeNo);
        orderMapper.updateById(order);
        log.info("充值入账 order={}, user={}, points={}+{}", order.getOrderNo(), order.getUserId(),
                order.getPoints(), order.getBonusPoints());
        // 邀请奖励：被邀请人首充后给邀请人追加积分（内部幂等，重复调用不会重复发放）
        try {
            growthService.onFirstRecharge(order.getUserId());
        } catch (Exception e) {
            log.warn("邀请首充奖励发放失败 userId={} err={}", order.getUserId(), e.getMessage());
        }
    }

    private OrdRechargeOrder requireOwned(String orderNo) {
        OrdRechargeOrder order = orderMapper.selectOne(
                new LambdaQueryWrapper<OrdRechargeOrder>().eq(OrdRechargeOrder::getOrderNo, orderNo));
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND, "充值订单不存在");
        }
        Long userId = SecurityUtils.currentUserId();
        if (!order.getUserId().equals(userId) && !SecurityUtils.currentUser().isAdmin()) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        return order;
    }

    /**
     * 自签回调与模拟支付的安全闸门。
     * <p>
     * 回调路径在 SecurityConfig 中匿名放行，若不限定通道，攻击者可用默认/泄露密钥
     * 为任意待支付充值单伪造入账（单号由时间戳+序号生成，可被枚举）。因此：
     * 非 mock 通道一律拒绝；mock 通道沿用默认密钥时输出告警提醒配置正式密钥。
     */
    private void assertMockChannelOpen(String scene) {
        if (!"mock".equalsIgnoreCase(payProvider)) {
            throw new BizException(ResultCode.STATE_CONFLICT,
                    "当前支付通道为 " + payProvider + "，" + scene + "已停用，请使用真实渠道回调");
        }
        if (DEFAULT_MOCK_SECRET.equals(mockSecret)) {
            log.warn("支付回调沿用默认 mock 密钥，存在伪造入账风险，请配置 app.pay.mock-secret");
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(mockSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("签名失败", e);
        }
    }

    private String statusName(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case 1 -> "待支付";
            case 2 -> "已支付";
            case 3 -> "已关闭";
            case 4 -> "已退款";
            default -> "未知";
        };
    }
}
