package com.ttdemo.modules.point.controller;

import com.ttdemo.common.api.PageResult;
import com.ttdemo.common.api.R;
import com.ttdemo.modules.point.entity.AccPointTransaction;
import com.ttdemo.modules.point.service.PointService;
import com.ttdemo.security.SecurityUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/points")
public class PointController {

    private final PointService pointService;

    public PointController(PointService pointService) {
        this.pointService = pointService;
    }

    @GetMapping("/account")
    public R<Map<String, Object>> account() {
        return R.ok(pointService.accountView(SecurityUtils.currentUserId()));
    }

    @GetMapping("/transactions")
    public R<PageResult<AccPointTransaction>> transactions(@RequestParam(defaultValue = "1") long pageNum,
                                                            @RequestParam(defaultValue = "10") long pageSize,
                                                            @RequestParam(required = false) String bizType) {
        return R.ok(pointService.transactions(SecurityUtils.currentUserId(), pageNum, pageSize, bizType));
    }

    /** 充值档位：前端展示用，金额单位分 */
    @GetMapping("/recharge-config")
    public R<List<Map<String, Object>>> rechargeConfig() {
        return R.ok(List.of(
                tier(600), tier(3000), tier(6800), tier(12800), tier(32800)));
    }

    private Map<String, Object> tier(int amountFen) {
        int rate = 10;
        long points = (long) amountFen / 100 * rate;
        return Map.of("amountFen", amountFen, "amountYuan", amountFen / 100,
                "points", points, "rate", rate);
    }
}
