package com.ttdemo.modules.notice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ttdemo.common.api.PageQuery;
import com.ttdemo.common.api.PageResult;
import com.ttdemo.common.api.ResultCode;
import com.ttdemo.common.exception.BizException;
import com.ttdemo.modules.notice.dto.MyNoticeVO;
import com.ttdemo.modules.notice.dto.UserNoticeDetailVO;
import com.ttdemo.modules.notice.entity.SysNotice;
import com.ttdemo.modules.notice.entity.SysNoticeReceiver;
import com.ttdemo.modules.notice.mapper.SysNoticeMapper;
import com.ttdemo.modules.notice.mapper.SysNoticeReceiverMapper;
import com.ttdemo.modules.notice.service.UserNoticeService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class UserNoticeServiceImpl implements UserNoticeService {

    private final SysNoticeMapper noticeMapper;
    private final SysNoticeReceiverMapper receiverMapper;

    public UserNoticeServiceImpl(SysNoticeMapper noticeMapper, SysNoticeReceiverMapper receiverMapper) {
        this.noticeMapper = noticeMapper;
        this.receiverMapper = receiverMapper;
    }

    @Override
    public PageResult<MyNoticeVO> page(Long userId, PageQuery pageQuery, Boolean unreadOnly, String type) {
        IPage<MyNoticeVO> result = receiverMapper.selectMyNoticePage(
                new Page<>(pageQuery.getPage(), pageQuery.getSize()),
                userId,
                Boolean.TRUE.equals(unreadOnly),
                type);
        return PageResult.of(result.getTotal(), pageQuery.getPage(), pageQuery.getSize(), result.getRecords());
    }

    @Override
    public long unreadCount(Long userId) {
        return receiverMapper.countUnread(userId);
    }

    @Override
    public UserNoticeDetailVO detail(Long userId, Long noticeId) {
        SysNotice notice = requirePublishedNotice(noticeId);
        SysNoticeReceiver receiver = receiverMapper.selectOne(new LambdaQueryWrapper<SysNoticeReceiver>()
                .eq(SysNoticeReceiver::getNoticeId, noticeId)
                .eq(SysNoticeReceiver::getUserId, userId)
                .last("limit 1"));
        if (receiver == null) {
            throw new BizException(ResultCode.NOTICE_NOT_FOUND);
        }
        LocalDateTime readAt = receiver.getReadAt();
        if (readAt == null) {
            readAt = LocalDateTime.now();
            receiverMapper.markRead(noticeId, userId, readAt);
        }
        return UserNoticeDetailVO.from(notice, readAt);
    }

    @Override
    public int markAllRead(Long userId) {
        return receiverMapper.markAllRead(userId, LocalDateTime.now());
    }

    private SysNotice requirePublishedNotice(Long noticeId) {
        SysNotice notice = noticeId == null ? null : noticeMapper.selectById(noticeId);
        if (notice == null || !notice.isPublished()) {
            throw new BizException(ResultCode.NOTICE_NOT_FOUND);
        }
        return notice;
    }
}
