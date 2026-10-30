package com.ttdemo.modules.feedback.service;

import com.ttdemo.common.api.PageQuery;
import com.ttdemo.common.api.PageResult;
import com.ttdemo.modules.feedback.dto.FeedbackCreateRequest;
import com.ttdemo.modules.feedback.dto.FeedbackVO;

/**
 * 用户端反馈服务
 */
public interface FeedbackService {

    Long create(Long userId, String phone, FeedbackCreateRequest request);

    PageResult<FeedbackVO> myPage(Long userId, PageQuery pageQuery);
}
