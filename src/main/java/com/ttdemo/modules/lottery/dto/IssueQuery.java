package com.ttdemo.modules.lottery.dto;

import com.ttdemo.common.api.PageQuery;

/**
 * 后台期次分页查询条件
 */
public class IssueQuery extends PageQuery {

    /** 彩种编码，空 = 全部 */
    private String typeCode;
    /** 开奖状态：0 待开奖 / 1 已开奖 */
    private Integer drawStatus;
    /** 数据来源：1 自动同步 / 2 人工录入，空 = 全部 */
    private Integer dataSource;
    /** 期号模糊搜索 */
    private String keyword;
    /** true = 只看官方期号（福彩 7 位 / 体彩 5 位），过滤掉历史测试期次 */
    private Boolean officialOnly;

    public String getTypeCode() {
        return typeCode;
    }

    public void setTypeCode(String typeCode) {
        this.typeCode = typeCode;
    }

    public Integer getDrawStatus() {
        return drawStatus;
    }

    public void setDrawStatus(Integer drawStatus) {
        this.drawStatus = drawStatus;
    }

    public Integer getDataSource() {
        return dataSource;
    }

    public void setDataSource(Integer dataSource) {
        this.dataSource = dataSource;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public Boolean getOfficialOnly() {
        return officialOnly;
    }

    public void setOfficialOnly(Boolean officialOnly) {
        this.officialOnly = officialOnly;
    }
}
