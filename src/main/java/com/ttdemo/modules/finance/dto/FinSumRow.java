package com.ttdemo.modules.finance.dto;

/**
 * 聚合行：不同统计 SQL 复用同一载体，未涉及字段为 null
 */
public class FinSumRow {

    private Long amountFen;
    private Long points;
    private Long cnt;
    private Long grossPoints;
    private Long feePoints;
    private Long creatorPoints;

    public Long getAmountFen() {
        return amountFen == null ? 0L : amountFen;
    }

    public void setAmountFen(Long amountFen) {
        this.amountFen = amountFen;
    }

    public Long getPoints() {
        return points == null ? 0L : points;
    }

    public void setPoints(Long points) {
        this.points = points;
    }

    public Long getCnt() {
        return cnt == null ? 0L : cnt;
    }

    public void setCnt(Long cnt) {
        this.cnt = cnt;
    }

    public Long getGrossPoints() {
        return grossPoints == null ? 0L : grossPoints;
    }

    public void setGrossPoints(Long grossPoints) {
        this.grossPoints = grossPoints;
    }

    public Long getFeePoints() {
        return feePoints == null ? 0L : feePoints;
    }

    public void setFeePoints(Long feePoints) {
        this.feePoints = feePoints;
    }

    public Long getCreatorPoints() {
        return creatorPoints == null ? 0L : creatorPoints;
    }

    public void setCreatorPoints(Long creatorPoints) {
        this.creatorPoints = creatorPoints;
    }
}
