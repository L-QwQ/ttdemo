package com.ttdemo.modules.lottery.sync.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ttdemo.common.api.ResultCode;
import com.ttdemo.common.exception.BizException;
import com.ttdemo.modules.lottery.dto.IssueVO;
import com.ttdemo.modules.lottery.dto.SyncResultVO;
import com.ttdemo.modules.lottery.entity.LotIssue;
import com.ttdemo.modules.lottery.entity.LotLotteryType;
import com.ttdemo.modules.lottery.entity.LotSyncError;
import com.ttdemo.modules.lottery.mapper.LotIssueMapper;
import com.ttdemo.modules.lottery.mapper.LotSyncErrorMapper;
import com.ttdemo.modules.lottery.service.LotteryService;
import com.ttdemo.modules.lottery.sync.CwlDrawClient;
import com.ttdemo.modules.lottery.sync.DrawRecord;
import com.ttdemo.modules.lottery.sync.DrawSyncService;
import com.ttdemo.modules.lottery.sync.SportteryDrawClient;
import com.ttdemo.modules.prediction.entity.PrdPrediction;
import com.ttdemo.modules.prediction.mapper.PrdPredictionMapper;
import com.ttdemo.modules.system.service.AuditLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 开奖数据同步：从官方公开接口拉取并落地到 lot_issue。
 * 按期号 upsert，可重复执行；单条失败不影响其余记录。
 */
@Service
public class DrawSyncServiceImpl implements DrawSyncService {

    private static final Logger log = LoggerFactory.getLogger(DrawSyncServiceImpl.class);
    private static final int MAX_COUNT = 500;

    /** 彩种 → 官方数据源 */
    private static final Map<String, SourceSpec> SOURCES;

    static {
        Map<String, SourceSpec> map = new LinkedHashMap<>();
        map.put("SSQ", new SourceSpec("cwl", "ssq", 0));
        map.put("DLT", new SourceSpec("sporttery", null, 85));
        map.put("FC3D", new SourceSpec("cwl", "3d", 0));
        map.put("PL3", new SourceSpec("sporttery", null, 35));
        map.put("KL8", new SourceSpec("cwl", "kl8", 0));
        SOURCES = Collections.unmodifiableMap(map);
    }

    private record SourceSpec(String kind, String gameName, int gameNo) {
    }

    private final LotIssueMapper issueMapper;
    private final LotSyncErrorMapper syncErrorMapper;
    private final PrdPredictionMapper predictionMapper;
    private final LotteryService lotteryService;
    private final CwlDrawClient cwlClient;
    private final SportteryDrawClient sportteryClient;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public DrawSyncServiceImpl(LotIssueMapper issueMapper, LotSyncErrorMapper syncErrorMapper,
                               PrdPredictionMapper predictionMapper, LotteryService lotteryService,
                               CwlDrawClient cwlClient, SportteryDrawClient sportteryClient,
                               AuditLogService auditLogService, ObjectMapper objectMapper) {
        this.issueMapper = issueMapper;
        this.syncErrorMapper = syncErrorMapper;
        this.predictionMapper = predictionMapper;
        this.lotteryService = lotteryService;
        this.cwlClient = cwlClient;
        this.sportteryClient = sportteryClient;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @Override
    public SyncResultVO sync(String typeCode, int count) {
        LotLotteryType type = lotteryService.requireType(typeCode);
        SourceSpec spec = SOURCES.get(type.getCode());
        if (spec == null) {
            return new SyncResultVO(type.getCode(), type.getName(), 0, 0, 0, 0, 0,
                    "该彩种未配置官方数据源", List.of());
        }
        int limit = Math.max(1, Math.min(count, MAX_COUNT));
        List<DrawRecord> records;
        try {
            records = "cwl".equals(spec.kind())
                    ? cwlClient.fetch(spec.gameName(), limit)
                    : sportteryClient.fetch(spec.gameNo(), limit);
        } catch (Exception e) {
            log.warn("拉取 {} 开奖数据失败", type.getCode(), e);
            return new SyncResultVO(type.getCode(), type.getName(), 0, 0, 0, 0, 0,
                    "拉取失败：" + rootMessage(e), List.of(rootMessage(e)));
        }
        if (records.isEmpty()) {
            return new SyncResultVO(type.getCode(), type.getName(), 0, 0, 0, 0, 0,
                    "数据源未返回记录（可能当天尚未开奖）", List.of());
        }

        int inserted = 0;
        int updated = 0;
        int skipped = 0;
        int failed = 0;
        int backfilled = 0;
        List<String> errors = new ArrayList<>();

        for (DrawRecord record : records) {
            try {
                LotIssue existing = issueMapper.selectOne(new LambdaQueryWrapper<LotIssue>()
                        .eq(LotIssue::getTypeCode, type.getCode())
                        .eq(LotIssue::getIssueNo, record.issueNo()));
                if (existing != null && existing.isSettled()) {
                    // 已结算期次不允许再改开奖号码（否则结算结果与号码脱钩），
                    // 但缺「销售额/奖池/奖项」这类纯展示字段时可以单独补齐
                    if (needsExtraBackfill(existing)) {
                        issueMapper.update(null, new LambdaUpdateWrapper<LotIssue>()
                                .eq(LotIssue::getId, existing.getId())
                                .set(LotIssue::getExtra, buildExtra(type, record)));
                        backfilled++;
                    } else {
                        skipped++;
                    }
                    continue;
                }
                validate(type, record);
                if (existing == null) {
                    issueMapper.insert(buildIssue(type, record));
                    inserted++;
                } else {
                    boolean changed = existing.getDrawStatus() == null
                            || existing.getDrawStatus() != LotIssue.DRAW_DONE
                            || !Objects.equals(existing.getRedNumbers(), record.red())
                            || !Objects.equals(nvl(existing.getBlueNumbers()), nvl(blankToNull(record.blue())))
                            // 老数据只存了号码指标，缺 sales/pool/prizes：重跑同步时按变更处理，回填官方奖项
                            || existing.getExtra() == null
                            || !existing.getExtra().contains("\"prizes\"");
                    if (changed) {
                        applyDraw(existing, type, record);
                        issueMapper.updateById(existing);
                        updated++;
                    } else {
                        skipped++;
                    }
                }
            } catch (Exception e) {
                failed++;
                String message = record.issueNo() + "：" + rootMessage(e);
                errors.add(message);
                saveError(type.getCode(), record, rootMessage(e));
            }
        }

        String message = String.format("拉取 %d 条，新增 %d，更正 %d，跳过 %d，失败 %d%s",
                records.size(), inserted, updated, skipped, failed,
                backfilled > 0 ? "，补全奖项 " + backfilled : "");
        log.info("开奖同步 {}：{}", type.getCode(), message);
        auditLogService.record("ISSUE", "DRAW_SYNC", "LOTTERY_TYPE", type.getId() == null ? null : type.getId().longValue(),
                null, message, "官方数据源同步");
        return new SyncResultVO(type.getCode(), type.getName(), records.size(),
                inserted, updated, skipped, failed, message, errors);
    }

    @Override
    public List<SyncResultVO> syncAll(int count) {
        List<SyncResultVO> results = new ArrayList<>();
        for (String code : SOURCES.keySet()) {
            try {
                results.add(sync(code, count));
            } catch (Exception e) {
                log.warn("同步 {} 失败", code, e);
                results.add(new SyncResultVO(code, code, 0, 0, 0, 0, 0,
                        "同步异常：" + rootMessage(e), List.of(rootMessage(e))));
            }
        }
        return results;
    }

    @Override
    public Map<String, Integer> cleanupNonOfficialIssues() {
        // 官方期号：福彩 7 位（2026122）、体彩 5 位（26122）；测试数据为 6 位、8 位等其他长度
        List<LotIssue> suspects = issueMapper.selectList(new LambdaQueryWrapper<LotIssue>()
                .apply("LENGTH(issue_no) NOT IN (5, 7)"));
        int deleted = 0;
        int skipped = 0;
        for (LotIssue issue : suspects) {
            long linked = predictionMapper.selectCount(new LambdaQueryWrapper<PrdPrediction>()
                    .eq(PrdPrediction::getIssueId, issue.getId()));
            if (linked > 0) {
                skipped++;
                continue;
            }
            issueMapper.deleteById(issue.getId());
            deleted++;
        }
        if (deleted > 0) {
            auditLogService.record("ISSUE", "CLEANUP_FAKE", "ISSUE", null, null,
                    String.valueOf(deleted), "清理非官方期号（无预测关联）");
        }
        return Map.of("deleted", deleted, "skipped", skipped);
    }

    @Override
    public List<IssueVO> ensureUpcoming() {
        List<IssueVO> created = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (String code : SOURCES.keySet()) {
            try {
                LotLotteryType type = lotteryService.requireType(code);
                // 已存在合法期号的在售期次则不重复创建（排除测试期号）
                LotIssue onSale = issueMapper.selectOne(new LambdaQueryWrapper<LotIssue>()
                        .eq(LotIssue::getTypeCode, code)
                        .gt(LotIssue::getSaleEndTime, now)
                        .eq(LotIssue::getDrawStatus, LotIssue.DRAW_PENDING)
                        .apply("LENGTH(issue_no) IN (5, 7)")
                        .orderByAsc(LotIssue::getSaleEndTime)
                        .last("limit 1"));
                if (onSale != null) {
                    continue;
                }
                // 期号基准：该彩种最新一期（不限定来源，同步与推算的都要看），按开奖时间倒序
                LotIssue latest = issueMapper.selectOne(new LambdaQueryWrapper<LotIssue>()
                        .eq(LotIssue::getTypeCode, code)
                        .apply("LENGTH(issue_no) IN (5, 7)")
                        .orderByDesc(LotIssue::getDrawTime)
                        .orderByDesc(LotIssue::getId)
                        .last("limit 1"));
                if (latest == null) {
                    continue;
                }
                // 开奖时间基准必须取「当前时刻」和「已有最新一期的开奖时间」的较大者：
                // 只按 now 推算时，若最新一期的开奖时刻还没到（例如 21:03 时最新期是 21:15），
                // 会再次推出同一个 21:15，造成同一开奖时间挂着多个期号（重复期次）
                LocalDateTime baseTime = now;
                if (latest.getDrawTime() != null && latest.getDrawTime().isAfter(baseTime)) {
                    baseTime = latest.getDrawTime();
                }
                LocalDateTime drawTime = nextDrawTime(type, baseTime);
                String base = latest.getIssueNo();
                String issueNo = null;
                for (int attempt = 0; attempt < 8; attempt++) {
                    String candidate = nextIssueNo(base, drawTime.toLocalDate());
                    if (candidate == null) {
                        break;
                    }
                    // 期号被占用 → 期号往后推；开奖时间被占用 → 时间往后推。两者互不影响
                    boolean issueTaken = issueMapper.selectCount(new LambdaQueryWrapper<LotIssue>()
                            .eq(LotIssue::getTypeCode, code)
                            .eq(LotIssue::getIssueNo, candidate)) > 0;
                    boolean timeTaken = issueMapper.selectCount(new LambdaQueryWrapper<LotIssue>()
                            .eq(LotIssue::getTypeCode, code)
                            .eq(LotIssue::getDrawTime, drawTime)) > 0;
                    if (!issueTaken && !timeTaken) {
                        issueNo = candidate;
                        break;
                    }
                    if (issueTaken) {
                        base = candidate;
                    }
                    if (timeTaken) {
                        drawTime = nextDrawTime(type, drawTime.plusMinutes(1));
                    }
                }
                if (issueNo == null) {
                    continue;
                }
                LotIssue issue = new LotIssue();
                issue.setTypeCode(code);
                issue.setIssueNo(issueNo);
                issue.setDrawTime(drawTime);
                int offset = type.getSaleEndOffsetMin() == null ? 20 : type.getSaleEndOffsetMin();
                issue.setSaleEndTime(drawTime.minusMinutes(offset));
                issue.setDrawStatus(LotIssue.DRAW_PENDING);
                issue.setSettleStatus(LotIssue.SETTLE_PENDING);
                issue.setDataSource(2);
                issueMapper.insert(issue);
                created.add(lotteryService.getIssue(issue.getId()));
                log.info("生成下一期 {} {}，开奖 {}", code, issueNo, drawTime);
            } catch (Exception e) {
                log.warn("生成 {} 下一期失败", code, e);
            }
        }
        return created;
    }

    /** 按开奖日历找最近一个尚未到达的开奖时间 */
    private LocalDateTime nextDrawTime(LotLotteryType type, LocalDateTime now) {
        LocalTime time = type.getDrawTimeOfDay() == null ? LocalTime.of(21, 15) : type.getDrawTimeOfDay();
        String raw = type.getDrawWeekdays();
        java.util.Set<Integer> weekdays = null;
        if (raw != null && !raw.isBlank() && !"0".equals(raw.trim())) {
            weekdays = Arrays.stream(raw.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(s -> {
                        try {
                            return Integer.parseInt(s);
                        } catch (NumberFormatException e) {
                            return -1;
                        }
                    })
                    .filter(v -> v >= 1 && v <= 7)
                    .collect(Collectors.toSet());
            if (weekdays.isEmpty()) {
                weekdays = null;
            }
        }
        for (int i = 0; i < 14; i++) {
            LocalDate date = now.toLocalDate().plusDays(i);
            if (weekdays != null && !weekdays.contains(date.getDayOfWeek().getValue())) {
                continue;
            }
            LocalDateTime candidate = date.atTime(time);
            if (candidate.isAfter(now)) {
                return candidate;
            }
        }
        return now.plusDays(1).toLocalDate().atTime(time);
    }

    /** 期号 +1：福彩 7 位（2026122 → 2026123），体彩 5 位（26122 → 26123），跨年重置为 001 */
    private String nextIssueNo(String latest, LocalDate drawDate) {
        if (latest == null) {
            return null;
        }
        try {
            if (latest.length() == 7) {
                int year = Integer.parseInt(latest.substring(0, 4));
                int seq = Integer.parseInt(latest.substring(4));
                int newYear = drawDate.getYear();
                if (newYear != year) {
                    return String.format("%d001", newYear);
                }
                return String.format("%d%03d", newYear, seq + 1);
            }
            if (latest.length() == 5) {
                int yy = Integer.parseInt(latest.substring(0, 2));
                int seq = Integer.parseInt(latest.substring(2));
                int newYy = drawDate.getYear() % 100;
                if (newYy != yy) {
                    return String.format("%02d001", newYy);
                }
                return String.format("%02d%03d", yy, seq + 1);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return null;
    }

    private void validate(LotLotteryType type, DrawRecord record) {
        if (record.red() == null || record.red().isBlank()) {
            throw new BizException(ResultCode.ILLEGAL_ARGUMENT, "缺少主号码");
        }
        JsonNode groups = parseGroups(type);
        if (groups.size() > 1 && (record.blue() == null || record.blue().isBlank())) {
            throw new BizException(ResultCode.ILLEGAL_ARGUMENT, "缺少副号码");
        }
        lotteryService.validateDrawNumbers(type, record.red(), record.blue());
    }

    private LotIssue buildIssue(LotLotteryType type, DrawRecord record) {
        LotIssue issue = new LotIssue();
        issue.setTypeCode(type.getCode());
        issue.setIssueNo(record.issueNo());
        issue.setRedNumbers(record.red());
        issue.setBlueNumbers(blankToNull(record.blue()));
        issue.setExtra(buildExtra(type, record));
        issue.setDrawStatus(LotIssue.DRAW_DONE);
        issue.setSettleStatus(LotIssue.SETTLE_PENDING);
        issue.setDataSource(1);
        applyTimes(type, issue, record);
        return issue;
    }

    private void applyDraw(LotIssue issue, LotLotteryType type, DrawRecord record) {
        issue.setRedNumbers(record.red());
        issue.setBlueNumbers(blankToNull(record.blue()));
        issue.setExtra(buildExtra(type, record));
        issue.setDrawStatus(LotIssue.DRAW_DONE);
        issue.setDataSource(1);
        issue.setSettleError(null);
        applyTimes(type, issue, record);
    }

    private void applyTimes(LotLotteryType type, LotIssue issue, DrawRecord record) {
        LocalTime time = type.getDrawTimeOfDay() == null ? LocalTime.of(21, 15) : type.getDrawTimeOfDay();
        LocalDateTime drawTime = record.drawDate().atTime(time);
        int offset = type.getSaleEndOffsetMin() == null ? 20 : type.getSaleEndOffsetMin();
        issue.setDrawTime(drawTime);
        issue.setSaleEndTime(drawTime.minusMinutes(offset));
    }

    /**
     * extra 里除号码指标外，还落官方公布的销售额、奖池与各奖等中奖情况，
     * 供「开奖结果」页展示中奖注数/单注奖金，不必每次回源再查。
     */
    private String buildExtra(LotLotteryType type, DrawRecord record) {
        List<Integer> reds = parse(record.red());
        if (reds.isEmpty()) {
            return null;
        }
        int sum = reds.stream().mapToInt(Integer::intValue).sum();
        int max = Collections.max(reds);
        int min = Collections.min(reds);
        int mid = midOf(type);
        long odd = reds.stream().filter(n -> n % 2 == 1).count();
        long big = reds.stream().filter(n -> n > mid).count();

        StringBuilder json = new StringBuilder(256);
        json.append(String.format("{\"sum\":%d,\"span\":%d,\"oddEven\":\"%d:%d\",\"bigSmall\":\"%d:%d\"",
                sum, max - min, odd, reds.size() - odd, big, reds.size() - big));

        JsonNode node = null;
        try {
            if (record.raw() != null && !record.raw().isBlank()) {
                node = objectMapper.readTree(record.raw());
            }
        } catch (Exception e) {
            log.debug("开奖原始报文解析失败 {} {}", record.issueNo(), e.getMessage());
        }
        if (node != null) {
            long sales = firstYuan(node, "sales", "totalSaleAmount");
            long pool = firstYuan(node, "poolmoney", "poolBalance", "poolBalanceAfterdraw");
            json.append(",\"sales\":").append(sales);
            json.append(",\"pool\":").append(pool);
            json.append(",\"prizes\":");
            appendPrizes(json, node, type.getCode());
        }
        json.append('}');
        return json.toString();
    }

    /** 官方金额字段有的是数字、有的带千分位与小数，统一折算成「元」 */
    private long firstYuan(JsonNode node, String... fields) {
        for (String f : fields) {
            JsonNode v = node.get(f);
            if (v == null || v.isNull()) {
                continue;
            }
            String raw = v.isNumber() ? v.asString() : v.asString("").trim();
            if (raw.isEmpty()) {
                continue;
            }
            String digits = raw.replace(",", "").replace("，", "").trim();
            try {
                return (long) Double.parseDouble(digits);
            } catch (NumberFormatException ignored) {
                // 试下一个字段
            }
        }
        return 0;
    }

    /** 福彩用 prizegrades（type 为序号或 x10z10 编码），体彩用 prizeLevelList（prizeLevel 直接是中文） */
    private void appendPrizes(StringBuilder json, JsonNode node, String typeCode) {
        List<String> rows = new ArrayList<>();
        JsonNode cwl = node.get("prizegrades");
        if (cwl != null && cwl.isArray()) {
            for (JsonNode g : cwl) {
                String name = prizeLevelName(typeCode, g.path("type").asString(""), rows.size() + 1);
                String countRaw = g.path("typenum").asString("").trim();
                String moneyRaw = g.path("typemoney").asString("").trim();
                if (name == null || countRaw.isEmpty() || moneyRaw.isEmpty()) {
                    continue;
                }
                long count = (long) parseDoubleSafe(countRaw);
                long amount = (long) parseDoubleSafe(moneyRaw.replace(",", ""));
                if (count <= 0) {
                    continue;
                }
                rows.add(String.format("{\"level\":\"%s\",\"count\":%d,\"amount\":%d}", name, count, amount));
            }
        } else {
            JsonNode list = node.get("prizeLevelList");
            if (list != null && list.isArray()) {
                for (JsonNode g : list) {
                    String name = g.path("prizeLevel").asString("").trim();
                    long count = (long) parseDoubleSafe(g.path("stakeCount").asString("0").replace(",", ""));
                    long amount = (long) parseDoubleSafe(g.path("stakeAmount").asString("0").replace(",", ""));
                    if (name.isEmpty() || count <= 0) {
                        continue;
                    }
                    rows.add(String.format("{\"level\":\"%s\",\"count\":%d,\"amount\":%d}", name, count, amount));
                }
            }
        }
        json.append('[').append(String.join(",", rows)).append(']');
    }

    /**
     * 福彩接口的奖等只有序号：双色球/3D 按序号映射，快乐8 用 x{选} z{中} 编码。
     * 取不到名称时返回 null，该条直接跳过（宁可少一条也不展示无意义的类型码）。
     */
    private String prizeLevelName(String typeCode, String rawType, int fallbackIndex) {
        if (rawType == null || rawType.isBlank()) {
            return null;
        }
        if ("KL8".equals(typeCode)) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("x(\\d+)z(\\d+)").matcher(rawType.trim());
            if (m.matches()) {
                return "选" + m.group(1) + "中" + m.group(2);
            }
            return null;
        }
        try {
            int idx = Integer.parseInt(rawType.trim());
            String[] names = {"一等奖", "二等奖", "三等奖", "四等奖", "五等奖", "六等奖", "七等奖"};
            if (idx >= 1 && idx <= names.length) {
                return names[idx - 1];
            }
        } catch (NumberFormatException ignored) {
            // 落回 null
        }
        return null;
    }

    private double parseDoubleSafe(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            return Double.parseDouble(raw.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private int midOf(LotLotteryType type) {
        try {
            JsonNode groups = parseGroups(type);
            int min = groups.get(0).path("min").asInt(1);
            int max = groups.get(0).path("max").asInt(33);
            return (min + max) / 2;
        } catch (Exception e) {
            return 17;
        }
    }

    private JsonNode parseGroups(LotLotteryType type) {
        if (type.getPlayConfig() == null || type.getPlayConfig().isBlank()) {
            throw new BizException(ResultCode.ILLEGAL_ARGUMENT, "彩种未配置玩法");
        }
        JsonNode groups = objectMapper.readTree(type.getPlayConfig()).get("groups");
        if (groups == null || !groups.isArray() || groups.isEmpty()) {
            throw new BizException(ResultCode.ILLEGAL_ARGUMENT, "彩种玩法配置缺少号码分组");
        }
        return groups;
    }

    private List<Integer> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split("[,\\s]+"))
                .filter(s -> !s.isBlank())
                .map(s -> {
                    try {
                        return Integer.parseInt(s.trim());
                    } catch (NumberFormatException e) {
                        return -1;
                    }
                })
                .filter(v -> v >= 0)
                .collect(Collectors.toList());
    }

    private void saveError(String typeCode, DrawRecord record, String message) {
        try {
            LotSyncError error = new LotSyncError();
            error.setTypeCode(typeCode);
            error.setIssueNo(record.issueNo());
            error.setErrorType("VALIDATE_FAIL");
            error.setRawData(record.raw());
            error.setMessage(message.length() > 500 ? message.substring(0, 500) : message);
            error.setHandled(0);
            syncErrorMapper.insert(error);
        } catch (Exception e) {
            log.warn("写入同步异常日志失败", e);
        }
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** 老数据只存了号码指标（sum/span/oddEven/bigSmall），缺少官方销售额与奖项 */
    private boolean needsExtraBackfill(LotIssue issue) {
        String extra = issue.getExtra();
        return extra == null || !extra.contains("\"prizes\"");
    }

    private String rootMessage(Throwable e) {
        Throwable cursor = e;
        while (cursor.getCause() != null && cursor.getCause() != cursor) {
            cursor = cursor.getCause();
        }
        String message = cursor.getMessage();
        return message == null ? cursor.getClass().getSimpleName() : message;
    }
}
