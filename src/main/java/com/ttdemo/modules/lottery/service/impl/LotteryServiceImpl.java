package com.ttdemo.modules.lottery.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ttdemo.common.api.PageResult;
import com.ttdemo.common.api.ResultCode;
import com.ttdemo.common.exception.BizException;
import com.ttdemo.modules.lottery.dto.DrawResultRequest;
import com.ttdemo.modules.lottery.dto.IssueQuery;
import com.ttdemo.modules.lottery.dto.IssueSaveRequest;
import com.ttdemo.modules.lottery.dto.IssueVO;
import com.ttdemo.modules.lottery.dto.LotteryTypeVO;
import com.ttdemo.modules.lottery.dto.LotteryTypeSaveRequest;
import com.ttdemo.modules.lottery.entity.LotIssue;
import com.ttdemo.modules.lottery.entity.LotLotteryType;
import com.ttdemo.modules.lottery.mapper.LotIssueMapper;
import com.ttdemo.modules.lottery.mapper.LotLotteryTypeMapper;
import com.ttdemo.modules.lottery.service.LotteryService;
import com.ttdemo.modules.system.service.AuditLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class LotteryServiceImpl implements LotteryService {

    private static final Logger log = LoggerFactory.getLogger(LotteryServiceImpl.class);

    private final LotLotteryTypeMapper typeMapper;
    private final LotIssueMapper issueMapper;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public LotteryServiceImpl(LotLotteryTypeMapper typeMapper, LotIssueMapper issueMapper,
                              AuditLogService auditLogService, ObjectMapper objectMapper) {
        this.typeMapper = typeMapper;
        this.issueMapper = issueMapper;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<LotteryTypeVO> listTypes(boolean onlyEnabled) {
        LambdaQueryWrapper<LotLotteryType> query = new LambdaQueryWrapper<LotLotteryType>()
                .orderByAsc(LotLotteryType::getSortNo);
        if (onlyEnabled) {
            query.eq(LotLotteryType::getStatus, 1);
        }
        return typeMapper.selectList(query).stream().map(this::toTypeVO).collect(Collectors.toList());
    }

    @Override
    public LotLotteryType requireType(String code) {
        LotLotteryType type = typeMapper.selectOne(
                new LambdaQueryWrapper<LotLotteryType>().eq(LotLotteryType::getCode, code));
        if (type == null) {
            throw new BizException(ResultCode.LOTTERY_TYPE_NOT_FOUND);
        }
        return type;
    }

    @Override
    public List<IssueVO> listIssues(String typeCode, Integer drawStatus, int limit) {
        LambdaQueryWrapper<LotIssue> query = new LambdaQueryWrapper<LotIssue>()
                .eq(typeCode != null && !typeCode.isBlank(), LotIssue::getTypeCode, typeCode)
                .eq(drawStatus != null, LotIssue::getDrawStatus, drawStatus)
                // C 端只展示官方期号（福彩 7 位 / 体彩 5 位）；8 位为历史测试数据
                .apply("LENGTH(issue_no) IN (5, 7)")
                // 期号是字符串：8 位测试期号 > 5 位体彩 > 7 位福彩，按字符串排序会错序，故按开奖时间排
                .orderByDesc(LotIssue::getDrawTime)
                .orderByDesc(LotIssue::getId)
                .last("limit " + Math.max(1, Math.min(limit, 100)));
        return issueMapper.selectList(query).stream().map(this::toIssueVO).collect(Collectors.toList());
    }

    @Override
    public PageResult<IssueVO> pageIssues(IssueQuery query) {
        String keyword = query.getKeyword() == null ? null : query.getKeyword().trim();
        LambdaQueryWrapper<LotIssue> wrapper = new LambdaQueryWrapper<LotIssue>()
                .eq(query.getTypeCode() != null && !query.getTypeCode().isBlank(),
                        LotIssue::getTypeCode, query.getTypeCode())
                .eq(query.getDrawStatus() != null, LotIssue::getDrawStatus, query.getDrawStatus())
                .eq(query.getDataSource() != null, LotIssue::getDataSource, query.getDataSource())
                .like(keyword != null && !keyword.isBlank(), LotIssue::getIssueNo, keyword)
                // 官方期号：福彩 7 位 / 体彩 5 位；超过 7 位的是历史测试数据
                .apply(Boolean.TRUE.equals(query.getOfficialOnly()), "LENGTH(issue_no) IN (5, 7)")
                // 不能用 issue_no 排序：跨彩种位数不同，字符串比较大期号会压住小期号
                .orderByDesc(LotIssue::getDrawTime)
                .orderByDesc(LotIssue::getId);
        Page<LotIssue> page = issueMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()), wrapper);
        List<IssueVO> list = page.getRecords().stream().map(this::toIssueVO).collect(Collectors.toList());
        return PageResult.of(page.getTotal(), query.getPage(), query.getSize(), list);
    }

    @Override
    public List<IssueVO> currentIssues(String typeCode) {
        List<LotteryTypeVO> types = typeCode == null || typeCode.isBlank()
                ? listTypes(true)
                : List.of(toTypeVO(requireType(typeCode)));
        List<IssueVO> result = new ArrayList<>();
        for (LotteryTypeVO type : types) {
            LotIssue issue = issueMapper.selectOne(new LambdaQueryWrapper<LotIssue>()
                    .eq(LotIssue::getTypeCode, type.code())
                    .gt(LotIssue::getSaleEndTime, LocalDateTime.now())
                    .apply("LENGTH(issue_no) IN (5, 7)")
                    .orderByAsc(LotIssue::getSaleEndTime)
                    .last("limit 1"));
            if (issue != null) {
                result.add(toIssueVO(issue));
            }
        }
        return result;
    }

    @Override
    public IssueVO getIssue(Long id) {
        return toIssueVO(requireIssue(id));
    }

    @Override
    public LotIssue requireIssue(Long id) {
        LotIssue issue = issueMapper.selectById(id);
        if (issue == null) {
            throw new BizException(ResultCode.ISSUE_NOT_FOUND);
        }
        return issue;
    }

    @Override
    public LotIssue requireIssueByNo(String typeCode, String issueNo) {
        LotIssue issue = issueMapper.selectOne(new LambdaQueryWrapper<LotIssue>()
                .eq(LotIssue::getTypeCode, typeCode)
                .eq(LotIssue::getIssueNo, issueNo));
        if (issue == null) {
            throw new BizException(ResultCode.ISSUE_NOT_FOUND);
        }
        return issue;
    }

    @Override
    public List<LotIssue> listDrawnIssues(String typeCode, int limit) {
        return issueMapper.selectList(new LambdaQueryWrapper<LotIssue>()
                .eq(typeCode != null && !typeCode.isBlank(), LotIssue::getTypeCode, typeCode)
                .eq(LotIssue::getDrawStatus, LotIssue.DRAW_DONE)
                // 官方期号：福彩 7 位、体彩 5 位；超长的是历史测试数据，不参与统计
                .apply("LENGTH(issue_no) IN (5, 7)")
                .orderByDesc(LotIssue::getIssueNo)
                .last("limit " + Math.max(1, Math.min(limit, 200))));
    }

    @Override
    public long countDrawnIssues(String typeCode) {
        return countDrawnIssues(typeCode, null);
    }

    @Override
    public long countDrawnIssues(String typeCode, Integer dataSource) {
        Long count = issueMapper.selectCount(new LambdaQueryWrapper<LotIssue>()
                .eq(typeCode != null && !typeCode.isBlank(), LotIssue::getTypeCode, typeCode)
                .eq(LotIssue::getDrawStatus, LotIssue.DRAW_DONE)
                .eq(dataSource != null, LotIssue::getDataSource, dataSource)
                .apply("LENGTH(issue_no) IN (5, 7)"));
        return count == null ? 0 : count;
    }

    @Override
    @Transactional
    public Long createIssue(IssueSaveRequest request) {
        LotLotteryType type = requireType(request.typeCode());
        Long exists = issueMapper.selectCount(new LambdaQueryWrapper<LotIssue>()
                .eq(LotIssue::getTypeCode, request.typeCode())
                .eq(LotIssue::getIssueNo, request.issueNo()));
        if (exists != null && exists > 0) {
            throw new BizException(ResultCode.STATE_CONFLICT, "该期号已存在");
        }
        LotIssue issue = new LotIssue();
        issue.setTypeCode(type.getCode());
        issue.setIssueNo(request.issueNo());
        issue.setDrawTime(request.drawTime());
        int offset = request.saleEndOffsetMin() != null ? request.saleEndOffsetMin()
                : (type.getSaleEndOffsetMin() == null ? 20 : type.getSaleEndOffsetMin());
        issue.setSaleEndTime(request.saleEndTime() != null ? request.saleEndTime()
                : request.drawTime().minusMinutes(offset));
        issue.setDrawStatus(LotIssue.DRAW_PENDING);
        issue.setSettleStatus(LotIssue.SETTLE_PENDING);
        issue.setDataSource(1);
        issueMapper.insert(issue);
        auditLogService.record("ISSUE", "CREATE", "ISSUE", issue.getId(), null,
                type.getCode() + "-" + request.issueNo(), "新增期次");
        return issue.getId();
    }

    @Override
    @Transactional
    public void updateIssue(Long id, IssueSaveRequest request) {
        LotIssue issue = requireIssue(id);
        if (issue.isSettled()) {
            throw new BizException(ResultCode.STATE_CONFLICT, "已结算期次不可修改");
        }
        String before = issue.getDrawTime() + "/" + issue.getSaleEndTime();
        int offset = request.saleEndOffsetMin() != null ? request.saleEndOffsetMin() : 20;
        issue.setDrawTime(request.drawTime());
        issue.setSaleEndTime(request.saleEndTime() != null ? request.saleEndTime()
                : request.drawTime().minusMinutes(offset));
        issueMapper.updateById(issue);
        auditLogService.record("ISSUE", "UPDATE", "ISSUE", id, before,
                issue.getDrawTime() + "/" + issue.getSaleEndTime(), "编辑期次（含延期）");
    }

    @Override
    public String prepareDrawResult(Long issueId, DrawResultRequest request) {
        LotIssue issue = requireIssue(issueId);
        validateNumbers(requireType(issue.getTypeCode()), request.redNumbers(), request.blueNumbers());
        String ticket = "RV" + System.currentTimeMillis();
        issue.setReviewTicket(ticket);
        issueMapper.updateById(issue);
        return ticket;
    }

    @Override
    @Transactional
    public void confirmDrawResult(Long issueId, DrawResultRequest request) {
        LotIssue issue = requireIssue(issueId);
        boolean force = Boolean.TRUE.equals(request.force());
        if (issue.isDrawn() && !force) {
            throw new BizException(ResultCode.STATE_CONFLICT, "该期已开奖，需超管强制更正");
        }
        if (!force) {
            if (request.reviewTicket() == null || !request.reviewTicket().equals(issue.getReviewTicket())) {
                throw new BizException(ResultCode.STATE_CONFLICT, "复核票据不匹配，请先录入");
            }
        }
        validateNumbers(requireType(issue.getTypeCode()), request.redNumbers(), request.blueNumbers());

        String before = issue.getRedNumbers() + "/" + issue.getBlueNumbers();
        issue.setRedNumbers(request.redNumbers());
        issue.setBlueNumbers(request.blueNumbers());
        issue.setDrawStatus(LotIssue.DRAW_DONE);
        issue.setDataSource(2);
        issue.setReviewTicket(null);
        issueMapper.updateById(issue);
        auditLogService.record("ISSUE", force ? "DRAW_FORCE_CORRECT" : "DRAW_CONFIRM", "ISSUE", issueId,
                before, request.redNumbers() + "/" + request.blueNumbers(), "录入开奖号");
        log.info("期次 {} {} 开奖号写入: {} + {}", issue.getTypeCode(), issue.getIssueNo(),
                request.redNumbers(), request.blueNumbers());
    }

    @Override
    public void validateNumbers(LotLotteryType type, String red, String blue) {
        validateDrawNumbers(type, red, blue);
    }

    @Override
    public void validateDrawNumbers(LotLotteryType type, String red, String blue) {
        if (red == null || red.isBlank()) {
            throw new BizException(ResultCode.ILLEGAL_ARGUMENT, "开奖号码不能为空");
        }
        JsonNode groups = parseConfig(type).get("groups");
        if (groups == null || !groups.isArray() || groups.isEmpty()) {
            throw new BizException(ResultCode.ILLEGAL_ARGUMENT, "彩种玩法配置缺失");
        }
        checkDrawGroup(groups.get(0), parseNumbers(red), type.getName() + "主号码");
        if (blue != null && !blue.isBlank() && groups.size() > 1) {
            checkDrawGroup(groups.get(1), parseNumbers(blue), type.getName() + "副号码");
        }
    }

    @Override
    public List<Integer> redList(LotIssue issue) {
        return parseNumbers(issue.getRedNumbers());
    }

    @Override
    public List<Integer> blueList(LotIssue issue) {
        return parseNumbers(issue.getBlueNumbers());
    }

    @Override
    @Transactional
    public void saveType(LotteryTypeSaveRequest request) {
        Long exists = typeMapper.selectCount(new LambdaQueryWrapper<LotLotteryType>()
                .eq(LotLotteryType::getCode, request.code()));
        if (exists != null && exists > 0) {
            throw new BizException(ResultCode.STATE_CONFLICT, "彩种编码已存在");
        }
        LotLotteryType type = new LotLotteryType();
        type.setCode(request.code());
        type.setName(request.name());
        type.setCategory(request.category() == null ? 1 : request.category());
        type.setDrawWeekdays(request.drawWeekdays());
        type.setDrawTimeOfDay(request.drawTimeOfDay());
        type.setSaleEndOffsetMin(request.saleEndOffsetMin() == null ? 20 : request.saleEndOffsetMin());
        type.setPlayConfig(request.playConfig());
        type.setSortNo(request.sortNo() == null ? 99 : request.sortNo());
        type.setStatus(request.status() == null ? 1 : request.status());
        typeMapper.insert(type);
        auditLogService.record("ISSUE", "TYPE_CREATE", "LOTTERY_TYPE", type.getId().longValue(), null,
                request.code(), "新增彩种");
    }

    @Override
    @Transactional
    public void updateType(Integer id, LotteryTypeSaveRequest request) {
        LotLotteryType type = typeMapper.selectById(id);
        if (type == null) {
            throw new BizException(ResultCode.LOTTERY_TYPE_NOT_FOUND);
        }
        String before = type.getCode() + "/" + type.getStatus();
        type.setName(request.name());
        type.setCategory(request.category() == null ? type.getCategory() : request.category());
        type.setDrawWeekdays(request.drawWeekdays());
        type.setDrawTimeOfDay(request.drawTimeOfDay());
        type.setSaleEndOffsetMin(request.saleEndOffsetMin() == null ? type.getSaleEndOffsetMin() : request.saleEndOffsetMin());
        type.setPlayConfig(request.playConfig());
        type.setSortNo(request.sortNo() == null ? type.getSortNo() : request.sortNo());
        type.setStatus(request.status() == null ? type.getStatus() : request.status());
        typeMapper.updateById(type);
        auditLogService.record("ISSUE", "TYPE_UPDATE", "LOTTERY_TYPE", id.longValue(), before,
                type.getCode() + "/" + type.getStatus(), "编辑彩种");
    }

    private void checkGroup(JsonNode group, List<Integer> values, String label) {
        int min = group.path("min").asInt(0);
        int max = group.path("max").asInt(99);
        int pickMin = group.path("pickMin").asInt(1);
        int pickMax = group.path("pickMax").asInt(10);
        if (values.size() < pickMin || values.size() > pickMax) {
            throw new BizException(ResultCode.ILLEGAL_ARGUMENT,
                    label + "个数应在 " + pickMin + "-" + pickMax + " 之间");
        }
        for (Integer v : values) {
            if (v < min || v > max) {
                throw new BizException(ResultCode.ILLEGAL_ARGUMENT,
                        label + "取值范围应为 " + min + "-" + max + "，当前值 " + v);
            }
        }
    }

    /**
     * 开奖号码校验：个数由官方决定，不能用投注可选的 pickMax 来卡。
     * 快乐8 一注最多选 10 个，但每期开奖开 20 个号码，两者是不同概念。
     * 分组配置了 drawCount 时按它精确校验，未配置时只校验取值范围。
     */
    private void checkDrawGroup(JsonNode group, List<Integer> values, String label) {
        int min = group.path("min").asInt(0);
        int max = group.path("max").asInt(99);
        int drawCount = group.path("drawCount").asInt(0);
        if (values.isEmpty()) {
            throw new BizException(ResultCode.ILLEGAL_ARGUMENT, label + "不能为空");
        }
        if (drawCount > 0 && values.size() != drawCount) {
            throw new BizException(ResultCode.ILLEGAL_ARGUMENT,
                    label + "应为 " + drawCount + " 个号码，实际 " + values.size() + " 个");
        }
        for (Integer v : values) {
            if (v < min || v > max) {
                throw new BizException(ResultCode.ILLEGAL_ARGUMENT,
                        label + "取值范围应为 " + min + "-" + max + "，当前值 " + v);
            }
        }
    }

    private JsonNode parseConfig(LotLotteryType type) {
        if (type.getPlayConfig() == null || type.getPlayConfig().isBlank()) {
            throw new BizException(ResultCode.ILLEGAL_ARGUMENT, "彩种未配置玩法");
        }
        return objectMapper.readTree(type.getPlayConfig());
    }

    private List<Integer> parseNumbers(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split("[,\\s]+"))
                .filter(s -> !s.isBlank())
                .map(s -> {
                    try {
                        return Integer.parseInt(s.trim());
                    } catch (NumberFormatException e) {
                        throw new BizException(ResultCode.ILLEGAL_ARGUMENT, "号码格式不正确: " + s);
                    }
                })
                .collect(Collectors.toList());
    }

    private LotteryTypeVO toTypeVO(LotLotteryType type) {
        return new LotteryTypeVO(type.getId(), type.getCode(), type.getName(), type.getCategory(),
                type.getDrawWeekdays(), type.getDrawTimeOfDay(), type.getSaleEndOffsetMin(),
                type.getPlayConfig(), type.getSortNo(), type.getStatus());
    }

    private IssueVO toIssueVO(LotIssue issue) {
        LocalDateTime now = LocalDateTime.now();
        long remain = issue.getSaleEndTime() == null ? 0
                : Duration.between(now, issue.getSaleEndTime()).getSeconds();
        return new IssueVO(issue.getId(), issue.getTypeCode(), issue.getIssueNo(), issue.getDrawTime(),
                issue.getSaleEndTime(), issue.getRedNumbers(), issue.getBlueNumbers(), issue.getExtra(),
                issue.getDrawStatus(), drawStatusName(issue.getDrawStatus()), issue.getSettleStatus(),
                remain <= 0, Math.max(remain, 0), issue.getDataSource());
    }

    private String drawStatusName(Integer status) {
        if (status == null) {
            return "未开奖";
        }
        return switch (status) {
            case 0 -> "未开奖";
            case 1 -> "已开奖";
            case 2 -> "延期";
            case 3 -> "取消";
            default -> "未知";
        };
    }
}
