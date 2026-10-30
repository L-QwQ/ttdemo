package com.ttdemo.modules.order.service;

import com.ttdemo.common.api.PageResult;
import com.ttdemo.modules.order.dto.OrderDtos;

public interface OrderService {

    OrderDtos.UnlockResult unlock(Long predictionId, String idempotentKey, String deviceId);

    boolean isPurchased(Long userId, Long predictionId);

    OrderDtos.OrderVO detail(String orderNo);

    PageResult<OrderDtos.OrderVO> mine(long pageNum, long pageSize);

    /** 预测撤回/下架时全额退款，返回退款订单数 */
    int refundByPrediction(Long predictionId, String reason);

    PageResult<OrderDtos.OrderVO> listForAdmin(long pageNum, long pageSize, Integer status, String keyword);

    /** 按状态统计订单数量（后台仪表盘用） */
    long countByStatus(int status);
}
