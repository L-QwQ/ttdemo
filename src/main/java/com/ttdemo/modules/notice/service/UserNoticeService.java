package com.ttdemo.modules.notice.service;

import com.ttdemo.common.api.PageQuery;
import com.ttdemo.common.api.PageResult;
import com.ttdemo.modules.notice.dto.MyNoticeVO;
import com.ttdemo.modules.notice.dto.UserNoticeDetailVO;

/**
 * 用户端通知服务：只读取「发布给我」的通知
 */
public interface UserNoticeService {

    PageResult<MyNoticeVO> page(Long userId, PageQuery pageQuery, Boolean unreadOnly, String type);

    long unreadCount(Long userId);

    /**
     * 查看详情并自动标记已读；无权查看时抛 BizException
     */
    UserNoticeDetailVO detail(Long userId, Long noticeId);

    int markAllRead(Long userId);
}
