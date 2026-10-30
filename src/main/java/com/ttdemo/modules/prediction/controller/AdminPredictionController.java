package com.ttdemo.modules.prediction.controller;

import com.ttdemo.common.api.PageResult;
import com.ttdemo.common.api.R;
import com.ttdemo.modules.prediction.dto.PredictionDtos;
import com.ttdemo.modules.prediction.service.PredictionService;
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

@RestController
@RequestMapping("/api/admin/v1/predictions")
public class AdminPredictionController {

    private final PredictionService predictionService;

    public AdminPredictionController(PredictionService predictionService) {
        this.predictionService = predictionService;
    }

    @GetMapping
    @RequirePerm(Permissions.PREDICTION_AUDIT)
    public R<PageResult<PredictionDtos.AuditRow>> queue(@RequestParam(defaultValue = "1") long pageNum,
                                                        @RequestParam(defaultValue = "10") long pageSize,
                                                        @RequestParam(required = false) Integer status) {
        return R.ok(predictionService.auditQueue(pageNum, pageSize, status));
    }

    @PostMapping("/{id}/audit")
    @RequirePerm(Permissions.PREDICTION_AUDIT)
    public R<Void> audit(@PathVariable Long id, @Valid @RequestBody PredictionDtos.AuditRequest request) {
        predictionService.audit(id, request);
        return R.ok();
    }

    @PostMapping("/{id}/offline")
    @RequirePerm(Permissions.PREDICTION_OFFLINE)
    public R<Void> offline(@PathVariable Long id, @Valid @RequestBody PredictionDtos.OfflineRequest request) {
        predictionService.offline(id, request.reason());
        return R.ok();
    }
}
