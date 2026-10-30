package com.ttdemo.modules.random.controller;

import com.ttdemo.common.api.R;
import com.ttdemo.modules.random.dto.RandomDtos;
import com.ttdemo.modules.random.service.RandomPredictionService;
import com.ttdemo.security.LoginUser;
import com.ttdemo.security.SecurityUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 随机预测生成器：基础方式游客可用，统计/玄学方式为会员专享（非会员每日限次试用）。
 */
@RestController
@RequestMapping("/api/v1/random")
public class RandomController {

    private final RandomPredictionService randomPredictionService;

    public RandomController(RandomPredictionService randomPredictionService) {
        this.randomPredictionService = randomPredictionService;
    }

    @GetMapping("/methods")
    public R<List<RandomDtos.MethodVO>> methods(@RequestParam(required = false) String typeCode) {
        return R.ok(randomPredictionService.methods(typeCode, currentUserId()));
    }

    /** 当前用户的会员状态与今日剩余免费次数 */
    @GetMapping("/quota")
    public R<RandomDtos.QuotaVO> quota() {
        return R.ok(randomPredictionService.quota(currentUserId()));
    }

    /** 历史数据概况，用于前端展示「库中可用期数」与明细核对 */
    @GetMapping("/history")
    public R<RandomDtos.HistorySummary> history(@RequestParam String typeCode,
                                                @RequestParam(defaultValue = "50") int limit) {
        return R.ok(randomPredictionService.history(typeCode, limit));
    }

    @PostMapping("/generate")
    public R<RandomDtos.GenerateResult> generate(@RequestBody RandomDtos.GenerateRequest request) {
        return R.ok(randomPredictionService.generate(request, currentUserId()));
    }

    /** 游客返回 null，由 service 决定是否放行 */
    private Long currentUserId() {
        LoginUser login = SecurityUtils.currentUserOrNull();
        return login == null ? null : login.id();
    }
}
