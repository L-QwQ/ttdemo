package com.ttdemo.modules.message.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ttdemo.common.api.PageResult;
import com.ttdemo.modules.message.MessageType;
import com.ttdemo.modules.message.entity.NotMessage;
import com.ttdemo.modules.message.mapper.NotMessageMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 站内信。业务侧通过领域事件语义调用，避免在各 Service 里拼文案。
 */
@Service
public class MessageService {

    private final NotMessageMapper mapper;

    public MessageService(NotMessageMapper mapper) {
        this.mapper = mapper;
    }

    // ---------- 领域语义方法：文案集中在此，业务侧只传事实 ----------

    /** 预测审核结果，落地后作者可在详情页看驳回理由 */
    public void predictionAudited(Long userId, Long predictionId, String title, boolean pass, String remark) {
        String subject = title == null || title.isBlank() ? "你发布的预测" : "《" + title + "》";
        if (pass) {
            send(userId, MessageType.AUDIT, "预测审核通过，已上架",
                    subject + " 已通过审核并上架，用户现在可以解锁查看。",
                    "PREDICTION", predictionId);
        } else {
            send(userId, MessageType.AUDIT, "预测审核未通过",
                    subject + " 未通过审核。\n原因：" + (remark == null || remark.isBlank() ? "未填写" : remark)
                            + "\n修改后可重新提交审核。",
                    "PREDICTION", predictionId);
        }
    }

    /** 预测被平台下架（作者侧） */
    public void predictionOffline(Long userId, Long predictionId, String title, String reason) {
        String subject = title == null || title.isBlank() ? "你发布的预测" : "《" + title + "》";
        send(userId, MessageType.AUDIT, "预测已被下架",
                subject + " 已被平台下架。\n原因：" + (reason == null || reason.isBlank() ? "内容违规" : reason)
                        + "\n已解锁用户将自动全额退款，如有异议可提交意见反馈。",
                "PREDICTION", predictionId);
    }

    /** 期次结算收益到账 */
    public void settlementCredited(Long userId, Long settlementId, String issueNo, long points) {
        send(userId, MessageType.SETTLE, "收益结算已入账",
                "第 " + issueNo + " 期结算完成，本期创作者收益 " + points + " 积分已计入你的账户。",
                "SETTLEMENT", settlementId);
    }

    /** 订单退款到账 */
    public void orderRefunded(Long userId, Long orderId, long points, String reason) {
        send(userId, MessageType.TRADE, "解锁订单已退款",
                points + " 积分已退回账户。原因：" + (reason == null || reason.isBlank() ? "内容下架" : reason),
                "ORDER", orderId);
    }

    /** 举报处理结果 */
    public void reportHandled(Long userId, Long reportId, boolean violated, String result) {
        String tail = violated ? "我们已对相关内容作出处理，感谢你的监督。" : "经核查内容未违规，本次举报不予支持。";
        send(userId, MessageType.SYSTEM, violated ? "举报已处理" : "举报核查完成",
                tail + (result == null || result.isBlank() ? "" : "\n处理说明：" + result),
                "REPORT", reportId);
    }

    /** 注销申请被驳回（通过时账号已停用，消息无处可投） */
    public void deactivationRejected(Long userId, Long recordId, String remark) {
        send(userId, MessageType.SYSTEM, "注销申请未通过",
                "你的账号注销申请未通过。原因：" + (remark == null || remark.isBlank() ? "未填写" : remark)
                        + "\n如有疑问可提交意见反馈。",
                "DEACTIVATION", recordId);
    }

    /** 权益兑换发放完成 */
    public void rewardIssued(Long userId, Long orderId, String itemName, String remark) {
        send(userId, MessageType.TRADE, "兑换权益已发放",
                "你兑换的「" + itemName + "」已发放。" + (remark == null || remark.isBlank() ? "" : "\n备注：" + remark),
                "REWARD", orderId);
    }

    /** 权益兑换取消并退分 */
    public void rewardCanceled(Long userId, Long orderId, String itemName, long points) {
        send(userId, MessageType.TRADE, "兑换已取消，积分已退回",
                "「" + itemName + "」的兑换已取消，" + points + " 积分已退回你的账户。",
                "REWARD", orderId);
    }

    public void send(Long userId, String msgType, String title, String content, String bizType, Long bizId) {
        if (userId == null) {
            return;
        }
        NotMessage message = new NotMessage();
        message.setUserId(userId);
        message.setMsgType(msgType);
        message.setTitle(title);
        message.setContent(content);
        message.setBizType(bizType);
        message.setBizId(bizId);
        message.setIsRead(0);
        mapper.insert(message);
    }

    public PageResult<NotMessage> list(Long userId, long pageNum, long pageSize, String msgType) {
        Page<NotMessage> page = mapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<NotMessage>()
                        .eq(NotMessage::getUserId, userId)
                        .eq(msgType != null && !msgType.isBlank(), NotMessage::getMsgType, msgType)
                        .orderByDesc(NotMessage::getId));
        return PageResult.of(page.getTotal(), pageNum, pageSize, page.getRecords());
    }

    public long unreadCount(Long userId) {
        return mapper.selectCount(new LambdaQueryWrapper<NotMessage>()
                .eq(NotMessage::getUserId, userId)
                .eq(NotMessage::getIsRead, 0));
    }

    /** 未读数按类型分组，供前端 tab 角标；返回 total + 各类型数量，缺省补 0 */
    public Map<String, Long> unreadStats(Long userId) {
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("total", 0L);
        for (String type : new String[]{MessageType.TRADE, MessageType.AUDIT, MessageType.SETTLE, MessageType.SYSTEM}) {
            stats.put(type, 0L);
        }
        if (userId == null) {
            return stats;
        }
        long total = 0;
        for (Map<String, Object> row : mapper.countUnreadByType(userId)) {
            Object type = row.get("msg_type");
            Object cnt = row.get("cnt");
            long value = cnt instanceof Number n ? n.longValue() : 0L;
            if (type != null && stats.containsKey(String.valueOf(type))) {
                stats.put(String.valueOf(type), value);
            }
            total += value;
        }
        stats.put("total", total);
        return stats;
    }

    public void markRead(Long userId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        mapper.update(null, new LambdaUpdateWrapper<NotMessage>()
                .eq(NotMessage::getUserId, userId)
                .in(NotMessage::getId, ids)
                .set(NotMessage::getIsRead, 1)
                .set(NotMessage::getReadTime, LocalDateTime.now()));
    }

    public void markAllRead(Long userId) {
        mapper.update(null, new LambdaUpdateWrapper<NotMessage>()
                .eq(NotMessage::getUserId, userId)
                .eq(NotMessage::getIsRead, 0)
                .set(NotMessage::getIsRead, 1)
                .set(NotMessage::getReadTime, LocalDateTime.now()));
    }
}
