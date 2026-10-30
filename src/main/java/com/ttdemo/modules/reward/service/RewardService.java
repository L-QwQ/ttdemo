package com.ttdemo.modules.reward.service;

import com.ttdemo.common.api.PageResult;
import com.ttdemo.modules.reward.dto.RewardDtos;
import com.ttdemo.modules.reward.entity.AccRewardItem;

import java.util.List;

/**
 * 积分兑换站内权益（会员天数 / 实物礼品）。
 * 合规边界：不提供任何形式的积分兑现金、提现、转让能力。
 */
public interface RewardService {

    /** 前台在售权益列表（带当前用户可兑换判断） */
    List<RewardDtos.ItemVO> listOnline(Long userId);

    /** 前台总览 */
    RewardDtos.SummaryVO summary(Long userId);

    /** 兑换 */
    RewardDtos.OrderVO exchange(Long userId, RewardDtos.ExchangeRequest request);

    /** 我的兑换记录 */
    PageResult<RewardDtos.OrderVO> myOrders(Long userId, long pageNum, long pageSize);

    // ---------- 后台 ----------

    List<AccRewardItem> listAllItems();

    AccRewardItem saveItem(Long id, RewardDtos.ItemRequest request);

    PageResult<RewardDtos.OrderVO> listOrders(long pageNum, long pageSize, Integer status, String keyword);

    /** 标记已发放 */
    void issue(Long orderId, String remark);

    /** 取消兑换：退回积分并归还库存 */
    void cancel(Long orderId, String remark);
}
