package com.ttdemo.modules.prediction.engine;

import com.ttdemo.modules.prediction.entity.PrdPredictionItem;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 命中判定引擎：所有 item_type 的判定算法集中在此，结算与申诉重算共用。
 */
@Component
public class HitJudge {

    private final ObjectMapper objectMapper;
    private final com.ttdemo.modules.lottery.service.LotteryService lotteryService;

    public HitJudge(ObjectMapper objectMapper,
                    com.ttdemo.modules.lottery.service.LotteryService lotteryService) {
        this.objectMapper = objectMapper;
        this.lotteryService = lotteryService;
    }

    public record ItemResult(Long itemId, int hit, BigDecimal ratio, String detail) {
    }

    public record JudgeResult(int itemCount, int hitCount, BigDecimal hitRate,
                              List<ItemResult> items) {
    }

    /**
     * @param items    预测项
     * @param reds     开奖主号码（红球/前区/位号）
     * @param blues    开奖副号码（蓝球/后区）
     * @param typeCode 彩种编码，用于取号码池中值判断大小
     */
    public JudgeResult judge(List<PrdPredictionItem> items, List<Integer> reds, List<Integer> blues,
                            String typeCode) {
        List<ItemResult> results = new ArrayList<>();
        BigDecimal weightedHit = BigDecimal.ZERO;
        BigDecimal weightSum = BigDecimal.ZERO;
        int hitCount = 0;

        int sum = reds.stream().mapToInt(Integer::intValue).sum();
        int span = reds.isEmpty() ? 0 : reds.stream().mapToInt(Integer::intValue).max().getAsInt()
                - reds.stream().mapToInt(Integer::intValue).min().getAsInt();
        String oddEven = ratio(reds, n -> n % 2 == 1);
        int middle = poolMid(typeCode, reds);
        String bigSmall = ratio(reds, n -> n > middle);

        for (PrdPredictionItem item : items) {
            ItemResult result;
            try {
                result = judgeOne(item, reds, blues, sum, span, oddEven, bigSmall);
            } catch (Exception e) {
                result = new ItemResult(item.getId(), 0, BigDecimal.ZERO,
                        "{\"error\":\"判定异常\"}");
            }
            results.add(result);
            BigDecimal weight = item.getWeight() == null ? BigDecimal.ONE : item.getWeight();
            weightSum = weightSum.add(weight);
            if (result.hit() == 1) {
                hitCount++;
                weightedHit = weightedHit.add(weight);
            }
        }

        BigDecimal rate = weightSum.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO
                : weightedHit.divide(weightSum, 4, RoundingMode.HALF_UP);
        return new JudgeResult(items.size(), hitCount, rate, results);
    }

    private ItemResult judgeOne(PrdPredictionItem item, List<Integer> reds, List<Integer> blues,
                               int sum, int span, String oddEven, String bigSmall) {
        String type = item.getItemType();
        List<Integer> predicted = parseNumbers(item.getItemValue());
        Set<Integer> redSet = new LinkedHashSet<>(reds);
        Set<Integer> blueSet = new LinkedHashSet<>(blues);
        Set<Integer> allDraw = new LinkedHashSet<>(reds);
        allDraw.addAll(blues);

        switch (type) {
            case "RED_SINGLE": {
                boolean hit = predicted.size() == reds.size()
                        && new LinkedHashSet<>(predicted).equals(redSet);
                return new ItemResult(item.getId(), hit ? 1 : 0, hit ? BigDecimal.ONE : BigDecimal.ZERO,
                        "{\"predicted\":\"" + join(predicted) + "\",\"actual\":\"" + join(reds) + "\"}");
            }
            case "RED_MULTI": {
                int inter = intersect(predicted, redSet);
                BigDecimal r = reds.isEmpty() ? BigDecimal.ZERO
                        : BigDecimal.valueOf(inter).divide(BigDecimal.valueOf(reds.size()), 4, RoundingMode.HALF_UP);
                int hit = r.compareTo(BigDecimal.valueOf(0.5)) >= 0 ? 1 : 0;
                return new ItemResult(item.getId(), hit, r,
                        "{\"intersect\":" + inter + ",\"drawSize\":" + reds.size()
                                + ",\"ratio\":" + r + ",\"hitNumbers\":\"" + join(intersectList(predicted, redSet)) + "\"}");
            }
            case "DAN": {
                int inter = intersect(predicted, redSet);
                BigDecimal r = predicted.isEmpty() ? BigDecimal.ZERO
                        : BigDecimal.valueOf(inter).divide(BigDecimal.valueOf(predicted.size()), 4, RoundingMode.HALF_UP);
                int hit = r.compareTo(BigDecimal.valueOf(0.5)) >= 0 ? 1 : 0;
                return new ItemResult(item.getId(), hit, r,
                        "{\"intersect\":" + inter + ",\"predictedSize\":" + predicted.size()
                                + ",\"ratio\":" + r + ",\"hitNumbers\":\"" + join(intersectList(predicted, redSet)) + "\"}");
            }
            case "KILL": {
                List<Integer> common = intersectList(predicted, allDraw);
                boolean hit = common.isEmpty();
                return new ItemResult(item.getId(), hit ? 1 : 0, hit ? BigDecimal.ONE : BigDecimal.ZERO,
                        "{\"killed\":\"" + join(predicted) + "\",\"overlap\":\"" + join(common) + "\"}");
            }
            case "BLUE_SINGLE": {
                // 蓝球/后区可能是一个池（复式 8+2 的 2 个蓝球、大乐透后区 3 个号）：
                // 开奖副号全部落在预测池内即算中；单式时池里只有一个号，等价于「相等」。
                boolean hit = !predicted.isEmpty() && !blueSet.isEmpty()
                        && blueSet.stream().allMatch(predicted::contains);
                return new ItemResult(item.getId(), hit ? 1 : 0, hit ? BigDecimal.ONE : BigDecimal.ZERO,
                        "{\"predicted\":\"" + join(predicted) + "\",\"actual\":\"" + join(blues) + "\"}");
            }
            case "SUM_RANGE": {
                int[] range = parseRange(item.getItemValue());
                boolean hit = sum >= range[0] && sum <= range[1];
                return new ItemResult(item.getId(), hit ? 1 : 0, hit ? BigDecimal.ONE : BigDecimal.ZERO,
                        "{\"range\":\"" + range[0] + "-" + range[1] + "\",\"actual\":" + sum + "}");
            }
            case "SPAN": {
                int expect = predicted.isEmpty() ? -1 : predicted.get(0);
                boolean hit = span == expect;
                return new ItemResult(item.getId(), hit ? 1 : 0, hit ? BigDecimal.ONE : BigDecimal.ZERO,
                        "{\"expect\":" + expect + ",\"actual\":" + span + "}");
            }
            case "ODD_EVEN": {
                boolean hit = oddEven.equals(item.getItemValue().trim());
                return new ItemResult(item.getId(), hit ? 1 : 0, hit ? BigDecimal.ONE : BigDecimal.ZERO,
                        "{\"expect\":\"" + item.getItemValue().trim() + "\",\"actual\":\"" + oddEven + "\"}");
            }
            case "BIG_SMALL": {
                boolean hit = bigSmall.equals(item.getItemValue().trim());
                return new ItemResult(item.getId(), hit ? 1 : 0, hit ? BigDecimal.ONE : BigDecimal.ZERO,
                        "{\"expect\":\"" + item.getItemValue().trim() + "\",\"actual\":\"" + bigSmall + "\"}");
            }
            case "FORM_COMBO": {
                JsonNode node = objectMapper.readTree(item.getItemValue());
                boolean oe = !node.has("oddEven") || oddEven.equals(node.get("oddEven").asString());
                boolean bs = !node.has("bigSmall") || bigSmall.equals(node.get("bigSmall").asString());
                boolean hit = oe && bs;
                return new ItemResult(item.getId(), hit ? 1 : 0, hit ? BigDecimal.ONE : BigDecimal.ZERO,
                        "{\"oddEven\":{\"ok\":" + oe + ",\"actual\":\"" + oddEven + "\"},"
                                + "\"bigSmall\":{\"ok\":" + bs + ",\"actual\":\"" + bigSmall + "\"}}");
            }
            case "AI_SCORE": {
                List<Integer> top = topFromAiScore(item.getItemValue(), Math.max(1, reds.size()));
                int inter = intersect(top, redSet);
                BigDecimal r = reds.isEmpty() ? BigDecimal.ZERO
                        : BigDecimal.valueOf(inter).divide(BigDecimal.valueOf(reds.size()), 4, RoundingMode.HALF_UP);
                int hit = r.compareTo(BigDecimal.valueOf(0.5)) >= 0 ? 1 : 0;
                return new ItemResult(item.getId(), hit, r,
                        "{\"top\":\"" + join(top) + "\",\"intersect\":" + inter + ",\"ratio\":" + r + "}");
            }
            default:
                return new ItemResult(item.getId(), 0, BigDecimal.ZERO,
                        "{\"error\":\"未知预测项类型\"}");
        }
    }

    private List<Integer> topFromAiScore(String json, int topN) {
        JsonNode node = objectMapper.readTree(json);
        JsonNode numbers = node.get("numbers");
        if (numbers == null || !numbers.isArray()) {
            return List.of();
        }
        List<JsonNode> list = new ArrayList<>();
        numbers.forEach(list::add);
        list.sort(Comparator.comparingDouble(n -> -n.path("p").asDouble(0)));
        return list.stream().limit(topN)
                .map(n -> {
                    try {
                        return Integer.parseInt(n.path("n").asString().trim());
                    } catch (NumberFormatException e) {
                        return -1;
                    }
                })
                .filter(v -> v >= 0)
                .collect(Collectors.toList());
    }

    /**
     * 大/小的分界值：按彩种号码池的中值（双色球红球 1-33 → 17）。
     * 不能用本期开奖号码的极值中值——那样每期基准都不一样，同一个「3:3」在不同期判定结果会漂移，
     * 也会与随机预测页用号码池范围算出的口径对不上。
     */
    private int poolMid(String typeCode, List<Integer> reds) {
        if (typeCode != null && !typeCode.isBlank()) {
            try {
                var type = lotteryService.requireType(typeCode);
                JsonNode cfg = objectMapper.readTree(type.getPlayConfig());
                JsonNode groups = cfg.get("groups");
                if (groups != null && groups.isArray() && !groups.isEmpty()) {
                    int lo = groups.get(0).path("min").asInt(0);
                    int hi = groups.get(0).path("max").asInt(0);
                    if (hi > lo) {
                        return (lo + hi) / 2;
                    }
                }
            } catch (Exception e) {
                // 配置异常时退回开奖号码极值，保证判定不中断
            }
        }
        return mid(reds);
    }

    private int mid(List<Integer> reds) {
        if (reds.isEmpty()) {
            return 0;
        }
        int min = reds.stream().mapToInt(Integer::intValue).min().getAsInt();
        int max = reds.stream().mapToInt(Integer::intValue).max().getAsInt();
        return (min + max) / 2;
    }

    private String ratio(List<Integer> numbers, java.util.function.IntPredicate predicate) {
        int yes = 0;
        for (Integer n : numbers) {
            if (predicate.test(n)) {
                yes++;
            }
        }
        return yes + ":" + (numbers.size() - yes);
    }

    private int intersect(List<Integer> predicted, Set<Integer> draw) {
        return (int) predicted.stream().distinct().filter(draw::contains).count();
    }

    private List<Integer> intersectList(List<Integer> predicted, Set<Integer> draw) {
        return predicted.stream().distinct().filter(draw::contains).collect(Collectors.toList());
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
                        return -1;
                    }
                })
                .filter(v -> v >= 0)
                .collect(Collectors.toList());
    }

    private int[] parseRange(String raw) {
        String[] parts = raw.split("[-~,]");
        if (parts.length < 2) {
            int v = Integer.parseInt(raw.replaceAll("[^0-9]", ""));
            return new int[]{v, v};
        }
        return new int[]{Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())};
    }

    private String join(List<Integer> numbers) {
        return numbers.stream().map(String::valueOf).collect(Collectors.joining(","));
    }
}
