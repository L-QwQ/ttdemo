package com.ttdemo.modules.report.controller;

import com.ttdemo.common.api.PageResult;
import com.ttdemo.common.api.R;
import com.ttdemo.modules.report.dto.ReportDtos;
import com.ttdemo.modules.report.service.ReportService;
import com.ttdemo.security.SecurityUtils;
import com.ttdemo.security.perm.Permissions;
import com.ttdemo.security.perm.RequirePerm;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 后台举报处置
 */
@RestController
@RequestMapping("/api/admin/v1/reports")
public class AdminReportController {

    private final ReportService reportService;

    public AdminReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping
    @RequirePerm(Permissions.PREDICTION_AUDIT)
    public R<PageResult<ReportDtos.ReportVO>> page(@RequestParam(defaultValue = "1") long pageNum,
                                                   @RequestParam(defaultValue = "10") long pageSize,
                                                   @RequestParam(required = false) Integer status) {
        return R.ok(reportService.page(pageNum, pageSize, status));
    }

    @GetMapping("/pending-count")
    @RequirePerm(Permissions.PREDICTION_AUDIT)
    public R<Map<String, Long>> pendingCount() {
        return R.ok(Map.of("count", reportService.pendingCount()));
    }

    @PostMapping("/{id}/handle")
    @RequirePerm(Permissions.PREDICTION_OFFLINE)
    public R<Void> handle(@PathVariable Long id, @Valid @RequestBody ReportDtos.HandleRequest request) {
        reportService.handle(id, SecurityUtils.currentUserId(), request);
        return R.ok();
    }
}
