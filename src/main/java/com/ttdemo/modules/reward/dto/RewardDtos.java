package com.ttdemo.modules.reward.dto;

import com.ttdemo.modules.reward.entity.AccRewardItem;
import com.ttdemo.modules.reward.entity.AccRewardOrder;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 积分兑换权益相关 DTO
 */
public final class RewardDtos {

    private RewardDtos() {
    }

    /** 商品视图：附带当前用户是否够积分兑换 */
    public record ItemVO(
            Long id,
            String itemCode,
            String name,
            Integer category,
            String categoryName,
            Long pointsCost,
            Integer valueFen,
            Integer durationDays,
            Integer stock,
            Integer exchangedCount,
            String description,
            Integer status,
            /** 不限量 */
            boolean unlimited,
            /** 已售罄 */
            boolean soldOut,
            /** 当前用户积分是否足够 */
            boolean affordable
    ) {
        public static ItemVO from(AccRewardItem item, long userAvailable) {
            boolean unlimited = item.getStock() != null && item.getStock() < 0;
            boolean soldOut = !unlimited && (item.getStock() == null || item.getStock() <= 0);
            return new ItemVO(item.getId(), item.getItemCode(), item.getName(), item.getCategory(),
                    item.getCategory() != null && item.getCategory() == AccRewardItem.CATEGORY_VIP ? "会员" : "实物礼品",
                    item.getPointsCost(), item.getValueFen(), item.getDurationDays(), item.getStock(),
                    item.getExchangedCount(), item.getDescription(), item.getStatus(),
                    unlimited, soldOut,
                    item.getPointsCost() != null && userAvailable >= item.getPointsCost());
        }
    }

    public record ExchangeRequest(
            @NotNull(message = "请选择要兑换的权益") Long itemId,
            @Min(value = 1, message = "兑换数量至少 1")
            @Max(value = 10, message = "单次最多兑换 10 份") Integer quantity,
            @Size(max = 32, message = "收货人姓名过长") String contactName,
            @Size(max = 20, message = "联系电话过长") String contactPhone,
            @Size(max = 200, message = "收货地址过长") String address
    ) {
    }

    public record OrderVO(
            Long id,
            String orderNo,
            Long userId,
            String userPhone,
            String userNickname,
            Long itemId,
            String itemName,
            Integer category,
            Long pointsCost,
            Integer valueFen,
            Integer quantity,
            Integer status,
            String statusName,
            String contactName,
            String contactPhone,
            String address,
            LocalDateTime issuedAt,
            String issueRemark,
            LocalDateTime createdAt
    ) {
        public static OrderVO from(AccRewardOrder order) {
            return new OrderVO(order.getId(), order.getOrderNo(), order.getUserId(), null, null,
                    order.getItemId(), order.getItemName(), order.getCategory(), order.getPointsCost(),
                    order.getValueFen(), order.getQuantity(), order.getStatus(),
                    statusName(order.getStatus()), order.getContactName(), order.getContactPhone(),
                    order.getAddress(), order.getIssuedAt(), order.getIssueRemark(), order.getCreatedAt());
        }

        public OrderVO withUser(String phone, String nickname) {
            return new OrderVO(id, orderNo, userId, phone, nickname, itemId, itemName, category,
                    pointsCost, valueFen, quantity, status, statusName, contactName, contactPhone,
                    address, issuedAt, issueRemark, createdAt);
        }

        public static String statusName(Integer status) {
            if (status == null) {
                return "未知";
            }
            return switch (status) {
                case AccRewardOrder.STATUS_PENDING -> "待发放";
                case AccRewardOrder.STATUS_ISSUED -> "已发放";
                case AccRewardOrder.STATUS_CANCELED -> "已取消";
                default -> "未知";
            };
        }
    }

    /** 后台新建/编辑商品 */
    public record ItemRequest(
            @NotBlank(message = "商品编码不能为空")
            @Size(max = 32, message = "商品编码过长") String itemCode,
            @NotBlank(message = "商品名称不能为空")
            @Size(max = 64, message = "商品名称过长") String name,
            @NotNull(message = "请选择权益类型") Integer category,
            @NotNull(message = "所需积分不能为空")
            @Min(value = 1, message = "所需积分必须大于 0") Long pointsCost,
            @Min(value = 0, message = "折算价值不能为负") Integer valueFen,
            @Min(value = 0, message = "会员天数不能为负") Integer durationDays,
            /** -1 不限量 */
            Integer stock,
            @Size(max = 500, message = "描述过长") String description,
            Integer status,
            Integer sortNo
    ) {
    }

    /** 前台总览：积分、会员、可兑换数 */
    public record SummaryVO(
            long available,
            long totalIncome,
            long totalRecharge,
            long totalConsume,
            boolean vip,
            LocalDateTime vipExpireAt,
            long exchangedPoints,
            long exchangedCount,
            int itemCount,
            int pendingCount,
            boolean exchangeEnabled,
            long minPoints,
            long dailyLimit,
            long todayCount
    ) {
    }
}
