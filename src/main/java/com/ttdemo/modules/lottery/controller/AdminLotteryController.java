package com.ttdemo.modules.lottery.controller;

import com.ttdemo.common.api.PageResult;
import com.ttdemo.common.api.R;
import com.ttdemo.modules.lottery.dto.DrawResultRequest;
import com.ttdemo.modules.lottery.dto.IssueQuery;
import com.ttdemo.modules.lottery.dto.IssueSaveRequest;
import com.ttdemo.modules.lottery.dto.IssueVO;
import com.ttdemo.modules.lottery.dto.LotteryTypeSaveRequest;
import com.ttdemo.modules.lottery.dto.LotteryTypeVO;
import com.ttdemo.modules.lottery.dto.SyncResultVO;
import com.ttdemo.modules.lottery.service.LotteryService;
import com.ttdemo.modules.lottery.sync.DrawSyncService;
import com.ttdemo.security.perm.Permissions;
import com.ttdemo.security.perm.RequirePerm;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/v1")
public class AdminLotteryController {

    private final LotteryService lotteryService;
    private final DrawSyncService drawSyncService;

    public AdminLotteryController(LotteryService lotteryService, DrawSyncService drawSyncService) {
        this.lotteryService = lotteryService;
        this.drawSyncService = drawSyncService;
    }

    // ---------- 彩种配置 ----------
    @GetMapping("/lottery/types")
    @RequirePerm(Permissions.ISSUE_MANAGE)
    public R<List<LotteryTypeVO>> types() {
        return R.ok(lotteryService.listTypes(false));
    }

    @PostMapping("/lottery/types")
    @RequirePerm(Permissions.ISSUE_MANAGE)
    public R<Void> createType(@Valid @RequestBody LotteryTypeSaveRequest request) {
        lotteryService.saveType(request);
        return R.ok();
    }

    @PutMapping("/lottery/types/{id}")
    @RequirePerm(Permissions.ISSUE_MANAGE)
    public R<Void> updateType(@PathVariable Integer id, @Valid @RequestBody LotteryTypeSaveRequest request) {
        lotteryService.updateType(id, request);
        return R.ok();
    }

    // ---------- 期次 ----------
    /**
     * 期次分页。注意不能用 limit 一次拉全量：期号跨彩种位数不同（福彩 7 位 / 体彩 5 位 / 测试 8 位），
     * 早期按 issue_no 字符串倒序 + limit 会把其它彩种全都截断，只看到个别彩种。
     */
    @GetMapping("/issues")
    @RequirePerm(Permissions.ISSUE_MANAGE)
    public R<PageResult<IssueVO>> issues(IssueQuery query) {
        return R.ok(lotteryService.pageIssues(query));
    }

    @PostMapping("/issues")
    @RequirePerm(Permissions.ISSUE_MANAGE)
    public R<Map<String, Object>> createIssue(@Valid @RequestBody IssueSaveRequest request) {
        Long id = lotteryService.createIssue(request);
        return R.ok(Map.of("id", id));
    }

    @PutMapping("/issues/{id}")
    @RequirePerm(Permissions.ISSUE_MANAGE)
    public R<Void> updateIssue(@PathVariable Long id, @Valid @RequestBody IssueSaveRequest request) {
        lotteryService.updateIssue(id, request);
        return R.ok();
    }

    /** 人工录入第一步：校验并生成 reviewTicket */
    @PostMapping("/issues/{id}/draw-result/prepare")
    @RequirePerm(Permissions.ISSUE_MANAGE)
    public R<Map<String, String>> prepareDraw(@PathVariable Long id, @Valid @RequestBody DrawResultRequest request) {
        String ticket = lotteryService.prepareDrawResult(id, request);
        return R.ok(Map.of("reviewTicket", ticket, "hint", "请由另一账号携带该票据确认"));
    }

    /** 人工录入第二步：复核确认写入 */
    @PostMapping("/issues/{id}/draw-result")
    @RequirePerm(Permissions.ISSUE_MANAGE)
    public R<Void> confirmDraw(@PathVariable Long id, @Valid @RequestBody DrawResultRequest request) {
        lotteryService.confirmDrawResult(id, request);
        return R.ok();
    }

    // ---------- 官方开奖数据同步 ----------

    /** 同步单个彩种最近 count 期（官方公开数据源） */
    @PostMapping("/issues/sync")
    @RequirePerm(Permissions.ISSUE_MANAGE)
    public R<SyncResultVO> sync(@RequestParam String typeCode,
                                @RequestParam(defaultValue = "100") int count) {
        return R.ok(drawSyncService.sync(typeCode, count));
    }

    /** 一键同步全部彩种 */
    @PostMapping("/issues/sync-all")
    @RequirePerm(Permissions.ISSUE_MANAGE)
    public R<List<SyncResultVO>> syncAll(@RequestParam(defaultValue = "100") int count) {
        return R.ok(drawSyncService.syncAll(count));
    }

    /** 清理非官方期号（测试数据）且无预测关联的期次 */
    @PostMapping("/issues/cleanup")
    @RequirePerm(Permissions.ISSUE_MANAGE)
    public R<Map<String, Integer>> cleanup() {
        return R.ok(drawSyncService.cleanupNonOfficialIssues());
    }

    /** 按最新真实期号推算并创建下一期（可预测期次） */
    @PostMapping("/issues/ensure-upcoming")
    @RequirePerm(Permissions.ISSUE_MANAGE)
    public R<List<IssueVO>> ensureUpcoming() {
        return R.ok(drawSyncService.ensureUpcoming());
    }
}
