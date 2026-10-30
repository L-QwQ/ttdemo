package com.ttdemo.modules.point.service;

import com.ttdemo.common.api.PageResult;
import com.ttdemo.modules.point.entity.AccPointAccount;
import com.ttdemo.modules.point.entity.AccPointTransaction;

import java.util.Map;

/**
 * 积分账本。所有余额变动必须走本服务，禁止直接 UPDATE available。
 */
public interface PointService {

    AccPointAccount getOrCreate(Long userId);

    long balance(Long userId);

    /** 扣减积分（余额不足抛 60001），返回扣后余额 */
    long consume(Long userId, long points, String bizType, String bizNo, String remark);

    /** 增加积分（不校验上限） */
    long earn(Long userId, long points, String bizType, String bizNo, String remark);

    /**
     * 增加积分并指定有效期天数（新人礼包等短期积分），expireDays <= 0 时按全局配置
     */
    long earn(Long userId, long points, String bizType, String bizNo, String remark, int expireDays);

    /** 充值入账：本金与赠送分两条流水 */
    long recharge(Long userId, long points, long bonusPoints, String orderNo);

    /** 人工调账（财务权限） */
    long adjust(Long userId, long points, boolean increase, String reason);

    PageResult<AccPointTransaction> transactions(Long userId, long page, long size, String bizType);

    Map<String, Object> accountView(Long userId);

    /** 过期任务：按批次 FIFO 扣减已到期积分 */
    int expireAll();
}
