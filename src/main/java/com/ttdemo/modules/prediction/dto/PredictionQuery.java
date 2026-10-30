package com.ttdemo.modules.prediction.dto;

public class PredictionQuery {

    private String typeCode;
    private String issueNo;
    private String playType;
    private Boolean onlyFree;
    private Integer priceMin;
    private Integer priceMax;
    /** hot / new / accuracy / priceAsc / priceDesc */
    private String sortBy = "hot";

    private Integer pageNum = 1;
    private Integer pageSize = 10;

    public String getTypeCode() { return typeCode; }
    public void setTypeCode(String typeCode) { this.typeCode = typeCode; }
    public String getIssueNo() { return issueNo; }
    public void setIssueNo(String issueNo) { this.issueNo = issueNo; }
    public String getPlayType() { return playType; }
    public void setPlayType(String playType) { this.playType = playType; }
    public Boolean getOnlyFree() { return onlyFree; }
    public void setOnlyFree(Boolean onlyFree) { this.onlyFree = onlyFree; }
    public Integer getPriceMin() { return priceMin; }
    public void setPriceMin(Integer priceMin) { this.priceMin = priceMin; }
    public Integer getPriceMax() { return priceMax; }
    public void setPriceMax(Integer priceMax) { this.priceMax = priceMax; }
    public String getSortBy() { return sortBy; }
    public void setSortBy(String sortBy) { this.sortBy = sortBy; }
    public Integer getPageNum() { return pageNum == null || pageNum < 1 ? 1 : pageNum; }
    public void setPageNum(Integer pageNum) { this.pageNum = pageNum; }
    public Integer getPageSize() {
        return pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 50);
    }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
}
