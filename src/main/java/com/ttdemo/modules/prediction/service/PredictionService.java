package com.ttdemo.modules.prediction.service;

import com.ttdemo.common.api.PageResult;
import com.ttdemo.modules.prediction.dto.PredictionDtos;
import com.ttdemo.modules.prediction.dto.PredictionQuery;
import com.ttdemo.modules.prediction.engine.PriceCalculator;

import java.util.Map;
import com.ttdemo.modules.prediction.entity.PrdPrediction;

public interface PredictionService {

    PredictionDtos.Created create(PredictionDtos.SaveRequest request);

    void update(Long id, PredictionDtos.SaveRequest request);

    Map<String, Object> submit(Long id);

    void withdraw(Long id);

    void delete(Long id);

    PageResult<PredictionDtos.ListItem> list(PredictionQuery query);

    PredictionDtos.Detail detail(Long id);

    Map<String, Object> content(Long id);

    PredictionDtos.HitInfo hitDetail(Long id);

    PageResult<PredictionDtos.ListItem> mine(long pageNum, long pageSize, Integer status);

    PageResult<PredictionDtos.ListItem> purchased(long pageNum, long pageSize);

    PriceCalculator.Quote priceQuote(String typeCode, String playType);

    // ---------- 后台 ----------
    PageResult<PredictionDtos.AuditRow> auditQueue(long pageNum, long pageSize, Integer status);

    void audit(Long id, PredictionDtos.AuditRequest request);

    void offline(Long id, String reason);

    /**
     * 置顶推广：按天扣积分并把预测置顶，返回到期时间与消耗积分
     */
    Map<String, Object> promote(Long id, int days, Long userId);

    /** 定时任务：停售到点锁定（3 → 4），返回锁定条数 */
    int lockExpired();

    /** 供结算任务读取 */
    PrdPrediction requireById(Long id);

    /** 按状态统计预测数量（后台仪表盘用） */
    long countByStatus(int status);
}
