package com.ttdemo.modules.reward.controller;

import com.ttdemo.common.api.PageResult;
import com.ttdemo.common.api.R;
import com.ttdemo.common.api.ResultCode;
import com.ttdemo.common.exception.BizException;
import com.ttdemo.modules.reward.dto.RewardDtos;
import com.ttdemo.modules.reward.entity.AccRewardItem;
import com.ttdemo.modules.reward.service.RewardService;
import com.ttdemo.modules.user.service.UserService;
import com.ttdemo.security.perm.Permissions;
import com.ttdemo.security.perm.RequirePerm;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 后台：权益商品与兑换单管理
 */
@RestController
@RequestMapping("/api/admin/v1/rewards")
public class AdminRewardController {

    private final RewardService rewardService;
    private final UserService userService;

    public AdminRewardController(RewardService rewardService, UserService userService) {
        this.rewardService = rewardService;
        this.userService = userService;
    }

    /**
     * 运营手动赠送会员天数（活动补偿、客服安抚），与积分兑换互不影响。
     */
    @PostMapping("/vip/grant")
    @RequirePerm(Permissions.CONFIG_MANAGE)
    public R<Map<String, Object>> grantVip(@Valid @RequestBody GrantVipRequest request) {
        LocalDateTime expireAt = userService.grantVipDays(request.userId(), request.days());
        return R.ok(Map.of("userId", request.userId(), "vipExpireAt", expireAt.toString()));
    }

    public record GrantVipRequest(
            @NotNull(message = "用户 ID 不能为空") Long userId,
            @NotNull(message = "天数不能为空")
            @Min(value = 1, message = "天数至少 1 天")
            @Max(value = 3650, message = "单次最多赠送 3650 天") Integer days
    ) {
    }

    @GetMapping("/items")
    @RequirePerm(Permissions.CONFIG_MANAGE)
    public R<List<AccRewardItem>> items() {
        return R.ok(rewardService.listAllItems());
    }

    @PostMapping("/items")
    @RequirePerm(Permissions.CONFIG_MANAGE)
    public R<AccRewardItem> create(@Valid @RequestBody RewardDtos.ItemRequest request) {
        return R.ok(rewardService.saveItem(null, request));
    }

    @PutMapping("/items/{id}")
    @RequirePerm(Permissions.CONFIG_MANAGE)
    public R<AccRewardItem> update(@PathVariable Long id, @Valid @RequestBody RewardDtos.ItemRequest request) {
        return R.ok(rewardService.saveItem(id, request));
    }

    @GetMapping("/orders")
    @RequirePerm(Permissions.POINT_VIEW)
    public R<PageResult<RewardDtos.OrderVO>> orders(@RequestParam(defaultValue = "1") long pageNum,
                                                    @RequestParam(defaultValue = "10") long pageSize,
                                                    @RequestParam(required = false) Integer status,
                                                    @RequestParam(required = false) String keyword) {
        return R.ok(rewardService.listOrders(pageNum, pageSize, status, keyword));
    }

    @PostMapping("/orders/{id}/issue")
    @RequirePerm(Permissions.CONFIG_MANAGE)
    public R<Void> issue(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        rewardService.issue(id, body == null ? null : body.get("remark"));
        return R.ok();
    }

    @PostMapping("/orders/{id}/cancel")
    @RequirePerm(Permissions.POINT_ADJUST)
    public R<Void> cancel(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String remark = body == null ? null : body.get("remark");
        if (remark == null || remark.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "取消兑换需填写原因");
        }
        rewardService.cancel(id, remark);
        return R.ok();
    }
}
