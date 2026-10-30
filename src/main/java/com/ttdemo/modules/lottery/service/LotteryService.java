package com.ttdemo.modules.lottery.service;

import com.ttdemo.common.api.PageResult;
import com.ttdemo.modules.lottery.dto.DrawResultRequest;
import com.ttdemo.modules.lottery.dto.IssueQuery;
import com.ttdemo.modules.lottery.dto.IssueSaveRequest;
import com.ttdemo.modules.lottery.dto.IssueVO;
import com.ttdemo.modules.lottery.dto.LotteryTypeVO;
import com.ttdemo.modules.lottery.dto.LotteryTypeSaveRequest;
import com.ttdemo.modules.lottery.entity.LotIssue;
import com.ttdemo.modules.lottery.entity.LotLotteryType;

import java.util.List;

public interface LotteryService {

    List<LotteryTypeVO> listTypes(boolean onlyEnabled);

    LotLotteryType requireType(String code);

    List<IssueVO> listIssues(String typeCode, Integer drawStatus, int limit);

    /** 后台期次分页：按期号所属彩种 / 开奖状态 / 数据来源 / 期号搜索过滤，按开奖时间倒序 */
    PageResult<IssueVO> pageIssues(IssueQuery query);

    /** 当前可预测期次：sale_end_time > now，按停售时间升序；typeCode 为空时每个彩种各取一条 */
    List<IssueVO> currentIssues(String typeCode);

    IssueVO getIssue(Long id);

    LotIssue requireIssue(Long id);

    LotIssue requireIssueByNo(String typeCode, String issueNo);

    /** 最近已开奖期次（按期号倒序），供随机预测分析历史 */
    List<LotIssue> listDrawnIssues(String typeCode, int limit);

    /** 已开奖期次总数（不受 limit 影响），供前端展示「库中可用期数」 */
    long countDrawnIssues(String typeCode);

    /** 按数据来源统计已开奖期次（dataSource: 1 自动同步 / 2 人工录入） */
    long countDrawnIssues(String typeCode, Integer dataSource);

    Long createIssue(IssueSaveRequest request);

    void updateIssue(Long id, IssueSaveRequest request);

    void saveType(LotteryTypeSaveRequest request);

    void updateType(Integer id, LotteryTypeSaveRequest request);

    /** 人工录入第一步：校验并生成复核票据（不写号码） */
    String prepareDrawResult(Long issueId, DrawResultRequest request);

    /** 人工录入第二步：凭票据写入开奖号（双人复核） */
    void confirmDrawResult(Long issueId, DrawResultRequest request);

    /** 号码校验：按 play_config 的范围与个数约束 */
    void validateNumbers(LotLotteryType type, String red, String blue);

    /** 取开奖号数组（红/前区） */
    List<Integer> redList(LotIssue issue);

    List<Integer> blueList(LotIssue issue);
}
