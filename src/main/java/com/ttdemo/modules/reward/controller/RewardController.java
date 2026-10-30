package com.ttdemo.modules.reward.controller;

import com.ttdemo.common.api.PageResult;
import com.ttdemo.common.api.R;
import com.ttdemo.modules.reward.dto.RewardDtos;
import com.ttdemo.modules.reward.service.RewardService;
import com.ttdemo.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 积分兑换站内权益（C 端）。
 * 只兑换会员天数与实物礼品，不涉及现金出款。
 */
@RestController
@RequestMapping("/api/v1/rewards")
public class RewardController {

    private final RewardService rewardService;

    public RewardController(RewardService rewardService) {
        this.rewardService = rewardService;
    }

    /** 权益列表（需登录，返回当前用户是否够积分兑换） */
    @GetMapping("/items")
    public R<List<RewardDtos.ItemVO>> items() {
        return R.ok(rewardService.listOnline(SecurityUtils.currentUserId()));
    }

    /** 我的兑换总览 */
    @GetMapping("/summary")
    public R<RewardDtos.SummaryVO> summary() {
        return R.ok(rewardService.summary(SecurityUtils.currentUserId()));
    }

    /** 兑换 */
    @PostMapping("/orders")
    public R<RewardDtos.OrderVO> exchange(@Valid @RequestBody RewardDtos.ExchangeRequest request) {
        return R.ok(rewardService.exchange(SecurityUtils.currentUserId(), request));
    }

    /** 我的兑换记录 */
    @GetMapping("/orders")
    public R<PageResult<RewardDtos.OrderVO>> myOrders(@RequestParam(defaultValue = "1") long pageNum,
                                                      @RequestParam(defaultValue = "10") long pageSize) {
        return R.ok(rewardService.myOrders(SecurityUtils.currentUserId(), pageNum, pageSize));
    }
}
