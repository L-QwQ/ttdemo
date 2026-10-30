package com.ttdemo.modules.score.controller;

import com.ttdemo.common.api.R;
import com.ttdemo.modules.score.service.ScoreService;
import com.ttdemo.security.perm.Permissions;
import com.ttdemo.security.perm.RequirePerm;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class RankController {

    private final ScoreService scoreService;

    public RankController(ScoreService scoreService) {
        this.scoreService = scoreService;
    }

    @GetMapping("/api/v1/rank/list")
    public R<Map<String, Object>> list(@RequestParam(defaultValue = "ACCURACY") String rankType,
                                       @RequestParam(defaultValue = "ALL") String typeCode,
                                       @RequestParam(defaultValue = "20") int limit) {
        return R.ok(scoreService.leaderboard(rankType, typeCode, limit));
    }

    @PostMapping("/api/admin/v1/rank/rebuild")
    @RequirePerm(Permissions.RANK_REBUILD)
    public R<Map<String, Object>> rebuild(@RequestParam(defaultValue = "ACCURACY") String rankType,
                                          @RequestParam(defaultValue = "ALL") String typeCode) {
        int count = scoreService.rebuildRank(rankType, typeCode);
        return R.ok(Map.of("count", count, "rankType", rankType, "typeCode", typeCode));
    }

    @PostMapping("/api/admin/v1/score/rebuild")
    @RequirePerm(Permissions.RANK_REBUILD)
    public R<Void> rebuildScore() {
        scoreService.rebuildAll();
        return R.ok();
    }
}
