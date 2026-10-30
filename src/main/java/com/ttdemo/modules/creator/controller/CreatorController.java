package com.ttdemo.modules.creator.controller;

import com.ttdemo.common.api.PageResult;
import com.ttdemo.common.api.R;
import com.ttdemo.modules.creator.dto.CreatorDtos;
import com.ttdemo.modules.creator.service.CreatorService;
import com.ttdemo.modules.settlement.entity.AccSettlement;
import com.ttdemo.modules.settlement.service.SettlementService;
import com.ttdemo.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * C 端创作者中心
 */
@RestController
@RequestMapping("/api/v1")
public class CreatorController {

    private final CreatorService creatorService;
    private final SettlementService settlementService;

    public CreatorController(CreatorService creatorService, SettlementService settlementService) {
        this.creatorService = creatorService;
        this.settlementService = settlementService;
    }

    /** 创作者收益明细：按结算单逐笔列出（本期次 + 平台抽成 + 本人所得） */
    @GetMapping("/creator/settlements")
    public R<PageResult<AccSettlement>> settlements(@RequestParam(defaultValue = "1") long pageNum,
                                                    @RequestParam(defaultValue = "10") long pageSize) {
        Long userId = SecurityUtils.currentUserId();
        Long creatorId = creatorService.requireByUserId(userId).getId();
        return R.ok(settlementService.listByCreator(creatorId, pageNum, pageSize));
    }

    // ---------- 实名 ----------
    @PostMapping("/creator/realname")
    public R<Void> submitRealname(@Valid @RequestBody CreatorDtos.RealnameRequest request) {
        creatorService.submitRealname(request);
        return R.ok();
    }

    @GetMapping("/creator/realname")
    public R<Map<String, Object>> myRealname() {
        return R.ok(creatorService.myRealname());
    }

    // ---------- 申请与档案 ----------
    @PostMapping("/creator/apply")
    public R<Void> apply(@Valid @RequestBody CreatorDtos.ApplyRequest request) {
        creatorService.apply(request);
        return R.ok();
    }

    @GetMapping("/creator/profile")
    public R<CreatorDtos.ProfileVO> profile() {
        return R.ok(creatorService.myProfile());
    }

    @PutMapping("/creator/profile")
    public R<Void> updateProfile(@Valid @RequestBody CreatorDtos.ProfileUpdateRequest request) {
        creatorService.updateProfile(request);
        return R.ok();
    }

    @GetMapping("/creator/dashboard")
    public R<CreatorDtos.DashboardVO> dashboard() {
        return R.ok(creatorService.dashboard());
    }

    @GetMapping("/creator/accuracy")
    public R<CreatorDtos.AccuracyVO> accuracy() {
        return R.ok(creatorService.accuracy());
    }

    // ---------- 创作者主页（公开） ----------
    @GetMapping("/creators/{id}")
    public R<CreatorDtos.CreatorHomeVO> home(@PathVariable Long id) {
        return R.ok(creatorService.home(id));
    }

    @PostMapping("/creators/{id}/follow")
    public R<Void> follow(@PathVariable Long id) {
        creatorService.follow(id);
        return R.ok();
    }

    @DeleteMapping("/creators/{id}/follow")
    public R<Void> unfollow(@PathVariable Long id) {
        creatorService.unfollow(id);
        return R.ok();
    }
}
