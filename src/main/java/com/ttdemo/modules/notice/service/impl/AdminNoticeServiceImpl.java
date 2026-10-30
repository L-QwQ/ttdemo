package com.ttdemo.modules.notice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ttdemo.common.api.PageResult;
import com.ttdemo.common.api.ResultCode;
import com.ttdemo.common.exception.BizException;
import com.ttdemo.modules.notice.dto.AdminNoticeDetailVO;
import com.ttdemo.modules.notice.dto.AdminNoticeQuery;
import com.ttdemo.modules.notice.dto.AdminNoticeVO;
import com.ttdemo.modules.notice.dto.NoticeSaveRequest;
import com.ttdemo.modules.notice.dto.ReceiverStat;
import com.ttdemo.modules.notice.entity.SysNotice;
import com.ttdemo.modules.notice.entity.SysNoticeReceiver;
import com.ttdemo.modules.notice.mapper.SysNoticeMapper;
import com.ttdemo.modules.notice.mapper.SysNoticeReceiverMapper;
import com.ttdemo.modules.notice.service.AdminNoticeService;
import com.ttdemo.modules.user.dto.UserVO;
import com.ttdemo.modules.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AdminNoticeServiceImpl implements AdminNoticeService {

    private static final Set<String> TYPES = Set.of(SysNotice.TYPE_ANNOUNCEMENT, SysNotice.TYPE_NOTICE);
    private static final Set<String> LEVELS = Set.of(SysNotice.LEVEL_NORMAL, SysNotice.LEVEL_IMPORTANT,
            SysNotice.LEVEL_URGENT);
    private static final Set<String> TARGET_TYPES = Set.of(SysNotice.TARGET_ALL, SysNotice.TARGET_SPECIFIC);
    private static final int SUMMARY_LENGTH = 80;
    /** 单批插入接收人数量，避免超大 SQL */
    private static final int BATCH_SIZE = 500;

    private final SysNoticeMapper noticeMapper;
    private final SysNoticeReceiverMapper receiverMapper;
    private final UserService userService;

    public AdminNoticeServiceImpl(SysNoticeMapper noticeMapper,
                                  SysNoticeReceiverMapper receiverMapper,
                                  UserService userService) {
        this.noticeMapper = noticeMapper;
        this.receiverMapper = receiverMapper;
        this.userService = userService;
    }

    @Override
    public PageResult<AdminNoticeVO> page(AdminNoticeQuery query) {
        LambdaQueryWrapper<SysNotice> wrapper = new LambdaQueryWrapper<SysNotice>()
                .like(StringUtils.hasText(query.getKeyword()), SysNotice::getTitle, query.getKeyword())
                .eq(StringUtils.hasText(query.getType()), SysNotice::getType, query.getType())
                .eq(query.getStatus() != null, SysNotice::getStatus, query.getStatus())
                .orderByDesc(SysNotice::getId);
        Page<SysNotice> result = noticeMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()), wrapper);

        List<Long> ids = result.getRecords().stream().map(SysNotice::getId).toList();
        Map<Long, ReceiverStat> statMap = ids.isEmpty()
                ? Map.of()
                : receiverMapper.statByNoticeIds(ids).stream()
                .collect(Collectors.toMap(ReceiverStat::getNoticeId, Function.identity(), (a, b) -> a));

        List<AdminNoticeVO> records = result.getRecords().stream()
                .map(notice -> {
                    ReceiverStat stat = statMap.get(notice.getId());
                    return new AdminNoticeVO(
                            notice.getId(),
                            notice.getTitle(),
                            summarize(notice.getContent()),
                            notice.getType(),
                            notice.getLevel(),
                            notice.getTargetType(),
                            notice.getStatus(),
                            notice.getPublisherName(),
                            notice.getPublishedAt(),
                            notice.getCreatedAt(),
                            stat == null ? 0L : toLong(stat.getTotal()),
                            stat == null ? 0L : toLong(stat.getReadCount()));
                })
                .toList();
        return PageResult.of(result.getTotal(), query.getPage(), query.getSize(), records);
    }

    @Override
    public AdminNoticeDetailVO detail(Long noticeId) {
        SysNotice notice = requireNotice(noticeId);
        List<ReceiverStat> stats = receiverMapper.statByNoticeIds(List.of(noticeId));
        ReceiverStat stat = stats.isEmpty() ? null : stats.get(0);
        List<Long> receiverIds = notice.isTargetAll()
                ? List.of()
                : receiverMapper.selectReceiverIds(noticeId);
        List<UserVO> receivers = receiverIds.isEmpty()
                ? List.of()
                : userService.listByIds(receiverIds);
        return new AdminNoticeDetailVO(
                notice.getId(),
                notice.getTitle(),
                notice.getContent(),
                notice.getType(),
                notice.getLevel(),
                notice.getTargetType(),
                notice.getStatus(),
                notice.getPublisherName(),
                notice.getPublishedAt(),
                notice.getCreatedAt(),
                stat == null ? 0L : toLong(stat.getTotal()),
                stat == null ? 0L : toLong(stat.getReadCount()),
                receivers);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(Long operatorId, String operatorName, NoticeSaveRequest request) {
        String type = requireEnum(request.type(), TYPES, "类型");
        String level = requireEnum(defaultIfBlank(request.level(), SysNotice.LEVEL_NORMAL), LEVELS, "级别");
        String targetType = requireEnum(request.targetType(), TARGET_TYPES, "接收范围");

        List<Long> receivers = resolveReceivers(targetType, request.targetUserIds());
        boolean publishNow = Boolean.TRUE.equals(request.publishNow());

        SysNotice notice = new SysNotice();
        notice.setTitle(request.title().trim());
        notice.setContent(request.content().trim());
        notice.setType(type);
        notice.setLevel(level);
        notice.setTargetType(targetType);
        notice.setStatus(SysNotice.STATUS_DRAFT);
        notice.setPublisherId(operatorId);
        notice.setPublisherName(operatorName);
        noticeMapper.insert(notice);

        // 指定人群：草稿阶段即落库，便于再次编辑回显
        saveReceivers(notice.getId(), receivers);
        if (publishNow) {
            doPublish(notice);
        }
        return notice.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long noticeId, NoticeSaveRequest request) {
        SysNotice notice = requireNotice(noticeId);
        if (notice.isPublished()) {
            throw new BizException(ResultCode.NOTICE_ALREADY_PUBLISHED);
        }
        String type = requireEnum(request.type(), TYPES, "类型");
        String level = requireEnum(defaultIfBlank(request.level(), SysNotice.LEVEL_NORMAL), LEVELS, "级别");
        String targetType = requireEnum(request.targetType(), TARGET_TYPES, "接收范围");
        List<Long> receivers = resolveReceivers(targetType, request.targetUserIds());
        if (SysNotice.TARGET_SPECIFIC.equals(targetType) && receivers.isEmpty()) {
            throw new BizException(ResultCode.NOTICE_TARGET_EMPTY);
        }

        noticeMapper.update(null, new LambdaUpdateWrapper<SysNotice>()
                .eq(SysNotice::getId, noticeId)
                .set(SysNotice::getTitle, request.title().trim())
                .set(SysNotice::getContent, request.content().trim())
                .set(SysNotice::getType, type)
                .set(SysNotice::getLevel, level)
                .set(SysNotice::getTargetType, targetType));

        // 草稿接收人整体重建
        receiverMapper.delete(new LambdaQueryWrapper<SysNoticeReceiver>()
                .eq(SysNoticeReceiver::getNoticeId, noticeId));
        saveReceivers(noticeId, receivers);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long noticeId) {
        SysNotice notice = requireNotice(noticeId);
        if (notice.isPublished()) {
            throw new BizException(ResultCode.NOTICE_ALREADY_PUBLISHED);
        }
        doPublish(notice);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long noticeId) {
        requireNotice(noticeId);
        noticeMapper.deleteById(noticeId);
        // 接收人与通知同生命周期，物理删除避免残留脏数据
        receiverMapper.delete(new LambdaQueryWrapper<SysNoticeReceiver>()
                .eq(SysNoticeReceiver::getNoticeId, noticeId));
    }

    /**
     * 发布：全员通知以「发布时刻」的用户集合为准重新展开；指定人群使用草稿已选的接收人
     */
    private void doPublish(SysNotice notice) {
        Long noticeId = notice.getId();
        if (notice.isTargetAll()) {
            receiverMapper.delete(new LambdaQueryWrapper<SysNoticeReceiver>()
                    .eq(SysNoticeReceiver::getNoticeId, noticeId));
            List<Long> allUserIds = userService.listEnabledUserIds();
            if (allUserIds.isEmpty()) {
                throw new BizException(ResultCode.NOTICE_TARGET_EMPTY, "当前没有可接收通知的启用用户");
            }
            saveReceivers(noticeId, allUserIds);
        } else {
            Long count = receiverMapper.selectCount(new LambdaQueryWrapper<SysNoticeReceiver>()
                    .eq(SysNoticeReceiver::getNoticeId, noticeId));
            if (count == null || count == 0) {
                throw new BizException(ResultCode.NOTICE_TARGET_EMPTY, "请先选择接收人再发布");
            }
        }
        LocalDateTime now = LocalDateTime.now();
        noticeMapper.update(null, new LambdaUpdateWrapper<SysNotice>()
                .eq(SysNotice::getId, noticeId)
                .set(SysNotice::getStatus, SysNotice.STATUS_PUBLISHED)
                .set(SysNotice::getPublishedAt, now));
    }

    /** 指定人群时校验用户存在且启用；全员时返回空（发布时才展开） */
    private List<Long> resolveReceivers(String targetType, List<Long> targetUserIds) {
        if (!SysNotice.TARGET_SPECIFIC.equals(targetType)) {
            return List.of();
        }
        List<Long> valid = userService.filterEnabledUserIds(targetUserIds);
        if (valid.isEmpty()) {
            throw new BizException(ResultCode.NOTICE_TARGET_EMPTY);
        }
        return valid;
    }

    private void saveReceivers(Long noticeId, Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        List<Long> list = List.copyOf(userIds);
        for (int i = 0; i < list.size(); i += BATCH_SIZE) {
            receiverMapper.insertBatch(noticeId, list.subList(i, Math.min(i + BATCH_SIZE, list.size())));
        }
    }

    private SysNotice requireNotice(Long noticeId) {
        SysNotice notice = noticeId == null ? null : noticeMapper.selectById(noticeId);
        if (notice == null) {
            throw new BizException(ResultCode.NOTICE_NOT_FOUND);
        }
        return notice;
    }

    private String requireEnum(String value, Set<String> allowed, String field) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        if (!allowed.contains(normalized)) {
            throw new BizException(ResultCode.BAD_REQUEST, field + "取值不合法");
        }
        return normalized;
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String summarize(String content) {
        if (content == null) {
            return "";
        }
        String flat = content.replaceAll("\\s+", " ").trim();
        return flat.length() <= SUMMARY_LENGTH ? flat : flat.substring(0, SUMMARY_LENGTH) + "…";
    }

    private long toLong(Long value) {
        return value == null ? 0L : value;
    }
}
