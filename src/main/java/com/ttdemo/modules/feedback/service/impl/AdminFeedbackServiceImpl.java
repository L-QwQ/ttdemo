package com.ttdemo.modules.feedback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ttdemo.common.api.PageResult;
import com.ttdemo.common.api.ResultCode;
import com.ttdemo.common.exception.BizException;
import com.ttdemo.modules.feedback.dto.FeedbackQuery;
import com.ttdemo.modules.feedback.dto.FeedbackReplyRequest;
import com.ttdemo.modules.feedback.dto.FeedbackVO;
import com.ttdemo.modules.feedback.entity.SysFeedback;
import com.ttdemo.modules.feedback.mapper.SysFeedbackMapper;
import com.ttdemo.modules.feedback.service.AdminFeedbackService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AdminFeedbackServiceImpl implements AdminFeedbackService {

    private final SysFeedbackMapper feedbackMapper;

    public AdminFeedbackServiceImpl(SysFeedbackMapper feedbackMapper) {
        this.feedbackMapper = feedbackMapper;
    }

    @Override
    public PageResult<FeedbackVO> page(FeedbackQuery query) {
        String keyword = query.getKeyword();
        LambdaQueryWrapper<SysFeedback> wrapper = new LambdaQueryWrapper<SysFeedback>()
                .and(StringUtils.hasText(keyword), w -> w
                        .like(SysFeedback::getTitle, keyword)
                        .or()
                        .like(SysFeedback::getUserPhone, keyword))
                .eq(StringUtils.hasText(query.getType()), SysFeedback::getType, query.getType())
                .eq(query.getStatus() != null, SysFeedback::getStatus, query.getStatus())
                .orderByDesc(SysFeedback::getId);
        Page<SysFeedback> result = feedbackMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()), wrapper);
        List<FeedbackVO> records = result.getRecords().stream().map(FeedbackVO::from).toList();
        return PageResult.of(result.getTotal(), query.getPage(), query.getSize(), records);
    }

    @Override
    public FeedbackVO detail(Long id) {
        return FeedbackVO.from(requireFeedback(id));
    }

    @Override
    public void reply(Long operatorId, Long id, FeedbackReplyRequest request) {
        requireFeedback(id);
        Integer status = request.status();
        if (status == null || status < SysFeedback.STATUS_PENDING || status > SysFeedback.STATUS_CLOSED) {
            throw new BizException(ResultCode.FEEDBACK_STATUS_INVALID);
        }
        feedbackMapper.update(null, new LambdaUpdateWrapper<SysFeedback>()
                .eq(SysFeedback::getId, id)
                .set(SysFeedback::getReply, request.reply().trim())
                .set(SysFeedback::getStatus, status)
                .set(SysFeedback::getRepliedBy, operatorId)
                .set(SysFeedback::getRepliedAt, LocalDateTime.now()));
    }

    @Override
    public void delete(Long id) {
        requireFeedback(id);
        feedbackMapper.deleteById(id);
    }

    private SysFeedback requireFeedback(Long id) {
        SysFeedback entity = id == null ? null : feedbackMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ResultCode.FEEDBACK_NOT_FOUND);
        }
        return entity;
    }
}
