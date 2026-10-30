package com.ttdemo.modules.finance.service;

import com.ttdemo.common.api.PageResult;
import com.ttdemo.common.api.ResultCode;
import com.ttdemo.common.exception.BizException;
import com.ttdemo.modules.finance.dto.FinDailyRow;
import com.ttdemo.modules.finance.dto.FinFlowRow;
import com.ttdemo.modules.finance.dto.FinSumRow;
import com.ttdemo.modules.finance.dto.MoneyOverviewVO;
import com.ttdemo.modules.finance.mapper.FinMoneyMapper;
import com.ttdemo.modules.system.service.SysConfigService;
import com.ttdemo.modules.user.dto.UserVO;
import com.ttdemo.modules.user.service.UserService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 真实资金口径统计：把积分按充值比例折算成人民币展示。
 * 现金流入只有充值一条通道；权益兑换是平台付出的成本；不存在现金流出。
 */
@Service
public class FinanceMoneyService {

    private final FinMoneyMapper moneyMapper;
    private final SysConfigService configService;
    private final UserService userService;

    public FinanceMoneyService(FinMoneyMapper moneyMapper, SysConfigService configService, UserService userService) {
        this.moneyMapper = moneyMapper;
        this.configService = configService;
        this.userService = userService;
    }

    public MoneyOverviewVO overview(String startDate, String endDate) {
        LocalDate[] range = resolveRange(startDate, endDate);
        LocalDateTime from = range[0].atStartOfDay();
        LocalDateTime to = range[1].plusDays(1).atStartOfDay();

        long pointRate = configService.getLong("point.to.fen.rate", 10L);
        if (pointRate <= 0) {
            pointRate = 10L;
        }

        FinSumRow recharge = moneyMapper.sumRecharge(from, to);
        FinSumRow exchange = moneyMapper.sumExchange(from, to);
        FinSumRow consume = moneyMapper.sumConsume(from, to);
        FinSumRow settlement = moneyMapper.sumSettlement(from, to);
        FinSumRow liability = moneyMapper.sumUserAvailable();
        FinSumRow pending = moneyMapper.countPendingRewards();

        long cashInFen = recharge.getAmountFen();
        long exchangeFen = exchange.getAmountFen();
        long netPlatformFen = cashInFen - exchangeFen;

        return new MoneyOverviewVO(
                configService.getString("finance.money.currency", "CNY"),
                pointRate,
                cashInFen,
                0L,
                recharge.getCnt(),
                recharge.getPoints(),
                consume.getPoints(),
                pointsToFen(consume.getPoints(), pointRate),
                consume.getCnt(),
                settlement.getFeePoints(),
                pointsToFen(settlement.getFeePoints(), pointRate),
                settlement.getCreatorPoints(),
                pointsToFen(settlement.getCreatorPoints(), pointRate),
                settlement.getGrossPoints(),
                pointsToFen(settlement.getGrossPoints(), pointRate),
                settlement.getCnt(),
                exchange.getPoints(),
                exchangeFen,
                exchange.getCnt(),
                pending.getCnt(),
                netPlatformFen,
                liability.getPoints(),
                pointsToFen(liability.getPoints(), pointRate),
                liability.getCnt(),
                "积分不可提现、不可转让、不可兑换现金；本页金额按 1 元 = " + pointRate
                        + " 积分折算，仅用于成本与收入口径统计，不构成任何出款承诺。",
                trend(from, to, pointRate));
    }

    public PageResult<Map<String, Object>> flows(String startDate, String endDate, String bizType,
                                                 long pageNum, long pageSize) {
        LocalDate[] range = resolveRange(startDate, endDate);
        LocalDateTime from = range[0].atStartOfDay();
        LocalDateTime to = range[1].plusDays(1).atStartOfDay();

        long total = moneyMapper.countFlows(from, to, bizType);
        long offset = Math.max(0, (pageNum - 1) * pageSize);
        List<FinFlowRow> rows = moneyMapper.selectFlows(from, to, bizType, offset, pageSize);

        Set<Long> userIds = new HashSet<>();
        rows.forEach(r -> {
            if (r.getUserId() != null) {
                userIds.add(r.getUserId());
            }
        });
        Map<Long, UserVO> users = new HashMap<>();
        if (!userIds.isEmpty()) {
            userService.listByIds(new ArrayList<>(userIds)).forEach(u -> users.put(u.id(), u));
        }

        List<Map<String, Object>> list = new ArrayList<>(rows.size());
        for (FinFlowRow row : rows) {
            UserVO user = users.get(row.getUserId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("bizType", row.getBizType());
            item.put("bizTypeName", bizTypeName(row.getBizType()));
            item.put("bizNo", row.getBizNo());
            item.put("userId", row.getUserId());
            item.put("userPhone", user == null ? null : user.phone());
            item.put("userNickname", user == null ? null : user.nickname());
            item.put("points", row.getPoints());
            item.put("amountFen", row.getAmountFen());
            item.put("status", row.getStatus());
            item.put("statusName", statusName(row.getBizType(), row.getStatus()));
            item.put("bizTime", row.getBizTime());
            list.add(item);
        }
        return PageResult.of(total, pageNum, pageSize, list);
    }

    private List<MoneyOverviewVO.DailyVO> trend(LocalDateTime from, LocalDateTime to, long pointRate) {
        Map<LocalDate, long[]> map = new LinkedHashMap<>();
        for (FinDailyRow row : moneyMapper.dailyRecharge(from, to)) {
            map.computeIfAbsent(row.getDay(), k -> new long[3])[0] = row.getAmountFen();
        }
        for (FinDailyRow row : moneyMapper.dailyExchange(from, to)) {
            long[] slot = map.computeIfAbsent(row.getDay(), k -> new long[3]);
            slot[1] = row.getAmountFen();
            slot[2] = row.getPoints();
        }
        List<MoneyOverviewVO.DailyVO> list = new ArrayList<>(map.size());
        map.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> list.add(new MoneyOverviewVO.DailyVO(
                        e.getKey().toString(),
                        e.getValue()[0],
                        e.getValue()[1],
                        e.getValue()[0] - e.getValue()[1],
                        e.getValue()[2])));
        return list;
    }

    private static long pointsToFen(long points, long pointRate) {
        return pointRate <= 0 ? 0 : points * 100 / pointRate;
    }

    private static LocalDate[] resolveRange(String startDate, String endDate) {
        LocalDate end = parse(endDate, LocalDate.now());
        LocalDate start = parse(startDate, end.minusDays(29));
        if (start.isAfter(end)) {
            throw new BizException(ResultCode.BAD_REQUEST, "开始日期不能晚于结束日期");
        }
        if (start.plusDays(366).isBefore(end)) {
            throw new BizException(ResultCode.BAD_REQUEST, "查询区间不能超过 366 天");
        }
        return new LocalDate[]{start, end};
    }

    private static LocalDate parse(String value, LocalDate fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(value);
        } catch (Exception e) {
            throw new BizException(ResultCode.BAD_REQUEST, "日期格式应为 yyyy-MM-dd");
        }
    }

    private static String bizTypeName(String bizType) {
        if (bizType == null) {
            return "未知";
        }
        return switch (bizType) {
            case "RECHARGE" -> "积分充值";
            case "REWARD" -> "权益兑换";
            case "SETTLE" -> "创作者结算";
            default -> bizType;
        };
    }

    private static String statusName(String bizType, Integer status) {
        if (status == null) {
            return "-";
        }
        return switch (bizType) {
            case "RECHARGE" -> switch (status) {
                case 1 -> "待支付";
                case 2 -> "已支付";
                case 3 -> "已关闭";
                case 4 -> "已退款";
                default -> String.valueOf(status);
            };
            case "REWARD" -> switch (status) {
                case 1 -> "待发放";
                case 2 -> "已发放";
                case 3 -> "已取消";
                default -> String.valueOf(status);
            };
            default -> switch (status) {
                case 1 -> "待入账";
                case 2 -> "已入账";
                case 3 -> "已撤销";
                default -> String.valueOf(status);
            };
        };
    }
}
