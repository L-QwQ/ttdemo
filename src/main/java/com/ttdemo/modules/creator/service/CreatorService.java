package com.ttdemo.modules.creator.service;

import com.ttdemo.common.api.PageResult;
import com.ttdemo.modules.creator.dto.CreatorDtos;
import com.ttdemo.modules.creator.entity.CreCreator;

import java.util.List;
import java.util.Map;

public interface CreatorService {

    // ---------- 实名 ----------
    void submitRealname(CreatorDtos.RealnameRequest request);

    Map<String, Object> myRealname();

    PageResult<Map<String, Object>> listRealnameForAudit(long page, long size, Integer auditStatus, String keyword);

    /** 撤销已通过的实名（审核纠错），用户会回到「已驳回」并可重新提交 */
    void resetRealname(Long id, String remark);

    /** 实名审核数量统计：pending / passed / rejected */
    Map<String, Long> realnameAuditStats();

    void auditRealname(Long id, CreatorDtos.AuditRequest request);

    // ---------- 创作者 ----------
    void apply(CreatorDtos.ApplyRequest request);

    PageResult<CreatorDtos.ProfileVO> listForAudit(long page, long size, Integer status, String keyword);

    void auditCreator(Long creatorId, CreatorDtos.AuditRequest request);

    CreatorDtos.ProfileVO myProfile();

    void updateProfile(CreatorDtos.ProfileUpdateRequest request);

    CreatorDtos.DashboardVO dashboard();

    CreatorDtos.AccuracyVO accuracy();

    CreatorDtos.CreatorHomeVO home(Long creatorId);

    void follow(Long creatorId);

    void unfollow(Long creatorId);

    // ---------- 内部 ----------
    CreCreator requireByUserId(Long userId);

    CreCreator requireById(Long creatorId);

    /** 按审核状态统计实名申请数量（后台仪表盘用） */
    long countRealnameByStatus(int auditStatus);

    /** 创作者总数（后台仪表盘用） */
    long countCreators();
}
