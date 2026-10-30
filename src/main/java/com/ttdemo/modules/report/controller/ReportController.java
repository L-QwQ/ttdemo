package com.ttdemo.modules.report.controller;

import com.ttdemo.common.api.PageResult;
import com.ttdemo.common.api.R;
import com.ttdemo.modules.report.dto.ReportDtos;
import com.ttdemo.modules.report.service.ReportService;
import com.ttdemo.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内容举报（C 端）
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping
    public R<Long> submit(@Valid @RequestBody ReportDtos.SubmitRequest request) {
        return R.ok(reportService.submit(SecurityUtils.currentUserId(), request));
    }

    @GetMapping("/mine")
    public R<PageResult<ReportDtos.ReportVO>> mine(@RequestParam(defaultValue = "1") long pageNum,
                                                   @RequestParam(defaultValue = "10") long pageSize) {
        return R.ok(reportService.myReports(SecurityUtils.currentUserId(), pageNum, pageSize));
    }
}
