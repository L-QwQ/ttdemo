package com.ttdemo.modules.settlement.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ttdemo.modules.lottery.entity.LotIssue;
import com.ttdemo.modules.lottery.mapper.LotIssueMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 期次结算状态的独立写入器。
 * <p>
 * 结算主流程是一个大事务，任何异常都会整体回滚。若把 RUNNING / DONE / FAILED 状态写在同一事务里，
 * 失败分支记录的 settle_error 会连同业务数据一起被撤销——既看不到失败原因，
 * 定时任务也会在下一轮把同一期次当成"首次结算"反复重试。因此状态写入一律走 REQUIRES_NEW，
 * 让状态在业务回滚后依然留存。
 */
@Service
public class IssueSettleStatusWriter {

    private final LotIssueMapper issueMapper;

    public IssueSettleStatusWriter(LotIssueMapper issueMapper) {
        this.issueMapper = issueMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRunning(Long issueId) {
        update(issueId, LotIssue.SETTLE_RUNNING, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markDone(Long issueId) {
        update(issueId, LotIssue.SETTLE_DONE, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long issueId, String error) {
        update(issueId, LotIssue.SETTLE_FAILED, error);
    }

    private void update(Long issueId, int status, String error) {
        LambdaUpdateWrapper<LotIssue> wrapper = new LambdaUpdateWrapper<LotIssue>()
                .eq(LotIssue::getId, issueId)
                .set(LotIssue::getSettleStatus, status)
                .set(LotIssue::getSettleError, status == LotIssue.SETTLE_FAILED ? truncate(error) : null);
        if (status == LotIssue.SETTLE_DONE) {
            wrapper.set(LotIssue::getSettleTime, java.time.LocalDateTime.now());
        }
        issueMapper.update(null, wrapper);
    }

    private static String truncate(String error) {
        if (error == null) {
            return "未知错误";
        }
        return error.length() > 200 ? error.substring(0, 200) : error;
    }
}
