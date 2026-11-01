package com.ttdemo.modules.random.service;

import com.ttdemo.modules.random.dto.RandomDtos;

import java.util.List;

public interface RandomPredictionService {

    /** 方法列表；userId 为空表示游客，会员方法会被锁 */
    List<RandomDtos.MethodVO> methods(String typeCode, Long userId);

    /** 当前用户的会员状态与今日剩余免费次数 */
    RandomDtos.QuotaVO quota(Long userId);

    /** 历史数据概况：可用期数、来源构成、最近期次明细 */
    RandomDtos.HistorySummary history(String typeCode, int limit);

    /** 复式规格：各组单注个数与复式可选范围，位号玩法返回 supported=false */
    RandomDtos.BetSpec betSpec(String typeCode);

    RandomDtos.GenerateResult generate(RandomDtos.GenerateRequest request, Long userId);
}
