package com.ttdemo.modules.order.controller;

import com.ttdemo.common.api.PageResult;
import com.ttdemo.common.api.R;
import com.ttdemo.modules.order.dto.OrderDtos;
import com.ttdemo.modules.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/unlock")
    public R<OrderDtos.UnlockResult> unlock(@Valid @RequestBody OrderDtos.UnlockRequest request,
                                            @RequestHeader(value = "Idempotent-Key", required = false) String idempotentKey,
                                            @RequestHeader(value = "X-Device-Id", required = false) String deviceId) {
        return R.ok(orderService.unlock(request.predictionId(), idempotentKey, deviceId));
    }

    @GetMapping("/mine")
    public R<PageResult<OrderDtos.OrderVO>> mine(@RequestParam(defaultValue = "1") long pageNum,
                                                 @RequestParam(defaultValue = "10") long pageSize) {
        return R.ok(orderService.mine(pageNum, pageSize));
    }

    @GetMapping("/{orderNo}")
    public R<OrderDtos.OrderVO> detail(@PathVariable String orderNo) {
        return R.ok(orderService.detail(orderNo));
    }
}
