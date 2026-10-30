package com.ttdemo.modules.score.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ttdemo.modules.creator.entity.CreCreator;
import com.ttdemo.modules.creator.mapper.CreCreatorMapper;
import com.ttdemo.modules.prediction.entity.PrdPrediction;
import com.ttdemo.modules.prediction.mapper.PrdPredictionMapper;
import com.ttdemo.modules.score.entity.ScoreCreatorAccuracy;
import com.ttdemo.modules.score.entity.ScoreRankSnapshot;
import com.ttdemo.modules.score.mapper.ScoreCreatorAccuracyMapper;
import com.ttdemo.modules.score.mapper.ScoreRankSnapshotMapper;
import com.ttdemo.modules.score.service.ScoreService;
import com.ttdemo.modules.system.service.SysConfigService;
import com.ttdemo.modules.user.entity.SysUser;
import com.ttdemo.modules.user.mapper.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ScoreServiceImpl implements ScoreService {

    private static final Logger log = LoggerFactory.getLogger(ScoreServiceImpl.class);
    private static final double Z = 1.96;

    private final PrdPredictionMapper predictionMapper;
    private final ScoreCreatorAccuracyMapper accuracyMapper;
    private final ScoreRankSnapshotMapper rankMapper;
    private final CreCreatorMapper creatorMapper;
    private final SysUserMapper userMapper;
    private final SysConfigService configService;

    public ScoreServiceImpl(PrdPredictionMapper predictionMapper, ScoreCreatorAccuracyMapper accuracyMapper,
                            ScoreRankSnapshotMapper rankMapper, CreCreatorMapper creatorMapper,
                            SysUserMapper userMapper, SysConfigService configService) {
        this.predictionMapper = predictionMapper;
        this.accuracyMapper = accuracyMapper;
        this.rankMapper = rankMapper;
        this.creatorMapper = creatorMapper;
        this.userMapper = userMapper;
        this.configService = configService;
    }

    @Override
    @Transactional
    public void recalculate(Long creatorId) {
        int windowDays = configService.getInt("score.window.days", 90);
        int maxCount = configService.getInt("score.window.max", 100);
        LocalDateTime from = LocalDateTime.now().minusDays(windowDays);

        List<PrdPrediction> settled = predictionMapper.selectList(new LambdaQueryWrapper<PrdPrediction>()
                .eq(PrdPrediction::getCreatorId, creatorId)
                .eq(PrdPrediction::getStatus, PrdPrediction.STATUS_SETTLED)
                .ge(PrdPrediction::getSettleTime, from)
                .orderByDesc(PrdPrediction::getSettleTime)
                .last("limit " + maxCount));

        saveAccuracy(creatorId, ScoreCreatorAccuracy.TYPE_ALL, settled);

        Map<String, List<PrdPrediction>> byType = settled.stream()
                .collect(Collectors.groupingBy(PrdPrediction::getTypeCode));
        for (Map.Entry<String, List<PrdPrediction>> entry : byType.entrySet()) {
            saveAccuracy(creatorId, entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void rebuildAll() {
        List<CreCreator> creators = creatorMapper.selectList(
                new LambdaQueryWrapper<CreCreator>().eq(CreCreator::getStatus, CreCreator.STATUS_PASS));
        for (CreCreator creator : creators) {
            try {
                recalculate(creator.getId());
            } catch (Exception e) {
                log.warn("重算创作者 {} 评分失败", creator.getId(), e);
            }
        }
        log.info("评分全量重算完成，共 {} 位创作者", creators.size());
    }

    @Override
    public ScoreCreatorAccuracy get(Long creatorId) {
        return accuracyMapper.selectOne(new LambdaQueryWrapper<ScoreCreatorAccuracy>()
                .eq(ScoreCreatorAccuracy::getCreatorId, creatorId)
                .eq(ScoreCreatorAccuracy::getTypeCode, ScoreCreatorAccuracy.TYPE_ALL));
    }

    @Override
    public BigDecimal calculateScore(List<HitSample> samples) {
        int n = samples.size();
        if (n == 0) {
            return configService.getDecimal("score.cold.start", "30");
        }
        double weightSum = 0;
        double hitSum = 0;
        double decayWeightSum = 0;
        double decayHitSum = 0;
        for (HitSample s : samples) {
            double w = s.free() ? 0.5 : 1.0;
            weightSum += w;
            if (s.hit()) {
                hitSum += w;
            }
            double wt = Math.pow(0.9, s.ageDays() / 30.0);
            decayWeightSum += wt * w;
            if (s.hit()) {
                decayHitSum += wt * w;
            }
        }
        double p = weightSum == 0 ? 0 : hitSum / weightSum;
        double pw = decayWeightSum == 0 ? 0 : decayHitSum / decayWeightSum;

        double lb = wilsonLower(p, n);
        double score = 100 * (0.7 * lb + 0.3 * pw);

        if (n < 5) {
            return configService.getDecimal("score.cold.start", "30");
        }
        if (n < 10) {
            double cap = configService.getDecimal("score.cold.cap", "60").doubleValue();
            score = Math.min(score, cap);
        }
        return BigDecimal.valueOf(Math.max(0, Math.min(100, score))).setScale(2, RoundingMode.HALF_UP);
    }

    private double wilsonLower(double p, int n) {
        if (n == 0) {
            return 0;
        }
        double z2 = Z * Z;
        double denominator = 1 + z2 / n;
        double centre = p + z2 / (2.0 * n);
        double margin = Z * Math.sqrt(p * (1 - p) / n + z2 / (4.0 * n * n));
        return Math.max(0, (centre - margin) / denominator);
    }

    private void saveAccuracy(Long creatorId, String typeCode, List<PrdPrediction> predictions) {
        List<HitSample> samples = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (PrdPrediction p : predictions) {
            boolean hit = p.getIsHit() != null && p.getIsHit() == 1;
            int ageDays = p.getSettleTime() == null ? 0
                    : (int) ChronoUnit.DAYS.between(p.getSettleTime(), now);
            samples.add(new HitSample(hit, p.isFree(), Math.max(0, ageDays)));
        }
        BigDecimal score = calculateScore(samples);

        int n = samples.size();
        long hits = samples.stream().filter(HitSample::hit).count();
        BigDecimal rawRate = n == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(hits).divide(BigDecimal.valueOf(n), 4, RoundingMode.HALF_UP);
        double weightSum = samples.stream().mapToDouble(s -> s.free() ? 0.5 : 1.0).sum();
        double hitWeight = samples.stream().mapToDouble(s -> s.hit() ? (s.free() ? 0.5 : 1.0) : 0).sum();
        BigDecimal weighted = weightSum == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(hitWeight / weightSum).setScale(4, RoundingMode.HALF_UP);
        double lb = n == 0 ? 0 : wilsonLower(rawRate.doubleValue(), n);

        // 连红：predictions 已按 settle_time desc 排序
        int streak = 0;
        for (PrdPrediction p : predictions) {
            if (p.getIsHit() != null && p.getIsHit() == 1) {
                streak++;
            } else {
                break;
            }
        }
        int recent10Hit = 0;
        int recent10 = Math.min(10, predictions.size());
        for (int i = 0; i < recent10; i++) {
            if (predictions.get(i).getIsHit() != null && predictions.get(i).getIsHit() == 1) {
                recent10Hit++;
            }
        }
        BigDecimal recent10Rate = recent10 == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(recent10Hit).divide(BigDecimal.valueOf(recent10), 4, RoundingMode.HALF_UP);

        BigDecimal coef = configService.getDecimal("price.coef.intercept", "0.6")
                .add(configService.getDecimal("price.coef.slope", "0.008").multiply(score))
                .setScale(4, RoundingMode.HALF_UP);

        ScoreCreatorAccuracy existing = accuracyMapper.selectOne(new LambdaQueryWrapper<ScoreCreatorAccuracy>()
                .eq(ScoreCreatorAccuracy::getCreatorId, creatorId)
                .eq(ScoreCreatorAccuracy::getTypeCode, typeCode));
        ScoreCreatorAccuracy entity = existing == null ? new ScoreCreatorAccuracy() : existing;
        entity.setCreatorId(creatorId);
        entity.setTypeCode(typeCode);
        entity.setSampleSize(n);
        entity.setHitTimes((int) hits);
        entity.setRawHitRate(rawRate);
        entity.setWeightedHitRate(weighted);
        entity.setWilsonLower(BigDecimal.valueOf(lb).setScale(4, RoundingMode.HALF_UP));
        entity.setAccuracyScore(score);
        entity.setCurrentHitStreak(streak);
        entity.setMaxHitStreak(existing == null ? streak : Math.max(streak, existing.getMaxHitStreak() == null ? 0 : existing.getMaxHitStreak()));
        entity.setRecent10Rate(recent10Rate);
        entity.setPriceCoefficient(coef);
        entity.setCalcVersion("v1");
        if (existing == null) {
            entity.setTotalView(0L);
            entity.setTotalBuyer(0L);
            entity.setTotalIncome(0L);
            accuracyMapper.insert(entity);
        } else {
            accuracyMapper.updateById(entity);
        }
    }

    // ================= 榜单 =================

    @Override
    @Transactional
    public Map<String, Object> leaderboard(String rankType, String typeCode, int limit) {
        String type = rankType == null || rankType.isBlank() ? "ACCURACY" : rankType;
        String code = typeCode == null || typeCode.isBlank() ? ScoreCreatorAccuracy.TYPE_ALL : typeCode;
        LocalDate today = LocalDate.now();

        List<ScoreRankSnapshot> snapshots = rankMapper.selectList(new LambdaQueryWrapper<ScoreRankSnapshot>()
                .eq(ScoreRankSnapshot::getRankType, type)
                .eq(ScoreRankSnapshot::getTypeCode, code)
                .eq(ScoreRankSnapshot::getSnapshotDate, today)
                .orderByAsc(ScoreRankSnapshot::getRankNo)
                .last("limit " + Math.max(1, Math.min(limit, 100))));
        if (snapshots.isEmpty()) {
            rebuildRank(type, code);
            snapshots = rankMapper.selectList(new LambdaQueryWrapper<ScoreRankSnapshot>()
                    .eq(ScoreRankSnapshot::getRankType, type)
                    .eq(ScoreRankSnapshot::getTypeCode, code)
                    .eq(ScoreRankSnapshot::getSnapshotDate, today)
                    .orderByAsc(ScoreRankSnapshot::getRankNo)
                    .last("limit " + Math.max(1, Math.min(limit, 100))));
        }

        List<Map<String, Object>> list = new ArrayList<>();
        for (ScoreRankSnapshot s : snapshots) {
            CreCreator creator = creatorMapper.selectById(s.getCreatorId());
            SysUser user = creator == null ? null : userMapper.selectById(creator.getUserId());
            ScoreCreatorAccuracy acc = accuracyMapper.selectOne(new LambdaQueryWrapper<ScoreCreatorAccuracy>()
                    .eq(ScoreCreatorAccuracy::getCreatorId, s.getCreatorId())
                    .eq(ScoreCreatorAccuracy::getTypeCode, ScoreCreatorAccuracy.TYPE_ALL));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("rankNo", s.getRankNo());
            row.put("creatorId", s.getCreatorId());
            row.put("penName", creator == null ? null : creator.getPenName());
            row.put("avatar", user == null ? null : user.getAvatar());
            row.put("level", creator == null ? 1 : creator.getCreatorLevel());
            row.put("metricValue", s.getMetricValue());
            row.put("metricText", metricText(type, s.getMetricValue()));
            row.put("sampleSize", acc == null ? 0 : acc.getSampleSize());
            row.put("currentHitStreak", acc == null || acc.getCurrentHitStreak() == null ? 0 : acc.getCurrentHitStreak());
            row.put("totalBuyer", acc == null || acc.getTotalBuyer() == null ? 0 : acc.getTotalBuyer());
            list.add(row);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rankType", type);
        result.put("typeCode", code);
        result.put("snapshotDate", today.toString());
        result.put("calcNote", "统计口径：近90天已结算预测，综合命中率≥" +
                configService.getDecimal("hit.threshold", "0.6").stripTrailingZeros().toPlainString()
                + " 记为命中，样本<10期不上榜");
        result.put("list", list);
        return result;
    }

    @Override
    @Transactional
    public int rebuildRank(String rankType, String typeCode) {
        String type = rankType == null || rankType.isBlank() ? "ACCURACY" : rankType;
        String code = typeCode == null || typeCode.isBlank() ? ScoreCreatorAccuracy.TYPE_ALL : typeCode;
        LocalDate today = LocalDate.now();

        List<ScoreCreatorAccuracy> candidates = accuracyMapper.selectList(
                new LambdaQueryWrapper<ScoreCreatorAccuracy>().eq(ScoreCreatorAccuracy::getTypeCode, code));

        List<ScoreCreatorAccuracy> filtered = new ArrayList<>();
        for (ScoreCreatorAccuracy acc : candidates) {
            CreCreator creator = creatorMapper.selectById(acc.getCreatorId());
            if (creator == null || !creator.isApproved()) {
                continue;
            }
            switch (type) {
                case "ACCURACY" -> {
                    if (acc.getSampleSize() != null && acc.getSampleSize() >= 10) {
                        filtered.add(acc);
                    }
                }
                case "HIT_STREAK" -> {
                    if (acc.getCurrentHitStreak() != null && acc.getCurrentHitStreak() >= 3) {
                        filtered.add(acc);
                    }
                }
                case "INCOME" -> {
                    if (acc.getTotalBuyer() != null && acc.getTotalBuyer() >= 10) {
                        filtered.add(acc);
                    }
                }
                case "NEWCOMER" -> {
                    boolean fresh = creator.getCertifiedAt() != null
                            && creator.getCertifiedAt().isAfter(LocalDateTime.now().minusDays(30));
                    if (fresh && acc.getSampleSize() != null && acc.getSampleSize() >= 5) {
                        filtered.add(acc);
                    }
                }
                default -> filtered.add(acc);
            }
        }

        Comparator<ScoreCreatorAccuracy> comparator = switch (type) {
            case "HIT_STREAK" -> Comparator.comparing((ScoreCreatorAccuracy a) ->
                    a.getCurrentHitStreak() == null ? 0 : a.getCurrentHitStreak()).reversed();
            case "INCOME" -> Comparator.comparing((ScoreCreatorAccuracy a) ->
                    a.getTotalBuyer() == null ? 0L : a.getTotalBuyer()).reversed();
            default -> Comparator.comparing((ScoreCreatorAccuracy a) ->
                    a.getAccuracyScore() == null ? BigDecimal.ZERO : a.getAccuracyScore()).reversed();
        };
        filtered.sort(comparator);

        rankMapper.delete(new LambdaQueryWrapper<ScoreRankSnapshot>()
                .eq(ScoreRankSnapshot::getRankType, type)
                .eq(ScoreRankSnapshot::getTypeCode, code)
                .eq(ScoreRankSnapshot::getSnapshotDate, today));

        int rankNo = 0;
        for (ScoreCreatorAccuracy acc : filtered) {
            rankNo++;
            if (rankNo > 100) {
                break;
            }
            ScoreRankSnapshot snapshot = new ScoreRankSnapshot();
            snapshot.setRankType(type);
            snapshot.setTypeCode(code);
            snapshot.setPeriod("ALL");
            snapshot.setSnapshotDate(today);
            snapshot.setRankNo(rankNo);
            snapshot.setCreatorId(acc.getCreatorId());
            snapshot.setMetricValue(switch (type) {
                case "HIT_STREAK" -> BigDecimal.valueOf(acc.getCurrentHitStreak() == null ? 0 : acc.getCurrentHitStreak());
                case "INCOME" -> BigDecimal.valueOf(acc.getTotalBuyer() == null ? 0 : acc.getTotalBuyer());
                default -> acc.getAccuracyScore() == null ? BigDecimal.ZERO : acc.getAccuracyScore();
            });
            rankMapper.insert(snapshot);
        }
        log.info("榜单 {} / {} 生成 {} 条", type, code, rankNo);
        return rankNo;
    }

    private String metricText(String type, BigDecimal value) {
        if (value == null) {
            return "-";
        }
        return switch (type) {
            case "HIT_STREAK" -> value.stripTrailingZeros().toPlainString() + " 连红";
            case "INCOME" -> value.stripTrailingZeros().toPlainString() + " 人解锁";
            default -> value.setScale(1, RoundingMode.HALF_UP) + " 分";
        };
    }
}
