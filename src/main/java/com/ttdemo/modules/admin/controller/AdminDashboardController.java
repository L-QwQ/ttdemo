package com.ttdemo.modules.admin.controller;

import com.ttdemo.common.api.PageQuery;
import com.ttdemo.common.api.PageResult;
import com.ttdemo.common.api.R;
import com.ttdemo.modules.admin.dto.AdminPlatformStatsVO;
import com.ttdemo.modules.admin.dto.AdminStatsVO;
import com.ttdemo.modules.admin.service.AdminUserService;
import com.ttdemo.modules.creator.service.CreatorService;
import com.ttdemo.modules.log.entity.SysLoginLog;
import com.ttdemo.modules.log.service.LoginLogService;
import com.ttdemo.modules.lottery.service.LotteryService;
import com.ttdemo.modules.order.entity.OrdPredictionOrder;
import com.ttdemo.modules.order.service.OrderService;
import com.ttdemo.modules.prediction.entity.PrdPrediction;
import com.ttdemo.modules.prediction.service.PredictionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ttdemo.security.perm.Permissions;
import com.ttdemo.security.perm.RequirePerm;

/**
 * 后台概览与审计日志
 */
@RestController
@RequestMapping("/api/admin")
public class AdminDashboardController {

    private final AdminUserService adminUserService;
    private final LoginLogService loginLogService;
    private final PredictionService predictionService;
    private final CreatorService creatorService;
    private final OrderService orderService;
    private final LotteryService lotteryService;

    public AdminDashboardController(AdminUserService adminUserService, LoginLogService loginLogService,
                                    PredictionService predictionService, CreatorService creatorService,
                                    OrderService orderService, LotteryService lotteryService) {
        this.adminUserService = adminUserService;
        this.loginLogService = loginLogService;
        this.predictionService = predictionService;
        this.creatorService = creatorService;
        this.orderService = orderService;
        this.lotteryService = lotteryService;
    }

    @GetMapping("/stats/overview")
    @RequirePerm(Permissions.DASHBOARD_VIEW)
    public R<AdminStatsVO> overview() {
        return R.ok(adminUserService.stats());
    }

    /** 彩票平台维度统计（仪表盘第二组卡片） */
    @GetMapping("/stats/platform")
    @RequirePerm(Permissions.DASHBOARD_VIEW)
    public R<AdminPlatformStatsVO> platformStats() {
        long officialDrawn = lotteryService.countDrawnIssues(null, 1);
        AdminPlatformStatsVO vo = new AdminPlatformStatsVO(
                predictionService.countByStatus(PrdPrediction.STATUS_ONLINE),
                predictionService.countByStatus(PrdPrediction.STATUS_PENDING_AUDIT),
                predictionService.countByStatus(PrdPrediction.STATUS_SETTLED),
                creatorService.countCreators(),
                creatorService.countRealnameByStatus(0),
                orderService.countByStatus(OrdPredictionOrder.STATUS_PAID),
                lotteryService.countDrawnIssues(null, null),
                officialDrawn
        );
        return R.ok(vo);
    }

    @GetMapping("/logs")
    @RequirePerm(Permissions.DASHBOARD_VIEW)
    public R<PageResult<SysLoginLog>> logs(PageQuery query,
                                           @RequestParam(required = false) String phone,
                                           @RequestParam(required = false) Integer success) {
        return R.ok(loginLogService.page(query.getPage(), query.getSize(), phone, success));
    }
}
