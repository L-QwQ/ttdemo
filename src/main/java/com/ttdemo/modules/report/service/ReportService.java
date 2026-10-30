package com.ttdemo.modules.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ttdemo.common.api.PageResult;
import com.ttdemo.common.api.ResultCode;
import com.ttdemo.common.exception.BizException;
import com.ttdemo.modules.prediction.entity.PrdPrediction;
import com.ttdemo.modules.prediction.service.PredictionService;
import com.ttdemo.modules.report.dto.ReportDtos;
import com.ttdemo.modules.report.entity.SysReport;
import com.ttdemo.modules.report.mapper.SysReportMapper;
import com.ttdemo.modules.system.service.SysConfigService;
import com.ttdemo.modules.user.dto.UserVO;
import com.ttdemo.modules.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.ttdemo.modules.message.service.MessageService;

/**
 * 内容举报：用户提交 → 后台处置 →（可选）自动下架。
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final SysReportMapper reportMapper;
    private final PredictionService predictionService;
    private final UserService userService;
    private final SysConfigService configService;
    private final MessageService messageService;

    public ReportService(SysReportMapper reportMapper, PredictionService predictionService,
                         UserService userService, SysConfigService configService,
                         MessageService messageService) {
        this.messageService = messageService;
        this.reportMapper = reportMapper;
        this.predictionService = predictionService;
        this.userService = userService;
        this.configService = configService;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long submit(Long userId, ReportDtos.SubmitRequest request) {
        // 只支持预测举报落库校验，其余类型仅记录，避免外部 id 乱传
        if (request.targetType() == SysReport.TARGET_PREDICTION) {
            PrdPrediction prediction = predictionService.requireById(request.targetId());
            if (prediction == null) {
                throw new BizException(ResultCode.NOT_FOUND, "被举报的预测不存在");
            }
            if (prediction.getUserId() != null && prediction.getUserId().equals(userId)) {
                throw new BizException(ResultCode.BAD_REQUEST, "不能举报自己发布的内容");
            }
        }
        Long dup = reportMapper.selectCount(new LambdaQueryWrapper<SysReport>()
                .eq(SysReport::getReporterId, userId)
                .eq(SysReport::getTargetType, request.targetType())
                .eq(SysReport::getTargetId, request.targetId())
                .eq(SysReport::getStatus, SysReport.STATUS_PENDING));
        if (dup != null && dup > 0) {
            throw new BizException(ResultCode.STATE_CONFLICT, "你已举报过该内容，正在处理中");
        }

        SysReport report = new SysReport();
        report.setReporterId(userId);
        report.setTargetType(request.targetType());
        report.setTargetId(request.targetId());
        report.setReasonType(request.reasonType());
        report.setContent(trim(request.content()));
        report.setEvidenceUrl(trim(request.evidenceUrl()));
        report.setStatus(SysReport.STATUS_PENDING);
        reportMapper.insert(report);

        if (request.targetType() == SysReport.TARGET_PREDICTION) {
            autoOfflineIfNeeded(request.targetId());
        }
        return report.getId();
    }

    /** 同一预测累计有效举报达到阈值时自动下架，避免违规内容靠人工响应速度 */
    private void autoOfflineIfNeeded(Long predictionId) {
        int threshold = configService.getInt("report.auto.offline.threshold", 3);
        if (threshold <= 0) {
            return;
        }
        Long count = reportMapper.selectCount(new LambdaQueryWrapper<SysReport>()
                .eq(SysReport::getTargetType, SysReport.TARGET_PREDICTION)
                .eq(SysReport::getTargetId, predictionId)
                .eq(SysReport::getStatus, SysReport.STATUS_PENDING));
        if (count != null && count >= threshold) {
            try {
                predictionService.offline(predictionId, "累计举报达 " + threshold + " 次，系统自动下架待复核");
                log.warn("预测因举报自动下架 predictionId={} count={}", predictionId, count);
            } catch (Exception e) {
                // 已下架/已结算等状态冲突不影响举报本身
                log.info("自动下架跳过 predictionId={} reason={}", predictionId, e.getMessage());
            }
        }
    }

    public PageResult<ReportDtos.ReportVO> myReports(Long userId, long pageNum, long pageSize) {
        Page<SysReport> page = reportMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<SysReport>()
                        .eq(SysReport::getReporterId, userId)
                        .orderByDesc(SysReport::getId));
        List<ReportDtos.ReportVO> list = page.getRecords().stream().map(ReportDtos.ReportVO::from).toList();
        return PageResult.of(page.getTotal(), pageNum, pageSize, list);
    }

    public PageResult<ReportDtos.ReportVO> page(long pageNum, long pageSize, Integer status) {
        Page<SysReport> page = reportMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<SysReport>()
                        .eq(status != null, SysReport::getStatus, status)
                        .orderByAsc(SysReport::getStatus)
                        .orderByDesc(SysReport::getId));
        List<SysReport> records = page.getRecords();

        Set<Long> userIds = new HashSet<>();
        Set<Long> predictionIds = new HashSet<>();
        for (SysReport r : records) {
            userIds.add(r.getReporterId());
            if (r.getTargetType() != null && r.getTargetType() == SysReport.TARGET_PREDICTION
                    && r.getTargetId() != null) {
                predictionIds.add(r.getTargetId());
            }
        }
        Map<Long, String> phones = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (UserVO u : userService.listByIds(new ArrayList<>(userIds))) {
                phones.put(u.id(), u.phone());
            }
        }

        List<ReportDtos.ReportVO> list = new ArrayList<>(records.size());
        for (SysReport r : records) {
            String title = null;
            if (r.getTargetType() != null && r.getTargetType() == SysReport.TARGET_PREDICTION) {
                try {
                    PrdPrediction p = predictionService.requireById(r.getTargetId());
                    title = p == null ? null : p.getTitle();
                } catch (Exception ignored) {
                    title = "（内容已删除）";
                }
            }
            list.add(ReportDtos.ReportVO.from(r).withDetail(phones.get(r.getReporterId()), title));
        }
        return PageResult.of(page.getTotal(), pageNum, pageSize, list);
    }

    @Transactional(rollbackFor = Exception.class)
    public void handle(Long reportId, Long operatorId, ReportDtos.HandleRequest request) {
        SysReport report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BizException(ResultCode.NOT_FOUND, "举报记录不存在");
        }
        if (report.getStatus() != null && report.getStatus() != SysReport.STATUS_PENDING) {
            throw new BizException(ResultCode.STATE_CONFLICT, "该举报已处理");
        }
        int status = request.status() == null ? SysReport.STATUS_HANDLED : request.status();
        if (status != SysReport.STATUS_HANDLED && status != SysReport.STATUS_REJECTED) {
            throw new BizException(ResultCode.BAD_REQUEST, "处理结果不合法");
        }
        if (status == SysReport.STATUS_REJECTED && !StringUtils.hasText(request.handleResult())) {
            throw new BizException(ResultCode.BAD_REQUEST, "驳回举报需填写理由");
        }
        reportMapper.update(null, new LambdaUpdateWrapper<SysReport>()
                .eq(SysReport::getId, reportId)
                .set(SysReport::getStatus, status)
                .set(SysReport::getHandleBy, operatorId)
                .set(SysReport::getHandleTime, LocalDateTime.now())
                .set(SysReport::getHandleResult, trim(request.handleResult())));

        if (Boolean.TRUE.equals(request.offline())
                && report.getTargetType() != null
                && report.getTargetType() == SysReport.TARGET_PREDICTION) {
            predictionService.offline(report.getTargetId(), "举报核实成立：" +
                    (StringUtils.hasText(request.handleResult()) ? request.handleResult() : "内容违规"));
        }

        try {
            messageService.reportHandled(report.getReporterId(), reportId, status == SysReport.STATUS_HANDLED,
                    request.handleResult());
        } catch (Exception e) {
            log.warn("举报处理消息推送失败 reportId={} err={}", reportId, e.getMessage());
        }
    }

    public long pendingCount() {
        Long count = reportMapper.selectCount(new LambdaQueryWrapper<SysReport>()
                .eq(SysReport::getStatus, SysReport.STATUS_PENDING));
        return count == null ? 0 : count;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
