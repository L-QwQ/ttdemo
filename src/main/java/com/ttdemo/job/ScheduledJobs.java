package com.ttdemo.job;

import com.ttdemo.modules.point.service.PointService;
import com.ttdemo.modules.prediction.service.PredictionService;
import com.ttdemo.modules.score.service.ScoreService;
import com.ttdemo.modules.lottery.sync.DrawSyncService;
import com.ttdemo.modules.settlement.service.SettlementService;
import com.ttdemo.modules.system.service.SysConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时任务（替代 Xxl-Job，单机部署足够）。
 * 每个任务都有状态机幂等 + 台账唯一键兜底，可安全重跑。
 */
@Component
public class ScheduledJobs {

    private static final Logger log = LoggerFactory.getLogger(ScheduledJobs.class);

    private final PredictionService predictionService;
    private final SettlementService settlementService;
    private final ScoreService scoreService;
    private final PointService pointService;
    private final SysConfigService configService;
    private final DrawSyncService drawSyncService;

    public ScheduledJobs(PredictionService predictionService, SettlementService settlementService,
                         ScoreService scoreService, PointService pointService,
                         SysConfigService configService, DrawSyncService drawSyncService) {
        this.predictionService = predictionService;
        this.settlementService = settlementService;
        this.scoreService = scoreService;
        this.pointService = pointService;
        this.configService = configService;
        this.drawSyncService = drawSyncService;
    }

    /** 官方开奖数据同步：开奖时段每 10 分钟拉取最近 5 期 */
    @Scheduled(cron = "0 0/10 21-23 * * ?")
    public void drawSyncJob() {
        if (!configService.getBoolean("feature.draw.sync", true)) {
            return;
        }
        try {
            var results = drawSyncService.syncAll(5);
            results.forEach(r -> log.info("[DrawSyncJob] {} {}", r.typeCode(), r.message()));
        } catch (Exception e) {
            log.warn("[DrawSyncJob] 执行失败", e);
        }
    }

    /**
     * 补齐各彩种下一期：同步完成后调用，避免某期开奖后「即将开奖」出现空档。
     * 每小时一次，与开奖时段解耦（跨天也要补）。
     */
    @Scheduled(cron = "0 5 * * * ?")
    public void ensureUpcomingJob() {
        if (!configService.getBoolean("feature.draw.sync", true)) {
            return;
        }
        try {
            var created = drawSyncService.ensureUpcoming();
            if (!created.isEmpty()) {
                log.info("[EnsureUpcomingJob] 补齐 {} 个可预测期次", created.size());
            }
        } catch (Exception e) {
            log.warn("[EnsureUpcomingJob] 执行失败", e);
        }
    }

    /** 停售到点锁定预测（3 → 4） */
    @Scheduled(cron = "0 * * * * ?")
    public void lockJob() {
        try {
            int count = predictionService.lockExpired();
            if (count > 0) {
                log.info("[LockJob] 锁定 {} 份预测", count);
            }
        } catch (Exception e) {
            log.warn("[LockJob] 执行失败", e);
        }
    }

    /** 已开奖期次结算 */
    @Scheduled(cron = "0 */5 * * * ?")
    public void settleJob() {
        if (!configService.getBoolean("feature.settle.enabled", true)) {
            return;
        }
        try {
            int count = settlementService.settlePending();
            if (count > 0) {
                log.info("[SettleJob] 结算 {} 个期次", count);
            }
        } catch (Exception e) {
            log.warn("[SettleJob] 执行失败", e);
        }
    }

    /** 评分全量重算 */
    @Scheduled(cron = "0 30 0 * * ?")
    public void scoreJob() {
        try {
            scoreService.rebuildAll();
        } catch (Exception e) {
            log.warn("[ScoreJob] 执行失败", e);
        }
    }

    /** 榜单快照 */
    @Scheduled(cron = "0 0 1 * * ?")
    public void rankJob() {
        for (String type : new String[]{"ACCURACY", "HIT_STREAK", "INCOME", "NEWCOMER"}) {
            try {
                scoreService.rebuildRank(type, "ALL");
            } catch (Exception e) {
                log.warn("[RankJob] {} 生成失败", type, e);
            }
        }
    }

    /** 积分过期 */
    @Scheduled(cron = "0 0 3 * * ?")
    public void pointExpireJob() {
        try {
            pointService.expireAll();
        } catch (Exception e) {
            log.warn("[PointExpireJob] 执行失败", e);
        }
    }
}
