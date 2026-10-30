package com.ttdemo.modules.lottery.controller;

import com.ttdemo.common.api.R;
import com.ttdemo.modules.lottery.dto.IssueVO;
import com.ttdemo.modules.lottery.dto.LotteryTypeVO;
import com.ttdemo.modules.lottery.service.LotteryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C 端彩种与期次（全部免登录）
 */
@RestController
@RequestMapping("/api/v1/lottery")
public class LotteryController {

    private final LotteryService lotteryService;

    public LotteryController(LotteryService lotteryService) {
        this.lotteryService = lotteryService;
    }

    @GetMapping("/types")
    public R<List<LotteryTypeVO>> types() {
        return R.ok(lotteryService.listTypes(true));
    }

    @GetMapping("/issues")
    public R<List<IssueVO>> issues(@RequestParam(required = false) String typeCode,
                                   @RequestParam(required = false) Integer drawStatus,
                                   @RequestParam(defaultValue = "20") int limit) {
        return R.ok(lotteryService.listIssues(typeCode, drawStatus, limit));
    }

    @GetMapping("/issues/current")
    public R<List<IssueVO>> current(@RequestParam(required = false) String typeCode) {
        return R.ok(lotteryService.currentIssues(typeCode));
    }

    @GetMapping("/issues/{id}")
    public R<IssueVO> detail(@PathVariable Long id) {
        return R.ok(lotteryService.getIssue(id));
    }
}
