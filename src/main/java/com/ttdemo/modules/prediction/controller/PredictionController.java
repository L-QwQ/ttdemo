package com.ttdemo.modules.prediction.controller;

import com.ttdemo.common.api.PageResult;
import com.ttdemo.common.api.R;
import com.ttdemo.modules.prediction.dto.PredictionDtos;
import com.ttdemo.modules.prediction.dto.PredictionQuery;
import com.ttdemo.modules.prediction.engine.PriceCalculator;
import com.ttdemo.modules.prediction.service.PredictionService;
import com.ttdemo.security.perm.RequireCreator;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/predictions")
public class PredictionController {

    private final PredictionService predictionService;

    public PredictionController(PredictionService predictionService) {
        this.predictionService = predictionService;
    }

    // ---------- 公开读 ----------
    @GetMapping
    public R<PageResult<PredictionDtos.ListItem>> list(@ModelAttribute PredictionQuery query) {
        return R.ok(predictionService.list(query));
    }

    @GetMapping("/{id}")
    public R<PredictionDtos.Detail> detail(@PathVariable Long id) {
        return R.ok(predictionService.detail(id));
    }

    @GetMapping("/{id}/hit-detail")
    public R<PredictionDtos.HitInfo> hitDetail(@PathVariable Long id) {
        return R.ok(predictionService.hitDetail(id));
    }

    // ---------- 登录后 ----------
    @GetMapping("/{id}/content")
    public R<Map<String, Object>> content(@PathVariable Long id) {
        return R.ok(predictionService.content(id));
    }

    @GetMapping("/purchased")
    public R<PageResult<PredictionDtos.ListItem>> purchased(@RequestParam(defaultValue = "1") long pageNum,
                                                            @RequestParam(defaultValue = "10") long pageSize) {
        return R.ok(predictionService.purchased(pageNum, pageSize));
    }

    // ---------- 创作者 ----------
    @GetMapping("/mine")
    @RequireCreator
    public R<PageResult<PredictionDtos.ListItem>> mine(@RequestParam(defaultValue = "1") long pageNum,
                                                       @RequestParam(defaultValue = "10") long pageSize,
                                                       @RequestParam(required = false) Integer status) {
        return R.ok(predictionService.mine(pageNum, pageSize, status));
    }

    @GetMapping("/price-quote")
    @RequireCreator
    public R<PriceCalculator.Quote> priceQuote(@RequestParam String typeCode,
                                               @RequestParam String playType) {
        return R.ok(predictionService.priceQuote(typeCode, playType));
    }

    @PostMapping
    @RequireCreator
    public R<PredictionDtos.Created> create(@Valid @RequestBody PredictionDtos.SaveRequest request) {
        return R.ok(predictionService.create(request));
    }

    @PutMapping("/{id}")
    @RequireCreator
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody PredictionDtos.SaveRequest request) {
        predictionService.update(id, request);
        return R.ok();
    }

    @PostMapping("/{id}/submit")
    @RequireCreator
    public R<Map<String, Object>> submit(@PathVariable Long id) {
        return R.ok(predictionService.submit(id));
    }

    @PostMapping("/{id}/withdraw")
    @RequireCreator
    public R<Void> withdraw(@PathVariable Long id) {
        predictionService.withdraw(id);
        return R.ok();
    }

    /**
     * 置顶推广：按天消耗积分（默认 50 积分/天），仅本人已上架的预测可推广。
     */
    @PostMapping("/{id}/promote")
    @RequireCreator
    public R<java.util.Map<String, Object>> promote(@PathVariable Long id,
                                                    @RequestBody(required = false) PromoteRequest request) {
        int days = request == null || request.days() == null ? 3 : request.days();
        return R.ok(predictionService.promote(id, days, com.ttdemo.security.SecurityUtils.currentUserId()));
    }

    public record PromoteRequest(Integer days) {
    }

    @DeleteMapping("/{id}")
    @RequireCreator
    public R<Void> delete(@PathVariable Long id) {
        predictionService.delete(id);
        return R.ok();
    }
}
