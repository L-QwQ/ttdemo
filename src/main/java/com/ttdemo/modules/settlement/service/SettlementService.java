package com.ttdemo.modules.settlement.service;

import com.ttdemo.common.api.PageResult;
import com.ttdemo.modules.settlement.entity.AccSettlement;

import java.util.Map;

public interface SettlementService {

    /** 结算单个期次（幂等，可重跑） */
    Map<String, Object> settleIssue(Long issueId);

    /** 扫描已开奖未结算期次并结算，返回处理期次数 */
    int settlePending();

    PageResult<AccSettlement> list(long pageNum, long pageSize, Integer status, Long issueId);

    /** 创作者自己的结算单（收益明细） */
    PageResult<AccSettlement> listByCreator(Long creatorId, long pageNum, long pageSize);

    /** 撤销结算单：扣回创作者积分（仅已入账可撤） */
    void cancel(Long settlementId, String reason);

    /** 财务对账 */
    Map<String, Object> reconciliation(String startDate, String endDate, String typeCode);
}
