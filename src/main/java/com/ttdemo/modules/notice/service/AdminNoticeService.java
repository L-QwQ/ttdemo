package com.ttdemo.modules.notice.service;

import com.ttdemo.common.api.PageResult;
import com.ttdemo.modules.notice.dto.AdminNoticeDetailVO;
import com.ttdemo.modules.notice.dto.AdminNoticeQuery;
import com.ttdemo.modules.notice.dto.AdminNoticeVO;
import com.ttdemo.modules.notice.dto.NoticeSaveRequest;

/**
 * 管理端通知公告服务：草稿 → 发布（按目标范围展开接收人）→ 下架删除
 */
public interface AdminNoticeService {

    PageResult<AdminNoticeVO> page(AdminNoticeQuery query);

    AdminNoticeDetailVO detail(Long noticeId);

    /**
     * 新建，publishNow=true 时立即发布
     *
     * @return 新建的通知 id
     */
    Long create(Long operatorId, String operatorName, NoticeSaveRequest request);

    /**
     * 编辑草稿（已发布不可编辑，只能下架）
     */
    void update(Long noticeId, NoticeSaveRequest request);

    /**
     * 发布草稿：按 ALL / SPECIFIC 展开接收人
     */
    void publish(Long noticeId);

    void delete(Long noticeId);
}
