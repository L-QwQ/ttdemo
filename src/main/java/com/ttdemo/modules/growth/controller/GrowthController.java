package com.ttdemo.modules.growth.controller;

import com.ttdemo.common.api.R;
import com.ttdemo.modules.growth.service.GrowthService;
import com.ttdemo.security.SecurityUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 增长：签到与邀请
 */
@RestController
@RequestMapping("/api/v1/growth")
public class GrowthController {

    private final GrowthService growthService;

    public GrowthController(GrowthService growthService) {
        this.growthService = growthService;
    }

    @GetMapping("/sign-in")
    public R<Map<String, Object>> signInStatus() {
        return R.ok(growthService.signInStatus(SecurityUtils.currentUserId()));
    }

    @PostMapping("/sign-in")
    public R<Map<String, Object>> signIn() {
        return R.ok(growthService.signIn(SecurityUtils.currentUserId()));
    }

    @GetMapping("/invite")
    public R<Map<String, Object>> invite() {
        return R.ok(growthService.inviteInfo(SecurityUtils.currentUserId()));
    }
}
