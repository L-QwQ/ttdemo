package com.ttdemo.modules.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 每日资金趋势点
 */
public class FinDailyRow {

    private LocalDate day;
    private Long amountFen;
    private Long points;
    private Long cnt;

    public LocalDate getDay() {
        return day;
    }

    public void setDay(LocalDate day) {
        this.day = day;
    }

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

    public BigDecimal getAmountYuan() {
        return BigDecimal.valueOf(getAmountFen()).movePointLeft(2);
    }
}
