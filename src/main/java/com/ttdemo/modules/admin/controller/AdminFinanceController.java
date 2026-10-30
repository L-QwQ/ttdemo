package com.ttdemo.modules.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ttdemo.common.api.PageResult;
import com.ttdemo.common.api.R;
import com.ttdemo.common.api.ResultCode;
import com.ttdemo.common.exception.BizException;
import com.ttdemo.modules.finance.dto.MoneyOverviewVO;
import com.ttdemo.modules.finance.service.FinanceMoneyService;
import com.ttdemo.modules.point.entity.AccPointTransaction;
import com.ttdemo.modules.point.service.PointService;
import com.ttdemo.modules.settlement.entity.AccSettlement;
import com.ttdemo.modules.settlement.service.SettlementService;
import com.ttdemo.modules.system.entity.SysAuditLog;
import com.ttdemo.modules.system.entity.SysConfig;
import com.ttdemo.modules.system.mapper.SysAuditLogMapper;
import com.ttdemo.modules.system.service.SysConfigService;
import com.ttdemo.security.SecurityUtils;
import com.ttdemo.security.perm.Permissions;
import com.ttdemo.security.perm.RequirePerm;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 财务与系统配置后台
 */
@RestController
@RequestMapping("/api/admin/v1")
public class AdminFinanceController {

    private final SettlementService settlementService;
    private final PointService pointService;
    private final SysConfigService configService;
    private final SysAuditLogMapper auditLogMapper;
    private final FinanceMoneyService financeMoneyService;

    public AdminFinanceController(SettlementService settlementService, PointService pointService,
                                  SysConfigService configService, SysAuditLogMapper auditLogMapper,
                                  FinanceMoneyService financeMoneyService) {
        this.settlementService = settlementService;
        this.pointService = pointService;
        this.configService = configService;
        this.auditLogMapper = auditLogMapper;
        this.financeMoneyService = financeMoneyService;
    }

    /** 真实资金总览：充值实收、平台抽成、创作者收益、权益兑换成本（金额按积分比例折算） */
    @GetMapping("/finance/money/overview")
    @RequirePerm(Permissions.POINT_VIEW)
    public R<MoneyOverviewVO> moneyOverview(@RequestParam(required = false) String startDate,
                                            @RequestParam(required = false) String endDate) {
        return R.ok(financeMoneyService.overview(startDate, endDate));
    }

    /** 资金流水：充值 / 权益兑换 / 创作者结算合并台账 */
    @GetMapping("/finance/money/flows")
    @RequirePerm(Permissions.POINT_VIEW)
    public R<PageResult<Map<String, Object>>> moneyFlows(@RequestParam(required = false) String startDate,
                                                         @RequestParam(required = false) String endDate,
                                                         @RequestParam(required = false) String bizType,
                                                         @RequestParam(defaultValue = "1") long pageNum,
                                                         @RequestParam(defaultValue = "20") long pageSize) {
        return R.ok(financeMoneyService.flows(startDate, endDate, bizType, pageNum, pageSize));
    }

    @GetMapping("/finance/reconciliation")
    @RequirePerm(Permissions.POINT_VIEW)
    public R<Map<String, Object>> reconciliation(@RequestParam(required = false) String startDate,
                                                 @RequestParam(required = false) String endDate,
                                                 @RequestParam(required = false) String typeCode) {
        return R.ok(settlementService.reconciliation(startDate, endDate, typeCode));
    }

    @GetMapping("/settlements")
    @RequirePerm(Permissions.SETTLEMENT_CONFIRM)
    public R<PageResult<AccSettlement>> settlements(@RequestParam(defaultValue = "1") long pageNum,
                                                    @RequestParam(defaultValue = "10") long pageSize,
                                                    @RequestParam(required = false) Integer status,
                                                    @RequestParam(required = false) Long issueId) {
        return R.ok(settlementService.list(pageNum, pageSize, status, issueId));
    }

    @PostMapping("/settlements/{id}/cancel")
    @RequirePerm(Permissions.SETTLEMENT_CONFIRM)
    public R<Void> cancel(@PathVariable Long id, @RequestBody Map<String, String> body) {
        settlementService.cancel(id, body.get("reason"));
        return R.ok();
    }

    @GetMapping("/points/transactions")
    @RequirePerm(Permissions.POINT_VIEW)
    public R<PageResult<AccPointTransaction>> transactions(@RequestParam Long userId,
                                                           @RequestParam(defaultValue = "1") long pageNum,
                                                           @RequestParam(defaultValue = "10") long pageSize,
                                                           @RequestParam(required = false) String bizType) {
        return R.ok(pointService.transactions(userId, pageNum, pageSize, bizType));
    }

    public record AdjustRequest(
            @NotNull(message = "调整积分不能为空") Long points,
            @NotNull(message = "方向不能为空") Boolean increase,
            @NotBlank(message = "调整理由不能为空")
            @Size(min = 10, max = 200, message = "调整理由至少 10 个字") String reason
    ) {
    }

    @PostMapping("/users/{id}/points/adjust")
    @RequirePerm(Permissions.POINT_ADJUST)
    public R<Map<String, Object>> adjust(@PathVariable Long id, @RequestBody AdjustRequest request) {
        long balance = pointService.adjust(id, request.points(), request.increase(), request.reason());
        return R.ok(Map.of("userId", id, "balance", balance));
    }

    @PostMapping("/issues/{id}/settle")
    @RequirePerm(Permissions.ISSUE_SETTLE)
    public R<Map<String, Object>> settle(@PathVariable Long id) {
        return R.ok(settlementService.settleIssue(id));
    }

    @GetMapping("/configs")
    @RequirePerm(Permissions.CONFIG_MANAGE)
    public R<List<SysConfig>> configs() {
        return R.ok(configService.listAll());
    }

    @PutMapping("/configs")
    @RequirePerm(Permissions.CONFIG_MANAGE)
    public R<Void> updateConfig(@RequestBody Map<String, String> body) {
        String key = body.get("key");
        String value = body.get("value");
        if (key == null || key.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "配置键不能为空");
        }
        configService.update(key, value, SecurityUtils.currentUserId());
        return R.ok();
    }

    @GetMapping("/audit-logs")
    @RequirePerm(Permissions.ROLE_GRANT)
    public R<PageResult<SysAuditLog>> auditLogs(@RequestParam(defaultValue = "1") long pageNum,
                                                @RequestParam(defaultValue = "10") long pageSize,
                                                @RequestParam(required = false) String module) {
        Page<SysAuditLog> page = auditLogMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<SysAuditLog>()
                        .eq(module != null && !module.isBlank(), SysAuditLog::getModule, module)
                        .orderByDesc(SysAuditLog::getId));
        return R.ok(PageResult.of(page.getTotal(), pageNum, pageSize, page.getRecords()));
    }
}
