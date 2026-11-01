package com.ttdemo.modules.random.service.impl;

import com.ttdemo.common.api.ResultCode;
import com.ttdemo.common.exception.BizException;
import com.ttdemo.modules.lottery.entity.LotIssue;
import com.ttdemo.modules.lottery.entity.LotLotteryType;
import com.ttdemo.modules.lottery.service.LotteryService;
import com.ttdemo.modules.random.MysticEngine;
import com.ttdemo.modules.random.RandomMethod;
import com.ttdemo.modules.random.dto.RandomDtos;
import com.ttdemo.modules.random.mapper.RndDailyUsageMapper;
import com.ttdemo.modules.random.service.RandomPredictionService;
import com.ttdemo.modules.system.service.SysConfigService;
import com.ttdemo.modules.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 随机预测生成器：14 种策略（基础 / 统计 / 玄学），输出可直接用于发布的结构化预测项。
 * 纯计算，不落库；结果不代表任何预测能力，页面必须展示免责声明。
 * <p>
 * 会员分级：BASIC 组永久免费；STATS 与 MYSTIC 组为会员专享，非会员按天计算免费试用次数。
 */
@Service
public class RandomPredictionServiceImpl implements RandomPredictionService {

    /** 位号玩法允许重复号码（如 3D 的 1,1,2），其余彩种号码不重复 */
    private static final Set<String> POSITIONAL_TYPES = Set.of("FC3D", "PL3");
    /**
     * 一注的号码个数（标准注结构）。play_config 的 pickMin/pickMax 是可投注的号码个数范围，不等于一注的数量。
     * 快乐8 每期开奖开 20 个号码，但一注（选十玩法）只能选 10 个，二者不能混用。
     */
    private static final Map<String, Integer> DEFAULT_STANDARD = Map.of(
            "SSQ:RED", 6, "SSQ:BLUE", 1,
            "DLT:RED", 5, "DLT:BLUE", 2,
            "FC3D:RED", 3, "PL3:RED", 3,
            "KL8:RED", 10);
    private static final int MAX_COUNT = 5;
    private static final int MAX_HISTORY = 100;
    private static final int DEFAULT_HISTORY = 30;
    private static final String DISCLAIMER =
            "以上号码由算法随机生成，仅用于演示与个人参考，不构成任何投注建议，也不代表平台对结果的任何承诺。";
    /** 一注 2 元，只用于把复式注数折算成投注金额，平台不提供代购 */
    private static final BigDecimal UNIT_PRICE = new BigDecimal("2");
    private static final String MODE_SINGLE = "SINGLE";
    private static final String MODE_MULTI = "MULTI";

    private static final String CFG_GRADE_ENABLED = "random.vip.enabled";
    private static final String CFG_FREE_QUOTA = "random.vip.daily.free";
    private static final int DEFAULT_FREE_QUOTA = 3;

    private final LotteryService lotteryService;
    private final ObjectMapper objectMapper;
    private final SysConfigService configService;
    private final RndDailyUsageMapper usageMapper;
    private final UserService userService;

    public RandomPredictionServiceImpl(LotteryService lotteryService, ObjectMapper objectMapper,
                                       SysConfigService configService, RndDailyUsageMapper usageMapper,
                                       UserService userService) {
        this.lotteryService = lotteryService;
        this.objectMapper = objectMapper;
        this.configService = configService;
        this.usageMapper = usageMapper;
        this.userService = userService;
    }

    @Override
    public List<RandomDtos.MethodVO> methods(String typeCode, Long userId) {
        int history = 0;
        if (typeCode != null && !typeCode.isBlank()) {
            try {
                history = lotteryService.listDrawnIssues(typeCode, MAX_HISTORY).size();
            } catch (Exception ignored) {
                history = 0;
            }
        }
        boolean gradeEnabled = configService.getBoolean(CFG_GRADE_ENABLED, true);
        boolean vip = userId != null && userService.isVip(userId);
        int freeQuota = configService.getInt(CFG_FREE_QUOTA, DEFAULT_FREE_QUOTA);
        int usedToday = (gradeEnabled && userId != null && !vip) ? currentUsed(userId) : 0;
        int remain = (!gradeEnabled || vip) ? -1 : Math.max(0, freeQuota - usedToday);

        List<RandomDtos.MethodVO> list = new ArrayList<>();
        boolean hasType = typeCode != null && !typeCode.isBlank();
        for (RandomMethod method : RandomMethod.values()) {
            // 只有统计类依赖历史开奖；玄学类用日期与卦象，不依赖数据
            boolean needsHistory = method.getGroup() == RandomMethod.Group.STATS;
            String dataNote = null;
            if (needsHistory && hasType) {
                dataNote = history == 0
                        ? "当前彩种暂无开奖数据，将退化为纯随机"
                        : "基于近 " + history + " 期开奖数据";
            }
            if (!gradeEnabled) {
                dataNote = method.isMystic() ? "玄学算法，仅供娱乐" : dataNote;
            }
            // 游客直接视为锁定（登录后可免费试用），已登录但额度用完也锁定
            boolean locked = gradeEnabled && method.isVip()
                    && (userId == null || (!vip && remain <= 0));
            list.add(new RandomDtos.MethodVO(method.name(), method.getTitle(), method.getDescription(),
                    !locked, dataNote,
                    method.getGroup().name(), method.getGroup().getTitle(), method.getGroup().getSubtitle(),
                    method.isVip(), locked,
                    method.isVip() && gradeEnabled && !vip ? freeQuota : -1,
                    method.isVip() ? usedToday : 0,
                    method.isVip() ? remain : -1));
        }
        return list;
    }

    @Override
    public RandomDtos.QuotaVO quota(Long userId) {
        boolean gradeEnabled = configService.getBoolean(CFG_GRADE_ENABLED, true);
        boolean vip = userId != null && userService.isVip(userId);
        int freeQuota = configService.getInt(CFG_FREE_QUOTA, DEFAULT_FREE_QUOTA);
        int usedToday = userId == null ? 0 : currentUsed(userId);
        int remain = (!gradeEnabled || vip) ? -1 : Math.max(0, freeQuota - usedToday);
        return new RandomDtos.QuotaVO(vip, gradeEnabled, freeQuota, usedToday, remain,
                MysticEngine.todayAlmanac(LocalDate.now()));
    }

    private int currentUsed(Long userId) {
        Integer used = usageMapper.usedCount(userId, LocalDate.now());
        return used == null ? 0 : used;
    }

    @Override
    public RandomDtos.BetSpec betSpec(String typeCode) {
        if (typeCode == null || typeCode.isBlank()) {
            throw new BizException(ResultCode.ILLEGAL_ARGUMENT, "请选择彩种");
        }
        LotLotteryType type = lotteryService.requireType(typeCode);
        if (POSITIONAL_TYPES.contains(type.getCode())) {
            return new RandomDtos.BetSpec(false,
                    "位号玩法（福彩3D / 排列3）的复式要按百位、十位、个位分别选号，暂未开放，请使用单式",
                    UNIT_PRICE, List.of());
        }
        JsonNode groups = parseGroups(type);
        Map<String, Integer> standard = standardCounts(groups, List.of(), type.getCode());
        List<RandomDtos.BetGroupSpec> specs = new ArrayList<>();
        for (int gi = 0; gi < groups.size(); gi++) {
            JsonNode group = groups.get(gi);
            String key = group.path("key").asString("MAIN");
            String name = group.path("name").asString(key);
            int min = group.path("min").asInt(1);
            int max = group.path("max").asInt(33);
            int pickMin = group.path("pickMin").asInt(1);
            int pickMax = Math.max(pickMin, group.path("pickMax").asInt(pickMin));
            int drawCount = Math.max(1, group.path("drawCount").asInt(pickMin));
            int poolSize = max - min + 1;
            int one = Math.min(pickMax, Math.max(1, standard.getOrDefault(key, pickMin)));
            // 主号（红球/前区/号码池）上限取「单注上限」与「开奖个数」的较大者：
            // 快乐8 一注只有 10 个号，但复式能选到 20 个（选十复式）。
            // 副号（蓝球/后区）单注只允许 1-2 个，复式上限就是整池（双色球 16、大乐透 12）。
            int limit = Math.min(poolSize, gi == 0 ? Math.max(pickMax, drawCount) : poolSize);
            int low = Math.min(one, limit);
            int def = Math.min(limit, one + (gi == 0 ? 2 : 1));
            specs.add(new RandomDtos.BetGroupSpec(key, name, one, low, limit, Math.max(low, def)));
        }
        return new RandomDtos.BetSpec(true,
                "复式注数 = 各号码组组合数相乘，金额按 2 元/注折算，仅供参考，平台不提供代购",
                UNIT_PRICE, specs);
    }

    /** 复式各组取号个数；单式一律取一注的标准个数 */
    private int[] resolvePicks(RandomDtos.BetSpec spec, RandomDtos.GenerateRequest request, boolean multi) {
        List<RandomDtos.BetGroupSpec> groups = spec.groups();
        int[] picks = new int[groups.size()];
        for (int i = 0; i < groups.size(); i++) {
            RandomDtos.BetGroupSpec group = groups.get(i);
            Integer raw = i == 0 ? request.multiMain() : (i == 1 ? request.multiSub() : null);
            int value = multi ? (raw == null ? group.defaultValue() : raw) : group.standard();
            picks[i] = Math.max(group.min(), Math.min(group.max(), value));
        }
        return picks;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RandomDtos.GenerateResult generate(RandomDtos.GenerateRequest request, Long userId) {
        if (request == null || request.typeCode() == null || request.typeCode().isBlank()) {
            throw new BizException(ResultCode.ILLEGAL_ARGUMENT, "请选择彩种");
        }
        RandomMethod method = RandomMethod.of(request.method());
        int freeQuota = configService.getInt(CFG_FREE_QUOTA, DEFAULT_FREE_QUOTA);
        // 会员分级校验放在最前面：不满足条件直接拒绝，不消耗学习成本更高的数据查询
        if (!checkAndConsumeQuota(method, userId, freeQuota)) {
            throw new BizException(ResultCode.RANDOM_QUOTA_EXCEEDED,
                    "今日会员专享方式的免费次数已用完（" + freeQuota + " 次），开通会员可不限次使用");
        }

        LotLotteryType type = lotteryService.requireType(request.typeCode());
        JsonNode groups = parseGroups(type);
        int historySize = clamp(request.historySize(), 5, MAX_HISTORY, DEFAULT_HISTORY);
        List<LotIssue> history = lotteryService.listDrawnIssues(type.getCode(), historySize);
        int count = clamp(request.count(), 1, MAX_COUNT, 1);
        Random random = request.seed() != null ? new Random(request.seed()) : new Random();

        Stats stats = buildStats(groups, history);
        Map<String, Integer> standardCounts = standardCounts(groups, history, type.getCode());
        RandomDtos.BetSpec betSpec = betSpec(type.getCode());
        boolean multi = MODE_MULTI.equalsIgnoreCase(request.betMode()) && betSpec.supported();
        int[] picks = resolvePicks(betSpec, request, multi);
        List<RandomDtos.SetResult> sets = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            sets.add(generateOne(type, groups, stats, standardCounts, method, request, random, picks, multi));
        }

        int autoCount = 0;
        int manualCount = 0;
        for (LotIssue issue : history) {
            if (issue.getDataSource() != null && issue.getDataSource() == 1) {
                autoCount++;
            } else {
                manualCount++;
            }
        }
        return new RandomDtos.GenerateResult(type.getCode(), type.getName(), historySize, history.size(),
                autoCount, manualCount, buildDataNote(historySize, history),
                sets.get(0).metrics(), sets, toHistoryRows(history, history.size()), DISCLAIMER);
    }

    /**
     * 会员方法配额校验：会员不限次，非会员按天扣减。
     *
     * @return false 表示已达当日上限
     */
    private boolean checkAndConsumeQuota(RandomMethod method, Long userId, int freeQuota) {
        if (!method.isVip()) {
            return true;
        }
        if (!configService.getBoolean(CFG_GRADE_ENABLED, true)) {
            return true;
        }
        if (userId == null) {
            throw new BizException(ResultCode.RANDOM_VIP_REQUIRED,
                    "「" + method.getTitle() + "」为会员专享，登录后可每日免费试用 " + freeQuota + " 次");
        }
        if (userService.isVip(userId)) {
            return true;
        }
        LocalDate today = LocalDate.now();
        usageMapper.ensureRow(userId, today);
        return usageMapper.takeOne(userId, today, freeQuota) > 0;
    }

    @Override
    public RandomDtos.HistorySummary history(String typeCode, int limit) {
        if (typeCode == null || typeCode.isBlank()) {
            throw new BizException(ResultCode.ILLEGAL_ARGUMENT, "请选择彩种");
        }
        LotLotteryType type = lotteryService.requireType(typeCode);
        List<LotIssue> issues = lotteryService.listDrawnIssues(typeCode, Math.max(1, Math.min(limit, 100)));
        int autoCount = 0;
        int manualCount = 0;
        for (LotIssue issue : issues) {
            if (issue.getDataSource() != null && issue.getDataSource() == 1) {
                autoCount++;
            } else {
                manualCount++;
            }
        }
        LotIssue latest = issues.isEmpty() ? null : issues.get(0);
        // total / 来源构成必须是库中全量统计，不能被 limit 截断
        int total = (int) lotteryService.countDrawnIssues(typeCode);
        int autoTotal = (int) lotteryService.countDrawnIssues(typeCode, 1);
        int manualTotal = total - autoTotal;
        return new RandomDtos.HistorySummary(type.getCode(), type.getName(), total,
                autoTotal, manualTotal,
                latest == null ? null : latest.getIssueNo(),
                latest == null || latest.getDrawTime() == null ? null : latest.getDrawTime().toString(),
                toHistoryRows(issues, Math.min(limit, 50)));
    }

    private List<RandomDtos.HistoryRow> toHistoryRows(List<LotIssue> issues, int limit) {
        List<RandomDtos.HistoryRow> rows = new ArrayList<>();
        for (LotIssue issue : issues) {
            if (rows.size() >= limit) {
                break;
            }
            boolean auto = issue.getDataSource() != null && issue.getDataSource() == 1;
            rows.add(new RandomDtos.HistoryRow(issue.getIssueNo(),
                    issue.getDrawTime() == null ? null : issue.getDrawTime().toString(),
                    issue.getRedNumbers(), issue.getBlueNumbers(),
                    issue.getDataSource(), auto ? "自动同步" : "人工录入"));
        }
        return rows;
    }

    private String buildDataNote(int requested, List<LotIssue> history) {
        if (history.isEmpty()) {
            return "平台开奖库中该彩种暂无已开奖记录，本次结果按纯随机生成，不代表任何历史规律，也无法体现冷热/遗漏等策略。";
        }
        int autoCount = 0;
        int manualCount = 0;
        for (LotIssue issue : history) {
            if (issue.getDataSource() != null && issue.getDataSource() == 1) {
                autoCount++;
            } else {
                manualCount++;
            }
        }
        StringBuilder note = new StringBuilder();
        note.append(String.format("本次计算使用平台开奖库最近 %d 期数据（自动同步 %d 期 / 人工录入 %d 期）",
                history.size(), autoCount, manualCount));
        if (requested > history.size()) {
            note.append(String.format("；你已设置参考 %d 期，库中仅 %d 期可用", requested, history.size()));
        }
        note.append("。数据为平台已录入的开奖记录，与官方公告不一致时以官方为准。");
        return note.toString();
    }

    // ================= 单个号码组生成 =================

    private RandomDtos.SetResult generateOne(LotLotteryType type, JsonNode groups, Stats stats,
                                             Map<String, Integer> standardCounts, RandomMethod method,
                                             RandomDtos.GenerateRequest request, Random random,
                                             int[] picks, boolean multi) {
        List<RandomDtos.GroupResult> groupResults = new ArrayList<>();
        List<Integer> mainNumbers = List.of();
        int mainMin = 0;
        int mainMax = 0;
        String reason = method.getDescription();

        for (int gi = 0; gi < groups.size(); gi++) {
            JsonNode group = groups.get(gi);
            String key = group.path("key").asString("MAIN");
            String name = group.path("name").asString(key);
            int min = group.path("min").asInt(1);
            int max = group.path("max").asInt(33);
            int pickMin = group.path("pickMin").asInt(1);
            int pickMax = Math.max(pickMin, group.path("pickMax").asInt(pickMin));
            // 一注的号码个数：优先历史开奖结构，其次内置标准表，最后退化为 pickMin
            int standard = Math.min(pickMax, Math.max(1, standardCounts.getOrDefault(key, pickMin)));
            // 复式按用户选择的个数取号，但不得少于单注个数、不得超出号码池
            int k = multi
                    ? Math.min(max - min + 1, Math.max(standard, gi < picks.length ? picks[gi] : standard))
                    : standard;
            boolean allowRepeat = POSITIONAL_TYPES.contains(type.getCode());
            boolean isMain = gi == 0;

            // 玄学方法按分组区间起卦：每组号码池不同，卦象/牌阵也各算一次
            MysticEngine.Oracle oracle = method.isMystic()
                    ? MysticEngine.cast(method, LocalDate.now(), min, max, random)
                    : null;

            List<Integer> numbers = pick(group, key, min, max, k, allowRepeat, method, stats, random,
                    isMain ? request.sumMin() : null, isMain ? request.sumMax() : null, oracle);

            if (isMain) {
                mainNumbers = numbers;
                mainMin = min;
                mainMax = max;
                reason = oracle != null ? oracle.narrative() : buildReason(method, stats, numbers, key, min, max);
            }
            boolean wide = max >= 10;
            List<String> formatted = numbers.stream()
                    .map(n -> wide ? String.format("%02d", n) : String.valueOf(n))
                    .collect(Collectors.toList());
            groupResults.add(new RandomDtos.GroupResult(key, name, formatted));
        }

        RandomDtos.Metrics metrics = metrics(mainNumbers, mainMin, mainMax);
        RandomDtos.BetInfo bet = buildBet(groupResults, standardCounts, multi);
        List<RandomDtos.ItemPayload> items = buildItems(groupResults, metrics, bet, standardCounts);
        return new RandomDtos.SetResult(method.name(), method.getTitle(), reason, groupResults, metrics, items, bet);
    }

    /**
     * 投注信息：注数 = 各组「从 picked 个号里取 standard 个」的组合数之积，金额 = 注数 × 2 元。
     * 例：双色球 8 红 2 蓝 → C(8,6) × C(2,1) = 28 × 2 = 56 注 → 112 元。
     */
    private RandomDtos.BetInfo buildBet(List<RandomDtos.GroupResult> groups,
                                        Map<String, Integer> standardCounts, boolean multi) {
        long betCount = 1L;
        boolean anyMulti = false;
        List<String> parts = new ArrayList<>();
        StringBuilder shape = new StringBuilder();
        for (RandomDtos.GroupResult group : groups) {
            int picked = group.numbers().size();
            int one = Math.min(picked, Math.max(1, standardCounts.getOrDefault(group.key(), picked)));
            betCount *= comb(picked, one);
            parts.add("C(" + picked + "," + one + ")");
            if (picked > one) {
                anyMulti = true;
            }
            if (shape.length() > 0) {
                shape.append('+');
            }
            shape.append(picked);
        }
        boolean isMulti = multi && anyMulti;
        BigDecimal amount = UNIT_PRICE.multiply(BigDecimal.valueOf(betCount));
        String unit = UNIT_PRICE.stripTrailingZeros().toPlainString();
        String formula = String.join(" × ", parts) + " = " + betCount + " 注 × " + unit + " 元/注";
        String label = (isMulti ? "复式 " : "单式 ") + shape + " · " + betCount + " 注 · ¥"
                + amount.stripTrailingZeros().toPlainString();
        return new RandomDtos.BetInfo(isMulti ? MODE_MULTI : MODE_SINGLE, isMulti ? "复式" : "单式",
                (int) Math.min(betCount, Integer.MAX_VALUE), amount, UNIT_PRICE, formula, label);
    }

    private List<Integer> pick(JsonNode group, String groupKey, int min, int max, int k, boolean allowRepeat,
                               RandomMethod method, Stats stats, Random random, Integer sumMin, Integer sumMax,
                               MysticEngine.Oracle oracle) {
        List<Integer> pool = IntStream.rangeClosed(min, max).boxed().collect(Collectors.toList());
        Map<Integer, Integer> freq = stats.freq.getOrDefault(groupKey, Map.of());
        Map<Integer, Integer> omission = stats.omission.getOrDefault(groupKey, Map.of());

        return switch (method) {
            case PURE_RANDOM -> sample(pool, n -> 1.0, k, allowRepeat, random);
            case HOT -> sample(pool, n -> 1.0 + freq.getOrDefault(n, 0) * 2.0, k, allowRepeat, random);
            case COLD -> sample(pool, n -> 1.0 + omission.getOrDefault(n, 0) * 1.5, k, allowRepeat, random);
            case BALANCED_HOT -> balancedHot(pool, freq, omission, k, allowRepeat, random);
            case OMISSION -> topOmission(pool, omission, k, random);
            case MARKOV -> stats.issues.isEmpty() || stats.issues.size() < 2
                    ? sample(pool, n -> 1.0, k, allowRepeat, random)
                    : sample(pool, n -> 1.0 + stats.coOccurrence
                            .getOrDefault(groupKey, Map.of()).getOrDefault(n, 0) * 2.0,
                    k, allowRepeat, random);
            case BALANCED -> balanced(min, max, k, random);
            case GOLDEN -> golden(min, max, k, random);
            case SUM_TARGET -> {
                // 默认区间按「本注号码个数 × 池均值」估算。不能用历史和值平均数：
                // 快乐8 的历史和值是 20 个开奖号的（约 810），而一注只有 10 个号（约 405），
                // 直接套用会导致拒绝采样永远命中不了，只能退化回纯随机。
                int expected = (int) Math.round((min + max) / 2.0 * k);
                int band = Math.max(5, expected / 5);
                yield sumTarget(pool, k, allowRepeat, random,
                        sumMin != null ? sumMin : expected - band,
                        sumMax != null ? sumMax : expected + band);
            }
            // 玄学：权重来自卦象/干支/牌阵，最终仍由加权随机落号
            case ICHING, FIVE_ELEMENTS, BAZI, ZODIAC, TAROT, ALMANAC -> {
                Map<Integer, Double> weights = oracle == null ? Map.of() : oracle.weights();
                yield sample(pool, n -> weights.getOrDefault(n, 1.0), k, allowRepeat, random);
            }
        };
    }

    /** 冷热均衡：一半取热号，一半取深遗漏号 */
    private List<Integer> balancedHot(List<Integer> pool, Map<Integer, Integer> freq, Map<Integer, Integer> omission,
                                      int k, boolean allowRepeat, Random random) {
        int hotCount = Math.max(1, k / 2);
        List<Integer> picked = new ArrayList<>(sample(pool, n -> 1.0 + freq.getOrDefault(n, 0) * 2.0,
                hotCount, allowRepeat, random));
        int rest = Math.max(0, k - picked.size());
        if (rest > 0) {
            List<Integer> candidates = allowRepeat ? pool
                    : pool.stream().filter(n -> !picked.contains(n)).toList();
            picked.addAll(sample(candidates, n -> 1.0 + omission.getOrDefault(n, 0) * 1.5,
                    Math.min(rest, candidates.size()), allowRepeat, random));
        }
        if (!allowRepeat) {
            Collections.sort(picked);
        }
        return picked;
    }

    /** 权重随机抽样；allowRepeat=false 时保证不重复 */
    private List<Integer> sample(List<Integer> pool, ToDoubleFunction<Integer> weight, int k,
                                 boolean allowRepeat, Random random) {
        List<Integer> available = new ArrayList<>(pool);
        List<Integer> result = new ArrayList<>();
        while (result.size() < k && !available.isEmpty()) {
            double total = available.stream().mapToDouble(weight).sum();
            double target = random.nextDouble() * total;
            double acc = 0;
            int chosen = available.get(0);
            for (Integer candidate : available) {
                acc += weight.applyAsDouble(candidate);
                if (target <= acc) {
                    chosen = candidate;
                    break;
                }
            }
            result.add(chosen);
            if (!allowRepeat) {
                available.remove(Integer.valueOf(chosen));
            }
        }
        // 位号玩法（allowRepeat）保留抽取顺序，如 3D 的 1,3,5 与 5,3,1 是不同结果；其余彩种升序。
        // 池容量不足时宁可少一个号，也不能用 pool 重复填充凑数
        if (!allowRepeat) {
            Collections.sort(result);
        }
        return result;
    }

    /** 遗漏最久的前 k 个，同等遗漏随机打散 */
    private List<Integer> topOmission(List<Integer> pool, Map<Integer, Integer> omission, int k, Random random) {
        List<Integer> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled, random);
        shuffled.sort(Comparator.comparingInt((Integer n) -> omission.getOrDefault(n, 0)).reversed());
        List<Integer> result = new ArrayList<>(shuffled.subList(0, Math.min(k, shuffled.size())));
        Collections.sort(result);
        return result;
    }

    /** 三区覆盖 + 奇偶平衡 */
    private List<Integer> balanced(int min, int max, int k, Random random) {
        List<Integer> best = null;
        int bestScore = Integer.MAX_VALUE;
        for (int attempt = 0; attempt < 60; attempt++) {
            List<Integer> pool = IntStream.rangeClosed(min, max).boxed().collect(Collectors.toList());
            Collections.shuffle(pool, random);
            int span = max - min;
            int lowEnd = min + span / 3;
            int midEnd = min + span * 2 / 3;
            List<Integer> result = new ArrayList<>();
            result.add(pickFromRange(pool, min, lowEnd, random));
            result.add(pickFromRange(pool, lowEnd + 1, midEnd, random));
            result.add(pickFromRange(pool, midEnd + 1, max, random));
            for (Integer candidate : pool) {
                if (result.size() >= k) {
                    break;
                }
                if (!result.contains(candidate)) {
                    result.add(candidate);
                }
            }
            result = result.stream().distinct().limit(k).sorted().collect(Collectors.toList());
            if (result.size() < k) {
                continue;
            }
            long odd = result.stream().filter(n -> n % 2 == 1).count();
            int score = Math.abs((int) (odd * 2 - result.size()));
            if (score < bestScore) {
                bestScore = score;
                best = result;
            }
            if (score <= 1) {
                return result;
            }
        }
        return best == null ? IntStream.rangeClosed(min, Math.min(max, min + k - 1)).boxed().collect(Collectors.toList()) : best;
    }

    private Integer pickFromRange(List<Integer> pool, int from, int to, Random random) {
        List<Integer> candidates = pool.stream().filter(n -> n >= from && n <= to).collect(Collectors.toList());
        if (candidates.isEmpty()) {
            return pool.get(random.nextInt(pool.size()));
        }
        return candidates.get(random.nextInt(candidates.size()));
    }

    /** 黄金分割三段取样 */
    private List<Integer> golden(int min, int max, int k, Random random) {
        int span = max - min;
        int p38 = min + (int) Math.round(span * 0.382);
        int p62 = min + (int) Math.round(span * 0.618);
        List<Integer> pool = IntStream.rangeClosed(min, max).boxed().collect(Collectors.toList());
        Collections.shuffle(pool, random);
        List<Integer> result = new ArrayList<>();
        result.add(pickFromRange(pool, min, p38, random));
        result.add(pickFromRange(pool, p38 + 1, p62, random));
        result.add(pickFromRange(pool, p62 + 1, max, random));
        for (Integer candidate : pool) {
            if (result.size() >= k) {
                break;
            }
            if (!result.contains(candidate)) {
                result.add(candidate);
            }
        }
        return result.stream().distinct().limit(k).sorted().collect(Collectors.toList());
    }

    /** 和值锚定：拒绝采样 */
    private List<Integer> sumTarget(List<Integer> pool, int k, boolean allowRepeat, Random random,
                                    int sumMin, int sumMax) {
        if (sumMin > sumMax) {
            int tmp = sumMin;
            sumMin = sumMax;
            sumMax = tmp;
        }
        List<Integer> fallback = sample(pool, n -> 1.0, k, allowRepeat, random);
        for (int attempt = 0; attempt < 3000; attempt++) {
            List<Integer> candidate = sample(pool, n -> 1.0, k, allowRepeat, random);
            int sum = candidate.stream().mapToInt(Integer::intValue).sum();
            if (sum >= sumMin && sum <= sumMax) {
                return candidate;
            }
        }
        return fallback;
    }

    // ================= 统计与指标 =================

    private record Stats(List<LotIssue> issues,
                         Map<String, Map<Integer, Integer>> freq,
                         Map<String, Map<Integer, Integer>> omission,
                         Map<String, Map<Integer, Integer>> coOccurrence) {
    }

    /**
     * 一注的号码个数：内置标准表 > 历史开奖结构 > pickMin。
     * <p>
     * 历史结构不能优先：开奖结果的号码个数不等于一注的号码个数。快乐8 每期开 20 个号码，
     * 若按历史结构取，会把「一注 10 码」算成 20 码，必须以内置标准表为准。
     */
    private Map<String, Integer> standardCounts(JsonNode groups, List<LotIssue> history, String typeCode) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        LotIssue last = history.isEmpty() ? null : history.get(0);
        for (int gi = 0; gi < groups.size(); gi++) {
            String key = groups.get(gi).path("key").asString("MAIN");
            int pickMin = groups.get(gi).path("pickMin").asInt(1);
            int pickMax = Math.max(pickMin, groups.get(gi).path("pickMax").asInt(pickMin));
            Integer standard = DEFAULT_STANDARD.get(typeCode + ":" + key);
            int count;
            if (standard != null) {
                count = standard;
            } else if (last != null) {
                List<Integer> numbers = parse(gi == 0 ? last.getRedNumbers() : last.getBlueNumbers());
                count = numbers.isEmpty() ? pickMin : numbers.size();
            } else {
                count = pickMin;
            }
            counts.put(key, Math.min(pickMax, Math.max(1, count)));
        }
        return counts;
    }

    private Stats buildStats(JsonNode groups, List<LotIssue> issues) {
        Map<String, Map<Integer, Integer>> freq = new LinkedHashMap<>();
        Map<String, Map<Integer, Integer>> omission = new LinkedHashMap<>();
        Map<String, Map<Integer, Integer>> coOccurrence = new LinkedHashMap<>();

        for (int gi = 0; gi < groups.size(); gi++) {
            String key = groups.get(gi).path("key").asString("MAIN");
            int min = groups.get(gi).path("min").asInt(1);
            int max = groups.get(gi).path("max").asInt(33);
            Map<Integer, Integer> freqMap = new HashMap<>();
            Map<Integer, Integer> omissionMap = new HashMap<>();
            for (int n = min; n <= max; n++) {
                freqMap.put(n, 0);
                omissionMap.put(n, Integer.MAX_VALUE);
            }
            for (int idx = 0; idx < issues.size(); idx++) {
                List<Integer> numbers = parseGroup(issues.get(idx), gi);
                for (Integer n : numbers) {
                    freqMap.merge(n, 1, Integer::sum);
                    if (omissionMap.containsKey(n)) {
                        omissionMap.put(n, Math.min(omissionMap.get(n), idx));
                    }
                }
            }
            // 从未出现的号码遗漏值记为总期数（最冷）
            int total = issues.size();
            omissionMap.replaceAll((n, v) -> v == Integer.MAX_VALUE ? total : v);
            freq.put(key, freqMap);
            omission.put(key, omissionMap);
            coOccurrence.put(key, coOccurrenceOf(issues, gi));
        }
        return new Stats(issues, freq, omission, coOccurrence);
    }

    /**
     * 共现统计：以最近一期该组的号码为种子，统计其余各期中与之有交集的号码出现次数。
     * 必须按组独立统计，否则双色球蓝球（1-16）会被算进红球权重，两个号码池互相污染。
     */
    private Map<Integer, Integer> coOccurrenceOf(List<LotIssue> issues, int groupIndex) {
        Map<Integer, Integer> co = new HashMap<>();
        if (issues.isEmpty()) {
            return co;
        }
        Set<Integer> latest = new HashSet<>(parseGroup(issues.get(0), groupIndex));
        if (latest.isEmpty()) {
            return co;
        }
        for (LotIssue issue : issues) {
            List<Integer> nums = parseGroup(issue, groupIndex);
            if (nums.stream().anyMatch(latest::contains)) {
                for (Integer n : nums) {
                    co.merge(n, 1, Integer::sum);
                }
            }
        }
        return co;
    }

    private List<Integer> parseGroup(LotIssue issue, int groupIndex) {
        return parse(groupIndex == 0 ? issue.getRedNumbers() : issue.getBlueNumbers());
    }

    private RandomDtos.Metrics metrics(List<Integer> numbers, int min, int max) {
        if (numbers.isEmpty()) {
            return new RandomDtos.Metrics(0, 0, "0:0", "0:0");
        }
        int sum = numbers.stream().mapToInt(Integer::intValue).sum();
        int span = Collections.max(numbers) - Collections.min(numbers);
        long odd = numbers.stream().filter(n -> n % 2 == 1).count();
        int mid = (min + max) / 2;
        long big = numbers.stream().filter(n -> n > mid).count();
        return new RandomDtos.Metrics(sum, span,
                odd + ":" + (numbers.size() - odd),
                big + ":" + (numbers.size() - big));
    }

    /**
     * 结构化预测项：itemValue 是提交给命中判定用的值，itemLabel 只放「值之外」的补充信息。
     * 历史上 label 里又写了一遍值（如 value=387-427、label=和值 387-427），前端并列展示就重复了。
     */
    private List<RandomDtos.ItemPayload> buildItems(List<RandomDtos.GroupResult> groups,
                                                    RandomDtos.Metrics metrics, RandomDtos.BetInfo bet,
                                                    Map<String, Integer> standardCounts) {
        List<RandomDtos.ItemPayload> items = new ArrayList<>();
        if (groups.isEmpty()) {
            return items;
        }
        boolean multi = MODE_MULTI.equals(bet.mode());
        RandomDtos.GroupResult main = groups.get(0);
        int mainOne = Math.min(main.numbers().size(),
                Math.max(1, standardCounts.getOrDefault(main.key(), main.numbers().size())));
        // 复式主号池用 RED_MULTI（命中率 ≥50% 算中），单式才是「完全一致」的 RED_SINGLE
        items.add(new RandomDtos.ItemPayload(multi ? "RED_MULTI" : "RED_SINGLE",
                String.join(",", main.numbers()),
                multi ? bet.label() : main.numbers().size() + " 码", true, new BigDecimal("5.0")));

        if (groups.size() > 1) {
            RandomDtos.GroupResult sub = groups.get(1);
            int subOne = Math.min(sub.numbers().size(),
                    Math.max(1, standardCounts.getOrDefault(sub.key(), sub.numbers().size())));
            items.add(new RandomDtos.ItemPayload("BLUE_SINGLE", String.join(",", sub.numbers()),
                    sub.numbers().size() > subOne ? "复式 " + sub.numbers().size() + " 码" : "",
                    false, new BigDecimal("3.0")));
        }

        // 复式不能直接拿池和值当预测值：12 个号的和值不是任何一注的和值，必然虚高。
        // 折算成「一注 standard 个号」的期望值，形态项才和开奖口径对得上。
        RandomDtos.Metrics shape = multi ? expectedMetrics(main, mainOne) : metrics;
        int half = Math.max(10, shape.sum() / 20);
        int sumLow = Math.max(0, shape.sum() - half);
        int sumHigh = shape.sum() + half;
        items.add(new RandomDtos.ItemPayload("SUM_RANGE", sumLow + "-" + sumHigh,
                multi ? "复式均值" : "", false, new BigDecimal("2.0")));
        // 复式池的跨度不是任何一注的跨度，单值必然判错，索性不下发；奇偶比按池内比例折算，仍是有效形态判断
        if (!multi) {
            items.add(new RandomDtos.ItemPayload("SPAN", String.valueOf(shape.span()),
                    "", false, new BigDecimal("1.5")));
        }
        items.add(new RandomDtos.ItemPayload("ODD_EVEN", shape.oddEven(),
                "", false, new BigDecimal("1.0")));
        return items;
    }

    /** 复式池折算成「一注 standard 个号」的期望形态：和值与奇偶按比例缩放，跨度取池跨度 */
    private RandomDtos.Metrics expectedMetrics(RandomDtos.GroupResult main, int standard) {
        List<Integer> numbers = new ArrayList<>();
        for (String text : main.numbers()) {
            try {
                numbers.add(Integer.parseInt(text.trim()));
            } catch (NumberFormatException ignored) {
                // 忽略脏数据
            }
        }
        if (numbers.isEmpty()) {
            return new RandomDtos.Metrics(0, 0, "0:0", "0:0");
        }
        int picked = numbers.size();
        int poolSpan = Collections.max(numbers) - Collections.min(numbers);
        if (standard >= picked) {
            long oddCount = numbers.stream().filter(n -> n % 2 == 1).count();
            return new RandomDtos.Metrics(numbers.stream().mapToInt(Integer::intValue).sum(), poolSpan,
                    oddCount + ":" + (picked - oddCount), "");
        }
        int sum = (int) Math.round(numbers.stream().mapToInt(Integer::intValue).sum() * (double) standard / picked);
        long oddRaw = Math.round(numbers.stream().filter(n -> n % 2 == 1).count() * (double) standard / picked);
        int odd = (int) Math.max(0, Math.min(standard, oddRaw));
        return new RandomDtos.Metrics(sum, poolSpan, odd + ":" + (standard - odd), "");
    }

    /** 组合数 C(n,k)，复式注数用 */
    private long comb(int n, int k) {
        if (n <= 0 || k < 0 || k > n) {
            return 1L;
        }
        int r = Math.min(k, n - k);
        long value = 1L;
        for (int i = 0; i < r; i++) {
            value = value * (n - i) / (i + 1);
        }
        return value;
    }

    private String buildReason(RandomMethod method, Stats stats, List<Integer> numbers,
                               String groupKey, int min, int max) {
        if (stats.issues.isEmpty()) {
            return method.getDescription() + "（暂无历史开奖数据，按基础策略生成）";
        }
        Map<Integer, Integer> freq = stats.freq.getOrDefault(groupKey, Map.of());
        Map<Integer, Integer> omission = stats.omission.getOrDefault(groupKey, Map.of());
        String picked = numbers.stream().map(String::valueOf).collect(Collectors.joining("、"));
        return switch (method) {
            case HOT -> String.format("%s（近 %d 期统计：%s）", method.getDescription(), stats.issues.size(),
                    numbers.stream().map(n -> n + "出现" + freq.getOrDefault(n, 0) + "次")
                            .collect(Collectors.joining("，")));
            case COLD -> String.format("%s（%s 的遗漏期数分别为 %s）", method.getDescription(),
                    picked, numbers.stream().map(n -> String.valueOf(omission.getOrDefault(n, 0)))
                            .collect(Collectors.joining("、")));
            case OMISSION -> String.format("按遗漏期数排序取最冷的 %d 个号码：%s（近 %d 期未开出的号码优先）",
                    numbers.size(), picked, stats.issues.size());
            case MARKOV -> String.format("%s（基于最近一期 %s 的历史共现次数加权）", method.getDescription(),
                    stats.issues.get(0).getIssueNo());
            case SUM_TARGET -> String.format("%s，本组和值 %d，跨 %d-%d", method.getDescription(),
                    numbers.stream().mapToInt(Integer::intValue).sum(), min, max);
            case BALANCED -> "强制低/中/高三区各覆盖至少 1 个，并优先生成奇偶接近平衡的组合";
            case BALANCED_HOT -> String.format("取 %d 个近期热号 + %d 个深遗漏号，兼顾惯性与回补",
                    Math.max(1, numbers.size() / 2), Math.max(0, numbers.size() - Math.max(1, numbers.size() / 2)));
            case GOLDEN -> "按 0.382 / 0.618 黄金分割点把号码池切成三段，各段取样后补足";
            default -> method.getDescription();
        };
    }

    // ================= 工具 =================

    private JsonNode parseGroups(LotLotteryType type) {
        if (type.getPlayConfig() == null || type.getPlayConfig().isBlank()) {
            throw new BizException(ResultCode.ILLEGAL_ARGUMENT, "该彩种未配置玩法");
        }
        JsonNode cfg = objectMapper.readTree(type.getPlayConfig());
        JsonNode groups = cfg.get("groups");
        if (groups == null || !groups.isArray() || groups.isEmpty()) {
            throw new BizException(ResultCode.ILLEGAL_ARGUMENT, "该彩种玩法配置缺少号码分组");
        }
        return groups;
    }

    private List<Integer> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<Integer> list = new ArrayList<>();
        for (String part : raw.split("[,\\s]+")) {
            if (part.isBlank()) {
                continue;
            }
            try {
                list.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ignored) {
                // 忽略脏数据
            }
        }
        return list;
    }

    private int clamp(Integer value, int min, int max, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return Math.max(min, Math.min(max, value));
    }
}
