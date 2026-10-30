package com.ttdemo.modules.score.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("score_creator_accuracy")
public class ScoreCreatorAccuracy {

    public static final String TYPE_ALL = "ALL";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long creatorId;
    private String typeCode;
    private Integer sampleSize;
    private Integer hitTimes;
    private BigDecimal rawHitRate;
    private BigDecimal weightedHitRate;
    private BigDecimal wilsonLower;
    private BigDecimal accuracyScore;
    private Integer currentHitStreak;
    private Integer maxHitStreak;
    private BigDecimal recent10Rate;
    private Long totalView;
    private Long totalBuyer;
    private Long totalIncome;
    private BigDecimal priceCoefficient;
    private String calcVersion;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCreatorId() { return creatorId; }
    public void setCreatorId(Long creatorId) { this.creatorId = creatorId; }
    public String getTypeCode() { return typeCode; }
    public void setTypeCode(String typeCode) { this.typeCode = typeCode; }
    public Integer getSampleSize() { return sampleSize; }
    public void setSampleSize(Integer sampleSize) { this.sampleSize = sampleSize; }
    public Integer getHitTimes() { return hitTimes; }
    public void setHitTimes(Integer hitTimes) { this.hitTimes = hitTimes; }
    public BigDecimal getRawHitRate() { return rawHitRate; }
    public void setRawHitRate(BigDecimal rawHitRate) { this.rawHitRate = rawHitRate; }
    public BigDecimal getWeightedHitRate() { return weightedHitRate; }
    public void setWeightedHitRate(BigDecimal weightedHitRate) { this.weightedHitRate = weightedHitRate; }
    public BigDecimal getWilsonLower() { return wilsonLower; }
    public void setWilsonLower(BigDecimal wilsonLower) { this.wilsonLower = wilsonLower; }
    public BigDecimal getAccuracyScore() { return accuracyScore; }
    public void setAccuracyScore(BigDecimal accuracyScore) { this.accuracyScore = accuracyScore; }
    public Integer getCurrentHitStreak() { return currentHitStreak; }
    public void setCurrentHitStreak(Integer currentHitStreak) { this.currentHitStreak = currentHitStreak; }
    public Integer getMaxHitStreak() { return maxHitStreak; }
    public void setMaxHitStreak(Integer maxHitStreak) { this.maxHitStreak = maxHitStreak; }
    public BigDecimal getRecent10Rate() { return recent10Rate; }
    public void setRecent10Rate(BigDecimal recent10Rate) { this.recent10Rate = recent10Rate; }
    public Long getTotalView() { return totalView; }
    public void setTotalView(Long totalView) { this.totalView = totalView; }
    public Long getTotalBuyer() { return totalBuyer; }
    public void setTotalBuyer(Long totalBuyer) { this.totalBuyer = totalBuyer; }
    public Long getTotalIncome() { return totalIncome; }
    public void setTotalIncome(Long totalIncome) { this.totalIncome = totalIncome; }
    public BigDecimal getPriceCoefficient() { return priceCoefficient; }
    public void setPriceCoefficient(BigDecimal priceCoefficient) { this.priceCoefficient = priceCoefficient; }
    public String getCalcVersion() { return calcVersion; }
    public void setCalcVersion(String calcVersion) { this.calcVersion = calcVersion; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
