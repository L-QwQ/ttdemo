package com.ttdemo.modules.creator.controller;

import com.ttdemo.common.api.PageResult;
import com.ttdemo.common.api.R;
import com.ttdemo.modules.creator.dto.CreatorDtos;
import com.ttdemo.modules.creator.service.CreatorService;
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
 * 后台创作者与实名审核
 */
@RestController
@RequestMapping("/api/admin/v1")
public class AdminCreatorController {

    private final CreatorService creatorService;

    public AdminCreatorController(CreatorService creatorService) {
        this.creatorService = creatorService;
    }

    @GetMapping("/realnames")
    @RequirePerm(Permissions.CREATOR_AUDIT)
    public R<PageResult<Map<String, Object>>> realnames(@RequestParam(defaultValue = "1") long pageNum,
                                                        @RequestParam(defaultValue = "10") long pageSize,
                                                        @RequestParam(required = false) Integer auditStatus,
                                                        @RequestParam(required = false) String keyword) {
        return R.ok(creatorService.listRealnameForAudit(pageNum, pageSize, auditStatus, keyword));
    }

    @GetMapping("/realnames/stats")
    @RequirePerm(Permissions.CREATOR_AUDIT)
    public R<Map<String, Long>> realnameStats() {
        return R.ok(creatorService.realnameAuditStats());
    }

    @PostMapping("/realnames/{id}/audit")
    @RequirePerm(Permissions.CREATOR_AUDIT)
    public R<Void> auditRealname(@PathVariable Long id, @Valid @RequestBody CreatorDtos.AuditRequest request) {
        creatorService.auditRealname(id, request);
        return R.ok();
    }

    @PostMapping("/realnames/{id}/reset")
    @RequirePerm(Permissions.CREATOR_AUDIT)
    public R<Void> resetRealname(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        creatorService.resetRealname(id, body == null ? null : body.get("remark"));
        return R.ok();
    }

    @GetMapping("/creators")
    @RequirePerm(Permissions.CREATOR_AUDIT)
    public R<PageResult<CreatorDtos.ProfileVO>> creators(@RequestParam(defaultValue = "1") long pageNum,
                                                         @RequestParam(defaultValue = "10") long pageSize,
                                                         @RequestParam(required = false) Integer status,
                                                         @RequestParam(required = false) String keyword) {
        return R.ok(creatorService.listForAudit(pageNum, pageSize, status, keyword));
    }

    @PostMapping("/creators/{id}/audit")
    @RequirePerm(Permissions.CREATOR_AUDIT)
    public R<Void> auditCreator(@PathVariable Long id, @Valid @RequestBody CreatorDtos.AuditRequest request) {
        creatorService.auditCreator(id, request);
        return R.ok();
    }
}
