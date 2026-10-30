package com.ttdemo.modules.order.service;

import com.ttdemo.modules.order.dto.OrderDtos;

import java.util.Map;

public interface RechargeService {

    OrderDtos.RechargeResult create(int amountFen, Integer payChannel);

    OrderDtos.RechargeStatus query(String orderNo);
    /** 模拟支付（provider=mock 时可用），返回是否成功 */
    boolean mockPay(String orderNo);

    /** 支付回调：验签 + 金额比对 + 幂等入账 */
    String notify(int channel, Map<String, String> body);

    /**
     * 微信支付 v3 回调：原始报文验签 + AES-GCM 解密 + 幂等入账
     */
    String notifyWechat(String rawBody, String signature, String timestamp, String nonce);
}
