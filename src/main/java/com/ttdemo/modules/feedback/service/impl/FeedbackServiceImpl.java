package com.ttdemo.modules.feedback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ttdemo.common.api.PageQuery;
import com.ttdemo.common.api.PageResult;
import com.ttdemo.common.api.ResultCode;
import com.ttdemo.common.exception.BizException;
import com.ttdemo.modules.feedback.dto.FeedbackCreateRequest;
import com.ttdemo.modules.feedback.dto.FeedbackVO;
import com.ttdemo.modules.feedback.entity.SysFeedback;
import com.ttdemo.modules.feedback.mapper.SysFeedbackMapper;
import com.ttdemo.modules.feedback.service.FeedbackService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
public class FeedbackServiceImpl implements FeedbackService {

    private static final Set<String> TYPES = Set.of(SysFeedback.TYPE_BUG, SysFeedback.TYPE_FEATURE,
            SysFeedback.TYPE_EXPERIENCE, SysFeedback.TYPE_OTHER);
    /** 单用户每日提交上限，防刷屏 */
    private static final int DAILY_LIMIT = 20;

    private final SysFeedbackMapper feedbackMapper;

    public FeedbackServiceImpl(SysFeedbackMapper feedbackMapper) {
        this.feedbackMapper = feedbackMapper;
    }

    @Override
    public Long create(Long userId, String phone, FeedbackCreateRequest request) {
        String type = request.type().trim().toUpperCase();
        if (!TYPES.contains(type)) {
            throw new BizException(ResultCode.BAD_REQUEST, "反馈类型取值不合法");
        }
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long todayCount = feedbackMapper.selectCount(new LambdaQueryWrapper<SysFeedback>()
                .eq(SysFeedback::getUserId, userId)
                .ge(SysFeedback::getCreatedAt, todayStart));
        if (todayCount >= DAILY_LIMIT) {
            throw new BizException(ResultCode.TOO_MANY_REQUESTS, "今日反馈次数已达上限，请明天再试");
        }

        SysFeedback entity = new SysFeedback();
        entity.setUserId(userId);
        entity.setUserPhone(phone == null ? "" : phone);
        entity.setType(type);
        entity.setTitle(request.title().trim());
        entity.setContent(request.content().trim());
        entity.setContact(StringUtils.hasText(request.contact()) ? request.contact().trim() : null);
        entity.setStatus(SysFeedback.STATUS_PENDING);
        feedbackMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public PageResult<FeedbackVO> myPage(Long userId, PageQuery pageQuery) {
        Page<SysFeedback> result = feedbackMapper.selectPage(
                new Page<>(pageQuery.getPage(), pageQuery.getSize()),
                new LambdaQueryWrapper<SysFeedback>()
                        .eq(SysFeedback::getUserId, userId)
                        .orderByDesc(SysFeedback::getId));
        List<FeedbackVO> records = result.getRecords().stream().map(FeedbackVO::from).toList();
        return PageResult.of(result.getTotal(), pageQuery.getPage(), pageQuery.getSize(), records);
    }
}
