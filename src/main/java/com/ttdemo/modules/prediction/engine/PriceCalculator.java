package com.ttdemo.modules.prediction.engine;

import com.ttdemo.modules.system.service.SysConfigService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 定价计算器：全项目唯一的定价公式实现。
 * 建议价 = round5(基础价 × 系数 × 样本因子)，创作者只能在 [下限, min(建议价, 上限)] 内定价。
 */
@Component
public class PriceCalculator {

    private final SysConfigService config;

    public PriceCalculator(SysConfigService config) {
        this.config = config;
    }

    public record Quote(
            int base,
            BigDecimal coefficient,
            BigDecimal sampleFactor,
            int suggestPrice,
            int minPrice,
            int maxPrice,
            BigDecimal accuracyScore,
            int sampleSize,
            String formula,
            String note
    ) {
    }

    public Quote quote(String playType, BigDecimal accuracyScore, int sampleSize) {
        int base = config.getInt("price.base." + playType, 20);
        BigDecimal score = accuracyScore == null ? BigDecimal.valueOf(30) : accuracyScore;

        BigDecimal slope = config.getDecimal("price.coef.slope", "0.008");
        BigDecimal intercept = config.getDecimal("price.coef.intercept", "0.6");
        BigDecimal coef = intercept.add(slope.multiply(score)).setScale(4, RoundingMode.HALF_UP);

        int fullSample = Math.max(1, config.getInt("price.sample.full", 30));
        BigDecimal ratio = BigDecimal.valueOf(Math.min(1.0, sampleSize * 1.0 / fullSample));
        BigDecimal sf = BigDecimal.valueOf(0.75).add(BigDecimal.valueOf(0.25).multiply(ratio))
                .setScale(4, RoundingMode.HALF_UP);

        BigDecimal raw = BigDecimal.valueOf(base).multiply(coef).multiply(sf);
        int suggest = round5(raw);

        int floor = (int) Math.ceil(base * 0.5);
        int hardCap = config.getInt("price.max.points", 200);
        int cap = Math.min((int) Math.floor(base * 2.5), hardCap);
        int maxPrice = Math.min(suggest, cap);
        if (maxPrice < floor) {
            maxPrice = floor;
        }

        String formula = String.format("round5(基础价%d × 系数%s × 样本因子%s) = %d",
                base, coef.stripTrailingZeros().toPlainString(),
                sf.stripTrailingZeros().toPlainString(), suggest);

        return new Quote(base, coef, sf, suggest, floor, maxPrice, score, sampleSize, formula,
                "准确度越高，系统允许的最高定价越高；你可在最低价至建议价之间调整");
    }

    /** 向上取整到 5 的倍数 */
    public static int round5(BigDecimal value) {
        return (int) (Math.ceil(value.doubleValue() / 5.0) * 5);
    }
}
