package com.ttdemo.modules.message;

/**
 * 站内信类型。通知中心（sys_notice）只放运营公告，
 * 业务进度一律走这里，由各领域服务在状态变更后主动推送。
 */
public final class MessageType {

    /** 交易：下单退款、兑换发放/取消 */
    public static final String TRADE = "TRADE";
    /** 审核：预测过审/驳回 */
    public static final String AUDIT = "AUDIT";
    /** 结算：期次收益入账 */
    public static final String SETTLE = "SETTLE";
    /** 系统：举报处理结果、注销审批结果 */
    public static final String SYSTEM = "SYSTEM";

    private MessageType() {
    }

    public static String name(String type) {
        if (type == null) {
            return "系统";
        }
        return switch (type) {
            case TRADE -> "交易";
            case AUDIT -> "审核";
            case SETTLE -> "结算";
            case SYSTEM -> "系统";
            default -> "系统";
        };
    }
}
