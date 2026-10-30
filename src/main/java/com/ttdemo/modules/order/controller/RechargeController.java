package com.ttdemo.modules.order.controller;

import com.ttdemo.common.api.R;
import com.ttdemo.modules.order.dto.OrderDtos;
import com.ttdemo.modules.order.service.RechargeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/recharge")
public class RechargeController {

    private final RechargeService rechargeService;

    public RechargeController(RechargeService rechargeService) {
        this.rechargeService = rechargeService;
    }

    @PostMapping("/orders")
    public R<OrderDtos.RechargeResult> create(@Valid @RequestBody OrderDtos.RechargeRequest request) {
        return R.ok(rechargeService.create(request.amountFen(), request.payChannel()));
    }

    @GetMapping("/orders/{orderNo}")
    public R<OrderDtos.RechargeStatus> query(@PathVariable String orderNo) {
        return R.ok(rechargeService.query(orderNo));
    }

    /** Mock 通道模拟支付成功（provider=mock 时开放） */
    @PostMapping("/orders/{orderNo}/mock-pay")
    public R<Void> mockPay(@PathVariable String orderNo) {
        rechargeService.mockPay(orderNo);
        return R.ok();
    }

    @PostMapping("/notify/{channel}")
    public R<String> notify(@PathVariable int channel, @RequestBody Map<String, String> body) {
        return R.ok(rechargeService.notify(channel, body));
    }

    /**
     * 微信支付 v3 回调：原始报文 + 签名头，需原样读取 body 参与验签。
     * 该路径在 SecurityConfig 中放行，靠签名保证来源可信。
     */
    @PostMapping("/notify/wechat")
    public Map<String, String> wechatNotify(@RequestBody String rawBody,
                                           @RequestHeader(value = "Wechatpay-Signature", required = false) String signature,
                                           @RequestHeader(value = "Wechatpay-Timestamp", required = false) String timestamp,
                                           @RequestHeader(value = "Wechatpay-Nonce", required = false) String nonce) {
        String result = rechargeService.notifyWechat(rawBody, signature, timestamp, nonce);
        // 微信要求成功时返回 200 + {"code":"SUCCESS"}，失败返回 4xx，这里统一按结果映射
        return "SUCCESS".equals(result)
                ? Map.of("code", "SUCCESS", "message", "成功")
                : Map.of("code", "FAIL", "message", result);
    }
}
