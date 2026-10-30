package com.ttdemo.common.api;

public enum ResultCode {

    /** 文档约定：0 表示成功 */
    SUCCESS(0, "ok"),

    // ---------- 通用 4xxxx（对齐需求文档 7.1.3） ----------
    BAD_REQUEST(40001, "参数校验失败"),
    UNAUTHORIZED(40101, "未登录或登录已失效"),
    FORBIDDEN(40301, "无权限或越权访问"),
    NOT_CREATOR(40302, "尚未认证为创作者"),
    NOT_REALNAME(40303, "尚未完成实名认证"),
    NOT_FOUND(40401, "资源不存在"),
    STATE_CONFLICT(40901, "当前状态不允许此操作"),
    IDEMPOTENT_CONFLICT(40902, "重复提交"),
    ALREADY_PURCHASED(40903, "已购买过该预测"),
    TOO_MANY_REQUESTS(42901, "请求过于频繁，请稍后再试"),
    ERROR(50001, "系统繁忙，请稍后再试"),

    // ---------- 交易 / 内容 6xxxx ----------
    POINT_NOT_ENOUGH(60001, "积分余额不足"),
    PREDICTION_UNAVAILABLE(60002, "预测已下架或不可购买"),
    SALE_CLOSED(60003, "已过购买截止时间"),
    CANNOT_BUY_OWN(60004, "不能购买自己发布的预测"),
    CONTENT_AUDIT_FAILED(60005, "内容审核未通过"),
    ISSUE_CLOSED(60006, "该期次已停售"),
    RECHARGE_HANDLED(60007, "充值订单已处理"),
    ITEM_EMPTY(60008, "预测项不能为空"),
    PRICE_OUT_OF_RANGE(60009, "定价超出允许区间"),
    MINOR_FORBIDDEN(60010, "未成年人不允许充值"),
    RISK_LIMITED(60011, "账号被风控限制"),
    PREDICTION_QUOTA_EXCEEDED(60012, "该期次发布预测数已达上限"),

    // ---------- 兼容既有模块（保留旧码值，避免历史调用方失效） ----------
    METHOD_NOT_ALLOWED(405, "请求方式不支持"),
    PHONE_FORMAT_ERROR(1001, "手机号格式不正确"),
    PHONE_ALREADY_REGISTERED(1002, "该手机号已注册，请直接登录"),
    ACCOUNT_NOT_FOUND(1003, "账号不存在"),
    PASSWORD_ERROR(1004, "手机号或密码错误"),
    ACCOUNT_DISABLED(1005, "账号已被禁用，请联系管理员"),
    SMS_CODE_ERROR(1006, "验证码不正确"),
    SMS_CODE_EXPIRED(1007, "验证码已过期，请重新获取"),
    SMS_CODE_USED(1008, "验证码已使用，请重新获取"),
    SMS_SEND_TOO_FREQUENT(1009, "验证码发送过于频繁，请稍后再试"),
    SMS_SEND_LIMIT(1010, "今日验证码发送次数已达上限"),
    SMS_SEND_FAILED(1011, "短信发送失败，请稍后再试"),
    TOKEN_INVALID(1012, "登录凭证无效"),
    TOKEN_EXPIRED(1013, "登录已过期，请重新登录"),
    REFRESH_TOKEN_INVALID(1014, "刷新凭证无效，请重新登录"),
    PASSWORD_NOT_SET(1015, "该账号未设置密码，请使用验证码登录"),
    NO_PERMISSION(1016, "无权访问该资源"),

    NOTICE_NOT_FOUND(1017, "通知不存在或已下架"),
    NOTICE_ALREADY_PUBLISHED(1018, "通知已发布，不能再编辑"),
    NOTICE_TARGET_EMPTY(1019, "请至少选择一个接收人"),
    FEEDBACK_NOT_FOUND(1020, "反馈不存在"),
    FEEDBACK_STATUS_INVALID(1021, "反馈状态值不合法"),

    // ---------- 创作者 / 预测 ----------
    CREATOR_NOT_FOUND(1022, "创作者不存在"),
    CREATOR_AUDIT_PENDING(1023, "认证申请审核中，请耐心等待"),
    PEN_NAME_TAKEN(1024, "该笔名已被占用"),
    REALNAME_REQUIRED_BEFORE_APPLY(1025, "请先完成实名认证"),
    ID_CARD_FORMAT_ERROR(1026, "身份证号格式不正确"),
    PREDICTION_NOT_FOUND(1027, "预测不存在"),
    PREDICTION_NOT_EDITABLE(1028, "仅草稿或被驳回的预测可编辑"),
    AUDIT_REMARK_REQUIRED(1029, "驳回必须填写理由（5-200 字）"),
    FREE_ITEM_RATIO_INVALID(1030, "免费预测项占比不能超过 40%"),
    ITEM_FORMAT_INVALID(1031, "预测项格式不合法"),
    ISSUE_NOT_FOUND(1032, "期次不存在"),
    LOTTERY_TYPE_NOT_FOUND(1033, "彩种不存在"),
    ILLEGAL_ARGUMENT(1034, "存在不合法的参数"),

    // ---------- 结算 / 榜单 ----------
    SETTLE_ALREADY_DONE(1035, "该期次已结算"),
    ISSUE_NOT_DRAWN(1036, "期次尚未开奖，无法结算"),
    SETTLEMENT_NOT_FOUND(1037, "结算单不存在"),
    SETTLEMENT_CANNOT_CANCEL(1038, "仅已入账的结算单可撤销"),
    REALNAME_AGE_LIMIT(1039, "未满 18 周岁不能完成实名认证"),
    REALNAME_ALREADY_PASSED(1040, "该账号已通过实名认证，如需修改请联系客服"),
    PASSWORD_ALREADY_SET(1041, "该账号已设置密码，请使用修改密码功能"),
    AVATAR_UPLOAD_FAILED(1042, "头像上传失败，请稍后重试"),
    RANDOM_VIP_REQUIRED(1043, "该预测方式为会员专享"),
    RANDOM_QUOTA_EXCEEDED(1044, "今日免费试用次数已用完"),
    ACCOUNT_LOCKED(1045, "登录失败次数过多，账号已临时锁定");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
