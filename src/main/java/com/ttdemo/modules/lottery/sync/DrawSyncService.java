package com.ttdemo.modules.lottery.sync;

import com.ttdemo.modules.lottery.dto.IssueVO;
import com.ttdemo.modules.lottery.dto.SyncResultVO;

import java.util.List;
import java.util.Map;

public interface DrawSyncService {

    /**
     * 同步单个彩种最近 count 期开奖（可重复执行，按期号 upsert）
     */
    SyncResultVO sync(String typeCode, int count);

    /**
     * 一键同步所有彩种
     */
    List<SyncResultVO> syncAll(int count);

    /**
     * 清理非官方期号且无预测关联的历史期次（测试数据）
     *
     * @return deleted / skipped（有预测关联未删除）
     */
    Map<String, Integer> cleanupNonOfficialIssues();

    /**
     * 按最新真实期号 + 开奖日历推算并创建「下一期」，使其处于可预测状态
     */
    List<IssueVO> ensureUpcoming();
}
