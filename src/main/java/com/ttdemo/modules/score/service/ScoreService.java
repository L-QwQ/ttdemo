package com.ttdemo.modules.score.service;

import com.ttdemo.modules.score.entity.ScoreCreatorAccuracy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface ScoreService {

    /** 重算某创作者全部维度（ALL + 各彩种） */
    void recalculate(Long creatorId);

    void rebuildAll();

    ScoreCreatorAccuracy get(Long creatorId);

    /** 纯函数：给定样本计算评分，便于单元测试 */
    BigDecimal calculateScore(List<HitSample> samples);

    record HitSample(boolean hit, boolean free, int ageDays) {
    }

    Map<String, Object> leaderboard(String rankType, String typeCode, int limit);

    /** 生成某日榜单快照 */
    int rebuildRank(String rankType, String typeCode);
}
