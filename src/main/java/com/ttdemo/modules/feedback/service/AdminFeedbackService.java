package com.ttdemo.modules.feedback.service;

import com.ttdemo.common.api.PageResult;
import com.ttdemo.modules.feedback.dto.FeedbackQuery;
import com.ttdemo.modules.feedback.dto.FeedbackReplyRequest;
import com.ttdemo.modules.feedback.dto.FeedbackVO;

/**
 * 管理端反馈处理服务
 */
public interface AdminFeedbackService {

    PageResult<FeedbackVO> page(FeedbackQuery query);

    FeedbackVO detail(Long id);

    /**
     * 回复并流转状态
     */
    void reply(Long operatorId, Long id, FeedbackReplyRequest request);

    void delete(Long id);
}
